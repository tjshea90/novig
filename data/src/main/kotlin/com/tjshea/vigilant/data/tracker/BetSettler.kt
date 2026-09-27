package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigHttpException
import com.tjshea.vigilant.data.novig.NovigMarket
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Settles tracked bets from Novig's own catalog (Tj, 2026-09-27: "keep track whether each bet was a
 * win or a loss … a background scores system"). Novig marks every outcome of a finished market
 * WIN, LOSS, PUSH, or a decimal payout per $1 contract when it settles at fair market value
 * (NOVIG_API.md §3), on a public route, so no key and no score feed are needed.
 *
 * Only open bets whose game started at least [AFTER_START_MS] ago are asked about, oldest first,
 * one read at a time [gapMs] apart; a bet still undecided is asked again after [RETRY_MS]. A bet
 * without Novig's ids yet (a CNO bet whose outcome wasn't found when it was marked, or one
 * imported from an old ✓ mark) is looked up first with [resolve]. A result tapped in the Tracker
 * ([BetTracker.settle], "you") is never overwritten, and neither is an undo of one.
 */
class BetSettler(
    private val tracker: BetTracker,
    /** Novig's market by id (`/v3/public/catalog/markets/{id}`); null when Novig doesn't know it. */
    private val market: suspend (String) -> NovigMarket?,
    /** A bet's Novig market and outcome ids, when it has none: null when not found. */
    private val resolve: suspend (TrackedBet) -> Pair<String, String>?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gapMs: Long = GAP_MS,
) {
    data class Report(val asked: Int, val settled: Int, val stopped: Boolean)

    private val mutex = Mutex()

    /** When each bet may be asked about again (kept while the app runs). */
    private val nextTry = HashMap<String, Long>()

    /** Bets due a look now. */
    fun due(bets: List<TrackedBet>, now: Long = clock()): List<TrackedBet> = bets
        .filter { it.status == BetStatus.PENDING && it.settledBy != BY_YOU }
        .filter { now - it.startsTs >= AFTER_START_MS && now - it.startsTs <= GIVE_UP_MS }
        .filter { (nextTry[it.id] ?: 0L) <= now }
        .sortedBy { it.startsTs }

    /** One pass: every due bet, oldest first. Runs one pass at a time; a second caller waits for it. */
    suspend fun run(): Report = mutex.withLock {
        var asked = 0
        var settled = 0
        for (bet in due(tracker.all()).take(MAX_PER_RUN)) {
            if (asked > 0) delay(gapMs)
            asked++
            nextTry[bet.id] = clock() + RETRY_MS
            val ids = try {
                idsOf(bet)
            } catch (e: IOException) {
                return@withLock Report(asked, settled, stopped = true)
            } ?: continue
            if (bet.marketId.isEmpty() || bet.outcomeId.isEmpty()) delay(gapMs)
            val m = try {
                market(ids.first)
            } catch (e: NovigHttpException) {
                // Novig busy (429) or down: the rest waits for the next pass.
                return@withLock Report(asked, settled, stopped = true)
            } catch (e: IOException) {
                return@withLock Report(asked, settled, stopped = true)
            } ?: continue
            val outcome = m.outcomes.firstOrNull { it.outcomeId == ids.second } ?: continue
            val (status, value) = resultOf(outcome.status) ?: continue
            val now = clock()
            tracker.edit(bet.id) {
                // Tapped (or undone) while this pass ran: the tap wins.
                if (it.status != BetStatus.PENDING || it.settledBy == BY_YOU) it
                else it.copy(status = status, settledAtMs = now, settleValue = value, settledBy = BY_NOVIG)
            }
            nextTry.remove(bet.id)
            settled++
        }
        Report(asked, settled, stopped = false)
    }

    /** The bet's ids, found and kept if it had none. */
    private suspend fun idsOf(bet: TrackedBet): Pair<String, String>? {
        if (bet.marketId.isNotEmpty() && bet.outcomeId.isNotEmpty()) return bet.marketId to bet.outcomeId
        val found = resolve(bet) ?: return null
        tracker.edit(bet.id) { if (it.marketId.isEmpty() || it.outcomeId.isEmpty()) it.copy(marketId = found.first, outcomeId = found.second) else it }
        return found
    }

    companion object {
        const val BY_NOVIG = "novig"
        const val BY_YOU = "you"

        /** Asked about only once the game is at least this old (most games are decided by then or soon after). */
        const val AFTER_START_MS = 60 * 60_000L

        /** An undecided bet is asked about again after this. */
        const val RETRY_MS = 30 * 60_000L

        /** Bets older than this are left to a tap: Novig's catalog doesn't keep games forever. */
        const val GIVE_UP_MS = 30L * 24 * 60 * 60_000L

        /** Novig's public routes allow short bursts only (NOVIG_API.md §5.1). */
        const val GAP_MS = 400L

        /** Reads per pass at most (~40 s at [GAP_MS]); the rest go next pass. */
        const val MAX_PER_RUN = 80

        /** Novig's outcome status as a result: WIN, LOSS, PUSH, or a fair-market-value payout ("0.47"). Null: undecided. */
        fun resultOf(status: String): Pair<BetStatus, Double?>? = when (status.trim().uppercase()) {
            "WIN", "WON" -> BetStatus.WON to null
            "LOSS", "LOSE", "LOST" -> BetStatus.LOST to null
            "PUSH" -> BetStatus.PUSH to null
            "TBD", "" -> null
            else -> status.trim().toDoubleOrNull()?.takeIf { it in 0.0..1.0 }?.let { BetStatus.FMV to it }
        }
    }
}
