package com.tjshea.vigilant.app

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/**
 * The floating widget: Vigilant's bets in a small window drawn over other apps (Novig), which,
 * unlike picture-in-picture, takes touches (Tj, 2026-09-26: permanent up/down buttons, tap a bet
 * to open it in Novig, mark bets placed; RESEARCH.md §17, §20). It needs Android's "Display over
 * other apps" ([allowed]); without it the app falls back to picture-in-picture.
 *
 * It lives exactly as long as it's showing: [show] adds the window, [hide] removes it. The app's
 * CNO reads follow [onWatching], which is true only while the window is up, not shrunk to a
 * bubble, and the screen is on and unlocked, so nothing is read with the phone in a pocket.
 * [MainActivity] hides it when Vigilant comes back to the front and when Vigilant is closed.
 *
 * Moving and resizing (Tj, 2026-09-26 ~23:45Z) happen here, before the list sees a touch
 * ([WidgetGestures]): two fingers anywhere spread or pinch it and move it; one finger on a corner
 * handle resizes it; one finger on the frame around it or on its top bar drags it. The window is
 * the widget plus a [FRAME_DP] frame on every side, which is where the corner handles are drawn
 * (inside the widget's rounded corners they were cut off).
 *
 * Where it sits and how big it is are kept between uses ([PREFS]).
 */
