package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.ScanContext
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One API for overlapping data, the others asleep (Tj, 2026-10-11): the best feed first, the next best only for what is missing, a failure, or an extra. */
class ApiChainTest {
    private val now = 1_791_000_000_000L
    private val nfl = Leagues.ALL.first { it.novigName == "NFL" }
    private val game = NovigEvent("ev1", "FOOTBALL", "NFL", NovigEvent.STATUS_PREGAME, "Denver Broncos @ Los Angeles Chargers", now + 3_600_000L)
    private fun market(type: String, id: String = "m-$type") = NovigMarket(id, "ev1", type, "OPEN", "x", now + 3_600_000L, MarketFee.GAME, listOf(NovigOutcome("a$id", "A", "TBD"), NovigOutcome("b$id", "B", "TBD")), 0.0)
    private fun ctx(
        covered: Set<String>? = setOf("MONEYLINE:0", "SPREAD:0", "TOTAL:0"), answered: Boolean = true, books: Set<String> = setOf("draftkings", "fanduel"),
        markets: List<NovigMarket> = listOf(market("MONEY")), events: List<NovigEvent> = listOf(game),
    ) = ScanContext(
        novigEvents = events, novigMarkets = markets, now = now,
        covered = covered?.let { mapOf("ev1" to it) } ?: emptyMap(), firstAnswered = if (answered) setOf("NFL") else emptySet(), coveredBooks = mapOf("NFL" to books),
    )

    @Test
    fun `a feed waits for the better feeds that are on, the best waits for none, and exchanges are not on a chain`() {
        val on = listOf("kalshi", "sgo", "oddspapi", "parlay", "unabated", "sgo-props", "oddspapi-props")
        assertEquals(emptyList<String>(), ApiChain.better("sgo", on))
        assertEquals(listOf("sgo"), ApiChain.better("oddspapi", on))
        assertEquals(listOf("sgo", "oddspapi", "parlay"), ApiChain.better("unabated", on))
        assertEquals(listOf("sgo-props"), ApiChain.better("oddspapi-props", on))
        assertEquals("a feed that is off is not waited for", listOf("sgo"), ApiChain.better("parlay", listOf("sgo", "parlay")))
        assertEquals(emptyList<String>(), ApiChain.better("kalshi", on))
        assertNull(ApiChain.kindOf("polymarket"))
    }

    @Test
    fun `when the better feeds gave the league's lines the next best sleeps`() {
        assertFalse(ApiChain.needed("oddspapi", nfl, ScanSettings(), ctx()))
        assertFalse(ApiChain.needed("unabated", nfl, ScanSettings(), ctx()))
    }

    @Test
    fun `when no better feed answered the league (failed, out of credits, held back) the next best takes over`() {
        assertTrue(ApiChain.needed("oddspapi", nfl, ScanSettings(), ctx(answered = false)))
    }

    @Test
    fun `a game the better feeds left without lines is filled by the next best`() {
        assertTrue(ApiChain.needed("oddspapi", nfl, ScanSettings(), ctx(covered = null)))
        assertTrue(ApiChain.needed("oddspapi", nfl, ScanSettings(), ctx(covered = setOf("PROP:RECEPTIONS"))))
        // Nothing on Novig to price: nothing to ask for.
        assertFalse(ApiChain.needed("oddspapi", nfl, ScanSettings(), ctx(covered = null, events = emptyList())))
    }

    @Test
    fun `player props are asked of the next best only for a game with props on Novig and none priced`() {
        val props = listOf(market("MONEY"), market(PropStats.NOVIG_TYPES.first(), "prop"))
        assertTrue(ApiChain.needed("oddspapi-props", nfl, ScanSettings(), ctx(covered = setOf("MONEYLINE:0"), markets = props)))
        assertFalse(ApiChain.needed("oddspapi-props", nfl, ScanSettings(), ctx(covered = setOf("PROP:RECEPTIONS"), markets = props)))
        assertFalse("no props listed on Novig: nothing to price", ApiChain.needed("oddspapi-props", nfl, ScanSettings(), ctx(covered = setOf("MONEYLINE:0"))))
    }

    @Test
    fun `a book only the lower feed carries and Tj has on is an extra that is asked for, once the better feeds sent it it is not`() {
        val s = ScanSettings(oddsPapi = true, opExtraBooks = true)
        assertTrue(ApiChain.needed("oddspapi", nfl, s, ctx(books = setOf("draftkings"))))
        assertFalse(ApiChain.needed("oddspapi", nfl, s, ctx(books = setOf("draftkings", "circa", "superbook", "bet365"))))
        assertFalse("switch off: no extra", ApiChain.needed("oddspapi", nfl, s.copy(opExtraBooks = false), ctx(books = setOf("draftkings"))))
    }
}
