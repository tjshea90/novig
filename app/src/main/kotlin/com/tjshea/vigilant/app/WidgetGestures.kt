package com.tjshea.vigilant.app

import kotlin.math.hypot
import kotlin.math.roundToInt

/** The floating widget's window on screen, in pixels. */
data class WidgetRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** What a finger that comes down at a spot on the widget does if it then moves. */
enum class WidgetZone {
    /** Drags the widget around: the frame all around it, and the top bar. */
    MOVE,

    /** The list and its buttons: taps and scrolling, nothing to do with the window. */
    CONTENT,

    /** A corner: drag out to enlarge, in to shrink. */
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
}

/**
 * Where the floating widget may go and how big it may be, and how a move, a corner drag or a
 * two-finger pinch changes it (Tj, 2026-09-26: "easily resize the widget by … two finger gesture …
 * or by easily accessed corners", "an easier way … to drag and move the widget").
 */
object WidgetGeometry {

    /** Smallest size, and the screen it has to stay on. */
    data class Limits(val minW: Int, val minH: Int, val screenW: Int, val screenH: Int)

    /**
     * The zone under a finger at ([x], [y]) inside a [w]×[h] widget: corners (a [corner]-px square
     * at each), the [frame]-px border and the top bar ([header] px under the border) move it, the
     * rest is the list. A bubble ([minimized]) is all move (a tap still opens it).
     */
    fun zoneAt(x: Float, y: Float, w: Int, h: Int, minimized: Boolean, frame: Float, corner: Float, header: Float): WidgetZone {
        if (minimized) return WidgetZone.MOVE
        val left = x < corner
        val right = x > w - corner
        val top = y < corner
        val bottom = y > h - corner
        return when {
            top && left -> WidgetZone.TOP_LEFT
            top && right -> WidgetZone.TOP_RIGHT
            bottom && left -> WidgetZone.BOTTOM_LEFT
            bottom && right -> WidgetZone.BOTTOM_RIGHT
            x < frame || x > w - frame || y < frame || y > h - frame -> WidgetZone.MOVE
            y < frame + header -> WidgetZone.MOVE
            else -> WidgetZone.CONTENT
        }
    }

    /** Dragged by ([dx], [dy]) from [start], kept on screen. */
    fun move(start: WidgetRect, dx: Float, dy: Float, l: Limits): WidgetRect =
        onScreen(start.copy(x = start.x + dx.roundToInt(), y = start.y + dy.roundToInt()), l)

    /**
     * [start] with corner [zone] dragged by ([dx], [dy]): out enlarges, in shrinks, and the
     * opposite corner stays where it is. Never smaller than the minimum or off the screen.
     */
    fun resize(start: WidgetRect, zone: WidgetZone, dx: Float, dy: Float, l: Limits): WidgetRect {
        val fromLeft = zone == WidgetZone.TOP_LEFT || zone == WidgetZone.BOTTOM_LEFT
        val fromTop = zone == WidgetZone.TOP_LEFT || zone == WidgetZone.TOP_RIGHT
        if (zone == WidgetZone.MOVE || zone == WidgetZone.CONTENT) return start
        val right = start.x + start.w
        val bottom = start.y + start.h
        // How far each edge can go before it leaves the screen.
        val maxW = if (fromLeft) right else l.screenW - start.x
        val maxH = if (fromTop) bottom else l.screenH - start.y
        val w = ((if (fromLeft) start.w - dx else start.w + dx).roundToInt()).coerceIn(minOf(l.minW, maxW), maxOf(maxW, 1))
        val h = ((if (fromTop) start.h - dy else start.h + dy).roundToInt()).coerceIn(minOf(l.minH, maxH), maxOf(maxH, 1))
        return WidgetRect(if (fromLeft) right - w else start.x, if (fromTop) bottom - h else start.y, w, h)
    }

