package one.plaza.nightwaveplaza.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object StorageHelper {
    @PublishedApi
    internal lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(
            "${appContext.packageName}_preferences",
            Context.MODE_PRIVATE
        )
    }

    inline fun <reified T> save(key: String, value: T) {
        prefs.edit {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                else -> throw IllegalArgumentException("Type ${T::class.java.simpleName} is not supported by SharedPreferences")
            }
        }
    }

    inline fun <reified T> load(key: String, default: T): T {
        return when (default) {
            is String -> prefs.getString(key, default) as T
            is Int -> prefs.getInt(key, default) as T
            is Long -> prefs.getLong(key, default) as T
            is Boolean -> prefs.getBoolean(key, default) as T
            is Float -> prefs.getFloat(key, default) as T
            else -> throw IllegalArgumentException("Type ${T::class.java.simpleName} is not supported by SharedPreferences")
        }
    }
}