package com.tjshea.vigilant.data.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedWatchTest {
    private val dead = FeedLine("parlay")
    private val live = FeedLine("sgo", fetched = 8)

    @Test fun `a feed silent three scans in a row is named, earlier it is not`() {
        val w = FeedWatch()
        assertTrue(w.observe(listOf(dead, live)).isEmpty())
        assertTrue(w.observe(listOf(dead, live)).isEmpty())
        assertEquals(listOf("parlay (3 scans in a row)"), w.observe(listOf(dead, live)))
    }

    @Test fun `an answer, standing by, a skip or a held back feed resets the streak`() {
        val w = FeedWatch()
        repeat(2) { w.observe(listOf(dead)) }
        assertTrue(w.observe(listOf(dead.copy(standingBy = 2))).isEmpty())
        repeat(2) { w.observe(listOf(dead)) }
        val w2 = FeedWatch()
        repeat(5) { w2.observe(listOf(dead.copy(heldBack = "credits paced"))) }
        assertTrue(w2.observe(listOf(dead.copy(heldBack = "credits paced"))).isEmpty())
        assertTrue(w2.observe(listOf(live.copy(id = "parlay"))).isEmpty())
    }

    @Test fun `a feed that leaves the list is forgotten`() {
        val w = FeedWatch()
        repeat(3) { w.observe(listOf(dead)) }
        assertTrue(w.observe(listOf(live)).isEmpty())
        assertTrue(w.observe(listOf(dead)).isEmpty())
    }
}
