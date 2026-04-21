package one.plaza.nightwaveplaza.updater

import android.content.Context
import timber.log.Timber
import java.io.File

object WebAppVersionProvider {

    /**
     * Find the latest updated version
     */
    fun getDownloadedVersion(context: Context): Int {
        val updatesBaseDir = File(context.filesDir, "updates")
        if (!updatesBaseDir.exists()) return 0

        return updatesBaseDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("v") }
            ?.mapNotNull { it.name.removePrefix("v").toIntOrNull() }
            ?.maxOrNull() ?: 0
    }

    /**
     * Reads version from APK assets (assets/www/version.txt)
     */
    fun getBundledVersion(context: Context): Int {
        return try {
            context.assets.open("www/version.txt").use { inputStream ->
                inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get view embedded version")
            0
        }
    }
}