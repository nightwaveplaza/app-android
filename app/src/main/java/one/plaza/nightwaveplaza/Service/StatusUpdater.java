package one.plaza.nightwaveplaza.Service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;

import java.util.Objects;

import one.plaza.nightwaveplaza.Api2.Models.Status;
import one.plaza.nightwaveplaza.Api2.ApiCallback;
import one.plaza.nightwaveplaza.Api2.ApiClient;
import one.plaza.nightwaveplaza.Entities.Artwork;
import one.plaza.nightwaveplaza.Utils.Utils;

abstract class StatusUpdater {
    private boolean updating = false;
    private final Context mContext;
    private String tickToken;

    private boolean updateFailed = false;
    private long updateFailedTime = 0;

    StatusUpdater(Context context) {
        mContext = context;
    }

    abstract void stopByTimer();
    abstract void onNetworkFailed();
    abstract void onSongUpdated();
    abstract void onSongChanged();
    abstract void onNewArtwork();

    /**
     * Start status updating thread
     */
    void start() {
        tickToken = Utils.randomString(10);
        new Handler(Looper.getMainLooper()).post(new StatusTask(tickToken));
    }

    /**
     * Stop status updating thread
     */
    void stop() {
        tickToken = Utils.randomString(10);
    }

    private void reRunTask(String token) {
        new Handler(Looper.getMainLooper()).postDelayed(new StatusTask(token), 2500);
    }

    private class StatusTask implements Runnable {
        private final String runToken;
        private final ApiClient client;

        StatusTask(String token) {
            runToken = token;
            client = new ApiClient();
        }

        @Override
        public void run() {
            if (!Objects.equals(runToken, tickToken)) {
                Utils.debugLog("DENIED TOKEN: " + runToken, this);
                return;
            }

            if (Status.getIsSleepTimerPass(mContext)) {
                Status.setSleepTime(0, mContext);
                stopByTimer();
            }

            if (updateFailed) {
                if (System.currentTimeMillis() - updateFailedTime > 10000) {
                    updateFailed = false;
                } else {
                    reRunTask(runToken);
                    return;
                }
            }

            //  || Artwork.isArtworkDead(mContext)
            if (Status.getIsOutdated(mContext) && !updating) {
                fetch();
            } else {
                reRunTask(runToken);
            }
        }

        /**
         * Обновление статуса
         */
        private void fetch() {
            updating = true;

            client.getStatus(new ApiCallback() {
                @Override
                public void onSuccess(String response) {
                    Gson gson = new Gson();
                    Status newStatus = gson.fromJson(response, Status.class);
                    boolean songChanged = !Status.isEqual(newStatus, mContext);

                    Status.setSong(newStatus.song, mContext);
                    Status.setRecievedAt(System.currentTimeMillis(), mContext);
                    Status.setListeners(newStatus.listeners, mContext);

                    if (songChanged) {
                        onSongChanged();
                    } else {
                        onSongUpdated();
                    }

                    if (songChanged) {
                        Artwork.fetch(newStatus.song.artworkSrc, mContext, StatusUpdater.this::onNewArtwork);
                    }
                }

                @Override
                public void onFailure(String error) {
                    onNetworkFailed();
                    updateFailed = true;
                    updateFailedTime = System.currentTimeMillis();
                }

                @Override
                public void onEnd() {
                    updating = false;
                    reRunTask(runToken);
                }
            });
        }
    }
}
