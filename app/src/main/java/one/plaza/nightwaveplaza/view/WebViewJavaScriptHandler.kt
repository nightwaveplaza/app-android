package one.plaza.nightwaveplaza.view

import android.webkit.JavascriptInterface
import androidx.media3.common.util.UnstableApi
import one.plaza.nightwaveplaza.BuildConfig
import one.plaza.nightwaveplaza.Settings
import one.plaza.nightwaveplaza.helpers.Utils

@UnstableApi
class WebViewJavaScriptHandler(private val callback: WebViewCallback) {
    @JavascriptInterface
    fun openDrawer() {
        callback.onOpenDrawer()
    }

    @JavascriptInterface
    fun audioPlay() {
        callback.onPlayAudio()
    }

    @JavascriptInterface
    fun setBackground(backgroundSrc: String) {
        callback.onSetBackground(backgroundSrc)
    }

    @JavascriptInterface
    fun toggleFullscreen() {
        callback.onToggleFullscreen()
    }

    @JavascriptInterface
    fun getUserAgent(): String {
        return Utils.getUserAgent()
    }

    @JavascriptInterface
    fun setAudioQuality(lowQuality: Boolean) {
        Settings.lowQualityAudio = lowQuality
    }

    @JavascriptInterface
    fun setSleepTimer(timestamp: Long) {
        callback.onSetSleepTimer(timestamp)
    }

    @JavascriptInterface
    fun getAuthToken(): String {
        return Settings.userToken
    }

    @JavascriptInterface
    fun setAuthToken(token: String) {
        Settings.userToken = token
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
    }

    @JavascriptInterface
    fun setLanguage(lang: String) {
        callback.onSetLanguage(lang)
    }

    @JavascriptInterface
    fun onReady() {
        callback.onReady()
    }

    @JavascriptInterface
    fun socketReconnect() {
        callback.onReconnectRequest()
    }
}