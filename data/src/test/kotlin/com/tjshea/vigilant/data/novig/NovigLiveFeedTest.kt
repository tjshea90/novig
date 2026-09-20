package com.tjshea.vigilant.data.novig

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovigLiveFeedTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses an order book PLACE message`() {
        val raw = """{"type": "PLACE", "orderBook": {"marketId": "mkt-1", "outcomeId": "sc", "price": 0.21}}"""
        val event = NovigWebSocketLiveFeed.parseTapeMessage(raw, json)

        assertTrue(event is NovigTapeEvent.OrderBookUpdate)
        event as NovigTapeEvent.OrderBookUpdate
        assertEquals("mkt-1", event.marketId)
        assertEquals("sc", event.outcomeId)
        assertEquals(0.21, event.price, 1e-9)
        assertEquals("PLACE", event.eventType)
    }

    @Test
    fun `parses a market lifecycle message`() {
        val raw = """{"type": "EVENT_GOLIVE", "lifecycle": {"marketId": "mkt-1"}}"""
        val event = NovigWebSocketLiveFeed.parseTapeMessage(raw, json)

        assertTrue(event is NovigTapeEvent.MarketLifecycle)
        event as NovigTapeEvent.MarketLifecycle
        assertEquals("mkt-1", event.marketId)
        assertEquals("EVENT_GOLIVE", event.state)
    }

    @Test
    fun `an unrecognized message surfaces as Unknown instead of throwing`() {
        val raw = """{"type": "SOMETHING_NEW_NOVIG_ADDS_LATER", "payload": {}}"""
        val event = NovigWebSocketLiveFeed.parseTapeMessage(raw, json)
        assertTrue(event is NovigTapeEvent.Unknown)
    }

    @Test
    fun `malformed JSON surfaces as Unknown instead of crashing the socket listener`() {
        val event = NovigWebSocketLiveFeed.parseTapeMessage("not json at all", json)
        assertTrue(event is NovigTapeEvent.Unknown)
    }
}
