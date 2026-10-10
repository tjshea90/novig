package com.tjshea.vigilant.data.pinnodds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The race on real frames: two books fed the same Pinnodds frames at different times pair every version and report the gap. */
class VersionRaceWiringTest {
    @Test
    fun `two books fed the same real frames pair every version they both took in`() {
        val race = VersionRace { 10_000_000L }
        val socket = PinnBook().also { b -> b.versionListener = { id, k, v, at -> race.note(VersionRace.Source.SOCKET, id, k, v, at) } }
        val site = PinnBook().also { b -> b.versionListener = { id, k, v, at -> race.note(VersionRace.Source.WEBSITE, id, k, v, at) } }
        val frames = PinnTestFrames.real().values.toList()
        assertTrue(frames.isNotEmpty())
        frames.forEach { socket.apply(it, 1_000L) }
        frames.forEach { site.apply(it, 3_000L) }
        val r = race.report()
        assertTrue("some versions came in through the real frames: $r", r.socketNotes > 0 && r.websiteNotes > 0)
        assertEquals(r.socketNotes, r.websiteNotes)
        assertEquals(r.paired.toLong(), r.socketNotes)
        assertEquals(2_000L, r.medianLagMs)
    }
}
