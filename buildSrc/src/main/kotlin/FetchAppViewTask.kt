import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException
import groovy.json.JsonSlurper
import java.net.URI
import java.security.MessageDigest
import java.io.File
import javax.inject.Inject

// Task that will download and embed view
@Suppress("UNCHECKED_CAST")
abstract class FetchAppViewTask @Inject constructor(
    private val fs: FileSystemOperations,
    private val archives: ArchiveOperations
) : DefaultTask() {

    @get:Input abstract val manifestUrl: Property<String>
    @get:Input abstract val bundleUrl: Property<String>
    @get:Input abstract val appVersionCode: Property<Int>
    @get:OutputDirectory abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun fetchView() {
        val outDir = assetsDir.get().asFile
        val zipFile = File(temporaryDir, "view.zip")

        if (bundleUrl.get().isNotEmpty()) {
            println(">>> [AppView] Downloading dev bundle...")
            downloadFile(bundleUrl.get(), zipFile)
            extractAndClean(zipFile, outDir)
            println(">>> [AppView] Dev bundle embedded.")
            return
        } else {
            println(">>> [AppView] Downloading production bundle...")
        }

        println(">>> [AppView] Fetching version manifest...")
        val jsonText = try {
            URI(manifestUrl.get()).toURL().readText()
        } catch (e: Exception) {
            println(">>> [AppView] Failed to fetch manifest. Skipping. ($e)")
            return
        }

        val manifest = JsonSlurper().parseText(jsonText) as Map<String, Any>
        val versions = manifest["versions"] as List<Map<String, Any>>

        val targetConfig = versions
            .filter { (it["min_android"] as Int) <= appVersionCode.get() }
            .maxByOrNull { it["view_version"] as Int }
            ?: run {
                println(">>> [AppView] No compatible view version found. Skipping.")
                return
            }

        val targetUrl = targetConfig["url"] as String
        val expectedHash = targetConfig["sha256"] as String

        println(">>> [AppView] Downloading view version ${targetConfig["view_version"]}...")
        downloadFile(targetUrl, zipFile)
        verifyHash(zipFile, expectedHash)
        extractAndClean(zipFile, outDir)
        println(">>> [AppView] Version ${targetConfig["view_version"]} successfully embedded.")
    }

    private fun downloadFile(url: String, dest: File) {
        try {
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            throw GradleException("Failed to download: $e")
        }
    }

    private fun verifyHash(file: File, expectedHash: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!calculatedHash.equals(expectedHash, ignoreCase = true)) {
            file.delete()
            throw GradleException("Hash mismatch! Expected: $expectedHash, Got: $calculatedHash")
        }
    }

    private fun extractAndClean(zipFile: File, outDir: File) {
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()
        fs.copy {
            from(archives.zipTree(zipFile))
            into(outDir)
        }
        zipFile.delete()
    }
}