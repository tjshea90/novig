package com.tjshea.vigilant.data.novig

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovigApiClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a well-formed open-markets response into domain events`() {
        val raw = """
            {
              "events": [
                {
                  "eventId": "evt-1",
                  "homeTeam": "Alabama",
                  "awayTeam": "South Carolina",
                  "startTime": "2026-09-26T23:00:00-04:00",
                  "isLive": false,
                  "markets": [
                    {
                      "marketId": "mkt-1",
                      "marketType": "MONEY",
                      "description": "Moneyline - Full Match",
                      "outcomes": [
                        {"outcomeId": "sc", "label": "South Carolina", "price": 0.20},
                        {"outcomeId": "bama", "label": "Alabama", "price": 0.85}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val events = NovigApiClient.parseOpenMarkets(raw, json)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("Alabama", event.homeTeam)
        assertEquals("South Carolina", event.awayTeam)
        assertEquals(1, event.markets.size)
        assertEquals(2, event.markets[0].outcomes.size)
        assertEquals(0.20, event.markets[0].outcomes[0].price, 1e-9)
    }

    @Test
    fun `tolerates an empty events list rather than throwing`() {
        val events = NovigApiClient.parseOpenMarkets("""{"events": []}""", json)
        assertTrue(events.isEmpty())
    }

    @Test(expected = Exception::class)
    fun `a market with the wrong outcome count fails loudly instead of being silently accepted`() {
        val raw = """
            {
              "events": [
                {
                  "eventId": "evt-1",
                  "homeTeam": "A",
                  "awayTeam": "B",
                  "startTime": "2026-09-26T23:00:00-04:00",
                  "markets": [
                    {
                      "marketId": "mkt-1",
                      "marketType": "MONEY",
                      "description": "Moneyline - Full Match",
                      "outcomes": [
                        {"outcomeId": "a", "label": "A", "price": 0.5}
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        NovigApiClient.parseOpenMarkets(raw, json)
    }
}
