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
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.cno.CnoScope
import com.tjshea.vigilant.data.cno.CnoLeagues
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.LiveScanText
import com.tjshea.vigilant.app.BuildConfig
import com.tjshea.vigilant.app.StudyText
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
import com.tjshea.vigilant.engine.Odds
import com.tjshea.vigilant.engine.FairSettings
import com.tjshea.vigilant.engine.FairSource
import java.util.Locale
import kotlin.math.roundToInt


/**
 * The headings the Settings home list is split under (Tj, 2026-10-07: "organize the settings and simplify them. Sometimes it's hard for me to find things because there are so many
 * settings"): what Vigilant does with a bet it found comes first (the Auto-bet and Bids tabs open from here too), then how it finds them, then the screen, then the data and help.
 */
enum class SettingsGroup(val title: String, val about: String) {
    // Regrouped for v1.0 (Tj, 2026-10-18, TCL122-9: "only settings should be in settings. organize the settings"): the engine and money first, then what is scanned, then live, then the phone itself.
    BET("Engine & money", "The GitHub engine, your Novig account, bet and bid rules, limits and alerts"),
    FIND("Odds & scanning", "What is scanned, how fair odds are worked out, what the lists show, and every API"),
    LIVE("Live", "The live taker and live bids (live games and where live runs are on the Live tab)"),
    SCREEN("On screen", "The floating widget"),
    DATA("Data & help", "Research recorders, diagnostics and the version"),
}

/**
 * The Settings pages (Tj, 2026-10-02 ~17:55Z: "the settings menu in this app is getting very large and confusing. organize the settings menu intuitively.
 * make it so everything is clear and easy to find"): a home list of these, each with a one-line summary of what it's set to now, opening a page with a
 * back arrow (Android's own Settings works this way). Auto-bet has its own bottom tab ([AutoBetScreen]); the home list links to it.
 */
enum class SettingsPage(val title: String, val about: String, val group: SettingsGroup) {
    // The order is the order on the home list: its groups, and inside each the pages as listed here. The APIs' own pages follow APIS (they open from it).

    /** The scan on GitHub's computers, the token, and the permanent saving of bets, bids and history to GitHub (Tj, 2026-10-14). */
    CLOUD("GitHub engine (cloud scan)", "The engine on GitHub's computers: on/off, its hours, engine trading, keys sent to GitHub, and saving history", SettingsGroup.BET),

    /** Push alerts for new +EV bets, and the sharp books' say over them. */
    ALERTS("Alerts", "A notification when a new bet is good enough", SettingsGroup.BET),

    /** The Novig key, the wallet, bet amounts, limits, bankroll. */
    BETTING("Betting & Novig account", "Your Novig key, wallet, bet amounts and limits", SettingsGroup.BET),

    /** Every protection against bad bets and bids in one list, with a switch each and one button to turn them all on (Tj, 2026-10-14). */
    SAFEGUARDS("Safeguards", "Every protection against bad bets and bids, on or off, and one button for all", SettingsGroup.BET),

    /** The GitHub engine at a glance (Tj, 2026-10-15): state, odds age, wallet, which APIs work, usage meters and trends, synced from the engine. */
    DASHBOARD("Engine dashboard", "The GitHub engine at a glance: is it running, which APIs work, what each has left, graphs of the last scans", SettingsGroup.FIND),

    /** Chat with whichever Claude session is working, through the repo (TCL29, Tj 2026-10-15). */
    CHAT("Chat with Claude", "Talk to Claude in the Claude tab: one account (mirage) answers, and your message wakes it when it is away", SettingsGroup.FIND),

    /** Everything Claude or the engine wants Tj to know: recommendations, bets placed, settings changed from GitHub (TCL28). */
    MESSAGES("Messages from Claude", "Alerts, API problems and recommendations from Claude and the engine (bets and bids are on the Engine tab)", SettingsGroup.FIND),

    /** Pause, which scanner, which games, the background scan. */
    SCANNING("Scanning", "What Vigilant reads, which games, and checking in the background", SettingsGroup.FIND),

    /** CrazyNinjaOdds' list: its filters, refresh, and each bet's book check. */
    CNO("CrazyNinjaOdds list", "What CNO's +EV list shows and how often it refreshes", SettingsGroup.FIND),

    /** What Vigilant's own +EV feed shows and how much a scan reads. */
    FEED("+EV feed & scan size", "Vigilant's own scan: what it shows and how much it reads", SettingsGroup.FIND),

    /** How fair odds are worked out: the method, the sharp books, the guards, the sportsbooks and their props. The feeds and keys are under [APIS]. */
    FAIR("Fair odds", "How the true odds are worked out: the method, the sharp books, the sportsbooks and their props", SettingsGroup.FIND),

    /** Every API in one place: its switch, a Test button, its keys and what is left on them, and a button to its own page for the keys and options (Tj, 2026-10-11; one home for them since v0.89.35). */
    APIS("APIs & keys", "Turn any API on or off, add its keys, test them and see what is left on each; the odds feeds, their options and a backup of your keys", SettingsGroup.FIND),

    /** SportsGameOdds Pro: the key(s), the one switch that makes it the source for everything it can do, and what it replaces (Tj, 2026-10-09). A page of [APIS]. */
    SGO("SportsGameOdds Pro", "One paid feed for odds, props, closing lines and results, read alongside every other API you have on", SettingsGroup.FIND),

    /** BoltOdds Pro: the odds WebSocket (lines and props) as a fair-line source, free to read once connected (Tj, 2026-10-12). A page of [APIS]. */
    BOLTODDS("BoltOdds Pro", "Real-time odds pushed over a WebSocket from many sportsbooks: lines and player props, read alongside every other API you have on", SettingsGroup.FIND),

    /** Kalshi: the exchange's prices (free to read), and with Tj's API key a real-time socket and a bigger read budget (Tj, 2026-10-13; KALSHI_API.md). A page of [APIS]. */
    KALSHI("Kalshi", "Exchange prices, free to read; your API key adds a real-time socket and a bigger read budget", SettingsGroup.FIND),

    /** OddsPapi v5: the key(s), the one switch that makes it the source for everything it can do, and what it replaces (Tj, 2026-10-09). A page of [APIS]. */
    ODDSPAPI("OddsPapi", "v5 (a trial or contract key): a second paid feed for odds, props, closing lines (CLV for any bet) and results, read alongside every other API you have on", SettingsGroup.FIND),

    /** Unabated's FREE API: soft-book lines and props at no credit cost, the last source in line (Tj, 2026-10-11). A page of [APIS]. */
    SXBET("SX Bet", "An exchange read as a sharp source: public markets, free read-only key for the prices", SettingsGroup.FIND),
    UNABATED("Unabated (free)", "Free lines and props from DraftKings, FanDuel, MGM, Caesars and more; fills in what the other feeds miss", SettingsGroup.FIND),

    /** OddsPapi's FREE v4 plan: Pinnacle closing lines for CLV from free historical odds (Tj, 2026-10-11). A page of [APIS]. */
    ODDSPAPI_FREE("OddsPapi Free (v4)", "Your free OddsPapi key: Pinnacle closing lines for CLV, spending almost none of its monthly requests", SettingsGroup.FIND),

    /** Pinnacle through PinnWire and pinnapi: the switch for both and their keys (moved out of Fair odds in v0.89.35). A page of [APIS]. */
    PINNACLE("Pinnacle (PinnWire, pinnapi)", "Pinnacle's board and player props: the keys and their order", SettingsGroup.FIND),

    /** PropLine: its keys and options. A page of [APIS]. */
    PROPLINE("PropLine", "30 books' lines and props: keys and options", SettingsGroup.FIND),

    /** ParlayAPI: its keys. A page of [APIS]. */
    PARLAY("ParlayAPI", "Pinnacle and 30 books, props, injuries and closing lines: keys", SettingsGroup.FIND),

    /** The Odds API: its keys and options. A page of [APIS]. */
    ODDS_API("The Odds API", "Sportsbook lines and props: keys and options", SettingsGroup.FIND),

    /** Each feed's usage charts and the keys backup. A page of [APIS]. */
    USAGE("Usage details & keys backup", "Credits left on each feed, the day-by-day chart, and a backup of your keys", SettingsGroup.FIND),

    /** Pinnacle's live price on the Pinnodds WebSocket against Novig's lagging quote: the key, the switches and the limits (Tj, 2026-10-08). */
    PINNODDS("Live taker", "Live bets against a live fair (BoltOdds' average, SX Bet's mid, or a Pinnodds socket): the feed, paper or real, limits", SettingsGroup.LIVE),

    /** Post-only bids on Novig's live lines, priced under Pinnacle's live fair: paper, then real money, with presets and every number as a chip or a box (Tj, 2026-10-10). */
    LIVEBIDS("Live bids", "Bids on live games, kept fresh and pulled fast: presets, stake and every safeguard", SettingsGroup.LIVE),

    /** The floating widget and the picture-in-picture window. */
    WIDGET("Widget & mini window", "The small window that floats over other apps", SettingsGroup.SCREEN),

    /** Research mode: one switch for every recorder that places nothing, the paper lab, and the one file to send Claude (Tj, 2026-10-09). */
    RESEARCH("Research mode", "One switch for every recorder that places nothing, and the file to send Claude", SettingsGroup.DATA),

    /** Diagnostics, the grading check, About. */
    HELP("Diagnostics & about", "Send Claude a report, check grading, version", SettingsGroup.DATA),
    ;

    /** The page this one belongs to, or null for a page on the Settings home list: the APIs' own pages and their usage details open from APIs & keys and go back to it. */
    val parent: SettingsPage?
        get() = when (this) {
            SGO, BOLTODDS, KALSHI, SXBET, ODDSPAPI, UNABATED, ODDSPAPI_FREE, PINNACLE, PROPLINE, PARLAY, ODDS_API, USAGE -> APIS
            else -> null
        }

    /** Pages for a scanner that's asleep are hidden (CNO only hides Vigilant's scan and its feeds; Vigilant only hides CNO's list). */
    fun shownIn(s: ScanSettings): Boolean = when (this) {
        CNO -> s.cnoOn
        FEED, FAIR -> s.vigilantOn
        ALERTS, RESEARCH, SGO, BOLTODDS, KALSHI, SXBET, ODDSPAPI, UNABATED, ODDSPAPI_FREE -> AppBook.isNovig
        APIS -> true
        // Central sections of their own since 2026-10-16 (the Engine and Claude tabs): not Settings pages any more.
        DASHBOARD, CHAT, MESSAGES -> false
        PINNODDS, LIVEBIDS -> AppBook.isNovig && !com.tjshea.vigilant.data.scanner.Dormant.PINNODDS   // Tj, 2026-10-09: "Stop using the pinnodds API"
        else -> true
    }

    companion object {
        /** Every page that can be opened for [s], in order (the home list's and the APIs' own pages). */
        fun shown(s: ScanSettings): List<SettingsPage> = entries.filter { it.shownIn(s) }

        /** The pages on the Settings home list for [s]: [shown] without the pages that open from another page ([parent]). */
        fun home(s: ScanSettings): List<SettingsPage> = shown(s).filter { it.parent == null }

        fun named(name: String?): SettingsPage? = entries.firstOrNull { it.name == name }
    }
}

/** Each page's one line on the Settings home list: what it's set to now, in a few words (pure, for tests). */
object SettingsSummary {
    fun of(page: SettingsPage, state: UiState): String {
        val s = state.settings
        return when (page) {
            SettingsPage.SCANNING -> listOfNotNull(
                if (s.killed) "STOPPED" else if (s.paused) "Paused" else s.scanner.displayName,
                if (!AppBook.isNovig) null else if (s.startsWithinHours <= 0) "any start time" else "games within ${s.startsWithinHours}h",
                if (!AppBook.isNovig) null else if (BackgroundScan.on(s)) "background every ${ScanSettings.intervalLabel(s.autoScanSeconds)}" else "background off",
            ).joinToString(" · ")
            SettingsPage.ALERTS -> if (s.alertMinEv <= 0.0) "Off" else "${alertLabel(s.alertMinEv)} · sharp books: ${s.sharpAlerts.displayName.lowercase(Locale.US)}" +
                // The veto's bar (v0.56.0, RESEARCH.md §72) is part of what the veto does.
                (if (s.sharpAlerts == com.tjshea.vigilant.data.scanner.SharpMode.VETO && s.sharpVetoMinEv > 0.0) " under ${AutoBetText.evLabel(s.sharpVetoMinEv)}" else "")
            SettingsPage.CNO -> s.cnoFilters.let { f ->
                "${f.devig.displayName} · ${f.minBooks}+ books · ${if (f.maxOdds > 0) "up to +${f.maxOdds}" else "any odds"} · ${Format.percent(f.minEv, 0)}+ · every ${secondsLabel(s.cnoRefreshSeconds)}" +
                    (CnoScopeText.tabSuffix(f.scope)?.let { " · $it" } ?: "")
            }
            SettingsPage.WIDGET -> (if (s.floatingWidget) "Floating widget" else "Picture-in-picture") + if (s.miniWindow) " · opens when you leave Vigilant" else ""
            SettingsPage.FEED -> "${pctShort(s.minEvPercent)}+ · ${maxOddsLabel(s.maxOdds).let { if (it == "Any") "any odds" else "up to $it" }} · ${s.families.size} market types · ${s.daysAhead} days ahead"
            SettingsPage.FAIR -> "${s.fairSource.shortName} · ${s.devigMethod.displayName.lowercase(Locale.US)} devig · at least ${s.minBooks} books"
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
            SettingsPage.SAFEGUARDS -> com.tjshea.vigilant.app.Safeguards.off(s).let { off -> if (off.isEmpty()) "All ${com.tjshea.vigilant.app.Safeguards.ALL.size} safeguards on" else "${off.size} of ${com.tjshea.vigilant.app.Safeguards.ALL.size} OFF" }
            SettingsPage.PINNODDS -> when {
                state.pinnoddsKeys.isEmpty() && !s.pinnWebsite.noPinnKey -> "No Pinnodds key saved"
                !s.pinnLive -> "Off" + if (s.pinnWebsite.website) " (Pinnacle website feed)" else if (s.pinnWebsite.bolt) " (BoltOdds soft-book average)" else if (s.pinnWebsite.sx) " (SX Bet exchange mid)" else ""
                s.pinnLiveHalted != null -> "Stopped"
                s.pinnLiveBet -> "On: real bets, ${com.tjshea.vigilant.app.PinnText.money(s.pinnLiveStake)} a bet"
                else -> "On: paper only (nothing is sent)"
            }
            SettingsPage.LIVEBIDS -> when {
                !s.liveBid -> "Off"
                s.liveBidHalted != null -> "Stopped"
                s.liveBidReal -> "On: real money, ${com.tjshea.vigilant.app.LiveBidText.preset(s)}"
                else -> "On: paper only (nothing is sent), ${com.tjshea.vigilant.app.LiveBidText.preset(s)}"
            }
            SettingsPage.CHAT -> state.chat.unread.let { if (it > 0) "$it new from Claude" else "In the Claude tab: one Claude account (mirage) answers" }
            SettingsPage.MESSAGES -> state.claudeInbox.unread.let { if (it > 0) "$it unread" else "No new messages" }
            SettingsPage.DASHBOARD -> {
                val h = com.tjshea.vigilant.data.cloud.DashboardModel.headline(state.cloudUi.status, System.currentTimeMillis(), state.settings.cloudHours)
                if (!state.cloudUi.hasToken) "No GitHub token saved" else h.title + (if (h.detail.isNotBlank() && h.title != "Not connected") ": " + h.detail.take(60) else "")
            }
            SettingsPage.CLOUD -> when {
                !state.cloudUi.hasToken -> "No GitHub token saved"
                s.cloudScan && !s.cloudDrives -> "Paused with scanning"
                s.cloudScan -> "On: " + state.cloudUi.line
                else -> "Off (token saved, history saved to GitHub: ${if (s.cloudSave) "on" else "off"})"
            }
            SettingsPage.SGO -> when {
                state.sgoKeys.isEmpty() -> "No key saved"
                s.sgoPro -> "On: ${state.sgoKeys.size} key${if (state.sgoKeys.size == 1) "" else "s"}, read alongside every other API that is on"
                else -> "Off (key saved)"
            }
            SettingsPage.ODDSPAPI -> when {
                state.opKeys.isEmpty() -> "No key saved"
                s.oddsPapi -> "On: ${state.opKeys.size} key${if (state.opKeys.size == 1) "" else "s"}, read alongside every other API that is on"
                else -> "Off (key saved)"
            }
            SettingsPage.APIS -> {
                val on = com.tjshea.vigilant.app.ApiEntry.active(s).size
                val keys = com.tjshea.vigilant.data.keys.ApiProvider.entries.sumOf { state.keysOf(it).size }
                "$on of ${com.tjshea.vigilant.app.ApiEntry.entries.size} on · $keys key${if (keys == 1) "" else "s"} saved"
            }
            SettingsPage.PINNACLE -> keyedSummary(s.usePinnacle && (s.gates.usePinnwire || s.gates.usePinnapi), state.pinnwireKeys.size + state.pinnapiKeys.size)
            SettingsPage.PROPLINE -> keyedSummary(s.usePropLine, state.proplineKeys.size)
            SettingsPage.PARLAY -> keyedSummary(s.useParlay, state.parlayKeys.size)
            SettingsPage.ODDS_API -> keyedSummary(s.useOddsApi, state.oddsApiKeys.size)
            SettingsPage.BOLTODDS -> when {
                state.boltKeys.isEmpty() -> "No key saved"
                s.gates.useBoltOdds -> "On: real-time lines and props over a WebSocket"
                else -> "Off (key saved)"
            }
            SettingsPage.KALSHI -> when {
                !s.useKalshi -> "Off"
                state.kalshiHasKey && s.gates.kalshiSocket -> "On: signed reads and a real-time socket"
                state.kalshiHasKey -> "On: signed reads, socket off"
                else -> "On: public reads (no key saved)"
            }
            SettingsPage.SXBET -> when {
                state.sxKeys.isEmpty() -> "No key saved"
                s.gates.useSxBet -> "On: sharp exchange odds"
                else -> "Off (key saved)"
            }
            SettingsPage.UNABATED -> when {
                state.unKeys.isEmpty() -> "No key saved"
                s.unabated -> "On: free lines and props, last in line"
                else -> "Off (key saved)"
            }
            SettingsPage.ODDSPAPI_FREE -> when {
                state.op4Keys.isEmpty() -> "No key saved"
                s.oddsPapiFree -> "On: Pinnacle closing lines for CLV"
                else -> "Off (key saved)"
            }
            SettingsPage.RESEARCH -> if (s.researchMode) "On: every recorder (nothing is bet)" else if (s.altLab) "Paper lab on" else "Off"
            SettingsPage.USAGE -> {
                val keys = state.pinnwireKeys.size + state.pinnapiKeys.size + state.proplineKeys.size + state.parlayKeys.size + state.oddsApiKeys.size
                if (keys == 0) "No feed keys saved" else "$keys feed key${if (keys == 1) "" else "s"} saved"
            }
            SettingsPage.HELP -> "Diagnostics · Vigilant ${BuildConfig.VERSION_NAME}"
        }
    }

    /** "On: 3 keys" / "Off (2 keys saved)" / "No key saved": the line for an API's own page. */
    private fun keyedSummary(on: Boolean, keys: Int): String = when {
        keys == 0 -> "No key saved"
        on -> "On: $keys key${if (keys == 1) "" else "s"}"
        else -> "Off ($keys key${if (keys == 1) "" else "s"} saved)"
    }

