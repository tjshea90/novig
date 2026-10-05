package com.tjshea.vigilant.data.novig.trading

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.PositiveDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pinnacle only (Tj, 2026-10-05): what the auto-bet bets, refuses and stakes when the fair is Pinnacle's devigged price alone. RESEARCH.md §88.5. */
class PinnacleBetTest {
    private val now = 1_800_000_000_000L
    private val rules = AutoBet.rules(ScanSettings(autoBetMinEv = 0.02, autoBetMaxStake = 25.0, autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 10.0))
    private val maxAge = 90_000L

    private fun fair(vararg books: String, asOf: Long = now - 20_000) = FairLine(
        probabilities = listOf(0.52, 0.48), requestedSource = FairSource.SHARP, sourceUsed = FairSource.SHARP, method = DevigMethod.WORST_CASE,
        perBook = books.map { BookFair(BookPrices(it.lowercase(), it, listOf(1.9, 1.95), asOf), listOf(0.52, 0.48), 0.04, true) },
        sharpBooksUsed = books.toList(), averageBooksUsed = emptyList(),
    )

    private fun opp(
        id: String = "m1",
        fairBooks: List<String> = listOf("Pinnacle"),
        fairAsOf: Long? = now - 20_000,
        cost: Double = 0.48,
        fairP: Double? = 0.52,
        bookAt: Long? = now - 5_000,
        starts: Long = now + 3 * 3_600_000L,
        live: Boolean = false,
        label: String = "Player Receiving Yards",
        selection: String = "Player Over 50.5",
        league: String = "NFL",
        depth: Double? = 200.0,
    ): Opportunity {
        val event = NovigEvent("e-$id", "FOOTBALL", league, if (live) NovigEvent.STATUS_LIVE else NovigEvent.STATUS_PREGAME, "A @ B", starts)
        val market = NovigMarket(
            "m-$id", event.eventId, "RECEIVING_YARDS", "OPEN", label, starts, MarketFee.GAME,
            listOf(NovigOutcome("$id-over", "Over 50.5", "TBD"), NovigOutcome("$id-under", "Under 50.5", "TBD")),
        )
        return Opportunity(
            league = Leagues.byNovigName(league)!!, event = event, market = market, outcome = market.outcomes.first(), marketLabel = label, kind = LineKind.PLAYER_PROP,
            selection = selection, fair = fair(*fairBooks.toTypedArray(), asOf = fairAsOf ?: now), fairProbability = fairP,
            quote = fairP?.let { EvQuote(it, cost, 0.0) }, ladder = emptyList(), depth = depth?.let { PositiveDepth(1000, it, 5.0, cost) }, suggestedStake = null,
            novigWidth = null, bookFetchedAtMs = bookAt, fairUpdatedMs = fairAsOf, refEvent = null, lineKey = null, target = null, fairAsOfMs = fairAsOf,
        )
    }

    @Test
    fun `a pregame bet under Pinnacle's price, read this minute, passes`() {
        assertNull(PinnacleBet.judge(rules, opp(), now, maxAge))
        assertTrue(PinnacleBet.pinnacleAlone(opp()))
    }

    @Test
    fun `a fair line with any other book in it is refused, whatever the case of Pinnacle's name`() {
        assertEquals("its fair line isn't Pinnacle's alone", PinnacleBet.judge(rules, opp(fairBooks = listOf("Pinnacle", "DraftKings")), now, maxAge))
        assertEquals("its fair line isn't Pinnacle's alone", PinnacleBet.judge(rules, opp(fairBooks = listOf("DraftKings")), now, maxAge))
        assertNull(PinnacleBet.judge(rules, opp(fairBooks = listOf("pinnacle")), now, maxAge))
    }

    @Test
    fun `Pinnacle's quote must be no older than the limit at the order - the limit itself passes, one second more does not`() {
        assertNull(PinnacleBet.judge(rules, opp(fairAsOf = now - 90_000), now, maxAge))
        assertEquals("Pinnacle's price is older than your limit", PinnacleBet.judge(rules, opp(fairAsOf = now - 91_000), now, maxAge))
        assertEquals("Pinnacle's price has no time on it", PinnacleBet.judge(rules, opp().copy(fairAsOfMs = null), now, maxAge))
        assertEquals(20_000L, PinnacleBet.ageMs(opp(), now))
        // A quote stamped in the future (a feed's clock) is age zero, never negative.
        assertEquals(0L, PinnacleBet.ageMs(opp(fairAsOf = now + 5_000), now))
    }

