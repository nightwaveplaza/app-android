package one.plaza.nightwaveplaza.Ui;


import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;

import one.plaza.nightwaveplaza.Entities.Reaction;
import one.plaza.nightwaveplaza.Entities.User;
import one.plaza.nightwaveplaza.MainActivity;
import one.plaza.nightwaveplaza.Utils.PrefKeys;
import one.plaza.nightwaveplaza.Utils.Storage;
import one.plaza.nightwaveplaza.Utils.Utils;

/**
 * WebApp Interface
 */
public class WebAppInterface {
    private final MainActivity mActivity;

    public WebAppInterface(MainActivity activity) {
        mActivity = activity;
    }

    @JavascriptInterface
    public void showToast(String message) {
        mActivity.makeToast(message);
    }

    @JavascriptInterface
    public void openDrawer() {
        mActivity.openDrawer();
    }

    @JavascriptInterface
    public String getStatus() {
        return mActivity.getStatus();
    }

    @JavascriptInterface
    public void requestUiUpdate() {
        mActivity.requestUiUpdate();
    }

    @JavascriptInterface
    public String getVersion() {
        try {
            PackageInfo pInfo = mActivity.getPackageManager().getPackageInfo(mActivity.getPackageName(), 0);
            return pInfo.versionName + " (build " + pInfo.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "Error";
        }
    }

    @JavascriptInterface
    public String getUserAgent() {
        return Utils.getUserAgent();
    }

    /**
     * Player controls
     */

    @JavascriptInterface
    public void audioPlay() {
        mActivity.playAudio();
    }

    @JavascriptInterface
    public void audioStop() {
        mActivity.stopAudio();
    }

    @JavascriptInterface
    public void setSleepTimer(String time) {
        mActivity.setSleepTimer(Integer.parseInt(time));
    }

    /**
     * Backgrounds
     */
    @JavascriptInterface
    public void setBackground(String backgroundSrc) {
        mActivity.setBackground(backgroundSrc);
    }

    @JavascriptInterface
    public void toggleFullscreen() {
        mActivity.toggleFullscreen();
    }

    /**
     * Audio quality
     */

    @JavascriptInterface
    public int getAudioQuality() {
        return Storage.get(PrefKeys.AUDIO_QUALITY, 0, mActivity);
    }

    @JavascriptInterface
    public void setAudioQuality(int lowQuality) {
        Storage.set(PrefKeys.AUDIO_QUALITY, lowQuality, mActivity);
        mActivity.setAudioQuality(lowQuality);
    }

    /**
     * User
     */

    @JavascriptInterface
    public String getAuthToken() {
        return User.getToken(mActivity);
    }

    @JavascriptInterface
    public void setAuthToken(String token) {
        if (token.isEmpty()) {
            Reaction.clear(mActivity);
            mActivity.pushViewData("reactionUpdate", Reaction.getAsJson(mActivity));
        }
        User.setToken(token, mActivity);
    }

    /**
     * Like
     */

    @JavascriptInterface
    public void setReaction(final int score) {
        mActivity.reactionUpdate(score);
    }

    @JavascriptInterface
    public String getReaction() {
        return Reaction.getAsJson(mActivity);
    }
}