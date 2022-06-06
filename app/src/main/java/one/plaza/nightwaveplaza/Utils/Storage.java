package one.plaza.nightwaveplaza.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class Storage {
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(context.getPackageName() + ".storage.v3", Context.MODE_PRIVATE);
    }

    public static void set(String name, String value, Context context) {
        getPrefs(context).edit().putString(name, value).apply();
    }

    public static String get(String name, String def, Context context) {
        return getPrefs(context).getString(name, def);
    }

    public static void set(String name, int value, Context context) {
        getPrefs(context).edit().putInt(name, value).apply();
    }

    public static int get(String name, int def, Context context) {
        return getPrefs(context).getInt(name, def);
    }

    public static void set(String name, long value, Context context) {
        getPrefs(context).edit().putLong(name, value).apply();
    }

    public static long get(String name, long def, Context context) {
        return getPrefs(context).getLong(name, def);
    }

    public static void set(String name, boolean value, Context context) {
        getPrefs(context).edit().putBoolean(name, value).apply();
    }

    public static boolean get(String name, boolean def, Context context) {
        return getPrefs(context).getBoolean(name, def);
    }

    public static void setCrypto(String name, String value, Context context) {
        String val = Utils.xorEncrypt(value);
        getPrefs(context).edit().putString(name, val).apply();
    }

    public static String getCrypto(String name, String def, Context context) {
        String value = getPrefs(context).getString(name, def);
        return Utils.xorDecrypt(value);
    }

    public static void clearPreviousPreferences(Context context) {
        SharedPreferences shPf = PreferenceManager.getDefaultSharedPreferences(context);
        shPf.edit().clear().apply();

        shPf = context.getSharedPreferences(context.getPackageName() + ".storage", Context.MODE_PRIVATE);
        shPf.edit().clear().apply();
    }
}
