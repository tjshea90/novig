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
        assertTrue("one sample per update, not per market: ${r.paired} of ${r.socketNotes}", r.paired > 0 && r.paired <= r.socketNotes)
        assertEquals(2_000L, r.medianLagMs)
    }

    @Test
    fun `matchups are reported per feed, and shared ones show each side's latest version`() {
        val race = VersionRace { 10_000_000L }
        race.note(VersionRace.Source.SOCKET, 1, "s;0;m", 10, 1_000)
        race.note(VersionRace.Source.SOCKET, 2, "s;0;m", 20, 1_000)
        race.note(VersionRace.Source.WEBSITE, 2, "s;0;m", 21, 4_000)
        race.note(VersionRace.Source.WEBSITE, 3, "s;0;m", 30, 4_000)
        val lines = race.lines()
        assertTrue(lines.first(), lines.first().startsWith("Matchups reported: socket 2, website 2, both 1"))
        assertTrue(lines.any { it.contains("matchup 2") && it.contains("v20") && it.contains("v21") })
        val none = VersionRace { 10_000_000L }
        none.note(VersionRace.Source.SOCKET, 1, "k", 1, 1)
        none.note(VersionRace.Source.WEBSITE, 9, "k", 1, 1)
        assertTrue(none.lines().first().contains("none shared"))
    }
}
