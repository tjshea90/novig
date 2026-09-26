package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving and resizing the floating widget (Tj, 2026-09-26 ~23:45Z): two fingers pinch or spread
 * it and move it, corners resize it, the frame and top bar drag it. Screen pixels throughout.
 */
class WidgetGesturesTest {

    private val limits = WidgetGeometry.Limits(minW = 240, minH = 180, screenW = 1080, screenH = 2400)

    /** The widget as a window: [rect] is where WidgetGestures put it last. */
    private inner class Window(var rect: WidgetRect, minimized: Boolean = false) {
        var saves = 0
        val gestures = WidgetGestures(
            slop = 10f,
            limits = { limits },
            current = { rect },
            apply = { rect = it },
            finished = { saves++ },
            canPinch = { !minimized },
        )
    }

    private fun p(id: Int, x: Float, y: Float) = WidgetGestures.Pointer(id, x, y)

    // ---- Where a finger lands ---------------------------------------------------------------

    @Test
    fun `corners resize, the frame and top bar move, the list is the list, and a bubble is all move`() {
        fun zone(x: Float, y: Float, minimized: Boolean = false) =
            WidgetGeometry.zoneAt(x, y, w = 1000, h = 800, minimized = minimized, frame = 30f, corner = 130f, header = 100f)
        assertEquals(WidgetZone.TOP_LEFT, zone(10f, 10f))
        assertEquals(WidgetZone.TOP_RIGHT, zone(990f, 120f))
        assertEquals(WidgetZone.BOTTOM_LEFT, zone(100f, 790f))
        assertEquals(WidgetZone.BOTTOM_RIGHT, zone(900f, 700f))
        assertEquals(WidgetZone.MOVE, zone(500f, 10f)) // the frame's top edge
        assertEquals(WidgetZone.MOVE, zone(5f, 400f)) // its left edge
        assertEquals(WidgetZone.MOVE, zone(500f, 795f)) // its bottom edge
        assertEquals(WidgetZone.MOVE, zone(500f, 100f)) // the top bar
        assertEquals(WidgetZone.CONTENT, zone(500f, 400f))
        assertEquals(WidgetZone.MOVE, zone(500f, 400f, minimized = true))
    }

    // ---- One finger ---------------------------------------------------------------------------

    @Test
    fun `dragging the top bar moves the widget exactly with the finger, and saves where it is`() {
        val w = Window(WidgetRect(100, 200, 700, 600))
        w.gestures.down(WidgetZone.MOVE, 400f, 250f)
        assertTrue(w.gestures.move(listOf(p(0, 450f, 330f))))
        assertEquals(WidgetRect(150, 280, 700, 600), w.rect)
        // The window moved; the next finger position is still measured from where it came down.
        assertTrue(w.gestures.move(listOf(p(0, 500f, 400f))))
        assertEquals(WidgetRect(200, 350, 700, 600), w.rect)
        assertTrue(w.gestures.up())
        assertEquals(1, w.saves)
    }

    @Test
    fun `a tap on a top-bar button stays a tap - under the slop nothing is taken over`() {
        val w = Window(WidgetRect(100, 200, 700, 600))
        w.gestures.down(WidgetZone.MOVE, 400f, 250f)
        assertFalse(w.gestures.move(listOf(p(0, 405f, 254f))))
        assertFalse(w.gestures.up())
        assertEquals(WidgetRect(100, 200, 700, 600), w.rect)
        assertEquals(0, w.saves)
    }

    @Test
    fun `one finger on the list scrolls the list, never the window`() {
        val w = Window(WidgetRect(100, 200, 700, 600))
        w.gestures.down(WidgetZone.CONTENT, 400f, 600f)
        assertFalse(w.gestures.move(listOf(p(0, 400f, 300f))))
        assertFalse(w.gestures.up())
        assertEquals(WidgetRect(100, 200, 700, 600), w.rect)
    }

    @Test
    fun `pulling a corner out enlarges and pushing it in shrinks, the opposite corner staying put`() {
        val start = WidgetRect(200, 400, 600, 500)
        fun drag(zone: WidgetZone, dx: Float, dy: Float): WidgetRect {
            val w = Window(start)
            w.gestures.down(zone, 0f, 0f)
            w.gestures.move(listOf(p(0, dx, dy)))
            w.gestures.up()
            return w.rect
        }
        // Bottom-right out: bigger, top-left fixed.
        assertEquals(WidgetRect(200, 400, 700, 580), drag(WidgetZone.BOTTOM_RIGHT, 100f, 80f))
        // Bottom-right in: smaller.
        assertEquals(WidgetRect(200, 400, 500, 420), drag(WidgetZone.BOTTOM_RIGHT, -100f, -80f))
        // Top-left out (up and left): bigger, bottom-right fixed at (800, 900).
        assertEquals(WidgetRect(100, 300, 700, 600), drag(WidgetZone.TOP_LEFT, -100f, -100f))
        // Top-right out: right edge and top move; left edge and bottom fixed.
        assertEquals(WidgetRect(200, 350, 650, 550), drag(WidgetZone.TOP_RIGHT, 50f, -50f))
        // Bottom-left in: left edge moves right, bottom moves up.
        assertEquals(WidgetRect(250, 400, 550, 450), drag(WidgetZone.BOTTOM_LEFT, 50f, -50f))
    }

