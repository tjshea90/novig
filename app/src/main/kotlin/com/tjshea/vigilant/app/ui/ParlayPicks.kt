package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.tjshea.vigilant.data.reference.ParlayCompare
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
    /** Every other book's odds for a tapped pick CNO doesn't list (by pick key; TASKS.md P4, Q2), from [com.tjshea.vigilant.data.reference.OtherBooks]. */
    val books: Map<String, com.tjshea.vigilant.data.cno.CnoBooksState> = emptyMap(),
    /** Who answered for each pick's books ("ParlayAPI", "PropLine", "The Odds API"). */
    val bookSources: Map<String, List<String>> = emptyMap(),
    /** Books whose last price is older than the freshness limit: shown apart, never counted. */
    val olderBooks: Map<String, List<com.tjshea.vigilant.data.reference.OtherBooks.Line>> = emptyMap(),
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
    /** Tapping a card: its sheet, every book's odds on the bet (TASKS.md P4). */
    val onSelect: (ParlayPick) -> Unit = {},
    /** The sheet's books: CNO's game page, else ParlayAPI's ([com.tjshea.vigilant.app.MainViewModel.loadPickBooks]); true = read again. */
    val onLoadBooks: (ParlayPick, Boolean) -> Unit = { _, _ -> },
)

/** CNO's and Vigilant's EV for a pick at Novig's price now, beside ParlayAPI's (TASKS.md P2). */
data class PickReads(val cno: ParlayCompare.Read, val vigilant: ParlayCompare.Read) {
    /** Why one or both have no number, for the line under them; null when both do. */
    val why: String?
        get() = listOfNotNull(cno.why?.let { "CNO: $it" }, vigilant.why?.let { "Vigilant: $it" }).joinToString(" · ").ifEmpty { null }
}

/** [picks]' CNO and Vigilant reads by pick key, from one [ParlayCompare.Index] of CNO's list and the last scan. */
fun UiState.pickReads(picks: List<ParlayPick>, now: Long): Map<String, PickReads> {
    if (picks.isEmpty()) return emptyMap()
    val index = ParlayCompare.Index(cno.snapshot?.rows.orEmpty(), result?.opportunities.orEmpty())
    return picks.associate { p ->
        p.key to PickReads(
            cno = ParlayCompare.cno(p, index, cno.snapshot?.dataAtMs, now, settings.cnoOn),
            vigilant = ParlayCompare.vigilant(p, index, parlayPicks.vigilant, now, settings.vigilantOn, parlayPicks.vigilantReading),
        )
    }
}

/**
 * The shown picks Vigilant's own fair odds are read for after a ParlayAPI scan or recheck (TASKS.md P2): not the ones the last scan already
 * priced freshly, nor ones read in the last [com.tjshea.vigilant.data.scanner.Freshness.MAX_REUSE_MS] (a recheck right after a scan asks the
 * fair-odds sources nothing new for them: credits are a real budget).
 */
fun UiState.vigilantAsks(now: Long): List<ParlayPick> {
    val index = ParlayCompare.Index(emptyList(), result?.opportunities.orEmpty())
    return parlayShown(now).filter { p ->
        val scanned = index.opportunityFor(p)?.takeIf { com.tjshea.vigilant.data.scanner.Freshness.fresh(it.fairAsOfMs, now, p.row.startsAtMs) }
        val read = parlayPicks.vigilant[p.key]?.takeIf { r -> r.fair != null && r.atMs?.let { now - it < com.tjshea.vigilant.data.scanner.Freshness.MAX_REUSE_MS } == true }
        scanned == null && read == null
    }
}

/** CNO's list row for the same bet as [p], when CNO is on and its game page can be read (TASKS.md P4). */
fun pickCnoRow(state: UiState, p: ParlayPick): CnoRow? =
    if (!state.settings.cnoOn) null
    else ParlayCompare.Index(state.cno.snapshot?.rows.orEmpty(), emptyList()).cnoRowFor(p)?.takeIf { it.gameUrl != null }

/**
 * A pick's books as its sheet shows them: CNO's game page once read, else every other book's from the odds sources; [fromCno] says which,
 * [sources] who answered, [older] the books whose last price is too old to count.
 */
