package one.plaza.nightwaveplaza

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.databinding.ActivityMainBinding
import one.plaza.nightwaveplaza.extensions.play
import one.plaza.nightwaveplaza.extensions.setSleepTimer
import one.plaza.nightwaveplaza.ui.ViewClient
import java.util.Locale


@UnstableApi
class MainActivity : AppCompatActivity() {
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var activity: AppCompatActivity
    private lateinit var webView: WebView
    private var bgPlayerView: ImageView? = null
    private var drawer: DrawerLayout? = null

    var webViewLoaded = false
    var webViewPaused = false
    private var viewVersionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this

        // Splash delay until page loaded
        val content: View = findViewById(android.R.id.content)

        content.viewTreeObserver.addOnPreDrawListener {
            return@addOnPreDrawListener webViewLoaded
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

        initializeWebView()
        loadWebView()

        setupDrawer()
        allowOnLockScreen()
        setBackButtonCallback()
    }

    override fun onStart() {
        super.onStart()
        initializeController()
        println("onStart")
        if (Build.VERSION.SDK_INT > 23) {
            resumeWebView()
        }
    }

    override fun onStop() {
        super.onStop()
        println("onStop")
        releaseController()

        // https://github.com/google/ExoPlayer/issues/4878
        if (Build.VERSION.SDK_INT > 23) {
            pauseWebView()
        }
    }

    override fun onResume() {
        super.onResume()
        println("onResume")
        setFullscreen()

        if (Build.VERSION.SDK_INT <= 23) {
            resumeWebView()
        }
    }

    override fun onPause() {
        super.onPause()
        println("onPause")

        if (Build.VERSION.SDK_INT <= 23) {
            pauseWebView()
        }
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
            pushViewData("sleepTime", Settings.sleepTime.toString())
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
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidInterface")
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        if (BuildConfig.DEBUG) {
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
            WebView.setWebContentsDebuggingEnabled(true)
        } else {
            webSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }

        webView.webViewClient = ViewClient(this)
    }

    private fun loadWebView() {
        if (BuildConfig.DEBUG) {
            webView.loadUrl("http://plaza.local:4173")
            return
        }

        if (viewVersionJob != null) {
            return
        }

        viewVersionJob = CoroutineScope(Dispatchers.Default).launch(Dispatchers.IO) {
            val client = ApiClient()
            var version: ApiClient.Version? = null

            while (version == null && isActive) {
                try {
                    version = client.getVersion()
                } catch (err: Exception) {
                    runOnUiThread {
                        makeToast(getString(R.string.no_internet))
                    }
                    delay(5000)
                }
            }

            if (version == null) {
                return@launch
            }

            runOnUiThread {
                if (version.viewSrc != Settings.viewUri) {
                    webView.clearCache(true)
                    Settings.viewUri = version.viewSrc
                }
                webView.stopLoading()
                webView.loadUrl(Settings.viewUri)
            }
        }
    }

    private fun pauseWebView() {
        if (!webViewLoaded) {
            webView.stopLoading()

            if (viewVersionJob != null) {
                viewVersionJob?.cancel()
                viewVersionJob = null
            }
        }

        webViewPaused = true
        if (webViewLoaded) {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    private fun resumeWebView() {
        webViewPaused = false
        if (webViewLoaded) {
            webView.onResume()
            webView.resumeTimers()
            pushViewData("isPlaying", Settings.isPlaying.toString())
        } else {
            loadWebView()
        }
    }

    fun pushViewData(action: String, payload: String) {
        val call = "window['emitter'].emit('$action', $payload)"
        runOnUiThread { webView.evaluateJavascript(call, null) }
    }

    fun setBackground(backgroundSrc: String) {
        if (backgroundSrc != "solid") {
            Glide.with(this).load(backgroundSrc).fitCenter().into(bgPlayerView!!)
        } else {
            Glide.with(this).clear(bgPlayerView!!)
        }
    }

    fun toggleFullscreen() {
        Settings.fullScreen = !Settings.fullScreen
        setFullscreen()
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
        pushViewData("openWindow", String.format("'%s'", window))
        drawer?.closeDrawers()
    }

    private var toast: Toast? = null
    fun makeToast(msg: String) {
        toast?.cancel()
        toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast?.show()
    }

    fun setSleepTimer(minutes: Int) {
        val time: Long = if (minutes > 0) System.currentTimeMillis() + (minutes * 60 * 1000L) else 0
        Settings.sleepTime = time
        controller?.setSleepTimer()
    }

    fun openDrawer() {
        drawer?.openDrawer(GravityCompat.START)
    }

    private fun allowOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            this.window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
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
}