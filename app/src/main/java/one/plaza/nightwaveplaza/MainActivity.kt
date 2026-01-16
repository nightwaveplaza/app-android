package one.plaza.nightwaveplaza

import android.content.ComponentName
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
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
import com.bumptech.glide.RequestManager
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
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Main activity that integrates the web UI, socket connection and media playback.
 * Handles the communication between UI interactions, WebView, Socket and the background player service.
 */
@UnstableApi
class MainActivity : AppCompatActivity(), WebViewCallback, SocketCallback {
    // Controller for media playback operations
    private val controller: MediaController?
        get() = try {
            if (controllerFuture.isDone) controllerFuture.get() else null
        } catch (e: Exception) {
            Timber.e(e, "Error getting controller")
            null
        }
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    // WebView manager
    private lateinit var webViewManager: WebViewManager
    private lateinit var webView: WebView

    // ImageViewer and backgrounds
    private var backgroundImageSrc = "solid"
    private lateinit var bgPlayerView: ImageView
    private lateinit var loadingImage: ImageView
    private lateinit var drawer: DrawerLayout
    private var glideRequestManager: RequestManager? = null

    // Socket client
    private lateinit var socketClient: SocketClient

    private var status: ApiClient.Status? = null

    private var appIsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        bgPlayerView = findViewById(R.id.bg_view)
        loadingImage = findViewById(R.id.loading_gif)
        glideRequestManager = Glide.with(this)
        glideRequestManager?.load(R.raw.hourglass)?.into(loadingImage)

        drawer = findViewById(R.id.drawer)
        setupDrawer()

        // WebView initialization
        webView = findViewById(R.id.webview)
        webViewManager = WebViewManager(
            callback = this,
            webView = webView,
            lifecycle = lifecycle
        )
        webViewManager.initialize()
        webViewManager.loadWebView()

        // Socket initialization
        socketClient = SocketClient(
            callback = WeakReference(this),
            lifecycle = lifecycle
        )
        socketClient.initialize()

        setNavigationBarInset(window, "#008080".toColorInt())
    }

    fun setNavigationBarInset(window: Window, @ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val statusBarInsets = insets.getInsets(WindowInsets.Type.navigationBars())

                view.setBackgroundColor(color)

                // Adjust padding to avoid overlap
                view.setPadding(0, 0, 0, statusBarInsets.bottom)
                insets
            }
        } else {
            window.navigationBarColor = color
        }
    }

    @Suppress("DEPRECATION")
    fun applyStatusBarColor(@ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.setBackgroundColor(color)
        } else {
            // For Android 14 and below
            window.navigationBarColor = color
        }
    }

    /**
     * Lifecycle methods to handle controller initialization and cleanup
     */

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadBackground()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        controllerFuture.let {
            if (it.isDone) {
                it.get()?.removeListener(playerListener)
                it.get()?.release()
            }
            MediaController.releaseFuture(it)
        }

        glideRequestManager?.let { manager ->
            bgPlayerView.let { manager.clear(it) }
            loadingImage.let { manager.clear(it) }
        }
    }

    // WebView state preservation
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    /**
     * Configure navigation drawer
     */
    private fun setupDrawer() {
        val navigationView = findViewById<NavigationView>(R.id.navView)
        navigationView.setOnApplyWindowInsetsListener { _: View?, insets: WindowInsets? -> insets!! }
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
//        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawer.drawerElevation = 0f
    }

    /**
     * Initialize media controller with the player service
     */
    @UnstableApi
    private fun initializeController() {
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlayerService::class.java))
        ).buildAsync()
        controllerFuture.addListener({ setupController() }, MoreExecutors.directExecutor())
    }

    /**
     * Configure controller with listener
     */
    private fun setupController() {
        val controller: MediaController = this.controller ?: return
        controller.addListener(playerListener)
        pushPlaybackState()
    }

    /**
     * Clean up media controller resources
     */
    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
        controller?.removeListener(playerListener)
        controller?.release()
    }

    /**
     * Media player state listener to sync UI with playback state
     */
    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            pushPlaybackState()
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

    /**
     * Push current status to the WebView if available
     */
    private fun pushStatus() {
        if (appIsReady) {
            if (status != null) {
                webViewManager.pushData("onStatusUpdate", Gson().toJson(status))
            } else {
                Timber.d("Attempt to push status not initialized yet.")
            }
        }
    }

    /**
     * Apply fullscreen mode based on current settings
     */
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

    /**
     * Show window in WebView based on navigation item selected
     */
    private fun showWindow(view: View) {
        val window = view.tag.toString()
        webViewManager.pushData("openWindow", window)
        drawer.closeDrawers()
    }

    /**
     * Apply language setting and update app locale
     */
    private fun setLanguage(lang: String) {
        val parts = lang.split("-")
        val language = parts[0]
        val region = if (parts.size > 1) parts[1] else ""

        val locale = if (region.isNotEmpty()) Locale(language, region) else Locale(language)

        // Don't react to change to the same language
        if (Settings.language == locale.toLanguageTag()) {
            return
        }

        Settings.language = locale.toLanguageTag()

        // Set application locale
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(locale.toLanguageTag())
        )
    }

    /**
     * Update WebView with current playback state
     */
    private fun pushPlaybackState() {
        val currentController = controller
        currentController?.let {
            webViewManager.pushData("isPlaying", it.isPlaying)
        }
        webViewManager.pushData("sleepTime", Settings.sleepTime)
    }

    /**
     * Show no internet connection dialog
     */
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

    /**
     * Hide loading animation
     */
    private fun stopLoadingAnimation() {
        loadingImage.visibility = View.GONE
        glideRequestManager?.clear(loadingImage)
    }

    /**
     * Load background image or clear it if set to solid
     */
    private fun loadBackground() {
        runOnUiThread {
            if (backgroundImageSrc != "solid") {
                Glide.with(this)
                    .load(backgroundImageSrc)
                    .override(bgPlayerView.width, bgPlayerView.height)
                    .fitCenter()
                    .transition(withCrossFade())
                    .into(bgPlayerView)
            } else {
                glideRequestManager?.clear(bgPlayerView)
            }
        }
    }

    /**
     * WebView callback implementations
     */

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
            drawer.openDrawer(GravityCompat.START)
        }
    }

    override fun onPlayAudio() {
        runOnUiThread {
            val currentController = controller
            currentController?.let {
                if (it.isPlaying) it.pause() else it.play(this)
            }
        }
    }

    override fun onSetBackground(backgroundSrc: String) {
        backgroundImageSrc = backgroundSrc
        runOnUiThread {
            if (backgroundSrc == "solid") {
                Glide.with(this).clear(bgPlayerView)
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

    override fun onSetSleepTimer(time: Long) {
        runOnUiThread {
            controller?.setSleepTimer(time)
        }
    }

    override fun onSetLanguage(lang: String) {
        runOnUiThread {
            setLanguage(lang)
        }
    }

    override fun onSetThemeColor(color: String) {
        runOnUiThread {
            applyStatusBarColor(color.toColorInt())
        }
    }

    override fun onReady() {
        appIsReady = true
        runOnUiThread {
            pushStatus()
            pushPlaybackState()
        }
    }

    /**
     * Socket callback implementations
     */

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