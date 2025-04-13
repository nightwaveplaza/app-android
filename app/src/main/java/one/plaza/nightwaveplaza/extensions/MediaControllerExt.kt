package one.plaza.nightwaveplaza.extensions

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import one.plaza.nightwaveplaza.helpers.Keys


fun MediaController.play(context: Context) {
    if (isPlaying) pause()
    // set media item, prepare and play
    setMediaItem(prepareUri())
    prepare()
    play()
}

fun prepareUri(): MediaItem {
    return MediaItem.Builder().setUri("https://radio.plaza.one/hls.m3u8").setLiveConfiguration(
        MediaItem.LiveConfiguration.Builder().setMaxOffsetMs(5000).build()
    ).build()
}

fun MediaController.setSleepTimer(time: Long) {
    var b = Bundle()
    b.putLong("time", time)
    sendCustomCommand(SessionCommand(Keys.SET_TIMER, Bundle.EMPTY), b)
}