    /**
     * Two fingers: their spread going from [startSpan] to [span] scales the widget (spread =
     * bigger, pinch = smaller) about its middle, and their midpoint moving by ([panX], [panY])
     * moves it.
     */
    fun pinch(start: WidgetRect, startSpan: Float, span: Float, panX: Float, panY: Float, l: Limits): WidgetRect {
        val scale = if (startSpan > 0f) span / startSpan else 1f
        val w = (start.w * scale).roundToInt().coerceIn(minOf(l.minW, l.screenW), l.screenW)
        val h = (start.h * scale).roundToInt().coerceIn(minOf(l.minH, l.screenH), l.screenH)
        val cx = start.x + start.w / 2f + panX
        val cy = start.y + start.h / 2f + panY
        return onScreen(WidgetRect((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt(), w, h), l)
    }

    /** No bigger than the screen, and all of it on the screen. */
    fun onScreen(r: WidgetRect, l: Limits): WidgetRect {
        val w = r.w.coerceAtMost(l.screenW)
        val h = r.h.coerceAtMost(l.screenH)
        return WidgetRect(r.x.coerceIn(0, (l.screenW - w).coerceAtLeast(0)), r.y.coerceIn(0, (l.screenH - h).coerceAtLeast(0)), w, h)
    }
}

/**
 * The widget's own touch handling, before the list sees a touch: one finger on the frame or the
 * top bar moves it, one finger on a corner resizes it, two fingers anywhere pinch-resize and move
 * it. Everything else (taps, scrolling the list) goes to the list untouched: a finger only
 * becomes a move or resize once it has moved past [slop], so a tap on a top-bar button still taps.
 *
 * Works in screen pixels (MotionEvent's raw coordinates): the widget's own coordinates shift
 * under the finger as the window moves, which made v0.15.0's top-bar drag lag behind.
 */
class WidgetGestures(
    private val slop: Float,
    private val limits: () -> WidgetGeometry.Limits,
    /** The window's current place and size. */
    private val current: () -> WidgetRect,
    /** Put the window here. */
    private val apply: (WidgetRect) -> Unit,
    /** A move, resize or pinch ended (the window remembers where it is). */
    private val finished: () -> Unit,
    /** Two-finger resizing is allowed (not for the bubble). */
    private val canPinch: () -> Boolean = { true },
) {
    /** One finger on the screen, in screen pixels. */
    data class Pointer(val id: Int, val rawX: Float, val rawY: Float)

    private enum class Mode { IDLE, WAITING, DRAG, PINCH, SPENT }

    private var mode = Mode.IDLE
    private var zone = WidgetZone.CONTENT
    private var downX = 0f
    private var downY = 0f
    private var start = WidgetRect(0, 0, 0, 0)
    private var pinchIds = 0 to 0
    private var startSpan = 0f
    private var startCx = 0f
    private var startCy = 0f

    /** Whether the widget has taken over the current gesture (the list gets a cancel). */
    val active: Boolean get() = mode == Mode.DRAG || mode == Mode.PINCH || mode == Mode.SPENT

    /** The first finger came down in [zone] at ([rawX], [rawY]). */
    fun down(zone: WidgetZone, rawX: Float, rawY: Float) {
        this.zone = zone
        downX = rawX
        downY = rawY
        start = current()
        mode = Mode.WAITING
    }

    /** Another finger came down; [pointers] are all of them. True = the widget takes the gesture. */
    fun pointerDown(pointers: List<Pointer>): Boolean {
        if (mode == Mode.SPENT || pointers.size < 2 || !canPinch()) return active
        val (a, b) = pointers[0] to pointers[1]
        pinchIds = a.id to b.id
        startSpan = span(a, b)
        startCx = (a.rawX + b.rawX) / 2f
        startCy = (a.rawY + b.rawY) / 2f
        start = current()
        mode = Mode.PINCH
        return true
    }

    /** Fingers moved. True = the widget has the gesture (and has moved or resized). */
    fun move(pointers: List<Pointer>): Boolean {
        when (mode) {
            Mode.WAITING -> {
                val p = pointers.firstOrNull() ?: return false
                if (zone == WidgetZone.CONTENT || hypot(p.rawX - downX, p.rawY - downY) < slop) return false
                mode = Mode.DRAG
                drag(p)
            }
            Mode.DRAG -> pointers.firstOrNull()?.let(::drag)
            Mode.PINCH -> {
                val a = pointers.firstOrNull { it.id == pinchIds.first }
                val b = pointers.firstOrNull { it.id == pinchIds.second }
                if (a != null && b != null) {
                    val cx = (a.rawX + b.rawX) / 2f
                    val cy = (a.rawY + b.rawY) / 2f
                    apply(WidgetGeometry.pinch(start, startSpan, span(a, b), cx - startCx, cy - startCy, limits()))
                }
            }
            Mode.IDLE, Mode.SPENT -> Unit
        }
        return active
    }

    /** One of several fingers lifted: a pinch ends there (the last finger doesn't start a drag). */
    fun pointerUp(id: Int): Boolean {
        if (mode == Mode.PINCH && (id == pinchIds.first || id == pinchIds.second)) {
            mode = Mode.SPENT
            finished()
        }
        return active
    }

    /** The last finger lifted, or the gesture was cancelled. True = it was the widget's. */
    fun up(): Boolean {
        val was = mode
        mode = Mode.IDLE
        if (was == Mode.DRAG) finished()
        return was == Mode.DRAG || was == Mode.PINCH || was == Mode.SPENT
    }

    private fun drag(p: Pointer) {
        val dx = p.rawX - downX
        val dy = p.rawY - downY
        val l = limits()
        apply(if (zone == WidgetZone.MOVE) WidgetGeometry.move(start, dx, dy, l) else WidgetGeometry.resize(start, zone, dx, dy, l))
    }

    private fun span(a: Pointer, b: Pointer) = hypot(a.rawX - b.rawX, a.rawY - b.rawY)
}
