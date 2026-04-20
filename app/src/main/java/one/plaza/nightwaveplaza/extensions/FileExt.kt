package one.plaza.nightwaveplaza.extensions

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun File.calculateSha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    FileInputStream(this).use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}