    /** "1%", "2.5%": a percent with a decimal only when it has one. */
    private fun pctShort(v: Double): String = Math.round(v * 1000).let { t -> if (t % 10 == 0L) "${t / 10}%" else String.format(Locale.US, "%.1f%%", t / 10.0) }

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
    /** Opens the Bids tab (its row on the home list, and search results for it). */
    onOpenBids: (() -> Unit)? = null,
    onOpenCno: (() -> Unit)? = null,
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
    androidx.activity.compose.BackHandler(enabled = current != null) { go(current?.parent) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.title ?: "Settings", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    if (current != null) {
                        IconButton(onClick = { go(current.parent) }, modifier = Modifier.testTag("settingsBack")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (current.parent != null) "Back to ${current.parent!!.title}" else "Back to Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        // Each page (and the list) has its own scroll, so a page always opens at its top and the list keeps its place.
        val scroll = androidx.compose.runtime.key(current) { rememberScrollState() }
        // Pull to refresh on the pages that show API usage (TCL122-8: "if I go to a page that shows the API usage, I should be able to pull down to refresh and it shows a fresh read on each api usage").
        val usagePage = current == SettingsPage.APIS || current == SettingsPage.USAGE || current?.parent == SettingsPage.APIS
        VigilantPullToRefresh(busy = state.usageBusy || state.cloudUi.refreshing, onRefresh = reportActions.onRefreshUsage, modifier = Modifier.padding(padding).fillMaxSize(), enabled = usagePage) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .testTag("settingsPage-${current?.name ?: "HOME"}"),
        ) {
            when (current) {
                null -> SettingsHome(state, onOpen = go, onOpenAutoBet = onOpenAutoBet, onOpenBids = onOpenBids, onOpenCno = onOpenCno)
                SettingsPage.SCANNING -> ScanningPage(s, onUpdate)
                SettingsPage.DASHBOARD -> DashboardPage(state, onOpenCloudSettings = { go(SettingsPage.CLOUD) })
                // Moved out of Settings (Tj, 2026-10-16): the Engine and Claude tabs.
                SettingsPage.CHAT, SettingsPage.MESSAGES -> Unit
                SettingsPage.CLOUD -> CloudPage(state, reportActions, onUpdate)
                SettingsPage.ALERTS -> AlertsPage(state, onUpdate)
                SettingsPage.CNO -> CnoPage(s, onUpdate)
                SettingsPage.WIDGET -> WidgetPage(s, onUpdate)
                SettingsPage.FEED -> FeedTab(s, onUpdate)
                SettingsPage.FAIR -> FairOddsTab(state, onUpdate, onOpenPage = go)
                SettingsPage.BETTING -> BettingTab(state, onUpdate, onNovigConnect, onNovigTest, onNovigDisconnect, bettingActions, onOpenAutoBet)
                SettingsPage.SAFEGUARDS -> SafeguardsPage(s, onUpdate)
                SettingsPage.PINNODDS -> PinnLiveSection(state, keys, reportActions, onUpdate)
                SettingsPage.LIVEBIDS -> LiveBidPage(state, reportActions, onUpdate)
                SettingsPage.RESEARCH -> ResearchPage(state, reportActions, onUpdate)
                SettingsPage.SGO -> SgoPage(state, keys, reportActions, onUpdate)
                SettingsPage.ODDSPAPI -> OddsPapiPage(state, keys, reportActions, onUpdate)
                SettingsPage.APIS -> ApisPage(state, reportActions, onUpdate, onOpenPage = go)
                SettingsPage.PINNACLE -> PinnaclePage(state, keys, onUpdate)
                SettingsPage.PROPLINE -> PropLinePage(state, keys, onUpdate)
                SettingsPage.PARLAY -> ParlayPage(state, keys, onUpdate)
                SettingsPage.ODDS_API -> OddsApiPage(state, keys, onUpdate)
                SettingsPage.SXBET -> SxBetPage(state, keys, onUpdate)
                SettingsPage.UNABATED -> UnabatedPage(state, keys, reportActions, onUpdate)
                SettingsPage.BOLTODDS -> BoltOddsPage(state, keys, reportActions, onUpdate)
                SettingsPage.KALSHI -> KalshiPage(state, reportActions, onUpdate)
                SettingsPage.ODDSPAPI_FREE -> OddsPapiFreePage(state, keys, reportActions, onUpdate)
                SettingsPage.USAGE -> UsageTab(state, keys)
                SettingsPage.HELP -> ToolsTab(state, reportActions, onUpdate)
            }
        }
        }
    }
}

