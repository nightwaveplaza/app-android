package one.plaza.nightwaveplaza.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    @SerialName("versions") val versions: List<ViewVersionConfig>
)