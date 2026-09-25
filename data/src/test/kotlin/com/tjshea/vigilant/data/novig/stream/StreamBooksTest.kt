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
}
