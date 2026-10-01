package com.tjshea.vigilant.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source pins for work that must not run on every recomposition (Tj, 2026-10-01: "make sure the app ... is well optimized"): the whole app
 * recomposes with each state it gets, so the tab badges' counts and the CNO tab's screening are remembered on exactly what they read.
 * (A count that's right is proven by the screens' own tests; what these pin is that it isn't recomputed for nothing.)
 */
class RecompositionCostTest {

    private fun source(path: String) = File("src/main/kotlin/com/tjshea/vigilant/app/$path").readText()

    @Test
    fun `the tab badges are counted only when what they count changes`() {
        val main = source("MainActivity.kt")
        val badge = main.substringAfter("private fun TabIconWithCount").substringBefore("BadgedBox")
        assertTrue(badge, badge.contains("remember(state.feed, state.settings, now) { state.feedAt(now).size }"))
        assertTrue(badge, Regex("remember\\(state\\.cno\\.snapshot, state\\.settings, state\\.placed, state\\.placedIndex, state\\.cnoLinks, state\\.books, state\\.novigLive, now\\)").containsMatchIn(badge))
    }

    @Test
    fun `the CNO tab screens its list once per change, not three times per recomposition`() {
        val cno = source("ui/CnoScreen.kt")
        assertTrue(cno.contains("val screened = remember(*keys) { state.cnoPicks(now) }"))
        assertTrue(cno.contains("val candidates = remember(*keys) { state.cnoCandidates(now) }"))
        assertTrue(cno.contains("val picks = remember(*keys) { state.cnoShown(now)"))
    }

    @Test
    fun `the screen takes a scan's ticks, the meters and the Tracker's saves a few times a second`() {
        val vm = source("MainViewModel.kt")
        assertTrue(vm.contains("followThrottled(c.runner.state, SCAN_MIRROR_MS)"))
        assertTrue(vm.contains("followThrottled(c.usage.flow, USAGE_MIRROR_MS)"))
        assertTrue(vm.contains("followThrottled(c.tracker.flow.filterNotNull(), TRACKER_MIRROR_MS)"))
    }
}
