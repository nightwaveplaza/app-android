package one.plaza.nightwaveplaza.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebAppUpdateManifest(
    @SerialName("versions") val versions: List<WebAppVersionConfig>
)