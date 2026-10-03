package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.WalletStrip
import com.tjshea.vigilant.app.ui.WalletStripText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wallet on every tab (Tj, 2026-10-03: "make a quick way inside the app where I can see my vigilant wallet balance, maybe show it somewhere in the
 * app at all times"): the strip above the tab bar, its words, a tap reading it again, and the one reading every part of the app shares.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class WalletStripTest {

    @get:Rule val compose = createComposeRule()

    private val now = 1_800_000_000_000L

    @Test
    fun `the strip says the balance, the bids up from it and, once it's a minute old, its age`() {
        val r = WalletBalance.Reading(18.51, now - 10_000)
        assertEquals("Vigilant wallet \$18.51", WalletStripText.balance(r))
        assertEquals("", WalletStripText.detail(r, 0, 0.0, now))
        assertEquals("3 bids up (\$8.20)", WalletStripText.detail(r, 3, 8.2, now))
        assertEquals("1 bid up (\$2.00) · ${com.tjshea.vigilant.app.ui.Format.age(now - 120_000, now)}", WalletStripText.detail(r.copy(atMs = now - 120_000), 1, 2.0, now))
        assertEquals("Vigilant wallet not read yet", WalletStripText.balance(null))
    }

    @Test
    fun `tapping the strip reads the wallet again`() {
        var taps = 0
        compose.setContent { VigilantTheme { WalletStrip(WalletBalance.Reading(18.51, System.currentTimeMillis()), 2, 4.1, onRefresh = { taps++ }) } }
        compose.onNodeWithText("Vigilant wallet \$18.51").assertExists()
        compose.onNodeWithTag("walletStrip").assertTextContains("2 bids up (\$4.10)", substring = true)
        compose.onNodeWithTag("walletStrip").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `every reading anywhere reaches the strip's flow, and an older one never replaces a newer`() {
        val w = WalletBalance(read = { 20.0 }, setUp = { true }, prefs = null, clock = { now })
        assertNull(w.flow.value)
        w.record(18.51, now - 5_000)
        assertEquals(18.51, w.flow.value!!.dollars, 1e-9)
        w.record(30.0, now - 60_000)
        assertEquals(18.51, w.flow.value!!.dollars, 1e-9)
        kotlinx.coroutines.runBlocking { w.fresh(maxAgeMs = 0L) }
        assertEquals(20.0, w.flow.value!!.dollars, 1e-9)
    }

    @Test
    fun `screenshot - the wallet strip above the tab bar, every tab there`() {
        compose.setContent { VigilantTheme { StripAndTabs() } }
        compose.onNodeWithText("Vigilant wallet \$18.51").assertExists()
        compose.onRoot().captureRoboImage("screenshots/0_wallet_strip_tabs.png")
    }

    @androidx.compose.runtime.Composable
    private fun StripAndTabs() {
        Column {
            WalletStrip(WalletBalance.Reading(18.51, System.currentTimeMillis() - 150_000), 3, 8.2, onRefresh = {})
            NavigationBar {
                for (t in Tab.entries.filter { it.shownIn(com.tjshea.vigilant.data.scanner.ScannerMode.BOTH) }) {
                    NavigationBarItem(selected = t == Tab.BIDS, onClick = {}, icon = { TabGlyph(t) }, label = { Text(t.label) })
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TabGlyph(t: Tab) {
        val vector = t.icon
        val drawable = t.drawable
        if (vector != null) Icon(vector, null) else if (drawable != null) Icon(painterResource(drawable), null)
    }
}
