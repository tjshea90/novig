package com.tjshea.vigilant.app

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The real floating window, driven with touches (Tj, 2026-09-26 ~23:45Z): a corner resizes it,
 * the frame and top bar move it, two fingers pinch it, and the list still gets its taps.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class FloatingWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var widget: FloatingWidget
    private var watching = false

    @Before
    fun up() {
        ShadowSettings.setCanDrawOverlays(true)
        context.getSharedPreferences(FloatingWidget.PREFS, 0).edit().clear().commit()
        widget = FloatingWidget(context, onWatching = { watching = it }) { Box(Modifier.fillMaxSize()) }
    }

    @After
    fun down() = widget.hide()

    /** Shows the window and lays its root out at the window's size (mdpi: 1 px = 1 dp). */
    private fun shown(): View {
        assertTrue(widget.show())
        shadowOf(Looper.getMainLooper()).idle()
        val root = widget.rootForTest!!
        val r = widget.rectForTest()
        root.measure(View.MeasureSpec.makeMeasureSpec(r.w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(r.h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, r.w, r.h)
        return root
    }

    private fun touch(root: View, action: Int, x: Float, y: Float): Boolean {
        val e = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        return root.dispatchTouchEvent(e).also { e.recycle() }
    }

    @Test
    fun `the window is the widget plus its frame, and it counts as watching CNO while it's up`() {
        shown()
        val r = widget.rectForTest()
        assertEquals(FloatingWidget.DEFAULT_W_DP + 2 * FloatingWidget.FRAME_DP, r.w)
        assertEquals(FloatingWidget.DEFAULT_H_DP + 2 * FloatingWidget.FRAME_DP, r.h)
        assertTrue(watching)
        widget.hide()
        assertFalse(watching)
    }

    @Test
    fun `pulling the bottom-left corner out enlarges the window, pushing it in shrinks it, and it's remembered`() {
        val root = shown()
        val start = widget.rectForTest() // flush with the screen's right edge, so grow to the left
        val x = 5f
        val y = start.h - 5f
        touch(root, MotionEvent.ACTION_DOWN, x, y)
        touch(root, MotionEvent.ACTION_MOVE, x + 30f, y - 20f) // in by 30, 20: smaller
        assertEquals(start.w - 30, widget.rectForTest().w)
        assertEquals(start.h - 20, widget.rectForTest().h)
        touch(root, MotionEvent.ACTION_MOVE, x - 30f, y + 40f) // out by 30, 40: bigger
        touch(root, MotionEvent.ACTION_UP, x - 30f, y + 40f)
        val r = widget.rectForTest()
        assertEquals(start.w + 30, r.w)
        assertEquals(start.h + 40, r.h)
        assertEquals(start.x - 30, r.x) // the right edge stayed put
        assertEquals(start.y, r.y)
        // Shown again later: same size.
        widget.hide()
        shown()
        assertEquals(start.w + 30, widget.rectForTest().w)
    }

    @Test
    fun `dragging the frame moves the window, and a tap on the list doesn't`() {
        val root = shown()
        val start = widget.rectForTest()
        // The frame's left edge, halfway down.
        touch(root, MotionEvent.ACTION_DOWN, 4f, start.h / 2f)
        touch(root, MotionEvent.ACTION_MOVE, 4f, start.h / 2f + 50f)
        touch(root, MotionEvent.ACTION_UP, 4f, start.h / 2f + 50f)
        assertEquals(start.y + 50, widget.rectForTest().y)
        // A tap in the middle of the list: nothing moves.
        val now = widget.rectForTest()
        touch(root, MotionEvent.ACTION_DOWN, start.w / 2f, start.h / 2f)
        touch(root, MotionEvent.ACTION_UP, start.w / 2f, start.h / 2f)
        assertEquals(now, widget.rectForTest())
    }

    @Test
    fun `two fingers pinched on the list shrink the window`() {
        val root = shown()
        val start = widget.rectForTest()
        val cy = start.h / 2f
        fun two(action: Int, spread: Float): MotionEvent {
            val props = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(2) { i -> MotionEvent.PointerCoords().apply { x = start.w / 2f + (if (i == 0) -spread else spread); y = cy } }
            return MotionEvent.obtain(0L, 0L, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        }
        touch(root, MotionEvent.ACTION_DOWN, start.w / 2f - 50f, cy)
        root.dispatchTouchEvent(two(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 50f))
        root.dispatchTouchEvent(two(MotionEvent.ACTION_MOVE, 40f)) // 100 → 80 apart: 0.8x
        root.dispatchTouchEvent(two(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 40f))
        touch(root, MotionEvent.ACTION_UP, start.w / 2f - 40f, cy)
        val r = widget.rectForTest()
        assertEquals(start.w * 0.8f, r.w.toFloat(), 2f)
        assertEquals(start.h * 0.8f, r.h.toFloat(), 2f)
    }
}
