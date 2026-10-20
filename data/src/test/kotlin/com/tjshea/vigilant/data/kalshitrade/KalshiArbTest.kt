package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.book.Sportsbook
import com.tjshea.vigilant.data.book.SportsbookScanner
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** KV5: a sure profit exists only when the opposite sides, bought one at each venue on the SAME line, cost under $1 after both fees. */
class KalshiArbTest {
    private val now = 1_790_500_000_000L
    private val start = now + 5 * 3_600_000L
    private val nfl: League = Leagues.ALL.first { it.oddsApiSportKey == "americanfootball_nfl" }

    private fun ev(id: String, book: String, markets: List<RefBookMarket>) = RefEvent(id, nfl.oddsApiSportKey, start, "Dallas Cowboys", "Baltimore Ravens", markets)
    private fun ml(book: String, title: String, away: RefQuote, home: RefQuote) = RefBookMarket(book, title, LineKind.MONEYLINE, listOf(away, home), now - 3_000)
    private fun spread(book: String, title: String, homeLine: Double, away: RefQuote, home: RefQuote) =
        RefBookMarket(book, title, LineKind.SPREAD, listOf(away.copy(point = -homeLine), home.copy(point = homeLine)), now - 3_000)

    private class Fake(override val id: String, override val displayName: String, val snap: RefSnapshot) : ReferenceSource {
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = snap
    }

    private val settings = ScanSettings(
        leagues = setOf("NFL"), fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE, sharpBooks = setOf("pinnacle"), minBooks = 1,
        usePolymarket = false, useKalshi = false, useOddsApi = false,
    )

    /** The Novig side stands in as a book (its prices are posted, no fee pregame), priced against Pinnacle; Kalshi's quotes are its venue read. */
    private suspend fun novigStandIn(ravens: Double, cowboys: Double, extra: List<RefBookMarket> = emptyList(), pinnExtra: List<RefBookMarket> = emptyList()): ScanResult {
        val pinn = ev("p", "pinnacle", listOf(ml("pinnacle", "Pinnacle", RefQuote(Side.AWAY, 2.3, null), RefQuote(Side.HOME, 1 + 100.0 / 150, null))) + pinnExtra)
        val standin = ev("n", "betmgm", listOf(ml("betmgm", "Novig", RefQuote(Side.AWAY, 1 / ravens, null), RefQuote(Side.HOME, 1 / cowboys, null))) + extra)
        val sources = listOf(
            Fake("pinnacle", "Pinnacle", RefSnapshot(nfl.oddsApiSportKey, listOf(pinn), now, provider = "pinnacle")),
            Fake("standin", "stand-in", RefSnapshot(nfl.oddsApiSportKey, listOf(standin), now, provider = "standin")),
        )
        val scanner = SportsbookScanner(Sportsbook.BETMGM, clock = { now }, boardLineFeeds = listOf("standin"), boardFeeds = listOf("standin"), askFeedsForBook = false)
        return scanner.scan(settings, sources).result!!
    }

    private fun kalshi(ravens: Double, cowboys: Double, size: Double = 400.0, extra: List<RefBookMarket> = emptyList()): List<RefSnapshot> {
        val k = ev(
            "kalshi:26OCT25BALDAL", "kalshi",
            listOf(
                ml("kalshi", "Kalshi", RefQuote(Side.AWAY, 1 / ravens, null, "KXNFLGAME-26OCT25BALDAL-BAL|yes", size), RefQuote(Side.HOME, 1 / cowboys, null, "KXNFLGAME-26OCT25BALDAL-DAL|yes", size)),
            ) + extra,
        )
        return listOf(RefSnapshot(nfl.oddsApiSportKey, listOf(k), now, provider = "kalshi-venue"))
    }

