package one.plaza.nightwaveplaza.view

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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

@UnstableApi
class WebViewManager(
    private val callback: WebViewCallback,
    private val webView: WebView,
    private val lifecycle: Lifecycle,
) : LifecycleObserver {
    val context = callback.getActivityContext()

    private var viewVersionJob: Job? = null
    var webViewLoaded = false
    private val webViewClient = CustomWebViewClient()

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.textZoom = 100
        webSettings.domStorageEnabled = true
        webView.addJavascriptInterface(WebViewJavaScriptHandler(callback), "AndroidInterface")
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        if (BuildConfig.DEBUG) {
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
            WebView.setWebContentsDebuggingEnabled(true)
        } else {
            webSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        webView.webViewClient = webViewClient

        lifecycle.addObserver(LifeCycleObserver())
    }

    fun loadWebView() {
        webViewClient.resetAttempts()

        if (BuildConfig.DEBUG) {
            webView.loadUrl("http://plaza.local:4173")
            return
        }

        if (Settings.viewUri != "") {
            webView.loadUrl(Settings.viewUri)
            updateViewVersion(true)
        } else {
            updateViewVersion(false)
        }
    }

    private fun updateViewVersion(silent: Boolean) {
        if (BuildConfig.DEBUG) {
            return
        }

        if (viewVersionJob?.isActive == true) {
            return
        }

        viewVersionJob = lifecycle.coroutineScope.launch {
            val client = ApiClient()
            var version: ApiClient.Version? = null
            var attempts = 0

            while (version == null && isActive && attempts < 4) {
                try {
                    version = client.getVersion()
                } catch (_: Exception) {
                    if (!silent) {
                        callback.onWebViewLoadFail()
                    }
                    delay(5000)
                    attempts += 1
                }
            }

            if (version == null) {
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (version.viewSrc != Settings.viewUri) {
                    webView.clearCache(true)
                    Settings.viewUri = version.viewSrc
                }
                webView.stopLoading()
                webView.loadUrl(Settings.viewUri)
            }
        }
    }

    fun pauseWebView() {
        if (!webViewLoaded) {
            webView.stopLoading()

            if (viewVersionJob != null) {
                viewVersionJob?.cancel()
                viewVersionJob = null
            }
        }

        //webViewPaused = true
        if (webViewLoaded) {
            webView.onPause()
        }
    }

    fun resumeWebView() {
        //webViewPaused = false
        webView.onResume()

//        if (webViewLoaded) {
//            onWebViewLoaded()
//        } else {
//            loadWebView()
//        }
    }

    fun pushData(action: String, payload: Any) {
        var call = "window['emitter'].emit('$action', $payload)"
        if (payload is String) {
            call = "window['emitter'].emit('$action', '$payload')"
        }
        webView.evaluateJavascript(call, null)
    }

    fun onWebViewLoaded() {
        webViewLoaded = true
        callback.onWebViewLoaded()
    }

    private inner class CustomWebViewClient : WebViewClient() {
        private var loadError = false
        private var attempts = 0

        fun resetAttempts() {
            attempts = 0
        }

        private fun retryWithDelay(url: String) {
            lifecycle.coroutineScope.launch {
                delay(3000)
                webView.loadUrl(url)
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            if (BuildConfig.DEBUG) {
                return false
            }

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
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)

            if (loadError) {
                webView.visibility = View.GONE
                if (attempts < 3) {
                    retryWithDelay(url)
                    attempts += 1
                } else {
                    callback.onWebViewLoadFail()
                }
            } else {
                attempts = 0
                view.visibility = View.VISIBLE
                onWebViewLoaded()
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            loadError = true
        }

    }

    inner class LifeCycleObserver : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumeWebView()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    pauseWebView()
                }

                else -> {}
            }
        }
    }

}