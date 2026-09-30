package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.Fixtures
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.Pricing
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * "Check odds now" reads every open bet both ways (Tj, 2026-09-30: "always scan relevant vigilant odds in addition to the cno scan. The goal
 * is to always get full updates on all of my bets and an accurate stats reading"): CNO's page (or ParlayAPI's books) and Vigilant's own fair
 * odds. Each read is kept on the bet; a bet read both ways shows and counts the average of the two fair lines, which is also its closing line
 * so far; one read alone is used as is; neither says why.
 */
class BothReadsTest {

    @get:Rule val tmp = TemporaryFolder()
    private var now = Fixtures.START_MS - 86_400_000L
    private val settings = ScanSettings(fairSource = FairSource.SHARP, minEvPercent = 0.0)

    private fun scan(pinDal: Double = 2.45): ScanResult {
        val event = NovigEvent(Fixtures.EVENT_ID, "FOOTBALL", "NFL", "OPEN_PREGAME", "Baltimore Ravens @ Dallas Cowboys", Fixtures.START_MS)
        val market = NovigMarket(Fixtures.ML_MARKET, Fixtures.EVENT_ID, "MONEY", "OPEN", "DAL", Fixtures.START_MS, MarketFee.GAME,
            listOf(NovigOutcome(Fixtures.ML_DAL, "DAL", "TBD"), NovigOutcome(Fixtures.ML_BAL, "BAL", "TBD")))
        val refs = mapOf("americanfootball_nfl" to RefSnapshot("americanfootball_nfl",
            TheOddsApiClient.parseEvents(Fixtures.oddsApi.replace("\"price\":2.45", "\"price\":$pinDal"), Json { ignoreUnknownKeys = true }), now))
        val plan = Planner.plan(listOf(event), listOf(market), refs, settings, now)
        val book = NovigBook(Fixtures.ML_MARKET, 1, mapOf(Fixtures.ML_DAL to listOf(BidLevel(380, 1000)), Fixtures.ML_BAL to listOf(BidLevel(615, 1000))), now)
        return Pricing.price(plan, mapOf(Fixtures.ML_MARKET to book), settings, now)
    }

    private fun dal(r: ScanResult) = r.opportunities.first { it.outcome.outcomeId == Fixtures.ML_DAL }

    private fun tracker() = BetTracker(File(tmp.root, "bets.json"), clock = { now })

    /** What CNO's page read writes on a bet ([BetRecheck]'s change), at [fair]. */
    private suspend fun cnoRead(t: BetTracker, id: String, fair: Double, at: Long = now) = t.edit(id) {
        it.copy(nowFair = fair, nowEv = fair / it.cost - 1.0, nowAtMs = at, nowVia = BetTracker.VIA_CNO, cnoFair = fair, cnoAtMs = at, closingFair = fair, closingSeenAtMs = at)
    }

    @Test
    fun `Vigilant's read beside CNO's is kept apart and never touches the EV, the close or a reason`() = runTest {
        val t = tracker()
        val a = t.track(dal(scan()), stake = 10.0)!!
        cnoRead(t, a.id, 0.40)
        val result = scan(pinDal = 2.30)
        assertEquals(BetTracker.Applied(1, 0), t.applyPricing(result, listOf(a.id), mapOf(a.id to "nope"), alongside = true))
        val b = t.all().single()
        assertEquals(dal(result).fairProbability!!, b.vigFair!!, 1e-12)
        assertEquals(now, b.vigAtMs)
        assertEquals(0.40, b.nowFair!!, 0.0)
        assertEquals(BetTracker.VIA_CNO, b.nowVia)
        assertEquals(0.40, b.closingFair!!, 0.0)
        assertNull(b.nowNote)
        // A pass that doesn't price it alongside leaves no reason either: the merge decides.
        t.applyPricing(null, listOf(a.id), mapOf(a.id to "no line"), alongside = true)
        assertNull(t.all().single().nowNote)
    }

    @Test
    fun `a bet read both ways shows and counts the average of the two, which is also its close so far`() = runTest {
        val t = tracker()
        val a = t.track(dal(scan()), stake = 10.0)!!
        val began = now
        now += 5_000
        cnoRead(t, a.id, 0.40)
        val result = scan(pinDal = 2.30)
        t.applyPricing(result, listOf(a.id), alongside = true)
        val vig = dal(result).fairProbability!!
        now += 1_000
        val merged = t.mergeReads(listOf(a.id), since = began)
        assertEquals(setOf(a.id), merged.both)
        val b = t.all().single()
        val avg = (0.40 + vig) / 2
        assertEquals(avg, b.nowFair!!, 1e-12)
        assertEquals(avg / b.cost - 1.0, b.nowEv!!, 1e-12)
        assertEquals(BetTracker.VIA_BOTH, b.nowVia)
        assertEquals(avg, b.closingFair!!, 1e-12)
        // The two reads stay on the bet, for its card.
        assertEquals(0.40, b.cnoFair!!, 0.0)
        assertEquals(vig, b.vigFair!!, 0.0)
        // The Tracker's counter counts the averaged EV.
        val stats = CheckOddsStats.of(t.all(), began)
        assertEquals(1, stats.priced)
        assertEquals(if (avg / b.cost - 1.0 > 0) 1 else 0, stats.positive)
    }

    @Test
    fun `CNO's read alone stays as read, Vigilant's alone takes over, neither says why unless CNO already did`() = runTest {
        val t = tracker()
        val began = now
        val cnoOnly = t.track(dal(scan()), stake = 1.0)!!
        val vigOnly = t.track(dal(scan()), stake = 2.0)!!
        val neither = t.track(dal(scan()), stake = 3.0)!!
        val cnoSaid = t.track(dal(scan()), stake = 4.0)!!
        now += 5_000
        cnoRead(t, cnoOnly.id, 0.41)
        t.edit(cnoSaid.id) { it.copy(nowNote = "CrazyNinjaOdds had stopped answering", nowNoteAtMs = now) }
        val result = scan(pinDal = 2.30)
        t.applyPricing(result, listOf(vigOnly.id), alongside = true)
        val merged = t.mergeReads(
            listOf(cnoOnly.id, vigOnly.id, neither.id, cnoSaid.id), since = began,
            reasons = mapOf(neither.id to "No fair-odds source has a line for this bet", cnoSaid.id to "No fair-odds source has a line for this bet"),
        )
        assertEquals(setOf(cnoOnly.id), merged.cnoOnly)
        assertEquals(setOf(vigOnly.id), merged.vigOnly)
        assertEquals(setOf(neither.id, cnoSaid.id), merged.neither)
        val by = t.all().associateBy { it.id }
        assertEquals(0.41, by.getValue(cnoOnly.id).nowFair!!, 0.0)
        assertEquals(BetTracker.VIA_CNO, by.getValue(cnoOnly.id).nowVia)
        assertEquals(dal(result).fairProbability!!, by.getValue(vigOnly.id).nowFair!!, 1e-12)
        assertEquals(BetTracker.VIA_VIGILANT, by.getValue(vigOnly.id).nowVia)
        assertEquals(by.getValue(vigOnly.id).nowFair, by.getValue(vigOnly.id).closingFair)
        assertEquals("No fair-odds source has a line for this bet", by.getValue(neither.id).nowNote)
        assertEquals("CrazyNinjaOdds had stopped answering", by.getValue(cnoSaid.id).nowNote)
    }

    @Test
    fun `a CNO page read in the minute before the tap counts as this check's, an older one doesn't`() = runTest {
        val t = tracker()
        val a = t.track(dal(scan()), stake = 10.0)!!
        val stale = t.track(dal(scan()), stake = 10.0)!!
        val began = now
        cnoRead(t, a.id, 0.40, at = began - 30_000)
        cnoRead(t, stale.id, 0.40, at = began - 10 * 60_000)
        val result = scan(pinDal = 2.30)
        t.applyPricing(result, listOf(a.id, stale.id), alongside = true)
        val merged = t.mergeReads(listOf(a.id, stale.id), since = began, cnoSince = began - BetRecheck.FRESH_MS)
        assertEquals(setOf(a.id), merged.both)
        assertEquals(setOf(stale.id), merged.vigOnly)
    }

    @Test
    fun `the toast still adds up to the open bets, and says how many were read both ways`() {
        // 5 open bets: CNO read 2, failed 1 (Vigilant covered it), and 2 are Vigilant's own (one priced, one not).
        val cno = BetRecheck.Report(open = 5, checked = 3, updated = 2, failed = 1, vigilantOnly = 2, cnoTried = 3, cnoFailed = 1, unreadIds = listOf("c"))
        val merged = BetTracker.Merged(both = setOf("a"), cnoOnly = setOf("b"), vigOnly = setOf("c", "d"), neither = setOf("e"))
        val r = cno.withEveryRead(merged, ownIds = listOf("d", "e"))
        assertEquals(1, r.both)
        assertEquals(0, r.failed)
        assertEquals(0, r.vigilantOnly)
        assertEquals(2, r.priced) // c (CNO's miss) and d
        assertEquals(1, r.unpriced) // e
        assertEquals(4, r.covered)
        assertEquals(5, r.covered + r.unpriced)
        assertTrue(r.summary().contains("1 read by both CNO and Vigilant (their fair lines averaged)"))
    }
}
