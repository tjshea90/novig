package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.ncaab.NcaabGameView
import com.tjshea.vigilant.data.ncaab.NcaabPick
import com.tjshea.vigilant.data.ncaab.NcaabSignals
import com.tjshea.vigilant.data.ncaab.NcaabSymbols
import com.tjshea.vigilant.data.ncaab.NcaabTeam
import com.tjshea.vigilant.data.ncaab.NcaabText
import com.tjshea.vigilant.data.ncaab.NcaabView
import com.tjshea.vigilant.data.scanner.Opportunity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * College basketball (NC1, Tj 2026-11-05): the day's games with each team's points per game and green-plus / red-minus average next to its name, Novig's spread and total, a Bet button on each side,
 * "strongest leans" by Tj's method and the spreads and totals furthest from the averages. The averages come from the `ncaab-data` branch (a GitHub job logs every finished game and its closing line),
 * Novig's prices from the engine's board. Everything shown beside a lean is what that signal actually did against closing lines, never a promise.
 */
@Composable
fun NcaabScreen(
    state: UiState,
    onWant: (Boolean) -> Unit,
    onTrack: (Opportunity, Double) -> Unit = { _, _ -> },
    onRecheck: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cu = state.cloudUi
    // Looked at: keep the files current while the tab is open (a free 304 when nothing changed).
    LaunchedEffect(Unit) { while (true) { onWant(false); delay(60_000L) } }
    val now = rememberNow(30_000)
    val minute = now / 60_000L
    val views = remember(cu.ncaabSlate, cu.ncaabRatings, cu.ncaabBoard, minute) { NcaabView.build(cu.ncaabSlate, cu.ncaabRatings, cu.ncaabBoard, now) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(2_500L); refreshing = false } }
    VigilantPullToRefresh(busy = refreshing, onRefresh = { refreshing = true; onWant(true) }, modifier = modifier.fillMaxSize()) {
        NcaabContent(
            views = views, ratingsAsOf = cu.ncaabRatings?.asOf, boardAt = cu.ncaabBoard?.atMs, boardReadAt = cu.ncaabBoardReadAtMs, note = cu.ncaabNote,
            model = cu.ncaabRatings?.model, ratedTeams = cu.ncaabRatings?.teams?.size ?: 0, hasSlate = cu.ncaabSlate != null, hasToken = cu.hasToken,
            settings = state.settings, now = now, onTrack = onTrack, onRecheck = onRecheck,
        )
    }
}

private enum class Page(val label: String) { GAMES("Games"), LEANS("Leans"), UNLIKE("Unlike averages") }

