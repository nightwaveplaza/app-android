package one.plaza.nightwaveplaza

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.JsonParseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.helpers.Keys
import one.plaza.nightwaveplaza.helpers.Utils
import timber.log.Timber
import java.io.IOException

/**
 * Provides background audio playback capabilities.
 * Handles HLS streaming, media notifications, and sleep timer functionality.
 */
@UnstableApi
class PlayerService : MediaLibraryService() {

    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession

    // Coroutine scope tied to service lifecycle for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Prevents concurrent metadata updates
    private val metadataUpdating = Mutex()
    private var isSessionReleased = false

    // Persistent sleep timer state with Settings sync
    private var sleepTimerDuration: Long = 0
        set(value) {
            field = value
            Settings.sleepTime = value
        }
    private lateinit var sleepTimer: CountDownTimer

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeSession()

        // Custom notification with branded icon
        val notificationProvider = CustomNotificationProvider()
        notificationProvider.setSmallIcon(R.drawable.ic_cat)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Handles app swipe-away behavior - only stops service if not playing
        // See: https://github.com/androidx/media/issues/167#issuecomment-1615184728
        if (!player.playWhenReady) {
            closePlayer()
            stopSelf()
        }

        Settings.isPlaying = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }
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
            createForwardingPlayer(player),
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
        Settings.sleepTime = 0L

        if (::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }

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
     * Customizes media notification buttons based on player state
     */
    private inner class CustomNotificationProvider :
        DefaultMediaNotificationProvider(this@PlayerService) {

        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            val playCommandButton = CommandButton.Builder(
                if (player.isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
            ).apply {
                setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                setEnabled(true)
            }.build()
            val commandButtons: MutableList<CommandButton> = mutableListOf(
                playCommandButton
            )
            return ImmutableList.copyOf(commandButtons)
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
                Keys.SET_TIMER -> setSleepTimer(args.getLong("time"))
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

            // Refresh metadata when starting playback if missing
            if (isPlaying && player.mediaMetadata.artist == null) {
                updateSongMetadata()
            }

            // Reset sleep timer state when not playing
            if (!isPlaying) {
                Settings.sleepTime = 0
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateSongMetadata()
        }
    }

    /**
     * Fetches current song information from API and updates player metadata
     * Uses mutex to prevent concurrent API calls
     */
    private fun updateSongMetadata() {
        serviceScope.launch(Dispatchers.IO) {
            metadataUpdating.withLock {
                runCatching {
                    val status = ApiClient().getStatus()
                    if (status.song.id.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            setNewMetadata(status.song)
                        }
                    }
                }.onFailure { error ->
                    when (error) {
                        is IOException -> Timber.w(error, "Network error during metadata update")
                        is JsonParseException -> Timber.e(error, "Invalid API response format")
                        else -> Timber.e(error, "Unknown error during metadata update")
                    }
                }
            }
        }
    }

    /**
     * Set new metadata to session
     */
    private fun setNewMetadata(song: ApiClient.Song) {
        player.currentMediaItem?.let { currentItem ->
            val updatedMetadata = currentItem.mediaMetadata.buildUpon()
                .setArtist(song.artist)
                .setTitle(song.title)
                .setArtworkUri(song.artworkSrc.toUri())
                .build()

            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(updatedMetadata)
                .build()

            player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
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

    /**
     * Configures and starts a sleep timer to auto-stop playback
     * @param time Duration in milliseconds until playback should stop
     */
    fun setSleepTimer(time: Long) {
        if (::sleepTimer.isInitialized) {
            Timber.d("Existed timer cancelled")
            sleepTimer.cancel()
        }

        if (time > 0) {
            sleepTimerDuration = time
            Timber.d("Sleep timer start $sleepTimerDuration")
            sleepTimer = object : CountDownTimer(sleepTimerDuration, 1000) {
                private val timerStartTime = SystemClock.elapsedRealtime()

                override fun onFinish() {
                    // Verify timer wasn't cancelled before stopping playback
                    if (SystemClock.elapsedRealtime() - timerStartTime >= sleepTimerDuration) {
                        Timber.d("Sleep timer finish")
                        if (player.isPlaying) {
                            player.stop() // todo: or pause
                        }
                    }
                    sleepTimerDuration = 0L
                }

                override fun onTick(millisUntilFinished: Long) {
                    Timber.d("Sleep timer tick: $millisUntilFinished")
                    sleepTimerDuration = millisUntilFinished
                }
            }

            sleepTimer.start()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return if (!isSessionReleased) {
            mediaLibrarySession
        } else {
            Timber.w("onGetSession returns null because the session is already released")
            null
        }
    }

    /**
     * Creates a player wrapper that disables seek functionality
     * Appropriate for live streaming content where seeking isn't applicable
     */
    private fun createForwardingPlayer(wrapped: Player): ForwardingPlayer {
        return object : ForwardingPlayer(wrapped) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_BACK)
                    .remove(COMMAND_SEEK_FORWARD)
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .build()
            }

            // Disable time reporting for live streams
            override fun getDuration(): Long = C.TIME_UNSET
            override fun getCurrentPosition(): Long = C.TIME_UNSET
            override fun getContentPosition(): Long = C.TIME_UNSET
        }
    }
}