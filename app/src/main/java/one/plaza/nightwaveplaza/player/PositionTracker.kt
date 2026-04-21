package one.plaza.nightwaveplaza.player

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player

class PositionTracker(private val player: Player) {
    var position = 0L
    var length = 0L
    var localUpdatedAt = 0L
    var latestPosition = 0L

    fun getCurrentPosition(): Long {
        if (localUpdatedAt == 0L) return C.TIME_UNSET
        if (!player.isPlaying) return latestPosition

        val elapsedSinceUpdate = SystemClock.elapsedRealtime() - localUpdatedAt
        val actualPosition = position + elapsedSinceUpdate

        latestPosition = if (actualPosition > length) length else actualPosition
        return latestPosition
    }
}