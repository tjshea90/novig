package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.UsageViews
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.BookPropSet
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.cno.CnoDevig
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import java.util.Locale
import kotlin.math.roundToInt


/** The Settings pages, one tab each (Tj, 2026-09-29: "the settings section is getting very long... Maybe tabs on the top"): what the tab holds is named in [labelFor]'s order below. */
enum class SettingsTab(val label: String) {
    /** Pause, scanner choice, start window, background auto-scan and alerts. */
    SCAN("Scan"),

    /** The CNO scanner's filters and refresh, and the widget / mini window. */
    CNO("CNO & widget"),

    /** How fair odds are worked out, where they come from (and their keys), the sportsbooks. */
    FAIR("Fair odds"),

    /** What the +EV feed shows and how big a scan is. */
    FEED("+EV feed"),

    /** Bankroll and Kelly, the bet slip's amount, and betting through Novig's API. */
    BETTING("Betting"),

    /** Each API's usage meter and the keys backup. */
    USAGE("Usage & keys"),

    /** Diagnostics, the grading check, About. */
    TOOLS("Tools"),
    ;

    /** Vigilant's own scanner is asleep in CNO only: its pages (fair odds, feed, API usage) go with it. */
    fun shownIn(s: ScanSettings): Boolean = when (this) {
        FAIR, FEED, USAGE -> s.vigilantOn
        else -> true
    }

    /** The tab's name for these settings: without CNO on, the CNO page is the widget's alone. */
    fun labelFor(s: ScanSettings): String = if (this == CNO && !s.cnoOn) "Widget" else label

    companion object {
        /** The tabs shown for [s], in order. */
        fun shown(s: ScanSettings): List<SettingsTab> = entries.filter { it.shownIn(s) }
    }
}