    @Test
    fun `live, about to start, a Novig price read long ago, no Novig offer and no Pinnacle price are each refused`() {
        assertEquals("not pregame (live betting isn't available)", PinnacleBet.judge(rules, opp(live = true), now, maxAge))
        assertEquals("not pregame (live betting isn't available)", PinnacleBet.judge(rules, opp(starts = now + 30_000), now, maxAge))
        assertNull(PinnacleBet.judge(rules, opp(starts = now + 61_000), now, maxAge))
        assertEquals("Novig's price for it was read too long ago", PinnacleBet.judge(rules, opp(bookAt = now - 20 * 60_000), now, maxAge))
        assertEquals("Novig's price for it was read too long ago", PinnacleBet.judge(rules, opp(bookAt = null), now, maxAge))
        assertEquals("Pinnacle has no price for it", PinnacleBet.judge(rules, opp().copy(fairProbability = null), now, maxAge))
        assertEquals("nobody is selling it at Novig", PinnacleBet.judge(rules, opp().copy(quote = null), now, maxAge))
    }

    @Test
    fun `the edge against Pinnacle must reach the minimum and stay under the too-good-to-be-true cap`() {
        // fair 0.52 against 0.50: +4%; against 0.51: +1.96% (under 2%); against 0.44: +18% (over the 15% cap).
        assertNull(PinnacleBet.judge(rules, opp(cost = 0.50), now, maxAge))
        assertEquals("its edge against Pinnacle is under your minimum", PinnacleBet.judge(rules, opp(cost = 0.51), now, maxAge))
        assertTrue(PinnacleBet.judge(rules, opp(cost = 0.44), now, maxAge)!!.startsWith("its edge against Pinnacle is over 15%"))
        // Exactly the minimum passes (0.5098 → +2.0%).
        assertNull(PinnacleBet.judge(rules.copy(minEv = 0.04), opp(cost = 0.5), now, maxAge))
    }

    @Test
    fun `the kinds and the odds limits of auto-bet apply`() {
        assertEquals("player props aren't among the kinds of bet you auto-bet", PinnacleBet.judge(rules.copy(kinds = setOf(BetKind.MONEYLINE)), opp(), now, maxAge))
        // 0.48 is +108: a +100 cap refuses it, a -200 floor (shortest) does not.
        assertEquals("its odds are longer than your limit", PinnacleBet.judge(rules.copy(maxOdds = 100), opp(), now, maxAge))
        assertNull(PinnacleBet.judge(rules.copy(minOdds = -200), opp(), now, maxAge))
        // 0.70 cost (a -233 favorite) is shorter than a -200 floor.
        assertEquals("its odds are shorter than your limit", PinnacleBet.judge(rules.copy(minOdds = -200), opp(cost = 0.70, fairP = 0.74), now, maxAge))
    }

    @Test
    fun `candidates are the priced outcomes in picked leagues, best edge first`() {
        val a = opp("a", cost = 0.49)
        val b = opp("b", cost = 0.46)
        val c = opp("c", cost = 0.50, league = "MLB")
        val unpriced = opp("d").copy(quote = null)
        val result = ScanResult(emptyList(), listOf(a, b, c, unpriced), ScanStats(0, 0, 0, 0, 0), now)
        val got = PinnacleBet.candidates(result, ScanSettings(leagues = setOf("NFL")))
        assertEquals(listOf("b-over", "a-over"), got.map { it.outcome.outcomeId })
        assertTrue(PinnacleBet.candidates(null, ScanSettings()).isEmpty())
    }

    @Test
    fun `a finished scan is followed by a pass only with Pinnacle only, auto-bet and the background scan on, and not while stopped or paused`() {
        val on = ScanSettings(pinnacleOnly = true, autoBet = true, autoScan = com.tjshea.vigilant.data.scanner.AutoScanMode.BOTH)
        val done = ScanResult(emptyList(), emptyList(), ScanStats(0, 0, 0, 0, 0), now)
        assertTrue(PinnacleBet.passDue(on, done))
        assertTrue(!PinnacleBet.passDue(on, null))
        assertTrue("a partial result is a scan still reading", !PinnacleBet.passDue(on, done.copy(freshSinceMs = now - 1)))
        assertTrue(!PinnacleBet.passDue(on.copy(pinnacleOnly = false), done))
        assertTrue(!PinnacleBet.passDue(on.copy(autoBet = false), done))
        assertTrue("auto-scan off: no background cycle, so no auto-bet", !PinnacleBet.passDue(on.copy(autoScan = com.tjshea.vigilant.data.scanner.AutoScanMode.OFF), done))
        assertTrue(!PinnacleBet.passDue(on.copy(killed = true), done))
        assertTrue(!PinnacleBet.passDue(on.copy(pausedByHand = true), done))
        assertTrue(!PinnacleBet.passDue(on.copy(autoBetHalted = "lost"), done))
    }