data class PickBooks(
    val books: com.tjshea.vigilant.data.cno.CnoBooksState?,
    val fromCno: Boolean,
    val sources: List<String> = emptyList(),
    val older: List<com.tjshea.vigilant.data.reference.OtherBooks.Line> = emptyList(),
) {
    /** Where the books came from, for the sheet ("CNO's game page", "ParlayAPI + PropLine"). */
    val sourceText: String
        get() = if (fromCno) "CNO's game page" else sources.joinToString(" + ").ifEmpty { "the odds sources" }
}

fun UiState.pickBooks(p: ParlayPick): PickBooks {
    val cnoRow = pickCnoRow(this, p)
    val fromCno = cnoRow?.let { books[it.key] }
    val other = parlayPicks.books[p.key]
    val sources = parlayPicks.bookSources[p.key].orEmpty()
    val older = parlayPicks.olderBooks[p.key].orEmpty()
    return when {
        fromCno?.view != null -> PickBooks(fromCno, true)
        other != null -> PickBooks(other, false, sources, older)
        fromCno != null -> PickBooks(fromCno, true)
        else -> PickBooks(null, cnoRow != null)
    }
}

/** [view] with Novig's price now as its judged row (CNO's page has one; the other sources' Novig prices are left out as older). */
fun withNovigNow(view: com.tjshea.vigilant.data.cno.CnoBooksView, odds: Int, available: Double?): com.tjshea.vigilant.data.cno.CnoBooksView =
    view.copy(prices = view.prices.filter { it.code != com.tjshea.vigilant.data.cno.CnoBooks.NOVIG } + com.tjshea.vigilant.data.cno.CnoBookPrice(com.tjshea.vigilant.data.cno.CnoBooks.NOVIG, odds, available, null, null))

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

/**
 * One ParlayAPI pick at Novig's price now: its EV there against ParlayAPI's fair price, with CNO's and Vigilant's own beside it ([reads], TASKS.md
 * P2), and what ParlayAPI had listed. A tap opens its sheet with every book's odds (P4); Bet places it through Novig's API (P1).
 */
