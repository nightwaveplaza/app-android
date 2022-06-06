package one.plaza.nightwaveplaza.Ui;

import android.content.ComponentName;
import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;

public class MediaBrowserHelper {
    private String TAG = "MediaBrowserHelper";
    private final Context mContext;
    private final Class<? extends MediaBrowserServiceCompat> mMediaBrowserServiceClass;

    private final List<MediaControllerCompat.Callback> mCallbackList = new ArrayList<>();

    private final MediaBrowserConnectionCallback mMediaBrowserConnectionCallback;
    private final MediaControllerCallback mMediaControllerCallback;
    private final MediaBrowserSubscriptionCallback mMediaBrowserSubscriptionCallback;

    private MediaBrowserCompat mMediaBrowser;

    @Nullable
    private MediaControllerCompat mMediaController;

    public MediaBrowserHelper(Context context, Class<? extends MediaBrowserServiceCompat> serviceClass) {
        mContext = context;
        mMediaBrowserServiceClass = serviceClass;

        mMediaBrowserConnectionCallback = new MediaBrowserConnectionCallback();
        mMediaControllerCallback = new MediaControllerCallback();
        mMediaBrowserSubscriptionCallback = new MediaBrowserSubscriptionCallback();
    }

    public void onStart() {
        if (mMediaBrowser == null) {
            mMediaBrowser =
                    new MediaBrowserCompat(
                            mContext,
                            new ComponentName(mContext, mMediaBrowserServiceClass),
                            mMediaBrowserConnectionCallback,
                            null);
            mMediaBrowser.connect();
        }
    }

    public void onStop() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mMediaControllerCallback);
            mMediaController = null;
        }
        if (mMediaBrowser != null && mMediaBrowser.isConnected()) {
            mMediaBrowser.disconnect();
            mMediaBrowser = null;
        }
        resetState();
        Log.d(TAG, "onStop: Releasing MediaController, Disconnecting from MediaBrowser");
    }

    /**
     * The internal state of the app needs to revert to what it looks like when it started before
     * any connections to the MusicService happens via the {@link MediaSessionCompat}.
     */
    private void resetState() {
        performOnAllCallbacks(callback -> callback.onPlaybackStateChanged(null));
        Log.d(TAG, "resetState: ");
    }

    public MediaControllerCompat.TransportControls getTransportControls() throws IllegalStateException {
        if (mMediaController == null) {
            Log.d(TAG, "getTransportControls: MediaController is null!");
            throw new IllegalStateException("MediaController is null!");
        }
        return mMediaController.getTransportControls();
    }

    public void registerCallback(MediaControllerCompat.Callback callback) {
        if (callback != null) {
            mCallbackList.add(callback);

            // Update with the latest metadata/playback state.
            if (mMediaController != null) {
                final MediaMetadataCompat metadata = mMediaController.getMetadata();
                if (metadata != null) {
                    callback.onMetadataChanged(metadata);
                }

                final PlaybackStateCompat playbackState = mMediaController.getPlaybackState();
                if (playbackState != null) {
                    callback.onPlaybackStateChanged(playbackState);
                }
            }
        }
    }

    private void performOnAllCallbacks(@NonNull CallbackCommand command) {
        for (MediaControllerCompat.Callback callback : mCallbackList) {
            if (callback != null) {
                command.perform(callback);
            }
        }
    }

    /**
     * Helper for more easily performing operations on all listening clients.
     */
    private interface CallbackCommand {
        void perform(@NonNull MediaControllerCompat.Callback callback);
    }

    // Receives callbacks from the MediaBrowser when it has successfully connected to the
    // MediaBrowserService (MusicService).
    private class MediaBrowserConnectionCallback extends MediaBrowserCompat.ConnectionCallback {

        // Happens as a result of onStart().
        @Override
        public void onConnected() {
            // Get a MediaController for the MediaSession.
            mMediaController =
                    new MediaControllerCompat(mContext, mMediaBrowser.getSessionToken());
            mMediaController.registerCallback(mMediaControllerCallback);

            // Sync existing MediaSession state to the UI.
            mMediaControllerCallback.onMetadataChanged(mMediaController.getMetadata());
            mMediaControllerCallback.onPlaybackStateChanged(
                    mMediaController.getPlaybackState());

            //MediaBrowserHelper.this.onConnected(mMediaController);

            mMediaBrowser.subscribe(mMediaBrowser.getRoot(), mMediaBrowserSubscriptionCallback);
        }
    }

    // Receives callbacks from the MediaBrowser when the MediaBrowserService has loaded new media
    // that is ready for playback.
    public static class MediaBrowserSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback {
    }

    // Receives callbacks from the MediaController and updates the UI state,
    // i.e.: Which is the current item, whether it's playing or paused, etc.
    private class MediaControllerCallback extends MediaControllerCompat.Callback {

        @Override
        public void onMetadataChanged(final MediaMetadataCompat metadata) {
            performOnAllCallbacks(callback -> callback.onMetadataChanged(metadata));
        }

        @Override
        public void onPlaybackStateChanged(@Nullable final PlaybackStateCompat state) {
            performOnAllCallbacks(callback -> callback.onPlaybackStateChanged(state));
        }

        // This might happen if the MusicService is killed while the Activity is in the
        // foreground and onStart() has been called (but not onStop()).
        @Override
        public void onSessionDestroyed() {
            resetState();
            onPlaybackStateChanged(null);

            //MediaBrowserHelper.this.onDisconnected();
        }
    }
}