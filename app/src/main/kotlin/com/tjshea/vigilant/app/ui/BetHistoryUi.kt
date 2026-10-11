package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.BetShare
import com.tjshea.vigilant.data.scanner.BetRating
import com.tjshea.vigilant.data.tracker.BetDim
import com.tjshea.vigilant.data.tracker.BetDims
import com.tjshea.vigilant.data.tracker.BetFilterSpec
import com.tjshea.vigilant.data.tracker.BetHistoryExport
import com.tjshea.vigilant.data.tracker.BetSmartFilters
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.Odds

/** A saveable form of a [BetFilterSpec] (the Stats filter survives rotation and the app being recreated). */
val BetFilterSaver: Saver<BetFilterSpec, String> = Saver(
    save = { spec -> spec.keep.entries.filter { it.value.isNotEmpty() }.joinToString("\u001E") { (d, v) -> d.name + "\u001F" + v.joinToString("\u001D") } },
    restore = { text ->
        BetFilterSpec(
            text.split("\u001E").filter { it.isNotEmpty() }.mapNotNull { part ->
                val (d, v) = part.split("\u001F", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val dim = runCatching { BetDim.valueOf(d) }.getOrNull() ?: return@mapNotNull null
                dim to v.split("\u001D").filter { it.isNotEmpty() }.toSet()
            }.toMap(),
        )
    },
)

/** [stars] as the gold "★★★½ 3.5" the scanners show; nothing with no rating. A bet whose rating was worked out afterwards says "est." */
@Composable
fun BetStars(bet: TrackedBet, modifier: Modifier = Modifier, large: Boolean = false) {
    val s = BetDims.stars(bet) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StarRating(BetRating.Rating(s.first, 0.0), large = large)
        if (s.second) Text(" est.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The Stats and Bets tabs' smart filter (Tj, 2026-10-11): a drop-down of ready choices (each kind of bet, the star ratings, bets a sharp book agreed with, auto and manual, and the groups the bets
 * themselves show beat the close the most and the least) and "Choose…" for any mix by type of bet, sport, stars, sharp book, books behind the fair, liquidity, result and more.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartFilterBar(
    bets: List<TrackedBet>,
    spec: BetFilterSpec,
    shownCount: Int,
    now: Long,
    onChange: (BetFilterSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    val minute = now / 60_000L
    val choices = remember(bets, minute) { BetSmartFilters.choices(bets, now) }
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.testTag("smartFilter")) {
                Text("Filter: ${spec.label()} ▾", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.width(210.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                choices.forEach { c ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${c.title}  (${c.n})", fontWeight = if (c.spec == spec) FontWeight.Bold else FontWeight.Normal)
                                if (c.note.isNotEmpty()) Text(c.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = { onChange(c.spec); menu = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Choose…") }, onClick = { custom = true; menu = false })
            }
        }
        if (!spec.isEmpty) {
            Text("$shownCount shown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(BetFilterSpec.ALL) }) { Text("Clear") }
        }
    }
    if (custom) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // The groups each dimension has in these bets, in their natural order.
        val present = remember(bets, minute) {
            BetDim.entries.associateWith { d -> bets.map { BetDims.valueOf(it, d, now) }.distinct().sortedWith(compareBy({ BetDims.sortKey(d, it) }, { it })) }
        }
        ModalBottomSheet(onDismissRequest = { custom = false }, sheetState = sheet) {
            Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose what to look at", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pick any groups in any of these; a bet is shown when it is in a chosen group of every row you touched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BetDim.entries.forEach { d ->
                    val values = present[d].orEmpty()
                    if (values.size < 2 && (spec.keep[d].isNullOrEmpty())) return@forEach
                    Text(d.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val chosen = spec.keep[d].orEmpty()
                        values.forEach { v ->
                            FilterChip(selected = v in chosen, onClick = { onChange(spec.with(d, if (v in chosen) chosen - v else chosen + v)) }, label = { Text(v) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { onChange(BetFilterSpec.ALL) }) { Text("Clear all") }
                    Button(onClick = { custom = false }) { Text("Show $shownCount") }
                }
            }
        }
    }
}

/** The same numbers split by any of [BetDim]'s dimensions: type of bet, sport, stars, sharp book, books behind the fair, liquidity, auto or manual, time before the game, result, and the close. */
@Composable
fun DimBreakdownCard(bets: List<TrackedBet>, dim: BetDim, onDim: (BetDim) -> Unit, now: Long) {
    val rows = remember(bets, dim, now / 60_000L) {
        bets.groupBy { BetDims.valueOf(it, dim, now) }
            .map { (label, g) -> label to BetTracker.stats(g, now) }
            .filter { it.second.bets > 0 }
            .sortedWith(compareBy({ BetDims.sortKey(dim, it.first) }, { -it.second.settled }, { -it.second.bets }, { it.first }))
    }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().testTag("dimBreakdown"),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Split by", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BetDim.entries.forEach { d -> FilterChip(selected = d == dim, onClick = { onDim(d) }, label = { Text(d.label) }) }
            }
            rows.forEach { (label, s) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1.6f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(Format.signedMoney(s.profit), Modifier.weight(1f), color = moneyColor(s.profit), style = MaterialTheme.typography.bodyMedium)
                        Text(s.roi?.let { Format.evPercentShort(it) } ?: "—", Modifier.weight(0.8f), color = moneyColor(s.roi ?: 0.0), style = MaterialTheme.typography.bodyMedium)
                    }
                    Caption(
                        "${s.bets} · ${s.won}-${s.lost}" + (if (s.pushed > 0) "-${s.pushed}" else "") + (if (s.pending > 0) " · ${s.pending} open" else "") +
                            (s.averageEv?.let { " · EV ${Format.evPercentShort(it)}" } ?: "") + (s.averageClv?.let { " · CLV ${Format.evPercentShort(it)}" } ?: "") +
                            (s.beatClosePercent?.let { " · beat close ${Format.percent(it, 0)}" } ?: ""),
                    )
                }
            }
            Caption("Profit and profit % per group, outliers aside; CLV only on bets with a true close. A group of a few bets says little: look at the big ones, and at CLV.")
        }
    }
}

/** "Share bets with Claude" (Tj, 2026-10-11): every bet and bid, or a recent stretch, as files Claude can analyze; plus where the day-by-day copies are. */
@Composable
fun ShareBetsCard(totalBets: Int, betsIn: (BetShare.Range) -> Int, busy: Boolean, archiveNote: String?, onShare: (BetShare.Range) -> Unit) {
    var range by remember { mutableStateOf(BetShare.Range.ALL) }
    val n = betsIn(range)
    val (bytes, parts) = BetHistoryExport.estimate(n)
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().testTag("shareBetsCard"),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Share bets with Claude", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BetShare.Range.entries.forEach { r -> FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) }) }
            }
            Caption(
                "$n of your $totalBets bets and bid fills, plus every bid posted: about ${if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${bytes / 1024 + 1} KB"} in $parts file${if (parts == 1) "" else "s"} and a README " +
                    "with the definitions, the stats split every way and the best and worst groups by closing-line value. Each file is written a line at a time, so a big history can't crash the app.",
            )
            Button(onClick = { onShare(range) }, enabled = !busy && totalBets > 0, modifier = Modifier.fillMaxWidth().testTag("shareBetsButton")) {
                Text(if (busy) "Making the files…" else "Share with Claude")
            }
            archiveNote?.let { Caption("Every day's bets and bids are also saved on their own: $it") }
        }
    }
}

