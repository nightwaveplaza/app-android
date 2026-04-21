package one.plaza.nightwaveplaza.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebAppVersionConfig(
    @SerialName("min_android") val minAndroid: Int,
    @SerialName("view_version") val viewVersion: Int,
    @SerialName("url") val viewSrc: String,
    @SerialName("sha256") val sha256: String
)