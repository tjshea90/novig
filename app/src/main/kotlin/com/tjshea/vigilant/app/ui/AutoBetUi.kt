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
import androidx.compose.material3.OutlinedButton
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
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.novig.trading.AutoBet
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.scanner.SharpBookChoice
import java.util.Locale
import kotlin.math.abs

/** The auto-bet's sentences, free of Compose so they're testable. */
object AutoBetText {

    /** "+3%", "+3.25%", "+2.5%". */
    fun evLabel(ev: Double): String = "+" + trim(ev * 100) + "%"

    /** "3", "3.25", "2.5": no trailing zeros. */
    fun trim(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    fun booksLabel(n: Int): String = if (n >= 5) "5+" else "$n"

    /** A longest-odds choice: "+130", or "No limit". */
    fun oddsLabel(maxOdds: Int): String = if (maxOdds <= 0) "No limit" else "+$maxOdds"

    /** What each stake choice means, for the confirm and the hint. */
    fun stakeText(s: ScanSettings): String = when (s.autoBetStake) {
        AutoBetStake.EIGHTH_KELLY, AutoBetStake.QUARTER_KELLY, AutoBetStake.HALF_KELLY -> "its ${s.autoBetStake.label} stake of your ${Format.money(s.bankroll)} bankroll"
        AutoBetStake.ONE_DOLLAR -> "$1"
        AutoBetStake.CUSTOM -> Format.money(s.autoBetCustomStake)
    }

    /** What the "every book must agree" switch means at these settings. */
    fun allAgreeNote(s: ScanSettings): String =
        if (s.autoBetAllAgree) {
            "On: a bet passes only if EVERY book that prices both sides of it says +EV on its own (\"5 of 5 books agree\" in the notification), and at least " +
                "${AutoBet.rules(s).minBooks} of them. A book that lists only one side (some sportsbooks list only the Over) can't be judged and isn't counted. " +
                "More books on a bet's page means a harder test, so fewer bets pass."
        } else {
            "Off: the number above is the fewest books that must agree, and the others may disagree (3 of 5 passes a 3)."
        }

    /** The criteria in one sentence, as the confirm and the status say them. */
    fun criteria(s: ScanSettings): String {
        val r = AutoBet.rules(s)
        val agreeing = if (r.allAgree) "every book that prices both sides agreeing it's +EV on their own (and at least ${r.minBooks} of them)"
        else "at least ${r.minBooks} book${if (r.minBooks == 1) "" else "s"} agreeing it's +EV on their own"
        return "$agreeing, ${r.twoSided} pricing both sides, an edge of " +
            "${evLabel(r.minEv)} or more at Novig's price now" + (if (r.maxOdds > 0) ", odds no longer than ${oddsLabel(r.maxOdds)}" else "") +
            ", staking ${stakeText(s)} (never over ${Format.money(r.maxStake)})"
    }

    /** The confirm's whole text. */
    fun confirm(s: ScanSettings, balance: Double?): String =
        "Vigilant will place REAL bets from your Vigilant wallet" + (balance?.let { " (${Format.money(it)})" } ?: "") + " with nobody asking you, " +
            "each time CrazyNinjaOdds' background scan finds a bet with ${criteria(s)}. It never bets a game that has started, never more than " +
            "${Format.money(s.apiMaxPerDay)} in a day across API bets, and stops when the wallet is empty (under a cent) or if an order's answer is lost. " +
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
            "and to what's in the wallet, floored to the cent; under a cent is skipped, never rounded up (a Kelly stake under a dollar is placed as it is). At the same edge a longer price stakes less " +
            "(a +300 bet gets a third of a +100 bet's stake), but nothing caps the odds itself: that is the longest-odds limit above."
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
fun AutoBetSection(
    state: UiState,
    /** Why a bet's notification wouldn't show on this phone (Android's permission or settings), or null ([AutoBetNotes.blocked]). */
    notificationsBlocked: String? = null,
    /** Posts a made-up auto-bet notification on the real channel; false when it couldn't be posted ([AutoBetNotes.sample]). */
    onTestNotification: () -> Boolean = { true },
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
) {
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
    // Every book scanned must agree (Tj, 2026-10-01: "require that every sports book scanned agrees the bet is positive EV (for example, 5 of 5 books agree positive EV)")
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = s.autoBetAllAgree, role = Role.Switch,
            onValueChange = { on -> onUpdate { it.copy(autoBetAllAgree = on) } },
        ).padding(top = 4.dp, bottom = 2.dp).testTag("autoBetAllAgree"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Every book scanned must agree (5 of 5, not 3 of 5)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = s.autoBetAllAgree, onCheckedChange = null)
    }
    Text(
        AutoBetText.allAgreeNote(s),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("autoBetAllAgreeNote"),
    )

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