@Composable
fun ParlayPickCard(
    p: ParlayPick,
    settings: ScanSettings,
    now: Long,
    modifier: Modifier = Modifier,
    injury: com.tjshea.vigilant.data.reference.Injury? = null,
    actions: ParlayPickActions = ParlayPickActions(),
    reads: PickReads? = null,
) {
    val row = p.row
    val play = p.play
    val league = Leagues.byNovigName(row.league)
    Card(
        modifier.fillMaxWidth().testTag("parlayPick").clickable { actions.onSelect(p) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("ParlayAPI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        EvBadge(p.ev ?: 0.0)
                    }
                    // CNO's and Vigilant's EV at the same price, beside ParlayAPI's (Tj: "so I can compare and see if it is truly positive EV").
                    reads?.let {
                        Spacer(Modifier.width(10.dp))
                        ScannerEv("CNO", it.cno, Modifier.padding(bottom = 3.dp))
                        Spacer(Modifier.width(10.dp))
                        ScannerEv("Vigilant", it.vigilant, Modifier.padding(bottom = 3.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { actions.onPlaced(parlayItem(p)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "I placed ${row.bet}: hide it", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.onHide(parlayItem(p)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${row.bet} from the list", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                listOfNotNull(league?.let { "${it.emoji} ${it.displayName}" }, row.startsAtMs?.let { Format.startTime(it) }, "ParlayAPI's pick").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.bet, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        injury?.let { Spacer(Modifier.width(6.dp)); InjuryTag(it) }
                    }
                    Text("${row.market} · ${row.event}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    GameBetsChip(row.event, row.startsAtMs, row.league, Modifier.padding(top = 3.dp))
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
            reads?.why?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                // Betting through Novig's API, as on CNO's cards (TASKS.md P1): only when it's set up.
                ApiBetButton { it.betParlay(p) }
                Spacer(Modifier.width(6.dp))
                OpenInBookButton("", opening = actions.opening == row.key, onClick = { actions.onOpen(row) })
            }
        }
    }
}

/** "CNO +2.31%" beside ParlayAPI's badge, or "CNO —" when it has no line (the card says why underneath). */
@Composable
fun ScannerEv(label: String, read: ParlayCompare.Read, modifier: Modifier = Modifier) {
    val ev = read.ev
    val edge = Edge.colors
    Column(modifier.testTag("pickEv-$label"), horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            ev?.let { Format.evPercent(it) } ?: "—",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = when {
                ev == null -> MaterialTheme.colorScheme.onSurfaceVariant
                ev >= 0 -> edge.positive
                else -> edge.negative
            },
        )
    }
}

/** A tapped pick's sheet (TASKS.md P4): [ParlayPickDetail] in a bottom sheet; its books are read when it opens. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ParlayPickSheet(
    p: ParlayPick,
    state: UiState,
    reads: PickReads?,
    actions: ParlayPickActions,
    onDismiss: () -> Unit,
) {
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = rememberNow(15_000)
    val onLoadBooks by androidx.compose.runtime.rememberUpdatedState(actions.onLoadBooks)
    val latest by androidx.compose.runtime.rememberUpdatedState(p)
    androidx.compose.runtime.LaunchedEffect(p.key) { onLoadBooks(latest, false) }
    // Which books show (CNO's page or ParlayAPI's): worked out when they change, not on every redraw.
    val books = androidx.compose.runtime.remember(p.key, state.cno.snapshot, state.books, state.parlayPicks.books, state.settings.scanner) { state.pickBooks(p) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        ParlayPickDetail(
            p, state.settings, now, reads, books,
            injury = state.injuries[p.key],
            opening = actions.opening == p.row.key,
            onOpen = { actions.onOpen(p.row) },
            onPlaced = {
                actions.onPlaced(parlayItem(p))
                onDismiss()
            },
            onReloadBooks = { actions.onLoadBooks(p, true) },
        )
    }
}

/**
 * A ParlayAPI pick in full, as CNO's sheet shows its bets (TASKS.md P4, Tj 2026-09-30: "shows other sports books odds on the same bet, exactly
 * how other sections of this app such as cno scanner do it"): the three EVs at Novig's price now, every book's odds for the bet and its other
 * side with Vigilant's worst-case verdict on them (CNO's game page when CNO lists the bet, else ParlayAPI's own books), and Bet / Open / placed.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ParlayPickDetail(
    p: ParlayPick,
    settings: ScanSettings,
    now: Long,
    reads: PickReads?,
    books: PickBooks,
    injury: com.tjshea.vigilant.data.reference.Injury? = null,
    opening: Boolean = false,
    onOpen: () -> Unit = {},
    onPlaced: (() -> Unit)? = null,
    onReloadBooks: () -> Unit = {},
) {
    val row = p.row
    val play = p.play
    val live = row.startsAtMs?.let { it <= now } == true
    val state = books.books
    // Every book but CNO's page gets Novig's price now as its judged row, as CNO's page has one.
    val view = state?.view?.let { if (books.fromCno) it else withNovigNow(it, row.odds, p.available) }
    val source = books.sourceText
    val check = view?.let { com.tjshea.vigilant.data.cno.CnoBooks.check(it, row, live, preferListOdds = (p.novigAtMs ?: 0L) > it.fetchedAtMs) }
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()).testTag("parlayPickSheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvBadge(p.ev ?: 0.0, large = true, low = (p.ev ?: 0.0) < settings.minEvPercent - 1e-9)
            Spacer(Modifier.width(12.dp))
            Text(
                "ParlayAPI's pick · ${com.tjshea.vigilant.app.AppBook.name}" + if (live) " · LIVE (fee included)" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            Text(row.bet, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${row.market} · ${row.event}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.startsAtMs?.let { Text("${row.league} · ${Format.startTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            injury?.let { InjuryLine(it, Modifier.padding(top = 6.dp)) }
        }
        // ---- the three scanners' EV at the same Novig price (TASKS.md P2) ----
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("EV at ${com.tjshea.vigilant.app.AppBook.name}'s price now", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ScannerEv("ParlayAPI", ParlayCompare.Read(p.ev, row.fairProbability, null, null))
                reads?.let {
                    ScannerEv("CNO", it.cno)
                    ScannerEv("Vigilant", it.vigilant)
                }
                check?.ev?.let { ScannerEv("Books", ParlayCompare.Read(it, check.fairProbability, view.fetchedAtMs, null)) }
            }
            reads?.why?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Wraps on a phone: five values don't fit one line at 393 dp.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabeledValue("${com.tjshea.vigilant.app.AppBook.name} now", MiniWindow.american(row.odds))
            if (play.listedAmerican != row.odds) LabeledValue("ParlayAPI had", MiniWindow.american(play.listedAmerican), valueColor = Edge.colors.warning)
            play.fairAmerican?.let { LabeledValue("ParlayAPI fair", MiniWindow.american(it)) }
            p.available?.let { LabeledValue("Available", Format.money(it)) }
            cnoStake(CnoPick(row, p.ev ?: 0.0, live), settings)?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
        }

        // ---- every book's odds, and Vigilant's own check on them, as CNO's sheet shows them ----
        when {
            state == null || (state.loading && view == null) -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Reading every book's odds from ${if (books.fromCno) "CNO" else "the odds sources"}…", style = MaterialTheme.typography.bodySmall)
            }
            view == null -> Banner(state.error ?: "No book list is available for this bet.", action = "Retry", onAction = onReloadBooks)
            check != null -> VerdictCard(check, row, view.otherBet, lister = "ParlayAPI", page = source)
        }
        if (view != null) {
            BookTable(view.prices, view.otherBet, com.tjshea.vigilant.data.cno.CnoBooks.NOVIG)
            Text(
                "Books read ${Format.age(view.fetchedAtMs, now)} from $source" + (state.error?.let { " · re-read failed: $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OlderBooks(books.older, now)
        Text(
            "ParlayAPI's EV is its fair price against ${com.tjshea.vigilant.app.AppBook.name}'s order book now" +
                (p.novigAtMs?.let { " (read ${Format.age(it, now)})" } ?: "") +
                "; CNO's and Vigilant's are their own fair lines at that same price" +
                (if (check?.ev != null) ", and Books is Vigilant's worst case of the books above." else ".") +
                if (com.tjshea.vigilant.app.AppBook.exchange) " Live bets pay Novig's taker fee; the EV here already takes it out." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ApiBetButton { it.betParlay(p) }
            androidx.compose.material3.Button(onClick = onOpen, enabled = !opening, modifier = Modifier.testTag("openInSheet")) {
                if (opening) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Opening…")
                } else {
                    Text("Open in ${com.tjshea.vigilant.app.AppBook.name}")
                }
            }
            if (onPlaced != null) OutlinedButton(onClick = onPlaced) { Text("I placed it") }
        }
        if (view != null) TextButton(onClick = onReloadBooks, enabled = !state.loading) { Text(if (state.loading) "Reading…" else "Re-read books") }
        // ParlayAPI's call on this bet at the price shown, only on a tap (5 credits; PARLAY_API.md §6.4).
        SecondOpinionFor(p.key, androidx.compose.runtime.remember(row.key, row.odds) { com.tjshea.vigilant.data.reference.VerdictQueries.of(row) })
    }
}

/**
 * Books whose last price is older than the freshness limit (Tj, 2026-09-30: always see the other books): listed with their age, never counted
 * in the check above, so an old price can't make a bet look +EV.
 */
@Composable
fun OlderBooks(older: List<com.tjshea.vigilant.data.reference.OtherBooks.Line>, now: Long) {
    if (older.isEmpty()) return
    Column(Modifier.testTag("olderBooks"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Older prices (not counted)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        older.forEach { l ->
            Text(
                com.tjshea.vigilant.data.cno.CnoBooks.name(l.code) + "  " + (l.odds?.let { MiniWindow.american(it) } ?: "—") +
                    (l.otherOdds?.let { " / " + MiniWindow.american(it) } ?: "") + (l.seenAtMs?.let { " · ${Format.age(it, now)}" } ?: "") + " · ${l.source}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
