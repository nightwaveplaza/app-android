package one.plaza.nightwaveplaza

import android.app.Application
import one.plaza.nightwaveplaza.helpers.StorageHelper.initStorage

class NightwavePlaza: Application() {

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        initStorage()
    }
}
