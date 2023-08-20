package one.plaza.nightwaveplaza

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.helpers.SongHelper

// Credit: https://github.com/androidx/media/issues/407
//         https://github.com/androidx/media/issues/258#issuecomment-1453449978
@UnstableApi class MetadataForwardingPlayer(wrapped: Player) : ForwardingPlayer(wrapped) {

    private val fromPlayerListener: Listener = object : Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            fetchNewMetadata()
        }
        override fun onMetadata(metadata: Metadata) = Unit
    }

    private val listenerSet: MutableSet<Listener> = mutableSetOf()

    init {
        wrapped.addListener(fromPlayerListener)
    }

    // Credit https://github.com/androidx/media/issues/265#issuecomment-1465266989
    override fun getDuration(): Long {
        return C.TIME_UNSET
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .remove(COMMAND_SEEK_BACK)
            .remove(COMMAND_SEEK_FORWARD)
            .remove(COMMAND_SEEK_TO_PREVIOUS)
            .remove(COMMAND_SEEK_TO_NEXT)
            .build()
    }
    // end Credit

    override fun getMediaMetadata(): MediaMetadata {
        return SongHelper.getSongAsMetadata()
    }

    override fun addListener(listener: Listener) {
        super.addListener(listener.also { listenerSet.add(it) })
    }

    override fun removeListener(listener: Listener) {
        super.removeListener(listener.also { listenerSet.remove(it) })
    }

    private fun updateMetadata() {
        listenerSet.forEach { it.onMediaMetadataChanged(SongHelper.getSongAsMetadata()) }
    }

    private var fetchingMetadata = false

    fun fetchNewMetadata() {
        updateMetadata()

        if (fetchingMetadata) return

        val currentSong = SongHelper.getSong()
        if (!SongHelper.isOutdated()) {
            println("updating: not yet")
            return
        }

        fetchingMetadata = true
        CoroutineScope(Dispatchers.Default).launch(Dispatchers.IO) {
            val client = ApiClient()
            var song = ApiClient.Song()
            var updated = false
            var sameSongCounter = 0

            while (!updated && sameSongCounter < 3) {
                try {
                    val status = client.getStatus()

                    if (status.song.id != currentSong.id) {
                        println("updating: new song")
                        song = status.song
                        updated = true
                    } else {
                        println("updating: same song, delay")
                        sameSongCounter++
                        delay(5000)
                    }
                } catch (err: Exception) {
                    println("updating: network exception")
                    delay(15000)
                    continue
                }
            }

            if (song.id.isNotEmpty() && song.id != currentSong.id) {
                SongHelper.setSong(song)
                withContext(Dispatchers.Main) {
                    updateMetadata()
                }
            }

            fetchingMetadata = false
        }
    }
}
