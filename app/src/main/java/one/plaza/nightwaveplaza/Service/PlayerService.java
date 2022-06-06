// Refactored with a huge help from
// https://github.com/y20k/transistor/blob/master/app/src/main/java/org/y20k/transistor/playback/PlayerService.kt

package one.plaza.nightwaveplaza.Service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ForwardingPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.NotificationListener;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import one.plaza.nightwaveplaza.Api2.Models.Status;
import one.plaza.nightwaveplaza.Entities.Artwork;
import one.plaza.nightwaveplaza.Entities.Reaction;
import one.plaza.nightwaveplaza.Events;
import one.plaza.nightwaveplaza.MainActivity;
import one.plaza.nightwaveplaza.R;
import one.plaza.nightwaveplaza.Utils.PrefKeys;
import one.plaza.nightwaveplaza.Utils.Storage;
import one.plaza.nightwaveplaza.Utils.Utils;

@SuppressLint("Registered")
public class PlayerService extends MediaBrowserServiceCompat {
    private static final String TAG = PlayerService.class.getSimpleName();
    private static final String STREAM_URL = "http://radio.plaza.one/ogg";
    private static final String STREAM_URL_LOWRES = "http://radio.plaza.one/ogg_low";
    public final static String NOTIFICATION_INTENT = "one.plaza.nightwaveplaza.notification_action";

    private Context mContext;
    private StatusUpdater mStatusUpdater = null;
    private Bitmap artwork;

    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;
    private ForwardingPlayer forwardingPlayer;
    private ExoPlayer mPlayer;
    private NotificationManager2 mNotificationManager2;
    private boolean isForegroundService = false;

    Player.Listener mPlayerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            Utils.debugLog("Player.listener onIsPlayingChanged");
            Player.Listener.super.onIsPlayingChanged(isPlaying);
            if (isPlaying) {
                mNotificationManager2.show(forwardingPlayer);
                Status.setIsPlaying(true, mContext);
            } else {
                if (mPlayer.getPlaybackState() != Player.STATE_BUFFERING) {
                    Status.setIsPlaying(false, mContext);
                }
            }

