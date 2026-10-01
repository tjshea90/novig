package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.AutoBettor
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.app.WalletAmount
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import java.util.Locale
import kotlin.math.abs

/** The auto-bet's sentences, free of Compose so they're testable. */
object AutoBetText {

    /** "+3%", "+3.25%", "+2.5%". */
    fun evLabel(ev: Double): String = "+" + trim(ev * 100) + "%"

    /** "3", "3.25", "2.5": no trailing zeros. */
    fun trim(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    fun booksLabel(n: Int): String = if (n >= 5) "5+" else "$n"

    /** What each stake choice means, for the confirm and the hint. */
    fun stakeText(s: ScanSettings): String = when (s.autoBetStake) {
        AutoBetStake.EIGHTH_KELLY, AutoBetStake.QUARTER_KELLY, AutoBetStake.HALF_KELLY -> "its ${s.autoBetStake.label} stake of your ${Format.money(s.bankroll)} bankroll"
        AutoBetStake.ONE_DOLLAR -> "$1"
        AutoBetStake.CUSTOM -> Format.money(s.autoBetCustomStake)
    }

    /** The criteria in one sentence, as the confirm and the status say them. */
    fun criteria(s: ScanSettings): String {
        val r = AutoBet.rules(s)
        return "at least ${r.minBooks} book${if (r.minBooks == 1) "" else "s"} agreeing it's +EV on their own, ${r.twoSided} pricing both sides, an edge of " +
            "${evLabel(r.minEv)} or more at Novig's price now, staking ${stakeText(s)} (never over ${Format.money(r.maxStake)})"
    }

    /** The confirm's whole text. */
    fun confirm(s: ScanSettings, balance: Double?): String =
        "Vigilant will place REAL bets from your Vigilant wallet" + (balance?.let { " (${Format.money(it)})" } ?: "") + " with nobody asking you, " +
            "each time CrazyNinjaOdds' background scan finds a bet with ${criteria(s)}. It never bets a game that has started, never more than " +
            "${Format.money(s.apiMaxPerDay)} in a day across API bets, and stops when the wallet can't fund a $1 bet or if an order's answer is lost. " +
            "Every bet is tracked like one you placed yourself, and you get a notification for each."

    /** Why auto-bet isn't running right now though it's on (or what turning it on needs), or null when it is running. */
    fun whyNotRunning(state: UiState): String? {
        val s = state.settings
        return when {
            !AppBook.isNovig -> "Auto-bet is for Novig."
            !state.betting.enabled -> "Betting through the API isn't set up: enable it above first (the wallet is where auto-bets come from)."
            s.autoBetHalted != null -> "Stopped: ${s.autoBetHalted}"
            s.paused -> "Scanning is paused (the ⏸ button): auto-bet waits for it."
            s.scanner == ScannerMode.VIGILANT -> "The scanner is Vigilant only, so CrazyNinjaOdds is asleep and auto-bet has nothing to read: pick Both or CNO only (Settings › Scan)."
            s.autoScan == AutoScanMode.OFF -> "Background auto-scan is off, and auto-bet runs with it: pick CNO or CNO + Vigilant (Settings › Scan)."
            else -> null
        }
    }

    /** What it does at these settings, when it's running. */
    fun running(s: ScanSettings): String =
        "Running with the background CNO scan, every ${ScanSettings.intervalLabel(s.autoScanSeconds)}: each bet that passes is placed best edge first, up to ${AutoBet.MAX_PER_CYCLE} a check."

    /** The Kelly sizing note, when a Kelly stake is chosen. */
    fun kellyNote(s: ScanSettings): String? {
        val f = s.autoBetStake.kelly ?: return null
        return "Kelly sizing uses your bankroll (${Format.money(s.bankroll)}, set in Bankroll & Kelly above): bankroll × ${Format.kellyLabel(f).removeSuffix(" Kelly")} × " +
            "(fair chance − price) ÷ (1 − price), so it changes with each bet's odds and edge. It's held to your most per bet, to what Novig has for sale at +EV " +
            "and to what's in the wallet; under ${Format.money(AutoBet.MIN_STAKE)} is skipped, never rounded up."
    }
}

/**
 * Settings › Betting › Auto-bet (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all, including … in the background as the
 * cno scanner is on in the background"). Off by default; turning it on asks once, plainly. The criteria are Tj's seven choices: books agreeing,
 * the smallest edge (a preset or typed), CNO only (fixed), the stake (⅛/¼/½ Kelly, $1 or typed), books pricing both sides, the most per bet
 * (typed), and the check interval (the background CNO scan's own choices). [onUpdate] edits the saved settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoBetSection(state: UiState, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    var confirming by remember { mutableStateOf(false) }
    val balance = state.betting.balance ?: state.autoBetStatus.balance
    SectionTitle("Auto-bet (CrazyNinjaOdds)")
    Text(
        "Places each CrazyNinjaOdds bet that passes your criteria for you, through Novig's API from the Vigilant wallet, with nobody confirming: " +
            "in the background as the CNO scan runs, with Vigilant open or closed. Pregame only. Off until you turn it on.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp),
    )
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = s.autoBet, role = Role.Switch, enabled = state.betting.enabled || s.autoBet,
            onValueChange = { on -> if (on) confirming = true else onUpdate { it.copy(autoBet = false) } },
        ).padding(vertical = 6.dp).testTag("autoBetSwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Place bets automatically", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = s.autoBet, onCheckedChange = null, enabled = state.betting.enabled || s.autoBet)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Turn on auto-bet?") },
            text = { Text(AutoBetText.confirm(s, balance)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = false
                        // The background CNO scan is what runs it: Off becomes CNO, anything else stays.
                        onUpdate { it.copy(autoBet = true, autoScan = if (it.autoScan == AutoScanMode.OFF) AutoScanMode.CNO else it.autoScan) }
                    },
                    modifier = Modifier.testTag("autoBetConfirm"),
                ) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }

    // Halted: a lost order. Never silently resumed.
    s.autoBetHalted?.let { why ->
        Text("Auto-bet is stopped: $why", style = MaterialTheme.typography.bodyMedium, color = Edge.colors.negative, modifier = Modifier.padding(vertical = 4.dp).testTag("autoBetHalted"))
        Text(
            "Check Novig for that bet (and the Tracker's Sync with Novig's fills), then resume. Nothing is placed until you do.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onUpdate { it.copy(autoBetHalted = null) } }, modifier = Modifier.testTag("autoBetResume")) { Text("Resume auto-bet") }
    }

    // Why it can't run (on or not): betting must be set up first, and the CNO scan must run in the background; else what it does.
    val why = AutoBetText.whyNotRunning(state)
    // (A halt has its own red block and Resume above: not said twice.)
    if (s.autoBetHalted == null && (s.autoBet || (why != null && !state.betting.enabled))) {
        Text(
            why ?: AutoBetText.running(s),
            style = MaterialTheme.typography.bodySmall,
            color = if (why != null && s.autoBetHalted == null) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp).testTag("autoBetRunning"),
        )
    }

    // 1) books agreeing
    Text("Books that each say +EV on their own", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_BOOKS_CHOICES, s.autoBetBooks, AutoBetText::booksLabel) { v -> onUpdate { it.copy(autoBetBooks = v) } }

    // 2) the smallest edge, a preset or typed
    Text("Smallest edge at Novig's price now", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_MIN_EV_CHOICES, s.autoBetMinEv, AutoBetText::evLabel, equal = { a, b -> abs(a - b) < 1e-9 }) { v -> onUpdate { it.copy(autoBetMinEv = v) } }
    var evText by remember(s.autoBetMinEv) { mutableStateOf(AutoBetText.trim(s.autoBetMinEv * 100)) }
    val evTyped = evText.toDoubleOrNull()
    OutlinedTextField(
        value = evText,
        onValueChange = { t ->
            evText = t.filter { it.isDigit() || it == '.' }.take(6)
            evText.toDoubleOrNull()?.takeIf { it >= AutoBet.MIN_EV_FLOOR * 100 && it <= 50.0 }?.let { v -> onUpdate { it.copy(autoBetMinEv = v / 100.0) } }
        },
        label = { Text("Or type your own minimum EV %") },
        isError = evTyped != null && evTyped < AutoBet.MIN_EV_FLOOR * 100,
        supportingText = { if (evTyped != null && evTyped < AutoBet.MIN_EV_FLOOR * 100) Text("At least ${AutoBetText.trim(AutoBet.MIN_EV_FLOOR * 100)}%") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("autoBetMinEvField"),
    )

    // 5) books offering both sides
    Text("Books that must price both sides", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_TWO_SIDED_CHOICES, s.autoBetTwoSided, { "$it" }) { v -> onUpdate { it.copy(autoBetTwoSided = v) } }

    // 3) CNO only: fixed
    Text(
        "Which scanner: CrazyNinjaOdds' only. Vigilant's own scan and ParlayAPI's picks are never bet automatically, and neither is a bet whose edge is over ${Format.percent(AutoBet.MAX_SANE_EV, 0)} (that high is usually a stale or mismatched price: place it by hand if you trust it).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
    )

    // 4) the stake
    Text("Amount per bet", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(AutoBetStake.entries.toList(), s.autoBetStake, { it.label }) { v -> onUpdate { it.copy(autoBetStake = v) } }
    if (s.autoBetStake == AutoBetStake.CUSTOM) {
        var custom by remember(s.autoBetCustomStake) { mutableStateOf(WalletAmount.text(s.autoBetCustomStake)) }
        OutlinedTextField(
            value = custom,
            onValueChange = { t ->
                custom = t.filter { it.isDigit() || it == '.' }.take(8)
                custom.toDoubleOrNull()?.takeIf { it > 0 }?.let { v -> onUpdate { it.copy(autoBetCustomStake = v) } }
            },
            label = { Text("Amount $") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("autoBetCustomStake"),
        )
    }
    AutoBetText.kellyNote(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

    // 6) the most per bet, typed
    var max by remember(s.autoBetMaxStake) { mutableStateOf(WalletAmount.text(s.autoBetMaxStake)) }
    OutlinedTextField(
        value = max,
        onValueChange = { t ->
            max = t.filter { it.isDigit() || it == '.' }.take(8)
            max.toDoubleOrNull()?.takeIf { it > 0 }?.let { v -> onUpdate { it.copy(autoBetMaxStake = v) } }
        },
        label = { Text("Most to stake on one bet $") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("autoBetMaxStake"),
    )

    // 7) the check interval: the background CNO scan's own choices
    Text("Check every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_SCAN_SECONDS_CHOICES, s.autoScanSeconds, ScanSettings::intervalLabel) { v -> onUpdate { it.copy(autoScanSeconds = v) } }
    Text(
        "The same choice as Settings › Scan › Background auto-scan: auto-bet runs inside each background CNO scan, so it checks as often as that scans. " +
            "Faster means a bet is placed sooner after CNO lists it.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // The wallet and what the last check did.
    Text(
        "Vigilant wallet " + (balance?.let { Format.money(it) } ?: "not read yet") + ". It stops placing bets when the wallet can't fund a ${Format.money(AutoBet.MIN_STAKE)} bet, " +
            "and holds each bet to what's left. Held to your daily limit of ${Format.money(s.apiMaxPerDay)} for API bets (above), and your Novig location check " +
            "(open the Novig app at least every 3 days; no VPN).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp).testTag("autoBetWallet"),
    )
    val status = state.autoBetStatus
    if (s.autoBet || status.lastRunMs != null) {
        val now = remember(status) { System.currentTimeMillis() }
        Text(AutoBettor.line(status, now), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("autoBetStatus"))
        if (status.placedSinceStart > 0) {
            Text(
                "Placed since Vigilant started: ${status.placedSinceStart} bet${if (status.placedSinceStart == 1) "" else "s"}, ${Format.money(status.stakedSinceStart)}. Every one is in the Tracker, marked Auto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, equal: (T, T) -> Boolean = { a, b -> a == b }, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = equal(o, selected), onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}
