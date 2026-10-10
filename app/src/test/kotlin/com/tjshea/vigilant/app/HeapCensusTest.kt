package com.tjshea.vigilant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The heap census names who holds memory (Tj, 2026-10-10): the big owner comes first, a shared object is counted once, and the walk stops at its limits. */
class HeapCensusTest {

    private class Holder(val big: List<ByteArray>, val small: Map<String, String>, val shared: Any? = null)

    @Test
    fun `the owner holding the most is first, and sizes are in the right order of magnitude`() {
        val big = (0 until 20).map { ByteArray(100_000) }                       // about 2 MB
        val small = (0 until 50).associate { "k$it" to "v$it" }
        val r = HeapCensus.run(listOf("big" to big, "small" to small, "none" to null))
        assertEquals(listOf("big", "small"), r.rows.map { it.owner })
        assertTrue("${r.rows}", r.rows[0].bytes in 1_900_000L..2_300_000L)
        assertTrue(r.rows[1].bytes < 20_000L)
        assertFalse(r.truncated)
        assertTrue(r.line().contains("big 1 MB") || r.line().contains("big 2 MB"))
    }

    @Test
    fun `an object two owners share is counted once, under the first`() {
        val shared = ByteArray(500_000)
        val r = HeapCensus.run(listOf("a" to listOf(shared), "b" to listOf(shared)))
        val a = r.rows.first { it.owner == "a" }.bytes
        val b = r.rows.firstOrNull { it.owner == "b" }?.bytes ?: 0L
        assertTrue("a=$a b=$b", a > 450_000L && b < 1_000L)
    }

    @Test
    fun `fields of this app's own classes are walked, the framework's and a thread's are not`() {
        val h = Holder(listOf(ByteArray(300_000)), mapOf("x" to "y"), shared = Thread.currentThread())
        val r = HeapCensus.run(HeapCensus.rootsOf("h.", h))
        assertEquals("h.big", r.rows.first().owner)
        assertTrue(r.rows.first().bytes > 290_000L)
        assertTrue(r.totalBytes < 400_000L)   // the thread and its stacks were not walked
    }

    @Test
    fun `it stops at its object limit and says so`() {
        val many = (0 until 10_000).map { "s$it" }
        val r = HeapCensus.run(listOf("many" to many), maxObjects = 500)
        assertTrue(r.truncated)
        assertTrue(r.objects <= 500)
        assertTrue(r.line().contains("STOPPED"))
    }
}
