package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
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
fun PropLineEvScreen(
    state: UiState,
    onTrack: (com.tjshea.vigilant.data.scanner.Opportunity, Double) -> Unit = { _, _ -> },
    onRecheck: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val now = rememberNow(30_000)
    val feed = state.cloudUi.propLineEv
    var league by rememberSaveable { mutableStateOf("All") }
    var minEv by rememberSaveable { mutableStateOf(1.0) }
    var props by rememberSaveable { mutableStateOf("All") }   // All | Props | Game lines
    // Longshots carry the biggest edges on paper (a +1900 home run at +28%) and lose to the favorite-longshot bias (RESEARCH.md §8.1, §16.2): the list starts at +300 and under.
    var maxOdds by rememberSaveable { mutableStateOf(300) }
    // statusBarsPadding: this tab has no top bar of its own, and the title sat under the clock (Tj, 2026-10-16 screenshot: "the app is getting cut off at the top").
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var betting by remember { mutableStateOf<com.tjshea.vigilant.data.scanner.Opportunity?>(null) }
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp).testTag("propLineEv"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leagues.take(12).forEach { l -> FilterChip(selected = league == l, onClick = { league = l }, label = { Text(l, maxLines = 1) }, modifier = Modifier.testTag("plLeague-$l")) }
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Edge ≥", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.5, 1.0, 2.0, 3.0, 5.0).forEach { v -> FilterChip(selected = minEv == v, onClick = { minEv = v }, label = { Text("${v.toString().removeSuffix(".0")}%") }, modifier = Modifier.testTag("plMin-$v")) }
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Props", "Game lines").forEach { k -> FilterChip(selected = props == k, onClick = { props = k }, label = { Text(k, maxLines = 1) }, modifier = Modifier.testTag("plKind-$k")) }
        }
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Odds up to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(150, 300, 600, PropLineEvView.ANY_ODDS).forEach { v -> FilterChip(selected = maxOdds == v, onClick = { maxOdds = v }, label = { Text(if (v == PropLineEvView.ANY_ODDS) "Any" else "+$v") }, modifier = Modifier.testTag("plOdds-$v")) }
        }
        val shown = remember(feed, league, minEv, props, maxOdds, now / 60_000L) {
            PropLineEvView.select(feed.rows, league, minEv, props, now, maxOdds)
        }
        Text(
            "${shown.size} +EV lines at Novig" + if (shown.size != feed.rows.size) " (of ${feed.rows.size})" else "",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("propLineEvCount"),
        )
        if (shown.isEmpty()) Text("No line passes these filters right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(shown, key = { PropLineEvView.key(it) }) { r ->
                val k = PropLineEvView.key(r)
                PropLineEvCard(r, now, expanded = k in expanded, onToggle = { expanded = if (k in expanded) expanded - k else expanded + k }, onBet = { betting = PropLineEvView.opportunity(r) })
            }
        }
    }
    betting?.let { o ->
        // A line Vigilant had no fair for sits in the scan unpriced: the row's own PropLine-priced copy is what the sheet shows then.
        val live = state.result?.opportunities?.firstOrNull { it.key == o.key && it.quote != null } ?: o
        OpportunitySheet(
            live, state.settings,
            onDismiss = { betting = null },
            onTrack = { stake -> onTrack(live, stake); betting = null },
            onRecheck = { onRecheck(listOf(live.market.marketId)) }.takeIf { !state.status.scanning },
            rechecking = state.status.rechecking,
        )
    }
}