/** The Settings home: a search box, the Auto-bet row (it opens its tab), then every page with what it's set to now. */
@Composable
private fun ColumnScope.SettingsHome(state: UiState, onOpen: (SettingsPage) -> Unit, onOpenAutoBet: (() -> Unit)?, onOpenBids: (() -> Unit)?, onOpenCno: (() -> Unit)? = null) {
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
            SettingsRow(
                title = e.title,
                summary = "${e.where} · ${e.help}",
                tag = "settingsHit-${e.title}",
                onClick = { if (e.bids) onOpenBids?.invoke() else if (e.page != null) onOpen(e.page) else onOpenAutoBet?.invoke() },
            )
        }
        return
    }
    val shown = SettingsPage.home(s)
    SettingsGroup.entries.forEach { g ->
        val rows = shown.filter { it.group == g }
        val autoBet = g == SettingsGroup.BET && AppBook.isNovig && onOpenAutoBet != null
        val bids = g == SettingsGroup.BET && AppBook.isNovig && onOpenBids != null
        if (rows.isEmpty() && !autoBet && !bids) return@forEach
        SectionTitle(g.title, Modifier.testTag("settingsGroup-${g.name}"))
        if (autoBet) {
            SettingsRow(
                title = "Auto-bet rules",
                summary = "Which bets are taken and how much (the GitHub engine bets under these rules) · ${SettingsSummary.autoBet(s)}",
                tag = "settingsRow-AUTOBET",
                onClick = onOpenAutoBet!!,
            )
        }
        if (bids) {
            SettingsRow(
                title = "Bid rules",
                summary = "Which bids go up and how big (on the Bids tab) · ${com.tjshea.vigilant.data.novig.trading.maker.BidMode.of(s).label} · ${MakerRulesText.summary(s)}",
                tag = "settingsRow-BIDS",
                onClick = onOpenBids!!,
            )
        }
        rows.forEach { p ->
            SettingsRow(title = p.title, summary = "${p.about}\n${SettingsSummary.of(p, state)}", tag = "settingsRow-${p.name}", onClick = { onOpen(p) })
        }
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
        s.pausedByHand,
        tag = "pauseSwitch",
    ) { v -> onUpdate { it.copy(pausedByHand = v) } }
    // Tj, 2026-10-10: "I want the auto bid feature and auto bet features both to include all tennis ... Let me toggle tennis on and off".
    SwitchRow(
        "Tennis (ATP and WTA)",
        "Scans tennis, bids on it (Make orders) and, with Auto-bet on, takes it. CrazyNinjaOdds has no tennis, so tennis comes from Vigilant's own scan: the Scanner must be " +
            "Both or Vigilant only. Live bids have their own Tennis switch.",
        s.tennisOn && s.autoBetTennis,
        tag = "tennisSwitch",
    ) { v -> onUpdate { it.withTennis(v) } }
    // The kill switch (Tj, 2026-10-05) is the red bar above the tab bar on every screen, not a setting here: a switch in a page would be one more screen away.
    Hint(
        "The red STOP ALL bar above the tab bar (on every screen, the widget and the auto-scan notification) stops everything at once: scans, CrazyNinjaOdds, " +
            "auto-bet, auto-lock, bids (every resting bid is taken down) and the background scan. It stays off, even after you close the app, until you tap RESUME there." +
            if (s.killed) " It is ON now." else "",
    )
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
        TypedIntField(NumberSpecs.time("hours", 1, 720), s.startsWithinHours, "startsWithinField") { v -> onUpdate { it.copy(startsWithinHours = v) } }
        Hint(
            "Every list (+EV, CNO, Games and the widgets) shows only games starting within this window, and Vigilant's scan reads only these games. " +
                "Games already under way still show when live games are on.",
        )
        Shadowed.startsWithinCut(s)?.let { Warn(it, "startsWithinCut") }
    }
    // Include live games and Live games only mode live on the Live tab now (TCL122-9: one home for each switch; the Live tab is the live control board).
    Hint("Live games (in the feed, and where live runs: GitHub or this phone) are set on the Live tab.")

    // ---- Background scan (Vigilant for Novig) ------------------------------
    // The phone's own background scan only runs when the GitHub engine is not the scan (TCL122-9: a setting that does nothing now is hidden behind one line, not shown as if it mattered).
    if (AppBook.isNovig) {
        if (s.cloudDrives) {
            var open by rememberSaveable { mutableStateOf(false) }
            SectionTitle("This phone's own scan")
            Hint("The GitHub engine is the scan now (Settings › Cloud scan), so this phone does not scan in the background. These settings matter only when the cloud scan is off.")
            TextButton(onClick = { open = !open }, modifier = Modifier.testTag("phoneScanToggle")) { Text(if (open) "Hide the phone's scan settings" else "Show the phone's scan settings") }
            if (open) BackgroundScanSection(s, onUpdate)
        } else BackgroundScanSection(s, onUpdate)
    }
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
    SwitchRow(
        "Only money notifications", "Off: every alert and message. On: only a bet placed and a bid filled (plus the one notice Android needs while Vigilant runs, which also shows how many bids are up).",
        s.quietNotifications, tag = "quietNotificationsSwitch",
    ) { v -> onUpdate { it.copy(quietNotifications = v) } }
    SectionTitle("When to alert")
    Text("Smallest edge (EV) to alert on", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(ScanSettings.ALERT_MIN_EV_CHOICES, s.alertMinEv, ::alertLabel) { v -> onUpdate { it.copy(alertMinEv = v) } }
    TypedPercentField(NumberSpecs.percent("smallest edge", 0.1, 50.0), s.alertMinEv, "alertMinEvField") { v -> onUpdate { it.copy(alertMinEv = v) } }
    Hint(alertHint(s))
    Shadowed.alertEdge(s)?.let { Warn(it, "alertShadowed") }
    if (s.alertMinEv > 0.0 && !BackgroundScan.on(s)) {
        Warn("The background scan is off (Scanning), so alerts only come while a list or the widget is open on screen.", "alertNoBackground")
        OutlinedButton(onClick = { onUpdate { BackgroundScan.set(it, true) } }, modifier = Modifier.testTag("alertTurnOnBackground")) { Text("Turn on the background scan") }
    }
    if (s.cnoOn) SharpVetoSection(state, forAlerts = true, onUpdate = onUpdate)
    if (AppBook.isNovig) TrapGuardSection(s, showMove = false, tag = "alerts", onUpdate = onUpdate)
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

/**
 * Which games the CrazyNinjaOdds list looks at (Tj, 2026-10-07: "for the cno only scanner, right now I can't filter sports leagues at all. Make sure the cno scanner has plenty of
 * filters just like vigilant scanner"): leagues grouped by sport, kinds of bet, pregame only, the fewest dollars available, words in or out, props per game. Nothing picked is
 * everything; each choice says what it does and how the list is read for it ([CnoScopeText.readAs]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CnoScopeSection(s: ScanSettings, onUpdate: SettingsUpdate) {
    val scope = s.cnoFilters.scope
    val onScope: ((CnoScope) -> CnoScope) -> Unit = { t -> onUpdate { it.copy(cnoFilters = it.cnoFilters.copy(scope = t(it.cnoFilters.scope))) } }
    SectionTitle(CnoScopeText.TITLE)
    Hint(CnoScopeText.INTRO)
    Text("Leagues listed", style = MaterialTheme.typography.bodyMedium)
    FilterChip(
        selected = scope.leagues.isEmpty(), onClick = { onScope { it.copy(leagues = emptySet()) } }, label = { Text("All leagues") },
        modifier = Modifier.testTag("cnoScopeAll"),
    )
    val picked = scope.pickedLabels()
    for (sport in CnoLeagues.SPORTS) {
        val leagues = CnoLeagues.of(sport)
        val all = leagues.all { l -> picked.any { CnoLeagues.same(it, l.label) } }
        TextButton(onClick = { onScope { it.toggleSport(sport) } }, modifier = Modifier.testTag("cnoScopeSport-${sport.name}")) {
            Text(sport.label + if (all) " (all picked: tap to clear)" else " (tap to pick all)", style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leagues.forEach { l ->
                FilterChip(
                    selected = picked.any { CnoLeagues.same(it, l.label) }, onClick = { onScope { it.toggleLeague(l.label) } }, label = { Text(l.label) },
                    modifier = Modifier.testTag("cnoScopeLeague-${l.label}"),
                )
            }
        }
    }
    // A league CNO has renamed or dropped stays as a chip so it can be taken off.
    val unknown = picked.filter { CnoLeagues.byLabel(it) == null }
    if (unknown.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        unknown.forEach { l -> FilterChip(selected = true, onClick = { onScope { it.toggleLeague(l) } }, label = { Text(l) }, modifier = Modifier.testTag("cnoScopeLeague-$l")) }
    }
    Hint(CnoScopeText.readAs(s.cnoFilters, s.cnoViewUrl))
    Text("Kinds of bet listed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = scope.kinds.isEmpty(), onClick = { onScope { it.copy(kinds = emptySet()) } }, label = { Text("All kinds") }, modifier = Modifier.testTag("cnoScopeKindAll"))
        BetKind.entries.forEach { k ->
            FilterChip(selected = k in scope.kinds, onClick = { onScope { it.toggleKind(k) } }, label = { Text(k.label) }, modifier = Modifier.testTag("cnoScopeKind-${k.name}"))
        }
    }
    Hint("CrazyNinjaOdds has no filter for this: the app drops the kinds you leave out from a longer read of its list.")
    Shadowed.cnoKinds(s)?.let { Warn(it, "cnoKindsShadowed") }
    SwitchRow(
        "Hide live games",
        "Leave out games already under way. CrazyNinjaOdds' live prices are not as fresh, and Novig charges takers a fee once a game is live that its edge does not subtract; the auto-bet only bets pregame anyway.",
        scope.hideLive, tag = "cnoHideLive",
    ) { v -> onScope { it.copy(hideLive = v) } }
    Text("Fewest dollars available", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(CnoScopeText.LIQUIDITY_CHOICES, scope.minLiquidity, CnoScopeText::liquidityLabel) { v -> onScope { it.copy(minLiquidity = v) } }
    TypedIntField(NumberSpecs.count("dollars", 1, 100_000), scope.minLiquidity, "cnoMinLiquidityField") { v -> onScope { it.copy(minLiquidity = v) } }
    Hint("The dollars sitting at ${AppBook.name}'s price: a bet with less than this cannot take a real stake. Asked of CrazyNinjaOdds directly. A bet it shows no dollars for stays.")
    Text("Show only bets with these words", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    TypedWordsField("Teams, players or markets, separated by commas", scope.include, "cnoIncludeField") { v -> onScope { it.copy(include = v) } }
    Text("Leave out bets with these words", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    TypedWordsField("Teams, players or markets, separated by commas", scope.exclude, "cnoExcludeField") { v -> onScope { it.copy(exclude = v) } }
    Hint("Looked for in the game, the market and the bet (a team, a player's name, \"Shots On Goal\"); capitals and accents don't matter.")
    Text("Props per game", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(CnoScopeText.PROPS_PER_GAME_CHOICES, scope.propsPerGame, CnoScopeText::propsLabel) { v -> onScope { it.copy(propsPerGame = v) } }
    TypedIntField(NumberSpecs.count("props a game", 1, 100), scope.propsPerGame, "cnoPropsPerGameField") { v -> onScope { it.copy(propsPerGame = v) } }
    Hint("At most this many player props from one game, the best edges first (one NHL game lists 20-30 shot lines). Game lines are not counted.")
}

/** A words box: what is typed saves a moment after typing stops (a list read for every letter would be a request per keystroke), or at once on Done. */
@Composable
private fun TypedWordsField(label: String, saved: String, tag: String, onSet: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    LaunchedEffect(text) {
        if (text != saved) {
            delay(WORDS_IDLE_MS)
            onSet(text)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(CNO_WORDS_MAX) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text != saved) onSet(text) }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** How long a words box waits after the last letter before it saves. */
const val WORDS_IDLE_MS = 700L

/** The most characters a words box takes. */
const val CNO_WORDS_MAX = 200

/** CrazyNinjaOdds list: what it shows, each bet's book check, refresh. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CnoPage(s: ScanSettings, onUpdate: SettingsUpdate) {
    val f = s.cnoFilters
    val onCno: ((CnoFilters) -> CnoFilters) -> Unit = { t -> onUpdate { it.copy(cnoFilters = t(it.cnoFilters)) } }
    Intro(
        "CrazyNinjaOdds (CNO) is a website that compares ${AppBook.name}'s odds with many sportsbooks' and lists the bets priced better than their true odds " +
            "(+EV). These choices decide what that list shows. A preset (Auto-bet tab) sets the list's rules (devig, odds, books, edge, rows) at once, but never the games you pick below.",
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
    TypedIntField(NumberSpecs.LONGEST_ODDS, f.maxOdds, "cnoMaxOddsField") { v -> onCno { it.copy(maxOdds = v) } }
    Hint(
        if (f.maxOdds > 0) "Favorites and underdogs up to +${f.maxOdds} only. Long shots are where fake edges hide (a small pricing error looks like a big edge)."
        else "Any odds, long shots included. Long shots are where fake edges hide.",
    )
    Text("Shortest odds", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.SHORTEST_ODDS_CHOICES, f.minOdds, AutoBetText::minOddsLabel) { v -> onCno { it.copy(minOdds = v) } }
    TypedNumberField(NumberSpecs.SHORTEST_ODDS, oddsShown(f.minOdds), "cnoMinOddsField") { v -> onCno { it.copy(minOdds = v.toInt()) } }
    Hint(
        when {
            f.minOdds < 0 -> "No favorite shorter than ${Odds.formatAmerican(f.minOdds)}: heavy favorites risk a lot to win a little. Applied to every row CNO sends."
            f.minOdds > 0 -> "Underdogs only: ${Odds.formatAmerican(f.minOdds)} or longer. Every favorite and shorter price is left out."
            else -> "No limit: favorites of any size are listed."
        },
    )
    Shadowed.oddsRange(f.minOdds, f.maxOdds)?.let { Warn(it, "cnoOddsRange") }
    Text("Fewest books behind the true odds", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_MIN_BOOKS_CHOICES, f.minBooks, { "$it+" }) { v -> onCno { it.copy(minBooks = v) } }
    TypedIntField(NumberSpecs.count("books", 1, 12), f.minBooks, "cnoMinBooksField", none = { false }) { v -> onCno { it.copy(minBooks = v) } }
    Hint("How many sportsbooks' prices the true odds are built from. One or two books can be one book's mistake; more books, more trustworthy.")
    Text("Smallest edge (EV) listed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_MIN_EV_CHOICES, f.minEv, { Format.percent(it, 0) }) { v -> onCno { it.copy(minEv = v) } }
    TypedPercentField(NumberSpecs.percent("smallest edge", 0.0, 50.0), f.minEv, "cnoMinEvField") { v -> onCno { it.copy(minEv = v) } }
    Hint("EV (expected value): how much a bet should return over time, per dollar, above break-even. 3% means about 3¢ per \$1 bet in the long run.")
    SwitchRow(
        "Require a complete sportsbook",
        "Only markets where at least one book prices every side, so the true odds aren't guessed from half a market (CNO's own filter).",
        f.completeBook,
    ) { v -> onCno { it.copy(completeBook = v) } }
    Hint("Always left out: an edge over 20% (almost always a stale line), rows CNO worked out from one side only (⚠️), and rows whose edge doesn't follow from their odds.")

    CnoScopeSection(s, onUpdate)

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
    TypedIntField(NumberSpecs.time("seconds", 5, 3600), s.cnoRefreshSeconds, "cnoRefreshField") { v -> onUpdate { it.copy(cnoRefreshSeconds = v) } }
    Hint(refreshHint(s))
    Text("Rows per read", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.CNO_ROWS_CHOICES, f.rows, { "$it" }) { v -> onCno { it.copy(rows = v) } }
    TypedIntField(NumberSpecs.count("rows", 10, 200), f.rows, "cnoRowsField", none = { false }) { v -> onCno { it.copy(rows = v) } }
    Hint("CNO sends its best bets first; more rows means more candidates for the book checks and auto-bet.")

    SectionTitle("Advanced")
    CnoViewEditor(s.cnoViewUrl) { link -> onUpdate { it.copy(cnoViewUrl = link) } }
}

/** Widget & mini window: the floating widget, picture-in-picture, and Vigilant's rescans while it's open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.WidgetPage(s: ScanSettings, onUpdate: SettingsUpdate) {
    Intro("A small window with your best bets that stays on screen over other apps (like ${AppBook.name}), so you can bet without switching back and forth.")
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
    if (s.scannerNow == ScannerMode.BOTH) {
        Text("Vigilant's scan again while the widget is open", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.WIDGET_RESCAN_CHOICES, s.widgetRescanMinutes, { if (it <= 0) "Off" else "$it min" }) { v -> onUpdate { it.copy(widgetRescanMinutes = v) } }
        TypedIntField(NumberSpecs.time("minutes", 1, 240), s.widgetRescanMinutes, "widgetRescanField") { v -> onUpdate { it.copy(widgetRescanMinutes = v) } }
        Hint(
            "The widget lists Vigilant's bets and CNO's together, best edge first; a bet both list shows once. " +
                (if (s.widgetRescanMinutes > 0) "Vigilant scans again every ${s.widgetRescanMinutes} min while the widget or CNO's tab is on screen. Each scan spends API credits (Pinnacle / The Odds API keys)."
                else "Off: Vigilant scans only when you tap Scan (no API credits spent on its own).") +
                " Its bets leave the widget once the odds behind them are too old (${com.tjshea.vigilant.data.scanner.Freshness.LIMIT_TEXT}).",
        )
    }
    if (!s.floatingWidget) {
        Hint(
            when (s.scannerNow) {
                ScannerMode.BOTH -> "Picture-in-picture: it lists Vigilant's bets and CrazyNinjaOdds' (tagged CNO), best EV first; tap it for Scan (which refreshes CNO's too), Recheck and Next. Pinch or double-tap to enlarge; drag it to the bottom to close."
                ScannerMode.VIGILANT -> "Picture-in-picture: it lists Vigilant's bets; tap it for Scan, Recheck and Next. Pinch or double-tap to enlarge; drag it to the bottom to close."
                ScannerMode.CNO -> "Picture-in-picture: it lists CNO's bets; tap it for Up, Down and Books (every book's odds for the top bet; Up and Down then move between bets). Pinch or double-tap to enlarge."
            },
        )
        Hint("If it never appears, turn on picture-in-picture for Vigilant in Android Settings › Apps › Special app access.")
    }
}

/** Fair odds: how fair odds are worked out, the sharp books, the guards, the sportsbooks and their props. The feeds, switches and keys are under APIs & keys. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.FairOddsTab(state: UiState, onUpdate: SettingsUpdate, onOpenPage: (SettingsPage) -> Unit) {
    val s = state.settings
    Intro(
        "Fair (true) odds are what a bet's odds would be with the sportsbooks' profit taken out. Vigilant compares Novig's price with them: a price better than " +
            "fair is a +EV bet. These choices decide how Vigilant's own scan works them out (CrazyNinjaOdds works out its own).",
    )
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
            FairSource.SHARP -> "From the sharp books you mark below (Pinnacle by default): books that let winning bettors bet big, so their odds are the most accurate."
            FairSource.MARKET_AVERAGE -> "The average of every book, each with its profit taken out first."
            FairSource.BLEND -> "A mix: ${(s.sharpWeight * 100).roundToInt()}% the sharp books, ${100 - (s.sharpWeight * 100).roundToInt()}% the average of every book."
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

    Text("Books that count as sharp", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
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

    Text("Fewest books for an average: ${s.minBooks}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    ChoiceChips((1..5).toList(), s.minBooks, { it.toString() }) { v -> onUpdate { it.copy(minBooks = v) } }
    TypedIntField(NumberSpecs.count("books", 1, 20), s.minBooks, "fairMinBooksField", none = { false }) { v -> onUpdate { it.copy(minBooks = v) } }
    Hint("An average of one or two books can be one book's mistake: a line with fewer books than this is skipped.")
    SwitchRow(
        "Outlier guard",
        "With 3 or more books, use the lower of their average and median, so one stale book can't create a fake edge.",
        s.outlierGuard,
    ) { v -> onUpdate { it.copy(outlierGuard = v) } }
    SwitchRow(
        "Ignore wide quotes",
        "A book or exchange whose two sides add up to too much (a book above 12%, an exchange such as ProphetX or Kalshi above 10%) is thin or careless: it is left out of every fair line and shown as \"wide\" in the books table.",
        s.ignoreWideQuotes, tag = "wideQuotesSwitch",
    ) { v -> onUpdate { it.copy(ignoreWideQuotes = v) } }
    SwitchRow(
        "One book per company",
        "Books of one parent company (Hard Rock's sites, FanDuel and FanDuel YourWay, Caesars' two names, SGO's many clones) quote one line, so only one of them is listed and counted in a fair price.",
        s.oneVotePerCompany, tag = "oneVotePerCompanySwitch",
    ) { v -> onUpdate { it.copy(vigGuards = it.vigGuards.copy(oneVotePerCompany = v)) } }
    SwitchRow(
        "Ignore books with wrong-looking odds",
        "A book whose odds are an extreme outlier next to the rest of the market, or put the favourite on the other side, is left out of the fair line (the bet's sheet lists it in amber).",
        s.ignoreBadOdds, tag = "ignoreBadOddsSwitch",
    ) { v -> onUpdate { it.copy(vigGuards = it.vigGuards.copy(ignoreBadOdds = v)) } }
    SwitchRow(
        "Cut thin early shots-on-goal fair prices",
        "NHL shots-on-goal fair prices built from fewer than 5 books, on a game more than 12 hours away, ran about 3 points above Pinnacle's closing price: each side's fair chance is cut 2.5 points before the edge, stake, rating, auto-bet and bids use it.",
        s.thinEarlyHaircut, tag = "thinEarlyHaircutSwitch",
    ) { v -> onUpdate { it.copy(vigGuards = it.vigGuards.copy(thinEarlyHaircut = v)) } }
    SwitchRow(
        "Cut player-prop fair prices (Overs 7 points, Unders 3)",
        "Your history (1,228 settled bets): player-prop Overs won 39.7% against a fair chance of 49.4% (t -4.2), Unders about 3 points under theirs, every other kind of bet matched. Each prop side's fair chance is cut before the edge, stake, star rating, auto-bet, bids and Bet sheet use it, so most prop Overs stop looking +EV. Turn it off to see the unadjusted edges.",
        s.propHaircut, tag = "propHaircutSwitch",
    ) { v -> onUpdate { it.copy(vigGuards = it.vigGuards.copy(propHaircut = v)) } }

    SectionTitle("Devig method (advanced)")
    Hint("How the sportsbooks' profit is taken out of a pair of odds. The methods differ most on long shots. Worst case (the default) takes the lowest fair chance of the other four for each side, so an edge that shows survives them all: the safest. Closing lines found after a game use the same method, so CLV compares like with like.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DevigMethod.entries.forEach { m ->
            FilterChip(selected = s.devigMethod == m, onClick = { onUpdate { it.copy(devigMethod = m) } }, label = { Text(m.displayName) })
        }
    }
    Hint(s.devigMethod.blurb)

    // ---- Sources (the switches, keys and credit options moved to APIs & keys in v0.89.35: one home for them) -----------------
    SectionTitle("Where fair odds come from")
    Hint("The feeds, their switches, keys and credits are all under APIs & keys: each API has its own page there. These choices below decide what is done with the prices they send.")
    OutlinedButton(onClick = { onOpenPage(SettingsPage.APIS) }, modifier = Modifier.testTag("fairOpenApis")) { Text("Open APIs & keys") }
    if (!AppBook.isNovig) {
        Hint(
            "${AppBook.name}'s own odds come in these same requests (no request just for ${AppBook.name}): PropLine first, " +
                "The Odds API where PropLine can't answer. Keep one of them on, with a key. ${AppBook.name}'s prices never " +
                "count toward their own fair line.",
        )
    }
    // PropLine goes first for the same sportsbooks; The Odds API backs it up (RESEARCH.md §23).
    val propLineFirst = s.usePropLine && state.proplineKeys.isNotEmpty()
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
            TypedIntField(NumberSpecs.time("hours", 1, 720), s.bookPropHours, "bookPropHoursField", none = { it <= 0 || it >= ScanSettings.NO_LIMIT }) { v -> onUpdate { it.copy(bookPropHours = v) } }
            if (s.bookPropHours >= ScanSettings.NO_LIMIT) Hint("All: every game the scan reads (${windowLabel(s.scanWindowHours)} now).")
            if (s.usePropLine) {
                Text("PropLine props: most games per scan", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(ScanSettings.PROPLINE_GAMES_CHOICES, s.propLineGamesPerScan, { if (it >= ScanSettings.NO_LIMIT) "No limit" else it.toString() }) { v ->
                    onUpdate { it.copy(propLineGamesPerScan = v) }
                }
                TypedIntField(NumberSpecs.count("games", 1, 1000), s.propLineGamesPerScan, "propLineGamesField", none = { it <= 0 || it >= ScanSettings.NO_LIMIT }) { v -> onUpdate { it.copy(propLineGamesPerScan = v) } }
                Hint(propLineGamesHint(s))
            }
            if (s.useOddsApi) {
                Text("Prop types per game (The Odds API)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(BookPropSet.entries, s.bookPropSet, { it.displayName }) { v -> onUpdate { it.copy(bookPropSet = v) } }
                Text("Most credits per scan on props", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(ScanSettings.BOOK_PROP_CREDIT_CHOICES, s.bookPropCreditsPerScan, { if (it == 0) "None" else if (it >= ScanSettings.NO_LIMIT) "No limit" else it.toString() }) { v ->
                    onUpdate { it.copy(bookPropCreditsPerScan = v) }
                }
                TypedIntField(NumberSpecs.count("credits", 1, 5000), s.bookPropCreditsPerScan, "bookPropCreditsField", none = { it <= 0 || it >= ScanSettings.NO_LIMIT }) { v -> onUpdate { it.copy(bookPropCreditsPerScan = v) } }
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
    Intro("Vigilant's own scan: what its +EV list shows, and how much each scan reads. Bigger scans find more but take longer and spend more of Novig's rate limit.")
    SectionTitle("What the feed shows")
    var minEv by remember(s.minEvPercent) { mutableFloatStateOf((s.minEvPercent * 100).toFloat()) }
    Text("Smallest edge (EV) shown: ${String.format(Locale.US, "%.1f", minEv)}%", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = minEv,
        onValueChange = { minEv = (it * 2).roundToInt() / 2f },
        onValueChangeFinished = { onUpdate { it.copy(minEvPercent = minEv / 100.0) } },
        valueRange = 0f..10f,
        steps = 19,
    )
    TypedPercentField(NumberSpecs.percent("smallest edge", 0.0, 50.0), s.minEvPercent, "feedMinEvField") { v -> onUpdate { it.copy(minEvPercent = v) } }
    Text("Longest odds shown: ${maxOddsLabel(s.maxOdds)}", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(ScanSettings.MAX_ODDS_CHOICES, s.maxOdds, ::maxOddsLabel) { v -> onUpdate { it.copy(maxOdds = v) } }
    TypedIntField(NumberSpecs.LONGEST_ODDS, s.maxOdds, "feedMaxOddsField", none = { it <= 0 }) { v -> onUpdate { it.copy(maxOdds = v) } }
    Text("Shortest odds shown: ${AutoBetText.minOddsLabel(s.minOdds)}", style = MaterialTheme.typography.bodyMedium)
    ChoiceChips(ScanSettings.SHORTEST_ODDS_CHOICES, s.minOdds, AutoBetText::minOddsLabel) { v -> onUpdate { it.copy(minOdds = v) } }
    TypedNumberField(NumberSpecs.SHORTEST_ODDS, oddsShown(s.minOdds), "feedMinOddsField") { v -> onUpdate { it.copy(minOdds = v.toInt()) } }
    Hint("Heavy favorites (-300 and shorter) risk a lot to win a little; +100 keeps only underdogs and even money.")
    Shadowed.oddsRange(s.minOdds, s.maxOdds)?.let { Warn(it, "feedOddsRange") }
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
    SectionTitle("Scan size (advanced)")
    if (AppBook.exchange) {
    Text("Alternate lines per game: ${allLabel(s.linesPerGame)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.LINES_PER_GAME_CHOICES, s.linesPerGame, ::allLabel) { v -> onUpdate { it.copy(linesPerGame = v) } }
    TypedIntField(NumberSpecs.count("lines", 1, 100), s.linesPerGame, "linesPerGameField", none = { it >= ScanSettings.NO_LIMIT }) { v -> onUpdate { it.copy(linesPerGame = v) } }
    Hint("Per spread, total and team total (full game and 1st half). Every line is one Novig request per scan: fewer lines scan faster and stay well under Novig's rate limit.")
    if (MarketFamily.PLAYER_PROPS in s.families) {
        Text("Player props per game: ${allLabel(s.propsPerGame)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.PROPS_PER_GAME_CHOICES, s.propsPerGame, ::allLabel) { v -> onUpdate { it.copy(propsPerGame = v) } }
        TypedIntField(NumberSpecs.count("props", 1, 100), s.propsPerGame, "propsPerGameField", none = { it <= 0 || it >= ScanSettings.NO_LIMIT }) { v -> onUpdate { it.copy(propsPerGame = v) } }
        Hint("NFL, MLB and WNBA props that Pinnacle, Kalshi or the sportsbooks also price, on the same line. The best-covered ones are checked first.")
    }
    Text("Most Novig prices per scan: ${limitLabel(s.maxBooksPerScan)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.MAX_BOOKS_CHOICES, s.maxBooksPerScan, ::limitLabel) { v -> onUpdate { it.copy(maxBooksPerScan = v) } }
    TypedIntField(NumberSpecs.count("prices", 10, 100_000), s.maxBooksPerScan, "maxBooksField", none = { it >= ScanSettings.NO_LIMIT }) { v -> onUpdate { it.copy(maxBooksPerScan = v) } }
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
    Text("Days ahead: ${s.daysAhead} (the furthest a scan reads)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.DAYS_AHEAD_CHOICES, s.daysAhead, { "${it}d" }) { v -> onUpdate { it.copy(daysAhead = v) } }
    TypedIntField(NumberSpecs.time("days", 1, 30), s.daysAhead, "daysAheadField", none = { false }) { v -> onUpdate { it.copy(daysAhead = v) } }
    Hint(
        "Games starting within this many days are scanned, soonest first. A week takes in the next college Saturday and NFL Sunday." +
            (Shadowed.daysAhead(s)?.let { " $it" } ?: ""),
    )
}

/** Betting & Novig account: the key and wallet, then how much each bet is (bankroll, Kelly, the starting amounts), and the limits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BettingTab(
    state: UiState,
    onUpdate: SettingsUpdate,
    onNovigConnect: (com.tjshea.vigilant.data.novig.signing.ManagementKey?) -> Unit,
    onNovigTest: () -> Unit,
    onNovigDisconnect: () -> Unit,
    bettingActions: BettingActions,
    onOpenAutoBet: (() -> Unit)? = null,
) {
    val s = state.settings
    if (AppBook.isNovig) {
        Intro(
            "Connect your Novig API key to bet straight from Vigilant (the Bet sheet) and to use auto-bet. Bets come from a separate Vigilant wallet inside " +
                "your Novig account that you move money into. Without a key, a tap on a bet opens Novig's own bet slip instead.",
        )
        // In CNO only too: CNO's cards bet through the API as well, and the Bet sheet's "Add money" lands on this wallet.
        SectionTitle("Novig API key")
        NovigKeySection(state.novig, onNovigConnect, onNovigTest, onNovigDisconnect, lastScan = state.status, onForgetKey = bettingActions.onForgetKey)
        // Betting through Novig's API: needs the connected key's subaccount (Tj, 2026-09-29).
        if (state.novig.connection != null) {
            NovigBettingSection(state.betting, s, bettingActions, onUpdate, savedKey = state.novig.managementKey)
        }
        // The bid guard (Tj, 2026-10-11): a resting order with no expiry is taken down after a while, whoever posted it.
        if (state.novig.connection != null) {
            SectionTitle("Bid guard")
            SwitchRow(
                "Take down bids left up", "A resting order with no expiry (Novig's own app, or any tool that set none) is cancelled once it has been up this long. Vigilant's own bids always carry a time limit and are not touched.",
                s.guards.bidGuard, tag = "bidGuardSwitch",
            ) { v -> onUpdate { it.copy(guards = it.guards.copy(bidGuard = v)) } }
            if (s.guards.bidGuard) {
                Text("Cancel after", style = MaterialTheme.typography.bodyMedium)
                ChoiceChips(listOf(10.0, 15.0, 30.0, 60.0, 120.0), s.guards.bidGuardMinutes.toDouble(), { "${it.toInt()} min" }) { v -> onUpdate { it.copy(guards = it.guards.copy(bidGuardMinutes = v.toInt())) } }
            }
        }
        // Novig's live feed (Tj, 2026-10-12: "Build the novig bbo feature with a backup to default toggle in case it doesn't work right"; NOVIG_API.md §26).
        SectionTitle("Novig live feed")
        SwitchRow(
            "Best-bid feed (bbo)",
            "Screens prices on each market's best bid alone (half the stream allowance, a fraction of the updates, fast on a phone) and holds the full order book only where depth matters: bets worth making, " +
                "live games and your resting bids. Off = the full order book for every market, as before. It puts itself back on the full book if Novig refuses it or it disagrees with the book " +
                "(Diagnostics says which); turn it off and on to try it again.",
            s.gates.novigBbo, tag = "novigBboSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(novigBbo = v)) } }
        SwitchRow(
            "Keep the Novig live feed open",
            "Holds the connection between scans and reconnects it by itself after a drop, so a scan finds its prices already pushed. Off = the connection closes two minutes after a scan.",
            s.gates.novigKeepOpen, tag = "novigKeepOpenSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(novigKeepOpen = v)) } }
        SwitchRow(
            "Instant order results",
            "Novig pushes an order's end (filled, cancelled, rejected) over the live feed the moment its matching engine has it, so a bet's result is not asked for every half second. The fills are still read " +
                "from Novig for the record. Off = the result is read by request, as before.",
            s.gates.novigOrderPush, tag = "novigOrderPushSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(novigOrderPush = v)) } }
        SwitchRow(
            "Orders over the socket",
            "Sends an immediate-or-cancel bet to Novig over a live connection of its own (signed with the trading key) instead of a web request: no new handshake per bet, and the answer comes back on the same line. " +
                "A bet is never sent twice: if Novig does not answer, the bet is looked up by its id rather than replayed, and the socket rests for ten minutes (Diagnostics says why). Off = every bet goes by web request, as before.",
            s.gates.novigSocketOrders, tag = "novigSocketOrdersSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(novigSocketOrders = v)) } }
        SwitchRow(
            "Compress the live feed",
            "Asks Novig to send the live feed compressed. Your phone downloads about 640 KB a second from Novig, so a first snapshot of a thousand markets takes a second or more plain and a fraction of that compressed; " +
                "prices and results arrive the same. It turns itself off if the feed can't be read compressed (Diagnostics says why). Off = plain text, as before. Takes effect when the feed next connects.",
            s.gates.novigCompress, tag = "novigCompressSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(novigCompress = v)) } }
        SectionTitle("Profit-proven only")
        SwitchRow(
            "Profit-proven only",
            "Skips the types of bet and bid that have lost money in YOUR bet history (for example an NFL player-prop Over), puts the best-performing types of bid up first, and warns on a bet by hand. A type with " +
                "a small sample is left alone. It learns from the bet-history files you share with Claude and updates when Claude regenerates it from a bigger history: " +
                (com.tjshea.vigilant.data.scanner.ProfitProfile.shipped?.summary() ?: "no profile is built in") + ". Off = no change at all.",
            s.gates.profitProven, tag = "profitProvenSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(profitProven = v)) } }
        SectionTitle("Streaming engine")
        SwitchRow(
            "Stream odds instead of scanning at intervals",
            "When Novig pushes a new price for a line, just that line is re-priced against the fair odds already held (milliseconds) and the auto-bet judges it at once; when BoltOdds' board changes, its games are matched to Novig's again and every held " +
                "price is re-priced. Bids follow. Nothing is requested from any API (the sockets' boards are in memory), so it spends no credits; your normal interval scans go on beside it and read everything. Needs BoltOdds on with a key, a background scan mode on, " +
                "and the Novig key. Off = nothing changes.",
            s.gates.streamScan, tag = "streamScanSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(streamScan = v)) } }
        if (s.gates.streamScan) {
            Text("Least seconds between BoltOdds refreshes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(listOf(1, 2, 3, 5, 10), s.gates.streamGapSeconds, { "$it s" }) { v -> onUpdate { it.copy(gates = it.gates.copy(streamGapSeconds = v)) } }
            SwitchRow(
                "Live games too",
                "Turns on Include live games, the real-time Pinnodds + Novig lane (the live fee, prices kept only while 5 seconds fresh, a bet's worst price keeps it +EV after Novig's ~5 s order delay) and live bids " +
                    "(a bid comes down the moment it stops being +EV). BoltOdds has no Pinnacle, so a live fair still comes from Pinnodds. Real orders still need their own switches in Pinnodds live (paper until you turn them on).",
                s.gates.streamLive, tag = "streamLiveSwitch",
            ) { v -> onUpdate { it.copy(includeLive = if (v) true else it.includeLive, pinnLive = if (v) true else it.pinnLive, liveBid = if (v) true else it.liveBid, gates = it.gates.copy(streamLive = v)) } }
        }
        SectionTitle("Freshest quote")
        SwitchRow(
            "Freshest quote wins",
            "When two of your APIs send the same book's price for the same line, use the one stamped more than 20 seconds newer (otherwise the usual feed order). Off = feed order only. Diagnostics › Source race shows how old each API's quotes were.",
            s.gates.freshestWins, tag = "freshestWinsSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(freshestWins = v)) } }
        SwitchRow(
            "Rank APIs by measured quality",
            "After every scan the app measures each API (how old its odds are by the books' own times, and how far its prices sit from Pinnacle's) and asks the freshest, most accurate one first; the others stand by, take over when it fails or runs out of keys, or fill games it lacks. An API that stood by is tried once every 6 hours so it can earn the top. Off = the fixed order the app shipped with. Diagnostics › API ranking shows the order.",
            s.gates.rankByMeasure, tag = "rankByMeasureSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(rankByMeasure = v)) } }
        SwitchRow(
            "API study mode",
            "For testing which of your APIs is fastest and most accurate: the next scan asks EVERY API that is switched on, instead of letting the best one answer and the rest stand by. It spends the credits of every API that is on, so switch off the ones you are short of (The Odds API, OddsPapi Free), run one scan, send the Diagnostics file (Source race), then switch this off again.",
            s.gates.askAllSources, tag = "askAllSourcesSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(askAllSources = v)) } }
        SectionTitle("Repeat and hedge guard")
        SwitchRow(
            "Repeat and hedge guard",
            "Stops the auto-bet, the bids and the live engine from betting or bidding the same side (player, team or prop) over and over because its price keeps changing: after the number of bets below, or the dollars below, " +
                "that side is left alone. The other side of the same market is allowed only when both sides together are a sure profit after fees, so it never locks in a loss. A bet by hand is only warned.",
            s.gates.repeatGuard, tag = "repeatGuardSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(repeatGuard = v)) } }
        if (s.gates.repeatGuard) {
            Text("Most bets or bids on one side", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(listOf(1, 2, 3, 5, 0), s.gates.repeatMaxBets, { if (it == 0) "No limit" else it.toString() }) { v -> onUpdate { it.copy(gates = it.gates.copy(repeatMaxBets = v)) } }
            Text("Most dollars on one side", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(listOf(25.0, 50.0, 100.0, 250.0, 0.0), s.gates.repeatMaxDollars, { if (it == 0.0) "No limit" else "$" + it.toInt() }) { v -> onUpdate { it.copy(gates = it.gates.copy(repeatMaxDollars = v)) } }
            Text("Most other lines of the same team or player in one game", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Text("A team at +6.5 and +3.5, or a player's over at 4.5 and 5.5, win and lose together. At 1 the auto-bet takes one line of a team's spread (or a player's side) per game; bids are not limited by this.", style = MaterialTheme.typography.bodySmall)
            ChoiceChips(listOf(1, 2, 3, 0), s.gates.repeatMaxLines, { if (it == 0) "No limit" else it.toString() }) { v -> onUpdate { it.copy(gates = it.gates.copy(repeatMaxLines = v)) } }
            SwitchRow(
                "Allow the other side when it is a sure profit",
                "Lets the auto-bet and bids take the other side of a market you already hold, but only if every way it can end pays a profit after every dollar spent. Off = never both sides.",
                s.gates.repeatHedge, tag = "repeatHedgeSwitch",
            ) { v -> onUpdate { it.copy(gates = it.gates.copy(repeatHedge = v)) } }
        }
        if (s.cnoOn && onOpenAutoBet != null) {
            OutlinedButton(onClick = onOpenAutoBet, modifier = Modifier.padding(top = 8.dp).testTag("bettingOpenAutoBet")) { Text("Auto-bet has its own tab: open it") }
        }
    }

    // ---- How much each bet is ------------------------------------------------------------
    SectionTitle("Bet amounts")
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
    Hint("Your bankroll: the money you set aside for betting. Kelly stakes are a share of it.")
    Text("Kelly fraction for suggested stakes", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(ScanSettings.KELLY_CHOICES, s.kellyMultiplier, Format::kellyLabel) { v -> onUpdate { it.copy(kellyMultiplier = v) } }
    TypedNumberField(NumberSpecs.KELLY, NumberSpecs.KELLY.show(s.kellyMultiplier), "kellyField") { v -> onUpdate { it.copy(kellyMultiplier = v) } }
    Hint(
        "Kelly is a formula that sizes a bet by how big its edge is: a bigger edge, a bigger bet. Full Kelly swings hard; most bettors use ¼ Kelly because " +
            "edges are estimates. Used for the stake each card suggests and for \"Kelly\" below. (Auto-bet has its own stake rule, on its tab.) " +
            if (AppBook.exchange) "Suggested stakes are capped at what Novig's book can actually fill at +EV."
            else "${AppBook.name} doesn't publish its limits: a suggested stake over your max bet there is capped by ${AppBook.name} itself.",
    )
    if (AppBook.isNovig) {
        // Novig's bet-slip links take a wager (Tj, 2026-09-28: "automatically enter 1 dollar per bet, the kelly value per bet, or an amount I can type into
        // the settings"; NovigLinks). One choice for both places a bet starts (2026-10-02 ~18:10Z: "$1" filled the slip with $1 but the Bet sheet with
        // "Amount a bet starts at"): the bet slip and Vigilant's own Bet sheet.
        Text("Amount a bet starts at", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        ChoiceChips(com.tjshea.vigilant.data.novig.SlipStake.entries.toList(), s.slipStake, { it.label }) { v -> onUpdate { it.copy(slipStake = v) } }
        // One amount (Tj, 2026-10-07: "redundant … settings"): "My amount" is the start when it is chosen, and the Bet sheet's start when the choice has none for a bet.
        if (StakeText.amountShown(s, state.betting.enabled)) {
            Text(StakeText.amountTitle(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            ChoiceChips(STAKE_AMOUNT_CHOICES, s.slipCustomStake, { Format.money(it) }) { v -> onUpdate { it.copy(slipCustomStake = v) } }
            TypedDollarField(NumberSpecs.dollars("amount"), s.slipCustomStake, "slipCustomStake") { v -> onUpdate { it.copy(slipCustomStake = v) } }
        }
        Hint(StakeText.startHint(s))
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
}

/** The bet-amount lines (pure, for tests). */
object StakeText {
    /** What the starting-amount choice does, for the bet slip and the Bet sheet. */
    fun startHint(s: ScanSettings): String = when (s.slipStake) {
        com.tjshea.vigilant.data.novig.SlipStake.OFF -> "Novig's bet slip opens with no amount (you type it in Novig); Vigilant's Bet sheet starts at the amount below."
        com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR -> "Every bet starts at \$1: in Novig's bet slip and in Vigilant's Bet sheet."
        com.tjshea.vigilant.data.novig.SlipStake.KELLY ->
            "Every bet starts at its own Kelly stake, to the cent: bankroll × the Kelly fraction above × (true chance − price) ÷ (1 − price), so a bigger edge " +
                "gets more. Held to what Novig has for sale at +EV and to your most for one bet."
        com.tjshea.vigilant.data.novig.SlipStake.CUSTOM -> "Every bet starts at this amount: in Novig's bet slip and in Vigilant's Bet sheet."
    } + " You can always change it before you confirm."

    /** Whether the one amount ([ScanSettings.slipCustomStake]) is used at this choice: always, except with "$1" (nothing to set). The Bet sheet's own start only matters with betting on. */
    fun amountShown(s: ScanSettings, bettingEnabled: Boolean): Boolean = when (s.slipStake) {
        com.tjshea.vigilant.data.novig.SlipStake.CUSTOM -> true
        com.tjshea.vigilant.data.novig.SlipStake.OFF, com.tjshea.vigilant.data.novig.SlipStake.KELLY -> bettingEnabled
        com.tjshea.vigilant.data.novig.SlipStake.ONE_DOLLAR -> false
    }

    fun amountTitle(s: ScanSettings): String = when (s.slipStake) {
        com.tjshea.vigilant.data.novig.SlipStake.CUSTOM -> "My amount"
        com.tjshea.vigilant.data.novig.SlipStake.KELLY -> "When a bet has no Kelly stake (no edge at its price), start at"
        else -> "Bet sheet starts at"
    }
}

/** The Bet sheet's starting amounts. */
val STAKE_AMOUNT_CHOICES = listOf(1.0, 2.0, 5.0, 10.0, 20.0)

/** Usage & keys: each API's usage meter and the keys backup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.UsageTab(state: UiState, keys: KeyActions) {
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(keys.exportTo) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(keys.importFrom) }
    // ---- Usage meters ------------------------------------------------------------------
    // Opening the tab asks ParlayAPI's keys what they have left (free, at most every few minutes): the meter shows the provider's figures.
    val onShown by androidx.compose.runtime.rememberUpdatedState(keys.onUsageShown)
    // With the scan on GitHub the engine spends these keys too (Tj, 2026-10-15): while this page is open the APIs' own figures are asked again every 2 minutes (free), so what is left stays true.
    val cloudSpends = state.settings.cloudScan
    androidx.compose.runtime.LaunchedEffect(cloudSpends) { onShown(); while (cloudSpends) { kotlinx.coroutines.delay(120_000L); onShown() } }
    Intro("The odds feeds Vigilant's own scan reads (Pinnacle, ParlayAPI, PropLine, The Odds API) each allow so many requests a day or month. This shows what's left on each key.")
    SectionTitle("API usage")
    OutlinedButton(onClick = keys.onUsageShown, enabled = !state.usageBusy, modifier = Modifier.testTag("usageRefresh")) { Text(if (state.usageBusy) "Reading…" else "Refresh from the APIs") }
    if (state.settings.cloudScan) Hint(
        "The scan is on GitHub, so GitHub spends these keys too: its engine sends its own count every half minute and it is added to this phone's (each meter says how much was GitHub's). Where an API reports its own figure, the newest reading from either side is shown, with its age.",
    )
    Hint("Where an API has a prompt for it (The Odds API, PropLine, SportsGameOdds, ParlayAPI, OddsPapi Free) the figures come from the API itself and say so; PinnWire, pinnapi, Pinnodds, OddsPapi v5 and Unabated offer none, so those show the app's own count.")
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

/**
 * Settings › SportsGameOdds Pro (Tj, 2026-10-09; SPORTSGAMEODDS_API.md): the key list (rotated like every provider's), a Test key that says what the key really carries and saves a sample for
 * Claude, and the one switch. On, the scan, bids, open-bet pricing and closing lines read SGO too, beside every other API that is on (none rests for it).
 */
@Composable
private fun ColumnScope.SgoPage(state: UiState, keys: KeyActions, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro(com.tjshea.vigilant.app.SgoText.INTRO)
    KeyListEditor(ApiProvider.SPORTSGAMEODDS, state.sgoKeys, keys, "Add a SportsGameOdds key")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onTestSgoKey(false) }, enabled = state.sgoKeys.isNotEmpty() && !state.sgoKeyBusy, modifier = Modifier.testTag("sgoTestKey")) {
            Text(if (state.sgoKeyBusy) "Testing…" else "Test key")
        }
        androidx.compose.material3.OutlinedButton(onClick = { reportActions.onTestSgoKey(true) }, enabled = state.sgoKeys.isNotEmpty() && !state.sgoKeyBusy, modifier = Modifier.testTag("sgoShareSample")) {
            Text("Test and share sample")
        }
    }
    state.sgoKeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("sgoKeyNote"),
            color = if (state.sgoKeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow(com.tjshea.vigilant.app.SgoText.SWITCH_TITLE, com.tjshea.vigilant.app.SgoText.switchSub(state.sgoKeys.isNotEmpty()), s.sgoPro, tag = "sgoProSwitch") { v -> onUpdate { it.copy(sgoPro = v) } }
    if (s.sgoPro) {
        SwitchRow("Extra books: Circa, SuperBook, bet365", "Price the fair line with them too (each must show both sides and be fresh, like every book).", s.sgoExtraBooks, tag = "sgoExtraSwitch") { v -> onUpdate { it.copy(sgoExtraBooks = v) } }
        SwitchRow("Every book for the fair average", "Price the fair line with every book SportsGameOdds carries (80+), not only the books picked in Settings and the extras: each is devigged on its own and averaged (Market average / Blend). A league whose unfiltered query times out falls back to the picked books.", s.gates.sgoAllBooks, tag = "sgoAllBooksSwitch") { v -> onUpdate { it.copy(gates = it.gates.copy(sgoAllBooks = v)) } }
        SwitchRow("Alternate lines", "Every book's alternate spreads and totals with the game lines (heavier replies; Pro handles them).", s.sgoAltLines, tag = "sgoAltSwitch") { v -> onUpdate { it.copy(sgoAltLines = v) } }
        Text("Drop odds older than", style = MaterialTheme.typography.bodyMedium)
        ChoiceChips(ScanSettings.SGO_MAX_AGE_CHOICES.map { it.toDouble() }, s.sgoMaxAgeMinutes.toDouble(), { "${it.toInt()} min" }) { v -> onUpdate { it.copy(sgoMaxAgeMinutes = v.toInt()) } }
        Hint("A bet or bid is dropped once the odds behind it are this old (SGO Pro only; off, the app's own 5/10-minute rule). SGO refreshes game lines about every 5 minutes and props about every 10, so with 10 minutes there can be gaps with no bids; 15 closes most of them.")
        Hint(com.tjshea.vigilant.app.SgoText.REPLACES)
    }
}