    @Test
    fun `a Novig side and the opposite Kalshi side that cost under a dollar together after both fees are listed with the profit`() = runTest {
        val novig = novigStandIn(ravens = 0.33, cowboys = 0.70)
        val arbs = KalshiArb.find(novig, kalshi(ravens = 0.40, cowboys = 0.62), ArbSettings(minPayout = 1.0), now)
        val a = arbs.single()
        assertEquals("Baltimore Ravens", a.novig.selection)
        assertEquals("KXNFLGAME-26OCT25BALDAL-DAL", a.kalshiTicker)
        assertEquals("yes", a.kalshiSide)
        val feeK = 0.07 * 0.62 * 0.38
        assertEquals(1.0 - (0.33 + 0.62 + feeK), a.profitPerDollar, 1e-9)
        assertTrue(a.safe)
        assertEquals(400.0, a.maxPayout, 1e-9)
    }

    @Test
    fun `fees decide it - prices that look like an arbitrage before Kalshi's fee are not one after it`() = runTest {
        // 0.37 + 0.62 = 0.99 before fees; Kalshi's fee on 62c is 1.65c: a loss of 0.65c.
        val novig = novigStandIn(ravens = 0.37, cowboys = 0.70)
        assertTrue(KalshiArb.find(novig, kalshi(0.40, 0.62), ArbSettings(minProfit = 0.0, minPayout = 1.0), now).isEmpty())
    }

    @Test
    fun `the same side on both venues is never paired, and a Kalshi quote that is stale is ignored`() = runTest {
        val novig = novigStandIn(ravens = 0.20, cowboys = 0.80)
        // Kalshi Cowboys 0.85 and Ravens 0.20: Novig Ravens 0.20 + Kalshi Ravens would be the same side; only opposite sides count.
        val arbs = KalshiArb.find(novig, kalshi(ravens = 0.20, cowboys = 0.85), ArbSettings(minProfit = 0.0, minPayout = 1.0), now)
        assertTrue(arbs.none { it.novig.selection == "Baltimore Ravens" && it.kalshiTicker.endsWith("BAL") })
        val old = KalshiArb.find(novigStandIn(0.33, 0.70), kalshi(0.40, 0.62), ArbSettings(minPayout = 1.0), now + 10 * 60_000L)
        assertTrue(old.isEmpty())
    }

    @Test
    fun `a spread pairs only when both venues are on the same number`() = runTest {
        val novigSpread = spread("betmgm", "Novig", -3.5, RefQuote(Side.AWAY, 1 / 0.30, null), RefQuote(Side.HOME, 1 / 0.75, null))
        val pinnSpread = spread("pinnacle", "Pinnacle", -3.5, RefQuote(Side.AWAY, 1.909, null), RefQuote(Side.HOME, 1.909, null))
        val novig = novigStandIn(0.5, 0.52, extra = listOf(novigSpread), pinnExtra = listOf(pinnSpread))
        val sameNumber = spread("kalshi", "Kalshi", -3.5, RefQuote(Side.AWAY, 1 / 0.40, null, "KXNFLSPREAD-26OCT25BALDAL-DAL4|no", 300.0), RefQuote(Side.HOME, 1 / 0.62, null, "KXNFLSPREAD-26OCT25BALDAL-DAL4|yes", 300.0))
        val otherNumber = spread("kalshi", "Kalshi", -2.5, RefQuote(Side.AWAY, 1 / 0.40, null, "KXNFLSPREAD-26OCT25BALDAL-DAL3|no", 300.0), RefQuote(Side.HOME, 1 / 0.62, null, "KXNFLSPREAD-26OCT25BALDAL-DAL3|yes", 300.0))
        val hit = KalshiArb.find(novig, kalshi(0.9, 0.2, extra = listOf(sameNumber)), ArbSettings(minPayout = 1.0), now)
        assertTrue(hit.toString(), hit.any { it.kind == LineKind.SPREAD })
        val miss = KalshiArb.find(novig, kalshi(0.9, 0.2, extra = listOf(otherNumber)), ArbSettings(minPayout = 1.0), now)
        assertTrue(miss.none { it.kind == LineKind.SPREAD })
    }
}
