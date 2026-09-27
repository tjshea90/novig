package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Check odds now" in the Tracker (Tj, 2026-09-27: "scan for up to date average odds against sports
 * books for each of the bets I made … 'now +3% ev' … or 'now -2% ev'"): each open CNO bet's game
 * page is read again (every book's price, through [books], which keeps CNO's pace and pauses) and
 * the books' fair probability now is judged against what the bet cost: [TrackedBet.nowEv]. Before
 * the game starts it is also the closing line so far ([TrackedBet.closingFair], CLV).
 *
 * Vigilant's own bets get the same from each scan instead ([BetTracker.observe]).
 */
class BetRecheck(
    private val tracker: BetTracker,
    /** The bet's game page on CNO, read now; null when it couldn't be read. */
    private val books: suspend (CnoRow) -> CnoBooksView?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Report(val checked: Int, val updated: Int)

    private val mutex = Mutex()

    /** Open bets a recheck can price: CNO's game page known, the game not long over. */
    fun due(bets: List<TrackedBet>, now: Long = clock()): List<TrackedBet> = bets
        .filter { it.status == BetStatus.PENDING && it.gameUrl != null && now - it.startsTs < STALE_AFTER_START_MS }
        .sortedBy { it.startsTs }
        .take(MAX_PER_RUN)

    suspend fun run(): Report = mutex.withLock {
        var checked = 0
        var updated = 0
        for (bet in due(tracker.all())) {
            checked++
            val row = rowOf(bet)
            val view = runCatching { books(row) }.getOrNull() ?: continue
            val check = CnoBooks.check(view, row, preferListOdds = true)
            val fair = check.fairProbability ?: continue
            val now = clock()
            tracker.edit(bet.id) {
                val closing = now < it.startsTs
                it.copy(
                    nowFair = fair, nowEv = fair / it.cost - 1.0, nowAtMs = now, nowBooks = check.twoSided,
                    closingFair = if (closing) fair else it.closingFair,
                    closingSeenAtMs = if (closing) now else it.closingSeenAtMs,
                )
            }
            updated++
        }
        Report(checked, updated)
    }

    companion object {
        /** A game this far past its start is over, or nearly: nothing left to recheck. */
        const val STALE_AFTER_START_MS = 4 * 60 * 60_000L

        /** CNO game pages per tap at most (each is a paced CNO read). */
        const val MAX_PER_RUN = 40

        /** The CNO row a tracked bet came from, as far as the Tracker kept it. */
        fun rowOf(b: TrackedBet) = CnoRow(
            ev = b.evPercentAtBet ?: 0.0,
            startsAtMs = b.startsTs,
            league = b.league,
            event = b.eventName,
            market = b.marketLabel,
            bet = b.selection,
            odds = b.american ?: Odds.probabilityToAmerican(b.price.coerceIn(0.001, 0.999)),
            book = b.book,
            gameUrl = b.gameUrl,
            betUrl = b.betUrl,
        )
    }
}
