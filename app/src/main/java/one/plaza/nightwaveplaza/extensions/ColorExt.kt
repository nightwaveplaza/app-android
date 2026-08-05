package one.plaza.nightwaveplaza.extensions

import androidx.core.graphics.toColorInt
import timber.log.Timber

private val HEX_ONLY = Regex("[0-9a-fA-F]+")

// parseColor() throws on anything but #RRGGBB, #AARRGGBB and a few color names
// alpha forms are left as is: css writes alpha last, android reads it first
fun String.toColorIntOrNull(): Int? {
    val value = trim()
    if (value.isEmpty()) return null

    val hex = value.removePrefix("#")
    val normalized = when {
        !hex.matches(HEX_ONLY) -> value
        hex.length == 3 -> hex.map { "$it$it" }.joinToString("", prefix = "#")
        hex.length == 6 -> "#$hex"
        else -> value
    }

    return try {
        normalized.toColorInt()
    } catch (_: IllegalArgumentException) {
        Timber.w("Unsupported color from web app: $this")
        null
    }
}
