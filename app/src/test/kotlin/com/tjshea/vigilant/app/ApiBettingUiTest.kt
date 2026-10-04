package com.tjshea.vigilant.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.ManagementKeyHint
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
    private fun sheetScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        swapped = content
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Keyed by the swap count, so a swapped-in sheet starts with none of the last one's remembered state.
                    androidx.compose.runtime.key(swaps) { swapped?.invoke() }
                }
            }
        }
    }

    /**
     * Another screen in the same test: the rule's content is set once, so this swaps what an already-set host shows.
     * (A mutable holder the one [sheetScreen] reads.)
     */
    private var swapped by mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)
    private var swaps by mutableStateOf(0)

    private fun showSheet(content: @androidx.compose.runtime.Composable () -> Unit) = if (swapped == null) sheetScreen(content) else sheetScreenFresh(content)

    private fun sheetScreenFresh(content: @androidx.compose.runtime.Composable () -> Unit) {
        if (swapped == null) throw IllegalStateException("call sheetScreen first")
        swaps++
        swapped = content
        compose.waitForIdle()
    }

    private val market = NovigMarket("m", "e", "MONEY", "OPEN", "A vs B", 0, MarketFee.GAME, listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")))
    private val target = BetTarget(market, "A", "NFL", "Team B @ Team A", 0, "Moneyline", "Team A", 0.50, null, BetTracker.SOURCE_CNO)
    private val plan = BetPlan(limitPrice = 0.465, contracts = 400, expectedCost = 1.85, averagePrice = 0.4625, payout = 4.0, evPercent = 0.081, bestPrice = 0.46, note = null)
    private val saved = ManagementKeyHint("5678", 0L)
    private fun sheet(plan: BetPlan? = this.plan, refusal: String? = null, result: PlaceResult? = null, placing: Boolean = false, balance: Double? = 12.5) =
        BetSheetUi("Team A", "Moneyline · Team B @ Team A", target, stake = 5.0, resolving = false, plan = plan, refusal = refusal, placing = placing, result = result, balance = balance)

    // ---- Settings ---------------------------------------------------------------------------------------------

    @Test
    fun `betting is off until the management key is given, and the key never stays in the form`() {
        var enabled: ManagementKey? = null
        screen { NovigBettingSection(BettingUi(), ScanSettings(), BettingActions(onEnable = { typed -> enabled = typed }), {}) }
        compose.onNodeWithTag("enableBetting").assertIsNotEnabled()
        compose.onNodeWithTag("mgmtKeyId").performTextInput("mgmt-key-1234")
        compose.onNodeWithTag("mgmtKeyPem").performTextInput("-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----") // FAKE
        compose.onNodeWithTag("enableBetting").assertIsEnabled().performClick()
        assertEquals("mgmt-key-1234", enabled!!.keyId)
        assertTrue(enabled!!.pem.contains("PRIVATE KEY"))
        // Cleared after use: the button is disabled again.
        compose.onNodeWithTag("enableBetting").assertIsNotEnabled()
    }

    @Test
    fun `with a saved management key, betting turns on without typing anything`() {
        var enabled = 0
        var sent: ManagementKey? = ManagementKey("x", "y")
        screen { NovigBettingSection(BettingUi(), ScanSettings(), BettingActions(onEnable = { typed -> enabled++; sent = typed }), {}, savedKey = saved) }
        compose.onNodeWithTag("savedMgmtKey").assertExists()
        compose.onNodeWithTag("mgmtKeyId").assertDoesNotExist()
        compose.onNodeWithTag("enableBetting").assertIsEnabled().performClick()
        assertEquals(1, enabled)
        assertEquals(null, sent) // null = the saved key
    }

    @Test
    fun `with betting on, the wallet balance shows and money moves only with the management key`() {
        var moved: List<Any?>? = null
        var synced = false
        var off = false
        var settings = ScanSettings()
        screen {
            NovigBettingSection(
                BettingUi(enabled = true, balance = 12.5), settings,
                BettingActions(onTransfer = { d, a, k -> moved = listOf(d, a, k) }, onSync = { synced = true }, onDisable = { off = true }),
                { t -> settings = t(settings) },
            )
        }
        compose.onNodeWithTag("bettingBalance").assertExists()
        compose.onNodeWithText("Betting is on · Vigilant wallet $12.50").assertExists()
        compose.onNodeWithTag("fundWallet").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("mgmtKeyId").performTextInput("mgmt-key-1234")
        compose.onNodeWithTag("mgmtKeyPem").performTextInput("-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----") // FAKE
        compose.onNodeWithTag("fundWallet").assertIsEnabled().performClick()
        assertEquals(listOf("fund", 10.0), moved!!.take(2))
        assertEquals("mgmt-key-1234", (moved!![2] as ManagementKey).keyId)
        compose.onNodeWithText("Sync Tracker with Novig's fills").performScrollTo().performClick()
        compose.onNodeWithText("Turn betting off").performScrollTo().performClick()
        assertTrue(synced && off)
        // A limit chip changes the setting.
        compose.onNodeWithText("$250.00").performScrollTo().performClick()
        assertEquals(250.0, settings.apiMaxPerDay, 1e-9)
        // No minimum edge for a bet placed by hand (Tj, 2026-10-02): the chips are gone and the section says so.
        compose.onAllNodesWithText("Smallest edge a bet is still placed at").assertCountEquals(0)
        compose.onAllNodesWithText("Any +EV").assertCountEquals(0)
        compose.onNodeWithText("Bets you place yourself from a Bet sheet have no minimum edge", substring = true).assertExists()
    }

    // ---- the per-game limit: chips and any amount typed (Tj, 2026-10-04: "add $5 and a manual entry") ---------------------------------------

    private val perGame = hasAnyAncestor(hasTestTag("perGameLimit"))
    private fun chip(label: String) = compose.onNode(hasText(label) and perGame)
    private fun field() = compose.onNodeWithTag("perGameLimitField")

    /** What the field holds (its editable text, not its label, prefix or hint). */
    private fun assertTyped(expected: String) = field().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)))

    private fun showPerGame(start: ScanSettings = ScanSettings(), onChange: (ScanSettings) -> Unit = {}): () -> ScanSettings {
        var settings by mutableStateOf(start)
        screen { NovigBettingSection(BettingUi(enabled = true, balance = 12.5), settings, BettingActions(), { t -> settings = t(settings); onChange(settings) }) }
        return { settings }
    }

    @Test
    fun `the most at risk on one game is on by default and has chips for $5 to $100 and No limit`() {
        val now = showPerGame()
        assertEquals("on by default (Tj, 2026-10-04: one event is one risk)", 25.0, now().apiMaxPerGame, 1e-9)
        compose.onNodeWithText("Most at risk on one game", substring = true).performScrollTo().assertExists()
        listOf("$5.00", "$10.00", "$25.00", "$50.00", "$100.00", "No limit").forEach { chip(it).assertExists() }
        chip("$25.00").assertIsSelected()
        chip("$5.00").performScrollTo().performClick()
        assertEquals(5.0, now().apiMaxPerGame, 1e-9)
        chip("$5.00").assertIsSelected()
        chip("No limit").performScrollTo().performClick()
        assertEquals(0.0, now().apiMaxPerGame, 1e-9)
        chip("No limit").assertIsSelected()
        compose.onNodeWithText("Auto-bet and auto-make never take a game past it", substring = true).assertExists()
    }

    @Test
    fun `any amount can be typed as the most on one game - dollars and cents - and it is saved as it is typed`() {
        val now = showPerGame()
        field().performScrollTo().performTextClearance()
        field().performTextInput("7.5")
        assertEquals(7.5, now().apiMaxPerGame, 1e-9)
        field().performTextClearance()
        field().performTextInput("12.50")
        assertEquals(12.5, now().apiMaxPerGame, 1e-9)
        field().performTextClearance()
        field().performTextInput("2000")
        assertEquals(2000.0, now().apiMaxPerGame, 1e-9)
        // A typed amount no chip has leaves every chip unselected.
        listOf("$5.00", "$10.00", "$25.00", "$50.00", "$100.00", "No limit").forEach { chip(it).assertIsNotSelected() }
    }

    @Test
    fun `an amount that can't be the limit isn't saved and the field says why`() {
        val changes = mutableListOf<ScanSettings>()
        val now = showPerGame { changes += it }
        field().performScrollTo().performTextClearance()
        field().performTextInput("0")
        assertEquals("0 is not a limit: No limit is its own chip", 25.0, now().apiMaxPerGame, 1e-9)
        compose.onNodeWithText("Pick No limit above to turn the limit off").assertExists()
        field().performTextClearance()
        field().performTextInput("12.345")
        assertEquals(25.0, now().apiMaxPerGame, 1e-9)
        compose.onNodeWithText("Dollars and cents only (two decimal places)").assertExists()
        field().performTextClearance()
        field().performTextInput("99999999")
        assertEquals(25.0, now().apiMaxPerGame, 1e-9)
        compose.onNodeWithText("At most $10,000 at a time").assertExists()
        // Letters never get in; clearing the field changes nothing (the last good limit stands).
        field().performTextClearance()
        field().performTextInput("abc")
        assertTyped("")
        assertEquals(25.0, now().apiMaxPerGame, 1e-9)
        assertTrue("nothing was saved while those were typed: $changes", changes.isEmpty())
    }

    @Test
    fun `the field shows what is saved - a whole amount, an amount with cents, and nothing for No limit - and follows a chip`() {
        val odd = showPerGame(ScanSettings(apiMaxPerGame = 7.5))
        field().performScrollTo()
        assertTyped("7.50")
        listOf("$5.00", "$10.00", "$25.00", "$50.00", "$100.00", "No limit").forEach { chip(it).assertIsNotSelected() }
        chip("$100.00").performScrollTo().performClick()
        assertEquals(100.0, odd().apiMaxPerGame, 1e-9)
        assertTyped("100")
        chip("No limit").performScrollTo().performClick()
        assertTyped("")
        compose.onNodeWithText("No limit: any amount on one game", substring = true).assertExists()
    }

    @Test
    fun `any amount can be typed in, and only a sendable one moves money`() {
        var moved: Pair<String, Double>? = null
        screen {
            NovigBettingSection(BettingUi(enabled = true, balance = 12.5), ScanSettings(), BettingActions(onTransfer = { d, a, _ -> moved = d to a }), {}, savedKey = saved)
        }
        val field = compose.onNodeWithTag("walletAmount").performScrollTo()
        field.performTextClearance()
        field.performTextInput("37.25")
        compose.onNodeWithText("Add $37.25 to the wallet").assertExists()
        compose.onNodeWithTag("fundWallet").assertIsEnabled().performClick()
        assertEquals("fund" to 37.25, moved)
        // More than the wallet holds can't be taken back.
        compose.onNodeWithTag("defundWallet").assertIsNotEnabled()
        compose.onNodeWithText("that's the most you can take back", substring = true).assertExists()
        field.performTextClearance()
        field.performTextInput("5")
        compose.onNodeWithTag("defundWallet").assertIsEnabled().performClick()
        assertEquals("defund" to 5.0, moved)
        // Not an amount: both buttons off, and the field says why.
        field.performTextClearance()
        field.performTextInput("12.345")
        compose.onNodeWithTag("fundWallet").assertIsNotEnabled()
        compose.onNodeWithText("Dollars and cents only (two decimal places)").assertExists()
        field.performTextClearance()
        field.performTextInput("50000")
        compose.onNodeWithTag("fundWallet").assertIsNotEnabled()
        // A quick-amount chip types its amount in.
        compose.onNodeWithTag("walletChip-20").performScrollTo().performClick()
        compose.onNodeWithText("Add $20.00 to the wallet").assertExists()
    }

    @Test
    fun `a saved key moves money with no typing, and can be replaced or forgotten`() {
        val moved = mutableListOf<ManagementKey?>()
        var forgot = 0
        var savedNow: ManagementKey? = null
        screen {
            NovigBettingSection(
                BettingUi(enabled = true, balance = 12.5), ScanSettings(),
                BettingActions(onTransfer = { _, _, k -> moved += k }, onForgetKey = { forgot++ }, onSaveKey = { savedNow = it }), {}, savedKey = saved,
            )
        }
        compose.onNodeWithText("✓ Management key ••••5678 saved on this phone").performScrollTo()
        compose.onNodeWithTag("mgmtKeyId").assertDoesNotExist()
        compose.onNodeWithTag("fundWallet").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf<ManagementKey?>(null), moved)
        // Replace: the fields show, and the typed key is what's sent (and saved).
        compose.onNodeWithTag("replaceMgmtKey").performClick()
        compose.onNodeWithTag("fundWallet").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("mgmtKeyId").performScrollTo().performTextInput("mgmt-key-new-9999")
        compose.onNodeWithTag("mgmtKeyPem").performTextInput("-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----") // FAKE
        compose.onNodeWithTag("saveMgmtKey").performScrollTo().assertIsEnabled().performClick()
        assertEquals("mgmt-key-new-9999", savedNow!!.keyId)
        // Back to the saved key's row; Forget asks the controller.
        compose.onNodeWithTag("forgetMgmtKey").performScrollTo().performClick()
        assertEquals(1, forgot)
    }

    @Test
    fun `a saved key this phone can't unlock asks for it once more`() {
        screen { NovigBettingSection(BettingUi(enabled = true, balance = 1.0), ScanSettings(), BettingActions(), {}, savedKey = saved.copy(unreadable = true)) }
        compose.onNodeWithText("can't unlock it any more", substring = true).performScrollTo()
        compose.onNodeWithTag("mgmtKeyId").assertExists()
        compose.onNodeWithTag("fundWallet").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `arriving from a bet the wallet can't cover, the shortfall is typed in and the bet is one tap away`() {
        var back = 0
        var notNow = 0
        var moved: Double? = null
        val top = TopUp(amount = 1.0, needed = 0.85, cost = 1.85, bet = sheet())
        screen {
            NovigBettingSection(
                BettingUi(enabled = true, balance = 1.0, topUp = top), ScanSettings(),
                BettingActions(onTransfer = { _, a, _ -> moved = a }, onBackToBet = { back++ }, onDismissTopUp = { notNow++ }), {}, savedKey = saved,
            )
        }
        compose.onNodeWithTag("topUpBanner").assertExists()
        compose.onNodeWithText("Your bet on Team A costs $1.85 and the wallet holds $1.00", substring = true).assertExists()
        compose.onNodeWithText("Add $1.00 to the wallet").performScrollTo().performClick()
        assertEquals(1.0, moved!!, 1e-9)
        compose.onNodeWithTag("backToBet").performScrollTo().performClick()
        compose.onNodeWithTag("dismissTopUp").performClick()
        assertEquals(1, back)
        assertEquals(1, notNow)
    }

    @Test
    fun `once the wallet covers the bet the banner says so`() {
        val top = TopUp(amount = 1.0, needed = 0.85, cost = 1.85, bet = sheet())
        screen { NovigBettingSection(BettingUi(enabled = true, balance = 2.0, topUp = top), ScanSettings(), BettingActions(), {}, savedKey = saved) }
        compose.onNodeWithText("The wallet now covers your bet on Team A ($1.85).").assertExists()
    }

    @Test
    fun `connecting the Novig key again uses the saved management key, or a typed one`() {
        val sent = mutableListOf<ManagementKey?>()
        screen { com.tjshea.vigilant.app.ui.NovigKeySection(NovigUi(managementKey = saved), onConnect = { sent += it }, onTest = {}, onDisconnect = {}) }
        compose.onNodeWithTag("savedMgmtKey").assertExists()
        compose.onNodeWithTag("novigConnect").assertIsEnabled().performClick()
        assertEquals(listOf<ManagementKey?>(null), sent)
        compose.onNodeWithTag("replaceMgmtKey").performClick()
        compose.onNodeWithTag("novigConnect").assertIsNotEnabled()
        compose.onNodeWithTag("mgmtKeyId").performTextInput("mgmt-key-new-9999")
        compose.onNodeWithTag("mgmtKeyPem").performTextInput("-----BEGIN PRIVATE KEY-----abc-----END PRIVATE KEY-----") // FAKE
        compose.onNodeWithTag("novigConnect").assertIsEnabled().performClick()
        assertEquals("mgmt-key-new-9999", sent[1]!!.keyId)
        // Connect has no separate Save: Novig accepting the key is what saves it.
        compose.onNodeWithTag("saveMgmtKey").assertDoesNotExist()
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

    /** Tj, 2026-09-30: "I can type in a custom account for any bet manually". */
    @Test
    fun `any amount can be typed for the bet, up to the per-bet limit`() {
        val typed = mutableListOf<Double>()
        val picked = mutableListOf<Double>()
        sheetScreen { ApiBetSheetContent(sheet(), { picked += it }, {}, {}, {}, {}, onTypeStake = { typed += it }) }
        compose.onNodeWithTag("betAmount").assertExists()
        compose.onNodeWithTag("betAmount").performTextClearance()
        compose.onNodeWithTag("betAmount").performTextInput("3.75")
        assertEquals(3.75, typed.last(), 0.0)
        // Over the limit ($10 here): said, and nothing is sent.
        val before = typed.size
        compose.onNodeWithTag("betAmount").performTextClearance()
        compose.onNodeWithTag("betAmount").performTextInput("25")
        assertEquals(before, typed.size)
        compose.onNodeWithText("Over your $10.00 limit per bet (Settings › Betting & Novig account)").assertExists()
        assertTrue(picked.isEmpty())
    }

    /** Tj, 2026-10-01: "Make sure it enters the Kelley value if I select it": the sheet says where its amount came from. */
    @Test
    fun `a sheet opened at the bet's Kelly stake says so, until another amount is picked`() {
        val note = "¼ Kelly of your $1,000.00 bankroll at this bet's odds"
        sheetScreen { ApiBetSheetContent(sheet().copy(stake = 4.37, baseStake = 4.37, stakeNote = note), {}, {}, {}, {}, {}) }
        compose.onNodeWithText(note).assertExists()
        compose.onNodeWithTag("betAmount").assert(hasText("4.37", substring = true))
    }

    @Test
    fun `a picked amount drops the Kelly note`() {
        sheetScreen { ApiBetSheetContent(sheet().copy(stake = 2.0, baseStake = 4.37, stakeNote = "¼ Kelly of your $1,000.00 bankroll at this bet's odds", stakeChosen = true), {}, {}, {}, {}, {}) }
        compose.onNodeWithText("¼ Kelly", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a sheet opened at what's left in the wallet says so`() {
        sheetScreen { ApiBetSheetContent(sheet(balance = 0.63).copy(stake = 0.63), {}, {}, {}, {}, {}) }
        compose.onNodeWithText("All that's left in the wallet").assertExists()
    }

    @Test
    fun `a wallet that's too small blocks the bet and opens the Add money block with what the bet is short by`() {
        val settings = mutableListOf<Double>()
        sheetScreen { ApiBetSheetContent(sheet(balance = 1.0), {}, {}, {}, {}, {}, onAddMoney = { settings += it }) }
        compose.onNodeWithTag("confirmBet").assertIsNotEnabled()
        compose.onNodeWithTag("walletShort").assertExists()
        // Open already, $0.85 short, so $1 is typed in.
        compose.onNodeWithTag("addMoneyBlock").performScrollTo().assertExists()
        compose.onNodeWithTag("sheetMoneyAmount").assertTextContains("1")
        // No management key saved on this phone: the button goes to Settings with the amount, where the key is typed once.
        compose.onNodeWithTag("sheetFund").performScrollTo().performClick()
        assertEquals(listOf(1.0), settings)
        compose.onNodeWithText("Opens Settings with the amount filled in", substring = true).assertExists()
    }

    /** Tj, 2026-10-01: "Right now if I have one cent, there is no option to add money in the bet slip." */
    @Test
    fun `every Bet sheet has an Add money button, whatever the wallet holds`() {
        // A wallet that covers the bet, a wallet of one cent, one of nothing, and one not read yet: the button is there each time, and says what's in it.
        for ((balance, said) in listOf(12.5 to "\$12.50 in it", 0.01 to "\$0.01 in it", 0.0 to "\$0.00 in it", null to "Add money to the wallet")) {
            showSheet { ApiBetSheetContent(sheet(balance = balance), {}, {}, {}, {}, {}) }
            compose.onNodeWithTag("addMoney").performScrollTo().assertExists()
            compose.onNodeWithText(said, substring = true).assertExists()
        }
    }

    @Test
    fun `the Add money button opens the amounts 1, 2, 5, 10, 15 and 20 and a typed amount`() {
        sheetScreen { ApiBetSheetContent(sheet(balance = 12.5), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("addMoneyBlock").assertDoesNotExist()
        compose.onNodeWithTag("addMoney").performScrollTo().performClick()
        compose.onNodeWithTag("addMoneyBlock").assertExists()
        assertEquals(listOf(1.0, 2.0, 5.0, 10.0, 15.0, 20.0), com.tjshea.vigilant.app.ui.SHEET_MONEY_CHOICES)
        for (c in listOf("1", "2", "5", "10", "15", "20")) compose.onNodeWithTag("sheetMoney-$c").performScrollTo().assertExists()
        compose.onNodeWithTag("sheetMoneyAmount").assertExists()
        // The button hides it again.
        compose.onNodeWithTag("addMoney").performClick()
        compose.onNodeWithTag("addMoneyBlock").assertDoesNotExist()
    }

    @Test
    fun `with a saved management key a chosen amount is added right there, one chip or one typed amount at a time`() {
        val funded = mutableListOf<Double>()
        val toSettings = mutableListOf<Double>()
        sheetScreen { ApiBetSheetContent(sheet(balance = 12.5), {}, {}, {}, {}, {}, onAddMoney = { toSettings += it }, onFundWallet = { funded += it }, keySaved = true) }
        compose.onNodeWithTag("addMoney").performScrollTo().performClick()
        compose.onNodeWithText("Moves money from your Novig cash wallet to the Vigilant wallet.").assertExists()
        // Picking a chip only chooses the amount: money moves on the button.
        compose.onNodeWithTag("sheetMoney-15").performScrollTo().performClick()
        assertTrue(funded.isEmpty())
        compose.onNodeWithText("Add \$15.00 to the wallet").assertExists()
        compose.onNodeWithTag("sheetFund").performScrollTo().performClick()
        assertEquals(listOf(15.0), funded)
        // A typed amount, cents allowed.
        compose.onNodeWithTag("sheetMoneyAmount").performTextClearance()
        compose.onNodeWithTag("sheetMoneyAmount").performTextInput("7.50")
        compose.onNodeWithText("Add \$7.50 to the wallet").assertExists()
        compose.onNodeWithTag("sheetFund").performClick()
        assertEquals(listOf(15.0, 7.5), funded)
        assertTrue("Settings wasn't opened", toSettings.isEmpty())
    }

    @Test
    fun `a typed amount that can't be sent can't be added, and says why`() {
        val funded = mutableListOf<Double>()
        sheetScreen { ApiBetSheetContent(sheet(balance = 12.5), {}, {}, {}, {}, {}, onFundWallet = { funded += it }, keySaved = true) }
        compose.onNodeWithTag("addMoney").performScrollTo().performClick()
        compose.onNodeWithTag("sheetMoneyAmount").performTextClearance()
        compose.onNodeWithTag("sheetMoneyAmount").performTextInput("0")
        compose.onNodeWithTag("sheetFund").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("sheetMoneyAmount").performTextClearance()
        compose.onNodeWithTag("sheetMoneyAmount").performTextInput("50000")
        compose.onNodeWithTag("sheetFund").assertIsNotEnabled()
        assertTrue(funded.isEmpty())
    }

    @Test
    fun `while money is being added the sheet says so, and then what Novig answered`() {
        sheetScreen { ApiBetSheetContent(sheet(balance = 12.5).copy(funding = true), {}, {}, {}, {}, {}, keySaved = true) }
        compose.onNodeWithTag("addMoney").performScrollTo().performClick()
        compose.onNodeWithText("Adding…").assertExists()
        compose.onNodeWithTag("sheetFund").assertIsNotEnabled()
        sheetScreenFresh { ApiBetSheetContent(sheet(balance = 17.5).copy(fundMessage = "Added \$5.00 to the Vigilant wallet."), {}, {}, {}, {}, {}, keySaved = true) }
        compose.onNodeWithTag("addMoney").performScrollTo().performClick()
        compose.onNodeWithTag("sheetFundMessage").assertExists()
        sheetScreenFresh { ApiBetSheetContent(sheet(balance = 12.5).copy(fundError = "Novig doesn't know that key ID."), {}, {}, {}, {}, {}, keySaved = true) }
        compose.onNodeWithTag("addMoney").performScrollTo().performClick()
        compose.onNodeWithTag("sheetFundError").assertExists()
    }

    @Test
    fun `a bet that's placed shows no Add money, and one that couldn't be placed does`() {
        val placed = TrackedBet("id", 1L, "NFL", "A @ B", 2L, "Moneyline", "A", "m", "o", 0.5, 0.5, 0.52, 0.04, 1.0, orderId = "o1")
        sheetScreen { ApiBetSheetContent(sheet(result = PlaceResult.Placed(placed, 0)), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("addMoney").assertDoesNotExist()
        sheetScreenFresh { ApiBetSheetContent(sheet(result = PlaceResult.Refused("The edge is gone.")), {}, {}, {}, {}, {}) }
        compose.onNodeWithTag("addMoney").assertExists()
    }

    @Test
    fun `Novig refusing the order for the balance opens Add money`() {
        val toSettings = mutableListOf<Double>()
        sheetScreen { ApiBetSheetContent(sheet(result = PlaceResult.Failed(com.tjshea.vigilant.data.novig.signing.NovigApiException(422, null, null).advice)), {}, {}, {}, {}, {}, onAddMoney = { toSettings += it }) }
        compose.onNodeWithTag("addMoneyBlock").assertExists()
        compose.onNodeWithTag("sheetFund").performClick()
        assertEquals(1, toSettings.size)
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
        screen { NovigBettingSection(BettingUi(enabled = true, balance = 12.5, message = "Added $10.00. The subaccount now holds $12.50."), ScanSettings(), BettingActions(), {}, savedKey = saved) }
        compose.onRoot().captureRoboImage("screenshots/5f_settings_api_betting.png")
    }

    @Test
    fun `screenshots - the wallet opened from a Bet sheet the wallet couldn't cover`() {
        val top = TopUp(amount = 1.0, needed = 0.85, cost = 1.85, bet = sheet())
        screen { NovigBettingSection(BettingUi(enabled = true, balance = 1.0, topUp = top), ScanSettings(), BettingActions(), {}, savedKey = saved) }
        compose.onRoot().captureRoboImage("screenshots/5g_settings_wallet_top_up.png")
    }

    @Test
    fun `screenshots - the Bet sheet with too little in the wallet`() {
        sheetScreen { ApiBetSheetContent(sheet(balance = 1.0), {}, {}, {}, {}, {}) }
        compose.onRoot().captureRoboImage("screenshots/4g_api_bet_sheet_wallet_short.png")
    }

    @Test
    fun `screenshots - the Bet sheet before and after placing`() {
        sheetScreen { ApiBetSheetContent(sheet(plan = plan.copy(note = "Only $1.85 of the $5.00 is offered at a positive edge right now: this bets $1.85.")), {}, {}, {}, {}, {}) }
        compose.onRoot().captureRoboImage("screenshots/4f_api_bet_sheet.png")
    }
}
