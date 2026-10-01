package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.AutoBetSection
import com.tjshea.vigilant.app.ui.AutoBetText
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Betting › Auto-bet (Tj, 2026-10-01): off until turned on (and asked once, plainly), then each of the seven choices he listed, the
 * stops it shows, and the Resume after a lost order. The state is a real one the callback edits, as Settings does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h3000dp-xxhdpi")
class AutoBetUiTest {

    @get:Rule val compose = createComposeRule()

    private var ui by mutableStateOf(SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0)))
    private val settings get() = ui.settings

    private fun show(
        configure: (ScanSettings) -> ScanSettings = { it },
        betting: BettingUi = BettingUi(enabled = true, balance = 25.0),
        blocked: String? = null,
        onTest: () -> Boolean = { true },
    ) {
        ui = SampleScan.state().copy(betting = betting, settings = configure(ScanSettings(autoScan = AutoScanMode.CNO)))
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AutoBetSection(ui, notificationsBlocked = blocked, onTestNotification = onTest) { t -> ui = ui.copy(settings = t(ui.settings)) }
                    }
                }
            }
        }
    }

    // ---- turning it on ------------------------------------------------------------------------------------------

    @Test
    fun `it is off by default, and turning it on asks first and says it places real bets`() {
        show()
        assertFalse(settings.autoBet)
        compose.onNodeWithTag("autoBetSwitch").assertIsOff()
        compose.onNodeWithTag("autoBetSwitch").performClick()
        // Nothing changed yet: the question is on screen with what it will do.
        assertFalse(settings.autoBet)
        compose.onNodeWithText("Turn on auto-bet?").assertExists()
        compose.onNodeWithText("Vigilant will place REAL bets from your Vigilant wallet", substring = true).assertExists()
        compose.onNodeWithText("never bets a game that has started", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(settings.autoBet)
        compose.onNodeWithText("Turn on auto-bet?").assertDoesNotExist()
        // Confirmed: on, and the background CNO scan it runs in is switched from Off to CNO.
        compose.onNodeWithTag("autoBetSwitch").performClick()
        compose.onNodeWithTag("autoBetConfirm").performClick()
        assertTrue(settings.autoBet)
        compose.onNodeWithTag("autoBetSwitch").assertIsOn()
        // Off again at one tap, no question.
        compose.onNodeWithTag("autoBetSwitch").performClick()
        assertFalse(settings.autoBet)
    }

    @Test
    fun `turning it on switches the background scan from Off to CNO, and leaves CNO + Vigilant as it is`() {
        show({ it.copy(autoScan = AutoScanMode.OFF) })
        compose.onNodeWithTag("autoBetSwitch").performClick()
        compose.onNodeWithTag("autoBetConfirm").performClick()
        assertEquals(AutoScanMode.CNO, settings.autoScan)
        compose.onNodeWithTag("autoBetSwitch").performClick() // off
        ui = ui.copy(settings = ui.settings.copy(autoScan = AutoScanMode.BOTH))
        compose.onNodeWithTag("autoBetSwitch").performClick()
        compose.onNodeWithTag("autoBetConfirm").performClick()
        assertEquals(AutoScanMode.BOTH, settings.autoScan)
    }

    @Test
    fun `the confirm lists the criteria Tj picked and the wallet`() {
        show({ it.copy(autoBetBooks = 4, autoBetMinEv = 0.0325, autoBetTwoSided = 3, autoBetStake = AutoBetStake.QUARTER_KELLY, autoBetMaxStake = 8.0, apiMaxPerDay = 40.0) })
        compose.onNodeWithTag("autoBetSwitch").performClick()
        val text = AutoBetText.confirm(settings, 25.0)
        assertTrue(text, text.contains("(\$25.00)"))
        assertTrue(text, text.contains("at least 4 books agreeing it's +EV on their own, 3 pricing both sides"))
        assertTrue(text, text.contains("an edge of +3.25% or more"))
        assertTrue(text, text.contains("staking its ¼ Kelly stake of your \$1,000.00 bankroll (never over \$8.00)"))
        assertTrue(text, text.contains("never more than \$40.00 in a day"))
        compose.onNodeWithText("Vigilant will place REAL bets", substring = true).assertExists()
    }

    @Test
    fun `betting must be set up first, so the switch is disabled and says so`() {
        show(betting = BettingUi(enabled = false))
        compose.onNodeWithTag("autoBetSwitch").assertIsNotEnabled()
        compose.onNodeWithTag("autoBetRunning").assertExists()
        assertTrue(AutoBetText.whyNotRunning(ui)!!.contains("isn't set up"))
    }

    // ---- the criteria -------------------------------------------------------------------------------------------

    @Test
    fun `books agreeing offers 2, 3, 4 and 5+, and books pricing both sides 1, 2 and 3`() {
        show()
        // The first row of digits is books agreeing; the second is books pricing both sides.
        for ((label, n) in listOf("2" to 2, "3" to 3, "4" to 4)) {
            compose.onAllNodesWithText(label)[0].performClick()
            assertEquals(n, settings.autoBetBooks)
        }
        compose.onNodeWithText("5+").performClick()
        assertEquals(5, settings.autoBetBooks)
        compose.onNodeWithText("5+").assertIsSelected()
        compose.onNodeWithText("1").performClick()
        assertEquals(1, settings.autoBetTwoSided)
        compose.onAllNodesWithText("2").onLast().performClick()
        assertEquals(2, settings.autoBetTwoSided)
        compose.onAllNodesWithText("3").onLast().performClick()
        assertEquals(3, settings.autoBetTwoSided)
        // Neither row touched the other's setting.
        assertEquals(5, settings.autoBetBooks)
    }

    @Test
    fun `the smallest edge offers Tj's seven choices and a typed amount`() {
        show()
        for ((label, ev) in listOf("+2%" to 0.02, "+2.5%" to 0.025, "+3%" to 0.03, "+3.25%" to 0.0325, "+3.5%" to 0.035, "+3.75%" to 0.0375, "+4%" to 0.04)) {
            compose.onNodeWithText(label).performClick()
            assertEquals(label, ev, settings.autoBetMinEv, 1e-12)
            compose.onNodeWithText(label).assertIsSelected()
        }
        // Typed: 3.1% is a value no chip has.
        compose.onNodeWithTag("autoBetMinEvField").performTextClearance()
        compose.onNodeWithTag("autoBetMinEvField").performTextInput("3.1")
        assertEquals(0.031, settings.autoBetMinEv, 1e-12)
        // Under the floor of 0.5% isn't taken (and the field says so).
        compose.onNodeWithTag("autoBetMinEvField").performTextClearance()
        compose.onNodeWithTag("autoBetMinEvField").performTextInput("0.2")
        assertEquals(0.031, settings.autoBetMinEv, 1e-12)
        compose.onNodeWithText("At least 0.5%").assertExists()
    }

    @Test
    fun `the amount per bet offers an eighth, quarter and half Kelly, a dollar, and a typed amount`() {
        show()
        for ((label, stake) in listOf("⅛ Kelly" to AutoBetStake.EIGHTH_KELLY, "¼ Kelly" to AutoBetStake.QUARTER_KELLY, "½ Kelly" to AutoBetStake.HALF_KELLY, "$1" to AutoBetStake.ONE_DOLLAR)) {
            compose.onNodeWithText(label).performClick()
            assertEquals(stake, settings.autoBetStake)
            compose.onNodeWithTag("autoBetCustomStake").assertDoesNotExist()
        }
        // Kelly says what it works from.
        compose.onNodeWithText("½ Kelly").performClick()
        compose.onNodeWithText("Kelly sizing uses your bankroll", substring = true).assertExists()
        // My amount: a field for it.
        compose.onNodeWithText("My amount").performClick()
        assertEquals(AutoBetStake.CUSTOM, settings.autoBetStake)
        compose.onNodeWithTag("autoBetCustomStake").performTextClearance()
        compose.onNodeWithTag("autoBetCustomStake").performTextInput("7.25")
        assertEquals(7.25, settings.autoBetCustomStake, 0.0)
    }

    /** Tj, 2026-10-01: "add an option for longest odds of any auto bet. For example, I don't want it to bet anything that is more of a longshot than +130". */
    @Test
    fun `the longest odds is a preset or typed, has no limit until picked, and the confirm says it`() {
        show()
        assertEquals("no limit by default: what ran before doesn't change", 0, settings.autoBetMaxOdds)
        assertFalse(AutoBetText.criteria(settings).contains("odds no longer than"))
        compose.onNodeWithText("Longest odds to bet").assertExists()
        for ((label, odds) in listOf("+100" to 100, "+110" to 110, "+120" to 120, "+130" to 130, "+150" to 150, "+200" to 200, "+300" to 300)) {
            compose.onNodeWithText(label).performClick()
            assertEquals(label, odds, settings.autoBetMaxOdds)
        }
        compose.onNodeWithText("+130").performClick()
        assertTrue(AutoBetText.criteria(settings).contains(", odds no longer than +130, staking"))
        compose.onNodeWithTag("autoBetSwitch").performClick()
        compose.onNodeWithText("odds no longer than +130", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        // Typed.
        compose.onNodeWithTag("autoBetMaxOddsField").performTextClearance()
        compose.onNodeWithTag("autoBetMaxOddsField").performTextInput("175")
        assertEquals(175, settings.autoBetMaxOdds)
        // Under +100 isn't saved (the last good one stands), and the field says why.
        compose.onNodeWithTag("autoBetMaxOddsField").performTextClearance()
        compose.onNodeWithTag("autoBetMaxOddsField").performTextInput("50")
        assertEquals(175, settings.autoBetMaxOdds)
        compose.onNodeWithText("(even money) or more", substring = true).assertExists()
        // No limit puts it back.
        compose.onNodeWithText("No limit").performClick()
        assertEquals(0, settings.autoBetMaxOdds)
        // The card says what Kelly does with longshots and what the limit adds.
        compose.onNodeWithTag("autoBetMaxOddsHint").assertExists()
    }

    @Test
    fun `Kelly's note says a longer price stakes less but the odds aren't capped`() {
        show({ it.copy(autoBetStake = AutoBetStake.QUARTER_KELLY) })
        val note = AutoBetText.kellyNote(settings)!!
        assertTrue(note, note.contains("a longer price stakes less"))
        assertTrue(note, note.contains("nothing caps the odds itself"))
    }

    /** Tj, 2026-10-01: "Make a push notification for every automatic bet, so I can see each bet placed and the stake and EV." */
    @Test
    fun `the card says every bet gets a notification with the stake and EV, and a test sends one`() {
        var sent = 0
        show(onTest = { sent++; true })
        compose.onNodeWithText("Every bet auto-bet places gets its own pop-up notification", substring = true).assertExists()
        compose.onNodeWithText("the stake and the EV in the title", substring = true).assertExists()
        compose.onNodeWithTag("autoBetTestNoteResult").assertDoesNotExist()
        compose.onNodeWithTag("autoBetTestNote").performScrollTo().performClick()
        assertEquals(1, sent)
        compose.onNodeWithTag("autoBetTestNoteResult").assertExists()
        compose.onNodeWithText("a test bet should pop up now", substring = true).assertExists()
    }

    @Test
    fun `a notification Android won't show is said on the card while auto-bet is on, and a test that couldn't be sent says so`() {
        show({ it.copy(autoBet = true) }, blocked = "Notifications are switched off for Vigilant (Android Settings › Apps › Vigilant › Notifications)", onTest = { false })
        compose.onNodeWithTag("autoBetNotifBlocked").assertExists()
        compose.onNodeWithText("its notifications can't show: Notifications are switched off for Vigilant", substring = true).assertExists()
        compose.onNodeWithTag("autoBetTestNote").performScrollTo().performClick()
        compose.onNodeWithText("Couldn't send it", substring = true).assertExists()
        // Off, or nothing blocking: no warning.
        show({ it.copy(autoBet = false) }, blocked = "x")
    }

    @Test
    fun `the most per bet is typed`() {
        show()
        compose.onNodeWithTag("autoBetMaxStake").performTextClearance()
        compose.onNodeWithTag("autoBetMaxStake").performTextInput("12.5")
        assertEquals(12.5, settings.autoBetMaxStake, 0.0)
    }

    @Test
    fun `the check interval is the background CNO scan's own, 5 sec to 40 min, and 5 sec says what it costs`() {
        show()
        for ((label, seconds) in listOf("5 sec" to 5, "15 sec" to 15, "30 sec" to 30, "1 min" to 60, "3 min" to 180, "5 min" to 300, "40 min" to 2400)) {
            compose.onNodeWithText(label).performClick()
            assertEquals(label, seconds, settings.autoScanSeconds)
        }
        compose.onNodeWithText("The same choice as Settings › Scan › Background auto-scan", substring = true).assertExists()
        // 5 sec: its cost is said (CNO reads ~12 a minute, may block addresses); 15 sec and slower: no warning.
        compose.onNodeWithText("5 sec").performClick()
        compose.onNodeWithTag("autoBetFastNote").assertExists()
        compose.onNodeWithText("about 12 times a minute", substring = true).assertExists()
        compose.onNodeWithText("15 sec").performClick()
        compose.onNodeWithTag("autoBetFastNote").assertDoesNotExist()
    }

    // ---- what it says while it runs, and the stops --------------------------------------------------------------

    @Test
    fun `it says why it can't run, and what it does when it can`() {
        show({ it.copy(autoBet = true) })
        compose.onNodeWithTag("autoBetRunning").assertExists()
        assertNull(AutoBetText.whyNotRunning(ui))
        assertTrue(AutoBetText.running(settings).contains("every 10 min"))
        // The scanner choice, the background scan and Pause each keep it from running.
        assertTrue(AutoBetText.whyNotRunning(ui.copy(settings = settings.copy(scanner = ScannerMode.VIGILANT)))!!.contains("Vigilant only"))
        assertTrue(AutoBetText.whyNotRunning(ui.copy(settings = settings.copy(autoScan = AutoScanMode.OFF)))!!.contains("Background auto-scan is off"))
        assertTrue(AutoBetText.whyNotRunning(ui.copy(settings = settings.copy(paused = true)))!!.contains("paused"))
    }

    @Test
    fun `after a lost order it stays stopped until Tj taps Resume`() {
        show({ it.copy(autoBet = true, autoBetHalted = "Novig didn't answer, and its lists don't show the order") })
        compose.onNodeWithTag("autoBetHalted").assertExists()
        compose.onNodeWithText("Nothing is placed until you do.", substring = true).assertExists()
        compose.onNodeWithTag("autoBetResume").performClick()
        assertNull(settings.autoBetHalted)
        compose.onNodeWithTag("autoBetHalted").assertDoesNotExist()
        compose.onNodeWithTag("autoBetResume").assertDoesNotExist()
        // Resuming doesn't switch it on or off.
        assertTrue(settings.autoBet)
    }

    @Test
    fun `the wallet and the last check are shown`() {
        show({ it.copy(autoBet = true) })
        ui = ui.copy(
            autoBetStatus = AutoBettor.Status(
                lastRunMs = System.currentTimeMillis() - 12_000, balance = 18.4, placedSinceStart = 3, stakedSinceStart = 12.0,
                last = AutoBettor.Report(looked = 6, passed = 2, placed = listOf(placedBet()), skipped = mapOf("its edge +2.10% is under your +3.00% minimum" to 4)),
            ),
        )
        compose.onNodeWithTag("autoBetWallet").assertExists()
        compose.onNodeWithText("Vigilant wallet \$25.00", substring = true).assertExists()
        compose.onNodeWithTag("autoBetStatus").assertExists()
        compose.onNodeWithText("placed 1 (\$1.00)", substring = true).assertExists()
        compose.onNodeWithText("Placed since Vigilant started: 3 bets, \$12.00", substring = true).assertExists()
    }

    private fun placedBet() = TrackedBet(
        "id", 1L, "NFL", "A @ B", 2L, "Moneyline", "A", "m", "o", 0.5, 0.5, 0.52, 0.04, 1.0, orderId = "o1", auto = true,
    )

    // ---- screenshots --------------------------------------------------------------------------------------------

    @Test
    fun `screenshot - auto-bet on, with its criteria and the last check`() {
        show({ it.copy(autoBet = true, autoBetBooks = 3, autoBetMinEv = 0.0325, autoBetStake = AutoBetStake.EIGHTH_KELLY, autoBetMaxStake = 10.0, autoScanSeconds = 15, autoScan = AutoScanMode.CNO) })
        ui = ui.copy(
            autoBetStatus = AutoBettor.Status(
                lastRunMs = System.currentTimeMillis() - 12_000, balance = 25.0, placedSinceStart = 2, stakedSinceStart = 9.5,
                last = AutoBettor.Report(looked = 6, passed = 2, placed = listOf(placedBet(), placedBet()), skipped = mapOf("its edge +2.10% is under your +3.25% minimum" to 3, "2 books say +EV on their own (you need 3)" to 1)),
            ),
        )
        compose.onNodeWithTag("autoBetSwitch").assertIsOn()
        compose.onRoot().captureRoboImage("screenshots/5k_settings_auto_bet.png")
    }

    @Test
    fun `screenshot - auto-bet stopped after a lost order`() {
        show({ it.copy(autoBet = true, autoBetHalted = "Novig didn't answer, and its lists don't show the order (connection reset). Nothing is assumed: open the Tracker and tap Sync with Novig in a minute, and check Novig before betting this again.") })
        compose.onRoot().captureRoboImage("screenshots/5k2_settings_auto_bet_stopped.png")
    }
}
