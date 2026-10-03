package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.CnoDetail
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.OpportunityDetail
import com.tjshea.vigilant.app.ui.TrapGuardText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cno.CnoPick
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The trap guard on the bet sheets (Tj, 2026-10-03: "find these trap bets and avoid them"; RESEARCH.md §71): the lists still show a game further off
 * than the guard's hours, and its sheet says why auto-bet, alerts and bids leave it alone, so a bet by hand is made knowing it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class TrapGuardUiTest {

    @get:Rule val compose = createComposeRule()

    private val now = SampleScan.NOW
    private val h = 3_600_000L

    private fun screen(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalClock provides { now }) {
            VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Test
    fun `the sheet's note is there past the guard's hours and only then`() {
        assertTrue(TrapGuardText.sheetNote(now + 24 * h, now, 6)!!.startsWith("Trap guard: this game starts in about 24 h, more than 6 h off, so auto-bet, alerts and bids leave it alone."))
        assertNull(TrapGuardText.sheetNote(now + 5 * h, now, 6))
        assertNull(TrapGuardText.sheetNote(now + 24 * h, now, 0))
        assertNull(TrapGuardText.sheetNote(null, now, 6))
    }

    @Test
    fun `CNO's bet sheet warns on a game a day off with the guard at 6 h, and not with it off`() {
        val s = SampleCno.withBooks()
        val row = SampleCno.rows[1] // Jefferson, a day off
        val pick = CnoPick(row, row.ev, false)
        screen { CnoDetail(pick, s.cno.snapshot, s.settings.copy(trapEarlyHours = 6), s.cnoUrl, s.books[row.key], now) }
        compose.onNodeWithTag("trapEarlySheet").assertTextContains("more than 6 h off", substring = true)
    }

    @Test
    fun `CNO's bet sheet has no note with the guard off`() {
        val s = SampleCno.withBooks()
        val row = SampleCno.rows[1]
        screen { CnoDetail(CnoPick(row, row.ev, false), s.cno.snapshot, s.settings.copy(trapEarlyHours = 0), s.cnoUrl, s.books[row.key], now) }
        compose.onNodeWithTag("trapEarlySheet").assertDoesNotExist()
    }

    @Test
    fun `Vigilant's bet sheet warns the same way`() {
        val state = SampleCno.state()
        val o = state.result!!.opportunities.first { it.quote != null && it.fairProbability != null && it.event.startsTs - now > 6 * h }
        screen { OpportunityDetail(o, state.settings.copy(trapEarlyHours = 6), onTrack = {}) }
        compose.onNodeWithTag("trapEarlySheet").assertTextContains("Trap guard", substring = true)
    }
}
