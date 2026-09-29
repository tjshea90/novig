package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.tjshea.vigilant.app.ui.ApiBetActions
import com.tjshea.vigilant.app.ui.ApiBetButton
import com.tjshea.vigilant.app.ui.ApiBetSheetContent
import com.tjshea.vigilant.app.ui.BettingActions
import com.tjshea.vigilant.app.ui.LocalApiBet
import com.tjshea.vigilant.app.ui.NovigBettingSection
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.trading.BetPlan
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.MarketFee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Betting through Novig's API (Tj, 2026-09-29): the Settings section that sets it up and moves money, the Bet button, and the Bet sheet
 * that shows what will be bought and asks for the confirm.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h2000dp-xxhdpi")
class ApiBettingUiTest {

    @get:Rule val compose = createComposeRule()

    private fun screen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Column(Modifier.verticalScroll(rememberScrollState())) { content() } } }
    }

    /** The sheet scrolls on its own: no scroll around it. */
    private fun sheetScreen(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        VigilantTheme(darkTheme = true) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
    }

    private val market = NovigMarket("m", "e", "MONEY", "OPEN", "A vs B", 0, MarketFee.GAME, listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")))
    private val target = BetTarget(market, "A", "NFL", "Team B @ Team A", 0, "Moneyline", "Team A", 0.50, null, BetTracker.SOURCE_CNO)
    private val plan = BetPlan(limitPrice = 0.465, contracts = 400, expectedCost = 1.85, averagePrice = 0.4625, payout = 4.0, evPercent = 0.081, bestPrice = 0.46, note = null)
    private fun sheet(plan: BetPlan? = this.plan, refusal: String? = null, result: PlaceResult? = null, placing: Boolean = false, balance: Double? = 12.5) =
        BetSheetUi("Team A", "Moneyline · Team B @ Team A", target, stake = 5.0, resolving = false, plan = plan, refusal = refusal, placing = placing, result = result, balance = balance)

    // ---- Settings ---------------------------------------------------------------------------------------------

    @Test
    fun `betting is off until the management key is given, and the key never stays in the form`() {
        var enabled: Pair<String, String>? = null
        screen { NovigBettingSection(BettingUi(), ScanSettings(), BettingActions(onEnable = { id, pem -> enabled = id to pem }), {}) }
        compose.onNodeWithTag("enableBetting").assertIsNotEnabled()
        compose.onNodeWithTag("mgmtKeyId").performTextInput("mgmt-key-1234")
        compose.onNodeWithTag("mgmtKeyPem").performTextInput("-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----") // FAKE
        compose.onNodeWithTag("enableBetting").assertIsEnabled().performClick()
        assertEquals("mgmt-key-1234", enabled!!.first)
        assertTrue(enabled!!.second.contains("PRIVATE KEY"))
        // Cleared after use: the button is disabled again.
        compose.onNodeWithTag("enableBetting").assertIsNotEnabled()
    }

    @Test
    fun `with betting on, the wallet balance shows and money moves only with the management key`() {
        var moved: List<Any>? = null
        var synced = false
        var off = false
        var settings = ScanSettings()
        screen {
            NovigBettingSection(
                BettingUi(enabled = true, balance = 12.5), settings,
                BettingActions(onTransfer = { d, a, k, p -> moved = listOf(d, a, k, p) }, onSync = { synced = true }, onDisable = { off = true }),
                { t -> settings = t(settings) },
            )
        }
        compose.onNodeWithTag("bettingBalance").assertExists()
        compose.onNodeWithText("Betting is on · Vigilant wallet $12.50").assertExists()
        compose.onNodeWithTag("fundWallet").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("mgmtKeyId").performTextInput("mgmt-key-1234")
        compose.onNodeWithTag("mgmtKeyPem").performTextInput("-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----") // FAKE
        compose.onNodeWithTag("fundWallet").assertIsEnabled().performClick()
        assertEquals(listOf("fund", 10.0, "mgmt-key-1234"), moved!!.take(3))
        compose.onNodeWithText("Sync Tracker with Novig's fills").performScrollTo().performClick()
        compose.onNodeWithText("Turn betting off").performScrollTo().performClick()
        assertTrue(synced && off)
        // A limit chip changes the setting.
        compose.onNodeWithText("Any +EV").performScrollTo().performClick()
        assertEquals(0.0, settings.apiMinEv, 1e-9)
    }

    @Test
    fun `messages and errors from setup show`() {
        screen { NovigBettingSection(BettingUi(message = "Betting is set up.", error = "Novig said no"), ScanSettings(), BettingActions(), {}) }
        compose.onNodeWithTag("bettingMessage").assertExists()
        compose.onNodeWithTag("bettingError").assertExists()
    }

    // ---- the Bet button ---------------------------------------------------------------------------------------

    @Test
    fun `the Bet button isn't there until betting is set up`() {
        screen { ApiBetButton { } }
        compose.onNodeWithTag("apiBet").assertDoesNotExist()
    }

    @Test
    fun `with betting set up the Bet button hands back the card's actions`() {
        var got: ApiBetActions? = null
        val actions = ApiBetActions(true, {}, {})
        screen { CompositionLocalProvider(LocalApiBet provides actions) { ApiBetButton { got = it } } }
        compose.onNodeWithTag("apiBet").performClick()
        assertTrue(got === actions)
    }

    @Test
    fun `betting turned off in the actions hides the button too`() {
        screen { CompositionLocalProvider(LocalApiBet provides ApiBetActions(false, {}, {})) { ApiBetButton { } } }
        compose.onNodeWithTag("apiBet").assertDoesNotExist()
    }

    // ---- the Bet sheet ----------------------------------------------------------------------------------------

    @Test
    fun `the sheet shows what will be bought, and only the confirm places it`() {
        var confirmed = 0
        sheetScreen { ApiBetSheetContent(sheet(), {}, { confirmed++ }, {}, {}, {}) }
        compose.onNodeWithText("Team A").assertExists()
        compose.onNodeWithText("You pay").assertExists()
        compose.onNodeWithText("$1.85").assertExists()
        compose.onNodeWithText("+8.1%").assertExists()
        assertEquals(0, confirmed)
        compose.onNodeWithTag("confirmBet").assertIsEnabled().performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `a wallet that's too small blocks the bet and says where to add money`() {
        sheetScreen { ApiBetSheetContent(sheet(balance = 1.0), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("confirmBet").assertIsNotEnabled()
        compose.onNodeWithText("add money in Settings", substring = true).assertExists()
    }

    @Test
    fun `a refused bet says why and offers to look again, or to bet it again`() {
        var again = 0
        var repeat = 0
        sheetScreen { ApiBetSheetContent(sheet(plan = null, refusal = "You've already bet this through the API and it's still open"), {}, {}, { again++ }, { repeat++ }, {}) }
        compose.onNodeWithTag("betRefusal").assertExists()
        compose.onNodeWithText("Look again").performClick()
        compose.onNodeWithText("Bet it again").performClick()
        assertEquals(1, again)
        assertEquals(1, repeat)
        compose.onNodeWithTag("confirmBet").assertDoesNotExist()
    }

    @Test
    fun `the result says what happened`() {
        val bet = TrackedBet(
            "x", 0, "NFL", "Team B @ Team A", 0, "Moneyline", "Team A", "m", "A", 0.4625, 0.4625, 0.5, 0.081, 1.85, orderId = "o1", contracts = 400, paid = 1.85, fee = 0.0,
        )
        sheetScreen { ApiBetSheetContent(sheet(result = PlaceResult.Placed(bet, unfilledContracts = 0)), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("betPlaced").assertExists()
        compose.onNodeWithText("400 contracts for $1.85", substring = true).assertExists()
        compose.onNodeWithTag("betDone").assertExists()
    }

    @Test
    fun `not placed, refused and unconfirmed results are told apart`() {
        sheetScreen { ApiBetSheetContent(sheet(result = PlaceResult.NotFilled("Nobody was selling at that price any more")), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("betNotPlaced").assertExists()
        compose.onNodeWithText("Nobody was selling", substring = true).assertExists()
    }

    // ---- pictures (written to app/screenshots/ with -Pscreenshots) ---------------------------------------------

    @Test
    fun `screenshots - Settings section and the Bet sheet`() {
        screen { NovigBettingSection(BettingUi(enabled = true, balance = 12.5, message = "Added $10.00. The subaccount now holds $12.50."), ScanSettings(), BettingActions(), {}) }
        compose.onRoot().captureRoboImage("screenshots/5f_settings_api_betting.png")
    }

    @Test
    fun `screenshots - the Bet sheet before and after placing`() {
        sheetScreen { ApiBetSheetContent(sheet(plan = plan.copy(note = "Only $1.85 of the $5.00 is offered at a positive edge right now: this bets $1.85.")), {}, {}, {}, {}, {}) }
        compose.onRoot().captureRoboImage("screenshots/4f_api_bet_sheet.png")
    }
}
