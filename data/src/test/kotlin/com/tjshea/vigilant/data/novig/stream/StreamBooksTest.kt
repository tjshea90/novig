package com.tjshea.vigilant.data.novig.stream

import com.tjshea.vigilant.data.novig.BidLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Message shapes copied from docs.novig.com/api/streaming/book (NOVIG_API.md §6). */
class StreamBooksTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    private val snapshot = obj(
        """{"seq":48120,"orders":{
            "A":[{"order":"o1","price":"0.665","qty":110},{"order":"o2","price":"0.660","qty":250}],
            "B":[{"order":"o3","price":"0.340","qty":90}]}}""",
    )

    @Test
    fun `a snapshot then in-order deltas keep the book exact`() {
        val b = StreamBooks { 5 }
        b.applySnapshot("m", snapshot)
        assertTrue(b.applyDelta("m", obj("""{"seq":48121,"deltas":[{"kind":"add","order":"o4","outcome":"A","price":"0.670","qty":250},{"kind":"remove","order":"o1","reason":"fill"}]}""")))
        val book = b.book("m")!!
        assertEquals(48121L, book.seq)
        assertEquals(listOf(BidLevel(670, 250), BidLevel(660, 250)), book.bidsByOutcome["A"])
        assertEquals(listOf(BidLevel(340, 90)), book.bidsByOutcome["B"])
    }

    @Test
    fun `a partial fill arrives as remove then add and keeps the level`() {
        val b = StreamBooks()
        b.applySnapshot("m", snapshot)
        b.applyDelta("m", obj("""{"seq":48121,"deltas":[{"kind":"remove","order":"o1","reason":"fill"},{"kind":"add","order":"o1","outcome":"A","price":"0.665","qty":60}]}"""))
        assertEquals(BidLevel(665, 60), b.book("m")!!.bidsByOutcome["A"]!!.first())
    }

    @Test
    fun `a seq gap marks the book stale until a new snapshot`() {
        val b = StreamBooks()
        b.applySnapshot("m", snapshot)
        assertFalse(b.applyDelta("m", obj("""{"seq":48123,"deltas":[]}""")))
        assertNull(b.book("m"))
        assertEquals(setOf("m"), b.needsSnapshot)
        b.applySnapshot("m", obj("""{"seq":48125,"orders":{"A":[{"order":"x","price":"0.5","qty":1}]}}"""))
        assertTrue(b.applyDelta("m", obj("""{"seq":48125,"deltas":[]}"""))) // already covered
        assertTrue(b.applyDelta("m", obj("""{"seq":48126,"deltas":[]}""")))
        assertEquals(48126L, b.book("m")!!.seq)
        assertTrue(b.needsSnapshot.isEmpty())
    }

    @Test
    fun `a market opening under an event subscription starts at seq 1 with no snapshot`() {
        val b = StreamBooks()
        assertTrue(b.applyDelta("new", obj("""{"seq":1,"deltas":[{"kind":"add","order":"o","outcome":"A","price":"0.4","qty":5}]}""")))
        assertEquals(listOf(BidLevel(400, 5)), b.book("new")!!.bidsByOutcome["A"])
    }

    @Test
    fun `a delta for an unknown market mid-stream asks for a snapshot`() {
        val b = StreamBooks()
        assertFalse(b.applyDelta("x", obj("""{"seq":77,"deltas":[]}""")))
        assertEquals(setOf("x"), b.needsSnapshot)
    }

    // ---- the OpenAPI's wording (docs.novig.com openapi-v3.json, read 2026-10-12): orderId / outcomeId, an `update` delta, bbo and lifecycle ----------------------------------------------

    private val openApiSnapshot = obj(
        """{"seq":10,"orders":{
            "A":[{"orderId":"o1","price":"0.665","qty":110},{"orderId":"o2","price":"0.660","qty":250}],
            "B":[{"orderId":"o3","price":"0.340","qty":90}]}}""",
    )

    @Test
    fun `the OpenAPI field names are read like the prose ones`() {
        val b = StreamBooks()
        b.applySnapshot("m", openApiSnapshot)
        assertTrue(b.applyDelta("m", obj("""{"seq":11,"deltas":[{"kind":"add","orderId":"o4","outcomeId":"A","price":"0.670","qty":250},{"kind":"remove","orderId":"o1","reason":"fill"}]}""")))
        assertEquals(listOf(BidLevel(670, 250), BidLevel(660, 250)), b.book("m")!!.bidsByOutcome["A"])
        assertEquals(listOf(BidLevel(340, 90)), b.book("m")!!.bidsByOutcome["B"])
        assertFalse("a full book is not partial", b.book("m")!!.partial)
    }

    @Test
    fun `an update delta lowers a resting order and keeps its place, and a listener is told as the prose tells a partial fill`() {
        val b = StreamBooks()
        val told = ArrayList<BookChange>()
        b.listener = BookListener { _, _, ch -> told += ch }
        b.applySnapshot("m", openApiSnapshot)
        told.clear()
        assertTrue(b.applyDelta("m", obj("""{"seq":11,"deltas":[{"kind":"update","orderId":"o1","remaining":60}]}""")))
        assertEquals(listOf(BidLevel(665, 60), BidLevel(660, 250)), b.book("m")!!.bidsByOutcome["A"])
        assertEquals(
            listOf(BookChange(BookChange.Kind.REMOVE, "A", 665, 110, "fill"), BookChange(BookChange.Kind.ADD, "A", 665, 60)),
            told,
        )
        // remaining 0 is a fill of the rest; an update of an order not held (beyond the snapshot's depth) changes nothing
        assertTrue(b.applyDelta("m", obj("""{"seq":12,"deltas":[{"kind":"update","orderId":"o1","remaining":0},{"kind":"update","orderId":"unknown","remaining":5}]}""")))
        assertEquals(listOf(BidLevel(660, 250)), b.book("m")!!.bidsByOutcome["A"])
    }

    @Test
    fun `a bbo snapshot is a partial book with one level per outcome, and its deltas move the best bid`() {
        val b = StreamBooks()
        b.applyBboSnapshot("m", obj("""{"seq":7,"bbo":{"A":{"price":"0.665","qty":110},"B":{"price":"0.340","qty":90}}}"""))
        val book = b.book("m")!!
        assertTrue(book.partial)
        assertEquals(BookChannel.BBO, b.channelOf("m"))
        assertEquals(7L, book.seq)
        assertEquals(listOf(BidLevel(665, 110)), book.bidsByOutcome["A"])
        assertEquals(listOf(BidLevel(340, 90)), book.bidsByOutcome["B"])
        // the taker's price for A is 1 - B's best bid
        assertEquals(0.66, 1.0 - book.bestBid("B")!!.price, 1e-9)
        assertTrue(b.applyBboDelta("m", obj("""{"seq":8,"deltas":[{"outcomeId":"A","bbo":{"price":"0.670","qty":40}},{"outcomeId":"B","bbo":null}]}""")))
        val next = b.book("m")!!
        assertEquals(listOf(BidLevel(670, 40)), next.bidsByOutcome["A"])
        assertNull("the last resting bid left B", next.bidsByOutcome["B"])
        // an absent `bbo` key means the same as null, the prose's `outcome` name is read too, and a level with nothing resting is no bid
        assertTrue(b.applyBboDelta("m", obj("""{"seq":9,"deltas":[{"outcome":"B","bbo":{"price":"0.335","qty":25}},{"outcomeId":"A"}]}""")))
        assertEquals(listOf(BidLevel(335, 25)), b.book("m")!!.bidsByOutcome["B"])
        assertNull(b.book("m")!!.bidsByOutcome["A"])
        assertTrue(b.applyBboDelta("m", obj("""{"seq":10,"deltas":[{"outcomeId":"B","bbo":{"price":"0.335","qty":0}}]}""")))
        assertNull(b.book("m")!!.bidsByOutcome["B"])
    }

    @Test
    fun `a bbo gap marks the state stale until a new snapshot, like a book gap`() {
        val b = StreamBooks()
        b.applyBboSnapshot("m", obj("""{"seq":7,"bbo":{"A":{"price":"0.6","qty":5}}}"""))
        assertFalse(b.applyBboDelta("m", obj("""{"seq":9,"deltas":[]}""")))
        assertNull(b.book("m"))
        assertEquals(setOf("m"), b.needsSnapshot)
        b.applyBboSnapshot("m", obj("""{"seq":12,"bbo":{"A":{"price":"0.61","qty":5}}}"""))
        assertTrue(b.applyBboDelta("m", obj("""{"seq":12,"deltas":[]}""")))
        assertTrue(b.applyBboDelta("m", obj("""{"seq":13,"deltas":[{"outcomeId":"A","bbo":{"price":"0.62","qty":1}}]}""")))
        assertEquals(listOf(BidLevel(620, 1)), b.book("m")!!.bidsByOutcome["A"])
        assertTrue(b.needsSnapshot.isEmpty())
    }

    @Test
    fun `a market moved between book and bbo takes the new channel's snapshot and ignores the old one's late deltas`() {
        val b = StreamBooks()
        b.applyBboSnapshot("m", obj("""{"seq":7,"bbo":{"A":{"price":"0.6","qty":5}}}"""))
        b.applySnapshot("m", snapshot)
        assertEquals(BookChannel.BOOK, b.channelOf("m"))
        assertFalse(b.book("m")!!.partial)
        // a bbo delta that was already on the wire
        assertTrue(b.applyBboDelta("m", obj("""{"seq":8,"deltas":[{"outcomeId":"A","bbo":{"price":"0.99","qty":1}}]}""")))
        assertEquals(listOf(BidLevel(665, 110), BidLevel(660, 250)), b.book("m")!!.bidsByOutcome["A"])
        // and back
        b.applyBboSnapshot("m", obj("""{"seq":20,"bbo":{"A":{"price":"0.7","qty":2}}}"""))
        assertTrue(b.applyDelta("m", obj("""{"seq":48121,"deltas":[{"kind":"add","order":"z","outcome":"A","price":"0.9","qty":1}]}""")))
        assertEquals(listOf(BidLevel(700, 2)), b.book("m")!!.bidsByOutcome["A"])
    }

    @Test
    fun `the lifecycle snapshot and deltas, plain transitions or objects, give the status and whether the market is live`() {
        val b = StreamBooks { 9 }
        assertNull(b.lifecycle("m"))
        b.applyLifecycleSnapshot("m", obj("""{"seq":12,"status":"OPEN"}"""))
        assertTrue(b.lifecycle("m")!!.open)
        assertNull(b.lifecycle("m")!!.live)
        // the prose: deltas is a list of transition names
        assertTrue(b.applyLifecycleDelta("m", obj("""{"seq":13,"deltas":["GOLIVE"]}""")))
        assertEquals(true, b.lifecycle("m")!!.live)
        assertEquals("GOLIVE", b.lifecycle("m")!!.transition)
        assertTrue(b.lifecycle("m")!!.open)
        // the OpenAPI: {kind, status}
        assertTrue(b.applyLifecycleDelta("m", obj("""{"seq":14,"deltas":[{"kind":"UNLIVE","status":"OPEN"},{"kind":"CLOSE","status":"CLOSED"}]}""")))
        assertEquals(false, b.lifecycle("m")!!.live)
        assertTrue(b.lifecycle("m")!!.closed)
        // a plain CLOSE and GRADE imply their status
        assertTrue(b.applyLifecycleDelta("m", obj("""{"seq":15,"deltas":["GRADE"]}""")))
        assertEquals("SETTLED", b.lifecycle("m")!!.status)
        // already covered by a newer snapshot: ignored
        assertTrue(b.applyLifecycleDelta("m", obj("""{"seq":15,"deltas":["OPEN"]}""")))
        assertEquals("SETTLED", b.lifecycle("m")!!.status)
    }

    @Test
    fun `a lifecycle gap forgets the status and asks for a snapshot`() {
        val b = StreamBooks()
        b.applySnapshot("m", snapshot)
        b.applyLifecycleSnapshot("m", obj("""{"seq":3,"status":"OPEN"}"""))
        assertFalse(b.applyLifecycleDelta("m", obj("""{"seq":5,"deltas":["CLOSE"]}""")))
        assertNull(b.lifecycle("m")!!.status)
        assertEquals(setOf("m"), b.needsSnapshot)
        b.applyLifecycleSnapshot("m", obj("""{"seq":6,"status":"CLOSED"}"""))
        b.applySnapshot("m", snapshot)
        assertTrue(b.lifecycle("m")!!.closed)
        assertTrue(b.needsSnapshot.isEmpty())
    }

    @Test
    fun `forgetting and clearing drop the lifecycle too`() {
        val b = StreamBooks()
        b.applyLifecycleSnapshot("m", obj("""{"seq":1,"status":"OPEN"}"""))
        b.applyLifecycleSnapshot("n", obj("""{"seq":1,"status":"OPEN"}"""))
        b.forget(listOf("m"))
        assertNull(b.lifecycle("m"))
        assertTrue(b.lifecycle("n")!!.open)
        b.clear()
        assertNull(b.lifecycle("n"))
    }

    @Test
    fun `a book carries what the lifecycle channel last said, bbo or book`() {
        val b = StreamBooks { 9 }
        b.applyBboSnapshot("m", obj("""{"seq":20,"bbo":{"A":{"price":"0.7","qty":2}}}"""))
        assertNull(b.book("m")!!.lifecycle)
        b.applyLifecycleSnapshot("m", obj("""{"seq":12,"status":"OPEN"}"""))
        assertTrue(b.book("m")!!.lifecycle!!.open)
        assertTrue(b.applyLifecycleDelta("m", obj("""{"seq":13,"deltas":["GOLIVE"]}""")))
        assertEquals(true, b.book("m")!!.lifecycle!!.live)
        b.applySnapshot("m", obj("""{"seq":1,"orders":{"A":[{"order":"x","price":"0.5","qty":1}]}}"""))
        assertEquals("a full book carries it too", true, b.book("m")!!.lifecycle!!.live)
    }
}
