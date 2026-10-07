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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.AutoBetSection
import com.tjshea.vigilant.app.ui.AutoBetText
import com.tjshea.vigilant.app.ui.PropGuardText
import com.tjshea.vigilant.data.novig.trading.PropGuard
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
import androidx.compose.ui.test.assertTextContains
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
@Config(sdk = [35], qualifiers = "w393dp-h4200dp-xxhdpi")
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
        // Each row of digits has its own tag: books agreeing, then books pricing both sides.
        fun chip(row: String, label: String) = compose.onNode(androidx.compose.ui.test.hasText(label) and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(row)))
        for ((label, n) in listOf("2" to 2, "3" to 3, "4" to 4)) {
            chip("autoBetBooksChips", label).performScrollTo().performClick()
            assertEquals(n, settings.autoBetBooks)
        }
        compose.onNodeWithText("5+").performClick()
        assertEquals(5, settings.autoBetBooks)
        compose.onNodeWithText("5+").assertIsSelected()
        chip("autoBetTwoSidedChips", "1").performScrollTo().performClick()
        assertEquals(1, settings.autoBetTwoSided)
        chip("autoBetTwoSidedChips", "2").performClick()
        assertEquals(2, settings.autoBetTwoSided)
        chip("autoBetTwoSidedChips", "3").performClick()
        assertEquals(3, settings.autoBetTwoSided)
        // Neither row touched the other's setting.
        assertEquals(5, settings.autoBetBooks)
    }

    @Test
    fun `the smallest edge offers Tj's seven choices and a typed amount`() {
        show()
        for ((label, ev) in listOf("+2%" to 0.02, "+2.5%" to 0.025, "+3%" to 0.03, "+3.25%" to 0.0325, "+3.5%" to 0.035, "+3.75%" to 0.0375, "+4%" to 0.04)) {
            // The edge's own chips come first ("+2%" is also one of the sharp veto's bar further down the tab).
            compose.onAllNodesWithText(label)[0].performClick()
            assertEquals(label, ev, settings.autoBetMinEv, 1e-12)
            compose.onAllNodesWithText(label)[0].assertIsSelected()
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

    /** Tj, 2026-10-01: "require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books agree positive EV)". */
    @Test
    fun `a switch makes every book that prices both sides agree, off until turned on, and the criteria say it`() {
        show()
        assertFalse(settings.autoBetAllAgree)
        compose.onNodeWithTag("autoBetAllAgree").assertIsOff()
        compose.onNodeWithText("Off: the number above is the fewest books that must agree", substring = true).assertExists()
        assertTrue(AutoBetText.criteria(settings).contains("at least 3 books agreeing it's +EV on their own"))
        compose.onNodeWithTag("autoBetAllAgree").performScrollTo().performClick()
        assertTrue(settings.autoBetAllAgree)
        compose.onNodeWithTag("autoBetAllAgree").assertIsOn()
        compose.onNodeWithText("On: a bet passes only if EVERY book that prices both sides of it says +EV on its own", substring = true).assertExists()
        compose.onNodeWithText("A book that lists only one side", substring = true).assertExists()
        val said = AutoBetText.criteria(settings)
        assertTrue(said, said.contains("every book that prices both sides agreeing it's +EV on their own (and at least 3 of them)"))
        // The confirm for turning auto-bet on says it, too.
        assertTrue(AutoBetText.confirm(settings, 25.0).contains("every book that prices both sides agreeing"))
        // It's independent of the minimum chips, and goes off again.
        compose.onNodeWithText("5+").performClick()
        assertEquals(5, settings.autoBetBooks)
        assertTrue(settings.autoBetAllAgree)
        assertTrue(AutoBetText.criteria(settings).contains("(and at least 5 of them)"))
        compose.onNodeWithTag("autoBetAllAgree").performClick()
        assertFalse(settings.autoBetAllAgree)
    }

    @Test
    fun `the amount per bet offers an eighth, quarter and half Kelly, a dollar, and a typed amount`() {
        show()
        for ((label, stake) in listOf("⅛ Kelly" to AutoBetStake.EIGHTH_KELLY, "¼ Kelly" to AutoBetStake.QUARTER_KELLY, "½ Kelly" to AutoBetStake.HALF_KELLY, "$1" to AutoBetStake.ONE_DOLLAR)) {
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(stake, settings.autoBetStake)
            compose.onNodeWithTag("autoBetCustomStake").assertDoesNotExist()
        }
        // Kelly says what it works from.
        compose.onNodeWithText("½ Kelly").performScrollTo().performClick()
        compose.onNodeWithText("Kelly sizing uses your bankroll", substring = true).assertExists()
        // My amount: a field for it.
        compose.onNodeWithText("My amount").performScrollTo().performClick()
        assertEquals(AutoBetStake.CUSTOM, settings.autoBetStake)
        compose.onNodeWithTag("autoBetCustomStake").performScrollTo().performTextClearance()
        compose.onNodeWithTag("autoBetCustomStake").performTextInput("7.25")
        assertEquals(7.25, settings.autoBetCustomStake, 0.0)
    }

    /** Tj, 2026-10-07: "anywhere there are settings for … any number inputs that have options, also put a box where I can manually type in a number to set … make a shortest odds setting as well." */
    @Test
    fun `every number option on the tab has a box that sets exactly what was typed, shortest odds taking a minus or a plus`() {
        show({ it.copy(sharpAutoBet = com.tjshea.vigilant.data.scanner.SharpMode.VETO) })
        fun type(tag: String, text: String) {
            compose.onNodeWithTag(tag).performScrollTo().performTextClearance()
            compose.onNodeWithTag(tag).performTextInput(text)
            compose.waitForIdle()
        }
        type("autoBetBooksField", "9"); assertEquals(9, settings.autoBetBooks)
        assertEquals("the rule uses what was typed, not a clamp to 5", 9, com.tjshea.vigilant.data.novig.trading.AutoBet.rules(settings).minBooks)
        type("autoBetTwoSidedField", "6"); assertEquals(6, settings.autoBetTwoSided)
        assertEquals(6, com.tjshea.vigilant.data.novig.trading.AutoBet.rules(settings).twoSided)
        type("autoBetMinOddsField", "-180"); assertEquals(-180, settings.autoBetMinOdds)
        type("autoBetMinOddsField", "-50"); assertEquals("not odds: nothing changes", -180, settings.autoBetMinOdds)
        type("autoBetMinOddsField", "130"); assertEquals("underdogs only", 130, settings.autoBetMinOdds)
        assertEquals(130, com.tjshea.vigilant.data.novig.trading.AutoBet.rules(settings).minOdds)
        type("autoScanSecondsField", "45"); assertEquals(45, settings.autoScanSeconds)
        type("autoLockMinField", "1.5"); assertEquals(0.015, settings.autoLockMinPercent, 1e-9)
        type("sharpVetoMinEvField", "0.75"); assertEquals(0.0075, settings.sharpVetoMinEv, 1e-9)
    }

    /** DH4 (2026-10-07): "odds no shorter than +130 or longer" read as a favourite limit; a positive limit is plus money only. */
    @Test
    fun `the criteria say a plus money shortest odds means underdogs only, and a minus one a favorite limit`() {
        val base = ScanSettings(autoScan = AutoScanMode.CNO)
        val plus = AutoBetText.criteria(base.copy(autoBetMinOdds = 110))
        assertTrue(plus, plus.contains(", underdogs only (odds +110 or longer)"))
        assertFalse(plus, plus.contains("odds no shorter than"))
        val minus = AutoBetText.criteria(base.copy(autoBetMinOdds = -200))
        assertTrue(minus, minus.contains(", odds no shorter than −200"))
        assertFalse(minus, minus.contains("underdogs only"))
        val none = AutoBetText.criteria(base.copy(autoBetMinOdds = 0))
        assertFalse(none, none.contains("underdogs only") || none.contains("odds no shorter than"))
        // The favorite note counts the extra in points, not a bare "1 more".
        val one = AutoBetText.favouriteNote(base.copy(autoBetFavouriteExtraEv = 0.01))
        assertTrue(one, one.contains("1 point more than the"))
        val two = AutoBetText.favouriteNote(base.copy(autoBetFavouriteExtraEv = 0.02))
        assertTrue(two, two.contains("2 points more than the"))
    }

    /**
     * Tj, 2026-10-07: "most of the auto bet feature is betting nhl player shots on goal ... set up some type of guard for obscure auto betting on small props like this":
     * the small-prop guard's chips and boxes are on the Auto-bet tab, Tj's to move or turn off, and the tab says what the last day and week of auto-bets look like.
     */
    @Test
    fun `the small-prop guard has a share chip and box, a sample, a per-game limit, says what it does and what the last day looked like`() {
        show()
        fun type(tag: String, text: String) {
            compose.onNodeWithTag(tag).performScrollTo().performTextClearance()
            compose.onNodeWithTag(tag).performTextInput(text)
            compose.waitForIdle()
        }
        assertEquals("on by default: 25% of the day's auto-bets, judged from 8, 3 on a game", PropGuard.Rules(0.25, 8, 3), PropGuard.rules(settings))
        compose.onNodeWithText("Small-prop guard", ignoreCase = true).performScrollTo().assertExists()
        compose.onNodeWithTag("propGuardNote").performScrollTo().assertTextContains("no kind of player prop over 25%", substring = true)
        compose.onNodeWithTag("propGuardShares").assertTextContains("Last 24 hours:", substring = true)
        compose.onNodeWithText("35%").performScrollTo().performClick()
        assertEquals(0.35, settings.propGuardShare, 1e-9)
        compose.onNodeWithTag("propGuardNote").assertTextContains("over 35%", substring = true)
        type("propGuardShareField", "40"); assertEquals(0.40, settings.propGuardShare, 1e-9)
        type("propGuardShareField", "0"); assertEquals("0 turns the cap off", 0.0, settings.propGuardShare, 1e-9)
        compose.onNodeWithTag("propGuardNote").assertTextContains("at most 3 auto-bets on one kind of prop in one game", substring = true)
        type("propGuardSampleField", "12"); assertEquals(12, settings.propGuardMinSample)
        type("propGuardPerGameField", "2"); assertEquals(2, settings.propGuardPerGame)
        compose.onNodeWithTag("propGuardNote").assertTextContains("at most 2 auto-bets", substring = true)
        type("propGuardPerGameField", "0"); assertEquals("0 saves nothing: Off is its own chip", 2, settings.propGuardPerGame)
        compose.onNodeWithText("A whole number from 1 to 50").assertExists()
    }

    @Test
    fun `the guard's line counts the Tracker's auto-bets by kind of prop for the last day and week`() {
        val now = 1_800_000_000_000L
        fun bet(id: String, market: String, selection: String, league: String = "NHL", agoH: Double = 2.0) = com.tjshea.vigilant.data.tracker.TrackedBet(
            id = id, createdAtMs = now - (agoH * 3_600_000L).toLong(), league = league, eventName = "A @ B", startsTs = now + 3_600_000L, marketLabel = market, selection = selection,
            marketId = "m$id", outcomeId = "o$id", price = 0.4, cost = 0.4, fairAtBet = 0.43, evPercentAtBet = 0.04, stake = 2.0, auto = true, eventId = "e$id",
        )
        val bets = List(3) { bet("s$it", "Player Shots On Goal", "P$it Over 2.5") } + bet("y", "Player Receiving Yards", "J Over 50.5", league = "NFL") + bet("old", "Player Shots On Goal", "Z Over 1.5", agoH = 60.0)
        val line = PropGuardText.shares(bets, now)
        assertEquals("Last 24 hours: 4 auto-bets: NHL shots on goal 3 (75%), NFL receiving yards 1 (25%). Last 7 days: 5 auto-bets: NHL shots on goal 4 (80%), NFL receiving yards 1 (20%).", line)
        assertEquals("Last 24 hours: no auto-bets. Last 7 days: no auto-bets.", PropGuardText.shares(emptyList(), now))
    }

    @Test
    fun `a shortest odds longer than the longest says nothing can pass`() {
        show({ it.copy(autoBetMaxOdds = 130) })
        compose.onNodeWithTag("autoBetMinOddsField").performScrollTo().performTextClearance()
        compose.onNodeWithTag("autoBetMinOddsField").performTextInput("200")
        compose.onNodeWithTag("autoBetOddsRange").performScrollTo().assertTextContains("nothing can pass both", substring = true)
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
        assertTrue(AutoBetText.criteria(settings), AutoBetText.criteria(settings).contains(", odds no longer than +130, unless the sharpest book for it gives it under +1%, staking"))
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
        // No limit puts it back (the longest odds' chips come before the shortest odds' own "No limit").
        compose.onAllNodesWithText("No limit")[0].performClick()
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
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(label, seconds, settings.autoScanSeconds)
        }
        compose.onNodeWithText("the same setting as Settings › Scanning › Background scan: one choice, two places", substring = true).assertExists()
        // 5 sec: its cost is said (CNO reads ~12 a minute, may block addresses); 15 sec and slower: no warning.
        compose.onNodeWithText("5 sec").performScrollTo().performClick()
        compose.onNodeWithTag("autoBetFastNote").assertExists()
        compose.onNodeWithText("about 12 times a minute", substring = true).assertExists()
        compose.onNodeWithText("15 sec").performScrollTo().performClick()
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
        assertTrue(AutoBetText.whyNotRunning(ui.copy(settings = settings.copy(autoScan = AutoScanMode.OFF)))!!.contains("The background scan is off"))
        assertTrue(AutoBetText.whyNotRunning(ui.copy(settings = settings.copy(pausedByHand = true)))!!.contains("paused"))
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
    fun `unticking every kind of bet says the auto-bet places nothing, and ticking one back clears it`() {
        show()
        compose.onNodeWithTag("autoBetNoKinds").assertDoesNotExist()
        com.tjshea.vigilant.data.scanner.BetKind.entries.forEach { k -> compose.onNodeWithText(k.label).performScrollTo().performClick() }
        assertTrue(settings.autoBetKinds.isEmpty())
        compose.onNodeWithTag("autoBetNoKinds").performScrollTo().assertExists()
        compose.onNodeWithText(com.tjshea.vigilant.data.scanner.BetKind.PROP.label).performScrollTo().performClick()
        assertEquals(setOf(com.tjshea.vigilant.data.scanner.BetKind.PROP), settings.autoBetKinds)
        compose.onNodeWithTag("autoBetNoKinds").assertDoesNotExist()
    }

    @Test
    fun `screenshot - auto-bet stopped after a lost order`() {
        show({ it.copy(autoBet = true, autoBetHalted = "Novig didn't answer, and its lists don't show the order (connection reset). Nothing is assumed: open the Tracker and tap Sync with Novig in a minute, and check Novig before betting this again.") })
        compose.onRoot().captureRoboImage("screenshots/5k2_settings_auto_bet_stopped.png")
    }

    @Test
    fun `the trap guard is on the Auto-bet tab - games within 6 h and game lines Novig just moved by default, each switch changes the setting, the notes follow`() {
        show()
        compose.onNodeWithTag("autoBet-trapEarlyNote").performScrollTo().assertTextContains("more than 6 h", substring = true)
        compose.onNodeWithTag("autoBet-trapMoveNote").assertTextContains("Novig's own trades", substring = true)
        compose.onNodeWithTag("autoBet-trapEarly").performScrollTo()
        compose.onNodeWithText("24 h").performScrollTo().performClick()
        assertEquals(24, settings.trapEarlyHours)
        compose.onNodeWithTag("autoBet-trapEarlyNote").assertTextContains("more than 24 h", substring = true)
        compose.onNodeWithTag("autoBet-trapMove").performScrollTo().performClick()
        assertFalse(settings.trapNovigMove)
        compose.onNodeWithTag("autoBet-trapMoveNote").assertTextContains("Off", substring = true)
    }

    @Test
    fun `the favorite bar is under the shortest-odds chips - one point by default, a chip or a typed percent changes it, and the plus money chip says so (Tj, 2026-10-07)`() {
        show()
        compose.onNodeWithTag("autoBetFavouriteNote").performScrollTo().assertTextContains("needs", substring = true)
        compose.onNodeWithText("+1 point").performScrollTo().assertExists()
        compose.onNodeWithText("+2 points").performScrollTo().performClick()
        assertEquals(0.02, settings.autoBetFavouriteExtraEv, 1e-9)
        compose.onNodeWithText("None").performScrollTo().performClick()
        assertEquals(0.0, settings.autoBetFavouriteExtraEv, 1e-9)
        compose.onNodeWithTag("autoBetFavouriteNote").assertTextContains("Off", substring = true)
        compose.onNodeWithTag("autoBetFavouriteEvField").performScrollTo().performTextInput("1.5")
        assertEquals(0.015, settings.autoBetFavouriteExtraEv, 1e-9)
        compose.onNodeWithText("+100 or longer (plus money only)").performScrollTo().performClick()
        assertEquals(100, settings.autoBetMinOdds)
    }

    @Test
    fun `the first-listed rule has its own switch in the trap guard, on by default, with a note that follows it (Tj, 2026-10-07)`() {
        show()
        compose.onNodeWithTag("autoBet-trapFirstListedNote").performScrollTo().assertTextContains("first seen more than 6 h before", substring = true)
        compose.onNodeWithTag("autoBet-trapFirstListed").performScrollTo().performClick()
        assertFalse(settings.trapFirstListed)
        compose.onNodeWithTag("autoBet-trapFirstListedNote").assertTextContains("Off", substring = true)
        compose.onNodeWithTag("autoBet-trapFirstListed").performClick()
        assertTrue(settings.trapFirstListed)
    }

    @Test
    fun `every setting search sends to the Auto-bet tab is on it`() {
        ui = SampleScan.state().copy(betting = BettingUi(enabled = true, balance = 25.0), settings = ScanSettings(autoScan = AutoScanMode.CNO))
        compose.setContent {
            VigilantTheme(darkTheme = true) { com.tjshea.vigilant.app.ui.AutoBetScreen(ui, { t -> ui = ui.copy(settings = t(ui.settings)) }) }
        }
        compose.waitForIdle()
        for (e in com.tjshea.vigilant.app.ui.SettingsIndex.entries.filter { it.page == null && !it.bids && it.shown(settings) }) {
            assertTrue(
                "\"${e.title}\" should be on the Auto-bet tab",
                compose.onAllNodesWithText(e.title, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }
}
