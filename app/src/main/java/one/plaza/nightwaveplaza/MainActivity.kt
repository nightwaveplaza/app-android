package one.plaza.nightwaveplaza

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.Intent
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
import java.util.Locale
import kotlin.system.exitProcess


@UnstableApi
class MainActivity : AppCompatActivity(), WebViewCallback, SocketCallback {
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var activity: AppCompatActivity
    private lateinit var webView: WebView
    private var bgPlayerView: ImageView? = null
    private var drawer: DrawerLayout? = null

    private lateinit var webViewManager: WebViewManager
    private lateinit var socketClient: SocketClient

    private lateinit var status: ApiClient.Status

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this

//        installSplashScreen().apply {
//            setKeepOnScreenCondition {
//                false
//                //!webViewManager.webViewLoaded
//            }
//        }

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
    }

    fun pushStatus() {
        runOnUiThread {
            if (::status.isInitialized) {
                webViewManager.pushData("status", Gson().toJson(status))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        println("onStart")
        initializeController()
    }

    override fun onStop() {
        super.onStop()
        println("onStop")
        releaseController()
    }

    override fun onResume() {
        super.onResume()
        println("onResume")
        setFullscreen()
    }

    override fun onPause() {
        super.onPause()
        println("onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Glide.with(this).clear(bgPlayerView!!)
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
    }

    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            webViewManager.pushData("isPlaying", isPlaying.toString())
            webViewManager.pushData("sleepTime", Settings.sleepTime.toString())
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
            // TODO: toast
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

    fun setLanguage(lang: String) {
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

        // As locale triggers activity lifecycle, set webview as not loaded
        // TODO change without reload
        //webViewLoaded = false

        // Set application locale
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(loc.language)
        )
    }

    fun pushPlaybackState() {
        runOnUiThread {
            if (controller != null) {
                webViewManager.pushData("isPlaying", controller!!.isPlaying.toString())
            }
            webViewManager.pushData("sleepTime", Settings.sleepTime.toString())
        }
    }

    var noInternetReasonIsWebView = true
    fun notifyNoInternet() {
        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder
            .setTitle("No Connection")
            .setMessage(getString(R.string.no_internet))
            .setCancelable(false)
            .setPositiveButton("Exit", object : OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    exitProcess(0)
                }
            })
            .setNegativeButton("Retry", object : OnClickListener {
                override fun onClick(dialog: DialogInterface, which: Int) {
                    dialog.cancel()
                    if (noInternetReasonIsWebView) {
                        webViewManager.loadWebView()
                    }
                    else {
                        socketClient.connect()
                    }
                }
            })
        alertBuilder.create().show()
    }

    override fun getActivityContext(): Context = this

    override fun onWebViewLoaded() {
        if (!socketClient.isConnected) {
            socketClient.connect()
        }
    }

    override fun onWebViewLoadFail() {
        noInternetReasonIsWebView = true
        runOnUiThread { notifyNoInternet() }
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

    override fun onSetBackground(backgroundSrc: String) {
        runOnUiThread {
            if (backgroundSrc != "solid") {
                Glide.with(this).load(backgroundSrc).fitCenter().into(bgPlayerView!!)
            } else {
                Glide.with(this).clear(bgPlayerView!!)
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

    override fun onStatus(s: String) {
        status = Gson().fromJson(s, ApiClient.Status::class.java)
        pushStatus()
    }

    override fun onListeners(listeners: Int) {
        runOnUiThread {
            webViewManager.pushData("listeners", listeners)
        }
    }

    override fun onReactions(reactions: Int) {
        runOnUiThread {
            webViewManager.pushData("reactions", reactions)
        }
    }

    override fun onSocketReconnectFailed() {
        runOnUiThread {
            noInternetReasonIsWebView = false
            notifyNoInternet()
        }
    }
}