package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.data.scanner.Presets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.tjshea.vigilant.app.AutoScanClock
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.UsageViews
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.BookPropSet
import com.tjshea.vigilant.data.scanner.KeepAwake
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


/**
 * The Settings pages (Tj, 2026-10-02 ~17:55Z: "the settings menu in this app is getting very large and confusing. organize the settings menu intuitively.
 * make it so everything is clear and easy to find"): a home list of these, each with a one-line summary of what it's set to now, opening a page with a
 * back arrow (Android's own Settings works this way). Auto-bet has its own bottom tab ([AutoBetScreen]); the home list links to it.
 */
enum class SettingsPage(val title: String, val about: String) {
    /** Pause, which scanner, which games, the background scan. */
    SCANNING("Scanning", "What Vigilant reads, which games, and checking in the background"),

    /** Push alerts for new +EV bets, and the sharp books' say over them. */
    ALERTS("Alerts", "A notification when a new bet is good enough"),

    /** CrazyNinjaOdds' list: its filters, refresh, and each bet's book check. */
    CNO("CrazyNinjaOdds list", "What CNO's +EV list shows and how often it refreshes"),

    /** The floating widget and the picture-in-picture window. */
    WIDGET("Widget & mini window", "The small window that floats over other apps"),

    /** What Vigilant's own +EV feed shows and how much a scan reads. */
    FEED("+EV feed & scan size", "Vigilant's own scan: what it shows and how much it reads"),

    /** How fair odds are worked out, and where they come from (with keys). */
    FAIR("Fair odds & sources", "How the true odds are worked out, and from which feeds"),

    /** The Novig key, the wallet, bet amounts, limits, bankroll. */
    BETTING("Betting & Novig account", "Your Novig key, wallet, bet amounts and limits"),

    /** Each feed's usage meter, and the keys backup. */
    USAGE("API usage & keys", "Credits left on each feed, and a backup of your keys"),

    /** Diagnostics, the grading check, About. */
    HELP("Diagnostics & about", "Send Claude a report, check grading, version"),
    ;

    /** Pages for a scanner that's asleep are hidden (CNO only hides Vigilant's scan and its feeds; Vigilant only hides CNO's list). */
    fun shownIn(s: ScanSettings): Boolean = when (this) {
        CNO -> s.cnoOn
        FEED, FAIR, USAGE -> s.vigilantOn
        ALERTS -> AppBook.isNovig
        else -> true
    }

    companion object {
        /** The pages shown for [s], in order. */
        fun shown(s: ScanSettings): List<SettingsPage> = entries.filter { it.shownIn(s) }

        fun named(name: String?): SettingsPage? = entries.firstOrNull { it.name == name }
    }
}

/** Each page's one line on the Settings home list: what it's set to now, in a few words (pure, for tests). */
object SettingsSummary {
    fun of(page: SettingsPage, state: UiState): String {
        val s = state.settings
        return when (page) {
            SettingsPage.SCANNING -> listOfNotNull(
                if (s.paused) "Paused" else s.scanner.displayName,
                if (!AppBook.isNovig) null else if (s.startsWithinHours <= 0) "any start time" else "games within ${s.startsWithinHours}h",
                if (!AppBook.isNovig) null else if (BackgroundScan.on(s)) "background every ${ScanSettings.intervalLabel(s.autoScanSeconds)}" else "background off",
            ).joinToString(" · ")
            SettingsPage.ALERTS -> if (s.alertMinEv <= 0.0) "Off" else "${alertLabel(s.alertMinEv)} · sharp books: ${s.sharpAlerts.displayName.lowercase(Locale.US)}"
            SettingsPage.CNO -> s.cnoFilters.let { f ->
                "${f.devig.displayName} · ${f.minBooks}+ books · ${if (f.maxOdds > 0) "up to +${f.maxOdds}" else "any odds"} · ${Format.percent(f.minEv, 0)}+ · every ${secondsLabel(s.cnoRefreshSeconds)}"
            }
            SettingsPage.WIDGET -> (if (s.floatingWidget) "Floating widget" else "Picture-in-picture") + if (s.miniWindow) " · opens when you leave Vigilant" else ""
            SettingsPage.FEED -> "${Format.percent(s.minEvPercent, 1)}+ · ${maxOddsLabel(s.maxOdds).let { if (it == "Any") "any odds" else "up to $it" }} · ${s.families.size} market types · ${s.daysAhead} days ahead"
            SettingsPage.FAIR -> {
                val on = listOf(s.usePinnacle, s.usePolymarket, s.useKalshi, s.usePropLine, s.useParlay, s.useOddsApi).count { it }
                "${s.fairSource.shortName} · $on of 6 feeds on"
            }
            SettingsPage.BETTING -> listOfNotNull(
                when {
                    !AppBook.isNovig -> null
                    state.novig.connection == null -> "Novig key not connected"
                    state.betting.enabled -> "Wallet " + (state.betting.balance?.let { Format.money(it) } ?: "…")
                    else -> "Novig key connected, betting off"
                },
                "bankroll ${Format.money(s.bankroll)}",
                Format.kellyLabel(s.kellyMultiplier),
            ).joinToString(" · ")
            SettingsPage.USAGE -> "Credits left on each feed · keys backup"
            SettingsPage.HELP -> "Share with Claude · Vigilant ${BuildConfig.VERSION_NAME}"
        }
    }

