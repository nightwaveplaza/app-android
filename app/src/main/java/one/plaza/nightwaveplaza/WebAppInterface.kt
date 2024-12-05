package one.plaza.nightwaveplaza

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.webkit.JavascriptInterface
import androidx.media3.common.util.UnstableApi
import one.plaza.nightwaveplaza.helpers.Utils

@UnstableApi
class WebAppInterface(private val activity: MainActivity) {
    @JavascriptInterface
    fun openDrawer() {
        activity.runOnUiThread {
            activity.openDrawer()
        }
    }

    @JavascriptInterface
    fun audioPlay() {
        activity.runOnUiThread {
            activity.play()
        }
    }

    @JavascriptInterface
    fun setBackground(backgroundSrc: String) {
        activity.runOnUiThread {
            activity.setBackground(backgroundSrc)
        }
    }

    @JavascriptInterface
    fun toggleFullscreen() {
        activity.runOnUiThread {
            activity.toggleFullscreen()
        }
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
        activity.runOnUiThread {
            activity.setSleepTimer(timestamp)
        }
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
            val pInfo: PackageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            return pInfo.versionName + " (build " + pInfo.versionCode + ")"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return "Error"
        }
    }

    @JavascriptInterface
    fun setLanguage(lang: String) {
        activity.runOnUiThread {
            activity.setLanguage(lang)
        }
    }
}
