package com.tjshea.vigilant.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.input.KeyboardType
import com.tjshea.vigilant.app.TopUp
import com.tjshea.vigilant.app.BetAmount
import com.tjshea.vigilant.app.WalletAmount
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.ManagementKeyHint
import com.tjshea.vigilant.app.BetSheetUi
import com.tjshea.vigilant.app.BettingUi
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.novig.trading.PlaceResult
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.Locale

/**
 * Betting through Novig's API (Tj, 2026-09-29: "Build the betting through the API function"): the Settings section that sets it up and moves
 * money, the Bet button on the +EV and CNO cards, and the Bet sheet that shows exactly what will be bought before anything is sent. The
 * content composables take immutable state and callbacks; the ViewModel ([com.tjshea.vigilant.app.ApiBettingController]) owns the rest.
 */

/** What Settings' betting section can do. */
data class BettingActions(
    /** [typed]: the management key Tj just entered (saved once Novig accepts it); null = the one saved on this phone. */
    val onEnable: (typed: ManagementKey?) -> Unit = {},
    /** [direction] "fund" or "defund"; [amount] in dollars, as typed; [typed] as in [onEnable]. */
    val onTransfer: (direction: String, amount: Double, typed: ManagementKey?) -> Unit = { _, _, _ -> },
    /** "Save key": checked with Novig, then saved for every later setup and transfer. */
    val onSaveKey: (ManagementKey) -> Unit = {},
    val onForgetKey: () -> Unit = {},
    val onDisable: () -> Unit = {},
    val onRefreshBalance: () -> Unit = {},
    val onSync: () -> Unit = {},
    /** The top-up banner's "Back to the bet" and its ✕. */
    val onBackToBet: () -> Unit = {},
    val onDismissTopUp: () -> Unit = {},
)

/** What a card's Bet button does; null (or not [enabled]) = betting isn't set up and the button isn't there. */
class ApiBetActions(
    val enabled: Boolean,
    val betOpportunity: (Opportunity) -> Unit,
    val betCno: (CnoRow) -> Unit,
    /** A ParlayAPI pick (TASKS.md P1): as a CNO bet, at ParlayAPI's fair odds, logged as ParlayAPI's. */
    val betParlay: (com.tjshea.vigilant.data.reference.ParlayPick) -> Unit = {},
)

val LocalApiBet = staticCompositionLocalOf<ApiBetActions?> { null }

/**
 * [LocalApiBet] for everything in [content], one [ApiBetActions] for as long as [enabled] and [api] stay the same. It is a STATIC local: a new
 * value redraws the whole tree under it with nothing skipped, and the app's root made a new one with every state it got, 3 a second while a
 * scan runs (Tj, 2026-10-02: "when I start the vigilant scanner the list of bets gets laggy", RESEARCH.md §63).
 */
@Composable
fun ProvideApiBet(enabled: Boolean, api: com.tjshea.vigilant.app.ApiBettingController, content: @Composable () -> Unit) {
    val actions = remember(enabled, api) {
        ApiBetActions(enabled = enabled, betOpportunity = { o -> api.bet(o) }, betCno = { row -> api.bet(row) }, betParlay = { p -> api.bet(p) })
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalApiBet provides actions, content = content)
}

/** The Bet button beside "Open in Novig" on a card: only when betting through the API is set up. */
@Composable
fun ApiBetButton(modifier: Modifier = Modifier, onClick: (ApiBetActions) -> Unit) {
    val actions = LocalApiBet.current?.takeIf { it.enabled } ?: return
    FilledTonalButton(
        onClick = { onClick(actions) },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = modifier.testTag("apiBet"),
    ) { Text("Bet", maxLines = 1) }
}

// ---- the management key: entered once, then saved on this phone ----------------------------------------------------

/** The management key being typed or picked (kept only in this state until it's used), and whether a saved one is being replaced. */
@Stable
class ManagementKeyState {
    var keyId by mutableStateOf("")
    var pem by mutableStateOf("")
    var fileName by mutableStateOf<String?>(null)

