package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cloud.DashboardModel
import com.tjshea.vigilant.data.reference.PlEvRow
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

/**
 * PropLine's cross-book +EV scanner at Novig (Tj, 2026-10-16, TCL56: "make a section in the android app for this because I want to see what +ev bets it finds"). The GitHub engine asks PropLine Pro's `/ev`
 * for every pregame game of the picked leagues (Novig's price against a no-vig fair line from the sharpest book that prices it) and publishes the +EV lines; this lists them, best first. A line is
 * information: PropLine read Novig's price some minutes ago (a prop's up to 15-45), so the Bet sheet in the +EV tab still re-prices at Novig's live book before anything is placed.
 */
@Composable
fun PropLineEvScreen(state: UiState, modifier: Modifier = Modifier) {
    val now = rememberNow(30_000)
    val feed = state.cloudUi.propLineEv
    var league by rememberSaveable { mutableStateOf("All") }
    var minEv by rememberSaveable { mutableStateOf(1.0) }
    var props by rememberSaveable { mutableStateOf("All") }   // All | Props | Game lines
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("propLineEv"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PropLine +EV at Novig", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (feed == null) {
            Text(
                "Nothing yet. The GitHub engine sweeps PropLine every 15 minutes while a paid PropLine key (Hobby or Pro) is on and the engine is running. Add the key under Settings › PropLine, then send your keys to GitHub.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("propLineEvEmpty"),
            )
            return@Column
        }
        Text(
            "Swept ${DashboardModel.ago(now - feed.atMs)} · ${feed.games} games · ${feed.requests} requests · ${feed.tier.ifBlank { "?" }} plan" + (if (feed.note.isNotBlank()) " · ${feed.note}" else ""),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("propLineEvAge"),
        )
        val leagues = listOf("All") + feed.rows.map { it.league }.distinct().sorted()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leagues.take(6).forEach { l -> FilterChip(selected = league == l, onClick = { league = l }, label = { Text(l, maxLines = 1) }, modifier = Modifier.testTag("plLeague-$l")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Edge ≥", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.5, 1.0, 2.0, 3.0, 5.0).forEach { v -> FilterChip(selected = minEv == v, onClick = { minEv = v }, label = { Text("${v.toString().removeSuffix(".0")}%") }, modifier = Modifier.testTag("plMin-$v")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Props", "Game lines").forEach { k -> FilterChip(selected = props == k, onClick = { props = k }, label = { Text(k, maxLines = 1) }, modifier = Modifier.testTag("plKind-$k")) }
        }
        val shown = remember(feed, league, minEv, props, now / 60_000L) {
            PropLineEvView.select(feed.rows, league, minEv, props, now)
        }
        Text(
            "${shown.size} +EV lines at Novig" + if (shown.size != feed.rows.size) " (of ${feed.rows.size})" else "",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("propLineEvCount"),
        )
        if (shown.isEmpty()) Text("No line passes these filters right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(shown, key = { PropLineEvView.key(it) }) { r -> PropLineEvCard(r, now) }
        }
    }
}

@Composable
private fun PropLineEvCard(r: PlEvRow, now: Long) {
    val priceAgeMin = ((now - r.updatedAtMs) / 60_000L).coerceAtLeast(0)
    val stale = priceAgeMin >= PropLineEvView.STALE_MIN
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("plRow-${PropLineEvView.key(r)}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PropLineEvView.pick(r), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "+%.1f%%", r.evPct), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("${r.league} · ${r.away} @ ${r.home} · ${PropLineEvView.starts(r, now)}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Novig ${Odds.formatAmerican(r.price)} · fair ${Format.american(r.fairProb)} (${Format.percent(r.fairProb)}) from ${r.fairSource.ifBlank { "?" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Novig's price read ${if (priceAgeMin <= 0) "just now" else "$priceAgeMin min ago"}" + if (stale) " · old: check the live price before betting" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (stale) com.tjshea.vigilant.app.ui.Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the screen shows, pure and testable ([PropLineEvViewTest]). */
object PropLineEvView {
    /** A price PropLine read this many minutes ago is flagged old. */
    const val STALE_MIN = 20

    fun key(r: PlEvRow) = "${r.eventId}|${r.market}|${r.player}|${r.point}|${r.outcome}"

    fun isProp(r: PlEvRow) = r.player.isNotBlank()

    /** "Tyrese Maxey Over 24.5 · Points" or "Celtics -3.5" for a game line. */
    fun pick(r: PlEvRow): String {
        val pt = r.point?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
        val market = r.market.removePrefix("player_").removePrefix("batter_").removePrefix("pitcher_").replace('_', ' ').replaceFirstChar { it.uppercase() }
        return if (isProp(r)) "${r.player} ${r.outcome}${pt?.let { " $it" } ?: ""} · $market" else "${r.outcome}${pt?.let { " $it" } ?: ""} · ${market}"
    }

    fun starts(r: PlEvRow, now: Long): String {
        val mins = (r.startsAtMs - now) / 60_000L
        return when {
            mins < 0 -> "started"
            mins < 90 -> "in $mins min"
            mins < 48 * 60 -> "in ${mins / 60} h ${mins % 60} min"
            else -> "in ${mins / 1440} days"
        }
    }

    /** The lines to list: league, edge floor and kind filters, games not yet started, best edge first. */
    fun select(rows: List<PlEvRow>, league: String, minEv: Double, kind: String, now: Long): List<PlEvRow> =
        rows.filter { r ->
            r.startsAtMs > now && r.evPct >= minEv &&
                (league == "All" || r.league == league) &&
                when (kind) { "Props" -> isProp(r); "Game lines" -> !isProp(r); else -> true }
        }.sortedByDescending { it.evPct }
}
