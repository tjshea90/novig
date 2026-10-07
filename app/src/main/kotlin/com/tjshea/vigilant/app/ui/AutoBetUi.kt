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
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.scanner.SharpMode
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

    /** "No limit", "−200", "+100 or longer": the shortest-odds choices (a positive one means underdogs only). */
    fun minOddsLabel(minOdds: Int): String = when {
        minOdds == 0 -> "No limit"
        minOdds < 0 -> "−${-minOdds}"
        else -> "+$minOdds or longer (plus money only)"
    }

    /** "None", "+1.0 point": the choices for the extra edge a favorite needs. */
    fun favouriteEvLabel(extra: Double): String = if (extra <= 1e-9) "None" else "+" + trim(extra * 100) + (if (abs(extra * 100 - 1.0) < 1e-9) " point" else " points")

    /** What the favorite bar does at [s]'s settings. */
    fun favouriteNote(s: ScanSettings): String {
        val extra = s.autoBetFavouriteExtraEv
        if (extra <= 1e-9) return "Off: a favorite needs the same edge as any other bet."
        val bar = s.autoBetMinEv + extra
        return "A favorite (odds shorter than even money, −101 or shorter) needs ${evLabel(bar)} or more, ${trim(extra * 100)} more than the ${evLabel(s.autoBetMinEv)} minimum. " +
            "In the first three days of data the edge a bet kept to the close fell as the price got shorter."
    }

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
            (if (r.minOdds < 0) ", odds no shorter than ${minOddsLabel(r.minOdds)}" else "") +
            (if (r.kinds.size < BetKind.entries.size) ", only ${r.kinds.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() }}" else "") +
            (if (s.sharpAutoBet == SharpMode.VETO) (if (s.sharpVetoMinEv <= 0.0) ", unless the sharpest book for it says it isn't +EV" else ", unless the sharpest book for it gives it under ${evLabel(s.sharpVetoMinEv)}") else if (s.sharpAutoBet == SharpMode.CONFIRM) ", confirmed by a sharp book" else "") +
            ", staking ${stakeText(s)} (never over ${Format.money(r.maxStake)})"
    }

    /** The confirm's whole text. */
    fun confirm(s: ScanSettings, balance: Double?): String =
        "Vigilant will place REAL bets from your Vigilant wallet" + (balance?.let { " (${Format.money(it)})" } ?: "") + " with nobody asking you, " +
            (if (s.pinnacleOnly) "each time a Vigilant scan finds a bet at Novig that beats Pinnacle's devigged price by ${evLabel(AutoBet.rules(s).minEv)} or more, on a Pinnacle price read within " +
                "${PinnacleOnlyText.ageLabel(s.pinnacleMaxAgeSeconds)} of the order (Pinnacle only is on)"
            else "each time CrazyNinjaOdds' background scan finds a bet with ${criteria(s)}") + ". It never bets a game that has started, never more than " +
            "${Format.money(s.apiMaxPerDay)} in a day across API bets${if (s.apiMaxPerGame > 0.0) " and never more than ${Format.money(s.apiMaxPerGame)} at risk on one game" else ""}, and stops when the wallet is empty (under a cent) or if an order's answer is lost. " +
            "Every bet is tracked like one you placed yourself, and you get a notification for each."

    /** Why auto-bet isn't running right now though it's on (or what turning it on needs), or null when it is running. */
    fun whyNotRunning(state: UiState): String? {
        val s = state.settings
        return when {
            !AppBook.isNovig -> "Auto-bet is for Novig."
            !state.betting.enabled -> "Betting through Novig's API isn't set up yet: connect your Novig key and turn on betting first (the wallet is where auto-bets come from)."
            s.autoBetHalted != null -> "Stopped: ${s.autoBetHalted}"
            s.killed -> "Everything is stopped by the STOP button: auto-bet waits for Resume (the red bar at the bottom)."
            s.paused -> "Scanning is paused (the ⏸ button): auto-bet waits for it."
            s.scanner == ScannerMode.VIGILANT && !s.pinnacleOnly -> "The scanner is Vigilant only, so CrazyNinjaOdds is asleep and auto-bet has nothing to read."
            s.autoScan == AutoScanMode.OFF -> "The background scan is off, and auto-bet runs inside it."
            else -> null
        }
    }

    /** The one tap that fixes [whyNotRunning]. */
    enum class Fix { SET_UP_BETTING, RESUME_SCANNING, SCANNER, BACKGROUND_SCAN }

    fun fixFor(state: UiState): Fix? {
        val s = state.settings
        return when {
            !AppBook.isNovig -> null
            !state.betting.enabled -> Fix.SET_UP_BETTING
            s.autoBetHalted != null -> null
            // The kill switch has its own Resume on the red bar; ▶ here can't lift it.
            s.killed -> null
            s.paused -> Fix.RESUME_SCANNING
            s.scanner == ScannerMode.VIGILANT && !s.pinnacleOnly -> Fix.SCANNER
            s.autoScan == AutoScanMode.OFF -> Fix.BACKGROUND_SCAN
            else -> null
        }
    }

    /** What it does at these settings, when it's running. */
    fun running(s: ScanSettings): String =
        if (s.pinnacleOnly) {
            "Running in Pinnacle only with the background scan, every ${ScanSettings.intervalLabel(s.autoScanSeconds)}: after each Vigilant scan, each bet that beats Pinnacle's devigged price by " +
                "${evLabel(AutoBet.rules(s).minEv)} or more at Novig's price now is placed, best edge first, up to ${AutoBet.MAX_PER_CYCLE} a pass, on a Pinnacle price no older than " +
                "${PinnacleOnlyText.ageLabel(s.pinnacleMaxAgeSeconds)}."
        } else
        "Running with the background CNO scan, every ${ScanSettings.intervalLabel(s.autoScanSeconds)}: each bet that passes is placed best edge first, up to ${AutoBet.MAX_PER_CYCLE} a check."

    /** The Kelly sizing note, when a Kelly stake is chosen. */
    fun kellyNote(s: ScanSettings): String? {
        val f = s.autoBetStake.kelly ?: return null
        return "Kelly sizing uses your bankroll (${Format.money(s.bankroll)}, Settings › Betting & Novig account): bankroll × ${Format.kellyLabel(f).removeSuffix(" Kelly")} × " +
            "(fair chance − price) ÷ (1 − price), so it changes with each bet's odds and edge. It's held to your most per bet, to what Novig has for sale at +EV " +
            "and to what's in the wallet, floored to the cent; under a cent is skipped, never rounded up (a Kelly stake under a dollar is placed as it is). At the same edge a longer price stakes less " +
            "(a +300 bet gets a third of a +100 bet's stake), but nothing caps the odds itself: that is the longest-odds limit (What it bets)."
    }
}

