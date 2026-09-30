package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.reference.LineMove
import com.tjshea.vigilant.data.reference.Mover
import com.tjshea.vigilant.data.reference.MoversBoard
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.engine.Odds
import java.util.Locale
import kotlin.math.abs

/** The test tag of the Games tab's line-moves card. */
const val LINE_MOVES_CARD = "lineMovesCard"

private fun american(v: Int?): String = v?.let { Odds.formatAmerican(it) } ?: "?"

private fun pts(pp: Double): String = String.format(Locale.US, "%+.1f pts", pp)

private fun window(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

/** "Pinnacle moved toward it: +130 → +123 (+1.4 pts in 6 h)", or "…against it…". */
fun moveText(m: LineMove): String =
    "Pinnacle moved ${if (m.toward) "toward" else "against"} it: ${american(m.first)} → ${american(m.last)} (${pts(m.pp)} in ${window(m.windowMinutes)})"

/**
 * A team bet's game moved at Pinnacle (Tj, 2026-09-30, PARLAY_API.md §6.3): toward the bet's side (green: closing-line value in the making)
 * or against it (red: the edge may be the market moving away from Novig's price).
 */
@Composable
fun LineMoveNote(m: LineMove, modifier: Modifier = Modifier) {
    Text(
        moveText(m),
        modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (m.toward) Edge.colors.positive else Edge.colors.negative,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The biggest moves across [boards] of the [leagues] picked, biggest first. */
fun topMoves(boards: Map<String, MoversBoard>, leagues: Set<String>, max: Int = MOVES_SHOWN): List<Mover> {
    val sports = leagues.mapNotNull { Leagues.byNovigName(it)?.oddsApiSportKey }.toSet()
    return boards.filterKeys { it in sports }.values.flatMap { it.movers }.sortedByDescending { it.size }.take(max)
}

/** Moves listed on the Games tab. */
const val MOVES_SHOWN = 5

/**
 * The Games tab's "Line moves" card (PARLAY_API.md §6.3): the games in the picked leagues whose Pinnacle moneyline moved most in the last
 * 6 hours (a point or more of probability), each with the side the money went to and its price then and now.
 */
@Composable
fun LineMovesCard(boards: Map<String, MoversBoard>, leagues: Set<String>, modifier: Modifier = Modifier) {
    val moves = topMoves(boards, leagues)
    if (moves.isEmpty()) return
    val windowMinutes = boards.values.maxOfOrNull { it.windowMinutes } ?: 360
    Card(
        modifier.fillMaxWidth().testTag(LINE_MOVES_CARD),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Line moves at Pinnacle · last ${window(windowMinutes)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            moves.forEach { mv ->
                val league = Leagues.ALL.firstOrNull { it.oddsApiSportKey == mv.sportKey }
                val (team, first, last, pp) = if (mv.steamHome) listOf(mv.home, mv.homeFirst, mv.homeLast, mv.homePp) else listOf(mv.away, mv.awayFirst, mv.awayLast, mv.awayPp)
                Column {
                    Text(
                        "${league?.emoji.orEmpty()} ${league?.displayName.orEmpty()} · ${Format.startTime(mv.commenceMs)}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${mv.away} @ ${mv.home}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Money on $team: ${american(first as Int?)} → ${american(last as Int?)} (${pts(abs(pp as Double))})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "Pinnacle's moneyline, from ParlayAPI (free). A move toward a side you bet is closing-line value; against it, the edge may be the market leaving ${com.tjshea.vigilant.app.AppBook.name}'s price behind.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
