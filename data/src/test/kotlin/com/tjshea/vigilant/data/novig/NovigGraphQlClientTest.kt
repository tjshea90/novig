package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.keys.KeyAttemptResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovigGraphQlClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- direct (no proxy) mode construction -------------------------------------------------

    @Test
    fun `constructs cleanly with zero proxies configured (direct mode)`() {
        // Regression test: KeyRotator itself throws on an empty key list, so the client must not
        // hand an empty proxy list straight to KeyRotator — direct mode has to be a real branch,
        // not just "pass an empty list through and hope."
        NovigGraphQlClient(leagues = listOf("NFL"), proxies = emptyList(), json = json)
    }

    @Test
    fun `constructs cleanly with one or more proxies configured`() {
        NovigGraphQlClient(leagues = listOf("NFL"), proxies = listOf("user:pass@proxy.example.com:8080"), json = json)
    }

    // --- proxy string parsing ---------------------------------------------------------------

    @Test
    fun `parses a valid proxy string`() {
        val parsed = NovigGraphQlClient.parseProxy("user1:secretpass@proxy.example.com:8080")
        assertEquals("proxy.example.com", (parsed.proxy.address() as java.net.InetSocketAddress).hostString)
        assertEquals(8080, (parsed.proxy.address() as java.net.InetSocketAddress).port)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a proxy string with no at-sign`() {
        NovigGraphQlClient.parseProxy("user1:secretpass-proxy.example.com:8080")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a proxy string with no colon before the at-sign`() {
        NovigGraphQlClient.parseProxy("user1secretpass@proxy.example.com:8080")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a proxy string with a non-numeric port`() {
        NovigGraphQlClient.parseProxy("user1:secretpass@proxy.example.com:notaport")
    }

    // --- request bodies -----------------------------------------------------------------------

    @Test
    fun `league request body carries the league variable`() {
        val body = NovigGraphQlClient.leagueRequestBody("NFL", json)
        assertTrue(body.contains("\"league\":\"NFL\""))
        assertTrue(body.contains("OPEN_PREGAME"))
    }

    @Test
    fun `market request body carries the event id variable`() {
        val body = NovigGraphQlClient.marketRequestBody("11111111-1111-1111-1111-111111111111", json)
        assertTrue(body.contains("\"eventId\":\"11111111-1111-1111-1111-111111111111\""))
    }

    // --- league response parsing ---------------------------------------------------------------

    @Test
    fun `parses league response into event ids`() {
        val raw = """
            {"data": {"event": [
                {"game": {"scheduled_start": "2026-09-21T17:00:00Z"}, "id": "11111111-1111-1111-1111-111111111111", "description": "Buffalo Bills @ Kansas City Chiefs"},
                {"game": {"scheduled_start": "2026-09-21T20:25:00Z"}, "id": "22222222-2222-2222-2222-222222222222", "description": "Dallas Cowboys @ Philadelphia Eagles"}
            ]}}
        """.trimIndent()

        val result = NovigGraphQlClient.parseLeagueResponse(raw, json)
        check(result is KeyAttemptResult.Success)
        assertEquals(listOf("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"), result.value)
    }

    @Test
    fun `a GraphQL timeout error is rate-limited, not invalid`() {
        val raw = """{"errors": [{"message": "timeout", "extensions": {"code": "time-limit-exceeded"}}]}"""
        val result = NovigGraphQlClient.parseLeagueResponse(raw, json)
        assertTrue(result is KeyAttemptResult.RateLimited)
    }

    @Test
    fun `a non-timeout GraphQL error is treated as empty, not this proxy's fault`() {
        val raw = """{"errors": [{"message": "field not found"}]}"""
        val result = NovigGraphQlClient.parseLeagueResponse(raw, json)
        check(result is KeyAttemptResult.Success)
        assertEquals(emptyList<String>(), result.value)
    }

    // --- market response parsing / outcome pricing ----------------------------------------------

    private val moneylineEventJson = """
        {"data": {"event": [{
            "description": "Buffalo Bills @ Kansas City Chiefs",
            "id": "11111111-1111-1111-1111-111111111111",
            "game": {"scheduled_start": "2026-09-21T17:00:00Z"},
            "markets": [
                {
                    "description": "Moneyline - Full Match",
                    "type": "moneyline",
                    "strike": null,
                    "player": null,
                    "outcomes": [
                        {"id": "out-buf", "description": "Buffalo Bills", "last": 0.42, "available": 500.0,
                         "orders": [{"status": "OPEN", "qty": 100, "price": 0.40, "originalQty": 100, "created_at": "2026-09-21T10:00:00Z"}]},
                        {"id": "out-kc", "description": "Kansas City Chiefs", "last": null, "available": 500.0,
                         "orders": [
                            {"status": "OPEN", "qty": 200, "price": 0.61, "originalQty": 200, "created_at": "2026-09-21T10:00:00Z"},
                            {"status": "OPEN", "qty": 50, "price": 0.58, "originalQty": 50, "created_at": "2026-09-21T10:01:00Z"}
                         ]}
                    ]
                },
                {
                    "description": "Total - Full Match",
                    "type": "total",
                    "strike": 47.5,
                    "player": null,
                    "outcomes": [
                        {"id": "out-over", "description": "Over 47.5", "last": 0.51, "available": 300.0, "orders": []},
                        {"id": "out-under", "description": "Under 47.5", "last": 0.49, "available": 300.0, "orders": []}
                    ]
                },
                {
                    "description": "Passing Yards - Josh Allen",
                    "type": "player_prop",
                    "strike": 255.5,
                    "player": {"full_name": "Josh Allen"},
                    "outcomes": [
                        {"id": "out-over-py", "description": "Over 255.5", "last": 0.5, "available": 100.0, "orders": []},
                        {"id": "out-under-py", "description": "Under 255.5", "last": 0.5, "available": 100.0, "orders": []}
                    ]
                }
            ]
        }]}}
    """.trimIndent()

    @Test
    fun `parses a full market response into a NovigEvent, dropping the player prop market`() {
        val result = NovigGraphQlClient.parseMarketResponse(moneylineEventJson, json)
        check(result is KeyAttemptResult.Success)
        val event = result.value
        checkNotNull(event)

        assertEquals(2, event.markets.size)
        assertEquals(setOf("Buffalo Bills", "Kansas City Chiefs"), setOf(event.homeTeam, event.awayTeam))
    }

    @Test
    fun `prefers last trade price over the order book when both are present`() {
        val result = NovigGraphQlClient.parseMarketResponse(moneylineEventJson, json)
        check(result is KeyAttemptResult.Success)
        val moneyline = result.value!!.markets.first { it.marketType == "MONEY" }
        val bills = moneyline.outcomes.first { it.label == "Buffalo Bills" }
        assertEquals(0.42, bills.price, 1e-9) // last, not the 0.40 order price
    }

    @Test
    fun `falls back to the best open order price when last is null`() {
        val result = NovigGraphQlClient.parseMarketResponse(moneylineEventJson, json)
        check(result is KeyAttemptResult.Success)
        val moneyline = result.value!!.markets.first { it.marketType == "MONEY" }
        val chiefs = moneyline.outcomes.first { it.label == "Kansas City Chiefs" }
        assertEquals(0.61, chiefs.price, 1e-9) // highest of 0.61/0.58, not just the first order
    }

    @Test
    fun `classifies the raw type string when it is human-readable`() {
        val result = NovigGraphQlClient.parseMarketResponse(moneylineEventJson, json)
        check(result is KeyAttemptResult.Success)
        assertEquals("TOTAL", result.value!!.markets.first { it.description == "Total - Full Match" }.marketType)
    }

    @Test
    fun `falls back to description-parsed team names when no moneyline market exists`() {
        val raw = """
            {"data": {"event": [{
                "description": "Dallas Cowboys @ Philadelphia Eagles",
                "id": "22222222-2222-2222-2222-222222222222",
                "game": {"scheduled_start": "2026-09-21T20:25:00Z"},
                "markets": [{
                    "description": "Spread - Full Match",
                    "type": "unknown_code_7",
                    "strike": -3.5,
                    "player": null,
                    "outcomes": [
                        {"id": "out-dal", "description": "Dallas Cowboys -3.5", "last": 0.52, "available": 100.0, "orders": []},
                        {"id": "out-phi", "description": "Philadelphia Eagles +3.5", "last": 0.48, "available": 100.0, "orders": []}
                    ]
                }]
            }]}}
        """.trimIndent()

        val result = NovigGraphQlClient.parseMarketResponse(raw, json)
        check(result is KeyAttemptResult.Success)
        val event = result.value
        checkNotNull(event)
        assertEquals(setOf("Dallas Cowboys", "Philadelphia Eagles"), setOf(event.homeTeam, event.awayTeam))
        assertEquals("SPREAD", event.markets.single().marketType)
    }

    @Test
    fun `an event with no usable markets is dropped entirely`() {
        val raw = """
            {"data": {"event": [{
                "description": "Some Team @ Other Team",
                "id": "33333333-3333-3333-3333-333333333333",
                "game": {"scheduled_start": "2026-09-21T20:25:00Z"},
                "markets": [{
                    "description": "Passing Yards - Some Player",
                    "type": "player_prop",
                    "strike": 200.5,
                    "player": {"full_name": "Some Player"},
                    "outcomes": [
                        {"id": "out-1", "description": "Over 200.5", "last": 0.5, "available": 1.0, "orders": []},
                        {"id": "out-2", "description": "Under 200.5", "last": 0.5, "available": 1.0, "orders": []}
                    ]
                }]
            }]}}
        """.trimIndent()

        val result = NovigGraphQlClient.parseMarketResponse(raw, json)
        check(result is KeyAttemptResult.Success)
        assertNull(result.value)
    }

    @Test
    fun `an outcome with no last price and no open orders is dropped`() {
        val outcome = NovigGraphQlClient.toNovigOutcome(
            GqlOutcomeDto(id = "x", description = "No Liquidity", last = null, available = null, orders = emptyList()),
        )
        assertNull(outcome)
    }

    @Test
    fun `a settled contract price at the boundary is dropped as garbage`() {
        assertNull(NovigGraphQlClient.toNovigOutcome(GqlOutcomeDto(id = "x", description = "Settled", last = 1.0)))
        assertNull(NovigGraphQlClient.toNovigOutcome(GqlOutcomeDto(id = "x", description = "Settled", last = 0.0)))
    }

    // --- matchup description parsing ------------------------------------------------------------

    @Test
    fun `parses an at-sign separated matchup`() {
        assertEquals("Buffalo Bills" to "Kansas City Chiefs", NovigGraphQlClient.parseMatchupDescription("Buffalo Bills @ Kansas City Chiefs"))
    }

    @Test
    fun `parses a vs separated matchup`() {
        assertEquals("Lakers" to "Celtics", NovigGraphQlClient.parseMatchupDescription("Lakers vs Celtics"))
    }

    @Test
    fun `parses a vs dot separated matchup`() {
        assertEquals("Lakers" to "Celtics", NovigGraphQlClient.parseMatchupDescription("Lakers vs. Celtics"))
    }

    @Test
    fun `returns null for an unparseable description`() {
        assertNull(NovigGraphQlClient.parseMatchupDescription("NFL Week 3 Special"))
    }

    @Test
    fun `returns null for a blank description`() {
        assertNull(NovigGraphQlClient.parseMatchupDescription(""))
    }
}