/**
 * The Auto-bet tab's content (Tj, 2026-10-01: "automatically bet each bet without me doing anything at all, including … in the background as the cno scanner
 * is on in the background"; 2026-10-02 ~17:55Z: "maybe make the auto bet feature its own section instead of buried in the settings"). Top to bottom: the
 * switch and what it's doing (with a one-tap fix when something stops it), [presets], what it bets, the sharp-book veto, how much, how often, and its
 * notifications. Off by default; turning it on asks once, plainly. [onOpenSettings] opens a Settings page (Betting, to set up the wallet).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoBetSection(
    state: UiState,
    /** Why a bet's notification wouldn't show on this phone (Android's permission or settings), or null ([AutoBetNotes.blocked]). */
    notificationsBlocked: String? = null,
    /** Posts a made-up auto-bet notification on the real channel; false when it couldn't be posted ([AutoBetNotes.sample]). */
    onTestNotification: () -> Boolean = { true },
    onOpenSettings: (SettingsPage) -> Unit = {},
    presets: @Composable () -> Unit = {},
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
) {
    val s = state.settings
    var confirming by remember { mutableStateOf(false) }
    val balance = state.betting.balance ?: state.autoBetStatus.balance
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        (if (s.pinnacleOnly) "Places each Novig bet that beats Pinnacle's devigged price and passes your rules for you"
        else "Places each CrazyNinjaOdds bet that passes your rules for you") +
            ", through Novig's API from your Vigilant wallet, with nobody confirming: in the " +
            "background, with Vigilant open or closed. Pregame only. Once on, it stays on until you turn it off (only a phone restart turns it off by itself).",
        style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp),
    )
    PinnacleOnlyRows(s, onUpdate)
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

    // Why it can't run (on or not), with the one tap that fixes it; else what it does.
    val why = AutoBetText.whyNotRunning(state)
    // (A halt has its own red block and Resume above: not said twice.)
    if (s.autoBetHalted == null && (s.autoBet || (why != null && !state.betting.enabled))) {
        Text(
            why ?: AutoBetText.running(s),
            style = MaterialTheme.typography.bodySmall,
            color = if (why != null) Edge.colors.warning else subtle,
            modifier = Modifier.padding(vertical = 4.dp).testTag("autoBetRunning"),
        )
        when (AutoBetText.fixFor(state)) {
            AutoBetText.Fix.SET_UP_BETTING -> OutlinedButton(onClick = { onOpenSettings(SettingsPage.BETTING) }, modifier = Modifier.testTag("autoBetFixBetting")) { Text("Set up betting") }
            AutoBetText.Fix.BACKGROUND_SCAN -> OutlinedButton(onClick = { onUpdate { BackgroundScan.set(it, true) } }, modifier = Modifier.testTag("autoBetFixBackground")) { Text("Turn on the background scan") }
            AutoBetText.Fix.RESUME_SCANNING -> OutlinedButton(onClick = { onUpdate { it.copy(pausedByHand = false) } }, modifier = Modifier.testTag("autoBetFixPause")) { Text("Resume scanning") }
            AutoBetText.Fix.SCANNER -> OutlinedButton(onClick = { onUpdate { it.copy(scanner = ScannerMode.BOTH) } }, modifier = Modifier.testTag("autoBetFixScanner")) { Text("Turn CrazyNinjaOdds back on") }
            null -> Unit
        }
    }

    // What it has done: the wallet, the last check, the sharp tally, bets since the app started.
    Text(
        "Vigilant wallet " + (balance?.let { Format.money(it) } ?: "not read yet") + ". It stops placing bets when the wallet is empty (under a cent), " +
            "and holds each bet to what's left, to your daily limit of ${Format.money(s.apiMaxPerDay)} (Settings › Betting & Novig account), and to your Novig " +
            "location check (open the Novig app at least every 3 days; no VPN).",
        style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(top = 4.dp).testTag("autoBetWallet"),
    )
    val status = state.autoBetStatus
    if (s.autoBet || status.lastRunMs != null) {
        val now = remember(status) { System.currentTimeMillis() }
        Text(AutoBettor.line(status, now, s.pinnacleOnly), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("autoBetStatus"))
        // What the sharp-book check said, bet by bet (Tj, 2026-10-02 16:05Z: "Is it getting sharp book pricing?").
        if (s.sharpAutoBet != SharpMode.OFF) {
            AutoBettor.sharpLine(status, s.sharpConfirmBooks.displayName)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("autoBetSharpTally"))
            }
        }
        if (status.placedSinceStart > 0) {
            Text(
                "Placed since Vigilant started: ${status.placedSinceStart} bet${if (status.placedSinceStart == 1) "" else "s"}, ${Format.money(status.stakedSinceStart)}. Every one is in the Tracker, marked Auto.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    presets()

    // ---- What it bets --------------------------------------------------------------------------------
    SectionTitle("What it bets")
    Text("Smallest edge (EV) at Novig's price now", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
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

    Text(
        "EV (expected value) is how much a bet should return over time above break-even: 3% is about 3¢ per \$1 bet in the long run. Estimated edges run " +
            "high, so a little room above the edge you want keeps the real one positive.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )
    Shadowed.autoBetEdge(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("autoBetEdgeShadowed")) }

    if (s.pinnacleOnly) {
        // The three book-count rules below judge CrazyNinjaOdds' bets by the books on their game page; with Pinnacle the one book, they don't apply.
        Text(
            "Pinnacle only is on: the book-count rules below (books agreeing, every book, books pricing both sides) don't apply, because Pinnacle is the one book. " +
                "What applies is your smallest edge above, the odds limits, the kinds of bet, how much, and the most on one game or in a day.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.padding(top = 8.dp).testTag("autoBetPinnacleNote"),
        )
    }
    Text("Books that each say +EV on their own", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_BOOKS_CHOICES, s.autoBetBooks, AutoBetText::booksLabel, modifier = Modifier.testTag("autoBetBooksChips")) { v -> onUpdate { it.copy(autoBetBooks = v) } }
    TypedIntField(NumberSpecs.count("books", 2, 12), s.autoBetBooks, "autoBetBooksField", none = { false }) { v -> onUpdate { it.copy(autoBetBooks = v) } }
    Text(
        "Each sportsbook on the bet's page, with its own profit taken out, must say Novig's price beats the true odds. More books agreeing means the edge " +
            "isn't one book's mistake.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )
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

    Text("Books that must price both sides", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_TWO_SIDED_CHOICES, s.autoBetTwoSided, { "$it" }, modifier = Modifier.testTag("autoBetTwoSidedChips")) { v -> onUpdate { it.copy(autoBetTwoSided = v) } }
    TypedIntField(NumberSpecs.count("books", 1, 12), s.autoBetTwoSided, "autoBetTwoSidedField", none = { false }) { v -> onUpdate { it.copy(autoBetTwoSided = v) } }
    Text(
        "Only books that price both sides count: with just one side, a book's profit can't be taken out to check the bet.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
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
        "Nothing longer than this is bet; favorites (−110, −150 …) always pass. Long shots are where fake edges hide. It's checked when the bet is found and " +
            "again on Novig's order book just before the order. Kelly stakes already shrink as odds grow, but a \$1 or typed amount doesn't: this keeps those off long shots.",
        style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.testTag("autoBetMaxOddsHint"),
    )
    Shadowed.autoBetOdds(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("autoBetOddsShadowed")) }
    Text("Extra edge a favorite needs", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_FAVOURITE_EV_CHOICES, s.autoBetFavouriteExtraEv, AutoBetText::favouriteEvLabel, equal = { a, b -> abs(a - b) < 1e-9 }) { v -> onUpdate { it.copy(autoBetFavouriteExtraEv = v) } }
    TypedNumberField(NumberSpecs.percent("extra edge for favorites", 0.0, 20.0), AutoBetText.trim(s.autoBetFavouriteExtraEv * 100), "autoBetFavouriteEvField") { v -> onUpdate { it.copy(autoBetFavouriteExtraEv = v / 100.0) } }
    Text(AutoBetText.favouriteNote(s), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.testTag("autoBetFavouriteNote"))
    Text("Shortest odds to bet", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_BET_MIN_ODDS_CHOICES, s.autoBetMinOdds, AutoBetText::minOddsLabel) { v -> onUpdate { it.copy(autoBetMinOdds = v) } }
    TypedNumberField(NumberSpecs.SHORTEST_ODDS, oddsShown(s.autoBetMinOdds), "autoBetMinOddsField") { v -> onUpdate { it.copy(autoBetMinOdds = v.toInt()) } }
    Text(
        if (s.autoBetMinOdds > 0) "Underdogs only: nothing shorter than ${com.tjshea.vigilant.engine.Odds.formatAmerican(s.autoBetMinOdds)}, so every favorite is skipped. Checked when the bet is found and again on Novig's order book just before the order."
        else "Heavy favorites (−250 and shorter) risk a lot to win a little; one bad price wipes out many small wins. Checked when the bet is found and again on Novig's order book just before the order.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )

    (Shadowed.oddsRange(s.autoBetMinOdds, s.autoBetMaxOdds) ?: Shadowed.autoBetShortOdds(s))?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("autoBetOddsRange"))
    }
    // The kinds of bet (RESEARCH.md §66: a preset sets them; Tj can change them here).
    Text("Kinds of bet to place", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("autoBetKinds")) {
        BetKind.entries.forEach { k ->
            FilterChip(
                selected = k in s.autoBetKinds,
                onClick = { onUpdate { it.copy(autoBetKinds = if (k in it.autoBetKinds) it.autoBetKinds - k else it.autoBetKinds + k) } },
                label = { Text(k.label) },
            )
        }
    }
    if (s.autoBetKinds.isEmpty()) {
        Text(
            "No kind of bet picked: the auto-bet places nothing until you pick one (or apply a preset).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("autoBetNoKinds"),
        )
    }

    Text(
        "Which scanner: CrazyNinjaOdds' only. Vigilant's own scan and ParlayAPI's picks are never bet automatically, and neither is a bet whose edge is over ${Format.percent(AutoBet.MAX_SANE_EV, 0)} (that high is usually a stale or mismatched price: place it by hand if you trust it).",
        style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(top = 8.dp),
    )

    // ---- The sharp books' say ---------------------------------------------------------------------------
    SharpVetoSection(state, forAlerts = false, onUpdate = onUpdate)
    // ---- Trap bets (RESEARCH.md §71) --------------------------------------------------------------------
    TrapGuardSection(s, showMove = true, tag = "autoBet", onUpdate = onUpdate)

    // ---- How much -----------------------------------------------------------------------------------
    SectionTitle("How much")
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
    AutoBetText.kellyNote(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = subtle) }
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

    Text(
        "The most one auto-bet can stake (bets you place yourself have their own most per bet, in Settings › Betting & Novig account). The daily limit there " +
            "(${Format.money(s.apiMaxPerDay)}) covers auto-bets and yours together" +
            (if (s.apiMaxPerGame > 0.0) ", and so does the most on one game (${Format.money(s.apiMaxPerGame)}: every line, prop and bid of a game added up)." else "."),
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )

    // ---- How often ----------------------------------------------------------------------------------
    SectionTitle("How often")
    Text("Check every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_SCAN_SECONDS_CHOICES, s.autoScanSeconds, ScanSettings::intervalLabel) { v -> onUpdate { it.copy(autoScanSeconds = v) } }
    TypedIntField(NumberSpecs.time("seconds", 5, 3600), s.autoScanSeconds, "autoScanSecondsField", none = { false }) { v -> onUpdate { it.copy(autoScanSeconds = v) } }
    Text(
        "Auto-bet runs inside the background scan, so this is the same setting as Settings › Scanning › Background scan: one choice, two places. Faster " +
            "means a bet is placed sooner after CrazyNinjaOdds lists it, before the price moves.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )
    if (s.autoScanSeconds < 15) {
        Text(
            "5 sec reads CrazyNinjaOdds about 12 times a minute (never two reads within 3 seconds; if CNO refuses, the scan waits 10 minutes). CNO's terms let it block " +
                "addresses that read too much, so use it only if you want the fastest catch; CNO itself refreshes its odds about once a minute. Vigilant's own scan still " +
                "starts at most every 4 minutes. In Doze (screen off and still) Android may delay the alarms.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("autoBetFastNote"),
        )
    }


    // ---- Auto-lock (Tj, 2026-10-02 ~18:50Z: "include an option to auto bet these bets in addition to whatever the auto bet system already does") ----
    SectionTitle("Lock in profits")
    Text(
        "When the odds move your way after a bet, buying the other side of the same Novig market can guarantee a profit whichever side wins (a lock). " +
            "Vigilant checks your open bets placed through the API at every background scan, using only Novig's own prices. Each lock is one fill-or-kill " +
            "order: it fills completely at a price that keeps the profit, or nothing is bought, and Novig's own record of what you hold is checked first. A lock " +
            "trades a little expected value for certainty (it cashes in the move you already got), so it's off until you turn it on; each bet's sheet in the " +
            "Tracker also offers it by hand.",
        style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp),
    )
    Row(
        Modifier.fillMaxWidth().toggleable(value = s.autoLock, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(autoLock = v) } })
            .padding(vertical = 6.dp).testTag("autoLockSwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Lock in profits automatically", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = s.autoLock, onCheckedChange = null)
    }
    Text("Smallest profit to lock, as a share of what's staked on the bet", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.AUTO_LOCK_MIN_CHOICES, s.autoLockMinPercent, { "${AutoBetText.trim(it * 100)}%" }, equal = { a, b -> abs(a - b) < 1e-9 }, modifier = Modifier.testTag("autoLockMinChips")) { v ->
        onUpdate { it.copy(autoLockMinPercent = v) }
    }
    TypedPercentField(NumberSpecs.percent("smallest profit", 0.1, 50.0), s.autoLockMinPercent, "autoLockMinField") { v -> onUpdate { it.copy(autoLockMinPercent = v) } }
    Text(
        "2% on a \$10 bet: it locks once at least \$0.20 profit is guaranteed whichever side wins. Lower locks sooner and more often for less; higher waits " +
            "for a bigger move, which may never come.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )
    Row(
        Modifier.fillMaxWidth().toggleable(value = s.autoLockLive, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(autoLockLive = v) } })
            .padding(vertical = 6.dp).testTag("autoLockLive"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Also during the game", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = s.autoLockLive, onCheckedChange = null)
    }
    Text(
        "In-game, prices swing the most, so locks are bigger; Novig's in-game fee is counted in, and a line that could push (a whole number) isn't locked then.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )
    Row(
        Modifier.fillMaxWidth().toggleable(value = s.trackerHideLocked, role = Role.Switch, onValueChange = { v -> onUpdate { it.copy(trackerHideLocked = v) } })
            .padding(vertical = 6.dp).testTag("hideLockedSwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Hide locked bets in the Tracker", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = s.trackerHideLocked, onCheckedChange = null)
    }
    Text(
        "A locked market is cashed out (it pays the same whichever side wins), so its bets leave the Tracker's lists and stats; the Tracker's Locked in card " +
            "still counts them, with the profit they locked.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
    )
    if (s.autoLock && s.autoScan == AutoScanMode.OFF) {
        Text("The background scan is off, so auto-lock can't run.", style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("autoLockNoBackground"))
        OutlinedButton(onClick = { onUpdate { BackgroundScan.set(it, true) } }) { Text("Turn on the background scan") }
    }

    // The notification every bet gets (Tj, 2026-10-01: "a push notification for every automatic bet, so I can see each bet placed and the stake and EV")
    SectionTitle("Notifications")
    Text(
        "Every bet auto-bet places gets its own pop-up notification: the stake and the EV in the title, then the odds, how many books agree, the game and what's left " +
            "in the wallet. Tap it to open Vigilant; every bet is also in the Tracker, marked Auto.",
        style = MaterialTheme.typography.bodySmall, color = subtle,
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

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    equal: (T, T) -> Boolean = { a, b -> a == b },
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
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

    /** What the sharp books are, in plain words. */
    fun sharpIntro(): String =
        "Sharp books are the sportsbooks whose odds are the most accurate (they let winning bettors bet big, so their prices get corrected fast). Which ones " +
            "are sharpest depends on the kind of bet."

    /** What each mode does, in plain words. */
    fun modeNote(mode: SharpMode): String = when (mode) {
        SharpMode.OFF -> "Off: the sharp books have no say; the other rules decide."
        SharpMode.VETO -> "Veto (recommended): a bet is skipped when the sharpest book for its kind of bet says it isn't +EV, or gives it less than the bar below. Free: it uses the prices already read."
        SharpMode.CONFIRM -> "Require a confirmation (strict, far fewer bets): a fresh Pinnacle price must also show the bet is +EV. On player props Pinnacle is " +
            "often missing or soft, so most props are skipped."
    }

    fun intro(): String =
        "On top of every other criterion: a sharp book (Pinnacle) must show the bet is +EV on its own price. Its two sides for the exact same game, market, line and " +
            "side are devigged (worst case of four methods) and compared with Novig's price now; the quote must be newer than the limit below, and a sharp book that says " +
            "it isn't +EV vetoes the bet."

    fun feedsNote(s: ScanSettings, feeds: List<String>): String = when {
        feeds.isEmpty() && !s.sharpConfirmViaCno ->
            "No Pinnacle feed is on with a key (Settings › Fair odds & sources: PinnWire or pinnapi, PropLine, ParlayAPI), so nothing can be confirmed: " +
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

    /**
     * The veto in words (Tj, 2026-10-02 17:01Z: "Only skip a bet if the sharpest book for that market says it is not +ev. This must separate types of bets by
     * which books are sharpest for those bet types"): which books decide for each kind of bet ([SharpVeto.ranking]).
     */
    fun vetoNote(): String =
        // (The mode's own note just above says what a veto does; this says who decides, without repeating it.)
        "Who decides: the sharpest of the books pricing both sides on the bet's book page, judged on its own two prices (devigged worst case) at " +
            "Novig's price. None of them on the page: no veto. Sharpest first: player props " +
            names(SharpVeto.ranking(BetKind.PROP, SharpVeto.Sport.FOOTBALL)) + " (MLB props " + names(SharpVeto.ranking(BetKind.PROP, SharpVeto.Sport.BASEBALL)) +
            "); moneylines, spreads, totals and period lines " + names(SharpVeto.ranking(BetKind.SPREAD, SharpVeto.Sport.FOOTBALL)) + " (college " +
            names(SharpVeto.ranking(BetKind.SPREAD, SharpVeto.Sport.COLLEGE_FOOTBALL)) + "; soccer and tennis " +
            names(SharpVeto.ranking(BetKind.SPREAD, SharpVeto.Sport.SOCCER)) + "). Free: no feed is called."

    private fun names(codes: List<String>) = codes.joinToString(", ") { com.tjshea.vigilant.data.cno.CnoBooks.name(it) }

    /**
     * The veto's bar in words ([ScanSettings.sharpVetoMinEv], RESEARCH.md §72): why a sharp book's small edge isn't enough, and that the same bar applies to
     * the auto-bet, CNO's alerts and the bids.
     */
    fun vetoBarNote(minEv: Double): String =
        (if (minEv <= 0.0) "Any +EV on the sharpest book's own price passes. " else "The sharpest book's own price must show at least ${AutoBetText.evLabel(minEv)} at Novig's price, or the bet is skipped. ") +
            "What a bet keeps by the close is about the sharpest book's own edge, not the average's: on 48,394 soccer matches, bets the average called +EV " +
            "kept +0.8% (no better than zero) when the sharp book gave them 0-1%, +1.6% at 1-2%, +2.8% at 2-4%. 1% is recommended. The same bar is used by " +
            "the auto-bet, CNO's alerts and the bids."

    fun confirmNote(s: ScanSettings): String? {
        val where = listOfNotNull("the auto-bet".takeIf { s.sharpAutoBet == SharpMode.CONFIRM }, "CNO's push alerts".takeIf { s.sharpAlerts == SharpMode.CONFIRM }).joinToString(" and ")
        if (where.isEmpty()) return null
        return "For $where: ${s.sharpConfirmBooks.displayName}'s own price, at most ${ageLabel(s.sharpConfirmMaxAgeSeconds)} old, must show ${edgeInWords(s.sharpConfirmMinEv)} at Novig's price now."
    }
}

/**
 * The sharp books' say over the auto-bet (its tab) or CNO's push alerts (Settings › Alerts) (Tj, 2026-10-02 17:01Z: "sharp veto instead of requirement"):
 * Off, Veto (the default: [SharpVeto]) or Require a confirmation ([SharpConfirm], its criteria shown only then; they're shared by both).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharpVetoSection(state: UiState, forAlerts: Boolean, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    val mode = if (forAlerts) s.sharpAlerts else s.sharpAutoBet
    SectionTitle(if (forAlerts) "Sharp-book veto for alerts" else "Sharp-book veto")
    if (s.pinnacleOnly && !forAlerts) {
        Text(
            "Pinnacle only is on: the auto-bet doesn't ask this veto, because every bet is already judged on Pinnacle's own price. It still applies to CrazyNinjaOdds' alerts and the bids.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.padding(vertical = 4.dp).testTag("pinnacleVetoNote"),
        )
    }
    Text(SharpConfirmText.sharpIntro(), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp))
    Chips(SharpMode.entries.toList(), mode, { it.displayName }, modifier = Modifier.testTag(if (forAlerts) "sharpAlerts" else "sharpAutoBet")) { v ->
        onUpdate { if (forAlerts) it.copy(sharpAlerts = v) else it.copy(sharpAutoBet = v) }
    }
    Text(SharpConfirmText.modeNote(mode), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(top = 4.dp).testTag(if (forAlerts) "sharpAlertsNote" else "sharpAutoBetNote"))
    if (mode == SharpMode.VETO) {
        Text(SharpConfirmText.vetoNote(), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp).testTag("sharpVetoNote"))
        // One bar for the auto-bet, the alerts and the bids (a filled bid is a bet): the same setting on each screen.
        Text("Edge the sharpest book must give Novig's price", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Chips(
            ScanSettings.SHARP_VETO_MIN_EV_CHOICES, s.sharpVetoMinEv, SharpConfirmText::edgeLabel,
            modifier = Modifier.testTag(if (forAlerts) "sharpVetoMinEvAlerts" else "sharpVetoMinEv"),
        ) { v -> onUpdate { it.copy(sharpVetoMinEv = v) } }
        TypedPercentField(NumberSpecs.percent("edge", 0.1, 10.0), s.sharpVetoMinEv, if (forAlerts) "sharpVetoMinEvAlertsField" else "sharpVetoMinEvField") { v -> onUpdate { it.copy(sharpVetoMinEv = v) } }
        Text(
            SharpConfirmText.vetoBarNote(s.sharpVetoMinEv), style = MaterialTheme.typography.bodySmall, color = subtle,
            modifier = Modifier.padding(top = 4.dp).testTag(if (forAlerts) "sharpVetoBarNoteAlerts" else "sharpVetoBarNote"),
        )
    }
    if (mode == SharpMode.CONFIRM) SharpConfirmCriteria(state, onUpdate)
}

/** The confirmation's criteria (shared by the auto-bet and the alerts): which sharp books, how fresh, how big an edge, and CNO's page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharpConfirmCriteria(state: UiState, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val feeds = SharpConfirmText.feedsOn(s) { state.keysOf(it).size }
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant
    Text(SharpConfirmText.intro(), style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp))
    Text("Sharp books that can confirm", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(SharpBookChoice.entries.toList(), s.sharpConfirmBooks, { it.displayName }) { v -> onUpdate { it.copy(sharpConfirmBooks = v) } }
    Text("Oldest quote allowed", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.SHARP_MAX_AGE_CHOICES, s.sharpConfirmMaxAgeSeconds, SharpConfirmText::ageLabel) { v -> onUpdate { it.copy(sharpConfirmMaxAgeSeconds = v) } }
    TypedIntField(NumberSpecs.time("seconds", 10, 3600), s.sharpConfirmMaxAgeSeconds, "sharpMaxAgeField", none = { false }) { v -> onUpdate { it.copy(sharpConfirmMaxAgeSeconds = v) } }
    Text("Edge the sharp book must show at Novig's price now", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    Chips(ScanSettings.SHARP_MIN_EV_CHOICES, s.sharpConfirmMinEv, SharpConfirmText::edgeLabel) { v -> onUpdate { it.copy(sharpConfirmMinEv = v) } }
    TypedPercentField(NumberSpecs.percent("edge", 0.1, 50.0), s.sharpConfirmMinEv, "sharpConfirmMinEvField") { v -> onUpdate { it.copy(sharpConfirmMinEv = v) } }
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