    /** Tj tapped Replace: the fields show although a key is saved. */
    var replacing by mutableStateOf(false)
    val ready: Boolean get() = typed() != null

    /** What's been entered, when it's a whole key. */
    fun typed(): ManagementKey? = ManagementKey(keyId, pem).takeIf { it.complete }

    fun clearSecret() {
        pem = ""
        fileName = null
    }

    /** The key a button sends: the typed one, or null for the saved one ([usesSaved]). */
    fun usesSaved(saved: ManagementKeyHint?): Boolean = saved != null && !saved.unreadable && !replacing
}

@Composable
fun ManagementKeyFields(state: ManagementKeyState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
                .getOrNull()?.takeIf { it.contains("PRIVATE KEY") }
                ?.let {
                    state.pem = it
                    state.fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "key file"
                }
        }
    }
    OutlinedTextField(
        value = state.keyId,
        onValueChange = { state.keyId = it.trim() },
        label = { Text("Management key ID") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().testTag("mgmtKeyId"),
    )
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(if (state.fileName == null) "Choose .pem file" else "Change file") }
        Text(state.fileName?.let { "✓ $it" } ?: "or paste it below", style = MaterialTheme.typography.bodySmall)
    }
    if (state.fileName == null) {
        OutlinedTextField(
            value = state.pem,
            onValueChange = { state.pem = it },
            label = { Text("…or paste the key text") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("mgmtKeyPem"),
            maxLines = 3,
        )
    }
}

/**
 * The management key for the buttons below it: "saved on this phone" with Replace and Forget once it's saved (Tj, 2026-09-29: "I only input the
 * API key and file one time"), else the fields, with Save key to save it without moving money. [onSave] null: no Save button (the Connect form).
 */
