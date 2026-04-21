package one.plaza.nightwaveplaza

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import one.plaza.nightwaveplaza.helpers.Keys
import one.plaza.nightwaveplaza.helpers.Utils
import one.plaza.nightwaveplaza.player.LiveStreamWrapper
import one.plaza.nightwaveplaza.player.MetadataSyncManager
import one.plaza.nightwaveplaza.player.SleepTimerManager
import timber.log.Timber

/**
 * Provides background audio playback capabilities.
 * Handles HLS streaming, media notifications, and sleep timer functionality.
 */
@UnstableApi
class PlayerService : MediaLibraryService() {
    private lateinit var player: Player
    private lateinit var liveWrapper: LiveStreamWrapper
    private lateinit var syncManager: MetadataSyncManager
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var mediaLibrarySession: MediaLibrarySession

    // Coroutine scope tied to service lifecycle for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isSessionReleased = false

    override fun onCreate() {
        super.onCreate()

        // Init player
        initializePlayer()

        // Create wrappers and managers
        liveWrapper = LiveStreamWrapper(player)
        syncManager = MetadataSyncManager(player, liveWrapper) {
            if (!isSessionReleased) mediaLibrarySession else null
        }
        sleepTimerManager = SleepTimerManager {
            // what to do on sleep timer
            if (player.isPlaying) player.stop()
        }
        sleepTimerManager.restoreTimerIfNeeded()

        // Init session
        initializeSession()

        // Custom notification with branded icon
        val notificationProvider =  DefaultMediaNotificationProvider
            .Builder(this)
            .setNotificationId(4208)
            .setChannelId("nightwave_plaza_channel")
            .setChannelName(R.string.app_name)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_cat_icon)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        if (!player.isPlaying) {
            player.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        sleepTimerManager.cancelTimer()
        closePlayer()
        Settings.isPlaying = false
    }

    /**
     * Configures ExoPlayer for HLS streaming with resilient error handling
     * and appropriate audio focus management
     */
    private fun initializePlayer() {
        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory().setUserAgent(
            Utils.getUserAgent()
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer: ExoPlayer = ExoPlayer.Builder(this).apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            setMediaSourceFactory(
                HlsMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            )
        }.build()

        exoPlayer.addListener(playerListener)

        // Apply bandwidth constraints for low-quality mode
        if (Settings.lowQualityAudio) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxAudioBitrate(70400)
                .build()
        }

        player = exoPlayer
    }

    /**
     * Initializes MediaLibrarySession with proper activity navigation intent
     */
    private fun initializeSession() {
        // Single-top intent prevents multiple activity instances
        val intent = Intent(
            applicationContext,
            MainActivity::class.java
        ).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            liveWrapper,
            CustomMediaLibrarySessionCallback()
        ).apply {
            setSessionActivity(pendingIntent)
        }.build()

        isSessionReleased = false
    }

    /**
     * Performs clean shutdown of player resources and service state
     */
    private fun closePlayer() {
        Settings.isPlaying = false

        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.release()
        }

        if (::mediaLibrarySession.isInitialized && !isSessionReleased) {
            mediaLibrarySession.release()
            isSessionReleased = true
        }
    }

    /**
     * Handles custom commands from clients, especially sleep timer controls
     */
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult: MediaSession.ConnectionResult =
                super.onConnect(session, controller)
            val builder: SessionCommands.Builder =
                connectionResult.availableSessionCommands.buildUpon()

            builder.add(SessionCommand(Keys.SET_TIMER, Bundle.EMPTY))
            return MediaSession.ConnectionResult.accept(
                builder.build(),
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Keys.SET_TIMER -> sleepTimerManager.setTimer(args.getLong("time"))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

    }

    /**
     * Monitors player state changes and triggers metadata updates
     */
    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            Settings.isPlaying = isPlaying
            if (isPlaying) {
                syncManager.fetchAndUpdate(serviceScope)
            } else {
                sleepTimerManager.cancelTimer()
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            syncManager.fetchAndUpdate(serviceScope)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Catch BehindLiveWindowException is music was paused too long
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // Re-initialize player at the live edge.
                player.seekToDefaultPosition()
                player.prepare()
            } else {
                // Handle other errors
            }
        }
    }

    /**
     * Custom error handling policy with aggressive retries for network issues
     */
    private val loadErrorHandlingPolicy: DefaultLoadErrorHandlingPolicy =
        object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                if (loadErrorInfo.errorCount <= 5 && loadErrorInfo.exception is HttpDataSource.HttpDataSourceException) {
                    return 5000
                }
                return 1000
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 10
        }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return if (!isSessionReleased) {
            mediaLibrarySession
        } else {
            Timber.w("onGetSession returns null because the session is already released")
            null
        }
    }
}