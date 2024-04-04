package one.plaza.nightwaveplaza

import android.webkit.JavascriptInterface
import androidx.media3.common.util.UnstableApi
import one.plaza.nightwaveplaza.helpers.Utils

@UnstableApi
class WebAppInterface(private val activity: MainActivity) {

    @JavascriptInterface
    fun showToast(message: String) {
        activity.runOnUiThread {
            activity.makeToast(message)
        }
    }

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
    fun getAudioQuality(): Boolean {
        return Settings.lowQualityAudio
    }

    @JavascriptInterface
    fun setAudioQuality(lowQuality: Boolean) {
        Settings.lowQualityAudio = lowQuality
        activity.runOnUiThread {
            activity.setAudioQuality(lowQuality)
        }
    }

    @JavascriptInterface
    fun setSleepTimer(minutes: Int) {
        activity.runOnUiThread {
            activity.setSleepTimer(minutes)
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
}