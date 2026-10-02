package com.tjshea.vigilant.app

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import com.tjshea.vigilant.data.diag.FrameStats

/**
 * Every frame Vigilant's screen draws, timed by Android and filed by what the app was doing ([FrameStats]) for Diagnostics (Tj, 2026-10-02: "When
 * I scan with vigilant scanner, the entire app becomes laggy still"): the lag measured on his phone. Runs while the screen is in front, on a thread
 * of its own: the main thread only hands Android the listener.
 */
internal class FrameMeter(private val activity: Activity, private val c: AppContainer) {
    private var thread: HandlerThread? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        // The first frame of a window is its layout from nothing (counted as the cold start, not as a stutter).
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6
        val deadline = (if (android.os.Build.VERSION.SDK_INT >= 31) m.getMetric(FrameMetrics.DEADLINE) / 1e6 else 0.0).takeIf { it > 0.0 } ?: FALLBACK_DEADLINE_MS
        c.frames.add(FrameStats.activity(c.runner.running, c.focus.active(), c.cno.state.value.refreshing), total, deadline)
    }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("vigilant-frames").also { it.start() }
        thread = t
        runCatching { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(t.looper)) }
    }

    fun stop() {
        val t = thread ?: return
        runCatching { activity.window.removeOnFrameMetricsAvailableListener(listener) }
        t.quitSafely()
        thread = null
    }

    private companion object {
        /** A 60 Hz frame, for an Android without the frame's own deadline (before 12). */
        const val FALLBACK_DEADLINE_MS = 1000.0 / 60.0
    }
}
