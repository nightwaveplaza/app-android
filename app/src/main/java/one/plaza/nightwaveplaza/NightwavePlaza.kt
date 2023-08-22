package one.plaza.nightwaveplaza

import android.app.Application
import com.facebook.drawee.backends.pipeline.Fresco
import one.plaza.nightwaveplaza.helpers.StorageHelper.initStorage

class NightwavePlaza: Application() {

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        Fresco.initialize(this)
        initStorage()
    }
}
