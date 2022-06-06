package one.plaza.nightwaveplaza;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.material.navigation.NavigationView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.Locale;

import one.plaza.nightwaveplaza.Api2.Models.Status;
import one.plaza.nightwaveplaza.Entities.Artwork;
import one.plaza.nightwaveplaza.Entities.Reaction;
import one.plaza.nightwaveplaza.Entities.User;
import one.plaza.nightwaveplaza.Service.PlayerService;
import one.plaza.nightwaveplaza.Ui.MediaBrowserHelper;
import one.plaza.nightwaveplaza.Ui.WebAppInterface;
import one.plaza.nightwaveplaza.Utils.JSONUtils;
import one.plaza.nightwaveplaza.Utils.PrefKeys;
import one.plaza.nightwaveplaza.Utils.Storage;
import one.plaza.nightwaveplaza.Utils.Utils;
import one.plaza.nightwaveplaza.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private final MainActivity context = this;
    private boolean onPause = false;
    private DrawerLayout drawer;

    // WebView
    private WebView view;
    private Toast toast;
    private boolean viewLoading = true;

    // Background Player
    ExoPlayer bgPlayer;
    SimpleCache bgCache;

    // Settings
    private boolean fullscreen = false;

    // Music service
    private MediaBrowserHelper mMediaBrowserHelper;

    /**
     * Activity onCreate
     *
     * @param savedInstanceState //
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Clear old SharedPreferences
        Storage.clearPreviousPreferences(this.context);

        // Button binding
        ActivityMainBinding activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        activityMainBinding.nav.ratings.setOnClickListener(this::showWindow);
        activityMainBinding.nav.history.setOnClickListener(this::showWindow);
        activityMainBinding.nav.userFavorites.setOnClickListener(this::showWindow);
        activityMainBinding.nav.user.setOnClickListener(this::showWindow);
        activityMainBinding.nav.settings.setOnClickListener(this::showWindow);
        activityMainBinding.nav.about.setOnClickListener(this::showWindow);
        activityMainBinding.nav.support.setOnClickListener(this::showWindow);
        setContentView(activityMainBinding.getRoot());

        // Load settings
        loadSettings();
        setFullscreen(fullscreen);

        // Background player
        createVideoPlayer();

        // Navigation drawer
        setupDrawer();

        // Register media browser
        registerService();

        // setup view
        setupView();
    }

    /**
     * Activity onStart
     */
    @Override
    protected void onStart() {
        super.onStart();
        Utils.debugLog("onStart", this);
        mMediaBrowserHelper.onStart();
        EventBus.getDefault().register(this);
    }

    /**
     * Activity onResume
     */
    @Override
    public void onResume() {
        super.onResume();
        Utils.debugLog("onResume", this);
        setFullscreen(fullscreen);

        if (onPause && !viewLoading) {
            view.onResume();
            onPause = false;
            mMediaBrowserHelper.onStart();
            pushViewData("reactionUpdate", Reaction.getAsJson(context));
            bgPlayer.setPlayWhenReady(true);
            bgPlayer.getPlaybackState();
        }
    }

    /**
     * Activity onPause
     */
    @Override
    protected void onPause() {
        Utils.debugLog("onPause", this);
        super.onPause();
        view.onPause();
        onPause = true;
        mMediaBrowserHelper.onStop();
        bgPlayer.setPlayWhenReady(false);
        bgPlayer.getPlaybackState();
    }

    @Override
    protected void onStop() {
        mMediaBrowserHelper.onStop();
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    /**
     * Activity onDestroy
     */
    @Override
    protected void onDestroy() {
        Utils.debugLog("onDestroy", this);
        releasePlayer();
        super.onDestroy();
    }

    /**
     * Activity when back button pressed
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Subscribe
    public void onPlaybackEvent(Events.onPlayback event) {
        sendStatus();
    }

    @Subscribe
    public void onArtworkEvent(Events.onArtwork event) {
        pushViewData("artwork", "'" + Artwork.getArtworkPath(context) + "'");
    }

    @Subscribe
    public void onToastEvent(Events.onToast event) {
        makeToast(event.message);
    }

    @Subscribe
    public void onReactionEvent(Events.onReaction event) {
        pushViewData("reactionUpdate", Reaction.getAsJson(getApplicationContext()));
    }

    /**
     * Setup WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupView() {
        view = findViewById(R.id.webview);

        WebSettings webSettings = view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setTextZoom(100);
        webSettings.setDomStorageEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setSaveFormData(false);

        view.addJavascriptInterface(new WebAppInterface(this), "AndroidInterface");
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/artworks/", new WebViewAssetLoader.InternalStoragePathHandler(this, new File(getCacheDir() + "/artworks")))
                .build();

        view.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                view.getContext().startActivity(intent);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                viewLoading = false;
            }
        });

        view.loadUrl("https://appassets.androidplatform.net/assets/app/index.html");
    }

    private void setupDrawer() {
        drawer = findViewById(R.id.drawer);
        NavigationView navigationView = findViewById(R.id.navView);
        if (navigationView != null) {
            navigationView.setOnApplyWindowInsetsListener((v, insets) -> insets);
        }
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        drawer.setDrawerElevation(0);
    }

    private void createVideoPlayer() {
        if (bgPlayer == null) {
            StyledPlayerView vw = findViewById(R.id.video_view);
            bgPlayer = new ExoPlayer.Builder(this).build();
            bgPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);

            vw.setPlayer(bgPlayer);
            vw.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

            if (bgCache == null) {
                File cacheFolder = new File(context.getFilesDir(), "backs");
                LeastRecentlyUsedCacheEvictor cacheEvictor = new LeastRecentlyUsedCacheEvictor(1024 * 1024 * 100);
                bgCache = new SimpleCache(cacheFolder, cacheEvictor, new StandaloneDatabaseProvider(this));
            }
        }
    }

    private void releasePlayer() {
        if (bgPlayer != null) {
            bgPlayer.release();
            bgPlayer = null;
        }
        if (bgCache != null) {
            bgCache.release();
            bgCache = null;
        }
    }

    private void registerService() {
        mMediaBrowserHelper = new MediaBrowserHelper(this, PlayerService.class);
        mMediaBrowserHelper.registerCallback(new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
//                Utils.debugLog("onPlaybackStateChanged");
//                sendStatus();
            }

            @Override
            public void onMetadataChanged(MediaMetadataCompat mediaMetadata) {
//                Utils.debugLog("onMetadataChanged");
//                sendStatus();
            }
        });
    }

    public void openDrawer() {
        runOnUiThread(() -> drawer.openDrawer(GravityCompat.START));
    }

    /**
     * Load application settings
     */
    private void loadSettings() {
        fullscreen = Storage.get(PrefKeys.FULLSCREEN, false, this);
    }

    /**
     * Send Play command to the service
     */
    public void playAudio() {
        mMediaBrowserHelper.getTransportControls().play();
    }

    /**
     * Send Stop command to the service
     */
    public void stopAudio() {
        mMediaBrowserHelper.getTransportControls().stop();
    }

    /**
     * Apply new status to the activity
     */
    public String getStatus() {
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
        return Status.getAsJson(this);
    }


    public void requestUiUpdate() {
        sendStatus();
    }

    /**
     * Send status to activity
     */
    private void sendStatus() {
        if (viewLoading) return;

        if (!Status.getIsOutdated(this)) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START);
            pushViewData("status", Status.getAsJson(this));
        }
    }

    public void setBackground(String backgroundSrc) {
        final Uri uri;

        if (!backgroundSrc.equals("solid")) {
            uri = Uri.parse(backgroundSrc);
        } else {
            uri = null;
        }

        runOnUiThread(() -> {
            if (bgPlayer == null) {
                return;
            }

            if (uri == null) {
                bgPlayer.setPlayWhenReady(false);
                bgPlayer.stop();
                bgPlayer.seekTo(0);
            } else {
                DataSource.Factory dsf = new DefaultDataSource.Factory(context);
                DataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory().setCache(bgCache).setUpstreamDataSourceFactory(dsf);
                MediaSource videoSource = new ProgressiveMediaSource.Factory(cacheDataSourceFactory).createMediaSource(MediaItem.fromUri(uri));

                bgPlayer.setPlayWhenReady(false);
                bgPlayer.setMediaSource(videoSource);
                bgPlayer.prepare();
                bgPlayer.setPlayWhenReady(true);
            }
        });
    }

    /**
     * Toggles fullscreen mode
     */
    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        Storage.set(PrefKeys.FULLSCREEN, fullscreen, this);
        setFullscreen(fullscreen);
    }

    /**
     * Switch data save mode
     *
     * @param lowQuality //
     */
    public void setAudioQuality(int lowQuality) {
        makeToast(String.format("Set audio quality to %s.", lowQuality == 1 ? "LOW" : "HIGH"));
        if (Status.getIsPlaying(this)) {
            stopAudio();
        }
    }

    /**
     * Set sleep timer
     *
     * @param time //
     */
    public void setSleepTimer(int time) {
        if (Status.getIsPlaying(this)) {
            long sleepTime = time == 0 ? 0 : System.currentTimeMillis() + ((long) time * 60 * 1000);
            Status.setSleepTime(sleepTime, this);

            if (time == 0) {
                makeToast(getString(R.string.timer_disabled));
            } else {
                makeToast(String.format(Locale.US, getString(R.string.timer_start), time));
            }

            pushViewData("status", Status.getAsJson(this));
        }
    }

    /**
     * @param action  //
     * @param payload //
     */
    public void pushViewData(String action, String payload) {
        Utils.debugLog("pushViewData: " + payload);
        String call = String.format("window['plaza'].push('%s', %s)", action, payload);
        runOnUiThread(() -> view.evaluateJavascript(call, null));
    }

    /**
     * Set app fullscreen
     *
     * @param fullscreen //
     */
    private void setFullscreen(final boolean fullscreen) {
        runOnUiThread(() -> {
            WindowCompat.setDecorFitsSystemWindows(getWindow(),!fullscreen);

            WindowInsetsControllerCompat windowInsetsController =
                    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
            if (windowInsetsController == null) {
                return;
            }

            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );

            if (fullscreen) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
            }
        });
    }

    /**
     *
     */
    public void reactionUpdate(int score) {
        Reaction.set(score, this);

        try {
            mMediaBrowserHelper.getTransportControls().sendCustomAction("REACTED", null);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void makeToast(final String msg) {
        runOnUiThread(() -> {
            if (toast != null) {
                toast.cancel();
            }

            toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    public void showWindow(View view) {
        String window = view.getTag().toString();
        if (window.equals("user-favorites") || window.equals("user")) {
            if (User.getToken(context).equals("")) {
                window = "user-login";
            }
        }

        window = JSONUtils.windowName(window);
        pushViewData("openWindow", window);
        drawer.closeDrawers();
    }
}