            broadcastPlayback();
            updateMetadata();
        }
    };

    /**
     * Создание сервиса
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;

        mPlayer = createPlayer();
        mPlayer.addListener(mPlayerListener);
        forwardingPlayer = createForwardingPlayer();

        createMediaSession();

        NotificationListener notificationListener = createNotificationListener();
        mNotificationManager2 = new NotificationManager2(this, mediaSession.getSessionToken(), notificationListener);

        // Load latest artwork available
        if (artwork == null) {
            Artwork.getBitmap(mContext, bitmap -> artwork = bitmap);
        }

        if (Status.getIsPlaying(this) && !mPlayer.isPlaying()) {
            Status.setIsPlaying(false, this);
        }

        createUpdater();
    }

    /**
     * Уничтожение сервиса и всего связанного с ним
     * Вызывается не всегда
     * TODO PROPER DESTROY!!!
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        mStatusUpdater.stop();
        Status.stop(mContext);

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }

        mPlayer.removeListener(mPlayerListener);
        mPlayer.setPlayWhenReady(false);
        mPlayer.release();
        mPlayer = null;

        Utils.debugLog("onDestroy: ExoAdapter stopped, and MediaSession released", TAG);
    }

    /**
     * Приложение удалено из списка приложений
     *
     * @param rootIntent //
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    /**
     * Create mPlayer
     */
    private ExoPlayer createPlayer() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setContentType(C.CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();

        return new ExoPlayer.Builder(mContext).
                setHandleAudioBecomingNoisy(true).
                setAudioAttributes(attributes, true).
                build();
    }

    /**
     * Create media session
     */
    public void createMediaSession() {
        mediaSession = new MediaSessionCompat(this, getPackageName());
        mediaSessionConnector = new MediaSessionConnector(mediaSession);
        mediaSessionConnector.setEnabledPlaybackActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_STOP
        );

        mediaSessionConnector.setPlaybackPreparer(new RadioPreparer() {
            @Override
            public void prepare(boolean playWhenReady) {
                preparePlayer(playWhenReady);
            }
        });

        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setSessionActivity(createContentIntent());
        mediaSessionConnector.setPlayer(forwardingPlayer);

        // Executes everytime after invalidate
        mediaSessionConnector.setQueueNavigator(new TimelineQueueNavigator(mediaSession) {
            @NonNull
            @Override
            public MediaDescriptionCompat getMediaDescription(@NonNull Player player, int windowIndex) {
                Utils.debugLog("getMediaDescription");
                return Status.getAsMediaDescriptor(mContext, artwork);
            }
        });

        mediaSession.setActive(true);
    }

    /**
     * Preparing player for playback
     *
     * @param playWhenReady //
     */
    public void preparePlayer(boolean playWhenReady) {
        boolean lowQuality = Storage.get(PrefKeys.AUDIO_QUALITY, 0, mContext) == 1;
        String streamUrl = lowQuality ? STREAM_URL_LOWRES : STREAM_URL;
        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
        factory.setUserAgent(Util.getUserAgent(mContext, mContext.getString(R.string.app_name)));
        factory.createDataSource();
        MediaSource audioSource = new ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(streamUrl));
        mPlayer.setMediaSource(audioSource);
        mPlayer.prepare();
        mPlayer.setPlayWhenReady(playWhenReady);
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private PendingIntent createContentIntent() {
        Intent openUI = new Intent(mContext, MainActivity.class);
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getActivity(mContext, 1422, openUI, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getActivity(mContext, 1422, openUI, PendingIntent.FLAG_CANCEL_CURRENT);
        }
    }

    public ForwardingPlayer createForwardingPlayer() {
        return new ForwardingPlayer(mPlayer) {
            @Override
            public void play() {
                Utils.debugLog("ForwardingPlayer: play");
                super.play();
            }

            @Override
            public void stop() {
                Utils.debugLog("ForwardingPlayer: stop");
                mPlayer.stop();
                mNotificationManager2.hide();
            }

            @Override
            public void pause() {
                Utils.debugLog("ForwardingPlayer: pause");
                mPlayer.stop();
            }
        };
    }

    public NotificationListener createNotificationListener() {
        return new NotificationListener() {
            @Override
            public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                NotificationListener.super.onNotificationCancelled(notificationId, dismissedByUser);
                stopForeground(true);
                isForegroundService = false;
                // TODO need stopself??
                stopSelf();
            }

            @Override
            public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                if (ongoing && !isForegroundService) {
                    ContextCompat.startForegroundService(PlayerService.this, new Intent(PlayerService.this, PlayerService.class));
                    startForeground(notificationId, notification);
                    isForegroundService = true;
                }
            }
        };
    }

    private void updateMetadata() {
        mediaSessionConnector.invalidateMediaSessionQueue();
        mediaSessionConnector.invalidateMediaSessionMetadata();
        mNotificationManager2.update();
    }

    /**
     * Апдейтер статуса
     */
    private void createUpdater() {
        mStatusUpdater = new StatusUpdater(mContext) {
            @Override
            void stopByTimer() {
                forwardingPlayer.stop();
            }

            @Override
            void onNetworkFailed() {
                broadcastNoInternet();
            }

            @Override
            void onSongUpdated() {
                broadcastPlayback();
            }

            @Override
            void onSongChanged() {
                Reaction.clear(mContext);
                broadcastPlayback();
            }

            @Override
            void onNewArtwork() {
                Artwork.getBitmap(mContext, bitmap -> {
                    artwork = Utils.getSmallerBitmap(bitmap);
                    broadcastArtwork();
                    if (Status.getIsPlaying(mContext)) {
                        updateMetadata();
                    }
                });
            }
        };

        mStatusUpdater.start();
    }

    private void broadcastNoInternet() {
        EventBus.getDefault().post(new Events.onToast("""
                Can't connect to the server.
                Check your internet connection and restart the application.
                (server also could be down)"""
        ));
    }

    private void broadcastPlayback() {
        Utils.debugLog("Broadcast playback");
        EventBus.getDefault().post(new Events.onPlayback());
    }

    private void broadcastArtwork() {
        EventBus.getDefault().post(new Events.onArtwork());
    }

    /**
     * Not important for general audio service, required for class
     */

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        if (TextUtils.equals(clientPackageName, getPackageName())) {
            return new BrowserRoot(getString(R.string.app_name), null);
        }

        return null;
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(null);
    }
}
