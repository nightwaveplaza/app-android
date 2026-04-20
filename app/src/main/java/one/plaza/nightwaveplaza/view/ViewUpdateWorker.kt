package one.plaza.nightwaveplaza.view

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.api.ViewVersionConfig
import one.plaza.nightwaveplaza.extensions.calculateSha256
import one.plaza.nightwaveplaza.helpers.Utils
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * View update worker. Checks and downloads new version.
 */
class ViewUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val currentAppVersionCode = PackageInfoCompat.getLongVersionCode(pInfo).toInt()

        try {
            Timber.d("Checking new view version...")

            val client = ApiClient()
            val currentViewVersion = getLocalViewVersion(appContext)
            val manifest = client.getManifest()

            val targetConfig = manifest.versions
                .filter { it.minAndroid <= currentAppVersionCode }
                .maxByOrNull { it.viewVersion }

            if (targetConfig == null) {
                Timber.d("No compatible view version found for Android versionCode $currentAppVersionCode")
                return Result.success()
            }

            if (targetConfig.viewVersion > currentViewVersion) {
                Timber.d("Found new view version")

                processUpdatedViewConfig(targetConfig, appContext)

                Timber.d("View updated")
                return Result.success()
            } else {
                Timber.d("No updates")
                return Result.success()
            }

        } catch (e: Exception) {
            Timber.d("Update failed: %s", e.message)
            return Result.failure()
        }
    }

    /**
     * Download and unzip updated view version
     */
    private fun processUpdatedViewConfig(
        targetConfig: ViewVersionConfig,
        context: Context
    ) {
        val updatesDir = File(context.filesDir, "updates")
        val tempZipFile = File(context.filesDir, "temp_update.zip")
        val tempExtractDir = File(context.filesDir, "temp_extract_dir")

        // Download
        try {
            URL(targetConfig.viewSrc).openStream().use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Timber.e("Failed to download zip file: %s", e.message)
            throw Exception("Download failed.")
        }

        // Check hash
        val calculatedHash = tempZipFile.calculateSha256()
        if (!calculatedHash.equals(targetConfig.sha256, ignoreCase = true)) {
            tempZipFile.delete()
            Timber.e("Download hash mismatch")
            throw Exception("SHA mismatch")
        }

        // Clear temp directory
        if (tempExtractDir.exists()) {
            tempExtractDir.deleteRecursively()
        }
        tempExtractDir.mkdirs()

        // Unzip file
        unzip(tempZipFile, tempExtractDir)

        // Save new version into txt
        File(tempExtractDir, "version.txt").writeText(targetConfig.viewVersion.toString())

        // Remove old updates dir
        if (updatesDir.exists()) {
            updatesDir.deleteRecursively()
        }
        // and replace folder
        tempExtractDir.renameTo(updatesDir)

        tempZipFile.delete()
    }

    /**
     * Get current view version from local cache or from embedded
     */
    private fun getLocalViewVersion(context: Context): Int {
        val cachedVersion = Utils.getViewCachedVersion(context)
        val embeddedVersion = Utils.getViewEmbeddedVersion(context)
        return maxOf(cachedVersion, embeddedVersion)
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { fos -> zis.copyTo(fos) }
                }
            }
        }
    }
}