package one.plaza.nightwaveplaza

import one.plaza.nightwaveplaza.helpers.StorageHelper

object Settings {
    private const val IS_PLAYING = "IsPlaying"
    private const val SLEEP_TIME = "sleepTimer"
    private const val USER_TOKEN = "UserToken"
    private const val FULLSCREEN = "Fullscreen"
    private const val AUDIO_LQ = "AudioLowQuality"
    private const val VIEW_SRC_URI = "ViewVersion"

    var isPlaying: Boolean = false
        set(v) {
            StorageHelper.save(IS_PLAYING, v)
            field = v
        }
        get() = StorageHelper.load(IS_PLAYING, false)

    var sleepTime: Long = 0L
        set(v) {
            StorageHelper.save(SLEEP_TIME, v)
            field = v
        }
        get() = StorageHelper.load(SLEEP_TIME, 0L)

    var userToken: String = ""
        set(v) {
            StorageHelper.save(USER_TOKEN, v)
            field = v
        }
        get() = StorageHelper.load(USER_TOKEN, "")

    var fullScreen: Boolean = false
        set(v) {
            StorageHelper.save(FULLSCREEN, v)
            field = v
        }
        get() = StorageHelper.load(FULLSCREEN, false)

    var lowQualityAudio: Boolean = false
        set(v) {
            StorageHelper.save(AUDIO_LQ, v)
            field = v
        }
        get() = StorageHelper.load(AUDIO_LQ, false)

    var viewUri: String = ""
        set(v) {
            StorageHelper.save(VIEW_SRC_URI, v)
            field = v
        }
        get() = StorageHelper.load(VIEW_SRC_URI, "")
}
