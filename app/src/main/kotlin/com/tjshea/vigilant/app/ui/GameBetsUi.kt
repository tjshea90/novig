package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.tracker.GameBets
import com.tjshea.vigilant.engine.Odds

/**
 * What Tj has on each game and the most he allows on one ([com.tjshea.vigilant.data.scanner.ScanSettings.apiMaxPerGame], 0 = none), read by the small
 * button on every listed bet ([GameBetsChip]). Provided once at the top of the app; equal while nothing changed, so a card only redraws for a real change.
 */
data class GameBetsView(val bets: GameBets = GameBets.EMPTY, val limit: Double = 0.0)

val LocalGameBets = compositionLocalOf { GameBetsView() }

/** The words on the button and in its sheet (pure, so the tests read them without drawing anything). */
object GameBetsText {

    /** The button: the money in open bets on the game ("$21.30 in game"), or, with only bids up, what they'd cost ("$4.50 in bids"). */
    fun chip(s: GameBets.Summary): String =
        if (s.bets.isNotEmpty()) "${Format.money(s.placed)} in game" else "${Format.money(s.resting)} in bids"

    /** What a screen reader says for the button. */
    fun chipDescription(s: GameBets.Summary): String {
        val bets = s.bets.size
        val bids = s.bids.size
        val what = buildList {
            if (bets > 0) add("${Format.money(s.placed)} bet in this game on $bets ${if (bets == 1) "bet" else "bets"}")
            if (bids > 0) add("${Format.money(s.resting)} in $bids resting ${if (bids == 1) "bid" else "bids"}")
        }.joinToString(", ")
        return "$what, press for details"
    }

    /** One line's second row: its market, its price and, for the ones the app placed or has resting, which. */
    fun detail(l: GameBets.Line): String = listOfNotNull(
        l.market.takeIf { it.isNotBlank() },
        l.american?.let { Odds.formatAmerican(it) },
        when (l.kind) {
            GameBets.Kind.BID -> "bid resting, not a bet yet"
            GameBets.Kind.LOCK -> "lock"
            GameBets.Kind.BET -> if (l.auto) "auto-bet" else null
        },
    ).joinToString(" · ")

    /** "Total in open bets $21.30 (7 bets)". */
    fun total(s: GameBets.Summary): String {
        val n = s.bets.size
        return "Total bet ${Format.money(s.placed)} across $n ${if (n == 1) "bet" else "bets"}"
    }

    /** Why the limit's number is not the total: a lock or both sides of one market is on the game, counted once. */
    fun hedgeNote(s: GameBets.Summary): String? =
        if (s.hedged) "${Format.money(s.atRisk)} at risk: a lock, or both sides of one market, counts once" else null

    /** What the per-game limit leaves: its room, or that the game is at it; null when none is set. */
    fun limit(s: GameBets.Summary, limit: Double): String? {
        if (limit <= 0.0) return null
        val room = limit - s.atRisk
        return if (room <= 0.004) "At your ${Format.money(limit)} limit per game: auto-bet and bids add nothing more here"
        else "${Format.money(room)} room under your ${Format.money(limit)} limit per game"
    }

    /** Whether the game is at its limit (the button turns amber). */
    fun atLimit(s: GameBets.Summary, limit: Double): Boolean = limit > 0.0 && s.atRisk >= limit - 0.004
}

/**
 * A small button on a listed bet whose game Tj already has money on (Tj, 2026-10-04): "$21.30 in game ▾", and a tap shows each bet he placed on that
 * game, what each cost and the total. Nothing at all for a game he has nothing on, so a list isn't any busier than it was.
 */
@Composable
fun GameBetsChip(event: String, startsTs: Long?, league: String, modifier: Modifier = Modifier, eventId: String = "") {
    val view = LocalGameBets.current
    val summary = remember(view, event, startsTs, league, eventId) { view.bets.of(event, null, league, eventId) } ?: return
    GameBetsButton(summary, view.limit, modifier)
}

/** The button and its sheet for one game's [summary]. */
@Composable
internal fun GameBetsButton(summary: GameBets.Summary, limit: Double, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val warn = GameBetsText.atLimit(summary, limit)
    val description = GameBetsText.chipDescription(summary)
    Row(
        modifier
            .testTag("gameBetsChip")
            .semantics { contentDescription = description }
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (warn) Edge.colors.warning.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer)
            .clickable { open = true }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            GameBetsText.chip(summary) + " ▾",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (warn) Edge.colors.warning else MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
    if (open) GameBetsSheet(summary, limit) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameBetsSheet(summary: GameBets.Summary, limit: Double, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        GameBetsDetail(summary, limit)
    }
}

/** What Tj has on one game: each open bet and resting bid with its money, then the total. Takes immutable state, so it draws with sample data. */
@Composable
internal fun GameBetsDetail(summary: GameBets.Summary, limit: Double, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("gameBetsDetail"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Your bets in this game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            summary.game.eventName.ifBlank { "This game" } + summary.game.startsTs.takeIf { it > 0L }?.let { " · ${Format.startTime(it)}" }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        for (line in summary.bets) GameBetRow(line)
        if (summary.bids.isNotEmpty()) {
            if (summary.bets.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text("Resting bids (not placed yet)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (line in summary.bids) GameBetRow(line)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (summary.bets.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(GameBetsText.total(summary), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(Format.money(summary.placed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("gameBetsTotal"))
            }
        }
        if (summary.bids.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Resting bids if filled", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(Format.money(summary.resting), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
        }
        GameBetsText.hedgeNote(summary)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        GameBetsText.limit(summary, limit)?.let {
            Text(
                it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = if (GameBetsText.atLimit(summary, limit)) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("gameBetsLimit"),
            )
        }
    }
}

@Composable
private fun GameBetRow(line: GameBets.Line) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.selection.ifBlank { "Bet" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(GameBetsText.detail(line), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.padding(start = 12.dp))
        Text(Format.money(line.dollars), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}