    /** The Auto-bet row on the home list (it opens the Auto-bet tab). */
    fun autoBet(s: ScanSettings): String = when {
        s.autoBetHalted != null -> "Stopped: needs you"
        s.autoBet -> "On" + (Presets.active(s)?.let { " · ${it.name}" } ?: "")
        else -> "Off" + (Presets.active(s)?.let { " · ${it.name}" } ?: "")
    }
}

/**
 * The background scan as one switch (2026-10-02 ~18:10Z, fixing a contradiction: "CNO + Vigilant" with the scanner on CNO only ran half of it, and "CNO"
 * with the scanner on Vigilant only ran nothing). On = it runs whatever the scanner has on; with both scanners on, Vigilant's own scan joins it only when
 * asked ([alsoVigilant]: it spends API credits). Pure, for tests.
 */
object BackgroundScan {
    /** Whether the background scan runs something (pause aside): what the switch shows. */
    fun on(s: ScanSettings): Boolean = (s.autoScan.cno && s.cnoOn) || (s.autoScan.vigilant && s.vigilantOn)

    /** Vigilant's own scan runs in the background too (only meaningful with both scanners on). */
    fun alsoVigilant(s: ScanSettings): Boolean = s.autoScan == AutoScanMode.BOTH

    /** [s] with the switch set to [on]: with Vigilant only, on means its scan (stored as BOTH, which is what runs it). */
    fun set(s: ScanSettings, on: Boolean): ScanSettings = s.copy(
        autoScan = when {
            !on -> AutoScanMode.OFF
            !s.cnoOn -> AutoScanMode.BOTH
            s.autoScan == AutoScanMode.BOTH -> AutoScanMode.BOTH
            else -> AutoScanMode.CNO
        },
    )

    /** [s] with Vigilant's own scan joining the background scan or not (both scanners on). */
    fun setAlsoVigilant(s: ScanSettings, also: Boolean): ScanSettings = s.copy(autoScan = if (also) AutoScanMode.BOTH else AutoScanMode.CNO)
}

private typealias SettingsUpdate = ((ScanSettings) -> ScanSettings) -> Unit

