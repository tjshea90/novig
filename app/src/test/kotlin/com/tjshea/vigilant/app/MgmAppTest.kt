package com.tjshea.vigilant.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.LocalClock
import com.tjshea.vigilant.app.ui.OpportunityDetail
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.betSlipLink
import com.tjshea.vigilant.data.book.Sportsbook
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Vigilant MGM's screens (Tj, 2026-09-27: the same app for BetMGM): the shared screens with the
 * book switched to BetMGM, as the `mgm` build runs them. Novig's own tests (the rest of this
 * suite) run with the book left at Novig and prove Vigilant reads exactly as before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class MgmAppTest {

    @get:Rule val compose = createComposeRule()

    @Before fun asMgm() { AppBook.current = Sportsbook.BETMGM }

    @After fun backToNovig() { AppBook.current = Sportsbook.NOVIG }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { SampleMgm.NOW }) {
                VigilantTheme(darkTheme = true) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun `the feed shows BetMGM's prices, best EV first, with no exchange depth`() {
        val s = SampleMgm.state()
        assertEquals(listOf("CeeDee Lamb Over 6.5", "Baltimore Ravens"), s.feed.map { it.selection })
        shoot("20_mgm_feed") { FeedScreen(s, {}, {}, {}, { _, _ -> }) }
        compose.onAllNodesWithText("BETMGM").assertCountEquals(2)
        compose.onAllNodesWithText("NOVIG").assertCountEquals(0)
        compose.onAllNodesWithText("fillable at +EV", substring = true).assertCountEquals(0)
    }

    @Test fun `a bet's sheet has BetMGM's price and hold, no order book or maker bid, and opens BetMGM's bet slip`() {
        val s = SampleMgm.state()
        val o = s.feed.first { it.selection == "Baltimore Ravens" }
        shoot("21_mgm_detail") { OpportunityDetail(o, s.settings) {} }
        compose.onNodeWithText("BetMGM hold").assertExists()
        compose.onNodeWithText("Open this bet in BetMGM").assertExists()
        compose.onAllNodesWithText("Novig", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Or post a bid (maker)").assertCountEquals(0)
        compose.onAllNodesWithText("order book", substring = true).assertCountEquals(0)
        // No state picked yet: it says so, and the link can only be BetMGM's home.
        compose.onNodeWithText("Pick your BetMGM state in Settings", substring = true).assertExists()
        assertEquals(com.tjshea.vigilant.data.book.BetMgmLinks.HOME, betSlipLink(o, ""))
        assertEquals("https://sports.nj.betmgm.com/en/sports?options=17345678-888-1002&type=Single", betSlipLink(o, "nj"))
    }

    @Test fun `the widget's rows open BetMGM's bet slip and show no exchange dollars`() {
        val s = SampleMgm.state().let { it.copy(settings = it.settings.copy(scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, bookState = "pa")) }
        val item = MiniWindow.items(s, SampleMgm.NOW).first { it.title == "Baltimore Ravens" }
        assertNull(item.available)
        assertEquals("https://sports.pa.betmgm.com/en/sports?options=17345678-888-1002&type=Single", MiniWindow.betLink(item, "pa"))
    }

    @Config(qualifiers = "w393dp-h6800dp-xxhdpi")
    @Test fun `Settings pick BetMGM's state and hide Novig's key and read limits`() {
        var saved: ScanSettings? = null
        val s = SampleMgm.state()
        shoot("22_mgm_settings") { SettingsScreen(s, { t -> saved = t(s.settings) }) }
        compose.onNodeWithText("BetMGM state").assertExists()
        compose.onAllNodesWithText("Novig API key").assertCountEquals(0)
        compose.onAllNodesWithText("Most Novig prices per scan", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Novig's price now").assertCountEquals(0)
        compose.onNodeWithText("NJ").performClick()
        assertEquals("nj", saved?.bookState)
    }

    @Test fun `with no PropLine or Odds API key the feed says where BetMGM's odds come from`() {
        shoot("23_mgm_feed_no_key") { FeedScreen(SampleMgm.state().copy(result = null, feed = emptyList(), proplineKeys = emptyList(), status = ScanStatus()), {}, {}, {}, { _, _ -> }) }
        compose.onNodeWithText("Add a PropLine key").assertExists()
    }

    @Test fun `CNO's list defaults to BetMGM`() {
        val s = SampleMgm.state()
        assertEquals(CnoView.defaultFor(Sportsbook.BETMGM.cnoSiteId), s.cnoUrl)
        assertTrue(s.cnoUrl.contains("site_id=4"))
        // A pasted view without a book filter gets BetMGM's, not Novig's.
        val pasted = s.copy(settings = s.settings.copy(cnoViewUrl = "https://crazyninjaodds.com/site/tools/positive-ev.aspx?ev_min=2"))
        assertTrue(pasted.cnoUrl.contains("site_id=4"))
    }

    @Test fun `the Novig app is unchanged - its book is Novig and CNO's default is Novig's`() {
        AppBook.current = Sportsbook.NOVIG
        assertEquals(CnoView.DEFAULT, SampleScan.state().cnoUrl)
        assertEquals("Novig", AppBook.name)
        assertEquals("novigapp://events/x", AppBook.betLink("x", null, ""))
    }
}
