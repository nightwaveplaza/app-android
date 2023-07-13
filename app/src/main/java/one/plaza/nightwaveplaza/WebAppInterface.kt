package one.plaza.nightwaveplaza

import android.webkit.JavascriptInterface
import androidx.media3.common.util.UnstableApi
import one.plaza.nightwaveplaza.helpers.PrefKeys
import one.plaza.nightwaveplaza.helpers.StorageHelper
import one.plaza.nightwaveplaza.helpers.UserHelper
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
    fun setBackground(backgroundSrc: String?) {
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
    fun getAuthToken(): String? {
        return UserHelper.getToken()
    }

    @JavascriptInterface
    fun setAuthToken(token: String) {
//        if (token.isEmpty()) {
//            activity.pushViewData("reactionUpdate", Reaction.getAsJson(mActivity))
//        }
        UserHelper.setToken(token)
    }

    @JavascriptInterface
    fun getUserAgent(): String {
        return Utils.getUserAgent()
    }

    @JavascriptInterface
    fun getAudioQuality(): Int {
        return StorageHelper.load(PrefKeys.AUDIO_QUALITY, 0)
    }

    @JavascriptInterface
    fun setAudioQuality(lowQuality: Int) {
        StorageHelper.save(PrefKeys.AUDIO_QUALITY, lowQuality)
        activity.runOnUiThread {
            activity.setAudioQuality(lowQuality)
        }
    }
}