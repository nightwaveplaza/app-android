package one.plaza.nightwaveplaza.extensions

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import one.plaza.nightwaveplaza.Settings
import one.plaza.nightwaveplaza.helpers.Keys


fun MediaController.play(context: Context) {
    if (isPlaying) pause()
    // set media item, prepare and play
    setMediaItem(prepareUri())
    prepare()
    play()
}

fun prepareUri(): MediaItem {
    val uriMp3 = "https://radio.plaza.one/mp3"
    val uriMp3Low = "https://radio.plaza.one/mp3_96"

    val streamUrl = if (Settings.lowQualityAudio) uriMp3Low else uriMp3

    val requestMetadata = MediaItem.RequestMetadata.Builder().apply {
        setMediaUri(Uri.parse(streamUrl))
    }.build()
    // build MediaMetadata
    val mediaMetadata = MediaMetadata.Builder().apply {
        setIsBrowsable(false)
        setIsPlayable(true)
    }.build()
    // build MediaItem and return it
    return MediaItem.Builder().apply {
        setRequestMetadata(requestMetadata)
        setMediaMetadata(mediaMetadata)
        setUri(Uri.parse("https://radio.plaza.one/mp3"))
    }.build()
}

fun MediaController.setSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.SET_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}