@Composable
fun ManagementKeyBlock(saved: ManagementKeyHint?, key: ManagementKeyState, busy: Boolean, onSave: ((ManagementKey) -> Unit)?, onForget: () -> Unit) {
    if (key.usesSaved(saved)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("savedMgmtKey")) {
            Text(
                "✓ Management key ••••${saved!!.keyIdEnd} saved on this phone",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { key.replacing = true }, enabled = !busy, modifier = Modifier.testTag("replaceMgmtKey")) { Text("Replace") }
            TextButton(onClick = onForget, enabled = !busy, modifier = Modifier.testTag("forgetMgmtKey")) { Text("Forget") }
        }
        return
    }
    if (saved?.unreadable == true) {
        Text(
            "A management key was saved, but this phone can't unlock it any more: enter it once more and it's saved again.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    ManagementKeyFields(key)
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onSave != null) {
            OutlinedButton(
                onClick = { key.typed()?.let(onSave); key.clearSecret(); key.replacing = false },
                enabled = !busy && key.ready,
                modifier = Modifier.testTag("saveMgmtKey"),
            ) { Text("Save key") }
        }
        if (saved != null && !saved.unreadable) {
            TextButton(onClick = { key.replacing = false; key.clearSecret() }, modifier = Modifier.testTag("keepMgmtKey")) { Text("Keep the saved key") }
        }
    }
    Text(
        "Saved on this phone once Novig accepts it, sealed by the phone's secure hardware, and kept through every app update: you enter it once.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---- Settings section --------------------------------------------------------------------------------------------

private val STAKE_CHOICES = listOf(1.0, 2.0, 5.0, 10.0, 20.0)
private val MAX_STAKE_CHOICES = listOf(5.0, 10.0, 20.0, 50.0, 100.0)
private val DAY_CHOICES = listOf(20.0, 50.0, 100.0, 250.0, 500.0)
private val MIN_EV_CHOICES = listOf(0.0, 0.005, 0.01, 0.02, 0.03)
private val MONEY_CHOICES = listOf(5.0, 10.0, 20.0, 50.0, 100.0)

/** Settings › Betting › Betting through the API. Shown once a key is connected. [savedKey]: the management key saved on this phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovigBettingSection(
    betting: BettingUi,
    settings: ScanSettings,
    actions: BettingActions,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    savedKey: ManagementKeyHint? = null,
) {
    val key = remember { ManagementKeyState() }
    SectionTitle("Betting through the API")
    Text(
        "Vigilant can place a bet on Novig for you from a separate \"Vigilant\" wallet on your Novig account, and grade it from Novig's own books. " +
            "It is not the cash wallet your Novig app bets use: you add money to it here, and only bets placed from Vigilant use it. " +
            "Every bet shows exactly what it will buy and asks you to confirm; nothing is sent before that, and it never bets a game that has started. " +
            "Novig only lets an order through from a network it doesn't list as a VPN or proxy, and when you've opened the Novig app in the last 3 days. " +
            "Setting up and moving money need your management key (Novig › Profile › Settings › Novig API); you enter it once and it's saved on this phone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    if (!betting.enabled) {
        ManagementKeyBlock(savedKey, key, betting.busy, onSave = null, onForget = actions.onForgetKey)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { actions.onEnable(if (key.usesSaved(savedKey)) null else key.typed()); key.clearSecret(); key.replacing = false },
                enabled = !betting.busy && (key.usesSaved(savedKey) || key.ready),
                modifier = Modifier.testTag("enableBetting"),
            ) { Text("Enable betting") }
            if (betting.busy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), strokeWidth = 2.dp)
        }
        BettingStatus(betting)
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Betting is on · Vigilant wallet " + (betting.balance?.let { Format.money(it) } ?: "…"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).testTag("bettingBalance"),
        )
        TextButton(onClick = actions.onRefreshBalance, enabled = !betting.busy) { Text("Refresh") }
    }

    WalletBlock(betting, savedKey, key, actions)

    Text("Amount a bet starts at", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
    ChoiceChips(STAKE_CHOICES, settings.apiBetStake, { Format.money(it) }) { v -> onUpdate { it.copy(apiBetStake = v) } }
    Text(
        "Each Bet sheet also takes any amount you type. With Kelly (or My amount) chosen for Novig's bet slip, a bet starts at that bet's " +
            "Kelly stake (or that amount) instead, within the most for one bet. When the wallet holds less, a bet starts at what's left in it.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Most for one bet", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(MAX_STAKE_CHOICES, settings.apiMaxStake, { Format.money(it) }) { v -> onUpdate { it.copy(apiMaxStake = v, apiBetStake = minOf(it.apiBetStake, v)) } }
    Text("Most in a day", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(DAY_CHOICES, settings.apiMaxPerDay, { Format.money(it) }) { v -> onUpdate { it.copy(apiMaxPerDay = v) } }
    Text("Smallest edge a bet is still placed at", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(MIN_EV_CHOICES, settings.apiMinEv, { if (it == 0.0) "Any +EV" else "+" + Format.percent(it, if (it * 1000 % 10 == 0.0) 0 else 1) }) { v -> onUpdate { it.copy(apiMinEv = v) } }
    Text(
        "If Novig's price moves so the edge is below this, the bet is refused, not placed.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = actions.onSync, enabled = !betting.busy) { Text("Sync Tracker with Novig's fills") }
        TextButton(onClick = actions.onDisable, enabled = !betting.busy) { Text("Turn betting off") }
    }
}

/**
 * Adding money to (or taking it back from) the Vigilant wallet, any amount Tj types (Tj, 2026-09-29). Arriving from a Bet sheet the wallet
 * couldn't cover ([BettingUi.topUp]), it's scrolled into view with what the bet is short by typed in, and offers "Back to the bet".
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WalletBlock(betting: BettingUi, savedKey: ManagementKeyHint?, key: ManagementKeyState, actions: BettingActions) {
    val topUp = betting.topUp
    // A new request types its amount in; otherwise the field keeps what Tj typed (also across a rotation).
    var amountText by rememberSaveable(topUp?.amount) { mutableStateOf(topUp?.amount?.let(WalletAmount::text) ?: "10") }
    val amount = WalletAmount.parse(amountText)
    val problem = WalletAmount.problem(amountText)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(topUp != null) {
        if (topUp != null) {
            withFrameNanos { } // once it's laid out
            requester.bringIntoView()
        }
    }
    Column(Modifier.bringIntoViewRequester(requester).padding(top = 8.dp).testTag("walletBlock")) {
        Text("Add money to the Vigilant wallet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        topUp?.let { TopUpBanner(it, betting.balance, actions) }
        OutlinedTextField(
            value = amountText,
            onValueChange = { t -> amountText = t.filter { it.isDigit() || it == '.' || it == ',' || it == '$' }.take(12) },
            label = { Text("Amount") },
            prefix = { Text("$") },
            singleLine = true,
            isError = problem != null,
            supportingText = problem?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("walletAmount"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MONEY_CHOICES.forEach { c ->
                FilterChip(
                    selected = amount != null && kotlin.math.abs(amount - c) < 1e-9, onClick = { amountText = WalletAmount.text(c) }, label = { Text(Format.money(c)) },
                    modifier = Modifier.testTag("walletChip-${WalletAmount.text(c)}"),
                )
            }
        }
        ManagementKeyBlock(savedKey, key, betting.busy, onSave = actions.onSaveKey, onForget = actions.onForgetKey)
        val keyOk = key.usesSaved(savedKey) || key.ready
        val sent = { if (key.usesSaved(savedKey)) null else key.typed() }
        val overBalance = amount != null && betting.balance != null && amount > betting.balance + 1e-9
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { amount?.let { actions.onTransfer("fund", it, sent()) }; key.clearSecret(); key.replacing = false },
                enabled = !betting.busy && keyOk && amount != null,
                modifier = Modifier.testTag("fundWallet"),
            ) { Text(amount?.let { "Add ${Format.money(it)} to the wallet" } ?: "Add to the wallet") }
            OutlinedButton(
                onClick = { amount?.let { actions.onTransfer("defund", it, sent()) }; key.clearSecret(); key.replacing = false },
                enabled = !betting.busy && keyOk && amount != null && !overBalance,
                modifier = Modifier.testTag("defundWallet"),
            ) { Text(amount?.let { "Take ${Format.money(it)} back" } ?: "Take back") }
            if (betting.busy) CircularProgressIndicator(Modifier.padding(start = 4.dp).size(20.dp), strokeWidth = 2.dp)
        }
        if (overBalance) {
            Text("The wallet holds ${Format.money(betting.balance)}: that's the most you can take back.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BettingStatus(betting)
    }
}

/** "Your bet needs $X more": what the pending bet costs against the wallet, and the way back to it. */
@Composable
private fun TopUpBanner(topUp: TopUp, balance: Double?, actions: BettingActions) {
    val covered = balance != null && balance + 1e-9 >= topUp.cost
    androidx.compose.material3.Surface(
        color = if (covered) Edge.colors.positive.copy(alpha = 0.14f) else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("topUpBanner"),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (covered) "The wallet now covers your bet on ${topUp.bet.title} (${Format.money(topUp.cost)})."
                else "Your bet on ${topUp.bet.title} costs ${Format.money(topUp.cost)} and the wallet holds ${Format.money(balance ?: 0.0)}: " +
                    "add at least ${Format.money(topUp.needed)} (typed in below), then go back to the bet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (covered) {
                    Button(onClick = actions.onBackToBet, modifier = Modifier.testTag("backToBet")) { Text("Back to the bet") }
                } else {
                    OutlinedButton(onClick = actions.onBackToBet, modifier = Modifier.testTag("backToBet")) { Text("Back to the bet") }
                }
                TextButton(onClick = actions.onDismissTopUp, modifier = Modifier.testTag("dismissTopUp")) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun BettingStatus(b: BettingUi) {
    b.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp).testTag("bettingMessage")) }
    b.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.padding(top = 6.dp).testTag("bettingError")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(choices: List<Double>, selected: Double, label: (Double) -> String, onPick: (Double) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            FilterChip(selected = kotlin.math.abs(selected - c) < 1e-9, onClick = { onPick(c) }, label = { Text(label(c)) })
        }
    }
}

