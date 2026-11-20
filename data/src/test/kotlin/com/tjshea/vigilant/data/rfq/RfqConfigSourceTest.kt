package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
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
