package one.plaza.nightwaveplaza.Service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.PlayerNotificationManager.NotificationListener;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import one.plaza.nightwaveplaza.Api2.ApiCallback;
import one.plaza.nightwaveplaza.Api2.ApiClient;
import one.plaza.nightwaveplaza.Entities.Reaction;
import one.plaza.nightwaveplaza.Entities.User;
import one.plaza.nightwaveplaza.Events;
import one.plaza.nightwaveplaza.R;
import one.plaza.nightwaveplaza.Utils.Utils;

public class NotificationManager2 {
    private static final String CHANNEL_ID = "one.plaza.nightwaveplaza.channel";
    static final int NOTIFICATION_ID = 1420;

    MediaControllerCompat mediaController;
    PlayerNotificationManager notificationManager;

    boolean liked = false;
    Context mContext;

    NotificationManager2(Context mContext, MediaSessionCompat.Token sessionToken, NotificationListener notificationListener) {
        this.mContext = mContext;
        mediaController = new MediaControllerCompat(mContext, sessionToken);
        PlayerNotificationManager.Builder builder = new PlayerNotificationManager.Builder(mContext, NOTIFICATION_ID, CHANNEL_ID);
        builder.setChannelNameResourceId(R.string.notification_channel_name);
        builder.setChannelDescriptionResourceId(R.string.notification_channel_desc);
        builder.setMediaDescriptionAdapter(new DescriptionManager(mediaController));
        builder.setNotificationListener(notificationListener);
        builder.setCustomActionReceiver(new LikeActionReceiver());
        notificationManager = builder.build();
        notificationManager.setMediaSessionToken(sessionToken);
        notificationManager.setSmallIcon(R.drawable.ic_cat);
        notificationManager.setUsePlayPauseActions(true);
        notificationManager.setUseNextAction(false);
        notificationManager.setUsePreviousAction(false);
        notificationManager.setUseStopAction(true);
        notificationManager.setUseChronometer(false);
        notificationManager.setColor(0xA097E9);
        notificationManager.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

    public void hide() {
        notificationManager.setPlayer(null);
    }

    public void show(Player player) {
        notificationManager.setPlayer(player);
    }

    public void update() {
        liked = Reaction.getScore(mContext) > 0;
        notificationManager.invalidate();
    }

    public void sendReaction(int reaction) {
        if (!User.isLogged(mContext)) {
            showToast(mContext.getString(R.string.auth_need));
            return;
        }

        int score = Reaction.matchNewScore(reaction, mContext);

        (new ApiClient()).sendReaction(score, User.getToken(mContext), new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                Reaction.set(score, mContext);

                // Update notification
                update();

                // Update activity
                EventBus.getDefault().post(new Events.onReaction());
            }

            @Override
            public void onFailure(String error) {
                Utils.debugLog(error);
                showToast("Operation failed.");
            }
        });
    }

    private void showToast(String text) {
        Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
        toast.show();
    }

    private class DescriptionManager implements PlayerNotificationManager.MediaDescriptionAdapter {

        MediaControllerCompat mController;

        DescriptionManager(MediaControllerCompat controller) {
            mController = controller;
        }

        @Override
        public CharSequence getCurrentContentTitle(Player player) {
            return mController.getMetadata().getDescription().getTitle();
        }

        @Nullable
        @Override
        public CharSequence getCurrentContentText(Player player) {
            return mController.getMetadata().getDescription().getSubtitle();
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            return mController.getSessionActivity();
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
            return mController.getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private class LikeActionReceiver implements PlayerNotificationManager.CustomActionReceiver {

        @Override
        public Map<String, NotificationCompat.Action> createCustomActions(Context context, int instanceId) {
            NotificationCompat.Action likeAction = new NotificationCompat.Action(
                    R.drawable.ic_heart, "Like", createIntent(context, instanceId, "ACTION_LIKE")
            );
            NotificationCompat.Action likeActionR = new NotificationCompat.Action(
                    R.drawable.ic_heart_active, "Remove like", createIntent(context, instanceId, "ACTION_LIKE_R")
            );

            Map<String, NotificationCompat.Action> actionMap = new HashMap<>();
            actionMap.put("ACTION_LIKE", likeAction);
            actionMap.put("ACTION_LIKE_R", likeActionR);
            return actionMap;
        }

        @Override
        public List<String> getCustomActions(Player player) {
            List<String> customActions = new ArrayList<>();
            customActions.add(liked ? "ACTION_LIKE_R" : "ACTION_LIKE");
            return customActions;
        }

        @Override
        public void onCustomAction(Player player, String action, Intent intent) {
            Utils.debugLog(action);
            if (action.equals("ACTION_LIKE")) {
                sendReaction(1);
            }
            if (action.equals("ACTION_LIKE_R")) {
                sendReaction(0);
            }
        }

        private PendingIntent createIntent(Context context, int instanceId, String action) {
            Intent intent = new Intent(action).setPackage(context.getPackageName());
            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = PendingIntent.getBroadcast(context, instanceId, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getBroadcast(context, instanceId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            }
            return pendingIntent;
        }
    }
}