// ---- the Bet sheet ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiBetSheet(
    sheet: BetSheetUi,
    onStake: (Double) -> Unit,
    onConfirm: () -> Unit,
    onRefresh: () -> Unit,
    onRepeat: () -> Unit,
    onDismiss: () -> Unit,
    onAddMoney: (Double) -> Unit = {},
    onTypeStake: (Double) -> Unit = onStake,
    onFundWallet: (Double) -> Unit = {},
    keySaved: Boolean = false,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!sheet.placing) onDismiss() }, sheetState = state) {
        ApiBetSheetContent(sheet, onStake, onConfirm, onRefresh, onRepeat, onDismiss, onAddMoney, onTypeStake, onFundWallet, keySaved)
    }
}

/** What the sheet shows for one bet: previewable with any state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiBetSheetContent(
    sheet: BetSheetUi,
    onStake: (Double) -> Unit,
    onConfirm: () -> Unit,
    onRefresh: () -> Unit,
    onRepeat: () -> Unit,
    onDismiss: () -> Unit,
    /** "Add money to the wallet" without a saved management key: Settings' wallet, with the amount typed in (the key is asked for there). */
    onAddMoney: (Double) -> Unit = {},
    /** An amount typed in the Amount field (priced once typing pauses). */
    onTypeStake: (Double) -> Unit = onStake,
    /** "Add money to the wallet" with a saved management key ([keySaved]): moves the amount from the cash wallet right here. */
    onFundWallet: (Double) -> Unit = {},
    keySaved: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().testTag("apiBetSheet"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(sheet.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(sheet.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val result = sheet.result
        if (result != null) {
            ResultBlock(result)
            // Novig refused it for the wallet's balance (the sheet's own check read an older balance): the money block is open.
            if (result !is PlaceResult.Placed) {
                AddMoneyBlock(sheet, keySaved, suggest = null, autoOpen = result is PlaceResult.Failed && WALLET_WORDS.containsMatchIn(result.message), onFund = onFundWallet, onSettings = onAddMoney)
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("betDone")) { Text("Done") }
            return@Column
        }

        if (sheet.resolving) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Finding Novig's exact bet…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (!sheet.resolving && sheet.target != null) {
            StakeField(sheet, onTypeStake)
            val choices = (STAKE_CHOICES + sheet.stake).filter { it <= sheet.maxStake + 1e-9 && it > 0.0 }.distinct().sorted()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { c ->
                    FilterChip(selected = kotlin.math.abs(sheet.stake - c) < 1e-9, onClick = { onStake(c) }, enabled = !sheet.placing, label = { Text(Format.money(c)) })
                }
            }
        }

        val plan = sheet.plan
        if (plan != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Price", "${Format.american(plan.averagePrice)} · ${Format.percent(plan.averagePrice)}")
                LabeledValue("You pay", Format.money(plan.expectedCost))
                LabeledValue("A win pays", Format.money(plan.payout))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledValue("Edge now", Format.evPercentShort(plan.evPercent), valueColor = if (plan.evPercent >= 0) Edge.colors.positive else Edge.colors.negative)
                LabeledValue("Profit if it wins", Format.money(plan.profitIfWon))
                sheet.balance?.let { LabeledValue("Wallet", Format.money(it), valueColor = if (it + 1e-9 < plan.expectedCost) Edge.colors.negative else androidx.compose.ui.graphics.Color.Unspecified) }
            }
            plan.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning) }
            val short = sheet.balance != null && sheet.balance + 1e-9 < plan.expectedCost
            if (short) {
                Text(
                    "The Vigilant wallet holds ${Format.money(sheet.balance)}, less than this bet's ${Format.money(plan.expectedCost)}: add money to it first.",
                    style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.testTag("walletShort"),
                )
            }
            Text(
                "It's placed as one order that buys at ${Format.american(plan.limitPrice)} or better and never rests: if Novig's price moves against you first, " +
                    "or nobody's selling, nothing is bought. Pregame bets pay no Novig fee.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onConfirm,
                enabled = !sheet.placing && !short,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirmBet"),
            ) {
                if (sheet.placing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Placing…")
                } else {
                    Text("Place bet · ${Format.money(plan.expectedCost)}", fontWeight = FontWeight.Bold)
                }
            }
        }

        sheet.refusal?.let { reason ->
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = Edge.colors.negative, modifier = Modifier.testTag("betRefusal"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sheet.target != null) OutlinedButton(onClick = onRefresh, enabled = !sheet.placing) { Text("Look again") }
                if (reason.contains("already bet this")) OutlinedButton(onClick = onRepeat) { Text("Bet it again") }
            }
        }
        if (sheet.plan == null && sheet.refusal == null && !sheet.resolving && sheet.target != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  Reading Novig's price…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        // Add money to the wallet, in every bet slip whatever the wallet holds (Tj, 2026-10-01: "Right now if I have one cent, there is no option to add money
        // in the bet slip"): open already, with what the bet is short by, when the wallet can't cover it.
        val shortBy = sheet.plan?.let { p -> sheet.balance?.takeIf { it + 1e-9 < p.expectedCost }?.let { p.expectedCost - it } }
        AddMoneyBlock(sheet, keySaved, suggest = shortBy, autoOpen = shortBy != null, onFund = onFundWallet, onSettings = onAddMoney)
        if (!sheet.placing) TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The sheet's Amount, typed (Tj, 2026-09-30: "I can type in a custom account for any bet manually"): dollars and cents up to the per-bet limit.
 * It follows the amount when a chip or the wallet changes it, and never fights what's being typed.
 */
@Composable
private fun StakeField(sheet: BetSheetUi, onTypeStake: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(WalletAmount.text(sheet.stake)) }
    // A chip, or the wallet holding less than the amount, set a new one: show it (what's typed and means the same stays as typed).
    LaunchedEffect(sheet.stake) {
        if (BetAmount.parse(text, sheet.maxStake)?.let { kotlin.math.abs(it - sheet.stake) < 1e-9 } != true) text = WalletAmount.text(sheet.stake)
    }
    val problem = BetAmount.problem(text, sheet.maxStake)
    val walletNote = sheet.balance?.takeIf { !sheet.stakeChosen && it >= 0.01 && kotlin.math.abs(it - sheet.stake) < 0.01 && sheet.stake < sheet.baseStake - 1e-9 }
        ?.let { "All that's left in the wallet" }
    // Where the amount came from (the bet's Kelly stake, Tj 2026-10-01), until Tj picks another.
    val sourceNote = sheet.stakeNote?.takeIf { !sheet.stakeChosen && kotlin.math.abs(sheet.stake - sheet.baseStake) < 0.005 }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == '.' || it == ',' || it == '$' }.take(10)
            BetAmount.parse(text, sheet.maxStake)?.let(onTypeStake)
        },
        label = { Text("Amount") },
        prefix = { Text("$") },
        singleLine = true,
        enabled = !sheet.placing,
        isError = problem != null,
        supportingText = { Text(problem ?: walletNote ?: sourceNote ?: "Type any amount up to ${Format.money(sheet.maxStake)}, or pick one", modifier = Modifier.testTag("betAmountNote")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("betAmount"),
    )
}

