package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tj, 2026-09-27: "scan for up to date average odds … for each of the bets I made … 'now +3% ev'". */
class BetRecheckTest {

    @get:Rule val tmp = TemporaryFolder()
    private val start = 1_790_528_400_000L
    private var now = start - 2 * 60 * 60_000L

    private fun bet(id: String, gameUrl: String? = "https://crazyninjaodds.com/game?side_id=$id", startsTs: Long = start) = TrackedBet(
        id = id, createdAtMs = now, league = "NFL", eventName = "Seattle Seahawks @ Washington Commanders", startsTs = startsTs,
        marketLabel = "Moneyline", selection = "SEA", marketId = "", outcomeId = "", price = 0.5, cost = 0.5,
        fairAtBet = 0.52, evPercentAtBet = 0.04, stake = 1.0, source = BetTracker.SOURCE_CNO, american = 100, gameUrl = gameUrl,
    )

    private fun tracker(vararg bets: TrackedBet): BetTracker {
        File(tmp.root, "bets.json").writeText(Json.encodeToString(ListSerializer(TrackedBet.serializer()), bets.toList()))
        return BetTracker(File(tmp.root, "bets.json"), clock = { now })
    }

    /** Three books pricing both sides at [odds] / [other]. */
    private fun view(odds: Int, other: Int) = CnoBooksView(
        "SEA", "WSH", null, false,
        listOf("PN", "DK", "FD").map { CnoBookPrice(it, odds, null, other, null) } + CnoBookPrice("NV", 100, null, -104, null),
        now,
    )

    @Test
    fun `each open CNO bet gets the books' fair odds now, as EV at what it cost, and the close so far`() = runTest {
        val t = tracker(bet("up"), bet("down"))
        val seen = mutableListOf<CnoRow>()
        val r = BetRecheck(t, books = { row -> seen += row; if (row.bet == "SEA" && row.gameUrl!!.endsWith("up")) view(-125, 105) else view(115, -135) }, clock = { now }).run()
        assertEquals(2, r.updated)
        val byId = t.all().associateBy { it.id }
        val up = byId.getValue("up")
        assertEquals(true, up.nowEv!! > 0.0)
        assertEquals(up.nowFair!! / 0.5 - 1.0, up.nowEv!!, 1e-12)
        assertEquals(3, up.nowBooks)
        assertEquals(up.nowFair, up.closingFair) // before the start: the close so far (CLV)
        assertEquals(true, byId.getValue("down").nowEv!! < 0.0)
        // The row asked for is the one CNO's list keyed: its game page, at its book.
        assertEquals("https://crazyninjaodds.com/game?side_id=up|Novig", seen.first { it.gameUrl!!.endsWith("up") }.key)
    }

    @Test
    fun `settled bets, bets without a CNO game page, long-over games and unreadable pages are left as they were`() = runTest {
        val t = tracker(
            bet("vig", gameUrl = null),
            bet("old", startsTs = now - BetRecheck.STALE_AFTER_START_MS),
            bet("fail"),
            bet("won").copy(status = BetStatus.WON),
        )
        var asked = 0
        val r = BetRecheck(t, books = { asked++; null }, clock = { now }).run()
        assertEquals(1, asked)
        assertEquals(0, r.updated)
        t.all().forEach { assertNull(it.id, it.nowEv) }
    }

    @Test
    fun `after the start the recheck is the EV now, not the closing line`() = runTest {
        val t = tracker(bet("live"))
        now = start + 30 * 60_000L
        BetRecheck(t, books = { view(-125, 105) }, clock = { now }).run()
        val b = t.all().single()
        assertEquals(true, b.nowEv != null)
        assertNull(b.closingFair)
    }
}
