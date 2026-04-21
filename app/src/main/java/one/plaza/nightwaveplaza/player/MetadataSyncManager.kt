package one.plaza.nightwaveplaza.player

import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.api.ApiClient
import timber.log.Timber

@UnstableApi
class MetadataSyncManager(
    private val player: Player,
    private val wrapper: LiveStreamWrapper,
    private val sessionBuilder: () -> MediaLibraryService.MediaLibrarySession? // Lazy getting session
) {
    private val mutex = Mutex()

    fun fetchAndUpdate(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val status = ApiClient.getStatus()
                    if (status.song.id.isEmpty()) return@runCatching

                    withContext(Dispatchers.Main) {
                        val serverTimeMs = status.updatedAt * 1000L
                        val networkDelayMs = System.currentTimeMillis() - serverTimeMs
                        val safeDelay = if (networkDelayMs > 0) networkDelayMs else 0L

                        wrapper.positionTracker.apply {
                            position = (status.position * 1000L) + safeDelay
                            length = status.song.length * 1000L
                            localUpdatedAt = SystemClock.elapsedRealtime()
                        }

                        updateMediaItem(status.song)
                        forceSessionUpdate()
                    }
                }.onFailure { Timber.e(it, "API sync failed") }
            }
        }
    }

    private fun updateMediaItem(song: one.plaza.nightwaveplaza.api.Song) {
        val currentItem = player.currentMediaItem ?: return
        if (song.artist == currentItem.mediaMetadata.artist && song.title == currentItem.mediaMetadata.title) return

        val updatedMetadata = MediaMetadata.Builder()
            .setArtist(song.artist)
            .setTitle(song.title)
            .setArtworkUri(song.artworkSrc.toUri())
            .setDurationMs(song.length * 1000L)
            .build()

        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(updatedMetadata)
            .build()

        player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
    }

    private fun forceSessionUpdate() {
        val session = sessionBuilder() ?: return
        session.connectedControllers.forEach { session.setCustomLayout(it, emptyList()) }
    }
}