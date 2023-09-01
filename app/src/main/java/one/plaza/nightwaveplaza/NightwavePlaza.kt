package one.plaza.nightwaveplaza

import android.app.Application
import com.facebook.drawee.backends.pipeline.Fresco
import one.plaza.nightwaveplaza.helpers.StorageHelper.initStorage
import org.acra.config.httpSender
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.HttpSender

class NightwavePlaza : Application() {

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        Fresco.initialize(this)
        initStorage()

        if (BuildConfig.ACRA_URI.isNotEmpty()) {
            initAcra {
                buildConfigClass = BuildConfig::class.java
                reportFormat = StringFormat.JSON


                httpSender {
                    uri = "https://acra.plaza.one/report"
                    basicAuthLogin = "cEPWFhhJCQNoRRSu"
                    basicAuthPassword = "gxezd2UjZ7aNuKj7"
                    httpMethod = HttpSender.Method.POST
                }
            }
        }
    }
}
