package one.plaza.nightwaveplaza.api

import com.google.gson.annotations.SerializedName

data class UpdateManifest(
    @SerializedName("versions") val versions: List<ViewVersionConfig>
)
