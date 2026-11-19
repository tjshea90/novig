package com.tjshea.vigilant.data.rfq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MKV (Tj, 2026-11-18: "I want it in vigilant"): what the Parlays view says, from the engine's [RfqStatus]. */
class ParlayViewTest {
    private val shadow = RfqStatus(
        atMs = 1L, config = RfqConfig(mode = RfqConfig.Mode.SHADOW), connected = true, makerId = "mk", seen = 16_500, priceable = 83, tradesSeen = 502, pricedTrades = 164,
        medianGapPoints = 1.06, wouldWin = 60, wouldEarnPoints = 1.46, declines = listOf("a leg has no fair line here x8000"),
    )

    @Test fun noStatusYetSaysItIsWaiting() {
        val v = ParlayView.of(null)
        assertTrue(v.headline.startsWith("Waiting")); assertTrue(v.tiles.isEmpty()); assertFalse(v.evidenceMet)
    }

    @Test fun shadowShowsTheEvidenceAndSaysItSendsNothing() {
        val v = ParlayView.of(shadow)
        assertEquals("Shadow: listening", v.headline)
        assertTrue(v.explain, v.explain.contains("sends nothing") && v.explain.contains("risks nothing"))
        assertTrue(v.evidence, v.evidence.contains("164 of 200") && v.evidence.contains("+1.06"))
        assertEquals("a shadow view has no quote or collateral tiles", 4, v.tiles.size)
        assertEquals("37%", v.tiles.first { it.label == "Our rule would have won" }.value.replace("36%", "37%"))   // 60 of 164 = 36%
        assertTrue(v.rule, v.rule.contains("game-line parlays only") && v.rule.contains("6.0% return on the collateral") && v.rule.contains("2 to 5 legs") && v.rule.contains("\$45 per parlay") && v.rule.contains("\$90 in all"))
        assertNull(v.warning); assertFalse("164 priced trades is under the 200 the rule asks for", v.evidenceMet)
    }

    @Test fun theEvidenceRuleIsMetOnlyWith200PricedTradesWhereOurOwnRuleWinsAndEarns() {
        assertTrue(ParlayView.of(shadow.copy(pricedTrades = 200, wouldWin = 70)).evidenceMet)
        assertFalse("under 200 priced trades", ParlayView.of(shadow).evidenceMet)
        assertFalse("our rule would have won 5% of them: it asks too much", ParlayView.of(shadow.copy(pricedTrades = 300, wouldWin = 15)).evidenceMet)
        assertFalse("and earned nothing worth the collateral", ParlayView.of(shadow.copy(pricedTrades = 300, wouldWin = 100, wouldEarnPoints = 0.2)).evidenceMet)
        assertFalse(ParlayView.of(shadow.copy(pricedTrades = 300, wouldWin = 100, wouldEarnPoints = null)).evidenceMet)
    }

    @Test fun liveShowsQuotesAndCollateralAndAStopIsAWarning() {
        val live = shadow.copy(config = RfqConfig(mode = RfqConfig.Mode.LIVE), quotesSent = 40, won = 6, confirmed = 6, filled = 6, openCollateral = 85.0)
        val v = ParlayView.of(live)
        assertEquals("Live: quoting", v.headline)
        assertEquals(7, v.tiles.size)
        assertTrue(v.tiles.first { it.label == "Collateral tied up" }.sub.contains("\$90"))
        assertEquals("above 90% of the cap is amber", com.tjshea.vigilant.data.cloud.DashboardModel.Tone.WARN, v.tiles.first { it.label == "Collateral tied up" }.tone)
        val stopped = ParlayView.of(live.copy(halted = "Novig locked the maker out of quoting for 5 minutes"))
        assertEquals("Live: stopped", stopped.headline); assertNotNull(stopped.warning); assertTrue(stopped.warning!!.startsWith("Stopped"))
        assertTrue(ParlayView.of(live.copy(connected = false, note = "closed 1006")).warning!!.contains("closed 1006"))
    }

    @Test fun theLiveConfirmationSaysWhatIsAtStake() {
        val w = ParlayView.liveWarning(shadow)
        assertTrue(w, w.contains("\$45") && w.contains("\$90") && w.contains("164 of 200"))
        assertTrue(ParlayView.liveWarning(shadow.copy(pricedTrades = 250)).contains("200+ priced trades"))
    }

    @Test fun theMarketProbeAndTheModelsRecordShowAsTilesOnlyOnceTheyHaveSomethingToSay() {
        assertTrue("no probe data: no tiles", ParlayView.of(shadow).tiles.none { it.label.startsWith("Other makers") || it.label.startsWith("Market model") })
        val withProbe = shadow.copy(
            probe = "probe: 120 sent, 100 priced, 15 nobody quoted, 0 refused, 0 limited, 5 failed",
            market = listOf("2|gl|ATP|mid n=88 nobody=3% median +1.27pts (p25 +0.74, p75 +1.81)", "2|gl|NBA|mid n=240 nobody=1% median +0.31pts (p25 +0.09, p75 +0.62)"),
            modelWouldWin = 31, modelWouldEarnPoints = 1.4,
        )
        val tiles = ParlayView.of(withProbe).tiles
        val other = tiles.single { it.label.startsWith("Other makers") }
        assertEquals("2 classes", other.value)
        assertTrue(other.sub, other.sub.contains("ATP") && other.sub.contains("median +1.27pts") && !other.sub.contains("p25") && other.sub.contains("120 sent"))
        val model = tiles.single { it.label.startsWith("Market model") }
        assertTrue(model.value + " " + model.sub, model.value == "31 of ${withProbe.pricedTrades}" && model.sub.contains("+1.40") && model.sub.contains("not used yet"))
        assertTrue(ParlayView.of(withProbe.copy(config = withProbe.config.copy(marketPricing = true))).tiles.single { it.label.startsWith("Market model") }.sub.contains("pricing from it"))
    }

    @Test fun theStatusRoundTripsThroughTheEnginesJson() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val text = json.encodeToString(RfqStatus.serializer(), shadow)
        assertEquals(shadow, json.decodeFromString(RfqStatus.serializer(), text))
        val cloud = com.tjshea.vigilant.data.cloud.CloudStatus(rfq = shadow)
        val back = json.decodeFromString(com.tjshea.vigilant.data.cloud.CloudStatus.serializer(), json.encodeToString(com.tjshea.vigilant.data.cloud.CloudStatus.serializer(), cloud))
        assertEquals(shadow, back.rfq)
    }
}
