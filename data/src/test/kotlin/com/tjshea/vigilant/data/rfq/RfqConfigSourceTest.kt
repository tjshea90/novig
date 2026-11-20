package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PF1 (Tj 2026-11-20: "ignore whatever settings are on my phone app"): the engine's profit profile is the whole configuration while it exists; the phone's file is read only without it. */
class RfqConfigSourceTest {
    private val engine = """{"mode":"LIVE","maxLegs":2}"""
    private val app = """{"mode":"LIVE","longshotsOnly":true,"maxLegs":6}"""

    @Test fun theProfilePutsTheAppsFileOutOfPlay() {
        val c = RfqConfigSource.choose(engine, app)
        assertEquals(RfqConfigSource.Source.ENGINE, c.source); assertEquals(engine, c.text)
        assertEquals(2, RfqConfig.parse(c.text)!!.maxLegs)
        assertTrue(c.lock.isNotEmpty())
    }

    @Test fun withoutAProfileTheAppsFileIsReadAsBefore() {
        val c = RfqConfigSource.choose(null, app)
        assertEquals(RfqConfigSource.Source.APP, c.source); assertEquals(app, c.text); assertEquals("", c.lock)
        assertEquals(RfqConfigSource.Source.APP, RfqConfigSource.choose("  ", app).source)
    }

    @Test fun neitherFileMeansNoConfigAndTheMakerIsOff() {
        val c = RfqConfigSource.choose(null, null)
        assertEquals(RfqConfigSource.Source.NONE, c.source); assertNull(c.text); assertEquals("", c.lock)
        assertEquals(RfqConfig.Mode.OFF, (RfqConfig.parse(c.text) ?: RfqConfig()).mode)
    }

    @Test fun aProfileThatDoesNotParseNeverFallsBackToThePhone() {
        val c = RfqConfigSource.choose("{not json", app)
        assertEquals(RfqConfigSource.Source.ENGINE, c.source)
        assertNull(RfqConfig.parse(c.text))     // CloudMain then runs `RfqConfig()`: OFF, not the app's longshot settings
    }
}

/** PF1: the profile as it was written to `cloud-config` (`rfq-maker-engine.json`), parsed by the engine's own reader: a mistyped key would be ignored silently (ignoreUnknownKeys), so every key must be a real field. */
class RfqProfitProfileTest {
    private val text = RfqProfitProfileTest::class.java.getResource("/rfq-maker-engine-pf1.json")!!.readText()
    private val now = 1_790_500_000_000L
    private fun leg(id: String, event: String, p: Double, startsInH: Long = 3L) = LegFair(id, event, "Moneyline $id", p, 5, now - 60_000L, now + startsInH * 3_600_000L, false, "MONEY", league = "NBA")
    private fun created(vararg ids: String) = RfqProtocol.Created(now, "r", ids.toList(), now + 3_000L, 10.0)

    @Test fun everyKeyIsARealFieldAndTheValuesAreTheProfile() {
        val keys = kotlinx.serialization.json.Json.parseToJsonElement(text).let { (it as kotlinx.serialization.json.JsonObject).keys }
        val known = (0 until RfqConfig.serializer().descriptor.elementsCount).map { RfqConfig.serializer().descriptor.getElementName(it) }.toSet()
        assertEquals("keys the engine would silently ignore", emptySet<String>(), keys - known)
        val c = RfqConfig.parse(text)!!
        assertEquals(RfqConfig.Mode.LIVE, c.mode); assertEquals(2, c.maxLegs); assertEquals(0.8, c.maxLegFair, 1e-12); assertEquals(0.65, c.maxFair, 1e-12)
        assertEquals(false, c.longshotsOnly); assertEquals(false, c.onlyToday); assertEquals(2, c.maxDaysAhead); assertEquals(0.3, c.dayLossStopPct, 1e-12)
        assertEquals(1, c.maxSameParlay); assertEquals(2, c.maxParlaysPerOutcome); assertEquals(RfqConfig.Mode.SHADOW, c.liveParlays)
    }

    @Test fun theProfileQuotesTwoModerateFavouritesTomorrowAndDeclinesTheShapesItLeavesOut() {
        val c = RfqConfig.parse(text)!!
        val legs = mapOf("a" to leg("a", "e1", 0.7), "b" to leg("b", "e2", 0.65), "c" to leg("c", "e3", 0.6), "heavy" to leg("heavy", "e4", 0.85), "next" to leg("next", "e5", 0.6, startsInH = 30L))
        fun decide(vararg ids: String) = RfqPricer.decide(created(*ids), now, c, LegFairs { legs[it] }, 0.0)
        val q = decide("a", "b") as RfqDecision.Quote
        assertEquals("two favourites of .70 and .65 (fair .455) are quoted, over a longshots-only limit of .40", 0.455, q.fair, 1e-9)
        assertTrue("a game tomorrow is quoted (no today-only filter)", decide("c", "next") is RfqDecision.Quote)
        assertTrue("a leg at 85% is the sharp heavy-favourite pocket", (decide("a", "heavy") as RfqDecision.Decline).reason.contains("too likely"))
        val three = decide("a", "b", "c") as RfqDecision.Decline
        assertTrue(three.reason, three.reason.contains("3 legs")); assertNotNull("priced for the journal", three.fair)
    }
}