/** What an order refused for the wallet's balance says: Novig's 422 ([NovigApiException.advice]: "doesn't have enough money") or its own words. */
private val WALLET_WORDS = Regex("(?i)enough money|balance|insufficient|funds")

/** The amounts the sheet's "Add money" offers (Tj, 2026-10-01: "$1, 2, 5, 10, 15, 20, or an amount I type in"). */
val SHEET_MONEY_CHOICES = listOf(1.0, 2.0, 5.0, 10.0, 15.0, 20.0)

/**
 * The Bet sheet's way to put money in the Vigilant wallet, in every sheet (Tj, 2026-09-29: a way to Settings when the wallet is short; 2026-10-01: "a button
 * in all the bet slips … in amounts of $1, 2, 5, 10, 15, 20, or an amount I type in", also when the wallet holds a cent): a button that opens the amounts and a
 * typed field. With a management key saved on the phone ([keySaved]) the money moves from here ([onFund]); without one, Settings' wallet opens with the amount
 * typed in ([onSettings]), where the key is entered once. [suggest]: what the bet is short by (rounded up to whole dollars, the amount to start from).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMoneyBlock(sheet: BetSheetUi, keySaved: Boolean, suggest: Double?, autoOpen: Boolean, onFund: (Double) -> Unit, onSettings: (Double) -> Unit) {
    var open by rememberSaveable(autoOpen) { mutableStateOf(autoOpen) }
    var text by rememberSaveable { mutableStateOf(WalletAmount.text(suggest?.let(WalletAmount::suggest) ?: 5.0)) }
    // A bet that is short suggests its shortfall, whenever that appears.
    LaunchedEffect(suggest?.let(WalletAmount::suggest)) { suggest?.let { text = WalletAmount.text(WalletAmount.suggest(it)) } }
    val amount = WalletAmount.parse(text)
    val problem = WalletAmount.problem(text)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { open = !open }, enabled = !sheet.placing, modifier = Modifier.fillMaxWidth().testTag("addMoney")) {
            Text("Add money to the wallet" + (sheet.balance?.let { " · ${Format.money(it)} in it" }.orEmpty()) + if (open) "  ▴" else "  ▾")
        }
        if (open) {
            Column(Modifier.testTag("addMoneyBlock"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (keySaved) "Moves money from your Novig cash wallet to the Vigilant wallet."
                    else "Opens Settings with the amount filled in; your Novig management key is asked for there once, then saved.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SHEET_MONEY_CHOICES.forEach { c ->
                        FilterChip(
                            selected = amount != null && kotlin.math.abs(amount - c) < 1e-9, onClick = { text = WalletAmount.text(c) },
                            enabled = !sheet.funding && !sheet.placing, label = { Text(Format.money(c)) },
                            modifier = Modifier.testTag("sheetMoney-${WalletAmount.text(c)}"),
                        )
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.filter { it.isDigit() || it == '.' || it == ',' || it == '$' }.take(12) },
                    label = { Text("Or type an amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    enabled = !sheet.funding && !sheet.placing,
                    isError = problem != null,
                    supportingText = problem?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("sheetMoneyAmount"),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { amount?.let { if (keySaved) onFund(it) else onSettings(it) } },
                        enabled = amount != null && !sheet.funding && !sheet.placing,
                        modifier = Modifier.testTag("sheetFund"),
                    ) { Text(amount?.let { "Add ${Format.money(it)} to the wallet" } ?: "Add to the wallet") }
                    if (sheet.funding) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Adding…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                sheet.fundMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.positive, modifier = Modifier.testTag("sheetFundMessage")) }
                sheet.fundError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.testTag("sheetFundError")) }
            }
        }
    }
}

@Composable
private fun ResultBlock(result: PlaceResult) {
    when (result) {
        is PlaceResult.Placed -> {
            val b = result.bet
            Text("✓ Bet placed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Edge.colors.positive, modifier = Modifier.testTag("betPlaced"))
            Text(
                "${b.contracts} contracts for ${Format.money(b.paid ?: b.stake)} at ${Format.american(b.price)}" +
                    (b.fee?.takeIf { it > 0.0 }?.let { " + ${Format.money(it)} fee" } ?: "") +
                    ". A win pays ${Format.money((b.contracts ?: 0L) * 0.01)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result.unfilledContracts > 0) {
                Text("Only part of it filled: ${result.unfilledContracts} contracts weren't available at that price and weren't bought.", style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning)
            }
            Text("It's in the Tracker, and Novig's own books will grade it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is PlaceResult.NotFilled -> ResultText("Not placed", result.reason, Edge.colors.warning)
        is PlaceResult.Refused -> ResultText("Not placed", result.reason, Edge.colors.warning)
        is PlaceResult.Failed -> ResultText("Novig said no", result.message, Edge.colors.negative)
        is PlaceResult.Unconfirmed -> ResultText("Couldn't confirm", result.message, Edge.colors.warning)
    }
}

@Composable
private fun ResultText(title: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.testTag("betNotPlaced"))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

