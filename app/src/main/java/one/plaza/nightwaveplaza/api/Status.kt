package one.plaza.nightwaveplaza.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Status(
    val song: Song = Song(),
    val listeners: Int = 0,
    val reactions: Int = 0,
    val position: Int = 0,
    @SerialName("updated_at")
    val updatedAt: Long = 0L
)