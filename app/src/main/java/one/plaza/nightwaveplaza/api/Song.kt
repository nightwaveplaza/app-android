package one.plaza.nightwaveplaza.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String = "",
    val artist: String = "",
    val album: String = "",
    val title: String = "",
    val length: Int = 0,
    @SerialName("artwork_src")
    val artworkSrc: String = "",
    @SerialName("artwork_sm_src")
    val artworkSmSrc: String = "",
    @SerialName("preview_src")
    val previewSrc: String = ""
)