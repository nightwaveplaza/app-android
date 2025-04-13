package one.plaza.nightwaveplaza.view

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.BuildConfig
import one.plaza.nightwaveplaza.Settings
import one.plaza.nightwaveplaza.api.ApiClient
import timber.log.Timber

/**
 * Manages WebView state
 */
@UnstableApi
class WebViewManager(
    private val callback: WebViewCallback,
    private val webView: WebView,
    private val lifecycle: Lifecycle,
) : LifecycleObserver {

    private var viewVersionJob: Job? = null
    var webViewLoaded = false
    private val webViewClient = CustomWebViewClient()
    private val lifeCycleObserver = LifeCycleObserver()

    /**
     * Initializes WebView with required settings and interfaces
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        webView.settings.apply {
            javaScriptEnabled = true
            textZoom = 100
            domStorageEnabled = true
            blockNetworkImage = false
            loadsImagesAutomatically = true
            cacheMode = if (BuildConfig.DEBUG) {
                WebSettings.LOAD_NO_CACHE
            } else {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
        }

        webView.apply {
            addJavascriptInterface(WebViewJavaScriptHandler(callback), "AndroidInterface")
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = this@WebViewManager.webViewClient
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        lifecycle.addObserver(lifeCycleObserver)
    }

    /**
     * Starts the WebView loading process using either the config URL
     * or fetching the latest from API
     */
    fun loadWebView() {
        Timber.d("Load WebView")
        webViewClient.resetAttempts()

        if (BuildConfig.PLAZA_URL_OVERRIDE.isNotEmpty()) {
            webView.loadUrl(BuildConfig.PLAZA_URL_OVERRIDE)
            return
        }

        updateViewVersion()
    }

    /**
     * Fetches the current UI version from server and load WebView
     */
    private fun updateViewVersion() {
        Timber.d("Updating view version")

        viewVersionJob?.cancel()
        viewVersionJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val client = ApiClient()
            var version: ApiClient.Version? = null
            var attempts = 0
            val maxAttempts = 3

            // Try to get UI version from server with retries
            while (version == null && isActive && attempts <= maxAttempts) {
                Timber.d("Trying to get new version...")
                try {
                    version = client.getVersion()
                } catch (e: Exception) {
                    Timber.d("Error updating version")
                    Timber.e(e)
                    delay(5000)
                    attempts += 1
                }
            }

            if (version == null) {
                callback.onWebViewLoadFail()
                return@launch
            }

            withContext(Dispatchers.Main) {
                // Only clear cache if URL has changed
                if (version.viewSrc != Settings.viewUri) {
                    Timber.d("Version changed, clearing cache")
                    webView.clearCache(true)
                    Settings.viewUri = version.viewSrc
                }

                Timber.d("Loading ${Settings.viewUri}")
                webView.loadUrl(Settings.viewUri)
            }
        }
    }

    /**
     * Pauses WebView and cancels current tasks
     */
    fun pauseWebView() {
        if (!webViewLoaded) {
            webView.stopLoading()
            viewVersionJob?.cancel()
            viewVersionJob = null
        } else {
            webView.onPause()
        }
    }

    /**
     * Resumes WebView operation
     */
    fun resumeWebView() {
        webView.onResume()
    }

    fun destroy() {
        webViewLoaded = false
        webView.apply {
            stopLoading()
            removeAllViews()
            loadUrl("about:blank")
            destroy()
        }
        viewVersionJob?.cancel()
        lifecycle.removeObserver(lifeCycleObserver)
    }

    /**
     * Sends data to JavaScript via event emission
     */
    fun pushData(action: String, payload: Any? = null) {
        println(action)
        println(payload)
        if (!webViewLoaded) {
            return
        }

        val call = when (payload) {
            null -> "window['emitter'].emit('$action')"
            is String -> "window['emitter'].emit('$action', '$payload')"
            else -> "window['emitter'].emit('$action', $payload)"
        }
        webView.post {
            webView.evaluateJavascript(call, null)
        }
    }

    /**
     * Handles successful WebView load event
     */
    fun onWebViewLoaded() {
        webViewLoaded = true
        callback.onWebViewLoaded()
    }

    /**
     * Custom WebViewClient to handle page loading and errors
     */
    private inner class CustomWebViewClient : WebViewClient() {
        private var loadError = false
        private var attempts = 0
        private val maxRetryAttempts = 3

        fun resetAttempts() {
            attempts = 0
        }

        /**
         * Retries loading the URL after delay
         */
        private fun retryWithDelay(url: String) {
            lifecycle.coroutineScope.launch {
                delay(3000)
                Timber.d("Retrying load...")
                webView.loadUrl(url)
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            // Allow loading any URL in debug mode
            if (BuildConfig.DEBUG) {
                return false
            }

            // Open external links in browser
            return if (!request.url.toString().startsWith("https://m.plaza.one")) {
                val intent = Intent(Intent.ACTION_VIEW, request.url)
                view.context.startActivity(intent)
                true
            } else {
                false
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            loadError = false
            Timber.d("onPageStarted")
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            // Wait for complete page load
            if (view.progress < 100) {
                return
            }

            Timber.d("onPageFinished (100)")

            if (loadError) {
                Timber.d("WebView load error")
                if (attempts <= maxRetryAttempts) {
                    retryWithDelay(url)
                    attempts += 1
                } else {
                    callback.onWebViewLoadFail()
                }
                return
            }

            Timber.d("WebView loaded")
            attempts = 0
            onWebViewLoaded()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            loadError = true
            if (error != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Timber.e(error.description.toString())
            }
        }
    }

    /**
     * Lifecycle observer to manage WebView state
     */
    inner class LifeCycleObserver : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumeWebView()
                    Timber.d("Lifecycle: onResume")
                }

                Lifecycle.Event.ON_PAUSE -> {
                    pauseWebView()
                    Timber.d("Lifecycle: onPause")
                }

                Lifecycle.Event.ON_DESTROY -> {
                    destroy()
                }

                else -> {}
            }
        }
    }

}