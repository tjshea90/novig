package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live bid presets: the built-in ones are the evidence's numbers, Tj's own are saved by name, and applying one never touches his money. */
class LiveBidPresetsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `a fresh install has the Balanced rules in force, real bids off, and one eighth Kelly`() {
        val s = ScanSettings()
        assertFalse(s.liveBid)
        assertFalse(s.liveBidReal)
        assertNull(s.liveBidHalted)
        assertEquals(LiveBidPresets.BALANCED, LiveBidPresets.active(s))
        assertEquals(AutoBetStake.EIGHTH_KELLY, s.liveBidLimits.stakeMode)
        assertEquals(0.125, s.liveBidLimits.stakeMode.kelly!!, 0.0)
    }

    @Test
    fun `the built-in presets are ordered from careful to wide, each safer than the next on the numbers that matter`() {
        val c = LiveBidPresets.CAREFUL.quality
        val b = LiveBidPresets.BALANCED.quality
        val w = LiveBidPresets.PAPER_WIDE.quality
        assertTrue("margin: 6% > 5% > 4%", c.margin > b.margin && b.margin > w.margin)
        assertTrue(c.maxOverround < b.maxOverround && b.maxOverround < w.maxOverround)
        assertTrue(c.minPinnLimit > b.minPinnLimit && b.minPinnLimit > w.minPinnLimit)
        assertTrue(c.maxQuietSec <= b.maxQuietSec)
        assertTrue("Careful never leads the book", c.neverLead && !b.neverLead)
        assertTrue("a pull that takes too long stops Careful sooner", c.maxCancelSec < b.maxCancelSec)
        assertTrue(LiveBidPresets.PAPER_WIDE.paperOnly)
        assertFalse(LiveBidPresets.CAREFUL.paperOnly || LiveBidPresets.BALANCED.paperOnly)
        // Every bid rests short enough to be a dead-man switch.
        assertTrue(LiveBidPresets.BUILT_IN.all { it.quality.ttlSec in 10..60 })
        // Every one pulls on a score and holds off after one.
        assertTrue(LiveBidPresets.BUILT_IN.all { it.quality.pullOnScore && it.quality.scoreHoldSec > 0 })
    }

    @Test
    fun `applying a preset sets the rules, names it, leaves the limits, and a paper-only one turns real bids off`() {
        val mine = ScanSettings(liveBid = true, liveBidReal = true, liveBidLimits = LiveBidLimits(maxStake = 7.0, maxPerDay = 99.0))
        val careful = LiveBidPresets.apply(mine, LiveBidPresets.CAREFUL)
        assertEquals(LiveBidPresets.CAREFUL.quality, careful.liveBidQuality)
        assertEquals("Careful", careful.liveBidPresetName)
        assertEquals("his money is his own", mine.liveBidLimits, careful.liveBidLimits)
        assertTrue("real stays on for a preset meant for money", careful.liveBidReal)
        val wide = LiveBidPresets.apply(mine, LiveBidPresets.PAPER_WIDE)
        assertFalse("a thin preset turns real bids off", wide.liveBidReal)
        assertTrue("and leaves the feed on to watch", wide.liveBid)
        assertEquals(LiveBidPresets.PAPER_WIDE, LiveBidPresets.active(wide))
    }

    @Test
    fun `a preset in force says so until one of its rules is changed`() {
        val s = LiveBidPresets.apply(ScanSettings(), LiveBidPresets.CAREFUL)
        assertEquals(LiveBidPresets.CAREFUL, LiveBidPresets.active(s))
        val edited = s.copy(liveBidQuality = s.liveBidQuality.copy(margin = 0.07))
        assertNull("changed since", LiveBidPresets.active(edited))
        assertEquals("Careful", edited.liveBidPresetName)
    }

    @Test
    fun `Tj's own presets are saved by name, replace one of the same name, survive a round trip, and a built-in's name is refused`() {
        val s = ScanSettings(liveBidQuality = LiveBidQuality(margin = 0.07, onlyLeagues = setOf("NCAAF", "NFL"), tennis = true))
        val saved = LiveBidPresets.save(s, "  Saturday football  ")!!
        assertEquals(1, saved.liveBidPresets.size)
        assertEquals("Saturday football", saved.liveBidPresets[0].name)
        assertEquals(s.liveBidQuality, saved.liveBidPresets[0].quality)
        assertEquals("Saturday football", saved.liveBidPresetName)
        assertEquals(LiveBidPresets.all(saved).last(), LiveBidPresets.active(saved))
        // The same name again replaces it.
        val again = LiveBidPresets.save(saved.copy(liveBidQuality = saved.liveBidQuality.copy(margin = 0.08)), "saturday FOOTBALL")!!
        assertEquals(1, again.liveBidPresets.size)
        assertEquals(0.08, again.liveBidPresets[0].quality.margin, 0.0)
        // A built-in's name, an empty name: refused.
        assertNull(LiveBidPresets.save(s, "careful"))
        assertNull(LiveBidPresets.save(s, "   "))
        // Applying it later brings the rules back.
        val back = LiveBidPresets.apply(ScanSettings(), again.liveBidPresets[0])
        assertEquals(0.08, back.liveBidQuality.margin, 0.0)
        assertEquals(setOf("NCAAF", "NFL"), back.liveBidQuality.onlyLeagues)
        // The whole settings file round-trips with presets and typed values in it.
        assertEquals(again, json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), again)))
        // Deleting his own works, deleting a built-in does not.
        val gone = LiveBidPresets.delete(again, "Saturday Football")
        assertTrue(gone.liveBidPresets.isEmpty())
        assertNull(gone.liveBidPresetName)
        assertEquals(gone, LiveBidPresets.delete(gone, "Careful"))
    }

    @Test
    fun `settings saved before live bids existed read as the defaults`() {
        val old = json.decodeFromString(ScanSettings.serializer(), """{"bankroll":250.0,"pinnLive":true}""")
        assertEquals(250.0, old.bankroll, 0.0)
        assertFalse(old.liveBid)
        assertEquals(LiveBidQuality(), old.liveBidQuality)
        assertEquals(LiveBidLimits(), old.liveBidLimits)
        assertNotNull(LiveBidPresets.active(old))
    }

    @Test
    fun `a crash switches live bids off, real ones too`() {
        val s = ScanSettings(liveBid = true, liveBidReal = true, pinnLive = true).safeStart(crashed = true)
        assertFalse(s.liveBid)
        assertFalse(s.liveBidReal)
        assertTrue("an ordinary restart changes nothing", ScanSettings(liveBid = true, liveBidReal = true).safeStart(crashed = false).liveBidReal)
    }

    @Test
    fun `the summary names the rules in words`() {
        val text = LiveBidPresets.CAREFUL.quality.summary()
        assertTrue(text, text.contains("6% under Pinnacle's fair"))
        assertTrue(text, text.contains("never leads the book"))
        assertTrue(text, text.contains("Pinnacle limit ≥ \$1000"))
        assertTrue(text, text.contains("30s hold after a score"))
        assertEquals("2.5%", LiveBidQuality.pct(0.025))
        assertEquals("5%", LiveBidQuality.pct(0.05))
    }
}
