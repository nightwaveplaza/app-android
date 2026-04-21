package one.plaza.nightwaveplaza

import android.app.Application
import one.plaza.nightwaveplaza.helpers.StorageHelper
import timber.log.Timber

class NightwavePlaza : Application() {

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        StorageHelper.init(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
