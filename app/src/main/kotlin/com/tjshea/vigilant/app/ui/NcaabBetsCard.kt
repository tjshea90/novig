package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.ncaab.NcaabBetStats
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The record of the bets placed by hand from the NCAAB tab (NT1, Tj 2026-11-20: "tracks bets i hand bet on the ncaab tab ... amount of bets, total money bet all time and daily, total profit and loss all time and
 * daily, total wins and losses, percent of bets won, etc. ... in the tracker and in the ncaab tab where i can easily see it"): the same card in the NCAAB tab and on the Tracker's Stats page. Every number is
 * [NcaabBetStats]'s, which counts nothing from any other tab.
 */
@Composable
fun NcaabBetsCard(summary: NcaabBetStats.Summary, modifier: Modifier = Modifier, onOpenTracker: (() -> Unit)? = null) {
    val edge = LocalEdgeColors.current
    Card(modifier.fillMaxWidth().testTag("ncaabBets"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("My NCAAB bets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (summary.isEmpty) {
                Text(
                    "Bets you place by hand from this tab are counted here: how many, the money bet and the profit or loss (all time and by day), the record and the percent won. Nothing from any other tab is in these numbers.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("ncaabBetsEmpty"),
                )
                if (summary.voided > 0) Text("${summary.voided} voided (refunded), not counted.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(
                "Bets you placed by hand from this tab" + (summary.since?.let { " · since ${it.format(DAY)}" } ?: "") + ".",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // all time | today, row by row
            Header()
            Line("Bets", "${summary.all.bets}", "${summary.today.bets}", "ncaabBets-bets")
            Line("Money bet", Format.money(summary.all.staked), Format.money(summary.today.staked), "ncaabBets-staked")
            Line("Profit / loss", signed(summary.all), signed(summary.today), "ncaabBets-profit", allColor = tone(summary.all, edge), todayColor = tone(summary.today, edge), bold = true)
            Line("Record (won-lost${if (summary.all.pushed > 0 || summary.today.pushed > 0) "-pushed" else ""})", summary.all.record, summary.today.record, "ncaabBets-record")
            Line("Percent won", pct(summary.all.winPct), pct(summary.today.winPct), "ncaabBets-winPct")
            Line("Return on money", roi(summary.all), roi(summary.today), "ncaabBets-roi")
            Line("Still open", openText(summary.all), openText(summary.today), "ncaabBets-open")
            val facts = listOfNotNull(
                summary.all.avgStake?.let { "Average bet ${Format.money(it)}" },
                summary.best?.let { "Best win ${Format.signedMoney(it.profit ?: 0.0)}" },
                summary.worst?.let { "Worst loss ${Format.signedMoney(it.profit ?: 0.0)}" },
                summary.voided.takeIf { it > 0 }?.let { "$it voided (not counted)" },
            )
            if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("ncaabBets-facts"))

            var view by rememberSaveable { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = view == "days", onClick = { view = if (view == "days") "" else "days" }, label = { Text("By day (${summary.days.size})", maxLines = 1) }, modifier = Modifier.testTag("ncaabBetsDays"))
                FilterChip(selected = view == "markets", onClick = { view = if (view == "markets") "" else "markets" }, label = { Text("By kind of bet", maxLines = 1) }, modifier = Modifier.testTag("ncaabBetsMarkets"))
                if (onOpenTracker != null) TextButton(onClick = onOpenTracker, modifier = Modifier.testTag("ncaabBetsTracker")) { Text("Tracker", maxLines = 1) }
            }
            if (view == "days") {
                var all by rememberSaveable { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth()) {
                    Cell("Game day", 1.5f, bold = true, align = TextAlign.Start); Cell("Bets", 0.7f, bold = true); Cell("Bet", 1f, bold = true); Cell("Profit / loss", 1.2f, bold = true); Cell("W-L", 0.8f, bold = true)
                }
                val rows = if (all) summary.days else summary.days.take(DAYS_SHOWN)
                rows.forEach { d ->
                    Row(Modifier.fillMaxWidth().testTag("ncaabBetsDay-${d.date}"), verticalAlignment = Alignment.CenterVertically) {
                        Cell(d.date.format(DAY), 1.5f, align = TextAlign.Start); Cell("${d.totals.bets}", 0.7f); Cell(Format.money(d.totals.staked), 1f)
                        Cell(if (d.totals.settled == 0) "open" else Format.signedMoney(d.totals.profit), 1.2f, color = if (d.totals.settled == 0) null else tone(d.totals, edge), bold = d.totals.settled > 0)
                        Cell(if (d.totals.settled == 0) "–" else d.totals.record, 0.8f)
                    }
                }
                if (summary.days.size > DAYS_SHOWN) TextButton(onClick = { all = !all }) { Text(if (all) "Show the last $DAYS_SHOWN days" else "Show all ${summary.days.size} days") }
            }
            if (view == "markets") {
                Row(Modifier.fillMaxWidth()) { Cell("Kind", 1.6f, bold = true, align = TextAlign.Start); Cell("Bets", 0.7f, bold = true); Cell("Bet", 1f, bold = true); Cell("Profit / loss", 1.2f, bold = true); Cell("W-L", 0.8f, bold = true) }
                summary.markets.forEach { m ->
                    Row(Modifier.fillMaxWidth().testTag("ncaabBetsMarket-${m.market}"), verticalAlignment = Alignment.CenterVertically) {
                        Cell(m.market, 1.6f, align = TextAlign.Start); Cell("${m.totals.bets}", 0.7f); Cell(Format.money(m.totals.staked), 1f)
                        Cell(if (m.totals.settled == 0) "open" else Format.signedMoney(m.totals.profit), 1.2f, color = if (m.totals.settled == 0) null else tone(m.totals, edge), bold = m.totals.settled > 0)
                        Cell(if (m.totals.settled == 0) "–" else m.totals.record, 0.8f)
                    }
                }
            }
            Text(
                "A day is the game's day (5 am to 5 am Eastern), like the Tracker's. Money bet counts every bet, open or settled; profit or loss counts only settled bets, an open bet is money at stake. Percent won is wins over wins plus losses.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val DAYS_SHOWN = 7
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

private fun signed(t: NcaabBetStats.Totals): String = if (t.settled == 0) Format.money(0.0) else Format.signedMoney(t.profit)
private fun pct(p: Double?): String = p?.let { Format.percent(it, 0) } ?: "–"
private fun roi(t: NcaabBetStats.Totals): String = t.roi?.let { (if (it >= 0) "+" else "−") + Format.percent(kotlin.math.abs(it), 1) } ?: "–"
private fun openText(t: NcaabBetStats.Totals): String = if (t.open == 0) "none" else "${t.open} · ${Format.money(t.openStaked)}"
private fun tone(t: NcaabBetStats.Totals, edge: EdgeColors): Color? = when {
    t.settled == 0 || kotlin.math.abs(t.profit) < 0.005 -> null
    t.profit > 0 -> edge.positive
    else -> edge.negative
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth()) {
        Cell("", 1.6f, bold = true, align = TextAlign.Start); Cell("All time", 1.2f, bold = true); Cell("Today", 1.2f, bold = true)
    }
}

@Composable
private fun Line(label: String, all: String, today: String, tag: String, allColor: Color? = null, todayColor: Color? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Cell(label, 1.6f, align = TextAlign.Start)
        Cell(all, 1.2f, color = allColor, bold = bold, tag = "$tag-all")
        Cell(today, 1.2f, color = todayColor, bold = bold, tag = "$tag-today")
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, bold: Boolean = false, color: Color? = null, align: TextAlign = TextAlign.End, tag: String? = null) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = color ?: MaterialTheme.colorScheme.onSurface, textAlign = align, maxLines = 1,
        modifier = Modifier.weight(weight).let { if (tag != null) it.testTag(tag) else it },
    )
}
