package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.scanner.Presets
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tj, 2026-10-02 17:01Z: "record all types of information on the bet as placed, such as odds, books in agreement, time before game start, percent EV, and more.
 * The more information logged the better." [AtBet], kept on the bet and never changed by a re-check.
 */
class AtBetTest {

    private val start = 1_800_000_000_000L
    private val now = start - 3 * 3_600_000L - 20 * 60_000L

    /** Tj's screenshot: Juwan Johnson Under 39.5, Novig +113 ($173), CNO fair +108, 12 books. */
    private val row = CnoRow(
        0.0251, startsAtMs = start, sport = "Football", league = "NFL", event = "Atlanta Falcons @ New Orleans Saints", market = "Player Receiving Yards",
        bet = "Juwan Johnson Under 39.5", odds = 113, available = 173.0, book = "Novig", fairOdds = 108, fairProbability = 0.481, books = 12,
        gameUrl = "https://crazyninjaodds.com/site/browse/game.aspx?side_id=1",
    )

    private val view = CnoBooksView(
        bet = row.bet, otherBet = "Juwan Johnson Over 39.5", fetchedAtMs = now - 12_000L,
        prices = listOf(
            CnoBookPrice("PX", -107, 342.0, -124, 10.0), CnoBookPrice("KI", -107, 352.0, -121, 1250.0), CnoBookPrice("NV", 113, 173.0, -127, 990.0),
            CnoBookPrice("MGM", -115, null, -115, null), CnoBookPrice("MGM-ON", -115, null, -115, null), CnoBookPrice("BR", -113, null, -118, null),
            CnoBookPrice("FD", null, null, -122, null), CnoBookPrice("DK", null, null, -130, null),
        ),
    )

    private val settings = Presets.apply(ScanSettings(bankroll = 200.0), Presets.VOLUME)

    private fun record(live: LivePrice? = LivePrice(113, 173.0, 0.0251, now - 8_000L), how: String = AtBet.HOW_AUTO) =
        AtBets.cno(row, false, view, null, live, now - 30_000L, settings, now, how, BetTracker.SOURCE_CNO, "0.45.0", stake = 1.03, wallet = 25.0)

    @Test
    fun `a CNO bet records its price, its books and what each said, the veto, the start and the rules in force`() {
        val r = record()
        assertEquals(AtBet.HOW_AUTO, r.how)
        assertEquals("0.45.0", r.version)
        assertEquals("Volume + safe CLV", r.preset)
        assertTrue(r.rules!!.startsWith("edge ≥ 2.5%"))
        assertEquals("FOOTBALL", r.sport)
        assertEquals("PROP", r.kind)
        assertEquals(200L, r.minutesToStart)
        assertEquals(113, r.american)
        assertEquals(-127, r.otherAmerican)
        assertEquals(173.0, r.available!!, 0.0)
        assertEquals(8L, r.novigAgeSec)
        assertEquals(30L, r.cnoListAgeSec)
        assertEquals(12L, r.pageAgeSec)
        assertEquals(0.0251, r.ev!!, 0.0)
        assertEquals(0.481, r.fair!!, 0.0)
        assertEquals(108, r.fairAmerican)
        assertEquals(12, r.cnoBooks)
        // The book check: four companies (BetMGM once), every one agreeing, as the sheet said.
        assertEquals(4, r.twoSided)
        assertEquals(4, r.agreeing)
        assertEquals(2, r.oneSided)
        assertEquals(CnoBooks.Verdict.CONFIRMED.name, r.verdict)
        assertEquals(0.489, r.checkFair!!, 0.0005)
        assertTrue(r.dissent.isEmpty())
        // Every book on the page, with its own fair and the EV it gives +113.
        assertEquals(listOf("ProphetX", "Kalshi", "Novig", "BetMGM", "BetMGM (ON)", "BetRivers", "FanDuel", "DraftKings"), r.books.map { it.book })
        val kalshi = r.books.single { it.book == "Kalshi" }
        assertEquals(-107, kalshi.odds)
        assertEquals(-121, kalshi.other)
        assertEquals(0.484, kalshi.fair!!, 0.001)
        assertTrue(kalshi.ev!! > 0.0)
        assertNull(r.books.single { it.book == "FanDuel" }.fair)
        // The veto: Kalshi, the sharpest for props, agreed.
        assertEquals("PASSED", r.sharpVerdict)
        assertEquals("Kalshi", r.sharpBook)
        assertEquals(kalshi.ev!!, r.sharpEv!!, 1e-12)
        // The stake and the money.
        assertEquals("¼ Kelly", r.stakeRule)
        assertEquals(1.03, r.stake!!, 0.0)
        assertEquals(200.0, r.bankroll!!, 0.0)
        assertEquals(25.0, r.wallet!!, 0.0)
        assertTrue(r.fullKelly!! > 0.0)
    }

