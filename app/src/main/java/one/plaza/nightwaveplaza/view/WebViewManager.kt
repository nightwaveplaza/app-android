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
import androidx.lifecycle.coroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.BuildConfig
import one.plaza.nightwaveplaza.api.Json
import one.plaza.nightwaveplaza.updater.WebAppAssetResolver
import org.json.JSONObject
import timber.log.Timber

/**
 * Manages WebView state
 */
@UnstableApi
class WebViewManager(
    private val callback: WebViewCallback,
    @PublishedApi internal val webView: WebView,
    private val lifecycle: Lifecycle,
) {
    private var isLoaded = false
    private val webViewClient = InternalWebViewClient()

    private val webAppRouter = WebAppAssetResolver(webView.context)
    private lateinit var assetLoader: WebViewAssetLoader

    /**
     * Lifecycle observer to manage WebView state
     */
    private val lifeCycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> webView.onResume()
            Lifecycle.Event.ON_PAUSE -> if (!isLoaded) webView.stopLoading() else webView.onPause()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> {}
        }
    }

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
            addJavascriptInterface(WebAppInterface(callback), "AndroidInterface")
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
    fun load() {
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

    fun destroy() {
        isLoaded = false
        webView.apply {
            removeJavascriptInterface("AndroidInterface")
            stopLoading()
            clearHistory()
            removeAllViews()
            loadUrl("about:blank")
            destroy()
        }
        lifecycle.removeObserver(lifeCycleObserver)
    }

    /**
     * Sends data to JavaScript via event emission
     */
    inline fun <reified T> emitEvent(action: String, payload: T? = null) {
        val arg = when (payload) {
            null -> "null"
            is Boolean, is Number -> payload.toString()
            is String -> JSONObject.quote(payload)
            else -> try {
                Json.mapper.encodeToString(payload)
            } catch (_: Exception) {
                JSONObject.quote(payload.toString())
            }
        }

        webView.post {
            webView.evaluateJavascript("window.emitter.emit('$action', $arg)", null)
        }
    }

    /**
     * Handles successful WebView load event
     */
    fun onWebViewLoaded() {
        isLoaded = true
        callback.onWebViewLoaded()
    }

    /**
     * Custom WebViewClient to handle page loading and errors
     */
    private inner class InternalWebViewClient : WebViewClient() {
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
                view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
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
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            super.onReceivedError(view, request, error)
            if (request.isForMainFrame) {
                Timber.e("Main frame load error: ${error.errorCode} - ${error.description}")
                callback.onWebViewLoadFail()
            }
        }
    }

}