class FloatingWidget(
    context: Context,
    private val onWatching: (Boolean) -> Unit,
    private val content: @Composable (FloatingWidget) -> Unit,
) {
    private val app = context.applicationContext
    private val wm = app.getSystemService(WindowManager::class.java)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val density = app.resources.displayMetrics.density

    private var root: TouchFrame? = null
    private var owner: WindowOwner? = null
    private var receiver: BroadcastReceiver? = null

    /** Shrunk to a bubble (no reads while it is). */
    var minimized by mutableStateOf(false)
        private set

    /** The bet whose Novig link is being looked up. */
    var opening by mutableStateOf<String?>(null)

    val showing: Boolean get() = root != null

    private val params = WindowManager.LayoutParams(
        savedW(),
        savedH(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes the keyboard or the back button from Novig; touches outside it go to Novig.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(KEY_X, -1).takeIf { it >= 0 } ?: (screenW() - width).coerceAtLeast(0)
        y = prefs.getInt(KEY_Y, -1).takeIf { it >= 0 } ?: dp(DEFAULT_Y_DP)
        title = "Vigilant widget"
    }

    /** Puts the window up (no-op if it is, or without the permission). False if it couldn't. */
    fun show(): Boolean {
        if (root != null) return true
        if (!allowed(app)) return false
        // Each time it comes up it's the full widget, never a bubble left from last time (a
        // bubble reads nothing, which would look like a stuck list).
        if (minimized) {
            minimized = false
            params.width = savedW()
            params.height = savedH()
        }
        val o = WindowOwner()
        val compose = ComposeView(app).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { content(this@FloatingWidget) }
        }
        val frame = TouchFrame(app).apply {
            // The frame is the view in the window, so the Compose view looks its owners up from it too.
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        clampToScreen()
        val added = runCatching { wm.addView(frame, params) }.isSuccess
        if (!added) {
            o.destroy()
            return false
        }
        owner = o
        root = frame
        listenToScreen()
        app.registerComponentCallbacks(rotation)
        report()
        return true
    }

    /** Turning the phone: the window is pulled back inside the new screen. */
    private val rotation = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
            if (root == null) return
            if (!minimized) {
                params.width = params.width.coerceAtMost(screenW())
                params.height = params.height.coerceAtMost(screenH())
            }
            clampToScreen()
            relayout()
        }

        @Deprecated("Required by the interface")
        override fun onLowMemory() = Unit
    }

    /** Takes the window down; CNO stops being read for it at once. */
    fun hide() {
        val v = root ?: return
        root = null
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        runCatching { app.unregisterComponentCallbacks(rotation) }
        runCatching { wm.removeViewImmediate(v) }
        owner?.destroy()
        owner = null
        opening = null
        onWatching(false)
    }

    fun minimize() {
        minimized = true
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        relayout()
        report()
        save()
    }

    fun expand() {
        minimized = false
        params.width = savedW()
        params.height = savedH()
        clampToScreen()
        relayout()
        report()
    }

    /** Remembers where the window is (and how big, unless it's a bubble). */
    fun save() {
        prefs.edit().apply {
            putInt(KEY_X, params.x)
            putInt(KEY_Y, params.y)
            if (!minimized && params.width > 0 && params.height > 0) {
                putInt(KEY_WIN_W, (params.width / density).roundToInt())
                putInt(KEY_WIN_H, (params.height / density).roundToInt())
            }
        }.apply()
    }

    /** The window's saved size: v0.15.0 saved the widget without its frame. */
    private fun savedW() = dp(prefs.getInt(KEY_WIN_W, prefs.getInt(KEY_OLD_W, DEFAULT_W_DP) + 2 * FRAME_DP))
    private fun savedH() = dp(prefs.getInt(KEY_WIN_H, prefs.getInt(KEY_OLD_H, DEFAULT_H_DP) + 2 * FRAME_DP))

    private fun relayout() {
        val v = root ?: return
        runCatching { wm.updateViewLayout(v, params) }
    }

    private fun clampToScreen() {
        val w = if (params.width > 0) params.width else dp(120)
        val h = if (params.height > 0) params.height else dp(40)
        params.x = params.x.coerceIn(0, (screenW() - w).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH() - h).coerceAtLeast(0))
    }

    /** The window as it is now (a bubble's size is whatever it drew). */
    private fun rect(): WidgetRect {
        val v = root
        val w = if (params.width > 0) params.width else v?.width ?: 0
        val h = if (params.height > 0) params.height else v?.height ?: 0
        return WidgetRect(params.x, params.y, w, h)
    }

    private fun place(r: WidgetRect) {
        params.x = r.x
        params.y = r.y
        if (!minimized) {
            params.width = r.w
            params.height = r.h
        }
        relayout()
    }

    private fun limits() = WidgetGeometry.Limits(dp(MIN_W_DP + 2 * FRAME_DP), dp(MIN_H_DP + 2 * FRAME_DP), screenW(), screenH())

    /** Watching = up, not a bubble, screen on and unlocked. */
    private fun report() {
        val watching = root != null && !minimized && screenUsable()
        owner?.setActive(watching)
        onWatching(watching)
    }

    private fun screenUsable(): Boolean {
        val power = app.getSystemService(PowerManager::class.java)
        val keyguard = app.getSystemService(KeyguardManager::class.java)
        return (power?.isInteractive ?: true) && !(keyguard?.isKeyguardLocked ?: false)
    }

    private fun listenToScreen() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = report()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    private fun screenW() = app.resources.displayMetrics.widthPixels
    private fun screenH() = app.resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * density).roundToInt()

    /**
     * The window's root: sees every touch before the list does and hands moves, corner drags and
     * two-finger pinches to [WidgetGestures] in screen pixels. The list gets a cancel when one of
     * those takes over; taps and scrolling reach it untouched.
     */
    @SuppressLint("ViewConstructor")
    private inner class TouchFrame(context: Context) : FrameLayout(context) {
        private val gestures = WidgetGestures(
            slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
            limits = ::limits,
            current = ::rect,
            apply = ::place,
            finished = ::save,
            canPinch = { !minimized },
        )

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = handle(ev)

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            // Only reached for touches the list didn't take (the frame, the top bar) or once the
            // widget took the gesture over: keep receiving the rest of it.
            handle(ev)
            return true
        }

        private fun handle(ev: MotionEvent): Boolean = when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val zone = WidgetGeometry.zoneAt(
                    ev.x, ev.y, width, height, minimized,
                    frame = FRAME_DP * density, corner = CORNER_DP * density, header = HEADER_DP * density,
                )
                gestures.down(zone, ev.rawX, ev.rawY)
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> gestures.pointerDown(pointers(ev))
            MotionEvent.ACTION_MOVE -> gestures.move(pointers(ev))
            MotionEvent.ACTION_POINTER_UP -> gestures.pointerUp(ev.getPointerId(ev.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gestures.up()
            else -> gestures.active
        }

        private fun pointers(ev: MotionEvent) = (0 until ev.pointerCount)
            .filter { !(ev.actionMasked == MotionEvent.ACTION_POINTER_UP && it == ev.actionIndex) }
            .map { WidgetGestures.Pointer(ev.getPointerId(it), ev.getRawX(it), ev.getRawY(it)) }
    }

    /** The window's own lifecycle (Compose needs one): resumed while watched, stopped otherwise. */
    private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun setActive(active: Boolean) {
            if (registry.currentState == Lifecycle.State.DESTROYED) return
            registry.currentState = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
        }

        fun destroy() {
            if (registry.currentState == Lifecycle.State.INITIALIZED) return
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        const val PREFS = "floating_widget"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        /** The window's size, frame included (since v0.15.1). */
        private const val KEY_WIN_W = "win_w_dp"
        private const val KEY_WIN_H = "win_h_dp"
        /** v0.15.0's: the widget alone. */
        private const val KEY_OLD_W = "w_dp"
        private const val KEY_OLD_H = "h_dp"

        /** Wide enough for "✓ J. Jefferson (MIN) Under 69.5 +117" and its placed button; five bets tall. */
        const val DEFAULT_W_DP = 340
        const val DEFAULT_H_DP = 290
        const val DEFAULT_Y_DP = 72
        const val MIN_W_DP = 220
        const val MIN_H_DP = 160

        /** The frame around the widget: drag it to move, its corners to resize. */
        const val FRAME_DP = 10

        /** Each corner handle's touch square, from the window's corner in. */
        const val CORNER_DP = 44

        /** The widget's top bar (under the frame): drag it to move. */
        const val HEADER_DP = 36

        /** Whether Vigilant may draw over other apps (Android's "Display over other apps"). */
        fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Android's page to allow it for Vigilant. */
        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