    // The longest odds, a preset or typed (Tj, 2026-10-01: "I don't want it to bet anything that is more of a longshot than +130")
    Text("Longest odds to bet", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_MAX_ODDS_CHOICES, s.autoBetMaxOdds, AutoBetText::oddsLabel) { v -> onUpdate { it.copy(autoBetMaxOdds = v) } }
    var oddsText by remember(s.autoBetMaxOdds) { mutableStateOf(if (s.autoBetMaxOdds > 0) "${s.autoBetMaxOdds}" else "") }
    val oddsTyped = oddsText.toIntOrNull()
    val oddsBad = oddsText.isNotEmpty() && (oddsTyped == null || oddsTyped < AutoBet.MIN_MAX_ODDS)
    OutlinedTextField(
        value = oddsText,
        onValueChange = { t ->
            oddsText = t.filter { it.isDigit() }.take(5)
            oddsText.toIntOrNull()?.takeIf { it >= AutoBet.MIN_MAX_ODDS }?.let { v -> onUpdate { it.copy(autoBetMaxOdds = v) } }
        },
        label = { Text("Or type your own longest odds (+)") },
        isError = oddsBad,
        supportingText = { if (oddsBad) Text("+${AutoBet.MIN_MAX_ODDS} (even money) or more; pick No limit for none") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("autoBetMaxOddsField"),
    )
    Text(
        "Nothing longer than this is bet, whatever the amount; favorites (−110, −150 …) always pass. It is checked on the bet's price when it's found and " +
            "again on Novig's order book just before the order, so a price that drifts out past it is skipped. Kelly stakes already shrink as odds grow, " +
            "but a $1 or typed amount doesn't: this is what keeps those off longshots. The CrazyNinjaOdds page's own Max odds filter (Settings › CNO) also still applies.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("autoBetMaxOddsHint"),
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
    if (s.autoScanSeconds < 15) {
        Text(
            "5 sec reads CrazyNinjaOdds about 12 times a minute (never two reads within 3 seconds; if CNO refuses, the scan waits 10 minutes). CNO's terms let it block " +
                "addresses that read too much, so use it only if you want the fastest catch; CNO itself refreshes its odds about once a minute. Vigilant's own scan still " +
                "starts at most every 4 minutes. In Doze (screen off and still) Android may delay the alarms.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("autoBetFastNote"),
        )
    }

    // The notification every bet gets (Tj, 2026-10-01: "a push notification for every automatic bet, so I can see each bet placed and the stake and EV")
    Text("Notifications", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Text(
        "Every bet auto-bet places gets its own pop-up notification: the stake and the EV in the title, then the odds, how many books agree, the game and what's left " +
            "in the wallet. Tap it to open Vigilant; every bet is also in the Tracker, marked Auto.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (s.autoBet && notificationsBlocked != null) {
        Text(
            "Auto-bet is on but its notifications can't show: $notificationsBlocked.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.padding(top = 4.dp).testTag("autoBetNotifBlocked"),
        )
    }
    var tested by remember { mutableStateOf<Boolean?>(null) }
    OutlinedButton(onClick = { tested = onTestNotification() }, modifier = Modifier.padding(top = 4.dp).testTag("autoBetTestNote")) { Text("Send a test notification") }
    tested?.let { posted ->
        Text(
            if (posted) "Sent: a test bet should pop up now. If nothing appeared, open Android Settings › Apps › Vigilant › Notifications and turn on \"Auto-bet bets placed\"."
            else "Couldn't send it: Android isn't allowing Vigilant to post notifications (Android Settings › Apps › Vigilant › Notifications).",
            style = MaterialTheme.typography.bodySmall, color = if (posted) MaterialTheme.colorScheme.onSurfaceVariant else Edge.colors.negative,
            modifier = Modifier.testTag("autoBetTestNoteResult"),
        )
    }

    // The wallet and what the last check did.
    Text(
        "Vigilant wallet " + (balance?.let { Format.money(it) } ?: "not read yet") + ". It stops placing bets when the wallet is empty (under a cent), " +
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

/** The sharp-book confirmation's sentences, free of Compose so they're testable. */
object SharpConfirmText {

    /** "1 min", "3 min": the quote age choices. */
    fun ageLabel(seconds: Int): String = ScanSettings.intervalLabel(seconds)

    /** The edge choice: "Any +EV", "+1%". */
    fun edgeLabel(minEv: Double): String = if (minEv <= 0.0) "Any +EV" else AutoBetText.evLabel(minEv)

    /** The edge in a sentence: "any +EV", "+2%". */
    private fun edgeInWords(minEv: Double): String = if (minEv <= 0.0) "any +EV" else AutoBetText.evLabel(minEv)

    /**
     * The Pinnacle feeds Tj has switched on with a key, in the order they're asked (the same conditions as `referenceSources`); empty when none.
     * [keys]: how many keys he has saved for a provider.
     */
    fun feedsOn(s: ScanSettings, keys: (ApiProvider) -> Int): List<String> = buildList {
        if (s.usePinnacle && (keys(ApiProvider.PINNWIRE) > 0 || keys(ApiProvider.PINNAPI) > 0)) add("PinnWire / pinnapi")
        if (s.usePropLine && keys(ApiProvider.PROPLINE) > 0) add("PropLine")
        if (s.useParlay && keys(ApiProvider.PARLAY) > 0) add("ParlayAPI")
        if (s.useOddsApi && keys(ApiProvider.THE_ODDS_API) > 0) add("The Odds API")
    }

    fun intro(): String =
        "On top of every other criterion: a sharp book (Pinnacle) must show the bet is +EV on its own price. Its two sides for the exact same game, market, line and " +
            "side are devigged (worst case of four methods) and compared with Novig's price now; the quote must be newer than the limit below, and a sharp book that says " +
            "it isn't +EV vetoes the bet. Each switch is off until you turn it on."

    fun feedsNote(s: ScanSettings, feeds: List<String>): String = when {
        feeds.isEmpty() && !s.sharpConfirmViaCno ->
            "No Pinnacle feed is on with a key (Settings › Usage & keys: PinnWire or pinnapi, PropLine, ParlayAPI), so with a switch on nothing can be confirmed: " +
                "the auto-bet skips every bet and no CNO alert is sent. Or switch on \"Also take Pinnacle's price from CNO's page\" below."
        feeds.isEmpty() -> "No Pinnacle feed is on with a key: only CNO's page can confirm."
        else -> "Asked in this order, and the first that has the bet answers: ${feeds.joinToString(", ")}. Only a bet that already passed every other criterion is looked up " +
            "(a league's board is kept a minute), so a quiet cycle costs nothing; each feed spends its own allowance as a scan does."
    }

    fun viaCnoNote(s: ScanSettings): String =
        if (s.sharpConfirmViaCno) {
            "On: Pinnacle's column on CNO's game page can confirm a bet too (free, already read). CNO dates the whole page, not each book's quote, so this is a weaker proof than a feed's own time."
        } else {
            "Off: only a Pinnacle feed, with the quote's own time, confirms. CNO's page can still veto: a fresh Pinnacle column there that says the bet isn't +EV skips it without a feed call."
        }

    fun confirmNote(s: ScanSettings): String? {
        val where = listOfNotNull("the auto-bet".takeIf { s.sharpConfirmAutoBet }, "CNO's push alerts".takeIf { s.sharpConfirmAlerts }).joinToString(" and ")
        if (where.isEmpty()) return null
        return "For $where: ${s.sharpConfirmBooks.displayName}'s own price, at most ${ageLabel(s.sharpConfirmMaxAgeSeconds)} old, must show ${edgeInWords(s.sharpConfirmMinEv)} at Novig's price now."
    }
}

/**
 * Settings › Betting › Sharp-book confirmation (Tj, 2026-10-02: "require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle … for the cno
 * scanner and auto bet feature"). One switch for the auto-bet, one for CNO's push alerts, and the shared criteria. Both off by default.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharpConfirmSection(state: UiState, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val feeds = SharpConfirmText.feedsOn(s) { state.keysOf(it).size }
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    SectionTitle("Sharp-book confirmation")
    Text(SharpConfirmText.intro(), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp))
    for ((tag, title, on, set) in listOf(
        Quad("sharpConfirmAutoBet", "Auto-bet: require a sharp book to confirm +EV", s.sharpConfirmAutoBet) { v: Boolean -> onUpdate { it.copy(sharpConfirmAutoBet = v) } },
        Quad("sharpConfirmAlerts", "CNO push alerts: require a sharp book to confirm +EV", s.sharpConfirmAlerts) { v: Boolean -> onUpdate { it.copy(sharpConfirmAlerts = v) } },
    )) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = on, role = Role.Switch, onValueChange = set).padding(vertical = 6.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
    if (s.sharpConfirmAutoBet || s.sharpConfirmAlerts) {
        Text("Sharp books", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Chips(SharpBookChoice.entries.toList(), s.sharpConfirmBooks, { it.displayName }) { v -> onUpdate { it.copy(sharpConfirmBooks = v) } }
        Text("Newest quote allowed", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Chips(ScanSettings.SHARP_MAX_AGE_CHOICES, s.sharpConfirmMaxAgeSeconds, SharpConfirmText::ageLabel) { v -> onUpdate { it.copy(sharpConfirmMaxAgeSeconds = v) } }
        Text("Edge the sharp book must show at Novig's price now", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Chips(ScanSettings.SHARP_MIN_EV_CHOICES, s.sharpConfirmMinEv, SharpConfirmText::edgeLabel) { v -> onUpdate { it.copy(sharpConfirmMinEv = v) } }
        Row(
            Modifier.fillMaxWidth().toggleable(value = s.sharpConfirmViaCno, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(sharpConfirmViaCno = v) } })
                .padding(top = 8.dp, bottom = 2.dp).testTag("sharpConfirmViaCno"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Also take Pinnacle's price from CNO's page", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = s.sharpConfirmViaCno, onCheckedChange = null)
        }
        Text(SharpConfirmText.viaCnoNote(s), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.testTag("sharpConfirmViaCnoNote"))
        SharpConfirmText.confirmNote(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(top = 4.dp).testTag("sharpConfirmNote")) }
        Text(
            SharpConfirmText.feedsNote(s, feeds), style = MaterialTheme.typography.bodySmall,
            color = if (feeds.isEmpty() && !s.sharpConfirmViaCno) Edge.colors.warning else subtle, modifier = Modifier.padding(top = 4.dp).testTag("sharpConfirmFeeds"),
        )
    }
}

private data class Quad(val tag: String, val title: String, val on: Boolean, val set: (Boolean) -> Unit)
