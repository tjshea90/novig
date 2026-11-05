package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.ncaab.NcaabGameView
import com.tjshea.vigilant.data.ncaab.NcaabModel
import com.tjshea.vigilant.data.ncaab.NcaabSignals
import com.tjshea.vigilant.data.ncaab.NcaabTeam
import com.tjshea.vigilant.data.ncaab.NcaabText
import com.tjshea.vigilant.data.ncaab.SpreadLine
import com.tjshea.vigilant.data.ncaab.TotalLine
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget

/**
 * One game (NC1): both teams' numbers, what each signal of the method says and what that kind of signal has done, then Novig's spread, total and moneyline with a Bet button on every side, the books'
 * prices and the EV at Novig's price (the same sheet the +EV list opens) for a number the scan priced, and drop-downs with every other number Novig offers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NcaabGameSheet(
    v: NcaabGameView,
    model: NcaabModel?,
    settings: com.tjshea.vigilant.data.scanner.ScanSettings,
    now: Long,
    onDismiss: () -> Unit,
    onTrack: (Opportunity, Double) -> Unit,
    onRecheck: (List<String>) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var books by remember { mutableStateOf<Opportunity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().testTag("ncaabSheet"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ParlayBar()
            Text("${v.game.a.n} @ ${v.game.h.n}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                Format.startTime(v.game.t) + (if (v.game.neu == 1) " · neutral site" else "") + (if (v.live) " · LIVE" else if (v.final) " · FINAL" else "") + " · lines from ${v.lineSource}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TeamLine(v.game.a.n, v.away)
            TeamLine(v.game.h.n, v.home)
            Readings(v)
            if (!v.bettable) {
                Text(
                    if (v.event == null) "Novig lists no market for this game, so there is nothing to bet here." else "This game has started, so it can't be bet from here.",
                    style = MaterialTheme.typography.bodyMedium, color = LocalEdgeColors.current.warning,
                )
            } else {
                HorizontalDivider()
                SpreadBlock(v, model, onBooks = { books = it })
                HorizontalDivider()
                TotalBlock(v, model, onBooks = { books = it })
                v.moneyline?.let { (h, a) ->
                    HorizontalDivider()
                    Text("Moneyline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SideRow(a, onBooks = { books = it })
                    SideRow(h, onBooks = { books = it })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    books?.let { o ->
        // Priced again if the engine's board has a newer row for the same side.
        OpportunitySheet(
            o, settings,
            onDismiss = { books = null },
            onTrack = { stake -> onTrack(o, stake); books = null },
            onRecheck = { onRecheck(listOf(o.market.marketId)) },
        )
    }
}

@Composable
private fun TeamLine(name: String, t: NcaabTeam?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(NcaabText.team(name, t), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (t == null) {
            Text("Not rated yet: a team needs 3 usable games this season.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${NcaabText.number(t.ppg)} scored · ${NcaabText.number(t.papg)} allowed · against the spread ${String.format(java.util.Locale.US, "%+.2f", t.ats)} · ${t.g} games" + (if (t.all > t.g || t.ex > 0) " (${t.all - t.g + 0} left out)" else ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                Tiers(t.last)
            }
        }
    }
}

@Composable
private fun Readings(v: NcaabGameView) {
    if (v.readings.isEmpty()) return
    val edge = LocalEdgeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("What the method says", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        v.picks.sortedByDescending { NcaabSignals.score(it.reading) }.forEach { p ->
            val r = p.reading
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text((if (r.backed) "✓ " else "") + r.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (r.backed) edge.positive else MaterialTheme.colorScheme.onSurface)
                Text(
                    "Points to: ${p.text}" + (p.cost?.let { " (${NcaabText.cents(it)})" } ?: "") + (if (r.fade) " — the history says the other side of this signal is the one that tends to lose" else "") +
                        "\nSize ${NcaabText.number(kotlin.math.abs(r.value))} ${r.unit}: that side won ${NcaabText.pct(r.rate)} of ${"%,d".format(r.n)} games" +
                        (r.rateCurrent?.takeIf { r.nCurrent >= 20 }?.let { ", ${NcaabText.pct(it)} of ${r.nCurrent} this season" } ?: "") + (p.systemEv?.let { "; ${Format.evPercentShort(it)} a bet at this price if that holds" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- the lines -----------------------------------------------------------------------------------------------------------------------------------

/** One side of a Novig line: its name and price, the Bet button, and the books' prices and EV when the scan priced it. */
@Composable
private fun SideRow(o: Opportunity, onBooks: (Opportunity) -> Unit) {
    val api = LocalApiBet.current?.takeIf { it.enabled }
    val cost = o.ladder.firstOrNull()?.price
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("ncaabSide-${o.outcome.outcomeId}")) {
        Column(Modifier.weight(1f)) {
            Text(o.selection + (cost?.let { "  ${NcaabText.cents(it)}" } ?: "  —"), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val ev = o.evPercent
            Text(
                if (ev != null) "EV at Novig vs the books: ${Format.evPercent(ev)}" else if (cost == null) "nobody is offering this side" else "no book prices this number",
                style = MaterialTheme.typography.labelSmall, color = if (ev != null && ev > 0) LocalEdgeColors.current.positive else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cost != null) ParlayToggle(o)
        }
        if (o.fairProbability != null) TextButton(onClick = { onBooks(o) }, modifier = Modifier.testTag("ncaabBooks")) { Text("Books & EV") }
        if (api != null && cost != null) BetChip("Bet") { betFrom(api, o, null) }
    }
}

