package com.tjshea.vigilant.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ApiBetActions
import com.tjshea.vigilant.app.ui.LocalApiBet
import com.tjshea.vigilant.app.ui.ProvideApiBet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-02: "when I start the vigilant scanner the list of bets gets laggy. This is ok if it's supposed to but not ok if it's a sign of bad
 * code." It was code: the app's root provided [LocalApiBet], a STATIC composition local, with a new [ApiBetActions] on every state it got (3 a
 * second while a scan runs, plus every meter and CNO read). A new value for a static local redraws everything under it with nothing skipped:
 * every card on screen, the bars, the chips, the badges, all of it, a few times a second while Tj scrolled (RESEARCH.md §63).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScanLagTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    /** A card-like piece of the screen whose inputs never change: it should draw once. */
    @Composable
    private fun Card(runs: IntArray) {
        SideEffect { runs[0]++ }
        Text("a bet")
    }

    @Composable
    private fun Root(progress: Int, api: ApiBettingController, runs: IntArray) {
        ProvideApiBet(enabled = true, api = api) {
            Text("Novig prices $progress/4588")
            Card(runs)
        }
    }

    @Test
    fun `a scan tick no longer redraws every card under the Bet button's actions`() {
        val api = ApiBettingController(app.container, MutableStateFlow(UiState()), CoroutineScope(SupervisorJob() + Dispatchers.Default), MutableSharedFlow(), readBook = { null })
        var tick by mutableIntStateOf(0)
        val runs = IntArray(1)
        // Shaped like the app's root: it takes the whole state (here a scan's progress) and recomposes with every one.
        compose.setContent { Root(tick, api, runs) }
        compose.waitForIdle()
        repeat(5) {
            tick++
            compose.waitForIdle()
        }
        compose.onNodeWithTextExists("Novig prices 5/4588")
        assertEquals("the card drew once, not once per tick", 1, runs[0])
    }

    @Test
    fun `what it was, a new value for the static local every tick, redraws the card every tick`() {
        var tick by mutableIntStateOf(0)
        val runs = IntArray(1)
        compose.setContent {
            val t = tick
            CompositionLocalProvider(LocalApiBet provides ApiBetActions(enabled = true, betOpportunity = {}, betCno = {})) {
                Text("Novig prices $t/4588")
                Card(runs)
            }
        }
        compose.waitForIdle()
        repeat(5) {
            tick++
            compose.waitForIdle()
        }
        assertEquals(6, runs[0])
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTextExists(text: String) {
        assertTrue(onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty())
    }

    // ---- every holder handed down the screens is made once, not per state (source pins: the screens' own tests prove what they do) ----

    private fun source(path: String) = File("src/main/kotlin/com/tjshea/vigilant/app/$path").readText()

    @Test
    fun `the root, the widget and the feed hand down remembered holders`() {
        val main = source("MainActivity.kt")
        // The Bet button's actions only through ProvideApiBet (remembered), never built inline.
        assertFalse(main.contains("LocalApiBet provides"))
        assertTrue(main.contains("ProvideApiBet(state.betting.enabled, vm.api)"))
        // Novig's link opener: a static local too.
        assertTrue(main.contains("val openNovigLocal: (String?) -> Unit = remember { { link -> openNovig(link) } }"))
        assertTrue(main.contains("LocalOpenNovig provides openNovigLocal"))
        // The floating widget's buttons, once per window.
        assertTrue(main.contains("val actions = remember(w) {"))
        // ParlayAPI's picks' buttons, once per bet being opened.
        assertTrue(main.contains("val parlayActions = remember(vm, onOpenInNovig, openingBet) {"))
        assertTrue(main.contains("parlay = parlayActions,"))
        assertTrue(source("ui/FeedScreen.kt").contains("val parlayActions = remember(parlay, scope, snackbar, onUnhide) {"))
        val provide = source("ui/ApiBettingUi.kt").substringAfter("fun ProvideApiBet").substringBefore("\n}\n")
        assertTrue(provide, provide.contains("remember(enabled, api)"))
    }
}
