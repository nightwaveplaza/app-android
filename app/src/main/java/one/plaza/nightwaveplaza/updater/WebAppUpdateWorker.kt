package one.plaza.nightwaveplaza.updater

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.Request
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.extensions.calculateSha256
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * View update worker. Checks and downloads new version.
 */
class WebAppUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val currentAppVersionCode = PackageInfoCompat.getLongVersionCode(pInfo).toInt()

        try {
            Timber.d("Checking new view version...")

            val currentViewVersion = getLocalViewVersion(appContext)
            val manifest = ApiClient.getManifest()

            val targetConfig = manifest.versions
                .filter { it.minAndroid <= currentAppVersionCode }
                .maxByOrNull { it.viewVersion }

            if (targetConfig == null) {
                Timber.d("No compatible view version found for Android versionCode $currentAppVersionCode")
                return Result.success()
            }

            if (targetConfig.viewVersion > currentViewVersion) {
                Timber.d("Found new view version")

                downloadAndExtract(targetConfig, appContext)

                Timber.d("View updated")
                return Result.success()
            } else {
                Timber.d("No updates")
                return Result.success()
            }

        } catch (e: Exception) {
            Timber.d("Update failed: %s", e.message)
            return Result.retry()
        }
    }

    /**
     * Download and unzip updated view version
     */
    private fun downloadAndExtract(
        targetConfig: WebAppVersionConfig,
        context: Context
    ) {
        val updatesBaseDir = File(context.filesDir, "updates")
        val targetDir = File(updatesBaseDir, "v${targetConfig.viewVersion}")
        val tempZipFile = File(context.filesDir, "temp_update.zip")

        // Download
        val request = Request.Builder().url(targetConfig.viewSrc).build()
        ApiClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download: $response")
            FileOutputStream(tempZipFile).use { output ->
                response.body.byteStream().copyTo(output)
            }
        }

        // Check hash
        val calculatedHash = tempZipFile.calculateSha256()
        if (!calculatedHash.equals(targetConfig.sha256, ignoreCase = true)) {
            tempZipFile.delete()
            Timber.e("Download hash mismatch")
            throw Exception("SHA mismatch")
        }

        // Clear temp directory
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        // Unzip file
        try {
            unzip(tempZipFile, targetDir)
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        } finally {
            tempZipFile.delete()
        }
    }

    /**
     * Get current view version from local cache or from embedded
     */
    private fun getLocalViewVersion(context: Context): Int {
        return maxOf(
            WebAppVersionProvider.getDownloadedVersion(context),
            WebAppVersionProvider.getBundledVersion(context)
        )
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