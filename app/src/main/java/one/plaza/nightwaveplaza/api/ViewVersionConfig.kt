package one.plaza.nightwaveplaza.api

import com.google.gson.annotations.SerializedName

data class ViewVersionConfig(
    @SerializedName("min_android") val minAndroid: Int,
    @SerializedName("view_version") val viewVersion: Int,
    @SerializedName("url") val viewSrc: String,
    @SerializedName("sha256") val sha256: String
)