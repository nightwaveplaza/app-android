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
    return MediaItem.fromUri("https://radio.plaza.one/hls.m3u8")
}

fun MediaController.setSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.SET_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}