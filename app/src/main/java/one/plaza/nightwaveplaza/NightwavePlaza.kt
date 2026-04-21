package one.plaza.nightwaveplaza

import android.app.Application
import one.plaza.nightwaveplaza.helpers.StorageHelper.initStorage
import timber.log.Timber

class NightwavePlaza : Application() {

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        initStorage()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
