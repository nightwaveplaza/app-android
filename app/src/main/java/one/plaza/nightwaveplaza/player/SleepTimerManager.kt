package one.plaza.nightwaveplaza.player

import android.os.CountDownTimer
import android.os.SystemClock
import one.plaza.nightwaveplaza.Settings
import timber.log.Timber

class SleepTimerManager(private val onStopPlayback: () -> Unit) {
    private var sleepTimer: CountDownTimer? = null

    var remainingTimeMs: Long = 0L
        private set

    fun setTimer(targetTime: Long) {
        cancelTimer()

        val durationMs = targetTime - System.currentTimeMillis()

        if (durationMs <= 0) {
            Timber.w("Ignored sleep timer: targetTime is in the past or zero")
            return
        }

        Timber.d("Sleep timer set to fire at $targetTime (in $durationMs ms)")
        Settings.sleepTargetTime = targetTime

        startCountdown(durationMs)
    }

    fun restoreTimerIfNeeded() {
        val targetTime = Settings.sleepTargetTime
        if (targetTime == 0L) return

        val durationMs = targetTime - System.currentTimeMillis()
        if (durationMs > 0) {
            Timber.d("Sleep timer restored: $durationMs ms left")
            startCountdown(durationMs)
        } else {
            Timber.d("Sleep timer expired while app was dead")
            cancelTimer()
            onStopPlayback()
        }
    }

    private fun startCountdown(durationMs: Long) {
        remainingTimeMs = durationMs
        sleepTimer = object : CountDownTimer(durationMs, 1000) {
            private val timerStartTime = SystemClock.elapsedRealtime()

            override fun onFinish() {
                if (SystemClock.elapsedRealtime() - timerStartTime >= durationMs) {
                    Timber.d("Sleep timer finished")
                    onStopPlayback()
                }
                resetState()
            }

            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMs = millisUntilFinished
            }
        }.apply { start() }
    }

    fun cancelTimer() {
        if (sleepTimer != null || Settings.sleepTargetTime != 0L) {
            sleepTimer?.cancel()
            Timber.d("Sleep timer cancelled")
            resetState()
        }
    }

    private fun resetState() {
        sleepTimer = null
        remainingTimeMs = 0L
        Settings.sleepTargetTime = 0L
    }
}