    @Test
    fun `the age limits are held between 10 seconds and 5 minutes, and a quote is read again once it passes a third of the limit`() {
        assertEquals(90_000L, PinnacleBet.maxAgeMs(ScanSettings(pinnacleMaxAgeSeconds = 90)))
        assertEquals(10_000L, PinnacleBet.maxAgeMs(ScanSettings(pinnacleMaxAgeSeconds = 1)))
        assertEquals(300_000L, PinnacleBet.maxAgeMs(ScanSettings(pinnacleMaxAgeSeconds = 9_999)))
        assertEquals(30_000L, PinnacleBet.refreshAfterMs(ScanSettings(pinnacleMaxAgeSeconds = 90)))
        assertEquals(10_000L, PinnacleBet.refreshAfterMs(ScanSettings(pinnacleMaxAgeSeconds = 10)))
    }

    @Test
    fun `stakes - a flat amount held to the per-bet maximum and the wallet, Kelly on Pinnacle's fair and Novig's price, never on an empty wallet`() {
        assertEquals(AutoBet.Stake.Amount(10.0), PinnacleBet.stake(rules, opp(), 1_000.0, 100.0))
        assertEquals(AutoBet.Stake.Amount(7.5), PinnacleBet.stake(rules, opp(), 1_000.0, 7.5))
        assertEquals(AutoBet.Stake.Amount(25.0), PinnacleBet.stake(rules.copy(customStake = 40.0), opp(), 1_000.0, 100.0))
        assertEquals(AutoBet.Stake.Amount(1.0), PinnacleBet.stake(rules.copy(stake = AutoBetStake.ONE_DOLLAR), opp(), 1_000.0, 100.0))
        assertEquals(AutoBet.Stake.WalletEmpty, PinnacleBet.stake(rules, opp(), 1_000.0, 0.0))
        // Quarter Kelly of a $1,000 bankroll on fair 0.52 at 0.48: full Kelly (0.52-0.48)/(1-0.48) = 7.69% → 19.23, under the $25 maximum and the $200 for sale.
        val kelly = rules.copy(stake = AutoBetStake.QUARTER_KELLY)
        assertEquals(AutoBet.Stake.Amount(19.23), PinnacleBet.stake(kelly, opp(), 1_000.0, 500.0))
        // Held to what Novig has for sale at +EV, and to nothing at all with no bankroll.
        assertEquals(AutoBet.Stake.Amount(5.0), PinnacleBet.stake(kelly, opp(depth = 5.0), 1_000.0, 500.0))
        assertTrue(PinnacleBet.stake(kelly, opp(), 0.0, 500.0) is AutoBet.Stake.Skip)
    }

    @Test
    fun `Pinnacle only is Vigilant's scan on Pinnacle alone - the scanner choice, the books and the flags that depend on them`() {
        val off = ScanSettings(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.BOTH, useOddsApi = true, useKalshi = true)
        assertEquals(off, off.effective())
        val on = off.copy(pinnacleOnly = true, autoBet = true, autoScan = com.tjshea.vigilant.data.scanner.AutoScanMode.BOTH)
        val e = on.effective()
        assertEquals(com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, e.scanner)
        assertEquals(com.tjshea.vigilant.engine.DevigMethod.WORST_CASE, e.devigMethod)
        assertEquals(setOf("pinnacle"), e.sharpBooks)
        assertEquals(listOf("pinnacle"), e.referenceBooks)
        assertEquals(1, e.minBooks)
        assertTrue(!e.fallbackToAverage && e.usePinnacle && !e.useKalshi && !e.usePolymarket && !e.useOddsApi)
        // The CNO list is asleep, Vigilant's scan runs, and the auto-bet bets from the background Vigilant scan.
        assertTrue(!on.cnoOn && on.vigilantOn && on.autoScansVigilant)
        assertEquals(com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, on.scannerNow)
        assertTrue(on.autoBetsNow)
        // A pause (or the kill switch) stops all of it.
        assertTrue(!on.copy(killed = true).autoBetsNow && !on.copy(pausedByHand = true).autoScansVigilant)
        // Everything the user picked besides the books stays.
        assertEquals(on.leagues, e.leagues)
        assertEquals(on.minEvPercent, e.minEvPercent, 0.0)
    }
}
