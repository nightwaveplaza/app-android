package one.plaza.nightwaveplaza

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.google.android.material.navigation.NavigationView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.databinding.ActivityMainBinding
import one.plaza.nightwaveplaza.extensions.play
import one.plaza.nightwaveplaza.extensions.setSleepTimer
import one.plaza.nightwaveplaza.socket.SocketCallback
import one.plaza.nightwaveplaza.socket.SocketClient
import one.plaza.nightwaveplaza.view.WebViewCallback
import one.plaza.nightwaveplaza.view.WebViewManager
import timber.log.Timber
import java.util.Locale


@UnstableApi
class MainActivity : AppCompatActivity(), WebViewCallback, SocketCallback {
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var activity: AppCompatActivity
    private lateinit var webView: WebView
    private var bgPlayerView: ImageView? = null
    private var loadingImage: ImageView? = null
    private var drawer: DrawerLayout? = null

    private lateinit var webViewManager: WebViewManager
    private lateinit var socketClient: SocketClient

    private lateinit var status: ApiClient.Status
    private var backgroundImageSrc = "solid"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this

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
        loadingImage = findViewById(R.id.loading_gif)
        Glide.with(this).load(R.raw.hourglass).into(loadingImage!!)

        setupDrawer()
        setBackButtonCallback()

        webViewManager = WebViewManager(
            callback = this,
            webView = webView,
            lifecycle = lifecycle
        )
        webViewManager.initialize()
        webViewManager.loadWebView()

        socketClient = SocketClient(
            callback = this,
            lifecycle = lifecycle
        )
        socketClient.initialize()

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )
    }

    private fun pushStatus() {
        if (::status.isInitialized) {
            webViewManager.pushData("onStatusUpdate", Gson().toJson(status))
        } else {
            Timber.d("Attempt to push status not initialized yet.")
        }
    }

    override fun onStart() {
        super.onStart()
        initializeController()
        Timber.d("Lifecycle: onStart")
    }

    override fun onStop() {
        super.onStop()
        releaseController()
        Timber.d("Lifecycle: onStop")
    }

    override fun onResume() {
        super.onResume()
        setFullscreen()
        Timber.d("Lifecycle: onResume")
    }

    override fun onDestroy() {
        super.onDestroy()

        controllerFuture.let {
            if (it.isDone) {
                it.get()?.removeListener(playerListener)
                it.get()?.release()
            }
            MediaController.releaseFuture(it)
        }

        bgPlayerView?.let { Glide.with(this).clear(it) }
        loadingImage?.let { Glide.with(this).clear(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    private fun setupDrawer() {
        val navigationView = findViewById<NavigationView>(R.id.navView)
        navigationView.setOnApplyWindowInsetsListener { _: View?, insets: WindowInsets? -> insets!! }
        drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
//        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawer?.drawerElevation = 0f
    }

    @UnstableApi
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
        controller?.removeListener(playerListener)
        controller?.release()
    }

    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            webViewManager.pushData("isPlaying", isPlaying)
            webViewManager.pushData("sleepTime", Settings.sleepTime)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (playWhenReady && controller?.isPlaying == false) {
                webViewManager.pushData("isBuffering", "true")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            webViewManager.pushData("isPlaying", "false")
            Timber.e(error)
        }
    }

    private fun setFullscreen() {
        if (Settings.fullScreen) {
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

    private fun showWindow(view: View) {
        val window = view.tag.toString()
        webViewManager.pushData("openWindow", window)
        drawer?.closeDrawers()
    }

    private fun setBackButtonCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val a = Intent(Intent.ACTION_MAIN)
                a.addCategory(Intent.CATEGORY_HOME)
                a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(a)
            }
        })
    }

    private fun setLanguage(lang: String) {
        // Don't react to change to the same language
        val loc: Locale = if (lang.contains('-')) {
            Locale(
                lang.substring(0, lang.indexOf('-')),
                lang.substring(lang.indexOf('-') + 1, lang.length)
            )
        } else {
            Locale(lang)
        }

        if (Settings.language == loc.language) {
            return
        }

        Settings.language = loc.language

        // Set application locale
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(loc.language)
        )
    }

    private fun pushPlaybackState() {
        if (controller != null) {
            webViewManager.pushData("isPlaying", controller!!.isPlaying)
        }
        webViewManager.pushData("sleepTime", Settings.sleepTime)
    }

    private fun notifyNoInternet() {
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder
            .setTitle("No Connection")
            .setMessage(getString(R.string.no_internet))
            .setCancelable(false)
            .setPositiveButton("Exit", object : OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    finish()
                }
            })
            .setNegativeButton("Retry", object : OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    dialog.cancel()
                    webViewManager.loadWebView()
                }
            })
        alertBuilder.create().show()
    }

    private fun stopLoadingAnimation() {
        loadingImage?.visibility = View.GONE
        Glide.with(this).clear(loadingImage!!)
    }

    private fun loadBackground() {
        runOnUiThread {
            if (backgroundImageSrc != "solid") {
                Glide.with(this)
                    .load(backgroundImageSrc)
                    .override(bgPlayerView!!.width, bgPlayerView!!.height)
                    .fitCenter()
                    .transition(withCrossFade())
                    .into(bgPlayerView!!)
            } else {
                bgPlayerView?.let { Glide.with(this).clear(it) }
            }
        }
    }

    override fun getActivityContext(): Context = this

    override fun onWebViewLoadFail() {
        runOnUiThread { notifyNoInternet() }
    }

    override fun onWebViewLoaded() {
        stopLoadingAnimation()
        if (!socketClient.isConnected) {
            socketClient.connect()
        }
    }

    override fun onOpenDrawer() {
        runOnUiThread {
            drawer?.openDrawer(GravityCompat.START)
        }
    }

    override fun onPlayAudio() {
        runOnUiThread {
            if (controller?.isPlaying == true) {
                controller?.pause()
            } else {
                controller?.play(activity as Context)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadBackground()
    }

    override fun onSetBackground(backgroundSrc: String) {
        backgroundImageSrc = backgroundSrc
        runOnUiThread {
            if (backgroundSrc == "solid") {
                Glide.with(this).clear(bgPlayerView!!)
            } else {
                loadBackground()
            }
        }
    }

    override fun onToggleFullscreen() {
        runOnUiThread {
            Settings.fullScreen = !Settings.fullScreen
            setFullscreen()
        }
    }

    override fun onSetSleepTimer(timestamp: Long) {
        runOnUiThread {
            val time: Long = if (timestamp > 0) timestamp else 0
            Settings.sleepTime = time
            controller?.setSleepTimer()
        }
    }

    override fun onSetLanguage(lang: String) {
        runOnUiThread {
            setLanguage(lang)
        }
    }

    override fun onReady() {
        pushStatus()
        pushPlaybackState()
    }

    override fun onReconnectRequest() {
        socketClient.connect()
    }

    override fun onStatus(s: String) {
        status = Gson().fromJson(s, ApiClient.Status::class.java)
        pushStatus()
    }

    override fun onListeners(listeners: Int) {
        webViewManager.pushData("onListenersUpdate", listeners)
    }

    override fun onReactions(reactions: Int) {
        webViewManager.pushData("onReactionsUpdate", reactions)
    }

    override fun onSocketConnect() {
        webViewManager.pushData("socketConnect")
    }

    override fun onSocketDisconnect() {
        webViewManager.pushData("socketDisconnect")
    }

    override fun onSocketReconnectFailed() {

        webViewManager.pushData("socketReconnectFailed")

    }
}