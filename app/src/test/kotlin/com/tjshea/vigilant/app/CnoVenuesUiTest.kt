@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.CnoScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The CNO tab's Novig / Kalshi chips (CK, 2026-10-21): each switches its book in the scan's filters; a Kalshi row says KALSHI and opens at Kalshi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2400dp-xxhdpi")
class CnoVenuesUiTest {

    @get:Rule val compose = createComposeRule()

    private fun cnoTab(): () -> ScanSettings {
        var s by mutableStateOf(ScanSettings(scanner = ScannerMode.CNO))
        val rows = SampleCno.rows.mapIndexed { i, r -> if (i == 1) r.copy(book = "Kalshi") else r }
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { SampleScan.NOW }) {
                VigilantTheme(darkTheme = true) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        CnoScreen(
                            SampleCno.state(SampleScan.state().copy(settings = s), CnoState(snapshot = SampleCno.snapshot(rows = rows))), {}, {},
                            onVenues = { v -> s = s.copy(cnoFilters = s.cnoFilters.copy(venues = v)) },
                        )
                    }
                }
            }
        }
        return { s }
    }

    @Test
    fun `the chips switch each book in the scan's filters`() {
        val s = cnoTab()
        compose.onNodeWithTag("cnoVenueNovig").assertIsSelected()
        compose.onNodeWithTag("cnoVenueKalshi").assertIsSelected()
        compose.onNodeWithText("Kalshi EV net of its fee").assertExists()
        compose.onNodeWithTag("cnoVenueKalshi").performClick()
        compose.waitForIdle()
        assertEquals(false, s().cnoFilters.venues.kalshi)
        assertEquals(true, s().cnoFilters.venues.novig)
        compose.onNodeWithTag("cnoVenueKalshi").assertIsNotSelected()
        compose.onNodeWithTag("cnoVenueNovig").performClick()
        compose.waitForIdle()
        assertEquals(false, s().cnoFilters.venues.novig)
    }

    @Test
    fun `a Kalshi row is labelled Kalshi and its button opens Kalshi`() {
        cnoTab()
        compose.onNodeWithText("KALSHI", substring = true).assertExists()
        compose.onNodeWithText("Open in Kalshi", substring = true).assertExists()
    }

    @Test
    fun `a screenshot of the CNO tab with both books`() {
        cnoTab()
        compose.onRoot().captureRoboImage("screenshots/5s_cno_tab_venues.png")
    }

    @Test
    fun `the bet sheet of a Kalshi row opens Kalshi`() {
        val s = SampleCno.withBooks()
        val pick = s.cnoPicks(SampleScan.NOW)!!.picks.first().let { it.copy(row = it.row.copy(book = "Kalshi")) }
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { SampleScan.NOW }) {
                VigilantTheme(darkTheme = true) {
                    com.tjshea.vigilant.app.ui.CnoDetail(pick, s.cno.snapshot, s.settings, s.cnoUrl, s.books[pick.row.key], SampleScan.NOW)
                }
            }
        }
        compose.onNodeWithText("Open in Kalshi").assertExists()
        compose.onNodeWithText("Open in Kalshi opens CNO's link to this bet at Kalshi.", substring = true).assertExists()
    }
}
