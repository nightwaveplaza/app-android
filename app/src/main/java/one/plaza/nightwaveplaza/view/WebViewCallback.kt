package one.plaza.nightwaveplaza.view

import android.content.Context

interface WebViewCallback {
    fun onWebViewLoaded()
    fun onWebViewLoadFail()
    fun getActivityContext(): Context

    fun onOpenDrawer()
    fun onPlayAudio()
    fun onSetBackground(backgroundSrc: String)
    fun onToggleFullscreen()
    fun onSetSleepTimer(timestamp: Long)
    fun onSetLanguage(lang: String)
    fun onReady()

}