@Composable
private fun PropLineEvCard(r: PlEvRow, now: Long, expanded: Boolean, onToggle: () -> Unit, onBet: () -> Unit) {
    val priceAgeMin = ((now - r.updatedAtMs) / 60_000L).coerceAtLeast(0)
    val stale = priceAgeMin >= PropLineEvView.STALE_MIN
    val cal = PropLineEvView.calibratedEv(r)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("plRow-${PropLineEvView.key(r)}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LosingTypeBanner(com.tjshea.vigilant.data.scanner.ProfitProfile.losingType(r.league, (if (r.player.isNotBlank()) com.tjshea.vigilant.data.scanner.BetKind.PROP else com.tjshea.vigilant.data.scanner.BetKind.of(r.market, PropLineEvView.pick(r))).label, PropLineEvView.pick(r)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PropLineEvView.pick(r), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(String.format(Locale.US, "%+.1f%%", cal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("${r.league} · ${r.away} @ ${r.home} · ${PropLineEvView.starts(r, now)}", style = MaterialTheme.typography.bodySmall)
            Text(
                "Novig ${Odds.formatAmerican(r.price)} · fair ${Format.american(r.fairProb)} (${Format.percent(r.fairProb)}) from ${r.fairSource.ifBlank { "?" }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (kotlin.math.abs(cal - r.evPct) >= 0.5) Text(
                String.format(Locale.US, "PropLine says %+.1f%%; after Vigilant's prop cut %+.1f%% (prop Overs PropLine called +EV lost money on 6,400 graded bets)", r.evPct, cal),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("plCalibrated"),
            )
            Text(
                "Novig's price read ${if (priceAgeMin <= 0) "just now" else "$priceAgeMin min ago"}" + if (stale) " · old: check the live price before betting" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (stale) com.tjshea.vigilant.app.ui.Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val verdict = PropLineEvView.verdict(r)
            Text(
                verdict.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("plVerdict"),
                color = when (verdict.level) {
                    PropLineEvView.Level.GOOD -> Edge.colors.positive
                    PropLineEvView.Level.WARN -> Edge.colors.warning
                    PropLineEvView.Level.BAD -> Edge.colors.negative
                    PropLineEvView.Level.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (expanded) {
                if (r.others.isNotEmpty()) {
                    Text("Same bet at other books now (best price first):", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        r.others.joinToString("  ·  ") { b -> PropLineEvView.bookName(b.book) + " " + Odds.formatAmerican(b.price) + PropLineEvView.oldTag(b, now) },
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("plOthers"),
                    )
                    val best = r.others.first()
                    Text(
                        if (r.price >= best.price) "Novig's ${Odds.formatAmerican(r.price)} is the best price of them all." else "Best elsewhere: ${PropLineEvView.bookName(best.book)} ${Odds.formatAmerican(best.price)} (Novig ${Odds.formatAmerican(r.price)}).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else Text("PropLine gave no other book's price for this line.", style = MaterialTheme.typography.bodySmall)
                if (r.trend.isNotBlank()) Text(r.trend, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("plTrend"))
                Text(
                    r.vigilantEv?.let { "Vigilant's own scan prices this Novig line at ${if (it >= 0) "+" else ""}${String.format(Locale.US, "%.1f", it)}% now" + (r.vigilantStars?.let { s -> " · ${s} stars" } ?: "") }
                        ?: if (r.oppFromPropLine) "Vigilant had no fair odds for this line, so the Bet slip is priced from PropLine's fair (after Vigilant's prop cut)."
                        else "Vigilant's own scan did not match this line to a Novig market (no Bet button).",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (r.opp != null) Button(onClick = onBet, modifier = Modifier.padding(top = 4.dp).testTag("plBet")) { Text("Bet this (re-checks Novig's live price)") }
            } else Text(if (r.opp != null) "Tap for the other books and to bet" else "Tap for the other books", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    enum class Level { GOOD, WARN, BAD, NONE }
    data class Verdict(val level: Level, val text: String)

    /** The cross-check in words: how many books that price both sides back Novig's price, and whether Pinnacle does. */
    fun verdict(r: PlEvRow): Verdict = when {
        r.others.isEmpty() -> Verdict(Level.NONE, "No cross-check: PropLine gave no other book's price for this line.")
        r.twoSided == 0 -> Verdict(Level.WARN, "Only ${r.fairSource.ifBlank { "the fair source" }} backs this: no other book prices both sides, so it may be a stale or thin line.")
        else -> {
            val share = r.agree.toDouble() / r.twoSided
            val pin = when (r.pinnacleAgrees) { true -> " Pinnacle agrees."; false -> " Pinnacle does not."; null -> "" }
            val text = "${r.agree} of ${r.twoSided} books that price both sides say Novig's price is +EV.$pin"
            when {
                share >= 0.6 && r.pinnacleAgrees != false -> Verdict(Level.GOOD, text)
                share <= 0.3 -> Verdict(Level.BAD, "$text Treat it as unproven.")
                else -> Verdict(Level.WARN, text)
            }
        }
    }

    /** "DraftKings", "Hard Rock"... from PropLine's keys. */
    fun bookName(key: String): String = when (key) {
        "draftkings" -> "DraftKings"; "fanduel" -> "FanDuel"; "betmgm" -> "BetMGM"; "pinnacle" -> "Pinnacle"; "hardrock" -> "Hard Rock"; "betrivers" -> "BetRivers"
        else -> key.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /** " (23 min old)" when PropLine saw the price change that long ago, else nothing. */
    fun oldTag(b: com.tjshea.vigilant.data.reference.PlBookPrice, now: Long): String {
        val m = (now - b.updatedAtMs) / 60_000L
        return if (m >= STALE_MIN) " (${m} min old)" else ""
    }

    /** The Opportunity PropLine's row was matched to by the engine (decoded from its JSON), or null. */
    fun opportunity(r: PlEvRow): com.tjshea.vigilant.data.scanner.Opportunity? = r.opp?.let { raw ->
        runCatching { com.tjshea.vigilant.data.cloud.CloudCodec.decode(kotlinx.serialization.json.Json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject) }.getOrNull()
    }

    /** No odds limit. */
    const val ANY_ODDS = 100_000

    fun select(rows: List<PlEvRow>, league: String, minEv: Double, kind: String, now: Long, maxOdds: Int = ANY_ODDS): List<PlEvRow> =
        rows.filter { r ->
            r.startsAtMs > now && calibratedEv(r) >= minEv && r.price <= maxOdds &&
                (league == "All" || r.league == league) &&
                when (kind) { "Props" -> isProp(r); "Game lines" -> !isProp(r); else -> true }
        }.sortedByDescending { calibratedEv(it) }

    /**
     * PropLine's edge after Vigilant's own prop cut ([com.tjshea.vigilant.data.scanner.PropHaircut]: -7 pts on an Over, -3 on an Under; research/propline_vs_vigilant_2026-10-16.md: the Overs PropLine called +EV
     * lost 9-14% over 6,400 graded bets). A game line is not cut. This is the number the list is filtered and ranked on.
     */
    fun calibratedEv(r: PlEvRow): Double {
        if (!isProp(r)) return r.evPct
        val cut = com.tjshea.vigilant.data.scanner.PropHaircut.points(com.tjshea.vigilant.data.scanner.BetKind.PROP, r.outcome)
        if (cut <= 0.0) return r.evPct
        val dec = if (r.price > 0) 1 + r.price / 100.0 else 1 + 100.0 / -r.price
        return ((r.fairProb - cut).coerceAtLeast(0.0) * dec - 1) * 100
    }
}
