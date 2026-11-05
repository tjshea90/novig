package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.ncaab.NcaabBoard
import com.tjshea.vigilant.data.ncaab.NcaabBoardFeed
import com.tjshea.vigilant.data.ncaab.NcaabView
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.reference.LineKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * Novig's REAL college basketball catalog through the engine's board code (NC1). Skipped unless VIGILANT_LIVE=1:
 * `VIGILANT_LIVE=1 bash tools/test.sh :data:test --tests '*LiveNcaabBoardTest'`. Public routes only (no key), so the catalog carries no prices and a few real books are read for the price checks.
 */
class LiveNcaabBoardTest {
    private fun client(): NovigPublicClient {
        // The agent container reaches the Internet through HTTPS_PROXY; a plain JVM client does not use that variable.
        val proxy = System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let { URI(it) }?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) }
        val http = OkHttpClient.Builder().apply { proxy?.let { proxy(it) } }.build()
        return NovigPublicClient(http, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun `the real NCAAB catalog becomes a board with a spread, a total and a moneyline per game and priced sides from real books`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val novig = client()
        val now = System.currentTimeMillis()
        val before = now + NcaabBoard.WINDOW_HOURS * 3_600_000L
        val events: List<NovigEvent> = novig.events(listOf("NCAAB"), listOf("OPEN_PREGAME"), before).filter { it.league == "NCAAB" }
        println("LIVE NCAAB: ${events.size} pregame events in the next ${NcaabBoard.WINDOW_HOURS} h")
        assumeTrue("Novig lists no NCAAB games right now", events.isNotEmpty())
        val ids = events.map { it.eventId }.toSet()
        val markets = novig.markets(listOf("NCAAB"), NcaabBoard.TYPES, listOf("OPEN_PREGAME"), before).filter { it.eventId in ids }
        println("LIVE NCAAB: ${markets.size} game-line markets: " + markets.groupingBy { it.marketType }.eachCount())

        // The structure alone (no prices): every game has sides the planner could name.
        val bare = NcaabBoard.build(events, markets, emptyMap(), emptyMap(), now)
        val bareGames = bare.groupBy { it.event.eventId }
        println("LIVE NCAAB: board without prices: ${bare.size} rows from ${bareGames.size} games")
        assertTrue("a board of $markets markets came out empty", bare.isNotEmpty())
        var spreads = 0; var totals = 0; var moneys = 0; var misses = 0
        for ((_, rows) in bareGames) {
            val kinds = rows.groupBy { it.kind }
            spreads += kinds[LineKind.SPREAD]?.size ?: 0; totals += kinds[LineKind.TOTAL]?.size ?: 0; moneys += kinds[LineKind.MONEYLINE]?.size ?: 0
            // two sides per market, each named for a team (spread/moneyline) or Over/Under
            rows.groupBy { it.market.marketId }.values.forEach { sides -> if (sides.size != 2) misses++ }
        }
        println("LIVE NCAAB: rows spread=$spreads total=$totals money=$moneys, markets with other than two sides: $misses")
        assertEquals("every market must give both sides", 0, misses)
        assertTrue(spreads > 0 && totals > 0)

        // A window around the main number: no row further than the reach from the middle of its game's numbers.
        val views = NcaabView.build(null, null, NcaabBoardFeed(now, bare, bareGames.size), now)
        assertEquals(bareGames.size, views.size)
        val withBoth = views.count { it.spreads.isNotEmpty() && it.totals.isNotEmpty() }
        println("LIVE NCAAB: games with a spread and a total: $withBoth of ${views.size}; spread numbers per game " + views.map { it.spreads.size }.sorted())
        assertTrue(withBoth > 0)
        views.filter { it.spreads.size > 1 }.forEach { v ->
            assertEquals("spreads come sorted by the home handicap", v.spreads.map { it.handicap }.sorted(), v.spreads.map { it.handicap })
            v.spreads.forEach { s ->
                // Novig's strike is the HOME handicap: the home side's name must carry it, the away side the opposite
                assertTrue("${s.home.selection} / ${s.away.selection}", s.home.selection.contains(com.tjshea.vigilant.data.ncaab.NcaabText.number(kotlin.math.abs(s.handicap))) && s.away.selection.contains(com.tjshea.vigilant.data.ncaab.NcaabText.number(kotlin.math.abs(s.handicap))))
            }
        }

        // Real books for the soonest game's lines: prices, a main number picked by price, costs near even.
        val first = events.minBy { it.startsTs }
        val mine = markets.filter { it.eventId == first.eventId }
        val batch = novig.books(mine.map { it.marketId }.take(14))
        val priced = NcaabBoard.build(listOf(first), mine, batch.books, emptyMap(), now)
        val v = NcaabView.build(null, null, NcaabBoardFeed(now, priced, 1), now).single()
        println("LIVE NCAAB: ${first.description}: books read ${batch.books.size}, main spread ${v.mainSpread?.handicap} costs ${v.mainSpread?.homeCost}/${v.mainSpread?.awayCost}, main total ${v.mainTotal?.line} costs ${v.mainTotal?.overCost}/${v.mainTotal?.underCost}, ML ${v.moneyline?.first?.ladder?.firstOrNull()?.price}")
        assertTrue("some side has a price from a real book", priced.any { it.ladder.isNotEmpty() })
        v.mainSpread?.let { s -> if (s.homeCost != null && s.awayCost != null) assertTrue("the main spread costs ${s.homeCost}+${s.awayCost}", s.homeCost!! + s.awayCost!! in 0.95..1.15) }
        assertNotNull(v.event)
    }
}