/**
 * Settings: a home list ([SettingsHome]: search, then every page with what it's set to now) and the page picked ([SettingsPage]), with a back arrow and
 * Android's back gesture returning to the list. [page]/[onPage] hoist which page is open (the app keeps it, so the Auto-bet tab can open Betting); left
 * null, the screen keeps it itself, starting at [page]. [onOpenAutoBet] opens the Auto-bet tab (its row on the home list, and search results for it).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    page: SettingsPage? = null,
    onPage: ((SettingsPage?) -> Unit)? = null,
    onOpenAutoBet: (() -> Unit)? = null,
) {
    val s = state.settings
    state.report?.let { ReportDialog(it, reportActions) }
    // A Bet sheet's "Add money" (Tj, 2026-09-29) opens on Betting, where the wallet is.
    var own by rememberSaveable { mutableStateOf((if (state.betting.topUp != null) SettingsPage.BETTING else page)?.name) }
    val go: (SettingsPage?) -> Unit = onPage ?: { own = it?.name }
    val wanted = if (onPage != null) page else SettingsPage.named(own)
    // A page that has gone (its scanner switched off) falls back to the list.
    val current = wanted?.takeIf { it.shownIn(s) }
    val topUp = state.betting.topUp != null
    LaunchedEffect(topUp) { if (topUp && current != SettingsPage.BETTING) go(SettingsPage.BETTING) }
    androidx.activity.compose.BackHandler(enabled = current != null) { go(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.title ?: "Settings", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { go(null) }, modifier = Modifier.testTag("settingsBack")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        // Each page (and the list) has its own scroll, so a page always opens at its top and the list keeps its place.
        val scroll = androidx.compose.runtime.key(current) { rememberScrollState() }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .testTag("settingsPage-${current?.name ?: "HOME"}"),
        ) {
            when (current) {
                null -> SettingsHome(state, onOpen = go, onOpenAutoBet = onOpenAutoBet)
                SettingsPage.SCANNING -> ScanningPage(s, onUpdate)
                SettingsPage.ALERTS -> AlertsPage(state, onUpdate)
                SettingsPage.CNO -> CnoPage(s, onUpdate)
                SettingsPage.WIDGET -> WidgetPage(s, onUpdate)
                SettingsPage.FEED -> FeedTab(s, onUpdate)
                SettingsPage.FAIR -> FairOddsTab(state, keys, onUpdate)
                SettingsPage.BETTING -> BettingTab(state, onUpdate, onNovigConnect, onNovigTest, onNovigDisconnect, bettingActions, onOpenAutoBet)
                SettingsPage.USAGE -> UsageTab(state, keys)
                SettingsPage.HELP -> ToolsTab(state, reportActions)
            }
        }
    }
}

/** The Settings home: a search box, the Auto-bet row (it opens its tab), then every page with what it's set to now. */
@Composable
private fun ColumnScope.SettingsHome(state: UiState, onOpen: (SettingsPage) -> Unit, onOpenAutoBet: (() -> Unit)?) {
    val s = state.settings
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(40) },
        label = { Text("Search settings") },
        placeholder = { Text("e.g. Kelly, alerts, odds, key") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("settingsSearch"),
    )
    if (query.isNotBlank()) {
        val hits = SettingsIndex.search(query, s)
        if (hits.isEmpty()) Hint("Nothing matches \"${query.trim()}\". Try another word, like edge, books, stake or widget.")
        hits.forEach { e ->
            val where = e.page?.title ?: "Auto-bet tab"
            SettingsRow(
                title = e.title,
                summary = "$where · ${e.help}",
                tag = "settingsHit-${e.title}",
                onClick = { if (e.page != null) onOpen(e.page) else onOpenAutoBet?.invoke() },
            )
        }
        return
    }
    if (AppBook.isNovig && s.cnoOn && onOpenAutoBet != null) {
        SettingsRow(
            title = "Auto-bet & presets",
            summary = "Its own tab now · ${SettingsSummary.autoBet(s)}",
            tag = "settingsRow-AUTOBET",
            onClick = onOpenAutoBet,
        )
    }
    SettingsPage.shown(s).forEach { p ->
        SettingsRow(title = p.title, summary = "${p.about}\n${SettingsSummary.of(p, state)}", tag = "settingsRow-${p.name}", onClick = { onOpen(p) })
    }
}

/** One row of the Settings home: its title, what it holds and what it's set to, and an arrow. The whole row is the button. */
@Composable
private fun SettingsRow(title: String, summary: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    androidx.compose.material3.HorizontalDivider()
}

