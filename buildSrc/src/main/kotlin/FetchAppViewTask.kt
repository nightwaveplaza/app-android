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

    @get:Input
    abstract val manifestUrl: Property<String>

    @get:Input
    abstract val appVersionCode: Property<Int>

    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @TaskAction
    fun fetchView() {
        val outDir = assetsDir.get().asFile
        val versionFile = File(outDir, "version.txt")
        val zipFile = File(temporaryDir, "view.zip")

        println(">>> [AppView] Fetching version manifest...")

        val jsonText = try {
            URI(manifestUrl.get()).toURL().readText()
        } catch (e: Exception) {
            println(">>> [AppView] Failed to fetch manifest. Network issue? Skipping. ($e)")
            return
        }

        val manifest = JsonSlurper().parseText(jsonText) as Map<String, Any>
        val versions = manifest["versions"] as List<Map<String, Any>>

        val targetConfig = versions
            .filter { (it["min_android"] as Int) <= appVersionCode.get() }
            .maxByOrNull { it["view_version"] as Int }

        if (targetConfig == null) {
            println(">>> [AppView] No compatible view version found. Skipping.")
            return
        }

        val targetViewVersion = targetConfig["view_version"] as Int
        val targetUrl = targetConfig["url"] as String
        val expectedHash = targetConfig["sha256"] as String

        if (versionFile.exists()) {
            val currentVersion = versionFile.readText().trim().toIntOrNull() ?: 0
            if (currentVersion >= targetViewVersion) {
                println(">>> [AppView] Local view ($currentVersion) is up to date. Skip downloading.")
                return
            }
        }

        println(">>> [AppView] Downloading view version $targetViewVersion...")

        try {
            URI(targetUrl).toURL().openStream().use { input ->
                zipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw GradleException("Failed to download AppView: $e")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        zipFile.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }

        if (!calculatedHash.equals(expectedHash, ignoreCase = true)) {
            zipFile.delete()
            throw GradleException("Hash mismatch! Expected: $expectedHash, Got: $calculatedHash")
        }

        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()

        fs.copy {
            from(archives.zipTree(zipFile))
            into(outDir)
        }

        zipFile.delete()
        versionFile.writeText(targetViewVersion.toString())
        println(">>> [AppView] Version $targetViewVersion successfully embedded.")
    }
}