    @Test
    fun `a live bet's full Kelly is worked out at its cost with Novig's live fee, as its EV is`() {
        val live = AtBets.cno(row, true, view, null, LivePrice(113, 173.0, 0.0251, now - 8_000L), now - 30_000L, settings, now, AtBet.HOW_AUTO, BetTracker.SOURCE_CNO, "0.48.1")
        val price = 1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(113)
        val cost = price + com.tjshea.vigilant.engine.Fees.takerFee(price, com.tjshea.vigilant.engine.MarketFee.GAME, eventLive = true)
        val fair = live.checkFair ?: live.fair!!
        assertEquals(((fair - cost) / (1.0 - cost)).coerceAtLeast(0.0), live.fullKelly!!, 1e-12)
    }

    @Test
    fun `the books that say no are named, at the price taken`() {
        // At -110 instead of +113 every book's own fair says no.
        val r = record(live = LivePrice(-110, 50.0, -0.08, now))
        assertEquals(-110, r.american)
        assertEquals(listOf("ProphetX", "Kalshi", "BetMGM", "BetMGM (ON)", "BetRivers"), r.dissent)
        assertEquals("VETOED", r.sharpVerdict)
        assertEquals(0, r.agreeing)
    }

    @Test
    fun `the record goes on the bet and survives a JSON round trip, a fill and every re-check`() = runBlocking {
        val tracker = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })
        // A ✓ on a card.
        val marked = tracker.logCno(row, row.ev, false, placedKey = "k1", atBet = record(how = AtBet.HOW_MARKED))
        assertEquals(AtBet.HOW_MARKED, tracker.all().single { it.id == marked.id }.atBet?.how)
        // An order through the API: the record rides on the target, with the dollars the order really cost.
        val market = NovigMarket("mkt", "ev", "PROP", "OPEN", "Juwan Johnson Receiving Yards", start, MarketFee.GAME, outcomes = listOf(NovigOutcome("A", "Under 39.5", "TBD"), NovigOutcome("B", "Over 39.5", "TBD")))
        val target = BetTarget(market, "A", "NFL", row.event, start, row.market, row.bet, fair = 0.489, fairAsOfMs = now, source = BetTracker.SOURCE_CNO, placedKey = "k2", auto = true, atBet = record())
        val bet = tracker.logApi(target, "o1", listOf(NovigFill("f1", "o1", null, "mkt", "A", 100, 0.47, true, 0.0, now)))!!
        assertEquals(AtBet.HOW_AUTO, bet.atBet?.how)
        assertEquals(0.47, bet.atBet?.stake!!, 1e-9)
        // Saved and read back whole.
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(bet, json.decodeFromString(TrackedBet.serializer(), json.encodeToString(TrackedBet.serializer(), bet)))
        // A re-check writes the "now" fields and the books, never the record as placed.
        tracker.edit(bet.id) { it.copy(nowEv = -0.01, books = listOf(BookLine("Kalshi", -120, -101))) }
        assertEquals(record().copy(stake = 0.47), tracker.all().single { it.id == bet.id }.atBet)
        // A bet from before v0.45.0 reads with none.
        assertNull(json.decodeFromString(TrackedBet.serializer(), json.encodeToString(TrackedBet.serializer(), bet.copy(atBet = null))).atBet)
    }

    @Test
    fun `a push alert's tick records what the alert carried`() {
        val a = com.tjshea.vigilant.data.alerts.EvAlert(
            scanner = "CNO", key = "k", outcomeId = null, bet = row.bet, market = row.market, event = row.event, american = 113, ev = 0.03, books = 5, agreeing = 4,
            startsAtMs = start, link = null, exact = false, league = "NFL", fair = 0.484,
        )
        val r = AtBets.alert(a, now)
        assertEquals(AtBet.HOW_ALERT, r.how)
        assertEquals(BetTracker.SOURCE_CNO, r.scanner)
        assertEquals("PROP", r.kind)
        assertEquals(200L, r.minutesToStart)
        assertEquals(5, r.twoSided)
        assertEquals(4, r.agreeing)
    }
}
