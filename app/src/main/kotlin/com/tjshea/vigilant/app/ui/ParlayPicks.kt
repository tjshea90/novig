package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.MiniWindow
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.reference.ParlayBestBets
import com.tjshea.vigilant.data.reference.ParlayPick
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * ParlayAPI's own +EV picks at Novig as last read (Tj, 2026-09-30, PARLAY_API.md §6.5): every play it listed, each re-priced at Novig's
 * book ([ParlayPick]); [loading] while its boards are read (10 credits a league), [rechecking] while Novig's prices are re-read (free).
 */
data class ParlayPicksUi(
    val loading: Boolean = false,
    val rechecking: Boolean = false,
    val picks: List<ParlayPick> = emptyList(),
    val readAtMs: Long? = null,
    val leagues: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val summaries: List<String> = emptyList(),
    /** Vigilant's own fair line for each shown pick (by pick key) from a bets-only read after each scan and recheck (TASKS.md P2). */
    val vigilant: Map<String, com.tjshea.vigilant.data.tracker.OpenBetPricer.FairRead> = emptyMap(),
    val vigilantReading: Boolean = false,
    /** ParlayAPI's own books for a tapped pick CNO doesn't list (by pick key; TASKS.md P4). */
    val books: Map<String, com.tjshea.vigilant.data.cno.CnoBooksState> = emptyMap(),
)

/** What the +EV tab's ParlayAPI section does; the activity wires them. */
class ParlayPickActions(
    /** Read ParlayAPI's boards (10 credits a league). */
    val onScan: () -> Unit = {},
    /** Novig's prices again (free). */
    val onRecheck: () -> Unit = {},
    val onPlaced: (MiniWindow.Item) -> Unit = {},
    val onHide: (MiniWindow.Item) -> Unit = {},
    /** Novig's bet slip on this bet. */
    val onOpen: (CnoRow) -> Unit = {},
    /** The bet (row key) whose Novig link is being found. */
    val opening: String? = null,
)

/** The test tag of the ParlayAPI section's header. */
const val PARLAY_PICKS = "parlayPicks"

/** The picked leagues ParlayAPI's list covers (player props), and what a read of them costs. */
fun parlayLeagues(settings: ScanSettings): List<String> =
    settings.leagues.mapNotNull { Leagues.byNovigName(it) }.filter { ParlayBestBets.supports(it) }.map { it.displayName }

/**
 * The picks shown: found at Novig and +EV at Novig's price now within Tj's EV range and odds cap, games not started and in the start-time
 * window, and not a bet he already has (✓, ✕, or the same bet from another list). Best EV first.
 */
fun UiState.parlayShown(now: Long): List<ParlayPick> = parlayPicks.picks.filter { p ->
    val ev = p.ev ?: return@filter false
    val start = p.row.startsAtMs
    p.found && ev >= settings.minEvPercent && ev <= settings.maxEvPercent &&
        settings.withinMaxOdds(1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(p.row.odds)) &&
        (start == null || (start > now && settings.startsInWindow(start, now))) &&
        p.key !in placedKeys &&
        !placedIndex.has(key = p.key, event = p.row.event, market = p.row.market, selection = p.row.bet, startsTs = start, league = p.row.league)
}.sortedByDescending { it.ev }

/** A pick as the widget's item, for ✓ / ✕ (placed.json under its "parlay:" key) and the Tracker (logged as ParlayAPI's). */
fun parlayItem(p: ParlayPick): MiniWindow.Item = MiniWindow.Item(
    key = p.key,
    ev = p.ev ?: 0.0,
    title = p.row.bet,
    subtitle = "${p.row.market} · ${p.row.event}",
    price = MiniWindow.american(p.row.odds),
    available = p.available?.let { "$" + kotlin.math.round(it).toInt() },
    cno = CnoPick(p.row, p.ev ?: 0.0, false),
    event = p.row.event,
    market = p.row.market,
    startsAtMs = p.row.startsAtMs,
    league = p.row.league,
)

/**
 * The +EV tab's ParlayAPI section: its button (only on a tap: 10 credits a league), when it was read, how many plays it listed and how many
 * held up at Novig's own price. Shown only while ParlayAPI is on with a key.
 */
