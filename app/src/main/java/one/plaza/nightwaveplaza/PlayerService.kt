package one.plaza.nightwaveplaza

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.plaza.nightwaveplaza.api.ApiClient
import one.plaza.nightwaveplaza.helpers.Keys
import one.plaza.nightwaveplaza.helpers.Utils


@UnstableApi
class PlayerService : MediaLibraryService() {

    private lateinit var player: Player
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var sleepTimer: CountDownTimer
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession.takeUnless { session ->
            session.invokeIsReleased
        }.also {
            if (it == null) {
                println("onGetSession returns null because the session is already released")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeSession()

        val notificationProvider: DefaultMediaNotificationProvider = CustomNotificationProvider()
        notificationProvider.setSmallIcon(R.drawable.ic_cat)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onDestroy() {
        super.onDestroy()
        closePlayer()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // https://github.com/androidx/media/issues/167#issuecomment-1615184728
        if (!player.playWhenReady) {
            closePlayer()
            stopSelf()
        }
    }

    private fun closePlayer() {
        Settings.isPlaying = false
        Settings.sleepTime = 0L
        player.removeListener(playerListener)
        player.release()
        mediaLibrarySession.release()

    }

    private fun initializePlayer() {
        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory().setUserAgent(
            Utils.getUserAgent())

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
        //exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)

        if (Settings.lowQualityAudio) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxAudioBitrate(70400)
                .setMaxVideoBitrate(70400)
                .build()
        }

        player = exoPlayer
    }

    private fun initializeSession() {
        val intent = Intent(
            applicationContext,
            MainActivity::class.java
        ).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
//        val pendingIntent = TaskStackBuilder.create(this).run {
//            addNextIntent(intent)
//            getPendingIntent(1422, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
//        }
        // This intent doesn't restart the activity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            createForwardingPlayer(player),
            CustomMediaLibrarySessionCallback()
        ).apply {
            setSessionActivity(pendingIntent)
        }.build()
    }

    private inner class CustomNotificationProvider :
        DefaultMediaNotificationProvider(this@PlayerService) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            val playCommandButton = CommandButton.Builder().apply {
                setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                if (player.isPlaying) {
                    setIconResId(R.drawable.ic_pause)
                } else {
                    setIconResId(R.drawable.ic_play)
                }
                setEnabled(true)
            }.build()
            val commandButtons: MutableList<CommandButton> = mutableListOf(
                playCommandButton
            )
            return ImmutableList.copyOf(commandButtons)
        }
    }

    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // add custom commands
            val connectionResult: MediaSession.ConnectionResult =
                super.onConnect(session, controller)
            val builder: SessionCommands.Builder =
                connectionResult.availableSessionCommands.buildUpon()

            builder.add(SessionCommand(Keys.SET_TIMER, Bundle.EMPTY))
//            builder.add(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY))
//            builder.add(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY))
//            builder.add(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY))
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
                Keys.SET_TIMER -> {
                    setSleepTimer()
                }
//                Keys.CMD_CANCEL_SLEEP_TIMER -> {
//                    cancelSleepTimer()
//                }
//                Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING -> {
//                    val resultBundle = Bundle()
//                    resultBundle.putLong(Keys.EXTRA_SLEEP_TIMER_REMAINING, sleepTimerTimeRemaining)
//                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
//                }
//                Keys.CMD_REQUEST_METADATA_HISTORY -> {
//                    val resultBundle = Bundle()
//                    resultBundle.putStringArrayList(Keys.EXTRA_METADATA_HISTORY, ArrayList(metadataHistory))
//                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
//                }
            }

            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private var playerListener: Player.Listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            Settings.isPlaying = isPlaying
            if (isPlaying && player.mediaMetadata.artist == null) {
                updateSongMetadata()
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateSongMetadata()
        }
    }

    private var updatingMetadata = false
    private fun updateSongMetadata() {
        updatingMetadata = true

        serviceScope.launch(Dispatchers.IO) {
            val client = ApiClient()
            val status: ApiClient.Status

            try {
                status = client.getStatus()
            } catch (err: Exception) {
                println("updating: network exception")
                updatingMetadata = false
                return@launch
            }

            updatingMetadata = false

            if (status.song.id.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val mi = player.currentMediaItem!!.mediaMetadata;
                    val mb = mi.buildUpon()
                        .setArtist(status.song.artist)
                        .setTitle(status.song.title)
                        .setArtworkUri(Uri.parse(status.song.artworkSrc))
                        .build()
                    player.replaceMediaItem(
                        player.currentMediaItemIndex,
                        player.currentMediaItem!!.buildUpon().setMediaMetadata(mb).build()
                    )
                }
            }
        }
    }

    private val loadErrorHandlingPolicy: DefaultLoadErrorHandlingPolicy =
        object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                if (loadErrorInfo.errorCount <= 5 && loadErrorInfo.exception is HttpDataSource.HttpDataSourceException) {
                    return 5000
                }
                return 1000
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 128
            }
        }

    fun setSleepTimer() {
        val time = Settings.sleepTime
        if (::sleepTimer.isInitialized) {
            sleepTimer.cancel()
        }

        if (time > 0) {
            sleepTimer = object : CountDownTimer(time - System.currentTimeMillis(), 1000) {
                override fun onFinish() {
                    // Check if finished and not cancelled
                    val sleepTime = Settings.sleepTime
                    if (sleepTime - System.currentTimeMillis() < 1000) {
                        if (player.isPlaying) {
                            player.stop() // todo: or pause
                        }
                    }
                    Settings.sleepTime = 0L
                }

                override fun onTick(millisUntilFinished: Long) {}
            }

            sleepTimer.start()
        }
    }

    private fun createForwardingPlayer(wrapped: Player): ForwardingPlayer {
        val fwPlayer = object : ForwardingPlayer(wrapped) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands()
                    .buildUpon()
                    .remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .remove(COMMAND_SEEK_BACK)
                    .remove(COMMAND_SEEK_FORWARD)
                    .remove(COMMAND_SEEK_TO_PREVIOUS)
                    .remove(COMMAND_SEEK_TO_NEXT)
                    .build()
            }

            // Credit https://github.com/androidx/media/issues/265#issuecomment-1465266989
            override fun getDuration(): Long {
                return -1L
            }
        }
        return fwPlayer
    }
}

private val MediaSession.invokeIsReleased: Boolean
    get() = try {
        // temporarily checked to debug
        // https://github.com/androidx/media/issues/422
        MediaSession::class.java.getDeclaredMethod("isReleased")
            .apply { isAccessible = true }
            .invoke(this) as Boolean
    } catch (e: Exception) {
        println("Couldn't check if it's released")
        false
    }