package one.plaza.nightwaveplaza.view

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.BuildConfig
import one.plaza.nightwaveplaza.updater.WebAppAssetResolver
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
    var webViewLoaded = false
    private val webViewClient = CustomWebViewClient()
    private val lifeCycleObserver = LifeCycleObserver()

    private val webAppRouter = WebAppAssetResolver(webView.context)
    private lateinit var assetLoader: WebViewAssetLoader

    /**
     * Initializes WebView with required settings and interfaces
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        val context = webView.context

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/updates/", WebViewAssetLoader.InternalStoragePathHandler(context, webAppRouter.updatesDir))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            textZoom = 100
            domStorageEnabled = true
            blockNetworkImage = false
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_NO_CACHE
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

        if (BuildConfig.PLAZA_URL_OVERRIDE.isNotEmpty()) {
            webView.loadUrl(BuildConfig.PLAZA_URL_OVERRIDE)
            return
        }

        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val urlToLoad = webAppRouter.resolveEntryPointUrl()

            withContext(Dispatchers.Main) {
                Timber.d("Loading URL: $urlToLoad")
                webView.loadUrl(urlToLoad)
            }

            webAppRouter.performCleanup()
        }
    }

    /**
     * Pauses WebView and cancels current tasks
     */
    fun pauseWebView() {
        if (!webViewLoaded) {
            webView.stopLoading()
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
        lifecycle.removeObserver(lifeCycleObserver)
    }

    /**
     * Sends data to JavaScript via event emission
     */
    fun pushData(action: String, payload: Any? = null) {
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
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return assetLoader.shouldInterceptRequest(request.url)
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
            return if (!request.url.toString().startsWith("https://appassets.androidplatform.net")) {
                val intent = Intent(Intent.ACTION_VIEW, request.url)
                view.context.startActivity(intent)
                true
            } else {
                false
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            Timber.d("onPageStarted")
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            Timber.d("onPageFinished")
            onWebViewLoaded()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (error != null) {
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