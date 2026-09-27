package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tap on a CNO bet opens it in Novig every time it can (Tj, 2026-09-27: "sometimes they pull up
 * the novig bet slip, but sometimes they don't … it says cno could not be reached").
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
    fun `CNO answers - its link, CNO hangs - Novig's own catalog after a few seconds, not forever`() = runTest {
        assertEquals(TapLink.Link(cnoLink, true), TapLink.resolve(null, false, { cnoLink }, { error("not asked") }))
        val start = testScheduler.currentTime
        val link = TapLink.resolve(null, false, { awaitCancellation() }, { bet })
        assertEquals(TapLink.Link("novigapp://events/o2", true), link)
        assertEquals(TapLink.CNO_MS, testScheduler.currentTime - start)
        // CNO failing outright (an exception, not a hang): straight on to Novig.
        assertEquals(TapLink.Link("novigapp://events/o2", true), TapLink.resolve(null, false, { throw java.io.IOException("reset") }, { bet }))
    }

    @Test
    fun `CNO's list failing - Novig first, CNO only when Novig found no more than the game`() = runTest {
        var cnoAsked = 0
        assertEquals(TapLink.Link("novigapp://events/o2", true), TapLink.resolve(null, true, { cnoAsked++; cnoLink }, { bet }))
        assertEquals(0, cnoAsked)
        assertEquals(TapLink.Link(cnoLink, true), TapLink.resolve(null, true, { cnoAsked++; cnoLink }, { game }))
        assertEquals(1, cnoAsked)
        // Neither has the bet: the game, said to be only the game.
        assertEquals(TapLink.Link("novigapp://event-markets/E1", false), TapLink.resolve(null, true, { null }, { game }))
    }

    @Test
    fun `nothing answers - null (the app opens Novig and says so), within both limits`() = runTest {
        val start = testScheduler.currentTime
        assertNull(TapLink.resolve(null, false, { awaitCancellation() }, { awaitCancellation() }))
        assertEquals(TapLink.CNO_MS + TapLink.NOVIG_MS, testScheduler.currentTime - start)
        assertNull(TapLink.resolve(null, false, { null }, { null }))
    }
}
