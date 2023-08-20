package one.plaza.nightwaveplaza.api

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import one.plaza.nightwaveplaza.helpers.Utils

class ApiClient {
    @Keep data class Status(
        val song: Song = Song(),
        val listeners: Int = 0
    )

    @Keep data class Song(
        var id: String = "",
        var artist: String = "",
        var title: String = "",
        var album: String = "",
        var position: Int = 0,
        var length: Int = 0,

        @SerializedName("artwork_src")
        var artworkSrc: String = "",
        var reactions: Int = 0,
        var updatedAt: Long = 0L
    )

    private val baseUrl = "https://api.plaza.one/"

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
}