/** Scanning: pause, which scanner, which games, the background scan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ScanningPage(s: ScanSettings, onUpdate: SettingsUpdate) {
    Intro("What Vigilant reads, for which games, and whether it keeps checking while you're in another app or the phone is locked.")
    SectionTitle("Scanner")
    // Tj, 2026-09-28: "Make an option in the app to pause all scanning".
    SwitchRow(
        "Pause all scanning",
        "Stops every read (Vigilant's scans, CrazyNinjaOdds' list, the background scan and auto-bet) until you switch it off. Also the ⏸ button on the " +
            "+EV and CNO tabs and the widget. Opening bets and grading tracked ones still work. A pull to refresh, or the Tracker's Check odds now, resumes.",
        s.paused,
        tag = "pauseSwitch",
    ) { v -> onUpdate { it.copy(paused = v) } }
    Text("Which scanner", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScannerMode.entries, s.scanner, { it.displayName }) { v -> onUpdate { it.copy(scanner = v) } }
    Hint(
        when (s.scanner) {
            ScannerMode.BOTH -> "Two sources of +EV bets: Vigilant's own scan (it works out fair odds itself, when you tap Scan) and CrazyNinjaOdds' list (a website that " +
                "does the same, kept current while on screen). Both show in the widget."
            ScannerMode.VIGILANT -> "Only Vigilant's own scan. CrazyNinjaOdds is never read, so its tab, auto-bet and its alerts are off."
            ScannerMode.CNO -> "Only CrazyNinjaOdds' list. Vigilant's own scan and every feed behind it (${if (AppBook.isNovig) "Novig, " else "PropLine, "}Pinnacle, Polymarket, " +
                "Kalshi, The Odds API) are asleep, in the background too: nothing of theirs loads or spends credits, and their tabs and settings are hidden. " +
                (if (AppBook.isNovig) "The Tracker's Check odds now still reads them for your open bets, so every bet gets its EV now." else "")
        },
    )
    if (AppBook.isNovig) {
        Text("Games starting within", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.STARTS_WITHIN_CHOICES, s.startsWithinHours, ::startsWithinLabel) { v -> onUpdate { it.copy(startsWithinHours = v) } }
        Hint(
            "Every list (+EV, CNO, Games and the widgets) shows only games starting within this window, and Vigilant's scan reads only these games. " +
                "Games already under way still show when live games are on.",
        )
    }

    // ---- Background scan (Vigilant for Novig) ------------------------------
    if (AppBook.isNovig) BackgroundScanSection(s, onUpdate)
}

/** Alerts: the push alerts' edge, the sharp books' say over them, and Android's permission for them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.AlertsPage(state: UiState, onUpdate: SettingsUpdate) {
    val s = state.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    Intro(
        "A notification on your phone for each new bet at least this good, from CrazyNinjaOdds' list (checked by the background scan, or while a list is " +
            "open). Each bet alerts once. Tap it to open Vigilant, or ✓ Placed to track it without opening the app.",
    )
    SectionTitle("When to alert")
    Text("Smallest edge (EV) to alert on", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(ScanSettings.ALERT_MIN_EV_CHOICES, s.alertMinEv, ::alertLabel) { v -> onUpdate { it.copy(alertMinEv = v) } }
    Hint(alertHint(s))
    Shadowed.alertEdge(s)?.let { Warn(it, "alertShadowed") }
    if (s.alertMinEv > 0.0 && !BackgroundScan.on(s)) {
        Warn("The background scan is off (Scanning), so alerts only come while a list or the widget is open on screen.", "alertNoBackground")
        OutlinedButton(onClick = { onUpdate { BackgroundScan.set(it, true) } }, modifier = Modifier.testTag("alertTurnOnBackground")) { Text("Turn on the background scan") }
    }
    if (s.cnoOn) {
        SectionTitle("Sharp-book veto for alerts")
        SharpModeChoice(s.sharpAlerts, tag = "sharpAlerts") { v -> onUpdate { it.copy(sharpAlerts = v) } }
        Hint(SharpConfirmText.modeNote(s.sharpAlerts))
        if (s.sharpAlerts == SharpMode.CONFIRM) SharpConfirmCriteria(state, onUpdate)
    }
    var notify by remember { mutableStateOf(com.tjshea.vigilant.app.ScanService.canNotify(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notify = com.tjshea.vigilant.app.ScanService.canNotify(context)
        onPauseOrDispose { }
    }
    if (s.alertMinEv > 0.0 && !notify) {
        Warn("Notifications are off for Vigilant, so no alert can show.", "alertNotifyOff")
        OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Allow notifications") }
    }
}

/** CrazyNinjaOdds list: what it shows, each bet's book check, refresh. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CnoPage(s: ScanSettings, onUpdate: SettingsUpdate) {
    val f = s.cnoFilters
    val onCno: ((CnoFilters) -> CnoFilters) -> Unit = { t -> onUpdate { it.copy(cnoFilters = t(it.cnoFilters)) } }
    Intro(
        "CrazyNinjaOdds (CNO) is a website that compares ${AppBook.name}'s odds with many sportsbooks' and lists the bets priced better than their true odds " +
            "(+EV). These choices decide what that list shows. A preset (Auto-bet tab) can set the first four at once.",
    )
    SectionTitle("What the list shows")
    Text("How the true odds are worked out", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(CnoDevig.entries, f.devig, { it.displayName }) { v -> onCno { it.copy(devig = v) } }
    Hint(
        "Sportsbooks build their profit (the \"vig\") into their odds; removing it (\"devigging\") gives each side's true chance. " +
            when (f.devig) {
                CnoDevig.CONSERVATIVE -> "Conservative: the most cautious of CNO's estimates. It hides some real edges; what's left is the safest. Recommended."
                CnoDevig.LIQUIDITY_WEIGHTED -> "Liquidity weighted: books that take bigger bets count more (CNO's own default)."
                CnoDevig.MARKET_CONSENSUS -> "Market consensus: every book counts the same."
            },
    )
    Text("Longest odds", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_MAX_ODDS_CHOICES, f.maxOdds, { if (it <= 0) "No cap" else "+$it" }) { v -> onCno { it.copy(maxOdds = v) } }
    Hint(
        if (f.maxOdds > 0) "Favorites and underdogs up to +${f.maxOdds} only. Long shots are where fake edges hide (a small pricing error looks like a big edge)."
        else "Any odds, long shots included. Long shots are where fake edges hide.",
    )
    Text("Fewest books behind the true odds", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_MIN_BOOKS_CHOICES, f.minBooks, { "$it+" }) { v -> onCno { it.copy(minBooks = v) } }
    Hint("How many sportsbooks' prices the true odds are built from. One or two books can be one book's mistake; more books, more trustworthy.")
    Text("Smallest edge (EV) listed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_MIN_EV_CHOICES, f.minEv, { Format.percent(it, 0) }) { v -> onCno { it.copy(minEv = v) } }
    Hint("EV (expected value): how much a bet should return over time, per dollar, above break-even. 3% means about 3¢ per \$1 bet in the long run.")
    SwitchRow(
        "Require a complete sportsbook",
        "Only markets where at least one book prices every side, so the true odds aren't guessed from half a market (CNO's own filter).",
        f.completeBook,
    ) { v -> onCno { it.copy(completeBook = v) } }
    Hint("Always left out: an edge over 20% (almost always a stale line), rows CNO worked out from one side only (⚠️), and rows whose edge doesn't follow from their odds.")

    SectionTitle("Checking each bet")
    // One switch with its stricter option under it (2026-10-02 ~18:10Z: "Only bets the books agree on" read the books with the ✓ switch shown off).
    val checking = s.cnoCheckBooks || s.cnoOnlyAgreed
    SwitchRow(
        "Green ✓ when books agree",
        "Vigilant reads the ${CnoFeed.AGREE_TOP} best bets' sportsbook prices from CNO in the background (one every few seconds) and marks a bet ✓ when " +
            "${CnoBooks.MIN_TWO_SIDED}+ books price both sides and ${CnoBooks.MIN_AGREEING}+ of them each say it's +EV on their own: the edge isn't one book's opinion.",
        checking,
        tag = "cnoCheckBooks",
    ) { v -> onUpdate { it.copy(cnoCheckBooks = v, cnoOnlyAgreed = if (v) it.cnoOnlyAgreed else false) } }
    if (checking) {
        SwitchRow(
            "  Only show ✓ bets",
            "The list, its count and the widget show only bets the books agree on (a bet shows once its books are in). Fewer bets, more reliable ones.",
            s.cnoOnlyAgreed,
            tag = "cnoOnlyAgreed",
        ) { v -> onUpdate { it.copy(cnoOnlyAgreed = v, cnoCheckBooks = true) } }
    }
    // Novig's own order books: Vigilant MGM has no such public feed for BetMGM.
    if (AppBook.isNovig) SwitchRow(
        "Novig's price now",
        "CNO's list can be a minute old. The ${com.tjshea.vigilant.data.cno.NovigLive.LIVE_TOP} best bets show Novig's current price instead, read from Novig " +
            "every ${com.tjshea.vigilant.data.cno.NovigLive.LIVE_EVERY_MS / 1000} s while the list is on screen, with the edge at that price. \"was +117\" means " +
            "it moved since CNO listed it; an orange edge, that it's now under your minimum.",
        s.cnoLivePrices,
    ) { v -> onUpdate { it.copy(cnoLivePrices = v) } }
    SwitchRow(
        "Player teams",
        "Player bets show the team, like D. Schultz (HOU), from ESPN's rosters.",
        s.cnoPlayerTeams,
    ) { v -> onUpdate { it.copy(cnoPlayerTeams = v) } }

    SectionTitle("Refresh")
    Text("Read the list every", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(CnoFeed.REFRESH_CHOICES, s.cnoRefreshSeconds, ::secondsLabel) { v -> onUpdate { it.copy(cnoRefreshSeconds = v) } }
    Hint(refreshHint(s))
    Text("Rows per read", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_ROWS_CHOICES, f.rows, { "$it" }) { v -> onCno { it.copy(rows = v) } }
    Hint("CNO sends its best bets first; more rows means more candidates for the book checks and auto-bet.")

    SectionTitle("Advanced")
    CnoViewEditor(s.cnoViewUrl) { link -> onUpdate { it.copy(cnoViewUrl = link) } }
}

/** Widget & mini window: the floating widget, picture-in-picture, and Vigilant's rescans while it's open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.WidgetPage(s: ScanSettings, onUpdate: SettingsUpdate) {
    Intro("A small window with your best bets that stays on screen over other apps (like ${AppBook.name}), so you can bet without switching back and forth.")
----------------------------------------------------------------
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
                if (s.autoScansCno) " (background auto-scan still reads it every ${ScanSettings.intervalLabel(s.autoScanSeconds)})." else ".",
        )
    }
    // Only with both scanners on: the widget then lists Vigilant's bets beside CNO's, and these rescans keep them fresh.
    if (s.scanner == ScannerMode.BOTH) {
        Text("Vigilant's scan again while the widget is open", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.WIDGET_RESCAN_CHOICES, s.widgetRescanMinutes, { if (it <= 0) "Off" else "$it min" }) { v -> onUpdate { it.copy(widgetRescanMinutes = v) } }
        Hint(
            "The widget lists Vigilant's bets and CNO's together, best edge first; a bet both list shows once. " +
                (if (s.widgetRescanMinutes > 0) "Vigilant scans again every ${s.widgetRescanMinutes} min while the widget or CNO's tab is on screen. Each scan spends API credits (Pinnacle / The Odds API keys)."
                else "Off: Vigilant scans only when you tap Scan (no API credits spent on its own).") +
                " Its bets leave the widget once the odds behind them are too old (${com.tjshea.vigilant.data.scanner.Freshness.LIMIT_TEXT}).",
        )
    }
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
    SwitchRow("Kalshi", "Free. NFL, college football, MLB, NBA, NHL, UFC, tennis (match winners).", s.useKalshi) { v -> onUpdate { it.copy(useKalshi = v) } }
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
            "off; tennis 3 a tour, with Pinnacle's set lines for set spreads and total sets), every book's player props (3 a league) and 1st-half lines (2 a league, only where Novig lists them) " +
            "each scan, paced to a day's share of your plan (background auto-scans use half of it at most), plus Pinnacle's closing " +
            "lines for your CLV (the last 300 credits are kept for them). Free with it: injury tags on prop bets (1 credit a league when a " +
            "player isn't in its props), Pinnacle's line moves, the credits-a-day chart. Only on a tap: its own picks at Novig (+EV tab, " +
            "10 a league) and a bet's second opinion (5). A free key is kept for closing lines. Off, spent or gone: the other feeds carry on.",
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

        SectionTitle("Sportsbooks for fair odds (${s.referenceBooks.size})")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TheOddsApiClient.KNOWN_BOOKMAKERS.forEach { (key, title) ->
                val on = key in s.referenceBooks
                FilterChip(
                    selected = on,
                    onClick = { onUpdate { it.copy(referenceBooks = if (on) it.referenceBooks - key else it.referenceBooks + key) } },
                    label = { Text(title) },
                )
            }
        }
        Hint(
            "Read from PropLine first, every book picked at no extra cost, and from The Odds API only for what PropLine couldn't give: " +
                "there, the first 10 picked, one credit per market per league and one per prop type per game for props. PropLine " +
                "carries all of them except Caesars, theScore Bet, Betfair, Bally Bet and MyBookie, which count only when The Odds " +
                "API is asked (a book of those picked as sharp above keeps it asked every scan). ParlayAPI reads its own books: " +
                "every sportsbook it has for props, and its sharp ones for game lines. LowVig is BetOnline's line with less juice: " +
                "picking both counts that line twice.",
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
                com.tjshea.vigilant.data.novig.SlipStake.KELLY ->
                    "Every bet opens with its own Kelly stake entered, to the cent: bankroll × the Kelly fraction above × (fair chance − price) ÷ (1 − price), " +
                        "so it changes with each bet's odds and edge, held to what Novig has for sale at +EV."
                com.tjshea.vigilant.data.novig.SlipStake.CUSTOM -> "Every bet you tap opens with this amount entered."
            } + " From the +EV tab, the widget, the CNO tab and alerts. You still confirm the bet in Novig." +
                (if (s.slipStake == com.tjshea.vigilant.data.novig.SlipStake.KELLY || s.slipStake == com.tjshea.vigilant.data.novig.SlipStake.CUSTOM)
                    " Vigilant's own Bet sheet (the wallet) opens with the same amount, within your per-bet limit." else ""),
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
        if (state.novig.connection != null) {
            NovigBettingSection(state.betting, s, bettingActions, onUpdate, savedKey = state.novig.managementKey)
            // Auto-bet (Tj, 2026-10-01): off until turned on; places CNO's bets through the wallet above.
            val context = androidx.compose.ui.platform.LocalContext.current
            AutoBetSection(
                state,
                notificationsBlocked = com.tjshea.vigilant.app.AutoBetNotes.blocked(context),
                onTestNotification = { (context.applicationContext as? android.app.Application)?.let(com.tjshea.vigilant.app.AutoBetNotes::sample) ?: false },
                onUpdate = onUpdate,
            )
        }
        // Sharp-book confirmation (Tj, 2026-10-02): for the auto-bet and for CNO's push alerts, so it doesn't need the betting key.
        SharpConfirmSection(state, onUpdate)
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
        "Tap Share with Claude: it makes one file with everything the app has recorded (errors with the code that threw them, every call's speed and failures by host, API usage, " +
            "scan and cycle timings, what auto-bet and the sharp check decided, the app's own log) and a ranked list of what to fix and speed up, then opens Android's Share sheet: " +
            "pick Claude. The next file is compared with this one, so each upload shows whether the last change worked. It never has a key in it.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Button(onClick = reportActions.onShare, modifier = Modifier.testTag("shareDiagnostics")) { Text("Share with Claude") }
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
            "Polymarket, Kalshi, ParlayAPI, PropLine, The Odds API · CNO scanner: crazyninjaodds.com (player teams: ESPN). Vigilant's scan " +
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
        ChoiceChips(ScanSettings.AUTO_SCAN_SECONDS_CHOICES, s.autoScanSeconds, ScanSettings::intervalLabel) { v -> onUpdate { it.copy(autoScanSeconds = v) } }
    }
    Hint(autoScanHint(s))
    if (s.autoScan != AutoScanMode.OFF) {
        SwitchRow("Keep awake (screen stays off)", keepAwakeHint(s), s.autoScanKeepAwake) { v -> onUpdate { it.copy(autoScanKeepAwake = v) } }
    }
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
        Hint(batteryHint(unrestricted = false))
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) { Text("Let Vigilant run in the background") }
        // The prompt above can be refused or ignored by a phone's own battery manager: the app's own page has "App battery usage".
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }) { Text("Open Vigilant's app settings") }
    } else if (s.autoScan != AutoScanMode.OFF) {
        Hint(batteryHint(unrestricted = true))
    }
}

/** What the keep-awake switch does at these settings (pure, for tests). */
fun keepAwakeHint(s: ScanSettings): String = when {
    KeepAwake.active(s) ->
        "On: the screen can stay off and locked, but the CPU stays awake, so scans and auto-bets keep to their ${ScanSettings.intervalLabel(s.autoScanSeconds)} schedule while the phone sits idle. " +
            "Uses more battery (best plugged in), and the notification stays up."
    s.autoScanKeepAwake -> "On, but not needed at ${ScanSettings.intervalLabel(s.autoScanSeconds)}: an alarm is on time at 9 minutes or slower, and the CPU sleeps between scans."
    s.autoScanSeconds < KeepAwake.ALARM_ONLY_BELOW_SECONDS ->
        "Off: with the screen off and the phone still, Android may run each alarm-driven scan only about every 9 minutes, whatever the ${ScanSettings.intervalLabel(s.autoScanSeconds)} above says. Saves battery."
    else -> "Off: the CPU sleeps between scans, each woken by an alarm, which is on time at ${ScanSettings.intervalLabel(s.autoScanSeconds)}."
}

