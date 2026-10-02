package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import kotlinx.coroutines.CancellationException

/**
 * Finds the Novig market and side of open bets logged without them (Tj, 2026-10-02 20:06Z: "many open bets are not finding the current novig odds for the
 * same exact bet. This may be because it is not currently offered, but make sure the feature is coded properly"). A CNO or ParlayAPI ✓, a ✓ Placed on
 * a CNO alert, and an imported mark keep the bet's words but not always Novig's ids, and one lookup when the bet was logged was all that filled them: a
 * bet whose lookup failed then (Novig busy, the line not up yet) was never priced from Novig again. Here every such bet is looked up again in Novig's own
 * catalog ([NovigBetFinder.locate]: public reads, no other book) before Novig's prices are read, and one that still isn't there says why.
 */
object NovigIds {

    /** A bet not found is looked up again after this; a forced read (Check Novig now) looks again at once, and so does one Novig didn't answer. */
    const val RETRY_MS = 10 * 60_000L

    /** Open bets at Novig whose odds can still be read but whose Novig market or side isn't on record, and are due a look ([RETRY_MS]). */
    fun missing(bets: List<TrackedBet>, now: Long, force: Boolean): List<TrackedBet> = bets.filter { b ->
        b.status == BetStatus.PENDING && BetsScope.readable(b, now) && (b.marketId.isBlank() || b.outcomeId.isBlank()) && atNovig(b) &&
            (force || b.novigWhyAtMs == null || b.novigWhy == NovigBetFinder.BUSY || now - b.novigWhyAtMs >= RETRY_MS)
    }

    /** Bet at Novig (or a ✓ that didn't say where). */
    fun atNovig(b: TrackedBet): Boolean = b.book.isBlank() || b.book.equals("Novig", ignoreCase = true)

    /** What a look found: bet id → (market id, outcome id), and bet id → why it isn't on Novig now. */
    data class Found(val ids: Map<String, Pair<String, String>>, val why: Map<String, String>)

    /** Each of [bets] in Novig's catalog, one after another (the finder paces and caches its reads): its side when known first ([NovigBetFinder.locate]). */
    suspend fun find(bets: List<TrackedBet>, locate: suspend (CnoRow, String?) -> NovigBetFinder.Located): Found {
        val ids = LinkedHashMap<String, Pair<String, String>>()
        val why = LinkedHashMap<String, String>()
        for (b in bets) {
            val found = try {
                locate(BetRecheck.rowOf(b), b.outcomeId.takeIf { it.isNotBlank() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NovigBetFinder.Located.Missing(NovigBetFinder.BUSY, retry = true)
            }
            when (found) {
                is NovigBetFinder.Located.Bet -> ids[b.id] = found.marketId to found.outcomeId
                is NovigBetFinder.Located.Missing -> why[b.id] = found.why
            }
        }
        return Found(ids, why)
    }
}
