package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tap on a CNO bet opens it in Novig every time it can (Tj, 2026-09-27: "sometimes they pull up
 * the novig bet slip, but sometimes they don't"; "make it so vigilant can open the bet in novig
 * even if it can't reach cno servers").
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TapLinkTest {

    private val cnoLink = "novigapp://events/o1/cno"
    private val bet = NovigBetFinder.Found.Bet("o2", "E1")
    private val game = NovigBetFinder.Found.Game("E1")

    @Test
    fun `a link read ahead of time opens at once, with no request at all`() = runTest {
        var asked = 0
        val link = TapLink.resolve("novigapp://events/o0/cno", cnoFailing = false, { asked++; cnoLink }, { asked++; bet })
        assertEquals(TapLink.Link("novigapp://events/o0/cno", exact = true), link)
        assertEquals(0, asked)
    }

    @Test
    fun `CNO not answering costs the tap nothing - Novig's catalog is asked at the same time`() = runTest {
        val start = testScheduler.currentTime
        assertEquals(TapLink.Link("novigapp://events/o2", true), TapLink.resolve(null, false, { awaitCancellation() }, { delay(300); bet }))
        assertEquals(300L, testScheduler.currentTime - start)
        // CNO failing outright: Novig's link all the same.
        assertEquals(TapLink.Link("novigapp://events/o2", true), TapLink.resolve(null, false, { throw java.io.IOException("reset") }, { bet }))
    }

    @Test
    fun `whichever exact link comes first wins, and the game alone waits for CNO's exact one`() = runTest {
        assertEquals(TapLink.Link(cnoLink, true), TapLink.resolve(null, false, { delay(100); cnoLink }, { delay(900); bet }))
        assertEquals(TapLink.Link("novigapp://events/o2", true), TapLink.resolve(null, false, { delay(900); cnoLink }, { delay(100); bet }))
        val start = testScheduler.currentTime
        assertEquals(TapLink.Link(cnoLink, true), TapLink.resolve(null, false, { delay(2_000); cnoLink }, { game }))
        assertEquals(2_000L, testScheduler.currentTime - start)
    }

    @Test
    fun `CNO failing or asking for a pause - Novig alone first, CNO only when Novig found no more than the game`() = runTest {
        var cnoAsked = 0
        assertEquals(TapLink.Link("novigapp://events/o2", true), TapLink.resolve(null, true, { cnoAsked++; cnoLink }, { bet }))
        assertEquals(0, cnoAsked)
        assertEquals(TapLink.Link(cnoLink, true), TapLink.resolve(null, true, { cnoAsked++; cnoLink }, { game }))
        assertEquals(1, cnoAsked)
        // Paused (no CNO at all): the game, said to be only the game.
        assertEquals(TapLink.Link("novigapp://event-markets/E1", false), TapLink.resolve(null, false, null, { game }))
    }

    @Test
    fun `nothing answers - null (the app opens Novig and says so), within the longer limit, not both`() = runTest {
        val start = testScheduler.currentTime
        assertNull(TapLink.resolve(null, false, { awaitCancellation() }, { awaitCancellation() }))
        assertEquals(maxOf(TapLink.CNO_MS, TapLink.NOVIG_MS), testScheduler.currentTime - start)
        assertNull(TapLink.resolve(null, false, { null }, { null }))
    }
}
