package one.plaza.nightwaveplaza

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.navigation.NavigationView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.plaza.nightwaveplaza.databinding.ActivityMainBinding
import one.plaza.nightwaveplaza.extensions.play
import one.plaza.nightwaveplaza.extensions.setSleepTimer
import one.plaza.nightwaveplaza.helpers.JsonHelper
import one.plaza.nightwaveplaza.helpers.Keys
import one.plaza.nightwaveplaza.helpers.StorageHelper
import one.plaza.nightwaveplaza.helpers.UserHelper
import java.io.File
import java.util.Locale


@UnstableApi
class MainActivity : AppCompatActivity() {
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var activity: AppCompatActivity
    private lateinit var webView: WebView
    private var bgPlayer: ExoPlayer? = null
    private var bgPlayerCache: SimpleCache? = null
    private var bgPlayerView: PlayerView? = null
    private var drawer: DrawerLayout? = null

    private var viewLoading = true
    private var viewError = false
    private var viewPaused = false
    private var fullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this

        // Splash delay until page loaded
        val content: View = findViewById(android.R.id.content)

        content.viewTreeObserver.addOnPreDrawListener {
            return@addOnPreDrawListener !viewLoading
        }

        // Button binding
        val activityMainBinding: ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        activityMainBinding.nav.ratings.setOnClickListener { v: View -> showWindow(v) }
        activityMainBinding.nav.history.setOnClickListener { v: View -> showWindow(v) }
        activityMainBinding.nav.userFavorites.setOnClickListener { v: View -> showWindow(v) }
        activityMainBinding.nav.user.setOnClickListener { v: View -> showWindow(v) }
        activityMainBinding.nav.settings.setOnClickListener { v: View -> showWindow(v) }
        activityMainBinding.nav.about.setOnClickListener { v: View -> showWindow(v) }
        activityMainBinding.nav.support.setOnClickListener { v: View -> showWindow(v) }
        setContentView(activityMainBinding.root)

        webView = findViewById(R.id.webview)
        bgPlayerView = findViewById(R.id.bg_view)
        drawer = findViewById(R.id.drawer)

        initializeBgPlayer()
        initializeWebView()
        setupDrawer()

