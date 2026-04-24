package one.plaza.nightwaveplaza

import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import one.plaza.nightwaveplaza.databinding.ActivityMainBinding
import one.plaza.nightwaveplaza.extensions.play
import one.plaza.nightwaveplaza.extensions.setSleepTimer
import one.plaza.nightwaveplaza.updater.WebAppUpdateWorker
import one.plaza.nightwaveplaza.view.WebViewCallback
import one.plaza.nightwaveplaza.view.WebViewManager
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main activity that integrates the web UI, socket connection and media playback.
 * Handles the communication between UI interactions, WebView, Socket and the background player service.
 */
@UnstableApi
class MainActivity : AppCompatActivity(), WebViewCallback {
    private lateinit var binding: ActivityMainBinding

    // Controller for media playback operations
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = controllerFuture?.let { if (it.isDone) it.get() else null }

    // WebView manager
    private lateinit var webViewManager: WebViewManager

    // ImageViewer and backgrounds
    private var backgroundImageSrc = "solid"
    private var glideRequestManager: RequestManager? = null

    private var appIsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            enableEdgeToEdge()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        glideRequestManager = Glide.with(this)

        setupDrawer()

        // WebView initialization
        webViewManager = WebViewManager(
            callback = this,
            webView = binding.webview,
            lifecycle = lifecycle
        )
        webViewManager.initialize()
        webViewManager.load()

