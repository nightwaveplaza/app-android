package one.plaza.nightwaveplaza

import android.app.Application
import one.plaza.nightwaveplaza.helpers.StorageHelper.initStorage
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender
import timber.log.Timber

class NightwavePlaza : Application() {

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        initStorage()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        if (BuildConfig.ACRA_URI.isNotEmpty() && !BuildConfig.DEBUG) {
            initAcra {
                buildConfigClass = BuildConfig::class.java
                reportFormat = StringFormat.JSON

                httpSender {
                    uri = BuildConfig.ACRA_URI
                    basicAuthLogin = BuildConfig.ACRA_LOGIN
                    basicAuthPassword = BuildConfig.ACRA_PASS
                    httpMethod = HttpSender.Method.POST
                }
            }
        }
    }
}
