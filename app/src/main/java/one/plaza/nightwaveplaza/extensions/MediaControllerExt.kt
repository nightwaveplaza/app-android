package one.plaza.nightwaveplaza.extensions

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import one.plaza.nightwaveplaza.helpers.PrefKeys
import one.plaza.nightwaveplaza.helpers.StorageHelper

const val URI_MP3 = "https://radio.plaza.one/mp3"
const val URI_MP3_LOW = "https://radio.plaza.one/mp3_96"

fun MediaController.play(context: Context) {
    if (isPlaying) pause()
    // set media item, prepare and play
    setMediaItem(prepareUri())
    prepare()
    play()
}

fun prepareUri(): MediaItem {
    val lowQuality = StorageHelper.load(PrefKeys.AUDIO_QUALITY, 0) == 1
    val streamUrl = if (lowQuality) URI_MP3 else URI_MP3_LOW

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