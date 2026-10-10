package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.LiveBidText
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.livebid.LiveBidLimits
import com.tjshea.vigilant.data.livebid.LiveBidPresets
import com.tjshea.vigilant.data.livebid.LiveBidQuality
import com.tjshea.vigilant.data.livebid.SavedLiveBidPreset
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod

/**
 * Settings › Live bids (Tj, 2026-10-10: "a good slate of options for this setting to fine tune it, including presets I can save and manual fields to type my own numbers. Give a couple default presets
 * that are safe positive EV live bid presets"): the two switches (paper, then real money behind a confirmation), the presets, and every number the bids use as chips with a box to type your own.
 * Order: what Tj is most likely to touch first. The money is his own and a preset never changes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveBidPage(state: UiState, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val q = s.liveBidQuality
    val lim = s.liveBidLimits
    val shown by rememberUpdatedState(reportActions.onLiveBidShown)
    LaunchedEffect(Unit) { while (true) { shown(); kotlinx.coroutines.delay(1_500) } }
    val setQ = { f: (LiveBidQuality) -> LiveBidQuality -> onUpdate { it.copy(liveBidQuality = f(it.liveBidQuality)) } }
    val setL = { f: (LiveBidLimits) -> LiveBidLimits -> onUpdate { it.copy(liveBidLimits = f(it.liveBidLimits)) } }
    val subtle = MaterialTheme.colorScheme.onSurfaceVariant

    Text(LiveBidText.HINT, style = MaterialTheme.typography.bodyMedium, color = subtle, modifier = Modifier.padding(vertical = 4.dp))
    Text(LiveBidText.EVIDENCE, style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.padding(vertical = 4.dp).testTag("liveBidEvidence"))
    LbSwitch(LiveBidText.FEED_TITLE, LiveBidText.FEED_SUB, s.liveBid, "liveBidSwitch") { v -> onUpdate { it.copy(liveBid = v, liveBidReal = it.liveBidReal && v) } }
    s.liveBidHalted?.let { why ->
        Column(Modifier.padding(vertical = 4.dp).testTag("liveBidHalted")) {
            Text("Stopped: $why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onUpdate { it.copy(liveBidHalted = null) } }, modifier = Modifier.testTag("liveBidResume")) { Text(LiveBidText.RESUME) }
        }
    }
    var confirming by remember { mutableStateOf(false) }
    val paperOnly = LiveBidPresets.active(s)?.paperOnly == true
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = s.liveBidReal, role = Role.Switch,
            onValueChange = { v -> if (!v) onUpdate { it.copy(liveBidReal = false) } else if (s.liveBid && !paperOnly) confirming = true },
        ).padding(vertical = 6.dp).testTag("liveBidRealSwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(LiveBidText.REAL_TITLE, style = MaterialTheme.typography.bodyMedium)
            Text(LiveBidText.REAL_SUB, style = MaterialTheme.typography.bodySmall, color = subtle)
        }
        Switch(checked = s.liveBidReal, onCheckedChange = null, enabled = (s.liveBid && !paperOnly) || s.liveBidReal)
    }
    if (paperOnly) Text("The preset in force is for watching only: real bids stay off until you apply another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("liveBidPaperOnlyNote"))
    Text(LiveBidText.statusLine(state.liveBidStatus, s), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp).testTag("liveBidNote"))
    if (state.pinnoddsKeys.isEmpty()) Text("No Pinnodds key is saved: add it in Settings › Pinnodds live. Live bids use the same feed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("liveBidNoKey"))

    // ---- presets ----------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("Live bid presets")
    Text("A preset sets every rule below except your money (the stake rule and the limits). Careful is the one to start real money with.", style = MaterialTheme.typography.bodySmall, color = subtle)
    Text(LiveBidText.inForce(s), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp).testTag("liveBidPresetInForce"))
    val active = LiveBidPresets.active(s)
    LiveBidPresets.all(s).forEach { p ->
        LbPresetCard(p, inForce = p == active, builtIn = LiveBidPresets.builtIn(p.name), onApply = { onUpdate { LiveBidPresets.apply(it, p) } }, onDelete = { onUpdate { LiveBidPresets.delete(it, p.name) } })
    }
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val taken = LiveBidPresets.builtIn(trimmed)
    OutlinedTextField(
        value = name, onValueChange = { name = it.take(LiveBidPresets.MAX_NAME) }, label = { Text("Name for your own preset") }, isError = taken, singleLine = true,
        supportingText = { Text(if (taken) "That's a built-in preset's name" else if (s.liveBidPresets.any { it.name.equals(trimmed, true) }) "Replaces your preset of that name" else "Saves the rules below as they are now") },
        modifier = Modifier.fillMaxWidth().testTag("liveBidPresetName"),
    )
    Button(onClick = { onUpdate { LiveBidPresets.save(it, name) ?: it }; name = "" }, enabled = trimmed.isNotEmpty() && !taken, modifier = Modifier.testTag("liveBidPresetSave")) { Text("Save these rules as a preset") }

    // ---- the money ----------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("Live bid limits (your money: a preset never changes it)")
    LbHint("A bid's size is a fraction of full Kelly on your bankroll (Settings › Betting & Novig account: ${LiveBidText.money(s.bankroll)}) for that bid's own price and edge, raised to the smallest stake and held to the biggest and to Settings' per-bet maximum. Makers pay no fee.")
    LbTitle("Live bid stake rule")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoBetStake.entries.forEach { m ->
            FilterChip(selected = m == lim.stakeMode, onClick = { setL { it.copy(stakeMode = m) } }, label = { Text(m.label) }, modifier = Modifier.testTag("liveBidStake-${m.name}"))
        }
    }
    LiveBidText.typicalStake(s)?.let { Text("A typical bid (fair 50%, ${LiveBidText.pct(q.margin)} margin) stakes ${LiveBidText.money(it)}.", style = MaterialTheme.typography.bodySmall, color = subtle, modifier = Modifier.testTag("liveBidTypical")) }
    if (lim.stakeMode == AutoBetStake.CUSTOM) TypedDollarField(NumberSpecs.dollars("a bid's amount", 0.5, 1_000.0), lim.customStake, "liveBidCustomStake") { v -> setL { it.copy(customStake = v) } }
    LbDollars("Smallest stake", listOf(0.5, 1.0, 2.0, 3.0, 5.0), lim.minStake, "liveBidMinStake", 0.5, 1_000.0) { v -> setL { it.copy(minStake = v, maxStake = maxOf(it.maxStake, v)) } }
    LbDollars("Biggest stake", listOf(1.0, 2.0, 3.0, 5.0, 10.0, 25.0), lim.maxStake, "liveBidMaxStake", 0.5, 1_000.0) { v -> setL { it.copy(maxStake = v, minStake = minOf(it.minStake, v)) } }
    LbCount("Most bids up at once", listOf(1, 2, 3, 5, 8, 12, 20), lim.maxBids, "liveBidMaxBids", 1, 100) { v -> setL { it.copy(maxBids = v, maxBidsPerGame = minOf(it.maxBidsPerGame, v)) } }
    LbCount("Most bids up in one game", listOf(1, 2, 3, 4, 6), lim.maxBidsPerGame, "liveBidMaxGameBids", 1, 100) { v -> setL { it.copy(maxBidsPerGame = v, maxBids = maxOf(it.maxBids, v)) } }
    LbDollars("Most at risk in one game", listOf(3.0, 5.0, 10.0, 15.0, 25.0, 50.0), lim.maxPerGame, "liveBidMaxGame", 1.0, 10_000.0) { v -> setL { it.copy(maxPerGame = v) } }
    LbDollars("Most filled in a day (fills plus the bids up)", listOf(10.0, 20.0, 40.0, 75.0, 150.0, 300.0), lim.maxPerDay, "liveBidMaxDay", 1.0, 100_000.0) { v -> setL { it.copy(maxPerDay = v) } }
    LbDollars("Stop for the day when settled live bids lose", listOf(5.0, 10.0, 15.0, 25.0, 50.0, 100.0), lim.haltLoss, "liveBidHaltLoss", 1.0, 100_000.0) { v -> setL { it.copy(haltLoss = v) } }
    LbDollars("Keep in the wallet (bids never touch it)", listOf(0.0, 2.0, 5.0, 10.0, 25.0, 50.0), lim.walletReserve, "liveBidReserve", 1.0, 100_000.0, zeroLabel = "None") { v -> setL { it.copy(walletReserve = v) } }

    // ---- the price ------------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("The bid's price")
    LbHint("A bid sits this far under Pinnacle's devigged price, so its edge when posted is the margin. On the stored tapes a 5% margin kept about +4% a fill two minutes later; 3% kept under +1%; fills hardly fell until 8%.")
    LbPercent("Margin under Pinnacle's fair", listOf(0.02, 0.03, 0.04, 0.05, 0.06, 0.08, 0.10), q.margin, "liveBidMargin", 1.0, 25.0) { v -> setQ { it.copy(margin = v) } }
    LbTitle("How Pinnacle's margin is removed")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DevigMethod.WORST_CASE, DevigMethod.POWER, DevigMethod.MULTIPLICATIVE).forEach { m ->
            FilterChip(selected = m == q.devig, onClick = { setQ { it.copy(devig = m) } }, label = { Text(m.displayName) }, modifier = Modifier.testTag("liveBidDevig-${m.name}"))
        }
    }
    Text(q.devig.blurb, style = MaterialTheme.typography.bodySmall, color = subtle)
    LbTyped("Lowest bid price", q.minPrice, "liveBidMinPrice", 1.0, 90.0) { v -> setQ { it.copy(minPrice = v, maxPrice = maxOf(it.maxPrice, v + 0.01)) } }
    LbTyped("Highest bid price", q.maxPrice, "liveBidMaxPrice", 10.0, 99.0) { v -> setQ { it.copy(maxPrice = v, minPrice = minOf(it.minPrice, v - 0.01)) } }
    LbTyped("Lowest Pinnacle fair to bid on", q.minFair, "liveBidMinFair", 1.0, 90.0) { v -> setQ { it.copy(minFair = v, maxFair = maxOf(it.maxFair, v + 0.01)) } }
    LbTyped("Highest Pinnacle fair to bid on", q.maxFair, "liveBidMaxFair", 10.0, 99.0) { v -> setQ { it.copy(maxFair = v, minFair = minOf(it.minFair, v - 0.01)) } }
    LbSwitch("Never lead the book", "Skip a bid that would be the best bid on its side. On the tapes bids that led were picked off most.", q.neverLead, "liveBidNeverLead") { v -> setQ { it.copy(neverLead = v) } }
    LbSwitch("Bid both sides of a market", "Off: one side at a time (the other is left alone while one is up).", q.bothSides, "liveBidBothSides") { v -> setQ { it.copy(bothSides = v) } }

    // ---- which lines --------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("Which lines")
    LbHint("Moneylines, and spreads and totals at Pinnacle's own main line (the lines Pinnacle's price can stand behind).")
    LbSwitch("Moneylines", "", q.moneyline, "liveBidMoneyline") { v -> setQ { it.copy(moneyline = v) } }
    LbSwitch("Spreads", "", q.spread, "liveBidSpread") { v -> setQ { it.copy(spread = v) } }
    LbSwitch("Totals", "", q.total, "liveBidTotal") { v -> setQ { it.copy(total = v) } }
    LbSwitch("Tennis", "Moves on every point and its feed has no clock. Off by default.", q.tennis, "liveBidTennis") { v -> setQ { it.copy(tennis = v) } }
    LbTitle("Leagues")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = q.onlyLeagues.isEmpty(), onClick = { setQ { it.copy(onlyLeagues = emptySet()) } }, label = { Text("All") }, modifier = Modifier.testTag("liveBidLeague-ALL"))
        Leagues.ALL.map { it.novigName }.distinct().forEach { lg ->
            FilterChip(
                selected = lg in q.onlyLeagues,
                onClick = { setQ { it.copy(onlyLeagues = if (lg in it.onlyLeagues) it.onlyLeagues - lg else it.onlyLeagues + lg) } },
                label = { Text(lg) }, modifier = Modifier.testTag("liveBidLeague-$lg"),
            )
        }
    }

    // ---- fresh --------------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("Live bid freshness: how fresh Pinnacle's price must be")
    LbHint("A live bid is only as good as the price behind it. These keep a bid from resting on a price that has gone old, and from trusting a price Pinnacle itself does not stand behind.")
    LbSeconds("Pinnacle silent too long (no bid, and a bid up is pulled)", listOf(0, 10, 15, 20, 30, 45, 60), q.maxQuietSec, "liveBidQuiet", 300) { v -> setQ { it.copy(maxQuietSec = v) } }
    LbSeconds("The price has not changed for (too old; 0 = no limit)", listOf(0, 20, 30, 60, 120, 300), q.maxFairAgeSec, "liveBidFairAge", 3_600) { v -> setQ { it.copy(maxFairAgeSec = v) } }
    LbSeconds("The price must have sat still for", listOf(0, 2, 3, 5, 8, 12), q.settleSec, "liveBidSettle", 120) { v -> setQ { it.copy(settleSec = v) } }
    LbPercent("Pinnacle's own margin at most", listOf(0.06, 0.07, 0.075, 0.08, 0.09, 0.10), q.maxOverround, "liveBidOverround", 1.0, 30.0) { v -> setQ { it.copy(maxOverround = v) } }
    LbDollars("Pinnacle's limit on the line at least", listOf(0.0, 250.0, 500.0, 1000.0, 2000.0, 3000.0), q.minPinnLimit, "liveBidPinnLimit", 1.0, 100_000.0, zeroLabel = "Off") { v -> setQ { it.copy(minPinnLimit = v) } }
    LbPercent("Pinnacle and Novig's middle may differ by at most (points)", listOf(0.0, 0.05, 0.08, 0.10, 0.15, 0.20), q.maxBookGap, "liveBidBookGap", 1.0, 50.0, unit = " pts", zeroLabel = "Off") { v -> setQ { it.copy(maxBookGap = v) } }

    // ---- life -----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("How long a bid lives")
    LbHint("Every order carries an expiry that Novig enforces itself: if the phone, the feed or a cancel fails, the bid is gone by then.")
    LbSeconds("A bid rests at most", listOf(10, 15, 20, 30, 45, 60), q.ttlSec, "liveBidTtl", 300, min = 10) { v -> setQ { it.copy(ttlSec = v, refreshBeforeSec = minOf(it.refreshBeforeSec, v / 2)) } }
    LbSeconds("Replace it with this many seconds left", listOf(0, 4, 6, 8, 10, 15), q.refreshBeforeSec, "liveBidRefresh", 150) { v -> setQ { it.copy(refreshBeforeSec = minOf(v, it.ttlSec / 2)) } }
    LbSwitch("Put the replacement up before the old one ends", "A live order takes seconds to land, so waiting leaves a gap. For a moment both can fill.", q.overlapRepost, "liveBidOverlap") { v -> setQ { it.copy(overlapRepost = v) } }

    // ---- pulls ----------------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("Live bid pulls: when a bid comes down")
    LbPercent("Pull when its edge falls under", listOf(0.005, 0.01, 0.015, 0.02, 0.03), q.pullBelowEv, "liveBidPullEv", 0.1, 20.0) { v -> setQ { it.copy(pullBelowEv = v) } }
    LbSwitch("Pull when the score changes", "Pinnacle's score reaches the feed about 2 s before its price moves.", q.pullOnScore, "liveBidPullScore") { v -> setQ { it.copy(pullOnScore = v) } }
    LbSeconds("No new bid for this long after a score", listOf(0, 10, 20, 30, 45, 60), q.scoreHoldSec, "liveBidScoreHold", 600) { v -> setQ { it.copy(scoreHoldSec = v) } }
    LbSeconds("No bid for this long after a danger frame (3 s at least)", listOf(0, 5, 10, 15, 20, 30), q.dangerHoldSec, "liveBidDangerHold", 300) { v -> setQ { it.copy(dangerHoldSec = v) } }
    LbPercent("Pull when Novig's own middle falls (points)", listOf(0.0, 0.02, 0.03, 0.04, 0.05, 0.06), q.novigMovePull, "liveBidNovigMove", 1.0, 30.0, unit = " pts", zeroLabel = "Off") { v -> setQ { it.copy(novigMovePull = v) } }
    LbSeconds("No new bid on a side for this long after a fill", listOf(0, 15, 30, 60, 120, 300), q.coolOffSec, "liveBidCoolOff", 3_600) { v -> setQ { it.copy(coolOffSec = v) } }

    // ---- self checks ----------------------------------------------------------------------------------------------------------------------------------------------------------------
    SectionTitle("Live bid self-checks that stop everything")
    LbHint("Real-money safeguards. Each stops the whole feature and takes every bid down until you tap Resume.")
    LbTitle("Stop after picked-off fills")
    val pickOffChoices = listOf(Triple(0, 0, "Off"), Triple(2, 3, "2 of 3"), Triple(3, 5, "3 of 5"), Triple(4, 6, "4 of 6"), Triple(5, 8, "5 of 8"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pickOffChoices.forEach { (limit, window, label) ->
            FilterChip(
                selected = if (limit == 0) q.pickOffLimit == 0 else q.pickOffLimit == limit && q.pickOffWindow == window,
                onClick = { setQ { it.copy(pickOffLimit = limit, pickOffWindow = if (limit == 0) it.pickOffWindow else window) } }, label = { Text(label) }, modifier = Modifier.testTag("liveBidPickOff-$limit"),
            )
        }
    }
    Text("A fill is picked off when Pinnacle's fair 30 s later is under the price paid. Counts real fills only.", style = MaterialTheme.typography.bodySmall, color = subtle)
    TypedIntField(NumberSpecs.count("picked-off fills that stop it", 1, 20), q.pickOffLimit, "liveBidPickOffLimit") { v -> setQ { it.copy(pickOffLimit = v, pickOffWindow = maxOf(it.pickOffWindow, v)) } }
    TypedIntField(NumberSpecs.count("of the last this many fills", 1, 30), q.pickOffWindow, "liveBidPickOffWindow") { v -> setQ { it.copy(pickOffWindow = v, pickOffLimit = minOf(it.pickOffLimit, v)) } }
    LbSeconds("Stop when pulling a bid takes longer than (90th percentile of the last ten)", listOf(0, 6, 8, 10, 12, 15, 20), q.maxCancelSec, "liveBidMaxCancel", 120) { v -> setQ { it.copy(maxCancelSec = v) } }
    LbSeconds("Stop when a bid takes longer than this to reach Novig's book", listOf(0, 8, 10, 12, 15, 20, 30), q.maxPlaceSec, "liveBidMaxPlace", 120) { v -> setQ { it.copy(maxPlaceSec = v) } }
    LbSwitch("Count Novig's maker credit as edge", "Half the taker's fee, paid in cash within 7 days, in play only. Off: the checks ignore it and it is a bonus.", q.countCredit, "liveBidCredit") { v -> setQ { it.copy(countCredit = v) } }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(LiveBidText.CONFIRM_TITLE) },
            text = { Text(LiveBidText.confirm(s)) },
            confirmButton = { TextButton(onClick = { confirming = false; onUpdate { it.copy(liveBidReal = true, liveBidHalted = null) } }, modifier = Modifier.testTag("liveBidRealConfirm")) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.testTag("liveBidRealCancel")) { Text("Cancel") } },
        )
    }
}

// ---- the pieces ---------------------------------------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun LbTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun LbHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun LbSwitch(title: String, sub: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row of chips for [choices] ([selected] lit when it matches within rounding) and, below, a box to type any other value. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> LbChoices(title: String, choices: List<T>, isSelected: (T) -> Boolean, label: (T) -> String, tag: String, onPick: (T) -> Unit) {
    LbTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEachIndexed { i, c -> FilterChip(selected = isSelected(c), onClick = { onPick(c) }, label = { Text(label(c)) }, modifier = Modifier.testTag("$tag-$i")) }
    }
}

@Composable
private fun LbPercent(title: String, choices: List<Double>, value: Double, tag: String, min: Double, max: Double, unit: String = "%", zeroLabel: String = "Off", onSet: (Double) -> Unit) {
    fun show(v: Double) = if (v <= 0.0) zeroLabel else LiveBidQuality.pct(v).removeSuffix("%") + unit
    LbChoices(title, choices, { kotlin.math.abs(it - value) < 1e-9 }, ::show, tag, onSet)
    TypedPercentField(NumberSpecs.percent(title.substringBefore(" ("), min, max), value, "$tag-field", onSet)
}

@Composable
private fun LbTyped(title: String, value: Double, tag: String, min: Double, max: Double, onSet: (Double) -> Unit) {
    LbTitle(title)
    TypedPercentField(NumberSpecs.percent(title.lowercase(), min, max), value, "$tag-field", onSet)
}

@Composable
private fun LbSeconds(title: String, choices: List<Int>, value: Int, tag: String, max: Int, min: Int = 0, onSet: (Int) -> Unit) {
    LbChoices(title, choices, { it == value }, { if (it == 0) "Off" else "$it s" }, tag, onSet)
    TypedIntField(NumberSpecs.time("seconds", maxOf(min, 1), max), value, "$tag-field", none = { it <= 0 }, onSet = onSet)
}

@Composable
private fun LbCount(title: String, choices: List<Int>, value: Int, tag: String, min: Int, max: Int, onSet: (Int) -> Unit) {
    LbChoices(title, choices, { it == value }, { it.toString() }, tag, onSet)
    TypedIntField(NumberSpecs.count(title.lowercase(), min, max), value, "$tag-field", onSet = onSet)
}

@Composable
private fun LbDollars(title: String, choices: List<Double>, value: Double, tag: String, min: Double, max: Double, zeroLabel: String = "None", onSet: (Double) -> Unit) {
    LbChoices(title, choices, { kotlin.math.abs(it - value) < 1e-9 }, { if (it <= 0.0) zeroLabel else LiveBidText.money(it) }, tag, onSet)
    TypedDollarField(NumberSpecs.dollars(title.lowercase().substringBefore(" ("), min, max), value, "$tag-field", onSet)
}

@Composable
private fun LbPresetCard(preset: SavedLiveBidPreset, inForce: Boolean, builtIn: Boolean, onApply: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("liveBidPreset:${preset.name}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(preset.name + if (builtIn) "" else " (yours)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (preset.paperOnly) Text("Paper only", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            LiveBidText.why(preset)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(preset.quality.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inForce) Text("In force", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("liveBidPresetActive:${preset.name}"))
                else Button(onClick = onApply, modifier = Modifier.testTag("liveBidPresetApply:${preset.name}")) { Text("Apply") }
                if (!builtIn) OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("liveBidPresetDelete:${preset.name}")) { Text("Delete") }
            }
        }
    }
}