/**
 * Settings › OddsPapi (Tj, 2026-10-09; ODDSPAPI_API.md): the key list (rotated like every provider's), a Test key that says what the key really carries and saves a sample for Claude, and the one
 * switch. On, the scan, bids, open-bet pricing, closing lines (CLV for any bet) and grading read OddsPapi too, beside every other API that is on (none rests for it). Off: it is never called.
 */
@Composable
private fun ColumnScope.OddsPapiPage(state: UiState, keys: KeyActions, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro(com.tjshea.vigilant.app.OpText.INTRO)
    KeyListEditor(ApiProvider.ODDSPAPI, state.opKeys, keys, "Add an OddsPapi key")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onTestOpKey(false) }, enabled = state.opKeys.isNotEmpty() && !state.opKeyBusy, modifier = Modifier.testTag("opTestKey")) {
            Text(if (state.opKeyBusy) "Testing…" else "Test key")
        }
        androidx.compose.material3.OutlinedButton(onClick = { reportActions.onTestOpKey(true) }, enabled = state.opKeys.isNotEmpty() && !state.opKeyBusy, modifier = Modifier.testTag("opShareSample")) {
            Text("Test and share sample")
        }
    }
    state.opKeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("opKeyNote"),
            color = if (state.opKeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow(com.tjshea.vigilant.app.OpText.SWITCH_TITLE, com.tjshea.vigilant.app.OpText.switchSub(state.opKeys.isNotEmpty()), s.oddsPapi, tag = "oddsPapiSwitch") { v -> onUpdate { it.copy(oddsPapi = v) } }
    if (s.oddsPapi) {
        SwitchRow("Extra books: Circa, SuperBook, bet365", "Price the fair line with them too (each must show both sides and be fresh, like every book).", s.opExtraBooks, tag = "opExtraSwitch") { v -> onUpdate { it.copy(opExtraBooks = v) } }
        SwitchRow("Every book for the fair average", "Price the fair line with every book OddsPapi carries, not just the books picked in Settings: each is devigged on its own and averaged (Market average / Blend), regional clones of one operator count once, and wide-margin or stale books are still left out. Heavier replies.", s.gates.opAllBooks, tag = "opAllBooksSwitch") { v -> onUpdate { it.copy(gates = it.gates.copy(opAllBooks = v)) } }
        SwitchRow("Alternate lines", "Read every game in depth for each book's alternate spreads and totals (one request a game). Off: the main lines of a whole league come in one request.", s.opAltLines, tag = "opAltSwitch") { v -> onUpdate { it.copy(opAltLines = v) } }
        Hint(com.tjshea.vigilant.app.OpText.REPLACES)
    }
}