    @Test
    fun `a corner never makes it smaller than the minimum, or bigger than the screen`() {
        val start = WidgetRect(200, 400, 600, 500)
        assertEquals(WidgetRect(200, 400, 240, 180), WidgetGeometry.resize(start, WidgetZone.BOTTOM_RIGHT, -900f, -900f, limits))
        // Pulled far past the screen's edge: stops at it.
        assertEquals(WidgetRect(200, 400, 880, 2000), WidgetGeometry.resize(start, WidgetZone.BOTTOM_RIGHT, 5_000f, 5_000f, limits))
        assertEquals(WidgetRect(0, 0, 800, 900), WidgetGeometry.resize(start, WidgetZone.TOP_LEFT, -5_000f, -5_000f, limits))
        // Shrunk from the left past the minimum: the right edge stays where it was.
        assertEquals(WidgetRect(560, 400, 240, 500), WidgetGeometry.resize(start, WidgetZone.BOTTOM_LEFT, 900f, 0f, limits))
    }

    // ---- Two fingers --------------------------------------------------------------------------

    @Test
    fun `two fingers spreading enlarge it about its middle, pinching shrinks it, and moving them moves it`() {
        val w = Window(WidgetRect(200, 400, 600, 500)) // middle (500, 650)
        w.gestures.down(WidgetZone.CONTENT, 400f, 600f)
        assertTrue(w.gestures.pointerDown(listOf(p(0, 400f, 600f), p(1, 600f, 600f)))) // 200 apart
        // Spread to 300 apart: 1.5x, same middle.
        w.gestures.move(listOf(p(0, 350f, 600f), p(1, 650f, 600f)))
        assertEquals(WidgetRect(50, 275, 900, 750), w.rect)
        // Pinch to 100 apart: 0.5x → 300x250, but not under the minimum height (180 is fine), same middle.
        w.gestures.move(listOf(p(0, 450f, 600f), p(1, 550f, 600f)))
        assertEquals(WidgetRect(350, 525, 300, 250), w.rect)
        // Both fingers move 100 right and 50 down at the original spread: same size, moved.
        w.gestures.move(listOf(p(0, 500f, 650f), p(1, 700f, 650f)))
        assertEquals(WidgetRect(300, 450, 600, 500), w.rect)
        // One finger lifts: the pinch ends and is saved; the last finger doesn't start a drag.
        assertTrue(w.gestures.pointerUp(1))
        assertEquals(1, w.saves)
        w.gestures.move(listOf(p(0, 900f, 900f)))
        assertEquals(WidgetRect(300, 450, 600, 500), w.rect)
        assertTrue(w.gestures.up())
    }

    @Test
    fun `a pinch starts even when the first finger was on the list, and never shrinks it past the minimum`() {
        val w = Window(WidgetRect(200, 400, 600, 500))
        w.gestures.down(WidgetZone.CONTENT, 400f, 600f)
        assertFalse(w.gestures.move(listOf(p(0, 400f, 580f)))) // the list scrolls a little first
        assertTrue(w.gestures.pointerDown(listOf(p(0, 400f, 580f), p(1, 800f, 580f))))
        w.gestures.move(listOf(p(0, 590f, 580f), p(1, 610f, 580f))) // 400 → 20 apart
        assertEquals(240, w.rect.w)
        assertEquals(180, w.rect.h)
    }

    @Test
    fun `the bubble doesn't pinch, but drags anywhere`() {
        val w = Window(WidgetRect(900, 100, 150, 60), minimized = true)
        w.gestures.down(WidgetZone.MOVE, 950f, 120f)
        assertFalse(w.gestures.pointerDown(listOf(p(0, 950f, 120f), p(1, 1000f, 120f))))
        assertTrue(w.gestures.move(listOf(p(0, 750f, 520f))))
        assertEquals(WidgetRect(700, 500, 150, 60), w.rect)
    }

    @Test
    fun `it can't be dragged off the screen`() {
        assertEquals(WidgetRect(0, 0, 600, 500), WidgetGeometry.move(WidgetRect(200, 400, 600, 500), -900f, -900f, limits))
        assertEquals(WidgetRect(480, 1900, 600, 500), WidgetGeometry.move(WidgetRect(200, 400, 600, 500), 900f, 9_000f, limits))
    }
}
