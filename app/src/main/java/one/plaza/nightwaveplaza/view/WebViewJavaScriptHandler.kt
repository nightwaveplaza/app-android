package one.plaza.nightwaveplaza.view

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.webkit.JavascriptInterface
import androidx.media3.common.util.UnstableApi
import one.plaza.nightwaveplaza.Settings
import one.plaza.nightwaveplaza.helpers.Utils

@UnstableApi
class WebViewJavaScriptHandler(private val callback: WebViewCallback) {
    val context: Context = callback.getActivityContext()

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
        try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return pInfo.versionName + " (build " + pInfo.versionCode + ")"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return "Error"
        }
    }

    @JavascriptInterface
    fun setLanguage(lang: String) {
        callback.onSetLanguage(lang)
    }

    @JavascriptInterface
    fun onReady() {
        callback.onReady()
    }
}