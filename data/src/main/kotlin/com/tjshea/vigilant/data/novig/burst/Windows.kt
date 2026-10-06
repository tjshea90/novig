package com.tjshea.vigilant.data.novig.burst

import com.tjshea.vigilant.data.novig.NovigBook

/**
 * A moment a cover paid (RESEARCH.md §95): from when a book change made [first] cost under $1 after fees until it stopped. Times are this phone's clock at the moment
 * the push ARRIVED, so a window is what the phone could see, not what the engine had.
 */
class OpenWindow(val first: Cover, val openedMs: Long) {
    var last: Cover = first
        internal set
    var peakNet: Double = first.net
        internal set
    var peakContracts: Long = first.contracts
        internal set

    /** When the cover last stopped paying; null while it does. A short flicker (a partial fill is a remove then an add) doesn't end a window ([CoverWindows.GRACE_MS]). */
    var notCrossedSinceMs: Long? = null
        internal set
    var updates: Int = 1
        internal set
}

/** A window that ended: it paid from [openedMs] for [durationMs] (to the moment it stopped, the grace not counted). */
class ClosedWindow(val window: OpenWindow, val closedMs: Long) {
    val durationMs: Long get() = (closedMs - window.openedMs).coerceAtLeast(0L)
}

/**
 * Finds covers on one game's ladders as the books change, and tracks how long each pays. Pure: the caller hands it the lines, a way to read each market's book and
 * the clock. A cover must pay at least [minNet] per $1 of payout after fees with at least [minContracts] on offer on both legs (a sliver of a contract is not a window).
 */
class CoverWindows(
    private val minNet: Double = MIN_NET,
    private val minContracts: Long = MIN_CONTRACTS,
    private val graceMs: Long = GRACE_MS,
) {
    private val open = HashMap<String, OpenWindow>()

    val openCount: Int get() = open.size

    /** The window open on [key] (a [Cover.key]), if any. */
    fun window(key: String): OpenWindow? = open[key]

    /**
     * [changed]'s book just changed: every pair it forms with another line of [ladder] is looked at again. Returns the windows that OPENED by this change (a window
     * already open is only updated). A pair that stops paying starts its grace; [sweep] closes it once the grace has passed.
     */
    fun onBook(ladder: Collection<LadderLine>, books: (String) -> NovigBook?, changed: LadderLine, nowMs: Long): List<OpenWindow> {
        val opened = ArrayList<OpenWindow>()
        val changedBook = books(changed.marketId)
        for (other in ladder) {
            if (other.marketId == changed.marketId || other.ladderKey != changed.ladderKey) continue
            val otherBook = books(other.marketId)
            val cover = if (other.threshold > changed.threshold) CoverMath.cover(changed, changedBook, other, otherBook) else CoverMath.cover(other, otherBook, changed, changedBook)
            val key = if (other.threshold > changed.threshold) changed.marketId + ">" + other.marketId else other.marketId + ">" + changed.marketId
            val crossed = cover != null && cover.net >= minNet && cover.contracts >= minContracts
            val w = open[key]
            when {
                crossed && w == null -> open[key] = OpenWindow(cover!!, nowMs).also { opened += it }
                crossed -> {
                    w!!.last = cover!!
                    w.notCrossedSinceMs = null
                    w.updates++
                    if (cover.net > w.peakNet) w.peakNet = cover.net
                    if (cover.contracts > w.peakContracts) w.peakContracts = cover.contracts
                }
                w != null && w.notCrossedSinceMs == null -> w.notCrossedSinceMs = nowMs
            }
        }
        return opened
    }

    /** Closes the windows whose cover has not paid for [graceMs]: they ended when it stopped. */
    fun sweep(nowMs: Long): List<ClosedWindow> {
        val closed = ArrayList<ClosedWindow>()
        val it = open.entries.iterator()
        while (it.hasNext()) {
            val w = it.next().value
            val since = w.notCrossedSinceMs ?: continue
            if (nowMs - since >= graceMs) {
                closed += ClosedWindow(w, since)
                it.remove()
            }
        }
        return closed
    }

    /** Ends everything open at [nowMs] (the game ended, the feed dropped, the recorder stopped): each closed when it last paid, or now when it still did. */
    fun closeAll(nowMs: Long): List<ClosedWindow> {
        val out = open.values.map { ClosedWindow(it, it.notCrossedSinceMs ?: nowMs) }
        open.clear()
        return out
    }

    companion object {
        /** The least a cover must pay, per $1 of payout, after both fees: under this it is inside the noise of a rounding of a price. */
        const val MIN_NET = 0.003

        /** The least on offer (contracts; 100 = $1 of payout) on both legs. */
        const val MIN_CONTRACTS = 100L

        /** A cover that stops paying and pays again inside this is the same window (a partial fill is a remove then an add of the rest). */
        const val GRACE_MS = 150L
    }
}
