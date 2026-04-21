package one.plaza.nightwaveplaza.player

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline

@UnstableApi
class LiveStreamWrapper(player: Player): ForwardingPlayer(player) {
    val positionTracker = PositionTracker(player)

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


    override fun isCurrentMediaItemDynamic(): Boolean = false
    override fun isCurrentMediaItemLive(): Boolean = false

    override fun getContentDuration(): Long = if (positionTracker.length > 0) positionTracker.length else C.TIME_UNSET
    override fun getDuration(): Long = contentDuration

    override fun getCurrentPosition(): Long = positionTracker.getCurrentPosition()
    override fun getContentPosition(): Long = positionTracker.getCurrentPosition()

    override fun getBufferedPosition(): Long = duration
    override fun getTotalBufferedDuration(): Long = duration

    override fun getCurrentTimeline(): Timeline {
        val originalTimeline = super.getCurrentTimeline()
        if (originalTimeline.isEmpty) return originalTimeline

        return object : ForwardingTimeline(originalTimeline) {
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                super.getWindow(windowIndex, window, defaultPositionProjectionUs)
                window.durationUs = if (positionTracker.length > 0) positionTracker.length * 1000L else C.TIME_UNSET
                window.presentationStartTimeMs = C.TIME_UNSET
                window.windowStartTimeMs = C.TIME_UNSET
                window.positionInFirstPeriodUs = 0L
                window.isDynamic = false
                window.liveConfiguration = null
                window.isSeekable = true
                return window
            }
        }
    }
}