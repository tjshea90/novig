package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.Leagues
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class SxBetTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Shapes from the real public /markets/active answer of 2026-10-18 (NFL) and the docs' best-odds example.
    private val marketsBody = """{"status":"success","data":{"markets":[
      {"status":"ACTIVE","marketHash":"0xA","outcomeOneName":"Over 49.5","outcomeTwoName":"Under 49.5","teamOneName":"Dallas Cowboys","teamTwoName":"Philadelphia Eagles","type":28,"gameTime":1795728600,"line":49.5,"sportXeventId":"L1","sportId":8,"leagueId":243,"leagueLabel":"NFL","mainLine":true,"extra":1},
      {"status":"ACTIVE","marketHash":"0xB","outcomeOneName":"Dallas Cowboys +2.5","outcomeTwoName":"Philadelphia Eagles -2.5","teamOneName":"Dallas Cowboys","teamTwoName":"Philadelphia Eagles","type":342,"gameTime":1795728600,"line":2.5,"sportXeventId":"L1","sportId":8,"leagueId":243,"leagueLabel":"NFL","mainLine":false},
      {"status":"ACTIVE","marketHash":"0xC","outcomeOneName":"Dallas Cowboys","outcomeTwoName":"Philadelphia Eagles","teamOneName":"Dallas Cowboys","teamTwoName":"Philadelphia Eagles","type":226,"gameTime":1795728600,"sportXeventId":"L1","sportId":8,"leagueId":243,"leagueLabel":"NFL"}
    ],"nextKey":"abc"}}"""

    private fun lvl(pct: Int, usdc: Long = 500) = """{"percentageOdds":"${pct}000000000000000000","size":"${usdc * 1_000_000}"}"""

    private fun best(vararg rows: Triple<String, Int?, Int?>) = """{"status":"success","data":{"bestOdds":[${
        rows.joinToString(",") { (h, a, b) -> """{"marketHash":"$h","outcomeOne":${a?.let { lvl(it) } ?: "null"},"outcomeTwo":${b?.let { lvl(it) } ?: "null"}}""" }
    }]}}"""

    @Test fun oddsAreScaledBy1e20() {
        assertEquals(0.4, SxBetConvert.toProb("40000000000000000000")!!, 1e-12)
        assertEquals(0.00125, SxBetConvert.toProb("125000000000000000")!!, 1e-12)
    }

    @Test fun marketsParseAndCarryTheNextPageKey() {
        val (list, next) = SxBetConvert.parseMarkets(marketsBody, json)
        assertEquals(3, list.size)
        assertEquals("abc", next)
        assertEquals(342, list[1].type)
    }

    @Test fun buildsMoneylineSpreadAndTotalFromBooks() {
        val (markets, _) = SxBetConvert.parseMarkets(marketsBody, json)
        val books = SxBetConvert.parseBest(best(Triple("0xA", 52, 49), Triple("0xB", 55, 46), Triple("0xC", 40, 61)), json).associateBy { it.marketHash }
        val ev = SxBetConvert.events(markets, books, Leagues.byNovigName("NFL")!!, 1L).single()
        assertEquals("Dallas Cowboys", ev.away)
        assertEquals("Philadelphia Eagles", ev.home)
        assertEquals(1795728600_000L, ev.commenceMs)
        val total = ev.markets.single { it.kind == LineKind.TOTAL }
        assertEquals(49.5, total.line)
        assertEquals(1.0 / 0.52, total.quotes.single { it.side == Side.OVER }.decimalOdds, 1e-9)
        val spread = ev.markets.single { it.kind == LineKind.SPREAD }
        assertEquals(2.5, spread.quotes.single { it.side == Side.AWAY }.point)
        assertEquals(-2.5, spread.quotes.single { it.side == Side.HOME }.point)
        val ml = ev.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals(1.0 / 0.40, ml.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-9)
    }

    @Test fun makersFrameOrThinOrOneSidedBooksAreDropped() {
        // sum < 1 = the makers' own frame (a crossed read); a missing side; a $10 level
        assertNull(SxBetConvert.pair(SxBetConvert.Book("h", SxBetConvert.Level(0.40, 500.0), SxBetConvert.Level(0.45, 500.0))))
        assertNull(SxBetConvert.pair(SxBetConvert.Book("h", SxBetConvert.Level(0.52, 500.0), null)))
        assertNull(SxBetConvert.pair(SxBetConvert.Book("h", SxBetConvert.Level(0.52, 10.0), SxBetConvert.Level(0.49, 500.0))))
        assertNull(SxBetConvert.pair(SxBetConvert.Book("h", SxBetConvert.Level(0.60, 500.0), SxBetConvert.Level(0.60, 500.0))))
        assertNotNull(SxBetConvert.pair(SxBetConvert.Book("h", SxBetConvert.Level(0.52, 500.0), SxBetConvert.Level(0.49, 500.0))))
    }

    @Test fun wholeNumberLinesAreSkipped() {
        val (markets, _) = SxBetConvert.parseMarkets(marketsBody.replace("49.5", "49.0"), json)
        val books = SxBetConvert.parseBest(best(Triple("0xA", 52, 49)), json).associateBy { it.marketHash }
        assertTrue(SxBetConvert.events(markets, books, Leagues.byNovigName("NFL")!!, 1L).isEmpty())
    }
}
