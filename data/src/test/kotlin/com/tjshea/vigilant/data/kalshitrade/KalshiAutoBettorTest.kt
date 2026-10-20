package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.trading.BetLimits
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** KV3b: the Novig app's auto-bet rules, run over the Kalshi scan: a passing bet is ordered (or papered), a failing one says why. */
class KalshiAutoBettorTest {
    private val now = 1_790_500_000_000L
    private val start = now + 3 * 3_600_000L
    private val nfl: League = Leagues.ALL.first { it.oddsApiSportKey == "americanfootball_nfl" }

    private class Fake(override val id: String, override val displayName: String, val snap: RefSnapshot) : ReferenceSource {
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = snap
    }

    private fun ev(id: String, book: String, away: Double, home: Double, ids: Boolean = false) = RefEvent(
        id, nfl.oddsApiSportKey, start, "Dallas Cowboys", "Baltimore Ravens",
        listOf(
            RefBookMarket(
                book, book, LineKind.MONEYLINE,
                listOf(
                    RefQuote(Side.AWAY, 1 / away, null, if (ids) "KXNFLGAME-26OCT25BALDAL-BAL|yes" else null, if (ids) 800.0 else null),
                    RefQuote(Side.HOME, 1 / home, null, if (ids) "KXNFLGAME-26OCT25BALDAL-DAL|yes" else null, if (ids) 800.0 else null),
                ),
                now - 2_000,
            ),
        ),
    )

    // Two sharp books agree the Ravens are 43.5%; Kalshi sells them at 40c.
    private fun sources() = listOf(
        Fake("pinnacle", "Pinnacle", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("p", "pinnacle", 0.435, 0.575)), now, provider = "pinnacle")),
        Fake("oddspapi", "Circa", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("c", "circa", 0.436, 0.574)), now, provider = "oddspapi")),
        Fake(KalshiClient.VENUE_ID, "Kalshi", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("kalshi:26OCT25BALDAL", "kalshi", 0.40, 0.62, ids = true)), now, provider = KalshiClient.VENUE_ID)),
    )

    private val settings = ScanSettings(
        leagues = setOf("NFL"), fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE, sharpBooks = setOf("pinnacle", "circa"), minBooks = 1,
        usePolymarket = false, useKalshi = false, useOddsApi = false, sgoPro = false, oddsPapi = true,
        autoBet = true, autoBetMinEv = 0.02, autoBetBooks = 2, autoBetTwoSided = 2, autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 10.0, autoBetMaxStake = 10.0,
        trapEarlyHours = 0, trapFirstListed = false, sharpAutoBet = SharpMode.OFF, apiMaxPerDay = 100.0, apiMaxPerGame = 0.0,
    )

    private suspend fun result(): ScanResult = KalshiScanner.create { now }.scan(settings, sources()).result!!

    private fun tracker() = BetTracker(File.createTempFile("kalshi-auto", ".json").also { it.delete() }, { now })

    private class FakeClient(val now: Long) : KalshiTradingClient(OkHttpClient(), Json, { null }) {
        val buys = ArrayList<List<Any?>>()
        override val ready get() = true
        override suspend fun tops(tickers: Collection<String>) = mapOf(
            "KXNFLGAME-26OCT25BALDAL-BAL" to KalshiTop("KXNFLGAME-26OCT25BALDAL-BAL", 0.38, 0.40, 800.0, 800.0, "active"),
            "KXNFLGAME-26OCT25BALDAL-DAL" to KalshiTop("KXNFLGAME-26OCT25BALDAL-DAL", 0.60, 0.62, 800.0, 800.0, "active"),
        ).filterKeys { it in tickers }
        override suspend fun buy(ticker: String, outcomeSide: String, price: Double, count: Long, clientOrderId: String, tif: String, postOnly: Boolean, expirationMs: Long?): KalshiPlaced {
            buys += listOf(ticker, outcomeSide, price, count)
            return KalshiPlaced("o-${buys.size}", clientOrderId, count.toDouble(), 0.0, price, KalshiFees.takerFeeDollars(count.toDouble(), price) / count)
        }
        override suspend fun order(orderId: String): KalshiOrder? = null
        override suspend fun fills(sinceMs: Long?, ticker: String?, orderId: String?, limit: Int): List<KalshiFill> =
            buys.lastOrNull()?.let { b -> listOf(KalshiFill("f-${buys.size}", orderId ?: "", b[0] as String, b[1] as String, (b[3] as Long).toDouble(), b[2] as Double, true, KalshiFees.takerFeeDollars((b[3] as Long).toDouble(), b[2] as Double), now)) } ?: emptyList()
    }

    private fun bettor(client: FakeClient, t: BetTracker, paper: BetTracker? = null): KalshiAutoBettor {
        val limits = { BetLimits(10.0, 100.0) }
        return KalshiAutoBettor(KalshiBetPlacer(client, t, limits, clock = { now }, pause = {}), t, paper, wallet = { 100.0 }, clock = { now })
    }

    @Test
    fun `a bet that passes the Novig app's rules is ordered at Kalshi once, as an IOC at the judged ask`() = runTest {
        val client = FakeClient(now)
        val t = tracker()
        val report = bettor(client, t).run(settings, result(), paper = false)
        assertEquals(report.toString(), 1, report.placed.size)
        assertEquals("KXNFLGAME-26OCT25BALDAL-BAL", client.buys.single()[0])
        assertEquals("yes", client.buys.single()[1])
        assertEquals(1, t.all().size)
        // The next pass finds the market open and orders nothing more.
        val again = bettor(client, t).run(settings, result(), paper = false)
        assertTrue(again.toString(), again.placed.isEmpty() && client.buys.size == 1)
    }

    @Test
    fun `paper mode records what would be ordered and sends nothing`() = runTest {
        val client = FakeClient(now)
        val paper = tracker()
        val report = bettor(client, tracker(), paper).run(settings, result(), paper = true)
        assertEquals(1, report.paper.size)
        assertTrue(client.buys.isEmpty())
        assertEquals(1, paper.all().size)
    }

    @Test
    fun `the auto-bet off, or the edge under the minimum, orders nothing and says why`() = runTest {
        val client = FakeClient(now)
        assertTrue(bettor(client, tracker()).run(settings.copy(autoBet = false), result(), paper = false).placed.isEmpty())
        val strict = bettor(client, tracker()).run(settings.copy(autoBetMinEv = 0.20), result(), paper = false)
        assertTrue(strict.placed.isEmpty())
        assertTrue(strict.toString(), strict.skipped.keys.any { it.contains("under") })
        assertTrue(client.buys.isEmpty())
    }
}
