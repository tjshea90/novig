package com.tjshea.vigilant.data.novig.stream

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The private `orders` stream's events (docs.novig.com/api/streaming/private; NOVIG_API.md §25), as JSON in and what the placer asks out. */
class OrderEventsTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    private fun live(e: OrderEvents = OrderEvents { 1_000L }, seq: Int = 5, open: String = "[]") = e.also { it.apply(obj("""{"seq":$seq,"open":$open}""")) }

    @Test
    fun `an IOC that fills ends as FILLED with its fills, the moment the last one arrives`() {
        val e = live()
        assertTrue(e.live)
        assertTrue(e.apply(obj("""{"seq":6,"deltas":[{"kind":"fill","orderId":"o1","clientId":"c1","outcomeId":"A","price":"0.4600","qty":100,"remaining":300},{"kind":"fill","orderId":"o1","outcomeId":"A","price":"0.4650","qty":300,"remaining":0}]}""")))
        val s = e.state("o1")!!
        assertEquals(OrderEvents.End.FILLED, s.end)
        assertEquals(400L, s.filledQty)
        assertEquals(listOf(0.46, 0.465), s.fills.map { it.price })
        assertEquals("c1", s.clientId)
        assertEquals(0L, s.remaining)
    }

    @Test
    fun `an unfilled IOC arrives as a reject, a partly filled one as fills then a cancel, and neither is called filled`() {
        val e = live()
        e.apply(obj("""{"seq":6,"deltas":[{"kind":"reject","orderId":"miss"}]}"""))
        assertEquals(OrderEvents.End.REJECTED, e.state("miss")!!.end)
        assertEquals(0L, e.state("miss")!!.filledQty)
        e.apply(obj("""{"seq":7,"deltas":[{"kind":"fill","orderId":"part","outcomeId":"A","price":"0.46","qty":40,"remaining":70},{"kind":"cancel","orderId":"part","reason":"MARKET_CLOSED"}]}"""))
        val p = e.state("part")!!
        assertEquals(OrderEvents.End.CANCELED, p.end)
        assertEquals(40L, p.filledQty)
        assertEquals("MARKET_CLOSED", p.reason)
        assertEquals(70L, p.remaining)
    }

    @Test
    fun `a resting order opens, partly fills and stays open until it ends`() {
        val e = live()
        e.apply(obj("""{"seq":6,"deltas":[{"kind":"open","orderId":"b1","clientId":"cb","marketId":"m","outcomeId":"A","price":"0.45","qty":110,"tif":"GTC"}]}"""))
        assertNull(e.state("b1")!!.end)
        assertEquals(listOf("b1"), e.resting().map { it.orderId })
        e.apply(obj("""{"seq":7,"deltas":[{"kind":"fill","orderId":"b1","outcomeId":"A","price":"0.45","qty":40,"remaining":70}]}"""))
        assertNull(e.state("b1")!!.end)
        assertEquals(70L, e.state("b1")!!.remaining)
        e.apply(obj("""{"seq":8,"deltas":[{"kind":"cancel","orderId":"b1"}]}"""))
        assertEquals(OrderEvents.End.CANCELED, e.state("b1")!!.end)
        assertTrue(e.resting().isEmpty())
    }

    @Test
    fun `a snapshot's open orders are the ones resting`() {
        val e = live(open = """[{"orderId":"r1","clientId":"c","marketId":"m","outcomeId":"A","price":"0.5","qty":10,"tif":"GTC"}]""")
        assertEquals(10L, e.state("r1")!!.restingQty)
        assertEquals(listOf("r1"), e.resting().map { it.orderId })
    }

    @Test
    fun `a gap in the seq stops the stream from being taken at its word, lets the waiters go, and a new snapshot starts it again`() = runBlocking {
        val e = live()
        val wait = async { e.awaitEnd("o1", 5_000) }
        delay(50)
        assertFalse(e.apply(obj("""{"seq":9,"deltas":[{"kind":"reject","orderId":"o1"}]}""")))
        assertFalse(e.live)
        assertNull("the waiter is let go with no answer: it asks REST", wait.await())
        assertEquals(1, e.gaps)
        assertNull("not live: nothing is waited for", e.awaitEnd("o2", 5_000))
        e.apply(obj("""{"seq":9,"open":[]}"""))
        assertTrue(e.live)
    }

    @Test
    fun `a heartbeat ahead of the last seq is a lost message`() {
        val e = live(seq = 5)
        assertTrue(e.heartbeat(5))
        assertTrue(e.live)
        assertFalse(e.heartbeat(7))
        assertFalse(e.live)
    }

    @Test
    fun `awaitEnd answers at once for an order that ended, waits for one that ends, and times out for one that never does`() = runBlocking {
        val e = live()
        e.apply(obj("""{"seq":6,"deltas":[{"kind":"reject","orderId":"early"}]}"""))
        assertNotNull(e.awaitEnd("early", 10_000))
        val late = async { e.awaitEnd("late", 5_000) }
        delay(50)
        e.apply(obj("""{"seq":7,"deltas":[{"kind":"fill","orderId":"late","outcomeId":"A","price":"0.5","qty":3,"remaining":0}]}"""))
        assertEquals(OrderEvents.End.FILLED, late.await()!!.end)
        assertNull(e.awaitEnd("never", 80))
    }

    @Test
    fun `a delta before any snapshot says nothing, and resetting forgets that the stream is live`() {
        val e = OrderEvents { 1L }
        e.apply(obj("""{"seq":3,"deltas":[{"kind":"reject","orderId":"x"}]}"""))
        assertNull(e.state("x"))
        live(e)
        assertTrue(e.live)
        e.reset()
        assertFalse(e.live)
    }
}
