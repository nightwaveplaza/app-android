package one.plaza.nightwaveplaza.helpers

import android.os.Build
import java.util.Locale


object Utils {
    fun getUserAgent(): String {
        return String.format(
            Locale.US,
            "%s/%s (Android: %s; %s %s; %s)",
            "NightwavePlaza",
            Build.VERSION.CODENAME,
            Build.MODEL,
            Build.BRAND,
            Build.DEVICE,
            Locale.getDefault().getLanguage()
        )
    }
}