/** What a bet looked like when it was placed and how it closed, in the order Tj asked for it (Tj, 2026-10-11): the facts the files shared with Claude carry. */
@Composable
fun BetRecordCard(bet: TrackedBet, now: Long) {
    val a = bet.atBet
    val clv = ClosingLine.clv(bet, now)
    val close = ClosingLine.closeOf(bet, now)
    val stars = BetDims.stars(bet)
    fun row(label: String, value: String) = label to value
    val lead = BetDims.leadMinutes(bet)
    val lines = listOfNotNull(
        row("Bet or bid", if (bet.isBid) "Bid (a make order that filled)" else "Bet (a taker order)"),
        row("Placed", (if (bet.isLock) "Lock" else if (bet.auto) "Automatically" else "By hand") + " · " + Format.placedAt(bet.createdAtMs)),
        row("Odds when placed", (bet.american ?: a?.american)?.let { Odds.formatAmerican(it) + " · price ${Format.percent(bet.cost)}" } ?: Format.percent(bet.cost)),
        row("EV when placed", bet.evPercentAtBet?.let { Format.evPercent(it) } ?: "not recorded"),
        row("Before the game", lead?.let { TrackerRecordText.lead(it) } ?: "not recorded"),
        row("Books behind the fair", BetDims.books(bet)?.let { n -> "$n priced both sides" + (a?.agreeing?.let { " · $it said +EV" } ?: "") } ?: "not recorded"),
        row("Sharp book", BetDims.sharpLabel(bet)),
        row("Rating", stars?.let { BetRating.label(it.first) + " stars" + if (it.second) " (worked out afterwards)" else "" } ?: "not rated"),
        row("Liquidity", a?.available?.let { Format.money(it) + " at that price" } ?: "not recorded"),
        row("Type · sport", BetDims.valueOf(bet, BetDim.KIND, now) + " · " + BetDims.valueOf(bet, BetDim.SPORT, now)),
        row(
            "Closing odds",
            close?.let { (fair, source) ->
                Format.american(fair) + " (fair, " + ClosingLine.sourceLabel(source) + ")" + (bet.novigClose?.takeIf { now >= bet.startsTs }?.let { " · Novig's own " + Format.american(it) } ?: "")
            } ?: if (now < bet.startsTs) "not yet: the game hasn't started" else "no true close found",
        ),
        row("CLV", clv?.let { Format.evPercent(it) + if (it > 0) " (beat the close)" else " (missed the close)" } ?: "—"),
        row(
            "Result",
            when (bet.status) {
                BetStatus.PENDING -> "Open"
                BetStatus.WON -> "Won"
                BetStatus.LOST -> "Lost"
                BetStatus.PUSH -> "Pushed"
                BetStatus.VOID -> "Void"
                BetStatus.FMV -> "Settled at fair value"
            } + (bet.profit?.let { " · " + Format.signedMoney(it) } ?: ""),
        ),
    )
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().testTag("betRecord"),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("The bet as placed, and how it closed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
                    Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.4f))
                }
            }
            Caption("Closing odds are the fair (no-vig) price of this side at the start of the game; CLV compares what you paid with it. These are the same facts the files shared with Claude carry.")
        }
    }
}

object TrackerRecordText {
    /** "3 h 20 min before the start", "after the start". */
    fun lead(minutes: Long): String = when {
        minutes < 0 -> "${-minutes} min after the start"
        minutes < 60 -> "$minutes min before the start"
        minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min before the start"
        else -> "${minutes / 1440} d ${(minutes % 1440) / 60} h before the start"
    }
}
