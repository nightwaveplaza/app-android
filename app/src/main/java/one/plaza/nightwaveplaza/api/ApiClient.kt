package one.plaza.nightwaveplaza.api

import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import one.plaza.nightwaveplaza.BuildConfig
import one.plaza.nightwaveplaza.helpers.Utils

class ApiClient {
    @Keep data class Status(
        val song: Song = Song(),
        val listeners: Int = 0,
        val reactions: Int = 0,
        val position: Int = 0,
        @SerializedName("updated_at")
        val updatedAt: Long = 0L
    )

    @Keep data class Song(
        val id: String = "",
        val artist: String = "",
        val album: String = "",
        val title: String = "",
        val length: Int = 0,
        @SerializedName("artwork_src")
        val artworkSrc: String = "",
        @SerializedName("artwork_sm_src")
        val artworkSmSrc: String = "",
        @SerializedName("preview_src")
        val previewSrc: String = ""
    )

    @Keep data class Version(
        @SerializedName("view_version")
        var viewVersion: Int = 0,
        @SerializedName("android_min_ver")
        var androidMinVersion: Int = 0,
        @SerializedName("ios_min_ver")
        var iOsMinVersion: Int = 0,
        @SerializedName("view_src")
        var viewSrc: String = ""
    )

    private val baseUrl = "https://api.plaza.one/v2"

    private var client: OkHttpClient = createClient()

    private fun createClient(): OkHttpClient {
        val client: OkHttpClient.Builder = OkHttpClient.Builder()
        val headerAuthorizationInterceptor = Interceptor { chain: Interceptor.Chain ->
            var request = chain.request()
            val headers: Headers = request.headers.newBuilder().add(
                "User-Agent", Utils.getUserAgent()
            ).build()
            request = request.newBuilder().headers(headers).build()
            chain.proceed(request)
        }
        client.addInterceptor(headerAuthorizationInterceptor)
        return client.build()
    }

    /**
     * Make okhttp request
     */
    @Throws(Exception::class)
    fun getStatus(): Status {
        val request: Request = Request.Builder().url("$baseUrl/status").build()
        var resp: String?
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Network error:" + " ${response.code} ${response.message}")
                }
                resp = response.body?.string()
            }
        } catch (err: IOException) {
            throw Exception("Network exception")
        }

        try {
            return Gson().fromJson(resp, Status::class.java)
        } catch (err: JsonSyntaxException) {
            throw Exception("Status parse error")
        }
    }

    @Throws(Exception::class)
    fun getVersion(): Version {
        val request: Request = Request.Builder().url("$baseUrl/versions/?platform=android&app_ver=" + BuildConfig.VERSION_CODE).build()
        var resp: String?
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Network error:" + " ${response.code} ${response.message}")
                }
                resp = response.body?.string()
            }
        } catch (err: IOException) {
            Log.e(ApiClient::class.toString(), err.message.toString())
            throw Exception("Network exception")
        }

        try {
            return Gson().fromJson(resp, Version::class.java)
        } catch (err: JsonSyntaxException) {
            throw Exception("Status parse error")
        }
    }


}