        setNavigationBarColor(window, Settings.themeColor.toColorInt())
        scheduleBackgroundUpdate()
        setVersionSwitchListener()
    }

    private fun setupNavigationListeners() {
        val navItems = listOf(
            binding.nav.ratings, binding.nav.history, binding.nav.userFavorites,
            binding.nav.user, binding.nav.settings, binding.nav.about, binding.nav.support
        )
        navItems.forEach { view ->
            view.setOnClickListener { showWindow(it) }
        }
    }

    /**
     * Configure navigation drawer
     */
    private fun setupDrawer() {
        setupNavigationListeners()
        binding.navView.setOnApplyWindowInsetsListener { _, insets -> insets }
        binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
        binding.drawer.drawerElevation = 0f
    }

    /**
     * Schedule view update every 12 hours
     */
    private fun scheduleBackgroundUpdate() {
        val workManager = WorkManager.getInstance(this)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        if (Settings.useDevChannel) {
            val updateRequest = OneTimeWorkRequestBuilder<WebAppUpdateWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(
                "plaza_dev_update",
                ExistingWorkPolicy.REPLACE,
                updateRequest
            )
        } else {
            val updateRequest = PeriodicWorkRequestBuilder<WebAppUpdateWorker>(
                12, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag("plaza_update")
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "plaza_update_job",
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest
            )
        }
    }

    fun setNavigationBarColor(window: Window, @ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.setBackgroundColor(color)
        } else {
            @Suppress("DEPRECATION")
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
        binding.webview.destroy()
    }

    // WebView state preservation
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webview.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webview.restoreState(savedInstanceState)
    }

    /**
     * Initialize media controller with the player service
     */
    @UnstableApi
    private fun initializeController() {
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlayerService::class.java))
        ).buildAsync().apply {
            addListener({ setupController() }, MoreExecutors.directExecutor())
        }
    }

    /**
     * Configure controller with listener
     */
    private fun setupController() {
        controller?.let {
            it.addListener(playerListener)
            pushPlaybackState()
        }
    }

    /**
     * Clean up media controller resources
     */
    private fun releaseController() {
        controllerFuture?.let { future ->
            if (future.isDone) {
                future.get().removeListener(playerListener)
                future.get().release()
            }
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
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
                webViewManager.emitEvent("player:buffering", true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            webViewManager.emitEvent("player:playing", false)
            Timber.e(error)
        }
    }

    /**
     * Apply fullscreen mode based on current settings
     */
    private fun setFullscreen() {
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        if (Settings.fullScreen) {
            // immersive mode, hide all
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }

        // call insets recalc css
        window.decorView.requestApplyInsets()
    }

    /**
     * Show window in WebView based on navigation item selected
     */
    private fun showWindow(view: View) {
        val windowTag = view.tag.toString()
        webViewManager.emitEvent("window:open", windowTag)
        binding.drawer.closeDrawers()
    }

    /**
     * Apply language setting and update app locale
     */
    private fun setLanguage(lang: String) {
        val locale = parseJsLocale(lang)
        val tag = locale.toLanguageTag()
        if (Settings.language == tag) return

        Settings.language = tag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun parseJsLocale(jsLocale: String): Locale {
        return when (jsLocale.lowercase()) {
            "zh-hant", "zh-tw", "zh-hk" -> Locale.TRADITIONAL_CHINESE
            "zh-hans", "zh-cn" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.forLanguageTag(jsLocale)
        }
    }

    /**
     * Update WebView with current playback state
     */
    private fun pushPlaybackState() {
        controller?.let { webViewManager.emitEvent("player:playing", it.isPlaying) }
        webViewManager.emitEvent("player:sleeptime", Settings.sleepTargetTime)
    }

    /**
     * Show no internet connection dialog
     */
    private fun notifyNoInternet() {
        AlertDialog.Builder(this)
            .setTitle("No Connection")
            .setMessage(getString(R.string.no_internet))
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setNegativeButton("Retry") { dialog, _ ->
                dialog.cancel()
                webViewManager.load()
            }
            .create()
            .show()
    }

    /**
     * Load background image or clear it if set to solid
     */
    private fun loadBackground() {
        lifecycleScope.launch {
            if (backgroundImageSrc != "solid") {
                Glide.with(this@MainActivity)
                    .load(backgroundImageSrc)
                    .override(binding.bgView.width, binding.bgView.height)
                    .fitCenter()
                    .transition(withCrossFade())
                    .into(binding.bgView)
            } else {
                Glide.with(this@MainActivity).clear(binding.bgView)
            }
        }
    }

    /**
     * WebView callback implementations
     */
    override fun onWebViewLoadFail() {
        lifecycleScope.launch { notifyNoInternet() }
    }

    override fun onWebViewLoaded() {
    }

    override fun onOpenDrawer() {
        lifecycleScope.launch { binding.drawer.openDrawer(GravityCompat.START) }
    }

    override fun onPlayAudio() {
        lifecycleScope.launch {
            controller?.let { if (it.isPlaying) it.pause() else it.play(this@MainActivity) }
        }
    }

    override fun onSetBackground(backgroundSrc: String) {
        backgroundImageSrc = backgroundSrc
        loadBackground()
    }

    override fun onToggleFullscreen() {
        lifecycleScope.launch {
            Settings.fullScreen = !Settings.fullScreen
            setFullscreen()
        }
    }

    override fun onSetSleepTimer(sleepTime: Long) {
        lifecycleScope.launch { controller?.setSleepTimer(sleepTime) }
    }

    override fun onSetLanguage(lang: String) {
        lifecycleScope.launch { setLanguage(lang) }
    }

    override fun onSetThemeColor(color: String) {
        lifecycleScope.launch {
            Settings.themeColor = color
            setNavigationBarColor(window, color.toColorInt())
        }
    }

    override fun onReady() {
        appIsReady = true
        lifecycleScope.launch {
            pushPlaybackState()
        }
    }

    private var versionTapCount = 0
    private var versionTapLastTime = 0L
    fun setVersionSwitchListener() {
        binding.navPlazaLabel.setOnClickListener {
            val now = SystemClock.elapsedRealtime()

            if (now - versionTapLastTime > 1000) {
                versionTapCount = 0
            }
            versionTapLastTime = now
            versionTapCount++

            if (versionTapCount == 7) {
                versionTapCount = 0

                val isDevNow = !Settings.useDevChannel
                Settings.useDevChannel = isDevNow

                val channelName = if (isDevNow) "DEV" else "PROD"
                Toast.makeText(this, "OTA Channel switched to: $channelName", Toast.LENGTH_LONG).show()
            }
        }
    }
}