        println(webView.isHardwareAccelerated)
    }

    override fun onStart() {
        super.onStart()
        initializeController()
        println("onStart")
        if (Build.VERSION.SDK_INT > 23) {
            resumeBgPlayer()
            resumeWebView()
        }
    }

    override fun onStop() {
        super.onStop()
        println("onStop")
        releaseController()

        // https://github.com/google/ExoPlayer/issues/4878
        if (Build.VERSION.SDK_INT > 23) {
            pauseBgPlayer()
            pauseWebView()
        }
    }

    override fun onResume() {
        super.onResume()
        println("onResume")
        setFullscreen(fullscreen)

        if (Build.VERSION.SDK_INT <= 23) {
            resumeBgPlayer()
            resumeWebView()
        }
    }

    override fun onPause() {
        super.onPause()
        println("onPause")

        if (Build.VERSION.SDK_INT <= 23) {
            pauseBgPlayer()
            pauseWebView()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseBgPlayer()
    }

    private fun setupDrawer() {
        val navigationView = findViewById<NavigationView>(R.id.navView)
        navigationView.setOnApplyWindowInsetsListener { _: View?, insets: WindowInsets? -> insets!! }
        drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
//        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawer?.drawerElevation = 0f
    }

    private fun initializeController() {
        controllerFuture = MediaController.Builder(
            activity as Context,
            SessionToken(
                activity as Context,
                ComponentName(activity as Context, PlayerService::class.java)
            )
        ).buildAsync()
        controllerFuture.addListener({ setupController() }, MoreExecutors.directExecutor())
    }

    private fun setupController() {
        val controller: MediaController = this.controller ?: return
        controller.addListener(playerListener)
    }

    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    fun play() {
        if (controller?.isPlaying == true) {
            controller?.pause()
        } else {
            controller?.play(activity as Context)
        }
    }

    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            pushViewData("isPlaying", isPlaying.toString())
            pushViewData("sleepTime", StorageHelper.load(Keys.SLEEP_TIMER, 0L).toString())
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            println(controller?.isPlaying)
            if (playWhenReady && controller?.isPlaying == false) {
                pushViewData("isBuffering", "true")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            pushViewData("isPlaying", "false")
            // TODO: toast
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initializeWebView() {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.textZoom = 100
        webSettings.domStorageEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidInterface")
        webView.setBackgroundColor(Color.TRANSPARENT)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
//            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
//            .addPathHandler("/assets/",
//                WebViewAssetLoader.InternalStoragePathHandler(this, File("$cacheDir/assets"))
//            )
//            .addPathHandler(
//                "/artworks/",
//                WebViewAssetLoader.InternalStoragePathHandler(this, File("$cacheDir/artworks"))
//            )
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val intent = Intent(Intent.ACTION_VIEW, request.url)
                view.context.startActivity(intent)
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                if (viewError) {
                    // Don't reload if webview paused
                    if (viewPaused) {
                        return
                    }

                    // Show toast and try again
                    makeToast(getString(R.string.no_internet))
                    activity.lifecycleScope.launch {
                        delay(10000)
                        view.reload()
                    }
                } else {
                    viewLoading = false
                    pushViewData("isPlaying", StorageHelper.load(Keys.IS_PLAYING, false).toString())
                    pushViewData("sleepTime", StorageHelper.load(Keys.SLEEP_TIMER, 0L).toString())
                }

                viewError = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                viewError = true
            }
        }

        webView.loadUrl(if (BuildConfig.DEBUG) "http://plaza.dev:4173/" else "https://m.plaza.one")
    }

    private fun resumeWebView() {
        webView.onResume()
        webView.resumeTimers()
        viewPaused = false
        if (viewError) {
            webView.reload()
        }
        pushViewData("isPlaying", StorageHelper.load(Keys.IS_PLAYING, false).toString())
    }

    private fun pauseWebView() {
        if (viewError) {
            webView.stopLoading()
        }
        viewPaused = true
        webView.onPause()
        webView.pauseTimers()
    }

    fun pushViewData(action: String, payload: String) {
        println("pushViewData: $payload")
        val call = "window['plaza'].push('$action', $payload)"
        runOnUiThread { webView.evaluateJavascript(call, null) }
    }

    private fun initializeBgPlayer() {
        println("initializeBgPlayer")
        if (bgPlayer == null) {
            bgPlayer = ExoPlayer.Builder(this).build()
            bgPlayer?.repeatMode = Player.REPEAT_MODE_ALL
            bgPlayerView?.player = bgPlayer
            bgPlayerView?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        if (bgPlayerCache == null) {
            val cacheFolder = File(filesDir, "backs")
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(1024 * 1024 * 100)
            bgPlayerCache = SimpleCache(cacheFolder, cacheEvictor, StandaloneDatabaseProvider(this))
        }

        bgPlayerView?.player?.playWhenReady = true
    }

    private fun releaseBgPlayer() {
        println("releaseBgPlayer")
        bgPlayerView?.player = null
        bgPlayerCache?.release()
        bgPlayerCache = null
        bgPlayer?.release()
        bgPlayer = null
    }

    private fun resumeBgPlayer() {
        bgPlayerView?.onResume()
        bgPlayerView?.player?.playWhenReady = true
    }

    private fun pauseBgPlayer() {
        bgPlayerView?.onPause()
        bgPlayerView?.player?.pause()
    }

    fun setBackground(backgroundSrc: String) {
        if (bgPlayer == null || bgPlayerCache == null) return

        val uri: Uri? = if (backgroundSrc != "solid") {
            Uri.parse(backgroundSrc)
        } else {
            null
        }

        if (uri == null) {
            bgPlayer!!.playWhenReady = false
            bgPlayer!!.stop()
            bgPlayer!!.seekTo(0)
        } else {

            val dsf: DataSource.Factory = DefaultDataSource.Factory(this)
            val cacheDataSourceFactory: DataSource.Factory =
                CacheDataSource.Factory().setCache(bgPlayerCache!!)
                    .setUpstreamDataSourceFactory(dsf)
            val videoSource: MediaSource =
                ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri))
            bgPlayer!!.playWhenReady = false
            bgPlayer!!.setMediaSource(videoSource)
            bgPlayer!!.prepare()
            bgPlayer!!.playWhenReady = true
        }
    }

    fun toggleFullscreen() {
        fullscreen = !fullscreen
        StorageHelper.save(Keys.FULLSCREEN, fullscreen)
        setFullscreen(fullscreen)
    }

    private fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView.rootView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(
                window,
                window.decorView.rootView
            ).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun openDrawer() {
        drawer?.openDrawer(GravityCompat.START)
    }

    private fun showWindow(view: View) {
        var window = view.tag.toString()
        if (window == "user-favorites" || window == "user") {
            if (UserHelper.getToken().equals("")) {
                window = "user-login"
            }
        }
        window = JsonHelper.windowName(window)
        pushViewData("openWindow", window)
        drawer?.closeDrawers()
    }

    fun setAudioQuality(lowQuality: Int) {
        val quality = if (lowQuality == 1) "LOW" else "HIGH"
        makeToast("Set audio quality to $quality. Please restart the playback.")
    }

    private var toast: Toast? = null
    fun makeToast(msg: String) {
        toast?.cancel()
        toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast?.show()
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes == 0) {
            makeToast(getString(R.string.timer_disabled))
        } else {
            makeToast(String.format(Locale.US, getString(R.string.timer_start), minutes))
        }

        val time: Long = if (minutes > 0) System.currentTimeMillis() + (minutes * 60 * 1000L) else 0
        StorageHelper.save(Keys.SLEEP_TIMER, time)
        controller?.setSleepTimer()
    }
}