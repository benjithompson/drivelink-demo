package com.drivelink.demo.timing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.drivelink.core.domain.TimingLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The cold start marker `DL_TIMING cold_start_ms=<ms>`: from process start
 * ([Process.getStartUptimeMillis]) to the end of the first frame that MainActivity draws.
 *
 * Logged at most once per process, and only when MainActivity is the first activity of the
 * process (a launch after DemoConfigActivity is not a cold start of the app UI).
 */
object ColdStart {
    const val TIMING_NAME = "cold_start_ms"

    private val done = AtomicBoolean(false)

    /** Another activity started first in this process. No cold start mark for this process. */
    fun skip() {
        done.set(true)
    }

    /** Call from MainActivity.onCreate after setContent. */
    fun onFirstFrame(activity: Activity, timing: TimingLog) {
        if (done.get()) return
        val decor = activity.window.decorView
        val main = Handler(Looper.getMainLooper())
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                // A draw listener cannot remove itself inside onDraw. postAtFrontOfQueue runs
                // right after this frame is drawn.
                main.postAtFrontOfQueue {
                    removeLater(decor, this)
                    if (done.compareAndSet(false, true)) {
                        val ms = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
                        timing.mark(TIMING_NAME, ms, emptyMap())
                        activity.reportFullyDrawn()
                    }
                }
            }
        }
        decor.viewTreeObserver.addOnDrawListener(listener)
    }

    private fun removeLater(view: View, listener: ViewTreeObserver.OnDrawListener) {
        view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener) }
    }
}
