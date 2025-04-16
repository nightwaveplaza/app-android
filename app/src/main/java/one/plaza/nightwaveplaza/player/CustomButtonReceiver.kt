package one.plaza.nightwaveplaza.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver

@UnstableApi
class CustomButtonReceiver: MediaButtonReceiver() {
    /**
     * Don't start the playback when app was completely closed and MEDIA_PLAY_PAUSE received
     */
    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean {
        return false
    }
}