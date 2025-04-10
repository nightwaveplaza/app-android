package one.plaza.nightwaveplaza.view

interface WebViewCallback {
    fun onWebViewLoaded()
    fun onWebViewLoadFail()

    fun onOpenDrawer()
    fun onPlayAudio()
    fun onSetBackground(backgroundSrc: String)
    fun onToggleFullscreen()
    fun onSetSleepTimer(timestamp: Long)
    fun onSetLanguage(lang: String)
    fun onSetThemeColor(color: String)
    fun onReady()
    fun onReconnectRequest()
}