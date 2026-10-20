package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.kalshitrade.KalshiAutoMode
import com.tjshea.vigilant.data.kalshitrade.KalshiBalance
import com.tjshea.vigilant.data.kalshitrade.KalshiConfig
import com.tjshea.vigilant.data.kalshitrade.KalshiEngineConfig
import com.tjshea.vigilant.data.kalshitrade.KalshiEngineStatus
import com.tjshea.vigilant.data.kalshitrade.KalshiFees
import com.tjshea.vigilant.data.kalshitrade.KalshiFill
import com.tjshea.vigilant.data.kalshitrade.KalshiOrder
import com.tjshea.vigilant.data.kalshitrade.KalshiPlaced
import com.tjshea.vigilant.data.kalshitrade.KalshiScanner
import com.tjshea.vigilant.data.kalshitrade.KalshiTop
import com.tjshea.vigilant.data.kalshitrade.KalshiTradingClient
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
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** KV9: the engine's Kalshi lane is off unless the phone says so, papers without orders, orders only when LIVE, and publishes its status. */
class CloudKalshiLaneTest {
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

    private fun sources() = listOf(
        Fake("pinnacle", "Pinnacle", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("p", "pinnacle", 0.435, 0.575)), now, provider = "pinnacle")),
        Fake("oddspapi", "Circa", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("c", "circa", 0.436, 0.574)), now, provider = "oddspapi")),
    )

    private val venue = Fake(KalshiClient.VENUE_ID, "Kalshi", RefSnapshot(nfl.oddsApiSportKey, listOf(ev("kalshi:26OCT25BALDAL", "kalshi", 0.40, 0.62, ids = true)), now, provider = KalshiClient.VENUE_ID))

    private val phone = ScanSettings(
        leagues = setOf("NFL"), fairSource = FairSource.SHARP, devigMethod = DevigMethod.MULTIPLICATIVE, sharpBooks = setOf("pinnacle", "circa"), minBooks = 1,
        oddsPapi = true, usePolymarket = false, useOddsApi = false, sgoPro = false,
    )

    private class FakeClient(val now: Long) : KalshiTradingClient(OkHttpClient(), Json, { null }) {
        val buys = ArrayList<List<Any?>>()
        override val ready get() = true
        override suspend fun balance() = KalshiBalance(100.0, 0.0)
        override suspend fun tops(tickers: Collection<String>) = mapOf(
            "KXNFLGAME-26OCT25BALDAL-BAL" to KalshiTop("KXNFLGAME-26OCT25BALDAL-BAL", 0.38, 0.40, 800.0, 800.0, "active"),
            "KXNFLGAME-26OCT25BALDAL-DAL" to KalshiTop("KXNFLGAME-26OCT25BALDAL-DAL", 0.60, 0.62, 800.0, 800.0, "active"),
        ).filterKeys { it in tickers }
        override suspend fun buy(ticker: String, outcomeSide: String, price: Double, count: Long, clientOrderId: String, tif: String, postOnly: Boolean, expirationMs: Long?): KalshiPlaced {
            buys += listOf(ticker, outcomeSide, price, count, tif, postOnly)
            return KalshiPlaced("o-${buys.size}", clientOrderId, if (postOnly) 0.0 else count.toDouble(), if (postOnly) count.toDouble() else 0.0, price, KalshiFees.takerFeeDollars(count.toDouble(), price) / count)
        }
        override suspend fun order(orderId: String): KalshiOrder? = null
        override suspend fun orders(status: String?, ticker: String?, limit: Int): List<KalshiOrder> = emptyList()
        override suspend fun fills(sinceMs: Long?, ticker: String?, orderId: String?, limit: Int): List<KalshiFill> =
            buys.lastOrNull()?.takeIf { it[4] == "immediate_or_cancel" }?.let { b -> listOf(KalshiFill("f-${buys.size}", orderId ?: "", b[0] as String, b[1] as String, (b[3] as Long).toDouble(), b[2] as Double, true, KalshiFees.takerFeeDollars((b[3] as Long).toDouble(), b[2] as Double), now)) } ?: emptyList()
        override suspend fun settlements(sinceMs: Long?, limit: Int) = emptyList<com.tjshea.vigilant.data.kalshitrade.KalshiSettlement>()
    }

    private fun lane(mode: KalshiAutoMode, client: FakeClient, published: MutableMap<String, String>, key: Boolean = true): CloudKalshiLane {
        val scan = KalshiConfig.defaultScan().copy(
            leagues = setOf("NFL"), autoBet = true, autoBetMinEv = 0.02, autoBetBooks = 2, autoBetTwoSided = 2, autoBetStake = AutoBetStake.CUSTOM, autoBetCustomStake = 10.0, autoBetMaxStake = 10.0,
            trapEarlyHours = 0, trapFirstListed = false, sharpAutoBet = SharpMode.OFF, apiMaxPerDay = 100.0, apiMaxPerGame = 0.0,
        )
        val ec = KalshiEngineConfig(rev = 7, mode = mode, config = KalshiConfig(scan = scan, tradingEnabled = true, autoMode = KalshiAutoMode.LIVE))
        return CloudKalshiLane(
            scope = CoroutineScope(Dispatchers.Unconfined), readConfig = { ec }, trading = client, hasKey = { key }, venue = venue, sources = { sources() }, novigHeld = { _, _ -> null },
            unavailable = { null }, dir = File.createTempFile("kalshilane", "").also { it.delete(); it.mkdirs() }, publish = { n, t -> published[n] = t }, clock = { now },
        )
    }

    private suspend fun novigScan() = KalshiScanner.create { now }.scan(phone, sources() + venue).result!!

    @Test
    fun `PAPER scans and records what it would bet and orders nothing`() = runTest {
        val client = FakeClient(now)
        val out = HashMap<String, String>()
        val l = lane(KalshiAutoMode.PAPER, client, out)
        assertTrue(l.runOnce(novigScan(), phone))
        val st = KalshiEngineStatus.parse(out[KalshiEngineStatus.FILE]!!)!!
        assertEquals(KalshiAutoMode.PAPER, st.mode)
        assertEquals(7L, st.rev)
        assertTrue(st.note, st.listed >= 1)
        assertEquals(1, st.betsPaper)
        assertTrue(client.buys.isEmpty())
    }

    @Test
    fun `LIVE places the auto-bet through the Kalshi key and publishes the carry-over files`() = runTest {
        val client = FakeClient(now)
        val out = HashMap<String, String>()
        val l = lane(KalshiAutoMode.LIVE, client, out)
        l.runOnce(novigScan(), phone)
        assertEquals(1, client.buys.size)
        assertEquals("immediate_or_cancel", client.buys.single()[4])
        assertNotNull(out["kalshi-bets.json"])
        assertTrue(out["kalshi-bets.json"]!!.contains("KXNFLGAME") || out["kalshi-bets.json"]!!.length > 10)
        assertEquals(1, KalshiEngineStatus.parse(out[KalshiEngineStatus.FILE]!!)!!.betsPlaced)
    }

    @Test
    fun `LIVE without the key on GitHub is paper, and OFF does nothing at all`() = runTest {
        val noKey = FakeClient(now)
        val out = HashMap<String, String>()
        lane(KalshiAutoMode.LIVE, noKey, out, key = false).runOnce(novigScan(), phone)
        assertTrue(noKey.buys.isEmpty())
        val off = FakeClient(now)
        val out2 = HashMap<String, String>()
        lane(KalshiAutoMode.OFF, off, out2).runOnce(novigScan(), phone)
        assertTrue(off.buys.isEmpty())
        assertEquals(KalshiAutoMode.OFF, KalshiEngineStatus.parse(out2[KalshiEngineStatus.FILE]!!)!!.mode)
    }
}
