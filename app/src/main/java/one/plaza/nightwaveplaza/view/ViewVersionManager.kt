package one.plaza.nightwaveplaza.view

import android.content.Context
import one.plaza.nightwaveplaza.helpers.Utils
import timber.log.Timber
import java.io.File

class ViewVersionManager(private val context: Context) {
    val updatesDir: File by lazy { File(context.filesDir, "updates") }

    /**
     * Determines the best URL to load.
     * Checks versions, cleans up old cache if necessary.
     */
    fun getStartUrl(): String {
        val cachedVersion = Utils.getViewCachedVersion(context)
        val embeddedVersion = Utils.getViewEmbeddedVersion(context)

        Timber.d("Version Check -> Embedded: $embeddedVersion vs Cached: $cachedVersion")

        return if (cachedVersion > embeddedVersion) {
            Timber.d("Selecting UPDATED version")
            "https://appassets.androidplatform.net/updates/index.html"
        } else {
            Timber.d("Selecting EMBEDDED version")
            // Remove cached version if embedded version newer after app update
            if (embeddedVersion > cachedVersion && updatesDir.exists()) {
                Timber.d("Newer embedded version found. Cleaning up old updates...")
                try {
                    updatesDir.deleteRecursively()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clean updates dir")
                }
            }
            "https://appassets.androidplatform.net/assets/www/index.html"
        }
    }
}