@Composable
fun ParlayPicksHeader(state: UiState, shown: Int, now: Long, actions: ParlayPickActions, modifier: Modifier = Modifier) {
    val ui = state.parlayPicks
    val leagues = parlayLeagues(state.settings)
    Card(
        modifier.fillMaxWidth().testTag(PARLAY_PICKS),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ParlayAPI's picks at ${com.tjshea.vigilant.app.AppBook.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            ui.loading -> "Reading ParlayAPI's boards, then ${com.tjshea.vigilant.app.AppBook.name}'s prices…"
                            ui.readAtMs == null -> "Its own +EV scan of player props, each checked at ${com.tjshea.vigilant.app.AppBook.name}'s price now."
                            else -> {
                                val found = ui.picks.count { it.found }
                                // +EV at Novig but longer than Tj's odds cap (home-run props often are): said, not silently dropped.
                                val s = state.settings
                                val capped = ui.picks.count { p ->
                                    p.found && (p.ev ?: -1.0) >= s.minEvPercent && !s.withinMaxOdds(1.0 / com.tjshea.vigilant.engine.Odds.americanToDecimal(p.row.odds))
                                }
                                "${ui.picks.size} listed · $shown +EV at ${com.tjshea.vigilant.app.AppBook.name} now" +
                                    (if (capped > 0) " · $capped over your +${s.maxOdds} odds cap" else "") +
                                    (if (ui.picks.size > found) " · ${ui.picks.size - found} not found there" else "") +
                                    " · read ${Format.age(ui.readAtMs, now)}"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ui.picks.isNotEmpty()) {
                    TextButton(onClick = actions.onRecheck, enabled = !ui.rechecking && !ui.loading) { Text(if (ui.rechecking) "Rechecking…" else "Recheck") }
                }
            }
            ui.errors.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning) }
            if (leagues.isEmpty()) {
                Text("Pick a league with player props (NFL, MLB, NBA, WNBA, NHL…): ParlayAPI's list is props only.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedButton(onClick = actions.onScan, enabled = !ui.loading, modifier = Modifier.fillMaxWidth()) {
                    if (ui.loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Asking ParlayAPI…")
                    } else {
                        Text(
                            (if (ui.readAtMs == null) "Scan ParlayAPI" else "Scan ParlayAPI again") +
                                " · ${leagues.size * ParlayBestBets.COST} credits (${leagues.joinToString(", ")})",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** One ParlayAPI pick at Novig's price now: Vigilant's EV there against ParlayAPI's fair price, and what ParlayAPI had listed. */
@Composable
fun ParlayPickCard(
    p: ParlayPick,
    settings: ScanSettings,
    now: Long,
    modifier: Modifier = Modifier,
    injury: com.tjshea.vigilant.data.reference.Injury? = null,
    actions: ParlayPickActions = ParlayPickActions(),
) {
    val row = p.row
    val play = p.play
    val league = Leagues.byNovigName(row.league)
    Card(
        modifier.fillMaxWidth().clickable { actions.onOpen(row) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvBadge(p.ev ?: 0.0)
                Spacer(Modifier.width(10.dp))
                Text(
                    listOfNotNull(league?.let { "${it.emoji} ${it.displayName}" }, row.startsAtMs?.let { Format.startTime(it) }, "ParlayAPI").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onPlaced(parlayItem(p)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "I placed ${row.bet}: hide it", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.onHide(parlayItem(p)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${row.bet} from the list", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.bet, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        injury?.let { Spacer(Modifier.width(6.dp)); InjuryTag(it) }
                    }
                    Text("${row.market} · ${row.event}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${com.tjshea.vigilant.app.AppBook.name.uppercase()} NOW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(MiniWindow.american(row.odds), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    // ParlayAPI's own listing is often far off Novig's book (PARLAY_API.md §5): shown when it differs.
                    if (play.listedAmerican != row.odds) {
                        Text("ParlayAPI had ${MiniWindow.american(play.listedAmerican)}", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                play.fairAmerican?.let { f -> LabeledValue("Fair", MiniWindow.american(f) + (row.fairProbability?.let { " · ${Format.percent(it)}" } ?: "")) }
                p.available?.let { LabeledValue("Available", Format.money(it)) }
                play.booksCompared?.let { LabeledValue("Books", it.toString()) }
                cnoStake(CnoPick(row, p.ev ?: 0.0, false), settings)?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
            }
            if (play.alert) {
                Text(
                    "Verify first: ParlayAPI flagged this price as far off the market" + (play.caveat?.let { " ($it)" } ?: "") + ".",
                    style = MaterialTheme.typography.labelSmall,
                    color = Edge.colors.warning,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (play.verdict?.let { "ParlayAPI: $it" } ?: "ParlayAPI edge alert") + (p.novigAtMs?.let { " · ${com.tjshea.vigilant.app.AppBook.name}'s book read ${Format.age(it, now)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OpenInBookButton("", opening = actions.opening == row.key, onClick = { actions.onOpen(row) })
            }
        }
    }
}