/** The battery line under the auto-scan switches (pure, for tests). */
fun batteryHint(unrestricted: Boolean): String =
    if (unrestricted) "Battery: Unrestricted for Vigilant, so Android's battery manager won't stop it in the background."
    else "Android may stop Vigilant while the phone sleeps unless its battery setting is \"Unrestricted\" (on a Moto: Settings › Apps › Vigilant › App battery usage › Unrestricted)."

private fun ignoresBatteryLimits(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

fun alertLabel(ev: Double): String = if (ev <= 0.0) "Off" else "${Math.round(ev * 100)}%+"

/** What background auto-scan does at these settings. */
fun autoScanHint(s: ScanSettings): String {
    if (s.autoScan == AutoScanMode.OFF) return "Off: Vigilant scans only when you tap Scan, and CrazyNinjaOdds is read only while its tab or a widget is on screen."
    val every = ScanSettings.intervalLabel(s.autoScanSeconds)
    val perDay = "%,d".format(java.util.Locale.US, 86_400 / s.autoScanSeconds.coerceAtLeast(1))
    val vigilantEvery = ScanSettings.vigilantEverySeconds(s.autoScanSeconds)
    val vigilantPerDay = 86_400 / vigilantEvery
    val cnoPart = "CrazyNinjaOdds' list, then Novig's price now and every book's odds for its best bets (the green check's reads), and the books of your " +
        "open bets starting within the hour (the Tracker's closing line, for CLV" +
        (if (AutoScanClock.closingFreshMs(s.autoScanSeconds) != null) "; in their last 15 minutes, once a minute each at most" else "") + ")"
    val vigilantPart = "Vigilant's own scan exactly as the Scan button runs it (" +
        (if (s.maxBooksPerScan >= ScanSettings.NO_LIMIT) "every priced line in ${windowLabel(s.scanWindowHours)}: " else "${s.maxBooksPerScan} Novig prices at most: ") +
        "${scanTime(s.maxBooksPerScan)}). Each scan spends API credits like a tap on Scan: $vigilantPerDay scans a day at this setting" +
        if (vigilantEvery != s.autoScanSeconds) " (it starts at most every ${ScanSettings.intervalLabel(vigilantEvery)}, however fast CNO is read)" else ""
    val fast = if (s.autoScanSeconds < 60) " Under a minute apart is constant background work: more battery." + (if (KeepAwake.active(s)) "" else " Android may space scans out while the phone sits idle (Keep awake, below, prevents that).") else ""
    val notification = " A quiet notification shows while it's on (Scan now, Stop)." +
        if (s.autoBetsNow) " Auto-bet is on: bets that pass your criteria are placed with each CNO check (Settings › Betting)." else ""
    return when {
        s.autoScansCno && s.autoScansVigilant -> "Every $every, with Vigilant open or closed: $cnoPart, then $vigilantPart.$notification$fast"
        s.autoScansCno -> "Every $every, with Vigilant open or closed: $cnoPart.$notification About $perDay reads of CNO a day, each well under a second of work.$fast" +
            if (s.autoScan.vigilant) " Vigilant's scan is skipped, because the scanner above is on CNO only: no API credits are spent in the background." else ""
        s.autoScansVigilant -> "Every $every, with Vigilant open or closed: $vigilantPart.$notification CrazyNinjaOdds isn't read, because the scanner above is on Vigilant only.$fast"
        s.paused -> "Paused with everything else: resume scanning (the ⏸ button) to run again."
        else -> "Nothing runs in the background: the scanner above is on ${s.scanner.displayName}, which leaves nothing for this choice to read. Pick a scanner that is on."
    }
}

/** Which bets alert, at these settings. */
fun alertHint(s: ScanSettings): String =
    if (s.alertMinEv <= 0.0) "Off: no alerts." else "A notification for each new bet at ${Math.round(s.alertMinEv * 100)}% EV or better that several books agree on " +
        "(${CnoBooks.MIN_TWO_SIDED}+ books price both sides and ${CnoBooks.MIN_AGREEING}+ of them alone make it +EV), found by a background scan " +
        "or a scan you left running. Tap it to open Vigilant; its ✓ Placed button tracks the bet from the notification, without " +
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
            "With a Novig key, up to 2,000 arrive by live feed once the other books' odds are in (30 seconds at most) and the rest by " +
            "request (about 16 a second); on " +
            "Novig's public prices about 300 a minute. Other books' odds are read as the scan starts and last 5 minutes (10 for games " +
            "over 3 hours off), so a scan never runs past about 8 minutes: lines it can't reach by then are read first next scan."
    }
    return "$books is ${scanTime(books)} on Novig's public prices; with a Novig key connected, the lines not yet read (up to 2,000, what " +
        "its live feed watches at once) arrive by live feed once the other books' odds are in (30 seconds at most), the rest by request. " +
        "A scan that runs long leaves lines whose other books' odds " +
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

/**
 * Key list callbacks from the view model; the file pickers live in [SettingsScreen]. A data class, so a new copy of the same callbacks (the root makes
 * one with every state, 3 a second mid-scan) is equal and the sections skip their redraw, like [BetActions] and [BettingActions].
 */
data class KeyActions(
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
