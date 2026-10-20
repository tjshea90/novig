package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** KV2: the Kalshi scan prices Kalshi's buy prices against the other books' fair line, with Kalshi's fee, and never against Kalshi itself. */
class KalshiScannerTest {
    private val now = 1_790_500_000_000L
    private val start = now + 5 * 3_600_000L
    private val nfl: League = Leagues.ALL.first { it.oddsApiSportKey == "americanfootball_nfl" }

    private fun event(id: String, bookKey: String, title: String, ml: List<RefQuote>) = RefEvent(
        id, nfl.oddsApiSportKey, start, "Dallas Cowboys", "Baltimore Ravens",
        listOf(RefBookMarket(bookKey, title, LineKind.MONEYLINE, ml, now - 5_000)),
    )

    private class Fake(override val id: String, override val displayName: String, val snap: RefSnapshot) : ReferenceSource {
        override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot = snap
    }

    /** Pinnacle -150 / +130 (fair .5798 Cowboys); Kalshi sells the Ravens at 40c (2.5) and the Cowboys at 62c. */
    private fun sources(): List<ReferenceSource> {
        val pinn = event("p1", "pinnacle", "Pinnacle", listOf(RefQuote(Side.HOME, 1 + 100.0 / 150, null), RefQuote(Side.AWAY, 2.3, null)))
        val kal = event(
            "kalshi:26OCT25BALDAL", "kalshi", "Kalshi",
            listOf(
                RefQuote(Side.AWAY, 1 / 0.40, null, "KXNFLGAME-26OCT25BALDAL-BAL|yes", 250.0),
                RefQuote(Side.HOME, 1 / 0.62, null, "KXNFLGAME-26OCT25BALDAL-DAL|yes", 300.0),
            ),
        )
        return listOf(
            Fake("pinnacle", "Pinnacle", RefSnapshot(nfl.oddsApiSportKey, listOf(pinn), now, provider = "pinnacle")),
            Fake(KalshiClient.VENUE_ID, "Kalshi (to bet)", RefSnapshot(nfl.oddsApiSportKey, listOf(kal), now, provider = KalshiClient.VENUE_ID)),
        )
    }

    private val settings = ScanSettings(
        leagues = setOf("NFL"),
        fairSource = FairSource.SHARP,
        devigMethod = DevigMethod.MULTIPLICATIVE,
        sharpBooks = setOf("pinnacle"),
        minBooks = 1,
        usePolymarket = false, useKalshi = false, useOddsApi = false,
    )

    @Test
    fun `a Kalshi price is priced against the other books with Kalshi's fee and carries its ticker and size`() = runTest {
        val scanner = KalshiScanner.create { now }
        val result = scanner.scan(settings, sources()).result!!
        val rav = result.opportunities.single { it.selection == "Baltimore Ravens" }
        val fair = (1 / 2.3) / (1 / 2.3 + 1 / (1 + 100.0 / 150))
        assertEquals(fair, rav.fairProbability!!, 1e-9)
        // Taker fee 0.07 x .4 x .6 per contract, subtracted from the payout.
        assertEquals(0.07 * 0.4 * 0.6, rav.quote!!.fee, 1e-9)
        assertEquals(0.40, rav.quote!!.price, 1e-12)
        assertEquals("KXNFLGAME-26OCT25BALDAL-BAL|yes", rav.outcome.bookRef!!.outcomeId)
        assertEquals(250L, rav.ladder.first().contracts)
        assertTrue(rav.evPercent!! > 0.0)
        assertTrue(result.opportunities.single { it.selection == "Dallas Cowboys" }.evPercent!! < 0.0)
    }
}