/**
 * Settings › Cloud scan (Tj, 2026-10-14): "use the phone only to set the settings and retrieve the scanned results. GitHub will provide all the power." The scan runs in a GitHub Actions job
 * (`cloud-scan.yml`) under THESE settings; this phone reads its list. The same GitHub token also saves every bet, bid and the scan study's days to the repository's `phone-data` branch, so Claude can
 * analyse them. The token is sealed in this phone's secure storage and only ever sent to api.github.com.
 */
@Composable
private fun ColumnScope.CloudPage(state: UiState, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val u = state.cloudUi
    val keyNow = rememberNow(30_000)
    Intro("GitHub's computers (free for this public repository) run Vigilant's scanner with the APIs you sent them keys for, under exactly these settings, and send this phone the +EV list. Pull to refresh and Scan ask GitHub for a new scan; the Feed shows its progress and how fresh each price is. Stays on this phone: live games, every order, Pinnodds and your Kalshi key. BoltOdds' key and a read-only Novig key made for GitHub can go there for faster scanning.")
    // 1. the token
    SectionTitle("GitHub token")
    var token by remember { mutableStateOf("") }
    Text(
        if (u.hasToken) "Saved (ends …${u.tokenTail}), sealed in this phone's secure storage and never shown again." else "None saved. Create a fine-grained token for tjshea90/novig with Contents, Actions and Secrets all set to read and write, and paste it here.",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudTokenState"),
    )
    OutlinedTextField(
        value = token, onValueChange = { token = it.trim() }, label = { Text("GitHub token") }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("cloudToken"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onSaveCloudToken(token); token = "" }, enabled = token.length >= 20, modifier = Modifier.testTag("cloudSave")) { Text("Save token") }
        Button(onClick = reportActions.onTestCloud, enabled = u.hasToken, modifier = Modifier.testTag("cloudTest")) { Text("Test") }
        androidx.compose.material3.OutlinedButton(onClick = reportActions.onRemoveCloudToken, enabled = u.hasToken, modifier = Modifier.testTag("cloudRemove")) { Text("Remove") }
    }
    u.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudNote")) }
    // 2. the scan
    SectionTitle("Scan")
    SwitchRow(
        "Scan on GitHub", "On: this phone makes no scan of its own and calls no odds API for it. Off: the app is what it was.",
        s.cloudScan, tag = "cloudScanSwitch", enabled = u.hasToken,
    ) { v -> onUpdate { it.copy(gates = it.gates.copy(cloudScan = v)) } }
    SwitchRow(
        "Only 8 am to 1 am Eastern", "GitHub's engine starts by itself at 8:00 and stops at 1:00 the next morning America/New_York every day (daylight saving included). Off: it runs round the clock.",
        s.cloudHours, tag = "cloudHoursSwitch", enabled = u.hasToken,
    ) { v -> onUpdate { it.copy(gates = it.gates.copy(cloudHours = v)) } }
    if (s.cloudScan && !s.cloudDrives) {
        Text(
            "Paused with scanning: GitHub's engine stands down until you resume.",
            style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudPausedNote"),
        )
    } else if (s.cloudScan) {
        val now = rememberNow(1_000)
        val v = com.tjshea.vigilant.data.cloud.CloudText.view(u.status, u.requestedAtMs, now, u.why)
        Text(v.headline + (v.detail?.let { "\n$it" } ?: ""), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudLine"))
        com.tjshea.vigilant.data.cloud.CloudText.settings(u.status, u.pushedRev, u.pushedAtMs, now)?.let { (lvl, text) ->
            Text(text, style = MaterialTheme.typography.bodySmall, color = if (lvl == com.tjshea.vigilant.data.cloud.CloudText.Level.GOOD) Edge.colors.positive else Edge.colors.warning, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudSettingsSync"))
        }
        if (s.bidsFromCloud) Text(
            "Bids are on: GitHub picks the candidate lines (every fair-odds check done there); this phone reads Novig's order books for them and posts, moves and cancels the bids with your wallet and limits.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudBidsNote"),
        )
        Button(onClick = reportActions.onStartCloud, enabled = u.hasToken, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudStart")) { Text("Start the engine now") }
        // Run / Stop by hand (Tj, 2026-10-17): outside the engine's hours too; the schedule (8 am to 1 am Eastern, the 15-minute start, the hand-overs) is untouched and resumes by itself.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
            Button(onClick = reportActions.onRunCloudOutsideHours, enabled = u.hasToken, modifier = Modifier.testTag("cloudRunNow")) { Text("Run now (even outside its hours)") }
            OutlinedButton(onClick = reportActions.onStopCloud, enabled = u.hasToken, modifier = Modifier.testTag("cloudStopNow")) { Text("Stop now") }
        }
        Text(
            u.manualWords ?: "Following its schedule: 8 am to 1 am Eastern. Run starts it at any hour; Stop keeps it off until its next scheduled opening, then it starts by itself.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudManualNote"),
        )
    }
    // 3. the API keys the engine uses
    SectionTitle("API keys for GitHub")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = reportActions.onSendCloudKeys, enabled = u.hasToken, modifier = Modifier.testTag("cloudSendKeys")) { Text("Send my API keys to GitHub") }
    }
    Hint("Every key saved on this phone for an API that is on, BoltOdds included, sealed here before it leaves (GitHub keeps secrets write-only). Pinnodds and Kalshi are never sent. Sent again by itself when a key changes while the scan is on GitHub.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = reportActions.onSendNovigKey, enabled = u.hasToken, modifier = Modifier.testTag("cloudSendNovigKey")) { Text(if (u.novigKeyMadeAtMs > 0L) "Send my Novig key again" else "Send a Novig read-only key") }
        if (u.novigKeyMadeAtMs > 0L) OutlinedButton(onClick = reportActions.onRemoveNovigKey, enabled = u.hasToken, modifier = Modifier.testTag("cloudRemoveNovigKey")) { Text("Remove it") }
    }
    Hint(
        if (u.novigKeyMadeAtMs > 0L) "A read-only Novig key made ${Format.age(u.novigKeyMadeAtMs, keyNow)} is on GitHub for the engine alone. It can read prices; it cannot bet or move money. Remove it to put the engine back on Novig's public routes."
        else "Makes ONE new read-only key in your Novig account for GitHub's engine alone (it can read prices, it cannot bet or move money; your trading and management keys never leave this phone), so the engine reads Novig at the key's own speed instead of the public routes' about 2 prices a second.",
    )
    // The subaccount's TRADING key for GitHub's autonomous orders (TCL20c): replaces the live trading key, so it asks twice.
    var confirmTrade by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        if (!confirmTrade) Button(onClick = { confirmTrade = true }, enabled = u.hasToken && !u.tradeKeyOnGitHub, modifier = Modifier.testTag("cloudSendTradeKey")) { Text(if (u.tradeKeyOnGitHub) "Trading key is on GitHub" else "Send my Novig trading key…") }
        else {
            Button(onClick = { confirmTrade = false; reportActions.onSendTradeKey() }, modifier = Modifier.testTag("cloudConfirmTradeKey")) { Text("Yes, replace it and send") }
            OutlinedButton(onClick = { confirmTrade = false }) { Text("Cancel") }
        }
        if (u.tradeKeyOnGitHub) OutlinedButton(onClick = reportActions.onRemoveTradeKey, enabled = u.hasToken, modifier = Modifier.testTag("cloudRemoveTradeKey")) { Text("Remove it") }
    }
    Hint(
        if (confirmTrade) "This REVOKES the Vigilant subaccount's current trading key at Novig (up to a minute without betting), makes a new one this phone also uses, and sends it to GitHub. It can place orders on the Vigilant subaccount; it can never move money (your management key stays on this phone). Do it with no bet in flight."
        else if (u.tradeKeyOnGitHub) "The subaccount's trading key is on GitHub for the engine (made ${Format.age(u.tradeKeyMadeAtMs, keyNow)}). The engine orders with it only when Cloud trading is Paper or Live. Remove it to take that away."
        else "Gives the engine the ability to place orders by itself. Cloud trading stays Off until you switch it on; nothing is ordered by this button.",
    )
    u.keysNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudKeysNote")) }
    u.status?.novigRoute?.takeIf { it.isNotBlank() && s.cloudScan }?.let { Text("GitHub reads Novig: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudNovigRoute")) }
    // Which APIs are working on GitHub right now (TCL22d): the engine's own per-feed answers of its last scan, with the live status page on GitHub (also shows each key's usage meter) one tap away.
    u.status?.feedReport?.takeIf { it.isNotEmpty() && s.cloudScan }?.let { fr ->
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val lines = fr.groupBy { com.tjshea.vigilant.data.cloud.CloudDashboard.providerOf(it.id) }.entries.joinToString("\n") { (p, feeds) ->
            val name = com.tjshea.vigilant.data.keys.QuotaPolicy.ALL.firstOrNull { it.id == p }?.displayName ?: p
            "${com.tjshea.vigilant.data.cloud.CloudDashboard.health(feeds, true).label}  $name: " + feeds.joinToString(" · ") { "${it.id} ${it.fetched}+${it.reused}" } +
                (feeds.firstNotNullOfOrNull { it.error ?: it.heldBack }?.let { e -> " (${e.take(50)})" } ?: "")
        }
        Text("APIs working on GitHub, last scan (leagues fresh+reused):\n$lines", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudApiHealth"))
        OutlinedButton(onClick = { runCatching { uriHandler.openUri("https://github.com/tjshea90/novig/blob/cloud-feed/DASHBOARD.md") } }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudOpenDashboard")) { Text("Open the live status page on GitHub") }
    }
    u.status?.ranking?.takeIf { it.isNotBlank() && s.cloudScan }?.let { Text("APIs on GitHub, best first (measured freshness and accuracy):\n$it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudRanking")) }
    // 3b. the engine's own trading (TCL20d): off / paper / live, and how it sizes (smart fractional Kelly)
    EngineTradingSection(state, onUpdate)
    // 4. history for Claude
    SectionTitle("History")
    SwitchRow(
        "Save bets, bids and history to GitHub", "Every 15 minutes: every bet and bid and the scan study go to the repository's phone-data branch, kept for good, for Claude to analyse. Works with the scan on or off; needs the token.",
        s.cloudSave, tag = "cloudSaveSwitch", enabled = u.hasToken,
    ) { v -> onUpdate { it.copy(gates = it.gates.copy(cloudSave = v)) } }
    if (u.hasToken && s.cloudSave) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = reportActions.onSaveCloudNow, modifier = Modifier.testTag("cloudSaveNow")) { Text("Save now") }
            Text(u.saveLine.ifBlank { "Nothing sent yet this session." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("cloudSaveLine"))
        }
    }
    Hint("The phone looks at GitHub every 5 s (3 s while a scan you asked for runs); an unchanged answer is free, so the token's 5,000 requests an hour are never near." + (u.rateRemaining?.let { " Left this hour: $it." } ?: ""))
}

/**
 * Settings › Kalshi (Tj, 2026-10-13; KALSHI_API.md): the Key ID and private key of an API key (Ed25519 or RSA), a Test key (the signed account call, and the socket's signed handshake), the API switch and the
 * socket switch. With a key the reads are signed and paced by the account's own budget, and the socket pushes every price change so scans read Kalshi without asking it. The private key is kept in the app's
 * own file and is never shown again, logged or shared.
 */
@Composable
private fun ColumnScope.KalshiPage(state: UiState, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro("Kalshi is the exchange whose prices Vigilant reads for fair odds (game lines, props, spreads and totals). Reading is free without a key. A key (kalshi.com › Profile › API keys; Ed25519 recommended) signs the reads, which then use the dedicated API host and the account's own read budget, and lets Vigilant keep Kalshi's WebSocket open, which pushes every price change in real time.")
    var keyId by remember(state.kalshiKeyId) { mutableStateOf(state.kalshiKeyId) }
    var pem by remember { mutableStateOf("") }
    Text(
        when {
            state.kalshiHasKey && state.kalshiAlgo == null -> "A key is saved but this phone can no longer open it (it was sealed by another phone's secure storage). Save it again."
            state.kalshiHasKey -> "Saved: Key ID …${state.kalshiKeyId.takeLast(6)} (${state.kalshiAlgo}). The private key is sealed in this phone's secure storage, left out of backups, and not shown again."
            else -> "No key saved: Kalshi is read without one."
        },
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("kalshiKeyState"),
    )
    OutlinedTextField(
        value = keyId, onValueChange = { keyId = it.trim() }, label = { Text("Key ID") }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("kalshiKeyId"),
    )
    OutlinedTextField(
        value = pem, onValueChange = { pem = it }, label = { Text("Private key (paste the whole file)") }, minLines = 3, maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("kalshiPem"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onSaveKalshiKey(keyId, pem); pem = "" }, enabled = keyId.isNotBlank() && pem.isNotBlank(), modifier = Modifier.testTag("kalshiSave")) { Text("Save key") }
        Button(onClick = reportActions.onTestKalshiKey, enabled = state.kalshiHasKey && !state.kalshiKeyBusy, modifier = Modifier.testTag("kalshiTest")) { Text(if (state.kalshiKeyBusy) "Testing…" else "Test key") }
        androidx.compose.material3.OutlinedButton(onClick = reportActions.onRemoveKalshiKey, enabled = state.kalshiHasKey, modifier = Modifier.testTag("kalshiRemove")) { Text("Remove") }
    }
    state.kalshiKeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("kalshiKeyNote"),
            color = if (state.kalshiKeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow("Use Kalshi", "Off: Kalshi is never called, by any scan, bid or check.", s.useKalshi, tag = "kalshiSwitch") { v -> onUpdate { it.copy(useKalshi = v) } }
    SwitchRow(
        "Kalshi socket", if (state.kalshiHasKey) com.tjshea.vigilant.app.ApiEntry.KALSHI.socketBlurb else "Needs a saved key. " + com.tjshea.vigilant.app.ApiEntry.KALSHI.socketBlurb,
        s.gates.kalshiSocket, tag = "kalshiSocketSwitch", enabled = s.useKalshi,
    ) { v -> onUpdate { it.copy(gates = it.gates.copy(kalshiSocket = v)) } }
    Hint("Kalshi's docs: reads cost 10 tokens each against the account's bucket (a Basic account has 200 tokens a second, so 20 reads); a refused request (429) is retried with backoff. Vigilant paces itself at half the bucket at most. Diagnostics shows the tier, the socket's state, the markets it holds and any error codes.")
}

/**
 * Settings › BoltOdds Pro (Tj, 2026-10-12; BOLTODDS_API.md): the key list, a Test key that says what the key carries (and listens on a socket for 15 s) and saves a sample for Claude, the one switch, and the
 * option to price with every book BoltOdds lists. On, two WebSockets (the lines, the props) open while scans use them and the fair line reads what they have delivered. Off: nothing is sent and nothing is open.
 */
@Composable
private fun ColumnScope.BoltOddsPage(state: UiState, keys: KeyActions, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro(com.tjshea.vigilant.app.BoltOddsText.INTRO)
    KeyListEditor(ApiProvider.BOLTODDS, state.boltKeys, keys, "Add a BoltOdds key")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onTestBoltKey(false) }, enabled = state.boltKeys.isNotEmpty() && !state.boltKeyBusy, modifier = Modifier.testTag("boltTestKey")) {
            Text(if (state.boltKeyBusy) "Testing (about 40 s)…" else "Test key")
        }
        androidx.compose.material3.OutlinedButton(onClick = { reportActions.onTestBoltKey(true) }, enabled = state.boltKeys.isNotEmpty() && !state.boltKeyBusy, modifier = Modifier.testTag("boltShareSample")) {
            Text("Test and share sample")
        }
    }
    state.boltKeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("boltKeyNote"),
            color = if (state.boltKeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow(com.tjshea.vigilant.app.BoltOddsText.SWITCH_TITLE, com.tjshea.vigilant.app.BoltOddsText.switchSub(state.boltKeys.isNotEmpty()), s.gates.useBoltOdds, tag = "boltSwitch") { v -> onUpdate { it.copy(gates = it.gates.copy(useBoltOdds = v)) } }
    SwitchRow(com.tjshea.vigilant.app.BoltOddsText.ALL_BOOKS_TITLE, com.tjshea.vigilant.app.BoltOddsText.ALL_BOOKS_SUB, s.gates.boltAllBooks, tag = "boltAllBooks") { v -> onUpdate { it.copy(gates = it.gates.copy(boltAllBooks = v)) } }
    if (s.gates.useBoltOdds) Hint(com.tjshea.vigilant.app.BoltOddsText.NOTES)
}

/**
 * Settings › Unabated (free) (Tj, 2026-10-11; UNABATED_API.md): the key list, a Test key that says what the Free key carries and saves a sample for Claude, and the one switch. On, Unabated joins the scan LAST in
 * line and prices only the lines no better feed sent. Off: the app as it was.
 */
@Composable
private fun ColumnScope.UnabatedPage(state: UiState, keys: KeyActions, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro(com.tjshea.vigilant.app.UnabatedText.INTRO)
    KeyListEditor(ApiProvider.UNABATED, state.unKeys, keys, "Add an Unabated key")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onTestUnabatedKey(false) }, enabled = state.unKeys.isNotEmpty() && !state.unKeyBusy, modifier = Modifier.testTag("unTestKey")) {
            Text(if (state.unKeyBusy) "Testing…" else "Test key")
        }
        androidx.compose.material3.OutlinedButton(onClick = { reportActions.onTestUnabatedKey(true) }, enabled = state.unKeys.isNotEmpty() && !state.unKeyBusy, modifier = Modifier.testTag("unShareSample")) {
            Text("Test and share sample")
        }
    }
    state.unKeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("unKeyNote"),
            color = if (state.unKeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow(com.tjshea.vigilant.app.UnabatedText.SWITCH_TITLE, com.tjshea.vigilant.app.UnabatedText.switchSub(state.unKeys.isNotEmpty()), s.unabated, tag = "unabatedSwitch") { v -> onUpdate { it.copy(unabated = v) } }
    if (s.unabated) Hint(com.tjshea.vigilant.app.UnabatedText.NOTES)
}

/**
 * Settings › SX Bet (Tj, 2026-10-18; SXBET_API.md): the read-only API key (scope "Market data: read" only, made at sx.bet > Account > API) and the one switch. On, SX Bet's exchange odds join the sharp books;
 * the markets are public, the prices and the real-time socket need the key. Off: nothing is sent to SX Bet.
 */
@Composable
private fun ColumnScope.SxBetPage(state: UiState, keys: KeyActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro("SX Bet is a peer-to-peer sports exchange. Vigilant reads its order books as a sharp price. Create a key at sx.bet > Account > API with ONLY the \"Market data: read\" scope (never trading or withdraw), paste it here, then Test it in Settings > APIs.")
    KeyListEditor(ApiProvider.SXBET, state.sxKeys, keys, "Add an SX Bet key")
    SwitchRow("Use SX Bet", if (state.sxKeys.isEmpty()) "Needs a saved read-only key to price anything" else "On: its odds join the sharp books", s.gates.useSxBet, tag = "sxbetSwitch") { v -> onUpdate { it.copy(gates = it.gates.copy(useSxBet = v)) } }
}

