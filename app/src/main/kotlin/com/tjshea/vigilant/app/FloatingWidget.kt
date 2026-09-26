package com.tjshea.vigilant.app

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
import android.view.WindowManager
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

    private var view: ComposeView? = null
    private var owner: WindowOwner? = null
    private var receiver: BroadcastReceiver? = null

    /** Shrunk to a bubble (no reads while it is). */
    var minimized by mutableStateOf(false)
        private set

    /** The bet whose Novig link is being looked up. */
    var opening by mutableStateOf<String?>(null)

    val showing: Boolean get() = view != null

    private val params = WindowManager.LayoutParams(
        dp(prefs.getInt(KEY_W, DEFAULT_W_DP)),
        dp(prefs.getInt(KEY_H, DEFAULT_H_DP)),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes the keyboard or the back button from Novig; touches outside it go to Novig.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(KEY_X, -1).takeIf { it >= 0 } ?: (screenW() - width - dp(8)).coerceAtLeast(0)
        y = prefs.getInt(KEY_Y, -1).takeIf { it >= 0 } ?: dp(DEFAULT_Y_DP)
        title = "Vigilant widget"
    }

    /** Puts the window up (no-op if it is, or without the permission). False if it couldn't. */
    fun show(): Boolean {
        if (view != null) return true
        if (!allowed(app)) return false
        val o = WindowOwner()
        val v = ComposeView(app).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { content(this@FloatingWidget) }
        }
        clampToScreen()
        val added = runCatching { wm.addView(v, params) }.isSuccess
        if (!added) {
            o.destroy()
            return false
        }
        owner = o
        view = v
        listenToScreen()
        report()
        return true
    }

    /** Takes the window down; CNO stops being read for it at once. */
    fun hide() {
        val v = view ?: return
        view = null
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
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
    }

    fun expand() {
        minimized = false
        params.width = dp(prefs.getInt(KEY_W, DEFAULT_W_DP))
        params.height = dp(prefs.getInt(KEY_H, DEFAULT_H_DP))
        clampToScreen()
        relayout()
        report()
    }

    fun moveBy(dx: Float, dy: Float) {
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampToScreen()
        relayout()
    }

    fun resizeBy(dw: Float, dh: Float) {
        if (minimized) return
        params.width = (params.width + dw.roundToInt()).coerceIn(dp(MIN_W_DP), screenW())
        params.height = (params.height + dh.roundToInt()).coerceIn(dp(MIN_H_DP), screenH())
        clampToScreen()
        relayout()
    }

    /** Remembers where the window is (and how big, unless it's a bubble). */
    fun save() {
        prefs.edit().apply {
            putInt(KEY_X, params.x)
            putInt(KEY_Y, params.y)
            if (!minimized) {
                putInt(KEY_W, (params.width / density).roundToInt())
                putInt(KEY_H, (params.height / density).roundToInt())
            }
        }.apply()
    }

    private fun relayout() {
        val v = view ?: return
        runCatching { wm.updateViewLayout(v, params) }
    }

    private fun clampToScreen() {
        val w = if (params.width > 0) params.width else dp(120)
        val h = if (params.height > 0) params.height else dp(40)
        params.x = params.x.coerceIn(0, (screenW() - w).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH() - h).coerceAtLeast(0))
    }

    /** Watching = up, not a bubble, screen on and unlocked. */
    private fun report() {
        val watching = view != null && !minimized && screenUsable()
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
        private const val KEY_W = "w_dp"
        private const val KEY_H = "h_dp"

        /** Wide enough for "✓ J. Jefferson (MIN) Under 69.5 +117" and its placed button; five bets tall. */
        const val DEFAULT_W_DP = 340
        const val DEFAULT_H_DP = 290
        const val DEFAULT_Y_DP = 72
        const val MIN_W_DP = 220
        const val MIN_H_DP = 160

        /** Whether Vigilant may draw over other apps (Android's "Display over other apps"). */
        fun allowed(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** Android's page to allow it for Vigilant. */
        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
