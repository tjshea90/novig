package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Open bets logged without Novig's ids are looked up again before Novig's prices are read (Tj, 2026-10-02 20:06Z: "many open bets are not finding the
 * current novig odds for the same exact bet … make sure the feature is coded properly"): a CNO ✓ whose one lookup when logged failed was never
 * priced from Novig again.
 */
class NovigIdsTest {

    private val now = 1_800_000_000_000L
    private val start = now + 6 * 3_600_000L

    private fun bet(id: String, market: String = "", outcome: String = "", book: String = "Novig", status: BetStatus = BetStatus.PENDING, starts: Long = start) = TrackedBet(
        id = id, createdAtMs = now - 86_400_000L, league = "NFL", eventName = "Seattle Seahawks @ Washington Commanders", startsTs = starts,
        marketLabel = "Total Points", selection = "Under 32.5", marketId = market, outcomeId = outcome, price = 0.5, cost = 0.5, fairAtBet = 0.52,
        evPercentAtBet = 0.04, stake = 1.0, status = status, book = book, american = 100,
    )

    @Test
    fun `bets at Novig with an id missing are looked up, not ones with both, settled, at another book, or long started - and a miss waits`() {
        val bets = listOf(
            bet("none"), bet("sideOnly", outcome = "tot-u"), bet("both", "m9", "tot-u"), bet("settled", status = BetStatus.WON),
            bet("mgm", book = "BetMGM"), bet("old", starts = now - BetsScope.LIVE_WINDOW_MS - 1), bet("blankBook", book = ""),
            bet("missedLately").copy(novigWhy = "Novig doesn't list this game now", novigWhyAtMs = now - 60_000),
            bet("missedLongAgo").copy(novigWhy = "Novig doesn't list this game now", novigWhyAtMs = now - NovigIds.RETRY_MS),
            bet("busyLately").copy(novigWhy = NovigBetFinder.BUSY, novigWhyAtMs = now - 60_000),
        )
        assertEquals(listOf("none", "sideOnly", "blankBook", "missedLongAgo", "busyLately"), NovigIds.missing(bets, now, force = false).map { it.id })
        // Check Novig now looks at every one again.
        assertEquals(listOf("none", "sideOnly", "blankBook", "missedLately", "missedLongAgo", "busyLately"), NovigIds.missing(bets, now, force = true).map { it.id })
    }

    @Test
    fun `each bet is looked up by its words and its side when known, and what's found or why not is written on it`() = runBlocking {
        val asked = mutableListOf<Pair<CnoRow, String?>>()
        val found = NovigIds.find(listOf(bet("a"), bet("b", outcome = "tot-u"), bet("c"), bet("d"))) { row, outcome ->
            asked += row to outcome
            when (asked.size) {
                1 -> NovigBetFinder.Located.Bet("m9", "tot-o")
                2 -> NovigBetFinder.Located.Bet("m9", "tot-u")
                3 -> throw IllegalStateException("boom")
                else -> NovigBetFinder.Located.Missing("Novig lists the game but not this exact bet now")
            }
        }
        assertEquals(listOf(null, "tot-u", null, null), asked.map { it.second })
        assertEquals("Under 32.5", asked.first().first.bet)
        assertEquals("NFL", asked.first().first.league)
        assertEquals(mapOf("a" to ("m9" to "tot-o"), "b" to ("m9" to "tot-u")), found.ids)
        assertEquals(mapOf("c" to NovigBetFinder.BUSY, "d" to "Novig lists the game but not this exact bet now"), found.why)

        val folder = TemporaryFolder().apply { create() }
        try {
            val tracker = BetTracker(folder.newFile("bets.json").apply { delete() }, clock = { now })
            val logged = tracker.logCno(CnoRow(0.04, start, "Football", "NFL", "Seattle Seahawks @ Washington Commanders", "Total Points", "Under 32.5", 100, book = "Novig"), 0.04, live = false, placedKey = "k1")
            val other = tracker.logCno(CnoRow(0.04, start, "Football", "NFL", "Seattle Seahawks @ Washington Commanders", "Total Points", "Over 32.5", 100, book = "Novig"), 0.04, live = false, placedKey = "k2")
            tracker.recordIds(mapOf(logged.id to ("m9" to "tot-u")), mapOf(other.id to "not offered"), now)
            val byId = tracker.all().associateBy { it.id }
            assertEquals("m9", byId.getValue(logged.id).marketId)
            assertEquals("tot-u", byId.getValue(logged.id).outcomeId)
            assertNull(byId.getValue(logged.id).novigWhy)
            assertEquals("not offered", byId.getValue(other.id).novigWhy)
            assertEquals(now, byId.getValue(other.id).novigWhyAtMs)
            // Found: now a bet the Novig-only read prices.
            assertTrue(NovigNow.priceable(tracker.all(), now).any { it.id == logged.id })
            // Ids already on record are never overwritten by a later look.
            tracker.recordIds(mapOf(logged.id to ("other" to "other")), emptyMap(), now)
            assertEquals("m9", tracker.all().first { it.id == logged.id }.marketId)
            // A price read clears the reason; a miss keeps the last price and says why.
            tracker.recordNovig(mapOf(logged.id to 0.47), now, mapOf(other.id to NovigNow.NOTHING_OFFERED))
            assertEquals(0.47, tracker.all().first { it.id == logged.id }.novigFair!!, 0.0)
            assertEquals(NovigNow.NOTHING_OFFERED, tracker.all().first { it.id == other.id }.novigWhy)
        } finally {
            folder.delete()
        }
    }
}
