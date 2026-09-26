package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tjshea.vigilant.app.FloatingWidget
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.MiniWindow
import com.tjshea.vigilant.app.R
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoDevig
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoScreened
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.Fees
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.Odds

/**
 * The CNO scanner (Tj, 2026-09-26; RESEARCH.md §18–19): CrazyNinjaOdds' +EV list for Tj's view,
 * read with the scanner's filters (worst-case devig, odds cap, fewest books), passed through the
 * app's own checks ([CnoChecks]), and kept current while this app or its mini window is on screen.
 * Tapping a bet shows every book's odds for it and Vigilant's own worst-case check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnoScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Shrink to the mini window over other apps. Null hides the button (no picture-in-picture). */
    onMiniWindow: (() -> Unit)? = null,
    /** Load every book's odds for a bet (force = re-read now). */
    onLoadBooks: (CnoRow, Boolean) -> Unit = { _, _ -> },
    /** Open the bet's game in Novig's app. */
    onOpenInNovig: (CnoRow) -> Unit = {},
    /** Switch scanners (the "CNO only" chip). */
    onScanner: (ScannerMode) -> Unit = {},
    /** "I placed it": hidden here and in the widget from now on. */
    onPlaced: (MiniWindow.Item) -> Unit = {},
    /** Undo, or "not placed after all". */
    onUnplace: (String) -> Unit = {},
) {
    val now = rememberNow(5_000)
    val cno = state.cno
    val on = state.settings.cnoOn
    // A list read for another link (Tj just changed it) isn't shown as if it were this one's.
    val snap = cno.snapshot?.takeIf { it.url == state.cnoUrl }
    val screened = state.cnoPicks(now)
    // Bets Tj placed are gone from the list (and the widget) until their game is over.
    val picks = screened?.picks?.filter { MiniWindow.cnoKey(it.row) !in state.placedKeys }.orEmpty()
    val placedHere = state.placed.filter { it.key.startsWith("cno:") }
    var showPlaced by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CnoPick?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val markPlaced: (CnoPick) -> Unit = { pick ->
        if (snap != null) {
            val item = MiniWindow.itemFor(pick, snap, state, now)
            onPlaced(item)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar("Placed: ${pick.row.bet}. Hidden here and in the widget.", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) onUnplace(item.key)
            }
        }
    }
    // Android's "Display over other apps", re-checked each time Tj comes back from its settings.
    val context = LocalContext.current
    var overlayAllowed by remember { mutableStateOf(FloatingWidget.allowed(context)) }
    LifecycleResumeEffect(Unit) {
        overlayAllowed = FloatingWidget.allowed(context)
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CrazyNinjaOdds", fontWeight = FontWeight.Bold)
                        Text(
                            cnoStatus(state, snap, now),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (cno.error != null || (snap != null && CnoChecks.stuck(snap, now))) Edge.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (onMiniWindow != null) {
                        IconButton(onClick = onMiniWindow) {
                            Icon(painterResource(R.drawable.ic_mini_window), contentDescription = "Mini window over Novig", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (cno.refreshing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh, enabled = on) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh CrazyNinjaOdds", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "summary") {
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.settings.scanner == ScannerMode.CNO,
                                onClick = { onScanner(if (state.settings.scanner == ScannerMode.CNO) ScannerMode.BOTH else ScannerMode.CNO) },
                                label = { Text("CNO only") },
                            )
                            Text(
                                cnoFiltersLabel(state.settings.cnoFilters),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Filters", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text(
                            "View: " + CnoView.describe(state.cnoUrl),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (snap != null && CnoChecks.stuck(snap, now)) {
                            Banner("CrazyNinjaOdds hasn't updated its odds in ${Format.age(snap.dataAtMs, now).removeSuffix(" ago")} (it may be down). These prices are likely gone.")
                        }
                        cno.error?.let { Banner(if (snap != null) "$it. Showing the list from ${Format.age(snap.fetchedAtMs, now)}." else it, color = Edge.colors.negative) }
                        snap?.note?.let { Banner("CrazyNinjaOdds says: $it") }
                        if (on && state.settings.floatingWidget && !overlayAllowed) {
                            Banner(
                                "The floating widget (scroll buttons, tap a bet to open it in Novig, mark bets placed) needs " +
                                    "Android's \"Display over other apps\" for Vigilant. Until then the widget is picture-in-picture. " +
                                    "If Android greys the switch out: App info › ⋮ › Allow restricted settings.",
                                action = "Allow",
                                onAction = { runCatching { context.startActivity(FloatingWidget.permissionIntent(context)) } },
                            )
                        }
                        if (snap != null && snap.evLabel != null && snap.filters != null && snap.evLabel != snap.filters!!.devig.label) {
                            Banner("CrazyNinjaOdds used ${evMethodName(snap.evLabel!!)} instead of ${snap.filters!!.devig.displayName.lowercase()} worst case.")
                        }
                        when {
                            !on -> EmptyState(
                                "CrazyNinjaOdds is off",
                                "Vigilant only is picked. Choose Both or CNO only in Settings to see CNO's +EV list here and in the mini window.",
                                action = "Settings",
                                onAction = onOpenSettings,
                            )
                            snap == null && cno.error == null -> EmptyState(
                                "Reading CrazyNinjaOdds…",
                                "Its +EV list for your view appears here in a few seconds, and stays current while Vigilant " +
                                    "or its mini window is on screen.",
                            )
                            screened != null && picks.isEmpty() -> EmptyState(
                                "No +EV bets pass right now",
                                "CNO listed ${snap?.rows?.size ?: 0} for your view" + hiddenText(screened).let { if (it.isEmpty()) "" else "; $it" } +
                                    (if (screened.picks.size > picks.size) "; ${screened.picks.size - picks.size} you placed" else "") +
                                    ". It's read again ${refreshLabel(state.settings)}.",
                            )
                            screened != null -> Text(
                                "${picks.size} bet${if (picks.size == 1) "" else "s"} pass" +
                                    hiddenText(screened).let { if (it.isEmpty()) "" else " · $it" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (on && placedHere.isNotEmpty()) {
                            TextButton(onClick = { showPlaced = !showPlaced }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(
                                    (if (showPlaced) "Hide" else "Show") + " the ${placedHere.size} bet${if (placedHere.size == 1) "" else "s"} you placed",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            if (showPlaced) {
                                placedHere.forEach { p ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("✓ ${p.title}" + (p.odds.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(p.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        TextButton(onClick = { onUnplace(p.key) }) { Text("Not placed") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (on && snap != null && screened != null) {
                    items(picks, key = { it.row.key }) { pick ->
                        CnoCard(
                            pick, snap, state.settings, state.books[pick.row.key], now,
                            team = state.teams[pick.row.key],
                            placedOther = state.placedFamilies[com.tjshea.vigilant.data.match.Picks.familyKey(pick.row.event, pick.row.market, pick.row.bet)]?.title,
                            modifier = Modifier.padding(horizontal = 12.dp).animateItem(),
                            onPlaced = { markPlaced(pick) },
                        ) { selected = pick }
                    }
                    item(key = "credit") {
                        Text(
                            "From crazyninjaodds.com (free; donations keep it running). EV is CNO's " +
                                (snap.evLabel?.let { "(${evMethodName(it)}) " } ?: "") +
                                "as of its last update, less Novig's fee on live games. Tap a bet for every book's odds " +
                                "and Vigilant's own check. Novig's price may have moved: check it in Novig before betting.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }

    selected?.let { pick ->
        // The freshest copy of the tapped bet: a refresh may have re-priced it since the tap.
        val live = screened?.picks?.firstOrNull { it.row.key == pick.row.key } ?: pick
        CnoSheet(
            live, snap, state.settings, state.cnoUrl, state.books[live.row.key],
            onLoadBooks = onLoadBooks,
            onOpenInNovig = onOpenInNovig,
            onDismiss = { selected = null },
            team = state.teams[live.row.key],
            onPlaced = {
                markPlaced(live)
                selected = null
            },
        )
    }
}

/** "Odds 20s old · every 15 s", "Reading…", or the error. */
private fun cnoStatus(state: UiState, snap: CnoSnapshot?, now: Long): String = when {
    !state.settings.cnoOn -> "Off"
    state.cno.refreshing && snap == null -> "Reading…"
    state.cno.error != null && snap == null -> "Couldn't read it"
    snap == null -> "Not read yet"
    else -> "Odds ${Format.age(snap.dataAtMs, now).removeSuffix(" ago")} old · " +
        (if (state.cno.refreshing) "refreshing…" else refreshLabel(state.settings)) +
        if (state.cno.error != null) " · last read failed" else ""
}

fun refreshLabel(s: ScanSettings): String = when {
    s.cnoRefreshSeconds == CnoFeed.REALTIME -> "in real time"
    s.cnoRefreshSeconds <= 0 -> "when you tap Refresh"
    else -> "every ${secondsLabel(s.cnoRefreshSeconds)}"
}

fun secondsLabel(seconds: Int): String = when {
    seconds == CnoFeed.REALTIME -> "Real time"
    seconds <= 0 -> "Tap only"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "$seconds s"
}

/** "Conservative worst case · to +150 · 5+ books · ≥1% EV". */
fun cnoFiltersLabel(f: CnoFilters): String = listOfNotNull(
    "${f.devig.displayName} worst case",
    if (f.maxOdds > 0) "to +${f.maxOdds}" else "any odds",
    "${f.minBooks}+ books",
    if (f.minEv > 0) "≥${Format.percent(f.minEv, 0)} EV" else null,
).joinToString(" · ")

/** "3 hidden: 2 too few books, 1 longer odds than your cap". */
fun hiddenText(s: CnoScreened): String =
    if (s.hiddenCount == 0) "" else "${s.hiddenCount} hidden: " + s.hidden.entries.joinToString(", ") { (reason, n) -> "$n ${reason.text}" }

/** CNO's EV column label, in words ("LW-WC" → liquidity-weighted, worst case). */
fun evMethodName(label: String): String {
    CnoDevig.entries.firstOrNull { it.label.equals(label, true) }?.let { return "${it.displayName.lowercase()}, worst case" }
    val parts = label.uppercase().split('-')
    val weight = when (parts.getOrNull(0)) {
        "LW" -> "liquidity-weighted"
        "UW", "UMC" -> "unweighted"
        "C" -> "conservative"
        else -> parts.getOrNull(0)?.lowercase()
    }
    val method = when (parts.getOrNull(1)) {
        "WC" -> "worst case"
        "M" -> "multiplicative"
        "A", "AS" -> "additive/Shin"
        "P" -> "power"
        else -> parts.getOrNull(1)?.lowercase()
    }
    return listOfNotNull(weight, method).joinToString(", ").ifEmpty { label }
}

/** "✓ Dalton Schultz (HOU) Over 5.5": the check when books agree, the team small and dim after the name. */
@Composable
fun pickText(bet: String, team: String?, agrees: Boolean): androidx.compose.ui.text.AnnotatedString {
    val (name, line) = MiniWindow.splitPick(bet)
    val positive = Edge.colors.positive
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    return androidx.compose.ui.text.buildAnnotatedString {
        if (agrees) withStyle(androidx.compose.ui.text.SpanStyle(color = positive)) { append("✓ ") }
        append(if (line == null) bet else name)
        if (team != null && name.isNotEmpty()) {
            withStyle(androidx.compose.ui.text.SpanStyle(color = dim, fontSize = MaterialTheme.typography.labelMedium.fontSize, fontWeight = FontWeight.SemiBold)) { append(" ($team)") }
        }
        if (line != null) append((if (name.isNotEmpty()) " " else "") + line)
    }
}

private fun sportEmoji(sport: String): String = when (sport.lowercase()) {
    "football" -> "🏈"
    "baseball" -> "⚾"
    "basketball" -> "🏀"
    "hockey" -> "🏒"
    "soccer" -> "⚽"
    else -> "🎯"
}

/** The Kelly stake for a CNO bet: CNO's fair probability against the price (and fee, live), capped at what's available. */
fun cnoStake(pick: CnoPick, s: ScanSettings): Double? {
    val row = pick.row
    val fair = CnoChecks.fairProbability(row) ?: return null
    val price = 1.0 / Odds.americanToDecimal(row.odds)
    // Pregame Novig game markets charge the taker nothing (NOVIG_API.md §8); live ones do.
    val fee = Fees.takerFee(price, MarketFee.GAME, eventLive = pick.live)
    val stake = EvMath.suggestedStake(EvQuote(fair, price, fee), s.bankroll, s.kellyMultiplier, row.available)
    return stake.takeIf { it > 0 }
}

/** Vigilant's verdict in a few words, and its color. */
@Composable
fun verdictLabel(check: CnoBooks.Check): Pair<String, Color> = when (check.verdict) {
    CnoBooks.Verdict.CONFIRMED -> "✓ ${check.agreeing} of ${check.twoSided} books agree" to Edge.colors.positive
    CnoBooks.Verdict.SPLIT -> "Books split: ${check.agreeing} of ${check.twoSided} say +EV" to Edge.colors.warning
    CnoBooks.Verdict.THIN -> "Thin: ${check.twoSided} book${if (check.twoSided == 1) "" else "s"} both sides" to Edge.colors.warning
    CnoBooks.Verdict.NOT_CONFIRMED -> "✗ books say ${Format.evPercent(check.ev ?: 0.0)}" to Edge.colors.negative
    CnoBooks.Verdict.NO_DATA -> "No book prices both sides" to Edge.colors.warning
}

@Composable
private fun CnoCard(
    pick: CnoPick,
    snap: CnoSnapshot,
    settings: ScanSettings,
    books: CnoBooksState?,
    now: Long,
    modifier: Modifier = Modifier,
    team: String? = null,
    /** Tj placed this bet at another line (that bet's name). */
    placedOther: String? = null,
    onPlaced: () -> Unit = {},
    onClick: () -> Unit,
) {
    val row = pick.row
    val old = now - snap.dataAtMs > MiniWindow.CNO_OLD_MS
    val check = books?.view?.let { CnoBooks.check(it, row, pick.live, preferListOdds = snap.fetchedAtMs > it.fetchedAtMs) }
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvBadge(pick.ev)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${sportEmoji(row.sport)} ${row.league.ifEmpty { row.sport }} · " +
                        if (pick.live) "LIVE" else row.startsAtMs?.let { Format.startTime(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pick.live) Edge.colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (old) Text("old price", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pickText(row.bet, team, check?.verdict == CnoBooks.Verdict.CONFIRMED),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    placedOther?.let {
                        Text("You placed $it", style = MaterialTheme.typography.labelSmall, color = Edge.colors.warning, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "${row.market} · ${row.event}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(row.book.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(MiniWindow.american(row.odds), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                row.fairOdds?.let { f -> LabeledValue("Fair", MiniWindow.american(f) + (row.fairProbability?.let { " · ${Format.percent(it)}" } ?: "")) }
                row.available?.let { LabeledValue("Available", Format.money(it)) }
                row.books?.let { LabeledValue("Books", it.toString()) }
                cnoStake(pick, settings)?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPlaced, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = "I placed ${row.bet}: hide it", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            check?.let {
                val (text, color) = verdictLabel(it)
                Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CnoSheet(
    pick: CnoPick,
    snap: CnoSnapshot?,
    settings: ScanSettings,
    viewUrl: String,
    books: CnoBooksState?,
    onLoadBooks: (CnoRow, Boolean) -> Unit,
    onOpenInNovig: (CnoRow) -> Unit,
    onDismiss: () -> Unit,
    team: String? = null,
    onPlaced: () -> Unit = {},
) {
    val row = pick.row
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uri = LocalUriHandler.current
    val now = rememberNow(15_000)
    LaunchedEffect(row.key) { onLoadBooks(row, false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        CnoDetail(
            pick, snap, settings, viewUrl, books, now,
            onReloadBooks = { onLoadBooks(row, true) },
            onOpenInNovig = { onOpenInNovig(row) },
            onOpenCno = { row.gameUrl?.let { runCatching { uri.openUri(it) } } },
            team = team,
            onPlaced = onPlaced,
        )
    }
}

/** A CNO bet in full: prices, Vigilant's check, every book's odds, and the ways to act on it. */
@Composable
fun CnoDetail(
    pick: CnoPick,
    snap: CnoSnapshot?,
    settings: ScanSettings,
    viewUrl: String,
    books: CnoBooksState?,
    now: Long,
    onReloadBooks: () -> Unit = {},
    onOpenInNovig: () -> Unit = {},
    onOpenCno: () -> Unit = {},
    team: String? = null,
    /** Null hides "I placed it" (the widget's Books view has its own ✓). */
    onPlaced: (() -> Unit)? = null,
) {
    val row = pick.row
    val view = books?.view
    val check = view?.let { CnoBooks.check(it, row, pick.live) }
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvBadge(pick.ev, large = true)
            Spacer(Modifier.width(12.dp))
            Text(
                "CrazyNinjaOdds · ${row.book}" + if (pick.live) " · LIVE (fee included)" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            Text(pickText(row.bet, team, agrees = false), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${row.market} · ${row.event}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.startsAtMs?.let { Text("${row.league.ifEmpty { row.sport }} · ${Format.startTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LabeledValue(row.book, MiniWindow.american(row.odds))
            row.fairOdds?.let { LabeledValue("CNO fair", MiniWindow.american(it)) }
            row.available?.let { LabeledValue("Available", Format.money(it)) }
            cnoStake(pick, settings)?.let { LabeledValue(Format.kellyLabel(settings.kellyMultiplier), Format.money(it), valueColor = Edge.colors.positive) }
        }

        // ---- Vigilant's own check, from every book that prices both sides ----
        when {
            books == null || (books.loading && view == null) -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Reading every book's odds from CNO…", style = MaterialTheme.typography.bodySmall)
            }
            view == null -> Banner(books.error ?: "CNO's book list isn't available for this bet.", action = "Retry", onAction = onReloadBooks)
            check != null -> VerdictCard(check, row, view.otherBet)
        }
        if (view != null) {
            BookTable(view.prices, view.otherBet, CnoBooks.codeFor(row.book) ?: CnoBooks.NOVIG)
            Text(
                "Books read ${Format.age(view.fetchedAtMs, now)}" + (books.error?.let { " · re-read failed: $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "CNO's numbers" + (snap?.let { " as of ${Format.age(it.dataAtMs, now)}" } ?: "") +
                (snap?.evLabel?.let { ", ${evMethodName(it)} devig" } ?: "") +
                ". The price and the dollars available were what Novig showed then: check them in Novig before betting." +
                if (CnoView.includesLive(viewUrl) || pick.live) " Live bets pay Novig's taker fee; the EV here already takes it out." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenInNovig) { Text("Open in Novig") }
            if (onPlaced != null) OutlinedButton(onClick = onPlaced) { Text("I placed it") }
            if (row.gameUrl != null) OutlinedButton(onClick = onOpenCno) { Text("CNO page") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view != null) TextButton(onClick = onReloadBooks, enabled = !books.loading) { Text(if (books.loading) "Reading…" else "Re-read books") }
        }
        Text(
            "Open in Novig puts this bet in Novig's bet slip. \"I placed it\" hides it here and in the widget until the game is over.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VerdictCard(check: CnoBooks.Check, row: CnoRow, otherBet: String?) {
    val (title, color) = verdictLabel(check)
    val body = when (check.verdict) {
        CnoBooks.Verdict.CONFIRMED ->
            "${check.twoSided} books price both sides. Devigged worst case and taking the lower of their average and median, " +
                "the fair price is ${Format.american(check.fairProbability!!)} (${Format.percent(check.fairProbability!!)}), " +
                "so ${MiniWindow.american(check.novigOdds)} on Novig is ${Format.evPercent(check.ev!!)} EV; " +
                "${check.agreeing} of them say +EV on their own (the widget's green ✓)."
        CnoBooks.Verdict.SPLIT ->
            "${check.twoSided} books price both sides and together (worst case) they make ${MiniWindow.american(check.novigOdds)} on Novig " +
                "${Format.evPercent(check.ev!!)} EV, but only ${check.agreeing} of them say so on their own. " +
                "The edge rests on one or two books: no green ✓."
        CnoBooks.Verdict.THIN ->
            "Only ${check.twoSided} book${if (check.twoSided == 1) "" else "s"} (Novig aside) price both sides" +
                (if (check.oneSided > 0) "; ${check.oneSided} list only one" else "") +
                ". That's a thin market to judge a fair price from" +
                (check.ev?.let { ": by them it's ${Format.evPercent(it)} EV." } ?: ".")
        CnoBooks.Verdict.NOT_CONFIRMED ->
            "${check.twoSided} books price both sides, and by them (worst case) the fair price is " +
                "${Format.american(check.fairProbability!!)}: ${MiniWindow.american(check.novigOdds)} on Novig is ${Format.evPercent(check.ev!!)} EV. " +
                "CNO's weighting sees it differently; skip it or look closer."
        CnoBooks.Verdict.NO_DATA ->
            "No book (Novig aside) prices both " + (otherBet?.let { "this and $it" } ?: "sides") +
                ", so CNO's fair value comes from one side and a guessed margin. Treat it as unconfirmed."
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (check.novigOdds != row.odds) {
                Text(
                    "Novig's price on CNO's game page is ${MiniWindow.american(check.novigOdds)} (the list said ${MiniWindow.american(row.odds)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Edge.colors.warning,
                )
            }
        }
    }
}

/** Every book's odds for the bet and its other side, sharp books first; [judged] is the bet's own book. */
@Composable
private fun BookTable(prices: List<CnoBookPrice>, otherBet: String?, judged: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Book", Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("This bet", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (otherBet != null) "Other side" else "Other", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Fair", Modifier.weight(0.7f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        prices.forEach { p ->
            val novig = p.code == judged
            val counted = CnoBooks.usableForFair(p.code, judged) && p.twoSided
            val dim = if (counted || novig) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.fillMaxWidth()
                    .background(if (novig) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.name, Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall, fontWeight = if (novig) FontWeight.Bold else FontWeight.Normal, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(priceText(p.odds, p.available), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = dim, maxLines = 1)
                Text(priceText(p.otherOdds, p.otherAvailable), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = dim, maxLines = 1)
                Text(
                    if (counted) CnoBooks.fairFor(p.odds!!, p.otherOdds!!)?.let { Format.percent(it) } ?: "" else if (novig) "judged" else "—",
                    Modifier.weight(0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                    maxLines = 1,
                )
            }
        }
        Text(
            "Fair = that book's odds devigged worst case. Only books pricing both sides count; ${CnoBooks.name(judged)} is the price being judged.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun priceText(odds: Int?, available: Double?): String =
    if (odds == null) "—" else MiniWindow.american(odds) + (available?.let { " ${Format.money(it).substringBefore('.')}" } ?: "")
