package one.plaza.nightwaveplaza.helpers

import android.net.Uri
import androidx.media3.common.MediaMetadata
import com.google.gson.Gson
import one.plaza.nightwaveplaza.api.ApiClient

object SongHelper {
    fun setSong(song: ApiClient.Song) {
        song.updatedAt = System.currentTimeMillis()
        StorageHelper.save("status_song", Gson().toJson(song, ApiClient.Song::class.java))
    }

    fun getSong(): ApiClient.Song {
        val json = StorageHelper.load("status_song", "{}")
        return if (json == null) ApiClient.Song() else Gson().fromJson(
            json, ApiClient.Song::class.java
        )
    }

    fun isOutdated(): Boolean {
        val song = getSong()
        return System.currentTimeMillis() - song.updatedAt > ((song.length - song.position - 10) * 1000L)
    }

    fun getSongAsMetadata(): MediaMetadata {
        val song = getSong()
        return MediaMetadata.Builder().apply {
            setIsBrowsable(false)
            setIsPlayable(true)
            setArtist(song.artist)
            setTitle(song.title)
            setArtworkUri(Uri.parse(song.artworkSrc))
        }.build()
    }
}