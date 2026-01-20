package one.plaza.nightwaveplaza.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.edit

object StorageHelper {
    private lateinit var sharedPreferences: SharedPreferences

    fun Context.initStorage() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    // Save-load strings
    fun save(name: String, value: String) {
        sharedPreferences.edit { putString(name, value) }
    }

    fun load(name: String, def: String): String {
        return sharedPreferences.getString(name, def) ?: def
    }

    // Save-load int
    fun save(name: String, value: Int) {
        sharedPreferences.edit { putInt(name, value) }
    }

    fun load(name: String, def: Int): Int {
        return sharedPreferences.getInt(name, def)
    }

    // Save-load Long
    fun save(name: String, value: Long) {
        sharedPreferences.edit { putLong(name, value) }
    }

    fun load(name: String, def: Long): Long {
        return sharedPreferences.getLong(name, def)
    }

    // Save-load boolean
    fun save(name: String, value: Boolean) {
        sharedPreferences.edit { putBoolean(name, value) }
    }

    fun load(name: String, def: Boolean): Boolean {
        return sharedPreferences.getBoolean(name, def)
    }
}