@Composable
internal fun NcaabContent(
    views: List<NcaabGameView>,
    ratingsAsOf: Long?,
    boardAt: Long?,
    boardReadAt: Long,
    note: String,
    model: com.tjshea.vigilant.data.ncaab.NcaabModel?,
    ratedTeams: Int,
    hasSlate: Boolean,
    hasToken: Boolean,
    settings: com.tjshea.vigilant.data.scanner.ScanSettings,
    now: Long,
    onTrack: (Opportunity, Double) -> Unit,
    onRecheck: (List<String>) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(Page.GAMES.name) }
    var day by rememberSaveable { mutableStateOf("Today") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var evidence by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(now / 3_600_000L) { LocalDate.now(zone) }
    fun dayOf(v: NcaabGameView): LocalDate = Instant.ofEpochMilli(v.game.t).atZone(zone).toLocalDate()
    val shown = remember(views, day, today) {
        views.filter { v ->
            when (day) {
                "Today" -> !dayOf(v).isAfter(today)
                "Tomorrow" -> dayOf(v) == today.plusDays(1)
                else -> true
            }
        }
    }
    val current = Page.valueOf(page)
    val selected = views.firstOrNull { it.id == open }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp).testTag("ncaab"), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("College basketball", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(freshness(ratingsAsOf, boardAt, boardReadAt, ratedTeams, now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("ncaabFresh"))
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.labelMedium, color = LocalEdgeColors.current.warning)
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Page.entries.forEach { p -> FilterChip(selected = current == p, onClick = { page = p.name }, label = { Text(p.label, maxLines = 1) }, modifier = Modifier.testTag("ncaabPage-${p.name}")) }
                }
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Today", "Tomorrow", "All").forEach { d -> FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d, maxLines = 1) }, modifier = Modifier.testTag("ncaabDay-$d")) }
                }
                if (model != null && model.measured) EvidenceCard(model, evidence, onToggle = { evidence = !evidence })
            }
        }
        when {
            !hasToken -> item { Hint("Save a GitHub token (Settings › Cloud scan) and this tab reads the college basketball log the engine keeps.") }
            !hasSlate && views.isEmpty() -> item { Hint("Nothing yet. A free GitHub job logs every college game and its closing line a few times a day; the first run builds the schedule. Pull down to look again.", "ncaabEmpty") }
            shown.isEmpty() -> item { Hint("No games for ${day.lowercase()}.", "ncaabNone") }
            current == Page.GAMES -> items(shown, key = { it.id }) { v -> GameCard(v, onOpen = { open = v.id }, now = now) }
            current == Page.LEANS -> leans(shown, onOpen = { open = it })
            else -> unlike(shown, onOpen = { open = it })
        }
        item { Text(footnote(model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    }
    if (selected != null) NcaabGameSheet(selected, model, settings, now, onDismiss = { open = null }, onTrack = onTrack, onRecheck = onRecheck)
}

private fun freshness(ratingsAsOf: Long?, boardAt: Long?, boardReadAt: Long, rated: Int, now: Long): String {
    val parts = ArrayList<String>()
    parts += if (ratingsAsOf != null && ratingsAsOf > 0) "averages ${Format.age(ratingsAsOf, now)} ($rated teams rated)" else "no averages yet"
    parts += if (boardAt != null && boardAt > 0) "Novig lines ${Format.age(boardAt, now)}" else "no Novig lines yet"
    return parts.joinToString(" · ")
}

private fun footnote(model: com.tjshea.vigilant.data.ncaab.NcaabModel?): String =
    "Averages skip games with a spread of 20+ points (a big school against a small one) and scoring outliers; a team shows after 3 usable games. − and + are the average of each game's result against its closing spread: " +
        "within 3 points a tie, 3-10 a plus or minus, 10+ double." + (model?.takeIf { it.measured }?.let { " Backtest: ${it.note}." } ?: "")

@Composable
private fun Hint(text: String, tag: String = "ncaabHint") {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp).testTag(tag))
}

