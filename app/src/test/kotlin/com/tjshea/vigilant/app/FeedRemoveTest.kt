@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.Opportunity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tj, 2026-09-30: "For the +ev vigilant scan tab, give me the x option for each bet to remove the bet from the list permanently, even through
 * refreshes and rescans, exactly like the cno section already does". The ✕ writes the same record as CNO's (placed.json, hidden), so the bet
 * stays out of the list and the widget through every rescan; Undo and "Put back" bring it back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class FeedRemoveTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW

    /** [o] removed with ✕, as [MainViewModel.hideOpportunity] records it. */
    private fun removed(o: Opportunity) = MiniWindow.placed(MiniWindow.itemFor(o, now)!!, now, hidden = true)

    @Test
    fun `a removed bet stays out of the list and the widget through a rescan, and comes back when put back`() {
        val base = SampleScan.state().indexed(now)
        val o = base.feedAt(now).first()
        val after = base.copy(placed = listOf(removed(o))).indexed(now)
        assertTrue(after.feedAt(now).none { it.key == o.key })
        assertTrue(MiniWindow.items(after, now).none { it.key == o.key })
        // A rescan finds it again, re-priced: still gone.
        val rescanned = after.copy(result = SampleScan.result()).let { it.copy(feed = it.feedOf(it.result)) }
        assertTrue(rescanned.feedAt(now).none { it.key == o.key })
        assertEquals(base.feedAt(now).size - 1, rescanned.feedAt(now).size)
        // Put back (the mark taken off): listed again.
        assertTrue(rescanned.copy(placed = emptyList()).indexed(now).feedAt(now).any { it.key == o.key })
    }

    private fun screen(state: UiState, onHide: (Opportunity) -> Unit = {}, onUnhide: (String) -> Unit = {}) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FeedScreen(state, onScan = {}, onToggleLeague = {}, onOpenSettings = {}, onTrack = { _, _ -> }, onHide = onHide, onUnhide = onUnhide)
                }
            }
        }
    }

    @Test
    fun `each +EV card has CNO's x, which removes that bet and offers Undo`() {
        val state = SampleScan.state().indexed(now)
        val o = state.feedAt(now).first()
        var hidden: Opportunity? = null
        var undone: String? = null
        screen(state, onHide = { hidden = it }, onUnhide = { undone = it })
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Remove ${o.selection} from the list").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(o.key, hidden?.key)
        compose.onNodeWithText("Removed: ${o.selection}", substring = true).assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(o.key, undone)
    }

    @Test
    fun `the bets removed are listed under the summary, each with Put back`() {
        val base = SampleScan.state().indexed(now)
        val o = base.feedAt(now).first()
        var putBack: String? = null
        screen(base.copy(placed = listOf(removed(o))).indexed(now), onUnhide = { putBack = it })
        compose.onNodeWithContentDescription("Remove ${o.selection} from the list").assertDoesNotExist()
        compose.onNodeWithText("Show the 1 bet you removed").performClick()
        compose.onNodeWithText("✕ ${o.selection}", substring = true).assertExists()
        compose.onNodeWithText("Put back").performClick()
        assertEquals(o.key, putBack)
    }
}
