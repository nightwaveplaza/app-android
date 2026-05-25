package one.plaza.nightwaveplaza.view

interface WebViewCallback {
    fun onWebViewLoaded()
    fun onWebViewLoadFail()

    fun onPlayAudio()
    fun onSetBackground(backgroundSrc: String)
    fun onToggleFullscreen()
    fun onSetSleepTimer(sleepTime: Long)
    fun onSetLanguage(lang: String)
    fun onSetThemeColor(color: String)
    fun onReady()
}