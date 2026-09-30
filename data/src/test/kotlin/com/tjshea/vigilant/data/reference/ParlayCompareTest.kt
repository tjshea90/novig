package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.ParlayCompare.Index
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.OpenBetPricer
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * TASKS.md P2 (Tj, 2026-09-30: "next to parlayapi's percent positive EV number in that section, put cno/vigilant's percentage so I can compare
 * and see if it is truly positive EV on each bet"): each ParlayAPI pick's EV at Novig's price now by CNO's fair line and by Vigilant's own, at
 * the same price, or why one has none.
 */
class ParlayCompareTest {

    private val now = 1_790_400_000_000L
    private val start = now + 5 * 3_600_000L

    /** Carson Kelly Over 0.5 HR at +950 on Novig now, ParlayAPI's fair +900; Novig's market m-hr, outcome o-kelly-over. */
    private fun pick(): ParlayPick {
        val play = ParlayPlay(
            sportKey = "baseball_mlb", league = "MLB", player = "Carson Kelly", over = true, line = 0.5, statLabel = "Home Runs", stat = "HOME_RUNS",
            away = "Chicago Cubs", home = "San Diego Padres", marketKey = "player_home_runs", fairAmerican = 900, listedAmerican = 2122,
        )
        val row = play.row(startsAtMs = start)
        return ParlayPick.priced(listOf(play), listOf(row), mapOf(row.key to LivePrice(950, 25.0, 0.045, now, "m-hr", "o-kelly-over"))).single()
    }

    private fun cnoRow(book: String = "Novig", startsAtMs: Long? = start, fair: Int? = 800, bet: String = "Carson Kelly Over 0.5") = CnoRow(
        ev = 0.05, startsAtMs = startsAtMs, sport = "Baseball", league = "MLB", event = "Chicago Cubs @ San Diego Padres", market = "Player Home Runs",
        bet = bet, odds = 950, available = 20.0, book = book, fairOdds = fair, fairProbability = null, books = 5, gameUrl = "https://crazyninjaodds.com/game/1",
    )

    private fun opportunity(outcomeId: String, fair: Double, asOf: Long, selection: String = "Carson Kelly Over 0.5"): Opportunity {
        val event = NovigEvent("e1", "BASEBALL", "MLB", NovigEvent.STATUS_PREGAME, "Chicago Cubs @ San Diego Padres", start)
        val market = NovigMarket("m-hr", "e1", "HOME_RUNS", "OPEN", "Carson Kelly Home Runs", start, MarketFee.GAME, listOf(NovigOutcome(outcomeId, "Over 0.5", "TBD")))
        return Opportunity(
            league = Leagues.byNovigName("MLB")!!, event = event, market = market, outcome = market.outcomes.single(),
            marketLabel = "Player Home Runs", kind = LineKind.PLAYER_PROP, selection = selection, fair = null, fairProbability = fair,
            quote = null, ladder = emptyList(), depth = null, suggestedStake = null, novigWidth = null, bookFetchedAtMs = now, fairUpdatedMs = asOf,
            refEvent = null, lineKey = null, target = null, fairAsOfMs = asOf,
        )
    }

    @Test
    fun `CNO's EV is its fair line at Novig's price now, from its row for the same bet`() {
        val p = pick()
        val read = ParlayCompare.cno(p, Index(listOf(cnoRow()), emptyList()), listAtMs = now - 30_000L, now = now, cnoOn = true)
        val fair = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(800)
        assertEquals(CnoBooks.evAt(fair, 950, live = false), read.ev!!, 1e-9)
        assertEquals(fair, read.fair!!, 1e-9)
        assertNull(read.why)
    }

    @Test
    fun `CNO's row is the same bet by game, market, side and line, a Novig row first, never another day's game`() {
        val p = pick()
        val dk = cnoRow(book = "DraftKings")
        val novig = cnoRow()
        assertSame(novig, Index(listOf(dk, novig), emptyList()).cnoRowFor(p))
        assertSame(dk, Index(listOf(dk), emptyList()).cnoRowFor(p))
        assertNull(Index(listOf(cnoRow(startsAtMs = start + 24 * 3_600_000L)), emptyList()).cnoRowFor(p))
        assertNull(Index(listOf(cnoRow(bet = "Carson Kelly Under 0.5")), emptyList()).cnoRowFor(p))
    }

    @Test
    fun `CNO says why it has no number`() {
        val p = pick()
        assertEquals("not on CNO's +EV list", ParlayCompare.cno(p, Index(emptyList(), emptyList()), now, now, true).why)
        assertEquals("CNO is asleep (Vigilant only)", ParlayCompare.cno(p, Index(listOf(cnoRow()), emptyList()), now, now, false).why)
        assertEquals("CNO's list hasn't been read yet: open the CNO tab", ParlayCompare.cno(p, Index(listOf(cnoRow()), emptyList()), null, now, true).why)
        // An hour-old list isn't compared (a game 5 hours off: 10 minutes at most).
        assertEquals("CNO's list is 60 min old: open the CNO tab", ParlayCompare.cno(p, Index(listOf(cnoRow()), emptyList()), now - 3_600_000L, now, true).why)
        assertEquals("CNO lists it with no fair odds", ParlayCompare.cno(p, Index(listOf(cnoRow(fair = null)), emptyList()), now, now, true).why)
    }

    @Test
    fun `Vigilant's EV comes from the last scan's same Novig outcome while fresh, else the read made for the picks, newest first`() {
        val p = pick()
        val scanned = Index(emptyList(), listOf(opportunity("o-kelly-over", 0.11, now - 60_000L)))
        val v = ParlayCompare.vigilant(p, scanned, emptyMap(), now, vigilantOn = true)
        assertEquals(ParlayCompare.evAt(0.11, p, now), v.ev!!, 1e-9)
        assertEquals(now - 60_000L, v.atMs)
        // A newer bets-only read wins over the scan's.
        val read = mapOf(p.key to OpenBetPricer.FairRead(0.10, now - 10_000L, null))
        assertEquals(0.10, ParlayCompare.vigilant(p, scanned, read, now, true).fair!!, 1e-9)
        // A stale scan (an hour ago) isn't used; the read is.
        val stale = Index(emptyList(), listOf(opportunity("o-kelly-over", 0.11, now - 3_600_000L)))
        assertEquals(0.10, ParlayCompare.vigilant(p, stale, read, now, true).fair!!, 1e-9)
        // Found by wording when the outcome id differs (the scan's own id for the same bet).
        val byWords = Index(emptyList(), listOf(opportunity("other-id", 0.12, now - 60_000L)))
        assertEquals(0.12, ParlayCompare.vigilant(p, byWords, emptyMap(), now, true).fair!!, 1e-9)
    }

    @Test
    fun `Vigilant says why it has no number`() {
        val p = pick()
        val none = Index(emptyList(), emptyList())
        assertEquals("Vigilant's scanner is asleep (CNO only)", ParlayCompare.vigilant(p, none, emptyMap(), now, vigilantOn = false).why)
        assertEquals("reading Vigilant's fair odds…", ParlayCompare.vigilant(p, none, emptyMap(), now, true, reading = true).why)
        assertEquals("not read yet: tap Recheck", ParlayCompare.vigilant(p, none, emptyMap(), now, true).why)
        val why = OpenBetPricer.FairRead(null, null, "No fair-odds source has a line for this bet")
        assertEquals("No fair-odds source has a line for this bet", ParlayCompare.vigilant(p, none, mapOf(p.key to why), now, true).why)
        val old = OpenBetPricer.FairRead(0.1, now - 30 * 60_000L, null)
        assertEquals("Vigilant's read is 30 min old: tap Recheck", ParlayCompare.vigilant(p, none, mapOf(p.key to old), now, true).why)
    }

    @Test
    fun `a pick keeps Novig's market and outcome from its live price`() {
        val p = pick()
        assertEquals("m-hr", p.marketId)
        assertEquals("o-kelly-over", p.outcomeId)
    }
}
