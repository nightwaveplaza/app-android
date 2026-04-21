package one.plaza.nightwaveplaza.updater

import android.content.Context
import timber.log.Timber
import java.io.File

class WebAppAssetResolver(private val context: Context) {
    val updatesDir: File by lazy { File(context.filesDir, "updates") }

    /**
     * Determines the best URL to load.
     * Checks versions, cleans up old cache if necessary.
     */
    fun resolveEntryPointUrl(): String {
        val cachedVersion = WebAppVersionProvider.getDownloadedVersion(context)
        val embeddedVersion = WebAppVersionProvider.getBundledVersion(context)

        Timber.d("Version Check -> Embedded: $embeddedVersion vs Cached: $cachedVersion")

        return if (cachedVersion > embeddedVersion) {
            Timber.d("Selecting UPDATED version: v$cachedVersion")
            "https://appassets.androidplatform.net/updates/v$cachedVersion/index.html"
        } else {
            Timber.d("Selecting EMBEDDED version")
            "https://appassets.androidplatform.net/assets/www/index.html"
        }
    }

    fun performCleanup() {
        if (!updatesDir.exists()) return

        val cachedVersion = WebAppVersionProvider.getDownloadedVersion(context)
        val embeddedVersion = WebAppVersionProvider.getBundledVersion(context)

        val activeVersion = maxOf(cachedVersion, embeddedVersion)

        updatesDir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name.startsWith("v")) {
                val folderVersion = file.name.removePrefix("v").toIntOrNull()
                if (folderVersion == null || folderVersion < activeVersion || folderVersion <= embeddedVersion) {
                    try {
                        file.deleteRecursively()
                        Timber.d("Deleted stale update directory: ${file.name}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to delete old update directory: ${file.name}")
                    }
                }
            }
        }
    }
}