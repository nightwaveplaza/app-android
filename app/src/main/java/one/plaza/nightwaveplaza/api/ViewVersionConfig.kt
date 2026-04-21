package one.plaza.nightwaveplaza.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ViewVersionConfig(
    @SerialName("min_android") val minAndroid: Int,
    @SerialName("view_version") val viewVersion: Int,
    @SerialName("url") val viewSrc: String,
    @SerialName("sha256") val sha256: String
)