/**
 * Settings › OddsPapi Free (v4) (Tj, 2026-10-11; ODDSPAPI_API.md addendum): the free key's own page, apart from the v5 one. Test key reads the plan's real allowance for free and says which page a key belongs
 * on. On, Pinnacle's closing line for your bets is read from OddsPapi's free historical odds (a few cached requests find the game), after v5 and SportsGameOdds and before ParlayAPI's credits.
 */
@Composable
private fun ColumnScope.OddsPapiFreePage(state: UiState, keys: KeyActions, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    Intro(com.tjshea.vigilant.app.Op4Text.INTRO)
    KeyListEditor(ApiProvider.ODDSPAPI_FREE, state.op4Keys, keys, "Add an OddsPapi Free (v4) key")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = { reportActions.onTestOddsPapiFreeKey(false) }, enabled = state.op4Keys.isNotEmpty() && !state.op4KeyBusy, modifier = Modifier.testTag("op4TestKey")) {
            Text(if (state.op4KeyBusy) "Testing…" else "Test key")
        }
        androidx.compose.material3.OutlinedButton(onClick = { reportActions.onTestOddsPapiFreeKey(true) }, enabled = state.op4Keys.isNotEmpty() && !state.op4KeyBusy, modifier = Modifier.testTag("op4ShareSample")) {
            Text("Test and share sample")
        }
    }
    state.op4KeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("op4KeyNote"),
            color = if (state.op4KeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow(com.tjshea.vigilant.app.Op4Text.SWITCH_TITLE, com.tjshea.vigilant.app.Op4Text.switchSub(state.op4Keys.isNotEmpty()), s.oddsPapiFree, tag = "oddsPapiFreeSwitch") { v -> onUpdate { it.copy(oddsPapiFree = v) } }
    if (s.oddsPapiFree) {
        Hint(com.tjshea.vigilant.app.Op4Text.NOTES)
        Text("Fair odds from whole games: requests a day (at most)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        // The plan is 250 requests a MONTH: chips above one day's even share (about 8) only ever promised what the app could not keep (the v0.89.34 file: "250 a day" spent 220 in one day).
        ChoiceChips(listOf(0.0, 2.0, 5.0, 10.0), s.gates.op4Fair.toDouble().coerceAtMost(10.0), { if (it == 0.0) "Off" else "${it.toInt()}" }) { v -> onUpdate { it.copy(gates = it.gates.copy(op4Fair = v.toInt())) } }
        Hint(com.tjshea.vigilant.app.Op4Text.FAIR_HINT)
        SwitchRow("Every book for the fair average", "Ask for every book the plan carries (about 300) in each game's one request, not only the books picked in Settings. Each is devigged on its own and averaged; regional clones count once.", s.gates.opAllBooks, tag = "op4AllBooksSwitch") { v -> onUpdate { it.copy(gates = it.gates.copy(opAllBooks = v)) } }
    }
}

/**
 * Settings › APIs & keys (Tj, 2026-10-11): every outside API in one list. The switch decides whether the app may call it at all (off = never called anywhere: scans, bids, closes, scores, tests); the small Test
 * button asks the API whether each of its keys is active and accepted (and reads the usage it offers into the meter); "Test active APIs" tests the combination the switches make. Under each API: what is left
 * on every key, from the API's own prompt where it has one, and a button to its own page for its keys and options (v0.89.35: the one home for them).
 */
@Composable
private fun ColumnScope.ApisPage(state: UiState, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit, onOpenPage: (SettingsPage) -> Unit = {}) {
    val s = state.settings
    val now = rememberNow(30_000)
    val views = meterViews(state, now).associateBy { it.policy.id }
    Intro("Turn APIs on and off here. Switch an API off and the app stops using it everywhere and adapts to the ones that are on; the first key that is spent or refused is skipped for the next one, automatically. Test checks each key; Test active APIs checks the combination the switches make and reads one league through every source in play.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Button(onClick = reportActions.onTestActiveApis, enabled = !state.apiTestBusy, modifier = Modifier.testTag("testActiveApis")) { Text(if (state.apiTestBusy) "Testing…" else "Test active APIs") }
        OutlinedButton(onClick = reportActions.onRefreshUsage, enabled = !state.usageBusy, modifier = Modifier.testTag("refreshUsage")) { Text(if (state.usageBusy) "Reading…" else "Refresh usage") }
    }
    val everyOn = s.gates.opAllBooks && s.gates.sgoAllBooks && s.gates.propLineAllBooks
    SwitchRow(
        "Every book from every API that has many",
        "Price the fair average with every book OddsPapi (v5 and Free), SportsGameOdds and PropLine carry, not only the picked ones (The Odds API and ParlayAPI stay on their picked books: more books there cost more credits). Each book is devigged on its own and averaged; clones count once; wide-margin and stale books are still left out.",
        everyOn, tag = "everyBookSwitch",
    ) { v -> onUpdate { it.copy(gates = it.gates.copy(opAllBooks = v, sgoAllBooks = v, propLineAllBooks = v)) } }
    state.apiTestLines?.let { lines ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("apiTestReport")) {
            Column(Modifier.padding(10.dp)) {
                lines.forEach { l ->
                    Text(
                        l, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = when { l.startsWith("FAIL") -> MaterialTheme.colorScheme.error; l.startsWith("EMPTY") -> Edge.colors.warning; else -> MaterialTheme.colorScheme.onSurface },
                    )
                }
            }
        }
    }
    SectionTitle("Sockets")
    Intro("An API with a WebSocket has a second switch here. Socket on: the app keeps that connection open for real-time prices. Socket off with the API still on: the regular requests, no socket. The API off: never called at all.")
    SwitchRow(
        "Novig live feed socket", "On: the Vigilant read key's market feed (book / best-bid) stays open and scans read the books it holds. Off: every scan reads Novig's books by request (slower, no pushes). Orders and their results have their own switches under Betting & Novig.",
        s.gates.novigSocket, tag = "novigSocketSwitch",
    ) { v -> onUpdate { it.copy(gates = it.gates.copy(novigSocket = v)) } }
    com.tjshea.vigilant.app.ApiEntry.entries.filter { it.hasSocket }.forEach { e ->
        SwitchRow(
            e.title + " socket", e.socketBlurb, e.socketOn(s), tag = "apiSocket-${e.name}", enabled = e.isOn(s),
        ) { v -> onUpdate { it2 -> e.setSocket(it2, v) } }
    }
    SectionTitle("How much is left on each key")
    com.tjshea.vigilant.app.ApiEntry.entries.forEach { e ->
        val keys = e.provider?.let { state.keysOf(it) }.orEmpty()
        val on = e.isOn(state.settings)
        val busy = e.name in state.apiBusy
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            e.blurb + if (e.keyless) "" else if (keys.isEmpty()) " · no key saved" else " · ${keys.size} key${if (keys.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { reportActions.onTestApi(e) }, enabled = !busy && (e.keyless || keys.isNotEmpty()),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).testTag("apiTest-${e.name}"),
                    ) { Text(if (busy) "…" else "Test", style = MaterialTheme.typography.labelMedium) }
                    androidx.compose.material3.Switch(checked = on, onCheckedChange = { v -> onUpdate { it2 -> e.setOn(it2, v) } }, modifier = Modifier.testTag("apiSwitch-${e.name}"))
                }
                if (!on) Text("Off: never called.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.page?.let { page ->
                    TextButton(
                        onClick = { onOpenPage(page) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp).testTag("apiOpen-${e.name}"),
                    ) { Text(if (e.keyless) "Options ›" else if (keys.isEmpty()) "Add a key ›" else "Keys & options ›", style = MaterialTheme.typography.labelMedium) }
                }
                state.apiChecks[e.name]?.forEach { c ->
                    Text(
                        (if (c.ok) "✓ " else "✗ ") + (if (c.masked.isBlank() || c.masked == "no key") "" else c.masked + " · ") + c.message,
                        style = MaterialTheme.typography.labelSmall, color = if (c.ok) Edge.colors.positive else MaterialTheme.colorScheme.error,
                    )
                }
                views[e.policy?.id]?.let { v ->
                    if (v.keys.size > 1) ProviderBar(v)
                    v.keys.forEach { k ->
                        Text(keyLine(e.policy!!, k, now), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        KeyBar(k, Modifier.testTag("apiBar-${e.name}-${k.index}"))
                    }
                }
                if (e.provider != null && e.provider != ApiProvider.PINNODDS) Text(
                    if (e.usagePrompt) "Usage: read from the API's own prompt." else "Usage: this API offers no prompt for it, so the count is the app's own.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Hint("Keys are added and ordered on each API's own page (the button under it); a key refused or spent is skipped for the next one and tried again after its reset.")
    SettingsRowLink("Usage details & keys backup", "The day-by-day chart, every meter, and an export of your keys.", SettingsPage.USAGE, onOpenPage)
}

/** A row that opens another settings page, at the bottom of a page. */
@Composable
private fun SettingsRowLink(title: String, summary: String, page: SettingsPage, onOpenPage: (SettingsPage) -> Unit) = SettingsRow(title, summary, "settingsLink-${page.name}") { onOpenPage(page) }

/**
 * Settings › APIs & keys › Pinnacle (PinnWire, pinnapi) (v0.89.35: moved here from Fair odds & sources). One switch for both services, and their keys in the order they are tried.
 */
@Composable
private fun ColumnScope.PinnaclePage(state: UiState, keys: KeyActions, onUpdate: SettingsUpdate) {
    val s = state.settings
    Intro("Pinnacle is the sharpest book and anchors most fair prices. Two services sell its board: PinnWire first (with Pinnacle's player props), then pinnapi (game lines only). Its live prices through the Pinnodds account are under Live games & research › Pinnodds live.")
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
}

/** Settings › APIs & keys › PropLine: the switch, its keys (1,000 requests a day each) and the every-book option. */
@Composable
private fun ColumnScope.PropLinePage(state: UiState, keys: KeyActions, onUpdate: SettingsUpdate) {
    val s = state.settings
    Intro("PropLine sends about 30 sportsbooks' lines and player props in one feed. A paid key (Hobby 5,000 or Pro 25,000 requests a day) is found by its own daily limit and used first, free keys after it. With a paid key every game's props are read, the +EV scanner at Novig runs on GitHub (the PropLine +EV card on the +EV tab), and PropLine's graded results, closing lines and exports are archived to GitHub for research. The sportsbooks it is asked for are picked under Fair odds.")
    SwitchRow(
        "PropLine",
        if (state.proplineKeys.isEmpty()) "Your sportsbooks below (Pinnacle, DraftKings, FanDuel, BetMGM…) in one feed. Free key at prop-line.com: 1,000 requests a day."
        else "1 request per league per scan for game lines, 1 per game for player props (${gamesLabel(s.propLineGamesPerScan)} a scan on a free key, set under Fair odds; a paid key reads every game while it is within the day's share). 1,000 a day on a free key, 5,000 Hobby, 25,000 Pro.",
        s.usePropLine,
    ) { v -> onUpdate { it.copy(usePropLine = v) } }
    if (s.usePropLine) {
        KeyListEditor(ApiProvider.PROPLINE, state.proplineKeys, keys, "Add a PropLine key")
        SwitchRow("PropLine: every book for the fair average", "Price the fair line with every sportsbook PropLine lists (about 30; one request either way), not only the books picked below. Each is devigged on its own and averaged.", s.gates.propLineAllBooks, tag = "propLineAllBooksSwitch") { v -> onUpdate { it.copy(gates = it.gates.copy(propLineAllBooks = v)) } }
    }
}

/** Settings › APIs & keys › ParlayAPI: the switch and its keys. */
@Composable
private fun ColumnScope.ParlayPage(state: UiState, keys: KeyActions, onUpdate: SettingsUpdate) {
    val s = state.settings
    Intro("ParlayAPI sends Pinnacle and many sportsbooks' game lines, player props and 1st-half lines, Pinnacle's closing lines for your CLV and injury tags. What is left on each key is under APIs & keys.")
    // ParlayAPI (Tj, 2026-09-30, RESEARCH.md §43): Pinnacle and 9 more books, a league's props and alternate lines in one call each, and
    // Pinnacle's closing lines for CLV. A paid key is paced to a day's share of the plan; free keys serve scans after it, down to their last 100.
    SwitchRow(
        "ParlayAPI",
        if (state.parlayKeys.isEmpty()) "Optional, best on its \$5 plan: Pinnacle, ProphetX, BetOnline, bet365, Bovada and the US books, a " +
            "whole league's player props and alternate lines in one call each, and Pinnacle's closing lines for your CLV. Free key at " +
            "parlay-api.com (1,000 credits a month each: add as many as you like, they are used one after another); \$5 a month (20,000) is paced to a day's share."
        else "Pinnacle and 9 more books (3 credits a league; 5 with Pinnacle's alternate lines, bought only when PinnWire and pinnapi are " +
            "off; tennis 3 a tour, with Pinnacle's set lines for set spreads and total sets), every book's player props (3 a league) and 1st-half lines (2 a league, only where Novig lists them) " +
            "each scan, paced to a day's share of your plan (background auto-scans use half of it at most), plus Pinnacle's closing " +
            "lines for your CLV (the last 300 credits are kept for them). Free with it: injury tags on prop bets (1 credit a league when a " +
            "player isn't in its props), Pinnacle's line moves, the credits-a-day chart. Only on a tap: its own picks at Novig (+EV tab, " +
            "10 a league) and a bet's second opinion (5). Keys are used in the order listed: each next key once the one before is spent or at its day's share (free keys down to their last 100, kept for closing lines), and again from key 1 whenever credits reset. Off, spent or gone: the other feeds carry on.",
        s.useParlay,
    ) { v -> onUpdate { it.copy(useParlay = v) } }
    if (s.useParlay) {
        KeyListEditor(ApiProvider.PARLAY, state.parlayKeys, keys, "Add a ParlayAPI key")
    }
}

/** Settings › APIs & keys › The Odds API: the switch, its keys and how long its game lines are re-used. */
@Composable
private fun ColumnScope.OddsApiPage(state: UiState, keys: KeyActions, onUpdate: SettingsUpdate) {
    val s = state.settings
    // PropLine goes first for the same sportsbooks; The Odds API backs it up (RESEARCH.md §23).
    val propLineFirst = s.usePropLine && state.proplineKeys.isNotEmpty()
    Intro("The Odds API sends US sportsbooks and Pinnacle. Its credits are the scarcest, so it is a backup to PropLine and asked only for what PropLine could not give.")
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
}

/** Research: the master switch, the one file to send, the paper lab (Tj, 2026-10-09: "I don't see the paper lab setting"), and the other recorders (burst, live feed test). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ResearchPage(state: UiState, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    Intro("Leave the app open, plugged in, through the games; then tap the button and send Claude the file. Nothing here places an order.")
    SectionTitle("Research mode")
    Hint(com.tjshea.vigilant.app.LabText.RESEARCH_HINT)
    SwitchRow(com.tjshea.vigilant.app.LabText.RESEARCH_TITLE, com.tjshea.vigilant.app.LabText.RESEARCH_SUB, state.settings.researchMode, tag = "researchSwitch") { v -> onUpdate { it.copy(researchMode = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        androidx.compose.material3.Button(onClick = reportActions.onShareResearch, modifier = Modifier.testTag("shareResearch")) { Text(com.tjshea.vigilant.app.LabText.RESEARCH_BUTTON) }
        OutlinedButton(onClick = reportActions.onShareResearchAll, modifier = Modifier.testTag("shareResearchAll")) { Text("Share ALL research data") }
    }
    Hint("Share research file sends only what was recorded since the last time you shared it (the first time: the last 3 days), so it stays under the 30 MB a chat takes. Share ALL sends everything the phone holds and can be much larger.")
    val labShown by androidx.compose.runtime.rememberUpdatedState(reportActions.onFeedRaceShown)
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { labShown(); kotlinx.coroutines.delay(5_000) } }
    SectionTitle("Paper lab")
    Hint(com.tjshea.vigilant.app.LabText.HINT)
    // Research mode runs every recorder whatever their own switches say, so under it they read ON and cannot be turned off here (they used to read OFF while running: Tj, 2026-10-10 settings audit).
    val master = state.settings.researchMode
    val viaMaster = " Running because Research mode is on."
    SwitchRow(com.tjshea.vigilant.app.LabText.SWITCH_TITLE, com.tjshea.vigilant.app.LabText.SWITCH_SUB + if (master) viaMaster else "", state.settings.altLab || master, tag = "altLabSwitch", enabled = !master) { v -> onUpdate { it.copy(altLab = v) } }
    state.labNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("altLabNote")) }
    Hint("Research mode switches the recorders below on too.")

    // ---- The live burst recorder (Tj, 2026-10-06): no orders; RESEARCH.md §95 ----------------------------------------------------------
    if (AppBook.isNovig) {
        SectionTitle("Live burst recorder")
        val burstShown by androidx.compose.runtime.rememberUpdatedState(reportActions.onBurstShown)
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { burstShown(); kotlinx.coroutines.delay(5_000) } }
        Hint(com.tjshea.vigilant.app.BurstText.HINT)
        SwitchRow(com.tjshea.vigilant.app.BurstText.SWITCH_TITLE, com.tjshea.vigilant.app.BurstText.SWITCH_SUB + if (master) viaMaster else "", state.settings.burstRecorder || master, tag = "burstSwitch", enabled = !master) { v -> onUpdate { it.copy(burstRecorder = v) } }
        if (state.settings.burstRecorder || master) {
            Text(com.tjshea.vigilant.app.BurstText.LEAGUES_TITLE, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanSettings.BURST_LEAGUES.forEach { lg ->
                    androidx.compose.material3.FilterChip(
                        selected = lg in state.settings.burstLeagues,
                        onClick = { onUpdate { s -> s.copy(burstLeagues = if (lg in s.burstLeagues) s.burstLeagues - lg else s.burstLeagues + lg) } },
                        label = { Text(lg) }, modifier = Modifier.testTag("burstLeague-$lg"),
                    )
                }
            }
        }
        state.burstNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("burstNote")) }
        BurstTraderSettings(state, onUpdate)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            androidx.compose.material3.Button(onClick = reportActions.onShareBurst, modifier = Modifier.testTag("shareBurstStudy")) { Text(com.tjshea.vigilant.app.BurstText.BUTTON) }
        }
    }

    // ---- The live feed test (Tj, 2026-10-07): which free feed shows a score or an odds move before Novig's price; no orders; RESEARCH.md §106 ------------------------------
    if (AppBook.isNovig) {
        SectionTitle("Live feed test")
        val feedShown by androidx.compose.runtime.rememberUpdatedState(reportActions.onFeedRaceShown)
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { feedShown(); kotlinx.coroutines.delay(5_000) } }
        Hint(com.tjshea.vigilant.app.FeedRaceText.HINT)
        SwitchRow(com.tjshea.vigilant.app.FeedRaceText.SWITCH_TITLE, com.tjshea.vigilant.app.FeedRaceText.SWITCH_SUB + if (master) viaMaster else "", state.settings.feedRace || master, tag = "feedRaceSwitch", enabled = !master) { v -> onUpdate { it.copy(feedRace = v) } }
        state.feedRaceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("feedRaceNote")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            androidx.compose.material3.Button(onClick = reportActions.onShareFeedRace, modifier = Modifier.testTag("shareFeedRace")) { Text(com.tjshea.vigilant.app.FeedRaceText.BUTTON) }
        }
    }
}

/** Tools: Diagnostics, the grading check, About. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ToolsTab(state: UiState, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    Intro("Claude reads your bets, bids, the engine's data and the scan study from GitHub by itself: there is nothing to share. What is left here is for looking at the app's own diagnostics.")
    // ---- Every bet and bid, kept day by day (Tj, 2026-10-11) -----------------------------
    SectionTitle("Bet history")
    Hint(
        "The app keeps a file for every day of bets and every day of bids (all the facts: odds, EV, books, sharp book, stars, liquidity, close, CLV, result). With this on it also copies each day to " +
            "Downloads/Vigilant/bets, so nothing is lost if the app is cleared.",
    )
    SwitchRow(
        "Save each day's bets to Downloads", "Downloads/Vigilant/bets: one file for the bets and one for the bids of each day, updated as results and closing lines arrive.",
        state.settings.vigGuards.betArchive, tag = "betArchiveSwitch",
    ) { v -> onUpdate { it.copy(vigGuards = it.vigGuards.copy(betArchive = v)) } }
    // ---- Diagnostics ---------------------------------------------------------------------
    SectionTitle("Diagnostics")
    Hint("Show report lists what the app recorded (errors with the code that threw them, every call's speed and failures by host, API usage, timings, what auto-bet decided) and a ranked list of what to fix. It never has a key in it.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = reportActions.onDiagnostics, modifier = Modifier.testTag("showDiagnostics")) { Text("Show report") }
        // The ledger check needs the betting key: Novig's own record of each API bet against what the Tracker did with it.
        if (AppBook.isNovig && state.betting.enabled) OutlinedButton(onClick = reportActions.onGradingCheck) { Text("Grading check") }
    }
    // Tj, 2026-10-10: "make a button for me to reset it. This will clear everything in the diagnostics log and start fresh."
    var confirmReset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    OutlinedButton(
        onClick = { if (confirmReset) { confirmReset = false; reportActions.onResetDiagnostics() } else confirmReset = true },
        modifier = Modifier.testTag("resetDiagnostics"),
    ) { Text(if (confirmReset) "Tap again: clear the diagnostics and start fresh" else "Reset diagnostics") }
    Hint(
        "Reset clears the event log, connection log, problems, timings and the recorders' journals (paper lab, bid lab, live bids, Pinnodds live, burst) and starts fresh. Your bets, settings, keys, bids and the scan study are not touched. " +
            "Nothing needs resetting to keep it small: recorder logs older than ${com.tjshea.vigilant.data.diag.DataKeeper.JOURNAL_DAYS} days (the scan study: ${com.tjshea.vigilant.data.diag.DataKeeper.STUDY_DAYS}) are deleted by themselves every few hours, " +
            "and the file is capped in size.",
    )
    if (AppBook.isNovig && state.betting.enabled) {
        Hint("Grading check: what Novig's ledger and positions say about each bet you placed through the API, beside how the Tracker graded it. Run it after a game ends.")
    }

    // ---- The scan study (Tj, 2026-10-03): every bet a scan lists, logged and graded, for Claude to find what beats the close -------------
    val studyShown by androidx.compose.runtime.rememberUpdatedState(reportActions.onStudyShown)
    androidx.compose.runtime.LaunchedEffect(Unit) { studyShown() }
    Hint("The scan study logs and grades every bet a scan lists, with its closing line and result. The phone's study is saved to GitHub with your other history; the GitHub engine keeps its own, wider one.")
    state.studyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("scanStudyNote")) }
    var confirmStudy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    OutlinedButton(
        onClick = { if (confirmStudy) { confirmStudy = false; reportActions.onClearStudy() } else confirmStudy = true },
        modifier = Modifier.testTag("clearScanStudy"),
    ) { Text(if (confirmStudy) "Tap again: delete the whole scan study" else "Clear scan study") }
    Hint("Clear scan study deletes every bet the study logged and graded, and starts it again from now. It is not in your Tracker, so no bet of yours is touched.")
    SwitchRow(StudyText.SWITCH_TITLE, StudyText.SWITCH_SUB, state.settings.scanStudy, tag = "scanStudySwitch") { v -> onUpdate { it.copy(scanStudy = v) } }
    SwitchRow(StudyText.HIDDEN_TITLE, StudyText.HIDDEN_SUB, state.settings.scanStudyHidden, tag = "scanStudyHiddenSwitch") { v -> onUpdate { it.copy(scanStudyHidden = v) } }

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
/**
 * The background scan (Scanning page): one switch ([BackgroundScan]: it runs whatever scanner is on), Vigilant's own scan joining it only when asked (both
 * scanners on), how often, keep awake, and what Android needs for it (notifications, unrestricted battery).
 */
@Composable
private fun ColumnScope.BackgroundScanSection(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val on = BackgroundScan.on(s)
    SectionTitle("Background scan")
    SwitchRow(
        "Keep scanning in the background",
        "Keeps reading for new bets every few seconds or minutes with Vigilant closed or the phone locked: that's what sends alerts and runs auto-bet.",
        on,
        tag = "backgroundScan",
    ) { v -> onUpdate { BackgroundScan.set(it, v) } }
    if (!on && s.autoBet && s.cnoOn) Warn("Auto-bet is on and runs inside the background scan: while this is off, auto-bet places nothing.", "backgroundAutoBetOff")
    if (on && s.scannerNow == ScannerMode.BOTH) {
        SwitchRow(
            "  Also run Vigilant's own scan",
            "Off: only CrazyNinjaOdds' list is read in the background (free). On: Vigilant's own scan runs too, spending API credits like a tap on Scan.",
            BackgroundScan.alsoVigilant(s),
            tag = "backgroundAlsoVigilant",
        ) { v -> onUpdate { BackgroundScan.setAlsoVigilant(it, v) } }
    }
    if (on) {
        Text("Every", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        ChoiceChips(ScanSettings.AUTO_SCAN_SECONDS_CHOICES, s.autoScanSeconds, ScanSettings::intervalLabel) { v -> onUpdate { it.copy(autoScanSeconds = v) } }
        TypedIntField(NumberSpecs.time("seconds", 5, 3600), s.autoScanSeconds, "autoScanField", none = { false }) { v -> onUpdate { it.copy(autoScanSeconds = v) } }
    }
    Hint(autoScanHint(s))
    if (on) {
        SwitchRow("Keep awake (screen stays off)", keepAwakeHint(s), s.autoScanKeepAwake) { v -> onUpdate { it.copy(autoScanKeepAwake = v) } }
    }
    // What Android needs from Tj for it: notifications (the ongoing note) and, so the phone's battery saver can't hold scans back while it sleeps,
    // unrestricted background use.
    var notify by remember { mutableStateOf(com.tjshea.vigilant.app.ScanService.canNotify(context)) }
    var unrestricted by remember { mutableStateOf(ignoresBatteryLimits(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        notify = com.tjshea.vigilant.app.ScanService.canNotify(context)
        unrestricted = ignoresBatteryLimits(context)
        onPauseOrDispose { }
    }
    if (on && !notify) {
        Hint("Notifications are off for Vigilant, so no alert or auto-bet notice can show.")
        OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Allow notifications") }
    }
    if (on && !unrestricted) {
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
    } else if (on) {
        Hint(batteryHint(unrestricted = true))
    }
}

/** Android's notification settings for Vigilant. */
fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Settings that another one makes moot, said where they're set (2026-10-02 ~18:10Z: "look for settings that contradict each other and fix them"). Each
 * returns the sentence, or null when nothing shadows it. Pure, for tests.
 */
object Shadowed {
    /** The alerts' edge under CNO's own smallest edge: CNO never lists those bets. */
    fun alertEdge(s: ScanSettings): String? =
        if (s.cnoOn && s.alertMinEv > 0.0 && s.alertMinEv < s.cnoFilters.minEv - 1e-9)
            "CrazyNinjaOdds' list (its page) already stops at ${Format.percent(s.cnoFilters.minEv, 0)}, so alerts start there, not at ${alertLabel(s.alertMinEv)}."
        else null

    /** The auto-bet's smallest edge under CNO's: those bets never reach it. */
    fun autoBetEdge(s: ScanSettings): String? =
        if (s.cnoOn && s.autoBetMinEv < s.cnoFilters.minEv - 1e-9)
            "CrazyNinjaOdds' list (Settings › CrazyNinjaOdds list) already stops at ${Format.percent(s.cnoFilters.minEv, 0)}, so auto-bet never sees a smaller edge than that."
        else null

    /** The auto-bet's longest odds past CNO's: CNO never lists them. */
    fun autoBetOdds(s: ScanSettings): String? {
        val cno = s.cnoFilters.maxOdds
        if (!s.cnoOn || cno <= 0) return null
        return if (s.autoBetMaxOdds <= 0 || s.autoBetMaxOdds > cno)
            "CrazyNinjaOdds' list (Settings › CrazyNinjaOdds list) stops at +$cno, so auto-bet never sees longer odds than that."
        else null
    }

    /** A shortest odds longer than the longest odds beside it: nothing can pass both (a typed +200 against a +150 limit). */
    fun oddsRange(min: Int, max: Int): String? =
        if (min != 0 && max > 0 && com.tjshea.vigilant.data.novig.trading.AutoBet.tooShort(min, max))
            "Your shortest odds (${Odds.formatAmerican(min)}) are longer than your longest (+$max): nothing can pass both."
        else null

    /** CNO's shortest odds past the auto-bet's: CNO never lists the shorter ones. */
    fun autoBetShortOdds(s: ScanSettings): String? {
        val cno = s.cnoFilters.minOdds
        if (!s.cnoOn || cno == 0) return null
        val auto = s.autoBetMinOdds
        return if (auto == 0 || Odds.americanToDecimal(cno) > Odds.americanToDecimal(auto))
            "CrazyNinjaOdds' list (Settings › CrazyNinjaOdds list) already drops odds shorter than ${Odds.formatAmerican(cno)}, so auto-bet never sees them."
        else null
    }

    /** The CNO list's kinds and the auto-bet's kinds that share nothing: the list shows bets the auto-bet will never place (said where the list is set). */
    fun cnoKinds(s: ScanSettings): String? {
        val shown = s.cnoFilters.scope.kinds
        if (!s.cnoOn || shown.isEmpty() || s.autoBetKinds.isEmpty()) return null
        return if (shown.none { it in s.autoBetKinds }) "None of these kinds is one the auto-bet places (Auto-bet tab › Kinds of bet to place), so it will place nothing from this list." else null
    }

    /** "Games starting within" longer than Days ahead: the scan stops at Days ahead (the list's own window is still the longer one). Said where the window is picked. */
    fun startsWithinCut(s: ScanSettings): String? =
        if (AppBook.isNovig && s.startsWithinHours > s.daysAhead.coerceAtLeast(1) * 24)
            "Scans read only ${s.daysAhead} day${if (s.daysAhead == 1) "" else "s"} ahead (Days ahead, under +EV feed & scan size), so games starting later than that are not scanned."
        else null

    /** Days ahead past the start window: the window decides. */
    fun daysAhead(s: ScanSettings): String? =
        if (AppBook.isNovig && s.startsWithinHours > 0 && s.startsWithinHours < s.daysAhead * 24)
            "Right now \"Games starting within ${s.startsWithinHours}h\" (Scanning) is shorter, so that's how far the scan reads."
        else null
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
    val vigilantEvery = ScanSettings.vigilantEverySeconds(s.autoScanSeconds, s.vigilantGapSeconds)
    val vigilantPerDay = 86_400 / vigilantEvery
    val cnoPart = "CrazyNinjaOdds' list, then Novig's price now and every book's odds for its best bets (the green check's reads), and the books of your " +
        "open bets starting within the hour (the Tracker's closing line, for CLV" +
        (if (AutoScanClock.closingFreshMs(s.autoScanSeconds) != null) "; in their last 15 minutes, once a minute each at most" else "") + ")"
    val vigilantPart = "Vigilant's own scan exactly as the Scan button runs it (" +
        (if (s.maxBooksPerScan >= ScanSettings.NO_LIMIT) "every priced line in ${windowLabel(s.effective().scanWindowHours)}: " else "${s.maxBooksPerScan} Novig prices at most: ") +
        "${scanTime(s.maxBooksPerScan)}). Each scan spends API credits like a tap on Scan: " +
        (if (s.lowUsageNow && s.lowUsagePace == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO)
            "at most $vigilantPerDay scans a day on Auto pace (every ${com.tjshea.vigilant.data.scanner.LowUsageBids.NEAR_GAP_SECONDS / 60} min while a game is inside 3 hours of its start, ${com.tjshea.vigilant.data.scanner.LowUsageBids.FAR_GAP_SECONDS / 60} min while every game is further off, none when nothing is in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(s)} hours)"
        else "$vigilantPerDay scans a day at this setting") +
        (if (vigilantEvery != s.autoScanSeconds) " (it starts at most every ${ScanSettings.intervalLabel(vigilantEvery)}, however fast CNO is read)" else "") +
        // Low API usage bids (RESEARCH.md §92) narrow this scan and set its pace; say so here, where the pace is read.
        (if (s.lowUsageNow) " Low API usage bids are on: this scan reads player props only, in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(s)} hours (the Bids tab's trap guard hours), from your picked sharp books (Bids tab), at the pace set there." else "")
    val fast = if (s.autoScanSeconds < 60) " Under a minute apart is constant background work: more battery." + (if (KeepAwake.active(s)) "" else " Android may space scans out while the phone sits idle (Keep awake, below, prevents that).") else ""
    val notification = " A quiet notification shows while it's on (Scan now, Stop)." +
        if (s.autoBetsNow) " Auto-bet is on: bets that pass your rules are placed with each CNO check (the Auto-bet tab)." else ""
    return when {
        s.autoScansCno && s.autoScansVigilant -> "Every $every, with Vigilant open or closed: $cnoPart, then $vigilantPart.$notification$fast"
        s.autoScansCno -> "Every $every, with Vigilant open or closed: $cnoPart.$notification About $perDay reads of CNO a day, each well under a second of work.$fast" +
            if (s.autoScan.vigilant) " Vigilant's scan is skipped, because the scanner above is on CNO only: no API credits are spent in the background." else ""
        s.autoScansVigilant -> "Every $every, with Vigilant open or closed: $vigilantPart.$notification CrazyNinjaOdds isn't read, because " +
            "the scanner above is on Vigilant only." + fast
        s.killed -> "Everything is stopped by the STOP button: tap RESUME on the red bar to run again."
        s.paused -> "Paused with everything else: resume scanning (the ⏸ button) to run again."
        else -> "Nothing runs in the background: the scanner above is on ${s.scannerNow.displayName}, which leaves nothing for this choice to read. Pick a scanner that is on."
    }
}

/** Which bets alert, at these settings. */
fun alertHint(s: ScanSettings): String =
    if (s.alertMinEv <= 0.0) "Off: no alerts." else "A notification for each new bet at ${Math.round(s.alertMinEv * 100)}% EV or better that several books agree on " +
        "(${CnoBooks.MIN_TWO_SIDED}+ books price both sides and ${CnoBooks.MIN_AGREEING}+ of them alone make it +EV), found by a background scan " +
        "or a scan you left running. Tap it to open Vigilant; its ✓ Placed button tracks the bet from the notification, without " +
        "opening Vigilant (Undo right after). Each bet alerts once; placed and removed bets, and games outside \"Starts within\", never do" +
        (if (s.trapEarlyHours > 0) ", nor games more than ${s.trapEarlyHours} h off (the trap guard, below)." else ".")

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
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, tag: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row toggles (a bigger target than the switch alone, and one control for TalkBack).
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp)
            .let { if (tag != null) it.testTag(tag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A page's opening line: what the page is for, in plain words. */
@Composable
private fun Intro(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
}

/** A setting another one makes moot, or a setup gap: in the warning color. */
@Composable
private fun Warn(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.padding(vertical = 4.dp).testTag(tag))
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
    /** Settings › API usage & keys opened: the providers that can say what's left (ParlayAPI) are asked, for free. */
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

/** Where Pinnacle's prices come from: the Pinnodds socket, or Pinnacle's own website polled for free; the compare run that times one against the other; the pace. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinnFeedChooser(state: UiState, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val w = state.settings.pinnWebsite
    val set = { f: (com.tjshea.vigilant.data.scanner.PinnWebsiteSettings) -> com.tjshea.vigilant.data.scanner.PinnWebsiteSettings -> onUpdate { it.copy(pinnWebsite = f(it.pinnWebsite)) } }
    Text("Where Pinnacle's prices come from", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.tjshea.vigilant.data.scanner.PinnFeedChoice.entries.forEach { c ->
            FilterChip(selected = w.feed == c, onClick = { set { it.copy(feed = c) } }, label = { Text(c.label) }, modifier = Modifier.testTag("pinnFeed-${c.name}"))
        }
    }
    Text(w.feed.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (w.bolt) {
        val g = state.settings.gates
        val setG = { f: (com.tjshea.vigilant.data.scanner.ApiGates) -> com.tjshea.vigilant.data.scanner.ApiGates -> onUpdate { it.copy(gates = f(it.gates)) } }
        Text("Companies that must agree on a line", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 4, 5, 6).forEach { n -> FilterChip(selected = g.boltLiveMinCompanies == n, onClick = { setG { it.copy(boltLiveMinCompanies = n) } }, label = { Text("$n") }, modifier = Modifier.testTag("boltLiveMin-$n")) }
        }
        Text("Each must have pushed within", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20).forEach { n -> FilterChip(selected = g.boltLiveMaxAgeSeconds == n, onClick = { setG { it.copy(boltLiveMaxAgeSeconds = n) } }, label = { Text("$n s") }, modifier = Modifier.testTag("boltLiveAge-$n")) }
        }
        Text("They may disagree by at most", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 5, 8).forEach { n -> FilterChip(selected = g.boltLiveMaxSpreadPoints == n, onClick = { setG { it.copy(boltLiveMaxSpreadPoints = n) } }, label = { Text("$n points") }, modifier = Modifier.testTag("boltLiveSpread-$n")) }
        }
        SwitchRow(
            "Allow REAL orders on the soft-book average",
            "Off (the default) = it decides, journals and follows up but sends nothing, and real live bets and bids stay paper whatever their own switches say. The soft books' average trails the truth by seconds " +
                "and carries no scores, so watch the paper results before turning this on. The edge floor is 4% after Novig's live fee.",
            g.boltLiveReal, tag = "boltLiveReal",
        ) { v -> setG { it.copy(boltLiveReal = v) } }
    }
    if (w.sx) {
        val g = state.settings.gates
        SwitchRow(
            "Allow REAL orders on the SX Bet exchange mid",
            "Off (the default) = it decides, journals and follows up but sends nothing, and real live bets and bids stay paper whatever their own switches say. SX Bet needs its read-only key and socket on (Settings › APIs).",
            g.sxLiveReal, tag = "sxLiveReal",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(sxLiveReal = v)) } }
    }
    SwitchRow("Compare the website feed with the socket", "Runs the free website feed beside the Pinnodds socket and times every price version against it (it drives nothing). Needs the socket on.", w.compare && !w.website, tag = "pinnCompare") { v -> set { it.copy(compare = v) } }
    Text("Website feed pace: a pause between polls of each live game", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1_000, 1_500, 2_000, 3_000, 5_000).forEachIndexed { i, ms ->
            FilterChip(selected = w.pollMs == ms, onClick = { set { it.copy(pollMs = ms) } }, label = { Text("${ms / 1000.0} s".replace(".0 s", " s")) }, modifier = Modifier.testTag("pinnWebsitePoll-$i"))
        }
    }
    Text("Most live games followed", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(8, 16, 24, 40, 60).forEachIndexed { i, n ->
            FilterChip(selected = w.maxGames == n, onClick = { set { it.copy(maxGames = n) } }, label = { Text("$n") }, modifier = Modifier.testTag("pinnWebsiteGames-$i"))
        }
    }
    var key by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(w.key) }
    OutlinedTextField(
        value = key, onValueChange = { key = it.trim(); set { c -> c.copy(key = key) } }, singleLine = true, label = { Text("Website key (leave empty)") },
        supportingText = { Text("The public key Pinnacle's own site sends every visitor. Only change it if the feed says Pinnacle refused it.") }, modifier = Modifier.fillMaxWidth().testTag("pinnWebsiteKey"),
    )
    state.pinnWebsiteStats?.let { st ->
        Text(
            "Website feed: ${st.games} live games followed · ${st.polls} requests (${st.failures} failed, ${st.rateLimited} rate-limited) · a full cycle takes ${st.lastCycleMs} ms (average ${st.avgLatencyMs} ms) · ${st.changes} price changes seen" +
                (if (w.compare && !w.website) " · compare book: ${st.shadowMatchups} matchups, ${st.shadowFrames} frames, ${st.shadowVersions} price changes" else "") + (st.lastError?.let { " · last problem: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp).testTag("pinnWebsiteStats"),
        )
    }
    if (state.pinnRaceLines.isNotEmpty() && w.compare) Text(state.pinnRaceLines.joinToString("\n"), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("pinnRace"))
}

/** Settings › Pinnodds live: the key and its test, the two switches (feed = paper, real bets), the limits, and the engine's status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinnLiveSection(state: UiState, keys: KeyActions, reportActions: ReportActions, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val shown by androidx.compose.runtime.rememberUpdatedState(reportActions.onPinnShown)
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { shown(); kotlinx.coroutines.delay(1_500) } }
    Intro(com.tjshea.vigilant.app.PinnText.HINT)
    PinnFeedChooser(state, onUpdate)
    KeyListEditor(ApiProvider.PINNODDS, state.pinnoddsKeys, keys, com.tjshea.vigilant.app.PinnText.KEY_LABEL)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
        Button(onClick = reportActions.onTestPinnKey, enabled = state.pinnoddsKeys.isNotEmpty() && !state.pinnKeyBusy, modifier = Modifier.testTag("pinnTestKey")) {
            Text(if (state.pinnKeyBusy) "Testing…" else com.tjshea.vigilant.app.PinnText.TEST_BUTTON)
        }
    }
    state.pinnKeyNote?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("pinnKeyNote"),
            color = if (state.pinnKeyOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
    SwitchRow(com.tjshea.vigilant.app.PinnText.FEED_TITLE, com.tjshea.vigilant.app.PinnText.FEED_SUB, s.pinnLive, tag = "pinnLiveSwitch") { v -> onUpdate { it.copy(pinnLive = v) } }
    s.pinnLiveHalted?.let { why ->
        Column(Modifier.padding(vertical = 4.dp).testTag("pinnLiveHalted")) {
            Text("Stopped: $why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onUpdate { it.copy(pinnLiveHalted = null) } }, modifier = Modifier.testTag("pinnLiveResume")) { Text(com.tjshea.vigilant.app.PinnText.RESUME) }
        }
    }
    var confirming by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = s.pinnLiveBet, role = Role.Switch,
            onValueChange = { v -> if (!v) onUpdate { it.copy(pinnLiveBet = false) } else if (s.pinnLive) confirming = true },
        ).padding(vertical = 6.dp).testTag("pinnLiveBetSwitch"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(com.tjshea.vigilant.app.PinnText.BET_TITLE, style = MaterialTheme.typography.bodyMedium)
            Text(com.tjshea.vigilant.app.PinnText.BET_SUB, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = s.pinnLiveBet, onCheckedChange = null, enabled = s.pinnLive || s.pinnLiveBet)
    }
    Text(
        com.tjshea.vigilant.app.PinnText.statusLine(state.pinnLive, state.pinnTrade, s, state.pinnoddsKeys.size),
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp).testTag("pinnLiveNote"),
    )
    if (s.pinnLive) {
        PinnDollarChips("A bet's stake", ScanSettings.PINN_LIVE_STAKE_CHOICES, s.pinnLiveStake, "pinnStake") { v -> onUpdate { it.copy(pinnLiveStake = v) } }
        PinnDollarChips("Most spent on one game", ScanSettings.PINN_LIVE_MAX_GAME_CHOICES, s.pinnLiveMaxGame, "pinnGame") { v -> onUpdate { it.copy(pinnLiveMaxGame = v) } }
        PinnDollarChips("Most spent in a day", ScanSettings.PINN_LIVE_MAX_DAY_CHOICES, s.pinnLiveMaxDay, "pinnDay") { v -> onUpdate { it.copy(pinnLiveMaxDay = v) } }
        PinnDollarChips("Stop for the day when settled bets lose", ScanSettings.PINN_LIVE_HALT_LOSS_CHOICES, s.pinnLiveHaltLoss, "pinnHalt") { v -> onUpdate { it.copy(pinnLiveHaltLoss = v) } }
        Text("Least edge after Novig's fee", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanSettings.PINN_LIVE_MIN_EV_CHOICES.forEach { v ->
                androidx.compose.material3.FilterChip(selected = v == s.pinnLiveMinEv, onClick = { onUpdate { it.copy(pinnLiveMinEv = v) } }, label = { Text(com.tjshea.vigilant.app.PinnText.pct(v)) }, modifier = Modifier.testTag("pinnEv-${(v * 1000).toInt()}"))
            }
        }
        Text("Least Pinnacle move toward the side (within 20 s)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanSettings.PINN_LIVE_MIN_MOVE_CHOICES.forEach { v ->
                androidx.compose.material3.FilterChip(selected = v == s.pinnLiveMinMove, onClick = { onUpdate { it.copy(pinnLiveMinMove = v) } }, label = { Text(com.tjshea.vigilant.app.PinnText.pts(v)) }, modifier = Modifier.testTag("pinnMove-${(v * 1000).toInt()}"))
            }
        }
        Text("What counts as Novig lagging", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.tjshea.vigilant.data.pinnodds.LiveTrigger.entries.forEach { t ->
                androidx.compose.material3.FilterChip(selected = t == s.pinnLiveTrigger, onClick = { onUpdate { it.copy(pinnLiveTrigger = t) } }, label = { Text(t.label) }, modifier = Modifier.testTag("pinnTrigger-${t.name}"))
            }
        }
        Text(s.pinnLiveTrigger.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("How Pinnacle's margin is removed", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(com.tjshea.vigilant.engine.DevigMethod.WORST_CASE, com.tjshea.vigilant.engine.DevigMethod.POWER, com.tjshea.vigilant.engine.DevigMethod.MULTIPLICATIVE).forEach { m ->
                androidx.compose.material3.FilterChip(selected = m == s.pinnLiveDevig, onClick = { onUpdate { it.copy(pinnLiveDevig = m) } }, label = { Text(m.displayName) }, modifier = Modifier.testTag("pinnDevig-${m.name}"))
            }
        }
        Text("Hold off after a score", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanSettings.PINN_HOLDOFF_CHOICES.forEach { v ->
                androidx.compose.material3.FilterChip(selected = v == s.pinnLiveHoldoffSeconds, onClick = { onUpdate { it.copy(pinnLiveHoldoffSeconds = v) } }, label = { Text(if (v == 0) "Off" else "$v s") }, modifier = Modifier.testTag("pinnHoldoff-$v"))
            }
        }
        Text("Novig pauses live betting for a moment after a score. With this on, no live bet is sent for that many seconds after the game's score changes. Diagnostics › Orders by timing shows how long the pause is on your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow("Also pregame moves", "Pregame steam: when Pinnacle's prematch line moves toward a side by 2 points or more in the last 15 minutes, the game is 5 minutes to 6 hours from its start, and Novig's ask is still at the price Pinnacle had before it moved (an order left up), it buys at Novig's ask. Novig charges no fee before the game starts. Paper unless real bets are on.", s.pinnLivePregame, tag = "pinnPregameSwitch") { v -> onUpdate { it.copy(pinnLivePregame = v) } }
    }
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(com.tjshea.vigilant.app.PinnText.CONFIRM_TITLE) },
            text = { Text(com.tjshea.vigilant.app.PinnText.confirm(s)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirming = false; onUpdate { it.copy(pinnLiveBet = true, pinnLiveHalted = null) } }, modifier = Modifier.testTag("pinnLiveConfirm")) { Text("Turn on") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirming = false }, modifier = Modifier.testTag("pinnLiveCancel")) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinnDollarChips(title: String, choices: List<Double>, selected: Double, tag: String, onPick: (Double) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { d ->
            androidx.compose.material3.FilterChip(selected = d == selected, onClick = { onPick(d) }, label = { Text("$" + d.toInt()) }, modifier = Modifier.testTag("$tag-${d.toInt()}"))
        }
    }
    TypedDollarField(NumberSpecs.dollars(title.lowercase(), 1.0, 10_000.0), selected, "$tag-field", onPick)
}


/** The one main switch for every live feature and socket ([com.tjshea.vigilant.app.LiveMaster]); on top of the Live tab. */
@Composable
fun LiveMasterRow(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val on = com.tjshea.vigilant.app.LiveMaster.isOn(s)
    SwitchRow(
        "Live features",
        if (on) "On: live lanes, live bids and live sockets run as each is set. Turn this off to switch ALL of them (Live light, live bids, Pinnodds live, live games, the Novig, BoltOdds, Pinnodds and Kalshi sockets) off at once; turning it back on restores each as it was."
        else "OFF: every live feature and every socket is switched off. Nothing live is read or sent. Turn it on to put each back as it was.",
        on, tag = "liveMasterSwitch",
    ) { v -> onUpdate { com.tjshea.vigilant.app.LiveMaster.set(it, v) } }
}

/** Settings › Safeguards: every protection with what it guards against and a switch, and one button for all of them. */
@Composable
fun SafeguardsPage(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val off = com.tjshea.vigilant.app.Safeguards.off(s)
    Hint(
        if (off.isEmpty()) "All ${com.tjshea.vigilant.app.Safeguards.ALL.size} safeguards are on."
        else "${off.size} of ${com.tjshea.vigilant.app.Safeguards.ALL.size} safeguards are off. Each one exists because of something that lost money or was measured to; turn them on unless you have a reason.",
    )
    Button(onClick = { onUpdate { com.tjshea.vigilant.app.Safeguards.allOn(it) } }, enabled = off.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("safeguardsAllOn")) { Text("Turn all safeguards on") }
    com.tjshea.vigilant.app.Safeguards.ALL.forEach { g ->
        SwitchRow(g.title, g.why, g.isOn(s), tag = "safeguard-${g.key}") { v -> onUpdate { g.set(it, v) } }
    }
}

/**
 * Settings › Cloud scan › Engine trading (Tj, 2026-10-15, TCL20d): what GitHub's engine does with the bets it lists. Off = nothing; Paper = it records the orders it WOULD have sent (no money moves); Live = it orders from
 * the Vigilant subaccount with the trading key sent above. Live needs that key on GitHub and a second tap. While it is not Off the phone's own auto-bet stands down, so one decision is never two orders.
 */
@Composable
private fun EngineTradingSection(state: UiState, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val s = state.settings
    val u = state.cloudUi
    val g = s.gates
    SectionTitle("Engine trading")
    val modes = com.tjshea.vigilant.data.scanner.CloudTradeMode.entries
    var pendingLive by remember { mutableStateOf(false) }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("cloudTradeMode")) {
        modes.forEachIndexed { i, m ->
            SegmentedButton(
                selected = g.cloudTrading == m,
                onClick = {
                    if (m == com.tjshea.vigilant.data.scanner.CloudTradeMode.LIVE && g.cloudTrading != m) pendingLive = true
                    else { pendingLive = false; onUpdate { it.copy(gates = it.gates.copy(cloudTrading = m)) } }
                },
                enabled = u.hasToken && (m != com.tjshea.vigilant.data.scanner.CloudTradeMode.LIVE || u.tradeKeyOnGitHub),
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }, maxLines = 1) }
        }
    }
    if (pendingLive) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            Button(onClick = { pendingLive = false; onUpdate { it.copy(gates = it.gates.copy(cloudTrading = com.tjshea.vigilant.data.scanner.CloudTradeMode.LIVE)) } }, modifier = Modifier.testTag("cloudTradeLiveConfirm")) { Text("Yes, bet real money") }
            OutlinedButton(onClick = { pendingLive = false }) { Text("Cancel") }
        }
    }
    Hint(
        when (g.cloudTrading) {
            com.tjshea.vigilant.data.scanner.CloudTradeMode.OFF -> "GitHub only lists bets. Paper first is the safe way in: it records what it would have bet so the results can be checked before any money moves."
            com.tjshea.vigilant.data.scanner.CloudTradeMode.PAPER -> "GitHub decides and sizes bets with your Auto-bet rules and writes the orders it WOULD have sent. No money moves. Your phone's auto-bet stands down."
            com.tjshea.vigilant.data.scanner.CloudTradeMode.LIVE -> "GitHub places the bets itself, 8 am to 1 am Eastern, from the Vigilant subaccount, sized by Kelly. Your phone's auto-bet stands down. It stops by itself on a location refusal, a loss stop or a short wallet, and says why below."
        } + if (!u.tradeKeyOnGitHub) " Live needs the trading key above on GitHub." else "",
    )
    if (g.cloudTrading == com.tjshea.vigilant.data.scanner.CloudTradeMode.LIVE) {
        SwitchRow(
            "Engine also posts bids",
            "GitHub's engine runs your bid desk too (the Bids tab's slider and rules, the same desk): resting bids under the fair on the games that start too far out for a taker bet. While it is live your phone's own bid desk stands down; if the engine goes quiet the phone takes the bids back.",
            g.cloudBids, tag = "cloudBidsSwitch",
        ) { v -> onUpdate { it.copy(gates = it.gates.copy(cloudBids = v)) } }
        u.status?.takeIf { it.liveNote.isNotBlank() || it.liveStopped.isNotBlank() }?.let { st ->
            Text(
                "Engine live lane: ${st.liveNote}" + (st.liveStopped.takeIf { it.isNotBlank() }?.let { "\nStopped: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = if (st.liveStopped.isNotBlank()) Edge.colors.warning else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudLiveLine"),
            )
        }
        u.status?.takeIf { it.deskNote.isNotBlank() || it.deskStopped.isNotBlank() }?.let { st ->
            Text(
                "Engine bids: ${st.deskNote}" + (st.deskStopped.takeIf { it.isNotBlank() }?.let { "\nStopped: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = if (st.deskStopped.isNotBlank()) Edge.colors.warning else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudBidsLine"),
            )
        }
    }
    u.status?.takeIf { it.tradeMode.isNotBlank() }?.let { st ->
        Text(
            "Engine ${st.tradeMode}: ${st.tradeNote}" + (st.tradeStopped.takeIf { it.isNotBlank() }?.let { "\nStopped: $it" } ?: "") +
                (st.walletSleep.takeIf { it.isNotBlank() }?.let { "\nPaused: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = if (st.tradeStopped.isNotBlank() || st.walletSleep.isNotBlank()) Edge.colors.warning else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("cloudTradeLine"),
        )
    }
    EngineActivity(u)
    fun pct(v: Double) = "${(v * 100).roundToInt()}%"
    @Composable
    fun sizing(label: String, value: Double, range: ClosedFloatingPointRange<Float>, stepsCount: Int, tag: String, set: (com.tjshea.vigilant.data.scanner.ApiGates, Double) -> com.tjshea.vigilant.data.scanner.ApiGates) {
        var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
        Text("$label: ${pct(v.toDouble())}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        Slider(
            value = v, onValueChange = { v = (it * 100).roundToInt() / 100f }, onValueChangeFinished = { onUpdate { s0 -> s0.copy(gates = set(s0.gates, v.toDouble())) } },
            valueRange = range, steps = stepsCount, modifier = Modifier.padding(horizontal = 8.dp).testTag(tag),
        )
    }
    sizing("Share of full Kelly", g.cloudKelly, 0.05f..1f, 18, "cloudKellySlider") { gg, v -> gg.copy(cloudKelly = v) }
    sizing("Most on one bet (of bankroll)", g.cloudBetCap, 0.01f..0.25f, 23, "cloudBetCapSlider") { gg, v -> gg.copy(cloudBetCap = v) }
    sizing("Most on one game", g.cloudGameCap, 0.01f..0.5f, 48, "cloudGameCapSlider") { gg, v -> gg.copy(cloudGameCap = v) }
    sizing("Most at risk at once", g.cloudWalletUse, 0.1f..1f, 18, "cloudWalletUseSlider") { gg, v -> gg.copy(cloudWalletUse = v) }
    sizing("Stop for the day when down", g.cloudDayStop, 0.05f..0.9f, 16, "cloudDayStopSlider") { gg, v -> gg.copy(cloudDayStop = v) }
    Hint("Stakes are a fraction of full Kelly, cut again when the fair odds are old, few books agree or the rating is low; half stakes after a drawdown of half the stop. The sliders move only how much, never which bets: those are your Auto-bet rules.")
}

/**
 * What the GitHub engine did (Tj, 2026-10-15): the wallet it trades from, the totals of its orders and the newest ones, read from its order book as soon as the app opens (CloudLink loop 6). Paper orders are
 * labelled as such; nothing here places or changes an order, so it is safe to look at while the engine runs.
 */
@Composable
private fun EngineActivity(u: com.tjshea.vigilant.app.CloudLink.Ui) {
    val ledger = u.trades ?: return
    val st = u.status
    SectionTitle("What the engine did")
    val now = System.currentTimeMillis()
    val sum = remember(ledger, now / 60_000L) { com.tjshea.vigilant.data.cloud.TradeSummary.of(ledger, now) }
    if (st != null && st.walletCash >= 0.0) {
        val age = ((now - st.walletReadAtMs) / 60_000L).coerceAtLeast(0L)
        Text(
            "Wallet \$${"%.2f".format(st.walletCash)} cash, \$${"%.2f".format(st.walletEquity.coerceAtLeast(st.walletCash))} with open orders (read ${if (age < 1) "just now" else "$age min ago"})",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp).testTag("engineWallet"),
        )
    }
    Text(sum.line(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).testTag("engineTotals"))
    if (sum.newest.isEmpty()) {
        Text("No orders yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
    } else {
        val fmt = remember { java.text.SimpleDateFormat("MMM d h:mm a", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("America/New_York") } }
        sum.newest.forEach { e ->
            val result = when (e.status) { "OPEN", "UNCONFIRMED" -> "open"; else -> e.status.lowercase() + (e.pnl?.let { " " + (if (it < 0) "-" else "+") + "\$${"%.2f".format(kotlin.math.abs(it))}" } ?: "") }
            Text(
                "${fmt.format(java.util.Date(e.atMs))} · ${if (e.mode.equals("paper", true)) "PAPER " else ""}${e.selection} (${e.league}) · \$${"%.2f".format(e.cost)} @ ${"%.0f".format(e.avgPrice * 100)}c · EV ${"%.1f".format(e.evPercent * 100)}% · $result",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
    }
}
