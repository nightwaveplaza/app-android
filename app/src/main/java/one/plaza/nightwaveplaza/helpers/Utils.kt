package one.plaza.nightwaveplaza.helpers

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.io.File
import java.util.Locale


object Utils {
    fun getUserAgent(): String {
        return String.format(
            Locale.US,
            "%s/%s (Android: %s; %s %s; %s)",
            "NightwavePlaza",
            Build.VERSION.CODENAME,
            Build.MODEL,
            Build.BRAND,
            Build.DEVICE,
            Locale.getDefault().getLanguage()
        )
    }

    /**
     * Reads version from internal storage (updates/version.txt)
     */
    fun getViewCachedVersion(context: Context): Int {
        val updatesDir = File(context.filesDir, "updates")
        val versionFile = File(updatesDir, "version.txt")
        if (versionFile.exists()) {
            try {
                val version = versionFile.readText()
                return Integer.parseInt(version)
            } catch (e: Exception) {
                Timber.e("Failed to get view cached version: %s", e.message)
            }
        }

        return 0
    }

    /**
     * Reads version from APK assets (assets/www/version.txt)
     */
    fun getViewEmbeddedVersion(context: Context): Int {
        try {
            context.assets.open("www/version.txt").use { inputStream ->
                val version = inputStream.bufferedReader().readText()
                return Integer.parseInt(version)
            }
        } catch (e: Exception) {
            Timber.e("Failed to get view embedded version: %s", e.message)
        }

        return 0
    }
}