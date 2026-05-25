package one.plaza.nightwaveplaza

import one.plaza.nightwaveplaza.helpers.StorageHelper
import java.util.Locale
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

inline fun <reified T> preference(
    key: String,
    crossinline defaultValueProvider: () -> T
): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        // Evaluates the lambda on every read if the key is missing
        return StorageHelper.load(key, defaultValueProvider())
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        StorageHelper.save(key, value)
    }
}

object Settings {
    var isPlaying       by preference("IsPlaying") { false }
    var sleepTargetTime by preference("sleepTargetTimer") { 0L }
    var userToken       by preference("UserToken") { "" }
    var fullScreen      by preference("Fullscreen") { false }
    var lowQualityAudio by preference("AudioLowQuality") { false }
    var language        by preference("Language") { Locale.getDefault().language }
    var themeColor      by preference("ThemeColor") { "#c0c0c0" }
}