private typealias SettingsUpdate = ((ScanSettings) -> ScanSettings) -> Unit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    keys: KeyActions = KeyActions(),
    onNovigConnect: (com.tjshea.vigilant.data.novig.signing.ManagementKey?) -> Unit = {},
    onNovigTest: () -> Unit = {},
    onNovigDisconnect: () -> Unit = {},
    bettingActions: BettingActions = BettingActions(),
    reportActions: ReportActions = ReportActions(),
    /** The tab open first (kept across rotation and process death once Tj has picked one). */
    startTab: SettingsTab = SettingsTab.SCAN,
) {
    val s = state.settings
    state.report?.let { ReportDialog(it, reportActions) }
    val tabs = SettingsTab.shown(s)
    // A Bet sheet's "Add money" (Tj, 2026-09-29) opens on the Betting tab, where the wallet is.
    var picked by rememberSaveable { mutableStateOf((if (state.betting.topUp != null) SettingsTab.BETTING else startTab).name) }
    // A tab that has gone (CNO only hides the fair-odds pages) falls back to the first one.
    val active = tabs.firstOrNull { it.name == picked } ?: tabs.first()
    val scroll = rememberScrollState()
    // Each tab opens at its top; the first composition (and one restored after a rotation) keeps its place.
    var shownTab by remember { mutableStateOf<SettingsTab?>(null) }
    LaunchedEffect(active) {
        if (shownTab != null && shownTab != active) scroll.scrollTo(0)
        shownTab = active
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                // Outside the scrolling page, so it stays put however far a tab is scrolled.
                ScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(active),
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("settingsTabs"),
                ) {
                    tabs.forEach { t ->
                        Tab(
                            selected = t == active,
                            onClick = { picked = t.name },
                            text = { Text(t.labelFor(s), maxLines = 1) },
                            modifier = Modifier.testTag("settingsTab-${t.name}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            when (active) {
                SettingsTab.SCAN -> ScanTab(s, onUpdate)
                SettingsTab.CNO -> CnoTab(s, onUpdate)
                SettingsTab.FAIR -> FairOddsTab(state, keys, onUpdate)
                SettingsTab.FEED -> FeedTab(s, onUpdate)
                SettingsTab.BETTING -> BettingTab(state, onUpdate, onNovigConnect, onNovigTest, onNovigDisconnect, bettingActions)
                SettingsTab.USAGE -> UsageTab(state, keys)
                SettingsTab.TOOLS -> ToolsTab(state, reportActions)
            }
        }
    }
}

/** Scan: pause, which scanner, the start window, background auto-scan and alerts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ScanTab(s: ScanSettings, onUpdate: SettingsUpdate) {
    SectionTitle("Scanner")
    // Tj, 2026-09-28: "Make an option in the app to pause all scanning".
    SwitchRow(
        "Pause all scanning",
        "Stops a scan running now; nothing is read (Vigilant's scans, CrazyNinjaOdds' list, background auto-scan) " +
            "until you switch it off. Also the pause button on the +EV and CNO tabs and the widget. Opening bets and grading tracked ones from final scores still work; the Tracker's Check odds now waits.",
        s.paused,
    ) { v -> onUpdate { it.copy(paused = v) } }
    ChoiceChips(ScannerMode.entries, s.scanner, { it.displayName }) { v -> onUpdate { it.copy(scanner = v) } }
    Hint(
        when (s.scanner) {
            ScannerMode.BOTH -> "Vigilant's own scan (tap Scan) and CrazyNinjaOdds' list (kept current while on screen), both in the mini window."
            ScannerMode.VIGILANT -> "Only Vigilant's own scan. CrazyNinjaOdds is never read."
            ScannerMode.CNO -> "Only CrazyNinjaOdds' list. Vigilant's scan and every API behind it (${if (AppBook.isNovig) "Novig, " else "PropLine, "}Pinnacle, Polymarket, " +
                "Kalshi, The Odds API) are asleep, in the background auto-scan too: nothing of theirs loads or spends credits, and their tabs and settings are hidden. " +
                "The CNO scanner reads only crazyninjaodds.com (and ESPN's rosters for player teams, if on)."
        },
    )
    if (AppBook.isNovig) {
        Text("Games starting within", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.STARTS_WITHIN_CHOICES, s.startsWithinHours, ::startsWithinLabel) { v -> onUpdate { it.copy(startsWithinHours = v) } }
        Hint(
            "Every list (+EV, CNO, Games and the widgets) shows only games starting within this window, and Vigilant's scan reads " +
                "only these games when it's shorter than Days ahead, then stops (widen it and scan again for more). Games already " +
                "under way still show when live games are on.",
        )
    }

    // ---- Background auto-scan and alerts (Vigilant for Novig) ------------------------------
    if (AppBook.isNovig) AutoScanSection(s, onUpdate)
}

/** CNO & widget: the CNO scanner's filters and refresh, and the widget / mini window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CnoTab(s: ScanSettings, onUpdate: SettingsUpdate) {
    // ---- The CNO scanner ----------------------------------------------------------------
    if (s.cnoOn) {
        val f = s.cnoFilters
        val onCno: ((CnoFilters) -> CnoFilters) -> Unit = { t -> onUpdate { it.copy(cnoFilters = t(it.cnoFilters)) } }
        SectionTitle("CNO scanner")
        Text("Devig (all worst case)", style = MaterialTheme.typography.bodyMedium)
        ChoiceChips(CnoDevig.entries, f.devig, { it.displayName }) { v -> onCno { it.copy(devig = v) } }
        Hint(
            when (f.devig) {
                CnoDevig.CONSERVATIVE -> "The worse of CNO's two worst cases: its most cautious setting. It hides some real edges; what's left is the safest."
                CnoDevig.LIQUIDITY_WEIGHTED -> "Books weighted by liquidity and limits (CNO's default)."
                CnoDevig.MARKET_CONSENSUS -> "Every book counts the same."
            } + " Worst case = the longest fair odds of multiplicative, additive/Shin and power.",
        )
        Text("Longest odds", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.CNO_MAX_ODDS_CHOICES, f.maxOdds, { if (it <= 0) "No cap" else "+$it" }) { v -> onCno { it.copy(maxOdds = v) } }
        Hint(if (f.maxOdds > 0) "Favorites and underdogs up to +${f.maxOdds} only: no longshots." else "Any odds, longshots included.")
        Text("Fewest books behind the fair price", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.CNO_MIN_BOOKS_CHOICES, f.minBooks, { "$it+" }) { v -> onCno { it.copy(minBooks = v) } }
        Hint("A fair price from one or two books can be a small market's mistake. Tap any bet to see which books price both sides and Vigilant's own check.")
        Text("Minimum EV", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.CNO_MIN_EV_CHOICES, f.minEv, { Format.percent(it, 0) }) { v -> onCno { it.copy(minEv = v) } }
        SwitchRow(
            "Require a complete sportsbook",
            "At least one book prices every side of the market (CNO's own filter).",
            f.completeBook,
        ) { v -> onCno { it.copy(completeBook = v) } }
        Hint("Always left out: EV over 20% (almost always a stale line), rows CNO devigged from one side only (⚠️), and rows whose EV doesn't follow from their fair odds.")

        Text("Refresh", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(CnoFeed.REFRESH_CHOICES, s.cnoRefreshSeconds, ::secondsLabel) { v -> onUpdate { it.copy(cnoRefreshSeconds = v) } }
        Hint(refreshHint(s))
        Text("Rows per read", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.CNO_ROWS_CHOICES, f.rows, { "$it" }) { v -> onCno { it.copy(rows = v) } }
        Hint("CNO sends its best-EV rows first; fewer rows is less data per read.")
        SwitchRow(
            "Green ✓ when books agree",
            "Reads the ${CnoFeed.AGREE_TOP} best bets' books from CNO in the background (one bet every few seconds, " +
                "each again after ${CnoFeed.AGREE_TTL_MS / 60_000} minutes), after the list, so the list is never slower. " +
                "✓ = ${CnoBooks.MIN_TWO_SIDED}+ books price both sides and at least ${CnoBooks.MIN_AGREEING} of them each say it's +EV.",
            s.cnoCheckBooks,
        ) { v -> onUpdate { it.copy(cnoCheckBooks = v) } }
        SwitchRow(
            "Only bets the books agree on",
            "The list, its count and the widget show only ✓ bets: ${CnoBooks.MIN_TWO_SIDED}+ other books price both sides " +
                "and ${CnoBooks.MIN_AGREEING}+ of them each say it's +EV, so the fair price isn't one book's. The " +
                "${CnoFeed.AGREE_TOP_ONLY_AGREED} best bets' books are read (one every few seconds); a bet shows once its " +
                "books are in. Fewer bets, more reliable ones.",
            s.cnoOnlyAgreed,
        ) { v -> onUpdate { it.copy(cnoOnlyAgreed = v) } }
        // Novig's own order books: Vigilant MGM has no such public feed for BetMGM.
        if (AppBook.isNovig) SwitchRow(
            "Novig's price now",
            "The ${com.tjshea.vigilant.data.cno.NovigLive.LIVE_TOP} best CNO bets on Novig show Novig's current price, read from Novig's own " +
                "order book every ${com.tjshea.vigilant.data.cno.NovigLive.LIVE_EVERY_MS / 1000} s while the list is on screen, and the EV " +
                "at it against CNO's fair odds: up to date even when CNO is slow or out of reach. \"was +117\" means it moved " +
                "since CNO listed it; an orange EV, that it's now under your minimum.",
            s.cnoLivePrices,
        ) { v -> onUpdate { it.copy(cnoLivePrices = v) } }
        SwitchRow(
            "Player teams",
            "Player bets show the team, like D. Schultz (HOU), from ESPN's rosters: two small reads per new game, kept for a day.",
            s.cnoPlayerTeams,
        ) { v -> onUpdate { it.copy(cnoPlayerTeams = v) } }
        CnoViewEditor(s.cnoViewUrl) { link -> onUpdate { it.copy(cnoViewUrl = link) } }
    }

    // ---- Mini window --------------------------------------------------------------------
    SectionTitle("Mini window")
    Hint("The widget opens only when you press its button at the top of the list.")
    SwitchRow(
        "Also open it when I leave Vigilant",
        "Off (the default): leaving Vigilant for another app does nothing. On: leaving it with bets to show (or a scan " +
            "running), or opening a bet in ${AppBook.name}, brings the widget up over that app by itself.",
        s.miniWindow,
    ) { v -> onUpdate { it.copy(miniWindow = v) } }
    // The floating widget, for either scanner (Tj, 2026-09-27: "on the regular vigilant
    // scanner, make it also have a widget").
    val context = androidx.compose.ui.platform.LocalContext.current
    var allowed by remember { mutableStateOf(com.tjshea.vigilant.app.FloatingWidget.allowed(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        allowed = com.tjshea.vigilant.app.FloatingWidget.allowed(context)
        onPauseOrDispose { }
    }
    SwitchRow(
        "Floating widget you can touch",
        "Up and down buttons always at the bottom, tap a bet to open it in ${AppBook.name}'s bet slip, ✓ to mark it placed " +
            "or ✕ to remove it without betting (hidden everywhere in Vigilant for good, with Undo), hold a CNO bet for " +
            "every book's odds. The top bar's switch picks CNO only, Both or Vigilant only. Spread or pinch two fingers " +
            "on it to resize it (and slide them to move it), or pull a green corner; drag its frame or top bar to move " +
            "it. − shrinks it to a bubble, the top ✕ closes it. Off: the picture-in-picture window.",
        s.floatingWidget,
    ) { v -> onUpdate { it.copy(floatingWidget = v) } }
    if (s.floatingWidget && !allowed) {
        Hint("Needs Android's \"Display over other apps\" for Vigilant (until then it's picture-in-picture). If Android greys the switch out: App info › ⋮ › Allow restricted settings.")
        OutlinedButton(onClick = { runCatching { context.startActivity(com.tjshea.vigilant.app.FloatingWidget.permissionIntent(context)) } }) {
            Text("Allow display over other apps")
        }
    }
    if (s.cnoOn) {
        Hint(
            "CNO is read only while its tab or a widget is on screen: closing the widget (✕), shrinking it to a bubble, locking the phone or closing Vigilant stops every read" +
                if (s.autoScansCno) " (background auto-scan still reads it every ${s.autoScanMinutes} min)." else ".",
        )
    }
    // Any mode: the widget's switch can turn Vigilant's scan on from there.
    Text("Vigilant's scan again while the widget is open", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.WIDGET_RESCAN_CHOICES, s.widgetRescanMinutes, { if (it <= 0) "Off" else "$it min" }) { v -> onUpdate { it.copy(widgetRescanMinutes = v) } }
    Hint(
        "With Both (the widget's top-bar switch, or Scanner above), the widget lists Vigilant's bets and CNO's together, best EV first; " +
            "a bet both list shows once, tagged with CNO's EV. " +
            (if (s.widgetRescanMinutes > 0) "Vigilant scans again every ${s.widgetRescanMinutes} min while the widget${if (s.cnoOn) " or CNO's tab" else ""} is on screen. Each scan spends API credits (Pinnacle / The Odds API keys)."
            else "Off: Vigilant scans only when you tap Scan (no API credits spent on its own).") +
            " Its bets leave the widget once the odds behind them are too old (${com.tjshea.vigilant.data.scanner.Freshness.LIMIT_TEXT}): older odds aren't compared.",
    )
    if (!s.floatingWidget) {
        Hint(
            when (s.scanner) {
                ScannerMode.BOTH -> "Picture-in-picture: it lists Vigilant's bets and CrazyNinjaOdds' (tagged CNO), best EV first; tap it for Scan (which refreshes CNO's too), Recheck and Next. Pinch or double-tap to enlarge; drag it to the bottom to close."
                ScannerMode.VIGILANT -> "Picture-in-picture: it lists Vigilant's bets; tap it for Scan, Recheck and Next. Pinch or double-tap to enlarge; drag it to the bottom to close."
                ScannerMode.CNO -> "Picture-in-picture: it lists CNO's bets; tap it for Up, Down and Books (every book's odds for the top bet; Up and Down then move between bets). Pinch or double-tap to enlarge."
            },
        )
        Hint("If it never appears, turn on picture-in-picture for Vigilant in Android Settings › Apps › Special app access.")
    }
}

/** Fair odds: how fair odds are worked out, where they come from (and their keys), the sportsbooks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FairOddsTab(state: UiState, keys: KeyActions, onUpdate: SettingsUpdate) {
    val s = state.settings
    // ---- Fair odds ---------------------------------------------------------------------
    SectionTitle("Fair odds method")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        FairSource.entries.forEachIndexed { i, source ->
            SegmentedButton(
                selected = s.fairSource == source,
                onClick = { onUpdate { it.copy(fairSource = source) } },
                shape = SegmentedButtonDefaults.itemShape(i, FairSource.entries.size),
            ) { Text(source.shortName, maxLines = 1) }
        }
    }
    Hint(
        when (s.fairSource) {
            FairSource.SHARP -> "Fair price from the sharp books you mark below (Pinnacle by default)."
            FairSource.MARKET_AVERAGE -> "Each book is devigged on its own, then all of them are averaged."
            FairSource.BLEND -> "A weighted mix: ${(s.sharpWeight * 100).roundToInt()}% sharp, ${100 - (s.sharpWeight * 100).roundToInt()}% market average."
        },
    )
    if (s.fairSource == FairSource.BLEND) {
        var weight by remember(s.sharpWeight) { mutableFloatStateOf(s.sharpWeight.toFloat()) }
        Text("Sharp weight: ${(weight * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = weight,
            onValueChange = { weight = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { onUpdate { it.copy(sharpWeight = weight.toDouble()) } },
            valueRange = 0f..1f,
            steps = 19,
        )
    }
    if (s.fairSource == FairSource.SHARP) {
        SwitchRow("Fall back to market average", "When no sharp book quotes a line, use every book instead of skipping it.", s.fallbackToAverage) { v ->
            onUpdate { it.copy(fallbackToAverage = v) }
        }
    }

    Text("Sharp books", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FairSettings.KNOWN_SHARP_CANDIDATES.forEach { key ->
            FilterChip(
                selected = key in s.sharpBooks,
                onClick = { onUpdate { it.copy(sharpBooks = if (key in it.sharpBooks) it.sharpBooks - key else it.sharpBooks + key) } },
                label = { Text(TheOddsApiClient.bookTitle(key)) },
            )
        }
    }
    Hint("Pinnacle comes from your PinnWire or pinnapi key (or PropLine / The Odds API). Polymarket and Kalshi are exchanges: their prices count as sharp when the market is tight (3¢ or less).")

    Text("Minimum books for an average: ${s.minBooks}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    ChoiceChips((1..5).toList(), s.minBooks, { it.toString() }) { v -> onUpdate { it.copy(minBooks = v) } }
    SwitchRow(
        "Outlier guard",
        "With 3 or more books, use the lower of their average and median, so one stale book can't create a fake edge.",
        s.outlierGuard,
    ) { v -> onUpdate { it.copy(outlierGuard = v) } }

    SectionTitle("Devig method")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DevigMethod.entries.forEach { m ->
            FilterChip(selected = s.devigMethod == m, onClick = { onUpdate { it.copy(devigMethod = m) } }, label = { Text(m.displayName) })
        }
    }
    Hint(s.devigMethod.blurb)

    // ---- Sources ------------------------------------------------------------------------
    SectionTitle("Where fair odds come from")
    Hint("Each is called only when you scan. Polymarket and Kalshi need no key; the others take a free key.")
    val pinnKeys = state.pinnwireKeys.size + state.pinnapiKeys.size
    SwitchRow(
        "Pinnacle",
        if (pinnKeys == 0) "The sharpest book. Needs a free key: pinnwire.com (100 requests a day, player props included) or pinnapi.com (game lines only)."
        else "About 1–2 of a key's 100 daily requests per scan. PinnWire keys go first (with Pinnacle's player props), then pinnapi's.",
        s.usePinnacle,
    ) { v -> onUpdate { it.copy(usePinnacle = v) } }
    if (s.usePinnacle) {
        Text("PinnWire keys (game lines and player props)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        KeyListEditor(ApiProvider.PINNWIRE, state.pinnwireKeys, keys, "Add a PinnWire key")
        Text("pinnapi keys (game lines, used when PinnWire's run out)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        KeyListEditor(ApiProvider.PINNAPI, state.pinnapiKeys, keys, "Add a pinnapi key")
        if (state.pinnapiKeys.size > 1 || state.pinnwireKeys.size > 1) {
            Hint(
                "Heads up: these feeds' terms forbid circumventing their rate limits, so using extra keys of one feed to get " +
                    "past 100 a day could get the keys suspended. Each key is still kept inside its own limit.",
            )
        }
    }
    SwitchRow("Polymarket", "Free. NFL, college football, NBA, WNBA, MLB, NHL, UFC.", s.usePolymarket) { v -> onUpdate { it.copy(usePolymarket = v) } }
    SwitchRow("Kalshi", "Free. NFL, college football, MLB, NBA, NHL, UFC.", s.useKalshi) { v -> onUpdate { it.copy(useKalshi = v) } }
    SwitchRow(
        "PropLine",
        if (state.proplineKeys.isEmpty()) "Your sportsbooks below (Pinnacle, DraftKings, FanDuel, BetMGM…) in one feed. Free key at prop-line.com: 1,000 requests a day."
        else "1 request per league per scan for game lines, 1 per game for player props (${gamesLabel(s.propLineGamesPerScan)} a scan, below). 1,000 a day per key.",
        s.usePropLine,
    ) { v -> onUpdate { it.copy(usePropLine = v) } }
    if (s.usePropLine) {
        KeyListEditor(ApiProvider.PROPLINE, state.proplineKeys, keys, "Add a PropLine key")
    }
    // ParlayAPI (Tj, 2026-09-30, RESEARCH.md §43): Pinnacle and 9 more books, a league's props and alternate lines in one call each, and
    // Pinnacle's closing lines for CLV. Paced to a day's share of the plan; a free key only buys the closes.
    SwitchRow(
        "ParlayAPI",
        if (state.parlayKeys.isEmpty()) "Optional, best on its \$5 plan: Pinnacle, ProphetX, BetOnline, bet365, Bovada and the US books, a " +
            "whole league's player props and alternate lines in one call each, and Pinnacle's closing lines for your CLV. Free key at " +
            "parlay-api.com (1,000 credits a month: closing lines only); \$5 a month (20,000) for scans too."
        else "Pinnacle and 9 more books (3 credits a league; 5 with Pinnacle's alternate lines, bought only when PinnWire and pinnapi are " +
            "off) and every book's player props (3 a league) " +
            "each scan, paced to a day's share of your plan (background auto-scans use half of it at most), plus Pinnacle's closing " +
            "lines for your CLV (the last 300 credits are kept for them). A free key is kept for closing lines. Off, spent or gone: the " +
            "other feeds carry on.",
        s.useParlay,
    ) { v -> onUpdate { it.copy(useParlay = v) } }
    if (s.useParlay) {
        KeyListEditor(ApiProvider.PARLAY, state.parlayKeys, keys, "Add a ParlayAPI key")
    }
    if (!AppBook.isNovig) {
        Hint(
            "${AppBook.name}'s own odds come in these same requests (no request just for ${AppBook.name}): PropLine first, " +
                "The Odds API where PropLine can't answer. Keep one of them on, with a key. ${AppBook.name}'s prices never " +
                "count toward their own fair line.",
        )
    }
    // PropLine goes first for the same sportsbooks; The Odds API backs it up (RESEARCH.md §23).
    val propLineFirst = s.usePropLine && state.proplineKeys.isNotEmpty()
    SwitchRow(
        "The Odds API",
        when {
            state.oddsApiKeys.isEmpty() -> "Optional: US sportsbooks plus Pinnacle. Free key at the-odds-api.com (500 credits a month)."
            propLineFirst -> "Backup to PropLine (the same sportsbooks): asked only for leagues, games and props PropLine couldn't give, " +
                "so its credits last. Keys are used in order; the next takes over when one runs out."
            else -> "US sportsbooks plus Pinnacle. Keys are used in order; the next takes over when one runs out."
        },
        s.useOddsApi,
    ) { v -> onUpdate { it.copy(useOddsApi = v) } }
    if (s.useOddsApi) {
        KeyListEditor(ApiProvider.THE_ODDS_API, state.oddsApiKeys, keys, "Add an Odds API key")
        Text("Re-use The Odds API between scans for", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        ChoiceChips(ScanSettings.ODDS_API_REUSE_CHOICES, (s.oddsApiReuseMs / 60_000L).toInt(), { if (it == 0) "Every scan" else "${it}m" }) { v ->
            onUpdate { it.copy(oddsApiReuseMinutes = v) }
        }
        Hint(creditEstimate(s, backup = propLineFirst))
    }
    if (s.useOddsApi || s.usePropLine || s.useParlay) {
        SwitchRow(
            "Sportsbook player props",
            "Your books' props (DraftKings, FanDuel, BetMGM…), each devigged, then averaged and blended with Pinnacle and " +
                "Kalshi where they have the same line. ParlayAPI (paid plan): a whole league's in one call. PropLine: 1 request per " +
                "game. The Odds API: 1 credit per prop type per game, only for games and prop types PropLine didn't price.",
            s.useBookProps,
        ) { v -> onUpdate { it.copy(useBookProps = v) } }
        if (s.useBookProps) {
            if (MarketFamily.PLAYER_PROPS !in s.families) Hint("Turn on Player props under Markets below to use these.")
            Text("Only games starting within", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(ScanSettings.BOOK_PROP_HOURS_CHOICES, s.bookPropHours, { if (it >= ScanSettings.NO_LIMIT) "All" else "${it}h" }) { v -> onUpdate { it.copy(bookPropHours = v) } }
            if (s.bookPropHours >= ScanSettings.NO_LIMIT) Hint("All: every game the scan reads (${windowLabel(s.scanWindowHours)} now).")
            if (s.usePropLine) {
                Text("PropLine props: most games per scan", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(ScanSettings.PROPLINE_GAMES_CHOICES, s.propLineGamesPerScan, { if (it >= ScanSettings.NO_LIMIT) "No limit" else it.toString() }) { v ->
                    onUpdate { it.copy(propLineGamesPerScan = v) }
                }
                Hint(propLineGamesHint(s))
            }
            if (s.useOddsApi) {
                Text("Prop types per game (The Odds API)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(BookPropSet.entries, s.bookPropSet, { it.displayName }) { v -> onUpdate { it.copy(bookPropSet = v) } }
                Text("Most credits per scan on props", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(ScanSettings.BOOK_PROP_CREDIT_CHOICES, s.bookPropCreditsPerScan, { if (it == 0) "None" else if (it >= ScanSettings.NO_LIMIT) "No limit" else it.toString() }) { v ->
                    onUpdate { it.copy(bookPropCreditsPerScan = v) }
                }
                Text("Re-use a game's props for", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(ScanSettings.BOOK_PROP_REUSE_CHOICES, (s.bookPropReuseMs / 60_000L).toInt(), ::minutesLabel) { v ->
                    onUpdate { it.copy(bookPropReuseMinutes = v) }
                }
                Hint(bookPropEstimate(s, backup = propLineFirst))
            }
        }

        SectionTitle("Sportsbooks for fair odds (${s.referenceBooks.size}/10)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TheOddsApiClient.KNOWN_BOOKMAKERS.forEach { (key, title) ->
                val on = key in s.referenceBooks
                FilterChip(
                    selected = on,
                    enabled = on || s.referenceBooks.size < TheOddsApiClient.MAX_BOOKMAKERS_ONE_REGION,
                    onClick = { onUpdate { it.copy(referenceBooks = if (on) it.referenceBooks - key else it.referenceBooks + key) } },
                    label = { Text(title) },
                )
            }
        }
        Hint(
            "Read from PropLine first, and from The Odds API only for what PropLine couldn't give. On The Odds API, up to 10 " +
                "books cost the same: one credit per market per league, and one per prop type per game for props. PropLine " +
                "carries all of them except Caesars, ESPN BET, Betfair, Bally Bet and MyBookie, which count only when The Odds " +
                "API is asked (a book of those picked as sharp above keeps it asked every scan).",
        )
    }
}

/** +EV feed: what the feed shows and how big a scan is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FeedTab(s: ScanSettings, onUpdate: SettingsUpdate) {
    SectionTitle("+EV feed")
    var minEv by remember(s.minEvPercent) { mutableFloatStateOf((s.minEvPercent * 100).toFloat()) }
    Text("Minimum EV: ${String.format(Locale.US, "%.1f", minEv)}%", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = minEv,
        onValueChange = { minEv = (it * 2).roundToInt() / 2f },
        onValueChangeFinished = { onUpdate { it.copy(minEvPercent = minEv / 100.0) } },
        valueRange = 0f..10f,
        steps = 19,
    )
    Text("Longest odds shown: ${maxOddsLabel(s.maxOdds)}", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(ScanSettings.MAX_ODDS_CHOICES, s.maxOdds, ::maxOddsLabel) { v -> onUpdate { it.copy(maxOdds = v) } }
    Hint("Fair odds are least reliable on longshots, which is where most fake edges show up.")
    Text("Markets", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarketFamily.entries.forEach { f ->
            FilterChip(
                selected = f in s.families,
                onClick = { onUpdate { it.copy(families = if (f in it.families) it.families - f else it.families + f) } },
                label = { Text(f.displayName) },
            )
        }
    }
    // Novig's per-line reads are what these limit; a sportsbook's lines cost no request each.
    if (AppBook.exchange) {
    Text("Alternate lines per game: ${allLabel(s.linesPerGame)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.LINES_PER_GAME_CHOICES, s.linesPerGame, ::allLabel) { v -> onUpdate { it.copy(linesPerGame = v) } }
    Hint("Per spread, total and team total (full game and 1st half). Every line is one Novig request per scan: fewer lines scan faster and stay well under Novig's rate limit.")
    if (MarketFamily.PLAYER_PROPS in s.families) {
        Text("Player props per game: ${allLabel(s.propsPerGame)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.PROPS_PER_GAME_CHOICES, s.propsPerGame, ::allLabel) { v -> onUpdate { it.copy(propsPerGame = v) } }
        Hint("NFL, MLB and WNBA props that Pinnacle, Kalshi or the sportsbooks also price, on the same line. The best-covered ones are checked first.")
    }
    Text("Most Novig prices per scan: ${limitLabel(s.maxBooksPerScan)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.MAX_BOOKS_CHOICES, s.maxBooksPerScan, ::limitLabel) { v -> onUpdate { it.copy(maxBooksPerScan = v) } }
    Hint(scanSizeHint(s.maxBooksPerScan))
    SwitchRow(
        "Fill the scan with every quoted line",
        (if (s.maxBooksPerScan >= ScanSettings.NO_LIMIT) "After the lines and props per game above, every other line the books quote is read too "
        else "After the lines and props per game above, what's left of the ${s.maxBooksPerScan} prices goes to every other line the books quote ") +
            "(alternate spreads and totals, more props), best-covered first. Off: only the picks above.",
        s.fillBudget,
    ) { v -> onUpdate { it.copy(fillBudget = v) } }
    } else {
        Hint("Every ${AppBook.name} line another book also prices is checked: its lines come in the fair-odds requests, so there's no per-line cost to limit.")
    }
    Text("Days ahead: ${s.daysAhead}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.DAYS_AHEAD_CHOICES, s.daysAhead, { "${it}d" }) { v -> onUpdate { it.copy(daysAhead = v) } }
    Hint(
        "Games starting within this many days are scanned (or within \"Starts within\" when that's shorter), then the scan " +
            "stops. A week takes in the next college Saturday and NFL Sunday; the fair-odds requests barely change with the " +
            "window, and the soonest games are read first.",
    )
    SwitchRow(
        "Include live games",
        if (AppBook.isNovig) "Off by default: reference odds lag in-game, and Novig charges its taker fee once a game is live."
        else "Off by default: in-game odds move faster than the feeds re-read them, so live edges are rarely real.",
        s.includeLive,
    ) { v -> onUpdate { it.copy(includeLive = v) } }
}

/** Betting: bankroll and Kelly, the bet slip's amount, and betting through Novig's API. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BettingTab(
    state: UiState,
    onUpdate: SettingsUpdate,
    onNovigConnect: (com.tjshea.vigilant.data.novig.signing.ManagementKey?) -> Unit,
    onNovigTest: () -> Unit,
    onNovigDisconnect: () -> Unit,
    bettingActions: BettingActions,
) {
    val s = state.settings
    // ---- Stake sizing -----------------------------------------------------------------
    SectionTitle("Bankroll & Kelly")
    var bankroll by remember(s.bankroll) { mutableStateOf(String.format(Locale.US, "%.0f", s.bankroll)) }
    OutlinedTextField(
        value = bankroll,
        onValueChange = { t ->
            bankroll = t.filter { it.isDigit() || it == '.' }.take(9)
            bankroll.toDoubleOrNull()?.takeIf { it > 0 }?.let { v -> onUpdate { it.copy(bankroll = v) } }
        },
        label = { Text("Bankroll $") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    ChoiceChips(ScanSettings.KELLY_CHOICES, s.kellyMultiplier, Format::kellyLabel) { v -> onUpdate { it.copy(kellyMultiplier = v) } }
    Hint(
        if (AppBook.exchange) "Suggested stakes are capped at what Novig's book can actually fill at +EV."
        else "${AppBook.name} doesn't publish its limits: a suggested stake over your max bet there is capped by ${AppBook.name} itself.",
    )
    if (AppBook.isNovig) {
        // Novig's bet-slip links take a wager (Tj, 2026-09-28: "automatically enter 1 dollar per bet, the kelly value
        // per bet, or an amount I can type into the settings"; NovigLinks).
        Text("Amount in Novig's bet slip", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        ChoiceChips(com.tjshea.vigilant.data.novig.SlipStake.entries.toList(), s.slipStake, { it.label }) { v -> onUpdate { it.copy(slipStake = v) } }
        if (s.slipStake == com.tjshea.vigilant.data.novig.SlipStake.CUSTOM) {
            var custom by remember(s.slipCustomStake) { mutableStateOf(com.tjshea.vigilant.data.novig.NovigLinks.amountText(s.slipCustomStake)) }
            OutlinedTextField(
                value = custom,
                onValueChange = { t ->
                    custom = t.filter { it.isDigit() || it == '.' }.take(8)
                    custom.toDoubleOrNull()?.takeIf { it > 0 }?.let { v -> onUpdate { it.copy(slipCustomStake = v) } }
                },
                label = { Text("Amount $") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("slipCustomStake"),
            )
        }
        Hint(
            when (s.slipStake) {
                com.tjshea.vigilant.data.novig.SlipStake.OFF -> "Tapping a bet opens Novig's bet slip with no amount: you type it in Novig."
                com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR -> "Every bet you tap opens in Novig's bet slip with $1 entered."
                com.tjshea.vigilant.data.novig.SlipStake.KELLY -> "Every bet you tap opens with its Kelly stake above entered (never under $1)."
                com.tjshea.vigilant.data.novig.SlipStake.CUSTOM -> "Every bet you tap opens with this amount entered."
            } + " From the +EV tab, the widget, the CNO tab and alerts. You still confirm the bet in Novig.",
        )
    }
    if (!AppBook.isNovig) {
        // BetMGM's sites are per state: its bet-slip links need Tj's (BetMgmLinks).
        SectionTitle("${AppBook.name} state")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.tjshea.vigilant.data.book.BetMgmLinks.STATES.forEach { st ->
                FilterChip(
                    selected = s.bookState == st,
                    onClick = { onUpdate { it.copy(bookState = if (it.bookState == st) "" else st) } },
                    label = { Text(st.uppercase()) },
                )
            }
        }
        Hint(
            if (s.bookState.isBlank()) "Pick the state you bet ${AppBook.name} in: its sites are per state, so without it a tap opens ${AppBook.name}'s home instead of the bet slip."
            else "Taps open sports.${s.bookState}.betmgm.com: the bet slip with the bet in it when the feed sent ${AppBook.name}'s ids, else the game.",
        )
    }

    // In CNO only too: CNO's cards bet through the API as well, and the Bet sheet's "Add money" lands on this wallet.
    if (AppBook.isNovig) {
        SectionTitle("Novig API key")
        NovigKeySection(state.novig, onNovigConnect, onNovigTest, onNovigDisconnect, lastScan = state.status, onForgetKey = bettingActions.onForgetKey)
        // Betting through Novig's API: needs the connected key's subaccount (Tj, 2026-09-29).
        if (state.novig.connection != null) NovigBettingSection(state.betting, s, bettingActions, onUpdate, savedKey = state.novig.managementKey)
    }
}

/** Usage & keys: each API's usage meter and the keys backup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.UsageTab(state: UiState, keys: KeyActions) {
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(keys.exportTo) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(keys.importFrom) }
    // ---- Usage meters ------------------------------------------------------------------
    // Opening the tab asks ParlayAPI's keys what they have left (free, at most every few minutes): the meter shows the provider's figures.
    val onShown by androidx.compose.runtime.rememberUpdatedState(keys.onUsageShown)
    androidx.compose.runtime.LaunchedEffect(Unit) { onShown() }
    SectionTitle("API usage")
    UsageSection(state)

    // ---- Keys backup ------------------------------------------------------------------
    SectionTitle("Keys backup")
    Hint(
        "Keys are saved on this phone, kept through every app update, and included in Android's backup. " +
            "Export a copy to keep them even if the app is uninstalled.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exporter.launch("vigilant-keys.json") }) { Text("Export keys") }
        OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import keys") }
    }
}

/** Tools: Diagnostics, the grading check, About. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ToolsTab(state: UiState, reportActions: ReportActions) {
    // ---- Diagnostics ---------------------------------------------------------------------
    SectionTitle("Diagnostics")
    Hint(
        "Something slow, odd or not grading? Tap Show report, then Copy, and paste it to Claude: your settings, the last scan's timing and errors, " +
            "each API's usage, the background scan and the Tracker's numbers, in one page. It never has a key in it.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = reportActions.onDiagnostics) { Text("Show report") }
        // The ledger check needs the betting key: Novig's own record of each API bet against what the Tracker did with it.
        if (AppBook.isNovig && state.betting.enabled) OutlinedButton(onClick = reportActions.onGradingCheck) { Text("Grading check") }
    }
    if (AppBook.isNovig && state.betting.enabled) {
        Hint("Grading check: what Novig's ledger and positions say about each bet you placed through the API, beside how the Tracker graded it. Run it after a game ends.")
    }

    SectionTitle("About")
    Hint(
        (if (AppBook.isNovig) "Vigilant ${BuildConfig.VERSION_NAME} · Novig prices: api.novig.com"
        else "Vigilant MGM ${BuildConfig.VERSION_NAME} · ${AppBook.name} prices: PropLine, The Odds API") + " · Fair odds: Pinnacle (PinnWire, pinnapi), " +
            "Polymarket, Kalshi, PropLine, The Odds API · CNO scanner: crazyninjaodds.com (player teams: ESPN). Vigilant's scan " +
            "fetches only when you tap Scan or pull to refresh; CrazyNinjaOdds' list only while its tab or a widget is on " +
            "screen (and the screen is on). Nothing runs in the background unless background auto-scan is on.",
    )
}

/** What a refresh choice means, with its data use (~17 KB per read plus ~0.7 KB per row, RESEARCH.md §19). */
fun refreshHint(s: ScanSettings): String {
    val kb = 17 + 0.7 * s.cnoFilters.rows
    val seconds = s.cnoRefreshSeconds
    val perHour = when {
        seconds == CnoFeed.REALTIME -> 720 // CNO updates every 13-33 s; real time averages about a read every 5 s
        seconds <= 0 -> 0
        else -> 3600 / seconds
    }
    val use = if (perHour == 0) "" else " About ${Math.round(perHour * kb / 1000)} MB of data an hour while on screen."
    return when {
        seconds == CnoFeed.REALTIME -> "Reads again as soon as CNO can have new odds (it updates every 13–33 s), then every 3 s until they land."
        seconds <= 0 -> "Only when you tap Refresh or pull down."
        else -> "Every ${secondsLabel(seconds)} while the CNO tab or a widget is on screen; CNO itself updates every 13–33 s."
    } + use + " Nothing is read once both are closed."
}

/**
 * Background auto-scan and +EV alerts (Tj, 2026-09-28: "auto scan either cno or both cno and vigilant
 * every 5 10 20 30 or 40 minutes in the background" and alerts "for a minimum of 2%, 3%, or 4%").
 */
@Composable
private fun AutoScanSection(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SectionTitle("Background auto-scan")
    ChoiceChips(AutoScanMode.entries, s.autoScan, { it.displayName }) { v -> onUpdate { it.copy(autoScan = v) } }
    if (s.autoScan != AutoScanMode.OFF) {
        Text("Every", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.AUTO_SCAN_MINUTES_CHOICES, s.autoScanMinutes, { "$it min" }) { v -> onUpdate { it.copy(autoScanMinutes = v) } }
    }
    Hint(autoScanHint(s))
    Text("Push alerts", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.ALERT_MIN_EV_CHOICES, s.alertMinEv, ::alertLabel) { v -> onUpdate { it.copy(alertMinEv = v) } }
    Hint(alertHint(s))
    // What Android needs from Tj for any of it: notifications (alerts, the ongoing note) and, so the
    // phone's battery saver can't hold scans back while it sleeps, unrestricted background use.
    var notify by remember { mutableStateOf(com.tjshea.vigilant.app.ScanService.canNotify(context)) }
    var unrestricted by remember { mutableStateOf(ignoresBatteryLimits(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notify = com.tjshea.vigilant.app.ScanService.canNotify(context)
        unrestricted = ignoresBatteryLimits(context)
        onPauseOrDispose { }
    }
    if ((s.autoScan != AutoScanMode.OFF || s.alertMinEv > 0.0) && !notify) {
        Hint("Notifications are off for Vigilant, so no alert can show.")
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) { Text("Allow notifications") }
    }
    if (s.autoScan != AutoScanMode.OFF && !unrestricted) {
        Hint("For scans on time while the phone sleeps, let Vigilant run in the background (Android's battery setting \"Unrestricted\").")
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) { Text("Let Vigilant run in the background") }
    }
}

private fun ignoresBatteryLimits(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

fun alertLabel(ev: Double): String = if (ev <= 0.0) "Off" else "${Math.round(ev * 100)}%+"

/** What background auto-scan does at these settings. */
fun autoScanHint(s: ScanSettings): String {
    if (s.autoScan == AutoScanMode.OFF) return "Off: Vigilant scans only when you tap Scan, and CrazyNinjaOdds is read only while its tab or a widget is on screen."
    val perDay = 60 / s.autoScanMinutes.coerceAtLeast(1) * 24
    val cnoPart = "CrazyNinjaOdds' list, then Novig's price now and every book's odds for its best bets (the green check's reads), and the books of your " +
        "open bets starting within the hour (the Tracker's closing line, for CLV)"
    val vigilantPart = "Vigilant's own scan exactly as the Scan button runs it (" +
        (if (s.maxBooksPerScan >= ScanSettings.NO_LIMIT) "every priced line in ${windowLabel(s.scanWindowHours)}: " else "${s.maxBooksPerScan} Novig prices at most: ") +
        "${scanTime(s.maxBooksPerScan)}). Each scan spends API credits like a tap on Scan: $perDay scans a day at this setting"
    val notification = " A quiet notification shows while it's on (Scan now, Stop)."
    return when {
        s.autoScansCno && s.autoScansVigilant -> "Every ${s.autoScanMinutes} min, with Vigilant open or closed: $cnoPart, then $vigilantPart.$notification"
        s.autoScansCno -> "Every ${s.autoScanMinutes} min, with Vigilant open or closed: $cnoPart.$notification About $perDay reads of CNO a day, each well under a second of work." +
            if (s.autoScan.vigilant) " Vigilant's scan is skipped, because the scanner above is on CNO only: no API credits are spent in the background." else ""
        s.autoScansVigilant -> "Every ${s.autoScanMinutes} min, with Vigilant open or closed: $vigilantPart.$notification CrazyNinjaOdds isn't read, because the scanner above is on Vigilant only."
        s.paused -> "Paused with everything else: resume scanning (the ⏸ button) to run again."
        else -> "Nothing runs in the background: the scanner above is on ${s.scanner.displayName}, which leaves nothing for this choice to read. Pick a scanner that is on."
    }
}

/** Which bets alert, at these settings. */
fun alertHint(s: ScanSettings): String =
    if (s.alertMinEv <= 0.0) "Off: no alerts." else "A notification for each new bet at ${Math.round(s.alertMinEv * 100)}% EV or better that several books agree on " +
        "(${CnoBooks.MIN_TWO_SIDED}+ books price both sides and ${CnoBooks.MIN_AGREEING}+ of them alone make it +EV), found by a background scan " +
        "or a scan you left running. Tap it to open the bet slip in ${AppBook.name}; its ✓ Placed button tracks the bet from the notification, without " +
        "opening Vigilant (Undo right after). Each bet alerts once; placed and removed bets, and games outside \"Starts within\", never do."

/** Tj's CNO Shared View link: paste, check, save. Blank means the app's book (Novig; BetMGM in Vigilant MGM) with CNO's defaults. */
@Composable
private fun CnoViewEditor(saved: String, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    val site = AppBook.current.cnoSiteId
    val normalized = CnoView.normalize(text, site)
    val current = CnoView.normalize(saved, site) ?: CnoView.defaultFor(site)
    Text("Your view: ${CnoView.describe(current)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Shared View link") },
        placeholder = { Text("Blank = ${AppBook.name}, CNO's recommended filters") },
        singleLine = true,
        isError = normalized == null,
        supportingText = {
            Text(
                when {
                    normalized == null -> "That isn't a CrazyNinjaOdds Positive EV link."
                    text.isBlank() -> "${AppBook.name} only, 3+ books, 2 sides."
                    else -> CnoView.describe(normalized)
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSave(if (text.isBlank()) "" else normalized!!) },
            enabled = normalized != null && (if (text.isBlank()) "" else normalized) != saved,
        ) { Text("Save") }
        if (saved.isNotBlank()) OutlinedButton(onClick = { text = ""; onSave("") }) { Text("Use ${AppBook.name} default") }
    }
    Hint("On crazyninjaodds.com's Positive EV page: set your filters (sports, leagues, markets, …), open Shared View, tap Copy Link, and paste it here. The scanner's settings above apply on top of it; where both set a limit, the stricter one wins.")
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles (a bigger target than the switch alone, and one control for TalkBack).
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}


/**
 * What a scan costs in Odds API credits, so the free tier's 500 isn't a surprise. Only the main
 * lines (moneyline, spread, total) are bought per league; 1st half, team totals and props are not.
 */
fun creditEstimate(s: ScanSettings, backup: Boolean = false): String {
    val markets = TheOddsApiClient.marketsFor(s.families).size
    if (markets == 0) return "No main-line markets are on, so game lines cost nothing."
    if (backup) return "Nothing while PropLine answers. When it can't, game lines cost about ${markets * s.leagues.size.coerceAtLeast(1)} " +
        "credits per refresh (${s.leagues.size} league${if (s.leagues.size == 1) "" else "s"} × $markets market${if (markets == 1) "" else "s"}), re-used as set here."
    val perScan = markets * s.leagues.size.coerceAtLeast(1)
    val base = "Game lines cost about $perScan credit${if (perScan == 1) "" else "s"} per refresh " +
        "(${s.leagues.size} league${if (s.leagues.size == 1) "" else "s"} × $markets market${if (markets == 1) "" else "s"})."
    val reuse = (s.oddsApiReuseMs / 60_000L).toInt()
    return if (reuse == 0) {
        "$base Every scan refreshes it."
    } else {
        val perHour = perScan * (60 / reuse)
        "$base Scanning as often as you like costs at most $perHour an hour (odds are never re-used past ${minutesLabel(reuse)}: older ones aren't compared)."
    }
}

/** What sportsbook props cost, in the same terms as [creditEstimate]. */
fun bookPropEstimate(s: ScanSettings, backup: Boolean = false): String {
    if (s.bookPropCreditsPerScan <= 0) return "No credits are set aside for props, so none are bought."
    if (s.bookPropCreditsPerScan >= ScanSettings.NO_LIMIT) return "No limit: " +
        (if (backup) "every game and prop type PropLine didn't price this scan" else "every game with props") +
        " in ${windowLabel(s.bookPropWindowHours)}, soonest first, one credit per prop type, each game once a scan (re-used for " +
        "${minutesLabel((s.bookPropReuseMs / 60_000L).toInt())}). " + creditWorstCase(s.bookPropCreditsPerScan)
    if (backup) return "Only games and prop types PropLine didn't price this scan, soonest first, never more than " +
        "${s.bookPropCreditsPerScan} credits a scan (one per prop type); re-used for ${minutesLabel((s.bookPropReuseMs / 60_000L).toInt())}. " +
        creditWorstCase(s.bookPropCreditsPerScan)
    val perGame = if (s.bookPropSet == BookPropSet.CORE) 4 else null
    val games = perGame?.let { s.bookPropCreditsPerScan / it }
    val reach = if (games != null) {
        "about $perGame credits a game, so up to $games game${if (games == 1) "" else "s"} a scan"
    } else {
        "one credit per prop type ${AppBook.name} lists for the game (up to 18 in football)"
    }
    return "Props cost $reach, soonest games first, never more than ${s.bookPropCreditsPerScan} credits a scan. " +
        "A game's props are re-used for ${minutesLabel((s.bookPropReuseMs / 60_000L).toInt())}, so scanning again sooner costs nothing for it " +
        "(never longer: older odds aren't compared). " + creditWorstCase(s.bookPropCreditsPerScan)
}

/**
 * How far a month's credits go if every scan spends the most it may on props (Tj, 2026-09-28: "Can I raise the most credits
 * per scan on props safely?"): safe as far as The Odds API plan allows; past it props come from PropLine, Pinnacle and Kalshi.
 */
fun creditWorstCase(perScan: Int): String = if (perScan >= ScanSettings.NO_LIMIT) {
    "About 4 credits a game with the Core 4 prop types (up to 18 in football with All), so a college Saturday or a full NFL " +
        "Sunday can be hundreds: The Odds API's free 500 a month can go in one or two scans, and background auto-scan repeats it. " +
        "When they run out, props still come from PropLine, Pinnacle and Kalshi."
} else {
    "At most $perScan a scan: The Odds API's free 500 credits a month last at least ${500 / perScan} scans, " +
        "a 20,000-credit plan at least ${String.format(java.util.Locale.US, "%,d", 20_000 / perScan)}. When they run out, props still come from PropLine, Pinnacle and Kalshi."
}

fun maxOddsLabel(american: Int): String = if (american <= 0) "Any" else "+$american"

/** About how long reading [books] Novig prices takes on the public routes (about 300 a minute). */
fun scanTime(books: Int): String =
    if (books >= ScanSettings.NO_LIMIT) "as long as the games take, 10 minutes at most"
    else (books / 300.0).let { if (it < 1.0) "under a minute" else if (it < 1.5) "about a minute" else "about ${Math.round(it)} minutes" }

/**
 * About how long a scan of [books] Novig prices takes on the public routes (4–6 a second, NOVIG_API.md
 * §11.1), and what the limit decides.
 */
fun scanSizeHint(books: Int): String {
    if (books >= ScanSettings.NO_LIMIT) {
        return "No limit: every line another book also prices, for the games in your time window (Days ahead, or Starts within " +
            "when shorter), each read once; then the scan stops. Most +EV lines are read first, so bets still show within seconds. " +
            "With a Novig key, the first 2,000 arrive by live feed about 8 seconds in and the rest by request (about 14 a second); on " +
            "Novig's public prices about 300 a minute. Other books' odds are read as the scan starts and last 5 minutes (10 for games " +
            "over 3 hours off), so a scan never runs past about 8 minutes: lines it can't reach by then are read first next scan."
    }
    return "$books is ${scanTime(books)} on Novig's public prices; with a Novig key connected, the whole scan (up to 2,000, what its " +
        "live feed watches at once) arrives by live feed about 8 seconds in. A scan that runs long leaves lines whose other books' odds " +
        "would already be too old for the next scan. Results appear as " +
        "they're priced, likeliest +EV first (last scan's edges, then props and period lines). Past the limit, main lines and " +
        "the soonest games come first. Only lines another book also prices are read, so a scan can finish below the limit."
}

fun minutesLabel(minutes: Int): String = if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"

/** A per-scan cap's chip: the number, or "No limit" ([ScanSettings.NO_LIMIT]). */
fun limitLabel(v: Int): String = if (v >= ScanSettings.NO_LIMIT) "No limit" else v.toString()

/** A per-game cap's chip: the number, or "All". */
fun allLabel(v: Int): String = if (v >= ScanSettings.NO_LIMIT) "All" else v.toString()

/** "12 games", "every game". */
fun gamesLabel(v: Int): String = if (v >= ScanSettings.NO_LIMIT) "every game" else "up to $v games"

/** A scan's time window: "12 hours", "7 days". */
fun windowLabel(hours: Int): String = when {
    hours % 24 == 0 -> (hours / 24).let { if (it == 1) "the next day" else "the next $it days" }
    hours == 1 -> "the next hour"
    else -> "the next $hours hours"
}

/** What PropLine's props cost at [s]'s games per scan (its free key: 1,000 requests a day). */
fun propLineGamesHint(s: ScanSettings): String =
    if (s.propLineGamesPerScan >= ScanSettings.NO_LIMIT) {
        "No limit: every game with props in ${windowLabel(s.bookPropWindowHours)}, one request each, soonest first (a game's props are " +
            "re-used for 2 minutes). A full slate can be 50+ requests a scan: the free 1,000 a day can run out with auto-scan on, " +
            "and then props come from The Odds API (credits), Pinnacle and Kalshi."
    } else {
        "Up to ${s.propLineGamesPerScan} games a scan, soonest first, one request each (a game's props are re-used for 2 minutes)."
    }

/** Key list callbacks from the view model; the file pickers live in [SettingsScreen]. */
class KeyActions(
    val add: (ApiProvider, String) -> Unit = { _, _ -> },
    val remove: (ApiProvider, String) -> Unit = { _, _ -> },
    val moveUp: (ApiProvider, String) -> Unit = { _, _ -> },
    val exportTo: (android.net.Uri) -> Unit = {},
    val importFrom: (android.net.Uri) -> Unit = {},
    /** Settings › API usage opened: the providers that can say what's left (ParlayAPI) are asked, for free. */
    val onUsageShown: () -> Unit = {},
)

/** A provider's keys in rotation order (key 1 is always tried first), with add, remove, reorder. */
@Composable
private fun KeyListEditor(provider: ApiProvider, keys: List<String>, actions: KeyActions, addLabel: String) {
    Column(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        keys.forEachIndexed { i, key ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(UsageViews.mask(key), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                if (i > 0) IconButton(onClick = { actions.moveUp(provider, key) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Try this key earlier") }
                IconButton(onClick = { actions.remove(provider, key) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove key") }
            }
        }
        var newKey by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it.trim() },
                label = { Text(if (keys.isEmpty()) addLabel else "Add another key") },
                singleLine = true,
                // Autocorrect off: an IME "fixing" a random token silently breaks it (found 2026-09-20).
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { actions.add(provider, newKey); newKey = "" }, enabled = newKey.length >= 8) { Text("Add") }
        }
    }
}
