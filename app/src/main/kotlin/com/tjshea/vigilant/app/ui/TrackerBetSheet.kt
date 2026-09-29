package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.data.novig.NovigLinks
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BetRecheck
import com.tjshea.vigilant.data.tracker.BetReplace
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.Odds

/** An open bet's books are read again on opening the sheet when the last read is older than this. */
private const val REREAD_AFTER_MS = 2 * 60_000L

/**
 * An open bet in full (Tj, 2026-09-29): the odds he bet at, the fair price now and the gap between them,
 * every book's odds for the same bet with what each would make of his price, how it was graded, and
 * what to do with it (Replace, re-read, mark a result). CNO bets read their books again on opening
 * when the last read is old; Vigilant's own update with each scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetSheet(
    bet: TrackedBet,
    now: Long,
    settings: ScanSettings,
    rereading: Boolean,
    grading: Boolean,
    replacing: Boolean,
    actions: BetActions,
    onSettle: (BetStatus) -> Unit,
    onStake: () -> Unit,
    onPrice: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Current odds, not last time's: the books are read once when the sheet opens on old ones.
    LaunchedEffect(bet.id) {
        val t = System.currentTimeMillis()
        val old = bet.booksAtMs == null || t - bet.booksAtMs!! > REREAD_AFTER_MS
        if (bet.status == BetStatus.PENDING && bet.gameUrl != null && t - bet.startsTs < BetRecheck.STALE_AFTER_START_MS && old) actions.onReread(bet.id, true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        BetSheetContent(bet, remember(bet) { BetInsight.of(bet) }, now, settings, rereading, grading, replacing, actions, onSettle, onStake, onPrice, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BetSheetContent(
    bet: TrackedBet,
    insight: BetInsight,
    now: Long,
    settings: ScanSettings,
    rereading: Boolean,
    grading: Boolean,
    replacing: Boolean,
    actions: BetActions,
    onSettle: (BetStatus) -> Unit,
    onStake: () -> Unit,
    onPrice: () -> Unit,
    onDelete: () -> Unit,
) {
    val open = bet.status == BetStatus.PENDING
    val started = now >= bet.startsTs
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()).testTag("betSheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(bet.selection, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${bet.marketLabel} · ${bet.eventName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                listOf(bet.league, Format.startTime(bet.startsTs), if (open) TrackerText.startsIn(bet.startsTs, now) else null).filter { !it.isNullOrBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                (if (bet.source == BetTracker.SOURCE_CNO) "Found by CNO" else "Found by Vigilant") + " · ${bet.book.ifBlank { AppBook.name }}" +
                    " · placed ${Format.startTime(bet.createdAtMs)}" + (if (bet.imported) " · from an earlier ✓" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- What happened / what it's waiting for ----
        StatusCard(bet, now)

        // ---- Your bet ----
        Text("Your bet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledValue("Odds bet at", Odds.formatAmerican(insight.betOdds))
            LabeledValue("Implied chance", Format.percent(insight.betImplied))
            if (bet.viaApi) LabeledValue("Stake", Format.money(bet.stake))
            else LabeledValue("Stake ✎", Format.money(bet.stake), Modifier.clickable(onClickLabel = "Change the stake", onClick = onStake))
            LabeledValue(
                if (open) "To win" else "Result",
                bet.profit?.let { Format.signedMoney(it) } ?: Format.money(bet.profitIfWon),
                valueColor = bet.profit?.let { moneyColor(it) } ?: Color.Unspecified,
            )
            insight.evAtBet?.let { LabeledValue("EV when bet", Format.evPercent(it)) }
            insight.fairAtBet?.let { LabeledValue("Fair when bet", "${Format.american(it)} · ${Format.percent(it)}") }
        }

        // ---- The fair price now against it ----
        if (open) NowCard(bet, insight, now, rereading)

        // ---- Every book ----
        if (insight.books.isNotEmpty()) {
            InsightBooks(insight, bet.otherSide, bet.book.ifBlank { AppBook.name })
            Text(
                "Read ${Format.age(insight.booksAtMs, now)}" + if (rereading) " · reading again…" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (open && !rereading) {
            Caption(
                if (bet.gameUrl != null) "No book has been read for this bet yet: tap Re-read books."
                else "No book has been read for this bet yet: Vigilant prices its own bets from Vigilant's fair odds. Tap Price now (or Check odds now on the list).",
            )
        }

        // ---- Actions ----
        if (open) {
            val stake = BetReplace.stake(bet, settings)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { actions.onReplace(bet) }, enabled = !replacing, modifier = Modifier.testTag("replaceInSheet")) {
                    if (replacing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Opening…")
                    } else {
                        Text("Replace bet" + (stake?.let { " · $" + NovigLinks.amountText(it) } ?: ""))
                    }
                }
                if (bet.gameUrl != null) {
                    OutlinedButton(onClick = { actions.onReread(bet.id, false) }, enabled = !rereading) { Text(if (rereading) "Reading…" else "Re-read books") }
                } else if (settings.vigilantOn) {
                    OutlinedButton(onClick = actions.onScan) { Text("Update from a scan") }
                }
                if (started) OutlinedButton(onClick = actions.onGrade, enabled = !grading) { Text(if (grading) "Grading…" else "Grade now") }
                if (bet.autoGradeOff) OutlinedButton(onClick = { actions.onRegrade(bet.id) }) { Text("Grade automatically") }
            }
            Caption(
                "Replace opens ${AppBook.name}'s bet slip on this exact bet" +
                    (stake?.let { " with $${NovigLinks.amountText(it)} filled in (Settings › bet slip amount)" } ?: " (Settings › bet slip amount can fill in an amount)") + ".",
            )
        }

        // ---- Result ----
        if (open) {
            if (started) {
                Text("Mark the result yourself", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { onSettle(BetStatus.WON) }, label = { Text("Won") })
                    AssistChip(onClick = { onSettle(BetStatus.LOST) }, label = { Text("Lost") })
                    AssistChip(onClick = { onSettle(BetStatus.PUSH) }, label = { Text("Push") })
                    AssistChip(onClick = { onSettle(BetStatus.VOID) }, label = { Text("Void") })
                }
            }
        } else {
            val name = if (bet.status == BetStatus.FMV) "Fair value" else bet.status.name.lowercase().replaceFirstChar { it.uppercase() }
            AssistChip(onClick = { onSettle(BetStatus.PENDING) }, label = { Text("$name · undo") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // A bet placed through the API has its stake and price from Novig's fills: nothing to correct.
            if (!bet.viaApi) {
                TextButton(onClick = onStake) { Text("Change stake") }
                TextButton(onClick = onPrice) { Text("Change price") }
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/** The result and how it was graded, or what the bet is waiting for. */
@Composable
private fun StatusCard(bet: TrackedBet, now: Long) {
    val open = bet.status == BetStatus.PENDING
    val awaiting = TrackerText.awaiting(bet, now)
    val (text, color) = when {
        !open -> {
            val name = if (bet.status == BetStatus.FMV) "Settled at fair value" else bet.status.name.lowercase().replaceFirstChar { it.uppercase() }
            val by = when (bet.settledBy) {
                BetSettler.BY_NOVIG -> if (bet.viaApi) "graded by Novig's own books" else "graded from the final score"
                BetSettler.BY_SCORES -> "graded from the final score"
                BetSettler.BY_YOU -> "marked by you"
                else -> null
            }
            (listOfNotNull(name, by).joinToString(" · ") + (bet.gradeNote?.takeIf { bet.settledBy != BetSettler.BY_YOU }?.let { "\n$it" }.orEmpty())) to
                (bet.profit?.let { moneyColor(it) } ?: Color.Unspecified)
        }
        awaiting != null -> awaiting.text to if (awaiting.tone == TrackerText.Tone.ATTENTION) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
        else -> TrackerText.startsIn(bet.startsTs, now) + ": open until the game is over and graded from the final score" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
        SelectionContainer {
            Text(text, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The price bet at against the fair price now: the gap in points, the EV, how far the market moved. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowCard(bet: TrackedBet, i: BetInsight, now: Long, rereading: Boolean) {
    val ev = i.evNow
    val tone = when {
        ev == null -> MaterialTheme.colorScheme.onSurfaceVariant
        ev >= 0 -> Edge.colors.positive
        else -> Edge.colors.negative
    }
    Surface(color = tone.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (ev != null) "Now ${Format.evPercent(ev)} EV at your price" else "Fair price now",
                color = tone, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
            )
            if (ev == null && rereading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading every book's odds from CNO…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(TrackerText.edgeSentence(i), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    i.fairNow?.let { LabeledValue("Fair now", "${Format.american(it)} · ${Format.percent(it)}") }
                    i.priceNow?.let { LabeledValue("${bet.book.ifBlank { AppBook.name }} now", Odds.formatAmerican(it)) }
                    i.clv?.let { LabeledValue("CLV so far", Format.evPercent(it), valueColor = moneyColor(it)) }
                    i.booksBehind?.let { LabeledValue("Books behind it", "$it") }
                }
                TrackerText.moveSentence(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                TrackerText.breakEvenSentence(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                bet.nowAtMs?.let { Caption("Fair price worked out ${Format.age(it, now)}, from the books' odds devigged worst case (the lower of their average and median).") }
            }
        }
    }
}

/** Every book's odds for the bet and its other side, its own devigged fair, and what the price bet at is worth against it. */
@Composable
private fun InsightBooks(i: BetInsight, otherSide: String?, ownBook: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Every book", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("Book", 1.5f)
            HeaderCell("This bet", 1f)
            HeaderCell(if (otherSide != null) "Other side" else "Other", 1f)
            HeaderCell("Fair", 0.8f)
            HeaderCell("EV at yours", 1f)
        }
        i.books.forEach { r ->
            val strong = r.counted || r.isOwn
            val dim = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.fillMaxWidth()
                    .background(if (r.isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.name, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, fontWeight = if (r.isOwn) FontWeight.Bold else FontWeight.Normal, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.odds?.let { Odds.formatAmerican(it) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = dim, maxLines = 1)
                Text(r.other?.let { Odds.formatAmerican(it) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = dim, maxLines = 1)
                Text(if (r.counted) r.fair?.let { Format.percent(it) } ?: "" else if (r.isOwn) "yours" else "—", Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1)
                val ev = r.ev.takeIf { r.counted }
                Text(
                    // A price a hair either side of fair is "0.0%", not "−0.0%".
                    ev?.let { if (kotlin.math.abs(it) < 0.0005) "0.0%" else Format.evPercentShort(it) } ?: "—",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ev == null) dim else moneyColor(ev),
                    maxLines = 1,
                )
            }
        }
        Caption(
            "Fair = that book's odds devigged worst case; only books pricing both sides count. EV at yours = what ${Odds.formatAmerican(i.betOdds)} on $ownBook " +
                "would be worth if that book's fair price alone were right (green: it still beats that book).",
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
}
