package one.plaza.nightwaveplaza.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import one.plaza.nightwaveplaza.BuildConfig
import one.plaza.nightwaveplaza.helpers.Utils
import timber.log.Timber

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = BuildConfig.PLAZA_API

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", Utils.getUserAgent())
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private suspend inline fun <reified T> fetch(url: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val responseBody: String

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("HTTP ${response.code}: ${response.message}")
                responseBody = response.body.string()
            }
        } catch (e: IOException) {
            Timber.e(e, "Network execution failed for $url")
            throw ApiException("Network exception", e)
        }

        try {
            json.decodeFromString<T>(responseBody)
        } catch (e: Exception) {
            Timber.e(e, "JSON parse failed for $url")
            throw ApiException("Status parse error", e)
        }
    }

    class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

    @Throws(ApiException::class)
    suspend fun getStatus(): Status =
        fetch("$baseUrl/status")

    @Throws(ApiException::class)
    suspend fun getManifest(): UpdateManifest =
        fetch("https://akai.plaza.one/app-view/update-manifest.json")
}