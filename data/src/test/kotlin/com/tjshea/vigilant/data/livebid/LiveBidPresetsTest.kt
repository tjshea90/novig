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

    @Test
    fun `More fills is thinner on the margin and looser on position than Balanced, but every guard that pulls a bid stays`() {
        val f = LiveBidPresets.FILL.quality
        val b = LiveBidPresets.BALANCED.quality
        assertTrue(f.margin < b.margin && f.margin >= 0.03)
        assertTrue(f.bothSides && !f.neverLead && f.countCredit)
        assertTrue(f.settleSec <= b.settleSec && f.scoreHoldSec <= b.scoreHoldSec)
        assertTrue("pulls on a score, a danger frame, silence, a fading edge, Novig's move", f.pullOnScore && f.dangerHoldSec > 0 && f.maxQuietSec > 0 && f.pullBelowEv > 0.0 && f.novigMovePull > 0.0)
        assertTrue("the self-checks stay on", f.pickOffLimit > 0 && f.maxCancelSec > 0 && f.maxPlaceSec > 0)
        assertTrue(f.ttlSec in 10..60)
        assertFalse(LiveBidPresets.FILL.paperOnly)
        assertTrue(LiveBidPresets.builtIn("More fills"))
        assertEquals(4, LiveBidPresets.BUILT_IN.size)
    }

    @Test
    fun `fill the wallet lifts the counts and the dollars to the wallet and never lowers what he set`() {
        val set = LiveBidLimits(maxBids = 8, maxBidsPerGame = 3, maxPerGame = 10.0, maxPerDay = 40.0)
        assertEquals("off: the numbers are the limits", set, set.effective(200.0))
        val fill = set.copy(fillWallet = true)
        val e = fill.effective(200.0)
        assertEquals(LiveBidLimits.WALLET_MAX_BIDS, e.maxBids)
        assertEquals(LiveBidLimits.WALLET_MAX_PER_GAME_BIDS, e.maxBidsPerGame)
        assertEquals(50.0, e.maxPerGame, 1e-9)
        assertEquals(400.0, e.maxPerDay, 1e-9)
        assertEquals("the loss stop and the reserve are untouched", fill.haltLoss, e.haltLoss, 0.0)
        assertEquals(fill.walletReserve, e.walletReserve, 0.0)
        // A bigger number he set himself is kept.
        assertEquals(500.0, fill.copy(maxPerDay = 500.0).effective(200.0).maxPerDay, 0.0)
        // Wallet unknown: nothing is lifted.
        assertEquals(fill, fill.effective(null))
        assertEquals(fill, fill.effective(0.0))
    }

    @Test
    fun `the autopilot sets both engines, keeps his money, and says when it is in force`() {
        val mine = ScanSettings(liveBidLimits = LiveBidLimits(maxStake = 7.0, haltLoss = 9.0), pinnLiveStake = 3.0, pinnLiveHaltLoss = 8.0, liveBidHalted = "x", pinnLiveHalted = "y", tailLive = com.tjshea.vigilant.data.scanner.TailLiveSettings(halted = "z"))
        val paper = LiveAutopilot.apply(mine, real = false)
        assertTrue(LiveAutopilot.inForce(paper))
        assertFalse(LiveAutopilot.real(paper))
        assertTrue(paper.liveBid && paper.pinnLive && paper.tailLive.on && !paper.liveBidReal && !paper.pinnLiveBet && !paper.tailLive.bet)
        assertEquals(com.tjshea.vigilant.data.pinnodds.LiveTrigger.EITHER, paper.pinnLiveTrigger)
        assertEquals("halts cleared", null, paper.liveBidHalted)
        assertEquals(null, paper.pinnLiveHalted)
        assertEquals(null, paper.tailLive.halted)
        assertEquals("his money stays", 7.0, paper.liveBidLimits.maxStake, 0.0)
        assertEquals(9.0, paper.liveBidLimits.haltLoss, 0.0)
        assertEquals(3.0, paper.pinnLiveStake, 0.0)
        assertEquals(8.0, paper.pinnLiveHaltLoss, 0.0)
        assertTrue(paper.liveBidLimits.fillWallet)
        val real = LiveAutopilot.apply(mine, real = true)
        assertTrue(LiveAutopilot.real(real) && real.liveBidReal && real.pinnLiveBet && real.tailLive.bet)
        assertTrue("the tail alone being real counts as real", LiveAutopilot.real(ScanSettings(tailLive = com.tjshea.vigilant.data.scanner.TailLiveSettings(on = true, bet = true))))
        assertFalse("changing one rule takes it out of force", LiveAutopilot.inForce(real.copy(liveBidQuality = real.liveBidQuality.copy(margin = 0.05))))
        val off = LiveAutopilot.off(real)
        assertFalse(LiveAutopilot.real(off) || off.liveBid || off.pinnLive || off.tailLive.on)
        assertEquals("the rules stay for next time", real.liveBidQuality, off.liveBidQuality)
    }
}
