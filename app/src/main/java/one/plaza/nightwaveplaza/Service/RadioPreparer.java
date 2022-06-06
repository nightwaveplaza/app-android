package one.plaza.nightwaveplaza.Service;

import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

public abstract class RadioPreparer implements MediaSessionConnector.PlaybackPreparer {

    public void prepare(boolean playWhenReady) {
    }

    @Override
    public long getSupportedPrepareActions() {
        return 0;
    }

    @Override
    public void onPrepare(boolean playWhenReady) {
        prepare(playWhenReady);
    }

    @Override
    public void onPrepareFromMediaId(String mediaId, boolean playWhenReady, @Nullable Bundle extras) {

    }

    @Override
    public void onPrepareFromSearch(String query, boolean playWhenReady, @Nullable Bundle extras) {

    }

    @Override
    public void onPrepareFromUri(Uri uri, boolean playWhenReady, @Nullable Bundle extras) {

    }

    @Override
    public boolean onCommand(Player player, String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {
        return false;
    }
}