// ---- the evidence ------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun EvidenceCard(model: com.tjshea.vigilant.data.ncaab.NcaabModel, open: Boolean, onToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag("ncaabEvidence")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("How this method has done ${if (open) "▴" else "▾"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(NcaabText.verdict(model), style = MaterialTheme.typography.bodySmall)
            if (open) {
                Text(NcaabText.evidenceHeadline(model), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NcaabText.evidence(model).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Text("Share of games the named side beat the closing line. Each bucket's own record is under the lean it applies to.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- the pages -----------------------------------------------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.leans(views: List<NcaabGameView>, onOpen: (String) -> Unit) {
    val picks = views.filter { it.bettable }.flatMap { v -> v.picks.map { v to it } }
        .sortedWith(compareByDescending<Pair<NcaabGameView, NcaabPick>> { it.second.reading.backed }.thenByDescending { NcaabSignals.score(it.second.reading) })
    if (picks.isEmpty()) {
        item { Hint("No game has a lean yet: a team needs 3 usable games before its averages count, and the game needs a Novig line.", "ncaabNoLeans") }
        return
    }
    val backed = picks.count { it.second.reading.backed }
    item {
        Text(
            if (backed == 0) "None of today's leans is backed by history. They are listed strongest first because you asked for your method; each shows what that kind of lean has done."
            else "$backed lean${if (backed == 1) " is" else "s are"} backed by history; the rest are your method's opinions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("ncaabLeanNote"),
        )
    }
    items(picks.take(40), key = { (v, p) -> v.id + p.reading.id }) { (v, p) -> LeanRow(v, p, onOpen = { onOpen(v.id) }) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.unlike(views: List<NcaabGameView>, onOpen: (String) -> Unit) {
    fun byId(id: String) = views.mapNotNull { v -> v.picks.firstOrNull { it.reading.id == id }?.let { v to it } }.sortedByDescending { kotlin.math.abs(it.second.reading.value) }.take(8)
    val spreads = byId(NcaabSignals.AVGS_LINE)
    val totals = byId(NcaabSignals.TOTAL_GAP)
    if (spreads.isEmpty() && totals.isEmpty()) {
        item { Hint("Nothing to compare yet: a game needs both teams rated (3 usable games each) and a spread or total.", "ncaabNoUnlike") }
        return
    }
    if (spreads.isNotEmpty()) {
        item { Text("Spreads furthest from the averages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(spreads, key = { (v, _) -> "s" + v.id }) { (v, p) -> UnlikeRow(v, p, spread = true, onOpen = { onOpen(v.id) }) }
    }
    if (totals.isNotEmpty()) {
        item { Text("Totals furthest from the teams' scoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(totals, key = { (v, _) -> "t" + v.id }) { (v, p) -> UnlikeRow(v, p, spread = false, onOpen = { onOpen(v.id) }) }
    }
}

@Composable
private fun UnlikeRow(v: NcaabGameView, p: NcaabPick, spread: Boolean, onOpen: () -> Unit) {
    val r = p.reading
    val line = if (spread) {
        val implied = r.value - (v.homeSpread ?: 0.0)   // what the averages make the home side's winning margin
        "${v.game.h.n} ${NcaabText.signed(v.homeSpread ?: 0.0)}: the averages say ${v.game.h.n} by ${NcaabText.number(kotlin.math.abs(implied))}${if (implied < 0) " the other way" else ""}, ${NcaabText.number(kotlin.math.abs(r.value))} pts from the line"
    } else {
        val est = r.value + (v.total ?: 0.0)
        "Total ${NcaabText.number(v.total ?: 0.0)}: the teams' scoring says ${NcaabText.number(est)}, ${NcaabText.number(kotlin.math.abs(r.value))} pts ${if (r.value > 0) "above" else "below"}"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${v.game.a.n} @ ${v.game.h.n} · ${Format.startTime(v.game.t)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(line, style = MaterialTheme.typography.bodyMedium)
            Text("When it is this far off: ${NcaabText.pct(if (r.fade) 1 - r.bucket.r else r.bucket.r)} for the side it points to, ${"%,d".format(r.n)} games", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LeanRow(v: NcaabGameView, p: NcaabPick, onOpen: () -> Unit) {
    val r = p.reading
    val edge = LocalEdgeColors.current
    val api = LocalApiBet.current?.takeIf { it.enabled }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (r.backed) edge.positiveContainer else MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("ncaabLean-${v.id}-${r.id}"),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${v.game.a.n} @ ${v.game.h.n} · ${Format.startTime(v.game.t)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.text + (p.cost?.let { "  ${NcaabText.cents(it)}" } ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(r.label + (if (r.fade) " — fading" else ""), style = MaterialTheme.typography.labelMedium)
                Text(
                    "History: ${NcaabText.pct(r.rate)} of ${"%,d".format(r.n)} games" + (r.rateCurrent?.takeIf { r.nCurrent >= 20 }?.let { " · this season ${NcaabText.pct(it)} (${r.nCurrent})" } ?: "") +
                        (p.systemEv?.let { " · ${Format.evPercentShort(it)} at this price" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = if (r.backed) edge.positive else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val o = p.opportunity
            if (api != null && o != null) BetChip("Bet") { betFrom(api, o, r.rate) }
        }
    }
}

// ---- a game --------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun GameCard(v: NcaabGameView, onOpen: () -> Unit, now: Long) {
    val edge = LocalEdgeColors.current
    val api = LocalApiBet.current?.takeIf { it.enabled }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("ncaabGame-${v.id}")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when { v.live -> "LIVE" ; v.final -> "FINAL" ; else -> Format.startTime(v.game.t) } + (if (v.game.neu == 1) " · neutral" else ""),
                    style = MaterialTheme.typography.labelMedium, color = if (v.live) edge.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (v.lineSource == "ESPN") Text("ESPN line (no Novig market)", style = MaterialTheme.typography.labelSmall, color = edge.warning)
            }
            // The away team first, then the home team, the way a schedule reads; each carries its own spread.
            TeamRow(v.game.a.n, v.game.a.rk, v.away, score = v.game.awayScore.takeIf { !v.bettable },
                spread = v.homeSpread?.let { NcaabText.signed(-it) }, cost = v.spreadCost(espnHome = false),
                onBet = if (api != null && v.bettable) v.spreadFor(espnHome = false)?.let { o -> { betFrom(api, o, null) } } else null)
            TeamRow(v.game.h.n, v.game.h.rk, v.home, score = v.game.hs.takeIf { !v.bettable },
                spread = v.homeSpread?.let { NcaabText.signed(it) }, cost = v.spreadCost(espnHome = true),
                onBet = if (api != null && v.bettable) v.spreadFor(espnHome = true)?.let { o -> { betFrom(api, o, null) } } else null)
            val t = v.total
            if (t != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Total ${NcaabText.number(t)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val tl = v.mainTotal
                    if (api != null && v.bettable && tl != null) {
                        BetChip("O ${tl.overCost?.let { NcaabText.cents(it) } ?: ""}".trim()) { betFrom(api, tl.over, null) }
                        BetChip("U ${tl.underCost?.let { NcaabText.cents(it) } ?: ""}".trim()) { betFrom(api, tl.under, null) }
                    }
                }
            }
            v.picks.sortedByDescending { NcaabSignals.score(it.reading) }.take(2).forEach { p ->
                Text(
                    (if (p.reading.backed) "✓ " else "• ") + "${p.reading.label}: ${p.text} · ${NcaabText.pct(p.reading.rate)} of ${"%,d".format(p.reading.n)}",
                    style = MaterialTheme.typography.labelSmall, color = if (p.reading.backed) edge.positive else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TeamRow(name: String, rank: Int?, team: NcaabTeam?, score: String?, spread: String?, cost: Double?, onBet: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (rank?.let { "#$it " } ?: "") + NcaabText.team(name, team),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        team?.let { Tiers(it.last.takeLast(5)) }
        if (score != null) Text(score, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        else if (spread != null) {
            if (onBet != null) BetChip(spread + (cost?.let { "  ${NcaabText.cents(it)}" } ?: ""), onClick = onBet)
            else Text(spread, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The team's last results as little squares: green for a plus, red for a minus, grey for a tie. */
@Composable
internal fun Tiers(last: List<Int>) {
    val edge = LocalEdgeColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        last.forEach { t ->
            val c: Color = when { t > 0 -> edge.positive; t < 0 -> edge.negative; else -> edge.muted }
            Box(Modifier.size(width = 12.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(c.copy(alpha = if (kotlin.math.abs(t) >= 2) 1f else 0.55f)))
        }
    }
}

@Composable
internal fun BetChip(label: String, tag: String = "ncaabBet", onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.testTag(tag)) { Text(label, maxLines = 1, fontSize = 13.sp) }
}

/** The Bet sheet for [o]: the books' own fair when the scan priced it, otherwise [systemFair] (a signal's chance) or Novig's middle price. */
internal fun betFrom(api: ApiBetActions, o: Opportunity, systemFair: Double?) {
    if (o.fairProbability != null) api.betOpportunity(o)
    else (systemFair ?: NcaabText.midFair(o))?.let { api.betWithFair(o, it) }
}