private fun sideOf(o: Opportunity): Side? = (o.target as? OutcomeTarget.Is)?.side

@Composable
private fun SpreadBlock(v: NcaabGameView, model: NcaabModel?, onBooks: (Opportunity) -> Unit) {
    val lines = v.spreads
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Spread", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (lines.isEmpty()) { Text("Novig lists no spread yet.", style = MaterialTheme.typography.bodySmall); return }
        val mainIx = lines.indexOfFirst { it === v.mainSpread }.coerceAtLeast(0)
        var ix by remember(v.id, lines.size) { mutableIntStateOf(mainIx) }
        val line: SpreadLine = lines[ix.coerceIn(0, lines.lastIndex)]
        SideRow(line.away, onBooks)
        SideRow(line.home, onBooks)
        NumberPicker(
            label = if (ix == mainIx) "Other spreads (${lines.size - 1})" else "Showing ${NcaabText.signed(line.handicap)} for ${line.home.selection.substringBeforeLast(' ')} · back to the main number",
            options = lines.map { "${it.home.selection}  ·  ${it.homeCost?.let(NcaabText::cents) ?: "—"} / ${it.awayCost?.let(NcaabText::cents) ?: "—"}" + if (it === v.mainSpread) "  (main)" else "" },
            selected = ix, onSelect = { ix = it }, tag = "ncaabSpreadPicker",
        )
        if (ix != mainIx) estimateNote(v, model, kind = "spread", main = v.mainSpread?.handicap, number = line.handicap)
    }
}

@Composable
private fun TotalBlock(v: NcaabGameView, model: NcaabModel?, onBooks: (Opportunity) -> Unit) {
    val lines = v.totals
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (lines.isEmpty()) { Text("Novig lists no total yet.", style = MaterialTheme.typography.bodySmall); return }
        val mainIx = lines.indexOfFirst { it === v.mainTotal }.coerceAtLeast(0)
        var ix by remember(v.id, lines.size) { mutableIntStateOf(mainIx) }
        val line: TotalLine = lines[ix.coerceIn(0, lines.lastIndex)]
        SideRow(line.over, onBooks)
        SideRow(line.under, onBooks)
        NumberPicker(
            label = if (ix == mainIx) "Other totals (${lines.size - 1})" else "Showing ${NcaabText.number(line.line)} · back to the main number",
            options = lines.map { "${NcaabText.number(it.line)}  ·  O ${it.overCost?.let(NcaabText::cents) ?: "—"} / U ${it.underCost?.let(NcaabText::cents) ?: "—"}" + if (it === v.mainTotal) "  (main)" else "" },
            selected = ix, onSelect = { ix = it }, tag = "ncaabTotalPicker",
        )
        if (ix != mainIx) estimateNote(v, model, kind = "total", main = v.mainTotal?.line, number = line.line)
    }
}

/** For a number other than the main one, the strongest reading's chance moved along the normal curve: an estimate, labelled as one. */
@Composable
private fun estimateNote(v: NcaabGameView, model: NcaabModel?, kind: String, main: Double?, number: Double) {
    if (main == null || model == null) return
    val pick = v.picks.filter { it.reading.kind == kind && it.opportunity != null }.maxByOrNull { NcaabSignals.score(it.reading) } ?: return
    val side = when (sideOf(pick.opportunity!!)) {
        Side.HOME -> NcaabSignals.Pick.HOME
        Side.AWAY -> NcaabSignals.Pick.AWAY
        Side.OVER -> NcaabSignals.Pick.OVER
        Side.UNDER -> NcaabSignals.Pick.UNDER
        null -> return
    }
    val p = NcaabText.estimateAt(pick.reading, side, main, number, model.sigma) ?: return
    Text(
        "If the method's lean on ${pick.text} held, this number would win about ${NcaabText.pct(p)} (an estimate that moves the lean's ${NcaabText.pct(pick.reading.rate)} along the normal curve; the Bet sheet does not use it).",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("ncaabEstimate"),
    )
}

@Composable
private fun NumberPicker(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, tag: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text("$label ▾", maxLines = 2) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, text ->
                DropdownMenuItem(
                    text = { Text(text, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(i); open = false },
                    modifier = Modifier.testTag("$tag-$i"),
                )
            }
        }
    }
}
