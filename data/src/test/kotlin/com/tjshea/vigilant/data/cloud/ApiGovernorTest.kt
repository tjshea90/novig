package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiGovernorTest {
    private val allOff = ApiToggles.rest(ScanSettings(), ApiToggles.IDS)
    private val settings = ApiToggles.setOn(ApiToggles.setOn(allOff, "parlay", true), "sgo", true)
    private val silent = listOf(FeedLine("parlay"), FeedLine("parlay_props"), FeedLine("sgo", fetched = 8))
    private val h = 3_600_000L

    @Test
    fun `an api silent for three scans is rested for an hour and the engine scans without it`() {
        val g = ApiGovernor()
        assertTrue(g.observe(silent, settings, 0).isEmpty())
        assertTrue(g.observe(silent, settings, 1).isEmpty())
        val ev = g.observe(silent, settings, 2)
        assertEquals(listOf<ApiGovernor.Event>(ApiGovernor.Event.Rested("parlay", 2 + h, 1)), ev)
        assertEquals(false, ApiToggles.isOn(g.apply(settings, 3), "parlay"))
        assertEquals("the phone's own switch is not touched", true, ApiToggles.isOn(settings, "parlay"))
        assertEquals("a working api is left alone", true, ApiToggles.isOn(g.apply(settings, 3), "sgo"))
        assertEquals(listOf("parlay"), g.resting(3).map { it.id })
    }

    @Test
    fun `try again forgives the one api, its rest and strikes go`() {
        val g = ApiGovernor()
        repeat(3) { g.observe(silent, settings, it.toLong()) }
        assertEquals(listOf("parlay"), g.resting(3).map { it.id })
        assertTrue(g.retry(ApiToggles.policy("parlay")!!.id))
        assertTrue(g.resting(3).isEmpty())
        assertEquals("a second tap finds nothing to forgive", false, g.retry(ApiToggles.policy("parlay")!!.id))
        repeat(2) { g.observe(silent, settings, 10L + it) }
        assertEquals("the next three silent scans start from the first rest", 1, (g.observe(silent, settings, 20).single() as ApiGovernor.Event.Rested).strikes)
    }

    @Test
    fun `the rest ends and the api is asked again, a good answer clears its record`() {
        val g = ApiGovernor()
        repeat(3) { g.observe(silent, settings, it.toLong()) }
        val ev = g.observe(listOf(FeedLine("sgo", fetched = 8)), settings, 2 + h + 1)
        assertEquals(listOf<ApiGovernor.Event>(ApiGovernor.Event.Resumed("parlay")), ev)
        assertTrue(g.resting(2 + h + 2).isEmpty())
        // it answers this time: no strike is kept, so three more silent scans start from the first rest again
        g.observe(listOf(FeedLine("parlay", fetched = 5)), settings, 2 + h + 10)
        repeat(2) { g.observe(silent, settings, 2 + h + 20L + it) }
        assertEquals(1, (g.observe(silent, settings, 2 + h + 30).single() as ApiGovernor.Event.Rested).strikes)
    }

    @Test
    fun `still silent after the third rest it is turned off once, by asking the phone, and the ask is not repeated`() {
        val g = ApiGovernor()
        var t = 0L
        var last: List<ApiGovernor.Event> = emptyList()
        repeat(3) { cycle ->
            repeat(3) { last = g.observe(silent, settings, t++) }
            assertEquals(cycle + 1, (last.single() as ApiGovernor.Event.Rested).strikes)
            t += 10 * h
            assertTrue(g.observe(silent.take(0) + FeedLine("sgo", fetched = 8), settings, t++).single() is ApiGovernor.Event.Resumed)
        }
        repeat(2) { g.observe(silent, settings, t++) }
        assertEquals(listOf<ApiGovernor.Event>(ApiGovernor.Event.TurnedOff("parlay")), g.observe(silent, settings, t++))
        repeat(5) { assertTrue(g.observe(silent, settings, t++).isEmpty()) }
        assertEquals(setOf("parlay"), g.askedOff())
        // the phone applied the request: parlay is off there, the governor lets go
        g.observe(silent, allOff, t++)
        assertTrue(g.askedOff().isEmpty())
    }

    @Test
    fun `errors, pacing holds, standing by and keyless apis are never silence`() {
        val g = ApiGovernor()
        val busy = listOf(FeedLine("parlay", error = "429"), FeedLine("parlay_props", heldBack = "day's share spent"), FeedLine("sgo", standingBy = 3), FeedLine("kalshi"), FeedLine("polymarket"))
        val s = ApiToggles.setOn(ApiToggles.setOn(settings, "kalshi", true), "polymarket", true)
        repeat(10) { assertTrue(g.observe(busy, s, it.toLong()).isEmpty()) }
        assertFalse(g.resting(100).any { it.id in setOf("kalshi", "polymarket", "parlay", "sgo") })
    }

    @Test
    fun `an api the phone has off, and the shared pinnacle feed, are never governed`() {
        val g = ApiGovernor()
        repeat(5) { g.observe(listOf(FeedLine("parlay"), FeedLine("pinnacle")), allOff, it.toLong()) }
        assertTrue(g.resting(10).isEmpty())
        val pin = ApiToggles.setOn(allOff, "pinnapi", true)
        repeat(5) { g.observe(listOf(FeedLine("pinnacle")), pin, it.toLong()) }
        assertTrue("two boards share one feed: no single switch owns it", g.resting(10).isEmpty())
    }
}
