package one.plaza.nightwaveplaza.view

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
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

    private var viewVersionJob: Job? = null
    var webViewLoaded = false
    private val webViewClient = CustomWebViewClient()
    private val lifeCycleObserver = LifeCycleObserver()

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        val webSettings = webView.settings
        webSettings.apply {
            javaScriptEnabled = true
            textZoom = 100
            domStorageEnabled = true
            blockNetworkImage = false
            loadsImagesAutomatically = true
        }
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

        lifecycle.addObserver(lifeCycleObserver)
    }

    fun loadWebView() {
        webViewClient.resetAttempts()

        if (BuildConfig.DEBUG) {
            webView.loadUrl("http://plaza.local:4173")
            return
        }

        updateViewVersion()
    }

    private fun updateViewVersion() {
        if (BuildConfig.DEBUG) {
            return
        }

        viewVersionJob?.cancel()
        viewVersionJob = lifecycle.coroutineScope.launch(Dispatchers.IO) {
            val client = ApiClient()
            var version: ApiClient.Version? = null
            var attempts = 0

            while (version == null && isActive && attempts < 4) {
                try {
                    version = client.getVersion()
                } catch (e: Exception) {
                    Log.e("Nightwave Plaza", "UpdateException", e)
                    delay(5000)
                    attempts += 1
                }
            }

            if (version == null) {
                callback.onWebViewLoadFail()
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

        if (webViewLoaded) {
            webView.onPause()
        }
    }

    fun resumeWebView() {
        webView.onResume()
        pushData("onResume")
    }

    fun destroy() {
        viewVersionJob?.cancel()
        webView.destroy()
        lifecycle.removeObserver(lifeCycleObserver)
    }

    fun pushData(action: String, payload: Any? = null) {
        var call = "window['emitter'].emit('$action', $payload)"
        if (payload == null) {
            call = "window['emitter'].emit('$action')"
        }
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
                delay(5000)
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

            return if (!request.url.toString().startsWith("https://m2.plaza.one")) {
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

            if (view.progress < 100) {
                return
            }

            if (loadError) {
                //webView.visibility = View.GONE
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
                    println("WebView onResume")
                    resumeWebView()
                }

                Lifecycle.Event.ON_PAUSE -> {
                    println("WebView onPause")
                    pauseWebView()
                }

                Lifecycle.Event.ON_DESTROY -> {
                    destroy()
                }

                else -> {}
            }
        }
    }

}