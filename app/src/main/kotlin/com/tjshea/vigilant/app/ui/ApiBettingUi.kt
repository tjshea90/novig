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
    val onEnable: (managementKeyId: String, pem: String) -> Unit = { _, _ -> },
    /** [direction] "fund" or "defund". */
    val onTransfer: (direction: String, amount: Double, managementKeyId: String, pem: String) -> Unit = { _, _, _, _ -> },
    val onDisable: () -> Unit = {},
    val onRefreshBalance: () -> Unit = {},
    val onSync: () -> Unit = {},
)

/** What a card's Bet button does; null (or not [enabled]) = betting isn't set up and the button isn't there. */
class ApiBetActions(val enabled: Boolean, val betOpportunity: (Opportunity) -> Unit, val betCno: (CnoRow) -> Unit)

val LocalApiBet = staticCompositionLocalOf<ApiBetActions?> { null }

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

// ---- the management key, held in memory while a form is open ---------------------------------------------------

/** The management key a setup or transfer needs: typed or picked, kept only in this state, cleared after use. */
@Stable
class ManagementKeyState {
    var keyId by mutableStateOf("")
    var pem by mutableStateOf("")
    var fileName by mutableStateOf<String?>(null)
    val ready: Boolean get() = keyId.length >= 8 && pem.contains("PRIVATE KEY")

    fun clearSecret() {
        pem = ""
        fileName = null
    }
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

// ---- Settings section --------------------------------------------------------------------------------------------

private val STAKE_CHOICES = listOf(1.0, 2.0, 5.0, 10.0, 20.0)
private val MAX_STAKE_CHOICES = listOf(5.0, 10.0, 20.0, 50.0, 100.0)
private val DAY_CHOICES = listOf(20.0, 50.0, 100.0, 250.0, 500.0)
private val MIN_EV_CHOICES = listOf(0.0, 0.005, 0.01, 0.02, 0.03)
private val MONEY_CHOICES = listOf(5.0, 10.0, 20.0, 50.0, 100.0)

/** Settings › Novig API key › Betting through the API. Shown once a key is connected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovigBettingSection(
    betting: BettingUi,
    settings: ScanSettings,
    actions: BettingActions,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
) {
    val key = remember { ManagementKeyState() }
    var amount by remember { mutableStateOf(10.0) }
    SectionTitle("Betting through the API")
    Text(
        "Vigilant can place a bet on Novig for you from a separate \"Vigilant\" wallet on your Novig account, and grade it from Novig's own books. " +
            "It is not the cash wallet your Novig app bets use: you add money to it here, and only bets placed from Vigilant use it. " +
            "Every bet shows exactly what it will buy and asks you to confirm; nothing is sent before that, and it never bets a game that has started. " +
            "Needs your management key (Novig › Profile › Settings › Novig API) for the steps below; Vigilant uses it once and never stores it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    if (!betting.enabled) {
        ManagementKeyFields(key)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { actions.onEnable(key.keyId, key.pem); key.clearSecret() },
                enabled = !betting.busy && key.ready,
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

    Text("Amount a bet starts at", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(STAKE_CHOICES, settings.apiBetStake, { Format.money(it) }) { v -> onUpdate { it.copy(apiBetStake = v) } }
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

    Text("Move money", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
    ChoiceChips(MONEY_CHOICES, amount, { Format.money(it) }) { amount = it }
    ManagementKeyFields(key)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { actions.onTransfer("fund", amount, key.keyId, key.pem); key.clearSecret() },
            enabled = !betting.busy && key.ready,
            modifier = Modifier.testTag("fundWallet"),
        ) { Text("Add ${Format.money(amount)} to the wallet") }
        OutlinedButton(
            onClick = { actions.onTransfer("defund", amount, key.keyId, key.pem); key.clearSecret() },
            enabled = !betting.busy && key.ready,
            modifier = Modifier.testTag("defundWallet"),
        ) { Text("Take ${Format.money(amount)} back") }
        if (betting.busy) CircularProgressIndicator(Modifier.padding(start = 4.dp).size(20.dp), strokeWidth = 2.dp)
    }
    BettingStatus(betting)
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = actions.onSync, enabled = !betting.busy) { Text("Sync Tracker with Novig's fills") }
        TextButton(onClick = actions.onDisable, enabled = !betting.busy) { Text("Turn betting off") }
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
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!sheet.placing) onDismiss() }, sheetState = state) {
        ApiBetSheetContent(sheet, onStake, onConfirm, onRefresh, onRepeat, onDismiss)
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
            Text("Amount", style = MaterialTheme.typography.labelMedium)
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
                    "The wallet holds ${Format.money(sheet.balance ?: 0.0)}: add money in Settings › Novig API key › Betting through the API first.",
                    style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative,
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
        if (!sheet.placing) TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.height(4.dp))
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

internal fun formatStake(v: Double): String = String.format(Locale.US, "%.2f", v)
