package one.plaza.nightwaveplaza.ui

import android.content.Intent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.plaza.nightwaveplaza.MainActivity
import one.plaza.nightwaveplaza.R
import one.plaza.nightwaveplaza.Settings

@UnstableApi class ViewClient(private var activity: MainActivity): WebViewClient() {
    private var loadError = false

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        return if (!request.url.toString().startsWith("https://m.plaza.one")) {
            val intent = Intent(Intent.ACTION_VIEW, request.url)
            view.context.startActivity(intent)
            true
        } else {
            false
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        if (loadError) {
            // Don't reload if web view paused
            if (activity.webViewPaused) {
                return
            }

            // Show toast and try again
            activity.makeToast(activity.getString(R.string.no_internet))
            activity.lifecycleScope.launch {
                delay(5000)
                view.reload()
            }
        } else {
            activity.webViewLoaded = true
            activity.pushViewData("isPlaying", Settings.isPlaying.toString())
            activity.pushViewData("sleepTime", Settings.sleepTime.toString())
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