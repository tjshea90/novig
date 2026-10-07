package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.RoundCost
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageDelta
import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.ManagementKeyHint
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigKeyTest
import com.tjshea.vigilant.data.novig.signing.NovigLiveCheck
import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSetup
import com.tjshea.vigilant.app.data.KeystoreVault
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoConfig
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoPick
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoScreened
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.cno.CnoWatch
import com.tjshea.vigilant.data.cno.TapLink
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.app.ui.KillBarText
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanTiming
import com.tjshea.vigilant.data.scanner.SourceReport
import com.tjshea.vigilant.data.teams.PlayerTeams
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.PlacedBet
import com.tjshea.vigilant.data.tracker.PlacedIndex
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.NonCancellable
import com.tjshea.vigilant.app.ui.vigilantAsks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the status line under the title shows. */
data class ScanStatus(
    /** A scan is running (Scan button or pull-to-refresh). */
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    val scannedAtMs: Long? = null,
    val errors: List<String> = emptyList(),
    val backoffSeconds: Int? = null,
    val booksFetched: Int = 0,
    val booksFromCache: Int = 0,
    val booksViaKey: Int = 0,
    /** Of [booksFetched], the ones Novig's websocket pushed through the connected key (no request each). */
    val booksViaPush: Int = 0,
    /** Where the last scan's time went (Settings › Betting & Novig account › Novig API key). */
    val timing: ScanTiming? = null,
    /** The connected key's `read` limit, per second, as Novig reported it (`GET /v3/limits`). */
    val keyReadPerSec: Double? = null,
    val sources: List<SourceReport> = emptyList(),
    /** Leagues picked since the last scan: nothing to show for them until the next one. */
    val unscanned: Set<String> = emptySet(),
    /** A recheck (a few Novig prices re-read, no fair-odds calls) is running. */
    val rechecking: Boolean = false,
    /** The time window the last scan read ([ScanSettings.scanWindowHours]): the feed says when it's wider now. */
    val scannedWindowHours: Int? = null,
)

/** The Novig API key section of Settings. */
data class NovigUi(
    val connection: NovigConnection? = null,
    val busy: Boolean = false,
    /** Progress or result text for setup/test. */
    val message: String? = null,
    val error: String? = null,
    /** The management key saved on this phone (its ID's last four, never the key), or null: setup and transfers use it without asking. */
    val managementKey: ManagementKeyHint? = null,
)

/** A report Tj can read, copy and paste (Settings › Diagnostics & about, Grading check): [busy] while it's being put together. */
data class ReportUi(val title: String, val text: String, val busy: Boolean = false)

data class UiState(
    /** The Diagnostics / Grading check report being shown, if any (Tj, 2026-09-29). */
    val report: ReportUi? = null,
    /** Betting through Novig's API: set up or not, the balance; and the Bet sheet while one is open (Tj, 2026-09-29). */
    val betting: BettingUi = BettingUi(),
    /** What the auto-bet did last cycle and the wallet it saw (Tj, 2026-10-01), for the Auto-bet tab. */
    val autoBetStatus: AutoBettor.Status = AutoBettor.Status(),
    /** Locks on the open API bets, by Novig market (RESEARCH.md §67), and the market one is being placed on now. */
    val locks: Map<String, LockView> = emptyMap(),
    val locking: String? = null,
    /** The "Novig only" filter's own read of Novig's prices is under way. */
    val readingNovig: Boolean = false,
    val betSheet: BetSheetUi? = null,
    val settings: ScanSettings = ScanSettings(),
    val result: ScanResult? = null,
    val feed: List<Opportunity> = emptyList(),
    val status: ScanStatus = ScanStatus(),
    val oddsApiKeys: List<String> = emptyList(),
    val pinnapiKeys: List<String> = emptyList(),
    val pinnwireKeys: List<String> = emptyList(),
    val proplineKeys: List<String> = emptyList(),
    val parlayKeys: List<String> = emptyList(),
    /** Every provider's usage ledger, updated after each call (the meters). */
    val usage: UsageBook = UsageBook(),
    val bets: List<TrackedBet> = emptyList(),
    /** The Tracker's "Check odds now" is running. */
    val checkingOdds: Boolean = false,
    /** While it runs: bets read so far, of how many it reads (null until the first count). */
    val checkProgress: Pair<Int, Int>? = null,
    /**
     * When the last "Check odds now" began (Tj, 2026-09-29): the Tracker's +EV / −EV counter and average EV count only open bets re-read
     * since, so each new check starts at 0. Null = no check yet.
     */
    val checkStartedAtMs: Long? = null,
    /** The Tracker's "Grade now" is reading final scores. */
    val gradingBets: Boolean = false,
    /** The open bet (by id) whose books the Tracker is re-reading, and the one whose Novig link Replace is finding. */
    val rereadingBet: String? = null,
    val replacingBet: String? = null,
    val loaded: Boolean = false,
    val novig: NovigUi = NovigUi(),
    /** CrazyNinjaOdds' +EV list (its own tab, and the mini window). */
    val cno: CnoState = CnoState(),
    /** Every book's price for the CNO bets someone tapped (or the green-check lane read), by row key. */
    val books: Map<String, CnoBooksState> = emptyMap(),
    /** Bets Tj marked placed: hidden from the widget and the CNO list until their game is over. */
    val placed: List<PlacedBet> = emptyList(),
    /** The team of each player bet on CNO's list, by row key ("HOU"), when ESPN's rosters say. */
    val teams: Map<String, String> = emptyMap(),
    /**
     * Injury tags (Tj, 2026-09-30, PARLAY_API.md §6.1): the report of each listed or open prop bet's player who may not play, by the
     * item's key ([com.tjshea.vigilant.data.reference.InjuryTags]: a +EV bet's own key, "cno:<row key>", "bet:<id>").
     */
    val injuries: Map<String, com.tjshea.vigilant.data.reference.Injury> = emptyMap(),
    /** When each listed bet was first seen, by key (a CNO row's key, a Vigilant bet's key): what the trap guard's "first listed" rule reads ([com.tjshea.vigilant.data.scanner.FirstListed]). */
    val firstListed: Map<String, Long> = emptyMap(),
    /** Pinnacle's biggest moneyline moves in the picked leagues, by sport key (ParlayAPI's movers; PARLAY_API.md §6.3). */
    val movers: Map<String, com.tjshea.vigilant.data.reference.MoversBoard> = emptyMap(),
    /** The listed and open team bets whose game moved at Pinnacle, by item key ([com.tjshea.vigilant.data.reference.LineMoves]). */
    val lineMoves: Map<String, com.tjshea.vigilant.data.reference.LineMove> = emptyMap(),
    /** ParlayAPI's own +EV picks at Novig, re-priced at Novig's book (read only on a tap; PARLAY_API.md §6.5). */
    val parlayPicks: com.tjshea.vigilant.app.ui.ParlayPicksUi = com.tjshea.vigilant.app.ui.ParlayPicksUi(),
    /** ParlayAPI's "Second opinion" on the bets Tj asked about, by item key (a +EV bet's key, "cno:<row key>", "bet:<id>"). */
    val opinions: Map<String, com.tjshea.vigilant.app.ui.OpinionUi> = emptyMap(),
    /** ParlayAPI's own usage log, day by day, and where the credits went (Settings › API usage & keys; PARLAY_API.md §6.2). */
    val parlayHistory: com.tjshea.vigilant.data.reference.ParlayAccount.History? = null,
    /** CNO is being kept current right now: its tab or a widget is on screen. */
    val cnoLive: Boolean = false,
    /** CNO bets' Novig links by [com.tjshea.vigilant.data.cno.CnoFeed.linkKey] (a bet both scanners list is shown once). */
    val cnoLinks: Map<String, String> = emptyMap(),
    /** Novig's price now for CNO's listed bets, by row key ([com.tjshea.vigilant.data.cno.NovigLive]). */
    val novigLive: Map<String, com.tjshea.vigilant.data.cno.LivePrice> = emptyMap(),
    /**
     * Every bet Tj already has (✓ placed or ✕ removed in the widget or CNO tab, or tracked from a
     * card), from either scanner: hidden from every list ([PlacedIndex]). Rebuilt when those change.
     */
    val placedIndex: PlacedIndex = PlacedIndex.EMPTY,
    /** What the scan study has logged, in a line for Settings › Diagnostics & about ([StudyText.note]); null until that page asked. */
    val studyNote: String? = null,
    /** The live burst recorder's one line (RESEARCH.md §95), refreshed while Settings › Diagnostics & about is open. */
    val burstNote: String? = null,
    /** The live feed test's line (Settings › Diagnostics & about; RESEARCH.md §106): what it is doing and its verdict, refreshed while that page is open. */
    val feedRaceNote: String? = null,
    /** Why the real-money burst trader is locked (the recorder's proof, in words), or null when it has proved itself; null too until the page asked. */
    val burstProofReason: String? = null,
    /** True once the page has read the proof (so a null [burstProofReason] means proved, not unread). */
    val burstProofRead: Boolean = false,
    /** The leagues the recorder has proved on their own windows: the only ones the trader may trade ([com.tjshea.vigilant.data.novig.burst.BurstStudy.Proof.leagues]). */
    val burstProvedLeagues: Set<String> = emptySet(),
    /** What the burst trader has done this run ([com.tjshea.vigilant.data.novig.trading.burst.BurstTradeStatus]). */
    val burstTrade: com.tjshea.vigilant.data.novig.trading.burst.BurstTradeStatus = com.tjshea.vigilant.data.novig.trading.burst.BurstTradeStatus(),
) {
    /**
     * The +EV feed as of [now]: without EVs whose other books' prices are over a few minutes old
     * (Tj, 2026-09-27: "The other sports books odds MUST be current or at most a few minutes old",
     * RESEARCH.md §24), and only games starting within [ScanSettings.startsWithinHours]. Every screen
     * that offers Vigilant's bets shows this, not [feed].
     */
    fun feedAt(now: Long): List<Opportunity> =
        feed.filter { !it.fairIsOld(now) && settings.startsInWindow(it.event.startsTs, now) }

    /** [r]'s +EV feed under these settings, without the bets Tj already has (Tj, 2026-09-27). */
    fun feedOf(r: ScanResult?): List<Opportunity> = r?.let { placedIndex.visible(it.feed(settings)) } ?: emptyList()

    /** This state with [placedIndex] rebuilt from [placed] and [bets], and the feed re-filtered. */
    fun indexed(now: Long = System.currentTimeMillis()): UiState =
        copy(placedIndex = PlacedIndex.of(placed, bets, now)).let { s -> s.copy(feed = s.feedOf(s.result)) }

    /** Whether CNO's [row] is a bet Tj already has: its ✓/✕, or the same bet placed or tracked from Vigilant's list. */
    fun hasCno(row: CnoRow): Boolean = MiniWindow.cnoKey(row) in placedKeys || placedIndex.has(
        key = MiniWindow.cnoKey(row),
        outcomeId = com.tjshea.vigilant.data.cno.CnoFeed.outcomeIdOf(cnoLinks[com.tjshea.vigilant.data.cno.CnoFeed.linkKey(row)]),
        event = row.event, market = row.market, selection = row.bet, startsTs = row.startsAtMs, league = row.league,
    )

    /** ParlayAPI can be asked for a second opinion: it's on and has a key (5 credits a question, only on a tap). */
    val canAskParlay: Boolean get() = settings.useParlay && parlayKeys.isNotEmpty()

    /** Tj's keys for [provider], in the order they're tried. */
    fun keysOf(provider: ApiProvider): List<String> = when (provider) {
        ApiProvider.THE_ODDS_API -> oddsApiKeys
        ApiProvider.PINNAPI -> pinnapiKeys
        ApiProvider.PINNWIRE -> pinnwireKeys
        ApiProvider.PROPLINE -> proplineKeys
        ApiProvider.PARLAY -> parlayKeys
    }

    /** This state with [provider]'s keys replaced. */
    fun withKeys(provider: ApiProvider, keys: List<String>): UiState = when (provider) {
        ApiProvider.THE_ODDS_API -> copy(oddsApiKeys = keys)
        ApiProvider.PINNAPI -> copy(pinnapiKeys = keys)
        ApiProvider.PINNWIRE -> copy(pinnwireKeys = keys)
        ApiProvider.PROPLINE -> copy(proplineKeys = keys)
        ApiProvider.PARLAY -> copy(parlayKeys = keys)
    }

    /** Novig's live price for [row], when that setting is on and it was read in the last minute (never in Vigilant MGM). */
    fun livePrice(row: CnoRow, now: Long): com.tjshea.vigilant.data.cno.LivePrice? =
        novigLive[row.key]?.takeIf { AppBook.isNovig && settings.cnoLivePrices && now - it.atMs <= com.tjshea.vigilant.data.cno.NovigLive.FRESH_MS }

    /** [placed]'s keys (and the other scanner's key for the same bet), for hiding them. */
    val placedKeys: Set<String> by lazy { placed.flatMapTo(HashSet()) { it.keys } }

    /** [placed]'s families (the same bet at another line), for the "placed O5.5" tag; bets removed with ✕ weren't bet. */
    val placedFamilies: Map<String, PlacedBet> by lazy { placed.filter { !it.hidden && it.family.isNotEmpty() }.associateBy { it.family } }

    /** The CNO view to read: Tj's saved link, or the app's book (Novig; BetMGM in Vigilant MGM) with CNO's defaults. */
    val cnoUrl: String
        get() = CnoView.normalize(settings.cnoViewUrl, AppBook.current.cnoSiteId) ?: CnoView.defaultFor(AppBook.current.cnoSiteId)

    val cnoConfig: CnoConfig get() = CnoConfig(loaded && settings.cnoOn, cnoUrl, settings.cnoRefreshSeconds, settings.cnoFilters)

    /**
     * CNO's list for the current link, through the app's own checks with the current filters
     * (so a changed filter applies at once, before CNO's next read). Null when CNO is off or
     * nothing was read for this link yet.
     */
    fun cnoPicks(now: Long): CnoScreened? =
        cno.snapshot?.takeIf { settings.cnoOn && it.url == cnoUrl && !cnoTooOld(now) }?.let { CnoChecks.screen(it, settings.cnoFilters, now) }

    /**
     * CNO's own odds (its EVs come from other books' prices) are over [Freshness.MAX_QUOTE_AGE_MS]
     * old at [now]: none of its bets are offered until it updates (RESEARCH.md §24).
     */
    fun cnoTooOld(now: Long): Boolean = cno.snapshot?.let { now - it.dataAtMs > Freshness.MAX_QUOTE_AGE_MS } ?: false

    /**
     * A bet's books as shown and compared: a CNO game page read over [Freshness.MAX_QUOTE_AGE_MS]
     * ago isn't (RESEARCH.md §24): it reads as needing a re-read.
     */
    fun booksAt(rowKey: String?, now: Long): com.tjshea.vigilant.data.cno.CnoBooksState? = books[rowKey]?.let { st ->
        val view = st.view ?: return@let st
        if (now - view.fetchedAtMs <= Freshness.MAX_QUOTE_AGE_MS) st
        else st.copy(view = null, error = st.error ?: "These books' odds are over ${Freshness.MAX_QUOTE_AGE_MS / 60_000} minutes old: open the books again to re-read them")
    }

    /**
     * CNO's rows whose players' teams are read from ESPN: none unless CNO's scanner is on (the widget
     * shows in Vigilant only mode too, and an old CNO list mustn't keep rosters loading behind it).
     */
    val cnoTeamRows: List<CnoRow>
        get() = if (!settings.cnoPlayerTeams || !settings.cnoOn) emptyList() else cno.snapshot?.takeIf { it.url == cnoUrl }?.rows ?: emptyList()

    /** Whether CNO bets' books are read: for the green check, or for "only bets the books agree on". */
    val cnoReadsBooks: Boolean get() = settings.cnoCheckBooks || settings.cnoOnlyAgreed

    /**
     * [pick] at Novig's price now ([livePrice]): the price, dollars and EV (CNO's fair odds against
     * that price) the widget, the CNO tab and the bet sheet show. [pick] itself without one.
     */
    fun livePick(pick: CnoPick, now: Long): CnoPick {
        val live = livePrice(pick.row, now) ?: return pick
        return CnoPick(pick.row.copy(odds = live.american, available = live.available ?: pick.row.available), live.ev ?: pick.ev, pick.live)
    }

    /** When [row]'s shown price was read: Novig's live read, else CNO's list. */
    fun priceReadAtMs(row: CnoRow, now: Long): Long? = livePrice(row, now)?.atMs ?: cno.snapshot?.fetchedAtMs

    /**
     * The CNO bets whose Novig price now is read ([com.tjshea.vigilant.data.cno.NovigLive]): the
     * candidates in CNO's order, never filtered by those prices (with "only bets the books agree
     * on", a live price that hides a bet must keep being read, or the bet would flicker back).
     */
    fun livePriceRows(now: Long): List<CnoRow> = cnoCandidates(now).map { it.row }

    /**
     * [cnoPicks] without the bets Tj placed or removed (✕), and only games starting within
     * [ScanSettings.startsWithinHours]: what the green-check lane reads.
     */
    fun cnoCandidates(now: Long): List<CnoPick> =
        cnoPicks(now)?.picks?.filter { settings.startsInWindow(it.row.startsAtMs, now) && !hasCno(it.row) }.orEmpty()

    /** The Games board at [now]: only games starting within [ScanSettings.startsWithinHours]. */
    fun gamesAt(now: Long): List<com.tjshea.vigilant.data.scanner.PricedGame> =
        result?.games.orEmpty().filter { settings.startsInWindow(it.event.startsTs, now) }

    /** The green check: several books price both sides of [pick] and agree it's +EV ([CnoBooks.agrees]). */
    fun cnoAgrees(pick: CnoPick, now: Long = System.currentTimeMillis()): Boolean {
        if (!cnoReadsBooks) return false
        val view = booksAt(pick.row.key, now)?.view ?: return false
        val snap = cno.snapshot ?: return false
        // Judged at the newest Novig price: the live one when it's newer than the list.
        val live = livePrice(pick.row, now)?.takeIf { it.atMs > snap.fetchedAtMs }
        return if (live != null) CnoBooks.agrees(view, pick.row.copy(odds = live.american), pick.live, live.atMs)
        else CnoBooks.agrees(view, pick.row, pick.live, snap.fetchedAtMs)
    }

    /**
     * What the CNO tab, its badge and the widgets list: [cnoCandidates], only the green-check ones
     * when "only bets the books agree on" is on (Tj, 2026-09-27).
     */
    fun cnoShown(now: Long): List<CnoPick> =
        cnoCandidates(now).let { all -> if (settings.cnoOnlyAgreed) all.filter { cnoAgrees(it, now) } else all }

    /**
     * With "only bets the books agree on" on: the bets held back only because their books haven't
     * been read yet (the green-check lane is on its way to them). Zero otherwise.
     */
    fun cnoBeingChecked(now: Long): Int {
        if (!settings.cnoOnlyAgreed) return 0
        return cnoCandidates(now).take(CnoFeed.AGREE_TOP_ONLY_AGREED).count { p ->
            val b = books[p.row.key]
            b?.view == null && b?.error == null
        }
    }
}

/**
 * The whole app's state. Network happens in exactly one place, [scan], and only when Tj taps
 * Scan or pulls to refresh (his rule, 2026-09-25): nothing fetches on launch, on a timer, on a
 * tab change, or when a setting changes. Settings changes re-price from what the last scan
 * fetched, so they're instant and free. The exceptions: CrazyNinjaOdds' list ([watchCno],
 * Tj 2026-09-26), read only while its tab or a widget is on screen, paced by [CnoFeed]; and
 * background auto-scan when Tj turns it on (Tj 2026-09-28; [AutoScanService], not this screen).
 *
 * The scan itself runs in the app's [com.tjshea.vigilant.data.scanner.ScanRunner], not here, so it
 * outlives this screen (Tj switches apps mid-scan); this only mirrors it: progress and partial
 * results while it runs, the report when it ends.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val c = (application as VigilantApp).container

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Serializes settings writes so two fast taps can't interleave read-modify-write. */
    private val settingsMutex = Mutex()

    /**
     * Who is looking at CNO's list right now: "tab" (the CNO tab, Vigilant started), "pip" (the
     * picture-in-picture window), "overlay" (the floating widget, screen on). CNO is read only
     * while someone is (Tj, 2026-09-26: "if I close the cno scanner or the app … nothing is
     * refreshing in the background").
     */
    private val cnoWatch = CnoWatch()

    /** One-shot messages for a toast ("Tracked", save errors). Declared before [init]: its coroutines can run at once. */
    private val _toasts = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: kotlinx.coroutines.flow.SharedFlow<String> = _toasts

    /** Betting through Novig's API: setup, money, the Bet sheet ([ApiBettingController]). */
    val api = ApiBettingController(c, _state, viewModelScope, _toasts)

    init {
        // What the auto-bet did (a few times a minute at most: once per background cycle) for the Auto-bet tab.
        viewModelScope.launch { c.autoBet.status.collect { st -> _state.update { it.copy(autoBetStatus = st) } } }
    }

    init {
        // Every failure shown on screen goes in Diagnostics' "Recent problems" too (Tj, 2026-09-30).
        viewModelScope.launch {
            _toasts.collect { text -> if (Diagnostics.isProblem(text)) runCatching { c.problems.add("Shown on screen", text) } }
        }
    }

    init {
        viewModelScope.launch {
            val stored = c.settingsStore.read()
            val settings = stored.migrate()
            if (settings != stored) runCatching { c.settingsStore.update { settings } }
            // Keys moved, meters, the Novig key: once per process (the background auto-scan may have done it).
            val connection = c.ensureLoaded()
            val keys = ApiProvider.entries.associateWith { c.keyStore.getKeys(it) }
            val bets = c.tracker.all()
            val lastCheck = runCatching { c.lastCheck.read().startedAtMs }.getOrNull()
            _state.update {
                keys.entries.fold(it) { s, (p, k) -> s.withKeys(p, k) }.copy(
                    settings = settings, bets = bets, loaded = true, checkStartedAtMs = it.checkStartedAtMs ?: lastCheck,
                    novig = it.novig.copy(connection = connection),
                )
            }
            // Betting through Novig's API: the Bet buttons show when this phone holds the subaccount's trading key.
            api.refreshEnabled()
            // After the settings, so a scan still running (or finished) is shown under them.
            follow()
        }
        viewModelScope.launch {
            // Settings written outside this screen (the auto-scan notification's Stop): shown at once.
            c.settingsStore.flow.filterNotNull().collect { saved ->
                val s = _state.value
                if (s.loaded && (saved.autoScan != s.settings.autoScan || saved.killed != s.settings.killed || saved.pausedByHand != s.settings.pausedByHand)) {
                    // The kill switch and the Pause, pressed from the notification or the widget, an empty wallet or Resume, show on every tab at once.
                    _state.update { it.copy(settings = it.settings.copy(autoScan = saved.autoScan, killed = saved.killed, killedAtMs = saved.killedAtMs, pausedByHand = saved.pausedByHand)) }
                }
            }
        }
        viewModelScope.launch {
            // A bet tracked anywhere leaves every list at once (Tj, 2026-09-27).
            // Taken at most every TRACKER_MIRROR_MS (a Check odds now saves every 5 bets), with the placed index and the feed rebuilt off the
            // main thread (Tj, 2026-10-01: "I used the check odds now function and the list of open bets got very laggy").
            // (If the marks, the result or the settings change while it's built, it is built again from what's current: [reindex].)
            followThrottled(c.tracker.flow.filterNotNull(), TRACKER_MIRROR_MS) { bets -> _state.reindex { it.copy(bets = bets) } }
        }
        viewModelScope.launch {
            // Every API call is counted as it happens (a scan's 1,500 Novig reads each change the meter): the screen takes the newest count
            // at most once a second, or each read would recompose the whole app mid-scan (Tj, 2026-09-30: "very laggy then crashed").
            followThrottled(c.usage.flow, USAGE_MIRROR_MS) { u -> _state.update { it.copy(usage = u) } }
        }
        viewModelScope.launch {
            runCatching { c.cno.load() }
            c.cno.state.collect { cno -> _state.update { it.copy(cno = cno) } }
        }
        viewModelScope.launch {
            c.cno.books.collect { b -> _state.update { it.copy(books = b) } }
        }
        viewModelScope.launch {
            c.cno.links.collect { l -> _state.update { it.copy(cnoLinks = l) } }
        }
        viewModelScope.launch {
            c.live.prices.collect { p -> _state.update { it.copy(novigLive = p) } }
        }
        viewModelScope.launch {
            val marks = runCatching { c.placed.load() }.getOrNull()
            // ✓ marks made before the Tracker kept them move into it, once (Tj, 2026-09-27).
            if (marks != null && !c.trackerImported.exists()) {
                val moved = runCatching { c.tracker.importPlaced(marks.bets).also { c.trackerImported.createNewFile() } }.getOrDefault(0)
                if (moved > 0) _toasts.tryEmit("Moved $moved earlier ✓ bet${if (moved == 1) "" else "s"} into the Tracker (at \$1 each: change it there)")
            }
            // Results of games that ended while Vigilant was closed, then every 3 h in the background.
            settleBets()
            runCatching { SettleWorker.schedule(getApplication()) }
            c.placed.flow.filterNotNull().collect { b -> _state.reindex { it.copy(placed = b.bets) } }
        }
        viewModelScope.launch {
            runCatching { c.teams.load() }
            // Recomputed only when the list's rows, the switch or the rosters change.
            combine(c.teams.state, state.map { (if (it.settings.cnoPlayerTeams && it.settings.cnoOn) it.cno.snapshot?.rows else null) }.distinctUntilChanged()) { cache, rows ->
                rows?.mapNotNull { r -> PlayerTeams.playerOf(r)?.let { p -> cache.teamOf(r.league, r.event, p) }?.let { r.key to it } }?.toMap() ?: emptyMap()
            }.flowOn(Dispatchers.Default).collect { t -> _state.update { if (it.teams == t) it else it.copy(teams = t) } }
        }
        viewModelScope.launch { keepInjuryTags() }
        viewModelScope.launch { keepMovers() }
        viewModelScope.launch { c.parlayMovers.boards.collect { b -> _state.update { if (it.movers == b) it else it.copy(movers = b) } } }
        viewModelScope.launch { keepLineMoves() }
        viewModelScope.launch { c.parlayAccount.history.collect { h -> _state.update { it.copy(parlayHistory = h) } } }
        // Nothing of CNO's is read before the settings say whether scanning is paused, or while it is
        // (Tj, 2026-09-28: "pause all scanning"). Before the watch below starts, so it starts held.
        viewModelScope.launch {
            // Also held while Check odds now runs (Tj, 2026-10-01: it pauses the CNO scanner so it can focus on refreshing the open bets).
            state.map(::cnoReadsHeld).distinctUntilChanged().collect { cnoWatch.hold(it) }
        }
        // CNO, its books lane and its teams lane run together, only while someone is looking.
        viewModelScope.launch {
            cnoWatch.runWhileWatched(onActive = { active -> _state.update { it.copy(cnoLive = active) } }) {
                launch { c.cno.watch(state.map { it.cnoConfig }) }
                // How many of the top bets the lane covers follows the switch (restarting the lane).
                launch {
                    state.map { it.settings.cnoOnlyAgreed }.distinctUntilChanged().collectLatest { only ->
                        c.cno.keepBooksFresh(agreementRows(), if (only) CnoFeed.AGREE_TOP_ONLY_AGREED else CnoFeed.AGREE_TOP)
                    }
                }
                // Each listed bet's Novig link, ahead of any tap (Tj, 2026-09-27: taps that didn't open the bet slip).
                launch { c.cno.keepLinksFresh(state.map { s -> s.cnoShown(System.currentTimeMillis()).map { it.row } }) }
                launch { c.teams.keepFresh(teamRows()) }
                // Vigilant's own scan again every N minutes, when Tj asked for that.
                launch { rescanWhileWatched() }
                // Novig's price now for the listed CNO bets (off the main thread: book parsing). Novig only.
                launch(Dispatchers.Default) {
                    state.map { AppBook.isNovig && it.settings.cnoLivePrices && it.settings.cnoOn }.distinctUntilChanged().collectLatest { on ->
                        if (on) c.live.keepFresh(state.map { s -> s.livePriceRows(System.currentTimeMillis()) })
                    }
                }
            }
        }
    }

    /** [MainActivity] and the widget say when they start and stop showing CNO's list. */
    fun watchCno(who: String, on: Boolean) = cnoWatch.set(who, on)

    /**
     * The bets whose books the green check reads: the list's best, placed and removed ones left
     * out. With "only bets the books agree on", every candidate (not just the ones shown, which
     * would be only those already read) and more of them.
     */
    private fun agreementRows(): Flow<List<CnoRow>> = state.map { s ->
        if (!s.cnoReadsBooks) emptyList()
        else s.cnoCandidates(System.currentTimeMillis()).map { it.row }
    }

    /**
     * Asks ParlayAPI's /v1/verdict about one bet (Tj, 2026-09-30, PARLAY_API.md §6.4: "Second opinion", 5 credits), kept under [key] for the
     * sheet to show. Only on a tap; a question already being asked isn't asked twice.
     */
    fun askOpinion(key: String, query: com.tjshea.vigilant.data.reference.VerdictQuery) {
        if (_state.value.opinions[key]?.asking == true) return
        _state.update { it.copy(opinions = it.opinions + (key to (it.opinions[key]?.copy(asking = true, error = null) ?: com.tjshea.vigilant.app.ui.OpinionUi(query, asking = true)))) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { c.parlayVerdicts.ask(query) }
            val ui = com.tjshea.vigilant.app.ui.OpinionUi.of(query, result, System.currentTimeMillis())
            // A failed re-ask keeps the last answer, with why this one failed.
            _state.update { s ->
                val kept = s.opinions[key]?.verdict?.takeIf { ui.verdict == null }
                s.copy(opinions = s.opinions + (key to ui.copy(verdict = ui.verdict ?: kept)))
            }
        }
    }

    /**
     * ParlayAPI's own +EV list at Novig (Tj, 2026-09-30, PARLAY_API.md §6.5): each picked league's /best-bets (10 credits a league, only
     * on this tap), every play found in Novig's catalog and priced from Novig's own book, so only what's +EV at Novig's real price shows.
     */
    fun scanParlayPicks() {
        val s = _state.value
        if (!s.canAskParlay || s.parlayPicks.loading) return
        val leagues = s.settings.leagues.mapNotNull { com.tjshea.vigilant.data.scanner.Leagues.byNovigName(it) }
            .filter { com.tjshea.vigilant.data.reference.ParlayBestBets.supports(it) }
        if (leagues.isEmpty()) {
            _toasts.tryEmit("Pick a league with player props first (ParlayAPI's list is props only)")
            return
        }
        _state.update { it.copy(parlayPicks = it.parlayPicks.copy(loading = true, errors = emptyList())) }
        viewModelScope.launch {
            val boards = ArrayList<com.tjshea.vigilant.data.reference.ParlayBoard>()
            val errors = ArrayList<String>()
            try {
                withContext(Dispatchers.IO) {
                    for (l in leagues) {
                        try {
                            c.parlayBestBets.read(l)?.let(boards::add)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errors += "${l.displayName}: ${com.tjshea.vigilant.data.reference.readableError(e)}"
                        }
                    }
                }
                val plays = boards.flatMap { it.plays }
                val picks = withContext(Dispatchers.IO) { priceAtNovig(plays) }
                val now = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        parlayPicks = com.tjshea.vigilant.app.ui.ParlayPicksUi(
                            picks = picks, readAtMs = now, leagues = leagues.map { l -> l.displayName },
                            errors = errors, summaries = boards.mapNotNull { b -> b.summary },
                        ),
                    )
                }
                readVigilantFairs()
            } finally {
                _state.update { if (it.parlayPicks.loading) it.copy(parlayPicks = it.parlayPicks.copy(loading = false)) else it }
            }
        }
    }

    private var vigilantFairsJob: Job? = null
    private var vigilantFairsRound = 0

    /**
     * Vigilant's own fair line for the ParlayAPI picks shown (TASKS.md P2, Tj 2026-09-30: "put cno/vigilant's percentage so I can compare and
     * see if it is truly positive EV on each bet"): the ones the last scan hasn't priced freshly get one bets-only read (the Tracker's
     * [com.tjshea.vigilant.data.tracker.OpenBetPricer], the feed's sources and rules), after each ParlayAPI scan and recheck. Only on those taps.
     */
    private fun readVigilantFairs() {
        val s = _state.value
        val pricer = c.betPricer ?: return
        val now = System.currentTimeMillis()
        val asks = s.vigilantAsks(now)
        if (asks.isEmpty()) return
        vigilantFairsJob?.cancel()
        // A newer read replaces this one: only the newest clears the "reading" line when it ends.
        val round = ++vigilantFairsRound
        _state.update { it.copy(parlayPicks = it.parlayPicks.copy(vigilantReading = true)) }
        vigilantFairsJob = viewModelScope.launch {
            try {
                val bets = asks.map { p ->
                    TrackedBet(
                        id = p.key, createdAtMs = now, league = p.row.league, eventName = p.row.event, startsTs = p.row.startsAtMs ?: 0L,
                        marketLabel = p.row.market, selection = p.row.bet, marketId = p.marketId.orEmpty(), outcomeId = p.outcomeId.orEmpty(),
                        price = 0.0, cost = 0.0, fairAtBet = null, evPercentAtBet = null, stake = 0.0,
                    )
                }
                val reads = withContext(Dispatchers.IO) { pricer.fairs(_state.value.settings, bets) }
                _state.update { it.copy(parlayPicks = it.parlayPicks.copy(vigilant = it.parlayPicks.vigilant + reads)) }
            } finally {
                if (round == vigilantFairsRound) _state.update { it.copy(parlayPicks = it.parlayPicks.copy(vigilantReading = false)) }
            }
        }
    }

    /**
     * Every book's odds for a tapped ParlayAPI pick (TASKS.md P4, Tj 2026-09-30: "opens a screen that shows other sports books odds on the same
     * bet, exactly how other sections of this app such as cno scanner do it"): CNO's game page when CNO lists the same bet (at CNO's pace), else
     * (or when that page can't be read) every other sportsbook's from the odds sources (TASKS.md Q2, "always see other sports books odds":
     * ParlayAPI and PropLine side by side, The Odds API last; one-sided books shown, older prices apart; [com.tjshea.vigilant.data.reference.OtherBooks]).
     */
    fun loadPickBooks(p: com.tjshea.vigilant.data.reference.ParlayPick, force: Boolean = false) {
        val s = _state.value
        val cnoRow = com.tjshea.vigilant.app.ui.pickCnoRow(s, p)
        viewModelScope.launch {
            if (cnoRow != null) {
                runCatching { c.cno.loadBooks(cnoRow, force) }
                if (c.cno.books.value[cnoRow.key]?.view != null) return@launch
            }
            val had = _state.value.parlayPicks.books[p.key]
            val keptAt = had?.view?.fetchedAtMs
            if (!force && keptAt != null && System.currentTimeMillis() - keptAt < com.tjshea.vigilant.data.reference.OtherBooks.KEEP_MS) return@launch
            setPickBooks(p.key, (had ?: com.tjshea.vigilant.data.cno.CnoBooksState()).copy(loading = true, error = null))
            val read = try {
                withContext(Dispatchers.IO) {
                    c.otherBooks.view(p.row.league, p.row.event, p.row.startsAtMs, p.row.market, p.row.bet, marketKey = p.play.marketKey)
                }
            } catch (e: CancellationException) {
                setPickBooks(p.key, (had ?: com.tjshea.vigilant.data.cno.CnoBooksState()).copy(loading = false))
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update {
                val ui = it.parlayPicks
                it.copy(
                    parlayPicks = ui.copy(
                        books = ui.books + (p.key to com.tjshea.vigilant.data.cno.CnoBooksState(
                            view = read?.view ?: had?.view,
                            error = read?.why ?: if (read == null) "The odds sources couldn't be read just now" else null,
                        )),
                        bookSources = ui.bookSources + (p.key to read?.sources.orEmpty()),
                        olderBooks = ui.olderBooks + (p.key to read?.older.orEmpty()),
                    ),
                )
            }
        }
    }

    private fun setPickBooks(key: String, books: com.tjshea.vigilant.data.cno.CnoBooksState) =
        _state.update { it.copy(parlayPicks = it.parlayPicks.copy(books = it.parlayPicks.books + (key to books))) }

    /** ParlayAPI's picks at Novig's price now again: Novig's books only, no ParlayAPI credits. */
    fun recheckParlayPicks() {
        val current = _state.value.parlayPicks
        if (current.picks.isEmpty() || current.rechecking || current.loading) return
        _state.update { it.copy(parlayPicks = it.parlayPicks.copy(rechecking = true)) }
        viewModelScope.launch {
            try {
                val picks = withContext(Dispatchers.IO) { priceAtNovig(current.picks.map { it.play }) }
                _state.update { it.copy(parlayPicks = it.parlayPicks.copy(picks = picks)) }
                readVigilantFairs()
            } finally {
                _state.update { it.copy(parlayPicks = it.parlayPicks.copy(rechecking = false)) }
            }
        }
    }

    /** [plays] found in Novig's catalog (their starts) and priced from Novig's own books: nothing at ParlayAPI's listed price is trusted. */
    private suspend fun priceAtNovig(plays: List<com.tjshea.vigilant.data.reference.ParlayPlay>): List<com.tjshea.vigilant.data.reference.ParlayPick> {
        if (plays.isEmpty()) return emptyList()
        val rows = plays.map { p ->
            val start = runCatching { c.betFinder.event(p.row()) }.getOrNull()?.startsTs?.takeIf { it > 0 }
            p.row(startsAtMs = start)
        }
        val live = runCatching { c.live.readNow(rows) }.getOrDefault(emptyMap())
        return com.tjshea.vigilant.data.reference.ParlayPick.priced(plays, rows, live)
    }

    /** The rows whose players' teams are wanted (all of the list's player bets). */
    private fun teamRows(): Flow<List<CnoRow>> = state.map { it.cnoTeamRows }

    /**
     * Pinnacle's moves in the picked leagues (PARLAY_API.md §6.3): ParlayAPI's public movers board (free, no key) read every few minutes
     * while Vigilant is on screen and ParlayAPI is on, again at once when the leagues change or Vigilant comes back on screen (the
     * notes are never minutes stale on return), and not at all off screen: no timer wakes the phone in a pocket.
     */
    private suspend fun keepMovers() = refreshWhileOnScreen(
        state.map { s ->
            if (!s.loaded || !s.settings.useParlay) emptyList()
            else s.settings.leagues.mapNotNull { com.tjshea.vigilant.data.scanner.Leagues.byNovigName(it)?.takeIf { l -> l.oddsApiListed }?.oddsApiSportKey }.distinct()
        },
        // Not while Check odds now runs (Tj, 2026-10-01).
        combine(c.screen, state.map { it.checkingOdds }.distinctUntilChanged()) { on, checking -> on && !checking },
        com.tjshea.vigilant.data.reference.ParlayMovers.EVERY_MS,
    ) { sports -> c.parlayMovers.refresh(sports) }

    /** The "Pinnacle moved toward/against" notes, recomputed off the main thread when a list or a board changes. */
    private suspend fun keepLineMoves() {
        state.map { s -> LineMoveInputs(s.movers, s.feed, if (s.settings.cnoOn) s.cno.snapshot?.rows.orEmpty() else emptyList(), s.bets) }
            .distinctUntilChanged { a, b -> a.movers === b.movers && a.feed === b.feed && a.rows === b.rows && a.bets === b.bets }
            .map { i -> com.tjshea.vigilant.data.reference.LineMoves.notes(i.movers, i.feed, i.rows, i.bets, System.currentTimeMillis()) }
            .flowOn(Dispatchers.Default)
            .collect { m -> _state.update { if (it.lineMoves == m) it else it.copy(lineMoves = m) } }
    }

    private class LineMoveInputs(
        val movers: Map<String, com.tjshea.vigilant.data.reference.MoversBoard>,
        val feed: List<Opportunity>,
        val rows: List<CnoRow>,
        val bets: List<TrackedBet>,
    )

    /**
     * Injury tags for every listed and open prop bet (Tj, 2026-09-30, PARLAY_API.md §6.1), recomputed off the main thread when a list or
     * the reports change. Players no ParlayAPI props answer covered are looked up in its /injuries list ([com.tjshea.vigilant.data.reference.ParlayInjuries]:
     * 1 credit a league, 10 minutes apart at most, only while ParlayAPI is on with a key).
     */
    private suspend fun keepInjuryTags() {
        class Lists(
            val feed: List<Opportunity>, val rows: List<CnoRow>, val teams: Map<String, String>, val bets: List<TrackedBet>,
            val picks: List<com.tjshea.vigilant.data.reference.ParlayPick>,
        )
        val wants = state
            .map { s -> Lists(s.feed, if (s.settings.cnoOn) s.cno.snapshot?.rows.orEmpty() else emptyList(), s.teams, s.bets, s.parlayPicks.picks) }
            // The same lists (by reference) as last time: nothing to look up again.
            .distinctUntilChanged { a, b -> a.feed === b.feed && a.rows === b.rows && a.teams === b.teams && a.bets === b.bets && a.picks === b.picks }
            .map { l -> com.tjshea.vigilant.data.reference.InjuryTags.wants(l.feed, l.rows, l.teams, l.bets, System.currentTimeMillis(), l.picks.map { it.row }) }
            .distinctUntilChanged()
        combine(wants, c.injuries.book) { w, book ->
            val now = System.currentTimeMillis()
            com.tjshea.vigilant.data.reference.InjuryTags.tags(book, w, now) to com.tjshea.vigilant.data.reference.InjuryTags.uncovered(book, w, now)
        }.flowOn(Dispatchers.Default).collect { (tags, uncovered) ->
            _state.update { if (it.injuries == tags) it else it.copy(injuries = tags) }
            // Each sport's read is gated in ParlayInjuries (10 minutes apart, ParlayAPI on with a key): asking again is free.
            if (!_state.value.checkingOdds) uncovered.forEach { (sport, players) -> viewModelScope.launch { c.parlayInjuries.fill(sport, players) } }
        }
    }

    /**
     * Marks a widget or CNO-tab bet placed: hidden from then on, through refreshes and restarts,
     * and logged in the Tracker for good (Tj, 2026-09-27: "for every bet that I check on the cno
     * scanner, log it permanently"), $1 unless he changes it there.
     */
    fun markPlaced(item: MiniWindow.Item) {
        viewModelScope.launch {
            if (runCatching { c.placed.mark(MiniWindow.placed(item, System.currentTimeMillis(), outcomeId = outcomeOf(item))) }.isFailure) _toasts.tryEmit("Couldn't save that")
            if (runCatching { logBet(item) }.isFailure) _toasts.tryEmit("Couldn't log the bet in the Tracker")
        }
    }

    /** [item]'s Novig outcome: Vigilant's own, or a CNO bet's from its Novig link when that's known. */
    private fun outcomeOf(item: MiniWindow.Item): String? = item.outcomeId
        ?: item.cno?.row?.let { com.tjshea.vigilant.data.cno.CnoFeed.outcomeIdOf(_state.value.cnoLinks[com.tjshea.vigilant.data.cno.CnoFeed.linkKey(it)]) }

    /** The Tracker's copy of a ✓: Vigilant's own bet (its scan's numbers), else CNO's bet at the price shown. */
    private suspend fun logBet(item: MiniWindow.Item) {
        val own = item.outcomeId?.let { _state.value.result?.opportunities?.firstOrNull { o -> o.key == item.key } }
        if (own != null) {
            val record = runCatching { BetRecord.opportunity(_state.value, own, com.tjshea.vigilant.data.tracker.AtBet.HOW_MARKED, System.currentTimeMillis(), stake = com.tjshea.vigilant.data.tracker.BetTracker.DEFAULT_STAKE) }.getOrNull()
            c.tracker.track(own, com.tjshea.vigilant.data.tracker.BetTracker.DEFAULT_STAKE, placedKey = item.key, atBet = record)
            return
        }
        val pick = item.cno ?: return
        // ParlayAPI's picks are CNO-shaped rows too, logged as ParlayAPI's.
        val source = if (item.key.startsWith(com.tjshea.vigilant.data.reference.ParlayPlay.KEY_PREFIX)) com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_PARLAY
        else com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO
        val record = runCatching {
            BetRecord.cno(_state.value, pick.row, pick.live, com.tjshea.vigilant.data.tracker.AtBet.HOW_MARKED, source, System.currentTimeMillis(), stake = com.tjshea.vigilant.data.tracker.BetTracker.DEFAULT_STAKE)
        }.getOrNull()
        val bet = c.tracker.logCno(pick.row, pick.ev, pick.live, placedKey = item.key, outcomeId = outcomeOf(item).orEmpty(), source = source, atBet = record)
        // Novig's outcome, so Vigilant's own scans can follow its line to the close (best effort).
        if (!AppBook.isNovig) return
        viewModelScope.launch {
            val found = runCatching { withContext(Dispatchers.IO) { c.betFinder.find(pick.row) } }.getOrNull() as? com.tjshea.vigilant.data.cno.NovigBetFinder.Found.Bet
            if (found?.marketId != null) runCatching { c.tracker.edit(bet.id) { it.copy(marketId = found.marketId!!, outcomeId = found.outcomeId) } }
        }
    }

    /** The widget's ✕: the bet leaves the list for good, without counting as placed (Tj, 2026-09-27). */
    fun markHidden(item: MiniWindow.Item) {
        viewModelScope.launch {
            if (runCatching { c.placed.mark(MiniWindow.placed(item, System.currentTimeMillis(), hidden = true, outcomeId = outcomeOf(item))) }.isFailure) _toasts.tryEmit("Couldn't save that")
        }
    }

    /**
     * The +EV tab's ✕ (Tj, 2026-09-30: "remove the bet from the list permanently, even through refreshes and rescans, exactly like the
     * cno section already does"): the same record as CNO's ✕, so it stays gone from the list and the widget through every scan and restart.
     */
    fun hideOpportunity(o: Opportunity) {
        val item = MiniWindow.itemFor(o, System.currentTimeMillis()) ?: return
        markHidden(item)
    }

    /** Undo, or "not placed after all": the bet shows again. */
    fun unmarkPlaced(key: String) {
        viewModelScope.launch {
            if (runCatching { c.placed.unmark(key) }.isFailure) _toasts.tryEmit("Couldn't save that")
            // Not placed after all: its open bet leaves the Tracker too.
            runCatching { c.tracker.untrack(key) }
        }
    }

    /**
     * Re-reads CNO now (Refresh, pull down, the mini window's button), if 3 s have passed. [resume]: Tj's own pull or Refresh on the CNO tab
     * resumes a paused scanner first ([resumeThen]).
     */
    fun refreshCno(quiet: Boolean = false, resume: Boolean = false) {
        val current = _state.value
        if (!current.loaded) return
        if (current.settings.paused && resume) {
            resumeThen { refreshCno(quiet) }
            return
        }
        if (!current.settings.cnoOn) return
        if (current.settings.paused) {
            if (!quiet) _toasts.tryEmit(PAUSED_TOAST)
            return
        }
        if (current.checkingOdds) {
            if (!quiet) _toasts.tryEmit(CHECKING_TOAST)
            return
        }
        viewModelScope.launch {
            val wait = c.cno.waitForGapMs()
            val read = c.cno.refresh(current.cnoUrl, current.settings.cnoFilters)
            if (!read && !quiet && !_state.value.cno.refreshing) {
                val seconds = ((wait + 999) / 1000).coerceAtLeast(1)
                _toasts.tryEmit("CrazyNinjaOdds can be read again in ${seconds}s (it allows one read per ${CnoFeed.MIN_GAP_MS / 1000}s)")
            }
        }
    }

    /**
     * One scan: Novig's board and prices plus fair odds from every enabled source. It runs in the
     * app's runner under a foreground service, so it keeps going if Tj switches apps.
     */
    fun scan(resume: Boolean = false) {
        val current = _state.value
        // A pull to refresh while paused resumes the scanner, then scans ([resumeThen]).
        if (resume && current.loaded && current.settings.paused) {
            resumeThen { scan() }
            return
        }
        // CNO only: Vigilant's scanner and every API behind it stay asleep.
        if (!current.loaded || !current.settings.vigilantOn || c.runner.running || current.status.rechecking) return
        if (current.settings.paused) {
            _toasts.tryEmit(PAUSED_TOAST)
            return
        }
        if (current.checkingOdds) {
            _toasts.tryEmit(CHECKING_TOAST)
            return
        }
        if (current.settings.leagues.isEmpty()) return
        // Open bets' lines are priced even past the per-game cap, so their closing value updates.
        val started = c.startVigilantScan(current.settings, current.bets)
        if (started) ScanService.start(getApplication())
    }

    /**
     * Re-reads just these markets' Novig prices (the feed's, or one bet's) and re-prices against
     * the last scan's fair odds: a few seconds, no fair-odds calls or credits. For checking an
     * edge is still there right before betting it.
     */
    fun recheck(marketIds: Collection<String>) {
        val current = _state.value
        if (!current.loaded || !current.settings.vigilantOn || c.runner.running || current.status.rechecking || marketIds.isEmpty()) return
        if (current.settings.paused) {
            _toasts.tryEmit(PAUSED_TOAST)
            return
        }
        if (current.checkingOdds) {
            _toasts.tryEmit(CHECKING_TOAST)
            return
        }
        // A recheck re-reads Novig only. When the other books' prices it compares against are about to
        // be too old to use (RESEARCH.md §24), it would show nothing: scan everything instead.
        if (WidgetRescan.fairTooOldToRecheck(current, marketIds, System.currentTimeMillis())) {
            _toasts.tryEmit("The other books' odds are too old to recheck against: scanning again")
            scan()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(status = it.status.copy(rechecking = true)) }
            val outcome = runCatching { withContext(Dispatchers.IO) { c.scanner.recheck(_state.value.settings, marketIds) } }
            val report = outcome.getOrNull()
            val r = report?.result ?: _state.value.result
            if (r != null) _state.publishResult(r) { s -> s.copy(status = s.status.copy(rechecking = false)) }
            else _state.update { s -> s.copy(status = s.status.copy(rechecking = false)) }
            runCatching { c.usage.flush() }
            _toasts.tryEmit(
                when {
                    report == null -> "Recheck failed: ${outcome.exceptionOrNull()?.message ?: "unknown error"}"
                    report.error != null && report.read == 0 -> "Recheck: ${report.error}"
                    report.failed > 0 -> "Rechecked ${report.read} price${if (report.read == 1) "" else "s"}, ${report.failed} not refreshed"
                    else -> "Rechecked ${report.read} price${if (report.read == 1) "" else "s"}"
                },
            )
        }
    }

    /** Every book's price for a CNO bet (its game page): the sheet and the widget's Books view. */
    fun loadBooks(row: CnoRow, force: Boolean = false) {
        if (!_state.value.settings.cnoOn) return
        viewModelScope.launch { runCatching { c.cno.loadBooks(row, force) } }
    }

    /** Where a tapped CNO bet opens in Novig: its bet slip, else its game ([TapLink]). Null when nothing answered. */
    suspend fun betLink(row: CnoRow): TapLink.Link? = c.betLink(row)

    /**
     * Mirrors the runner into the screen's state, for as long as this screen lives. A scan publishes after every Novig price it reads (about
     * 14 a second through the key), and every new state recomposes the whole app, its tab badges' counts included: that many a second kept
     * the main thread busy until switching tabs mid-scan froze and then crashed the app (Tj, 2026-09-30: "very laggy then crashed"). So the
     * screen takes the scan's newest state at most every [SCAN_MIRROR_MS] ([followThrottled]), rebuilds the feed only when the result itself
     * changed, and rebuilds it off the main thread.
     */
    private suspend fun follow() {
        var seen = c.runner.state.value.finished
        var first = true
        var shown: ScanResult? = null
        followThrottled(c.runner.state, SCAN_MIRROR_MS) { run ->
            val report = run.report
            val ended = run.finished != seen
            seen = run.finished
            if (!run.scanning && report != null && (ended || first)) {
                // A scan just ended, or this screen opened after one did.
                applyReport(report, run.settings ?: _state.value.settings, run.result)
                shown = _state.value.result
            } else {
                val r = run.result
                if (r != null && r !== shown) {
                    shown = r
                    // Settings or the placed marks changed while the feed was built: built again from what's current (rare, and cheap once).
                    _state.publishResult(r) { s -> s.copy(status = s.status.copy(scanning = run.scanning, progress = run.progress)) }
                } else {
                    // Only the progress moved: the feed is what it was.
                    _state.update { s -> s.copy(status = s.status.copy(scanning = run.scanning, progress = run.progress)) }
                }
            }
            first = false
        }
    }

    private suspend fun applyReport(report: ScanReport, settings: ScanSettings, result: ScanResult?) {
        _state.publishResult(result ?: _state.value.result) { s ->
            s.copy(
                status = s.status.copy(
                    scanning = false,
                    progress = null,
                    scannedAtMs = report.result?.computedAtMs ?: s.status.scannedAtMs,
                    errors = report.errors,
                    backoffSeconds = report.retryAfterSeconds,
                    booksFetched = report.booksFetched + report.booksNotModified,
                    booksFromCache = report.booksFromCache,
                    booksViaKey = report.booksViaKey,
                    booksViaPush = report.booksViaPush,
                    timing = report.timing ?: s.status.timing,
                    keyReadPerSec = c.novig.limits?.readPerSec,
                    sources = report.sources,
                    unscanned = emptySet(),
                    scannedWindowHours = settings.effective().scanWindowHours,
                ),
            )
        }
        // A setting changed while the scan was in flight: re-price from cache so the screen never
        // shows numbers computed under the old settings.
        val latest = _state.value.settings
        if (result != null && latest != settings) repriceNow(latest)
    }

    private suspend fun repriceNow(settings: ScanSettings) {
        val repriced = withContext(Dispatchers.Default) { c.scanner.reprice(settings) }
        // Before the first scan there's nothing "missing": the whole feed says tap Scan.
        val unscanned = if (_state.value.status.scannedAtMs == null) emptySet() else c.scanner.unscannedLeagues(settings)
        _state.publishResult(repriced ?: _state.value.result) { it.copy(status = it.status.copy(unscanned = unscanned)) }
    }

    /**
     * Connects the Novig key. [typed]: the management key Tj just entered, saved on this phone once Novig accepts it (Tj, 2026-09-29: "I only
     * input the API key and file one time"); null = the saved one.
     */
    fun connectNovig(typed: ManagementKey? = null) {
        if (_state.value.novig.busy) return
        viewModelScope.launch {
            _state.update { it.copy(novig = it.novig.copy(busy = true, error = null, message = "Starting…")) }
            val key = typed ?: withContext(Dispatchers.IO) { runCatching { c.managementKeys.load() }.getOrNull() }
            if (key == null) {
                _state.update { it.copy(novig = it.novig.copy(busy = false, message = null, error = "Enter your management key ID and its .pem file first.")) }
                return@launch
            }
            try {
                val setup = NovigSetup(c.http, c.json, KeystoreVault)
                val conn = withContext(Dispatchers.IO) {
                    setup.connect(key.keyId, key.pem) { step ->
                        _state.update { it.copy(novig = it.novig.copy(message = step)) }
                    }
                }
                if (typed != null) {
                    val hint = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.managementKeys.save(typed) }.getOrNull() }
                    if (hint != null) _state.update { it.copy(novig = it.novig.copy(managementKey = hint)) }
                }
                // Replace any earlier read key: its Keystore entry is no longer needed.
                val old = _state.value.novig.connection
                old?.let { if (it.readAlias != conn.readAlias) KeystoreVault.delete(it.readAlias) }
                // Reconnecting the same subaccount keeps the trading key betting through the API already set up.
                val kept = if (old != null && conn.tradingKeyId == null && old.subaccountKeyId == conn.subaccountKeyId) {
                    conn.copy(tradingKeyId = old.tradingKeyId, tradingAlias = old.tradingAlias)
                } else {
                    conn
                }
                c.novigConnection.save(kept)
                c.useConnection(kept)
                _state.update {
                    it.copy(novig = it.novig.copy(connection = kept, busy = false, message = "Connected. Scans now read Novig prices through your key's own rate limit."))
                }
                api.refreshEnabled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NovigApiException) {
                val replace = if (typed == null && e.status == 401 && e.serverMessage?.contains("timestamp") != true) " Tap Replace to enter the key again." else ""
                _state.update { it.copy(novig = it.novig.copy(busy = false, message = null, error = e.advice + replace)) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(novig = it.novig.copy(busy = false, message = null, error = e.message ?: "That key file couldn't be read.")) }
            } catch (e: Exception) {
                _state.update { it.copy(novig = it.novig.copy(busy = false, message = null, error = "Setup failed: ${e.message ?: e.javaClass.simpleName}")) }
            }
        }
    }

    fun testNovig() {
        val conn = _state.value.novig.connection ?: return
        viewModelScope.launch {
            _state.update { it.copy(novig = it.novig.copy(busy = true, error = null, message = "Testing…")) }
            // Novig judges the internet address a request comes from: say which connection was used, whether a VPN is
            // really up, and when Novig blamed the address, try the other connection (Tj, 2026-09-28: "The app is
            // telling me I have a proxy or vpn when I test the novig key, but I don't").
            val report = withContext(Dispatchers.IO) {
                val nets = PhoneNetworks(getApplication())
                val first = NovigKeyTest.Attempt(nets.current(), echo { c.readKeyClient(conn).echo() })
                val other = if (!NovigKeyTest.worthOtherNetwork(first)) null else nets.onOther { name, network ->
                    NovigKeyTest.Attempt(name, echo { c.readKeyClient(conn, PhoneNetworks.bound(c.http, network)).echo() })
                }
                NovigKeyTest.report(first, nets.vpnUp(), other)
            }
            _state.update { it.copy(novig = it.novig.copy(busy = false, message = report.message, error = report.error)) }
            // A key that's accepted still can be slow (Tj, 2026-09-29: "the novig scan is slow, even though I tested my key and it says
            // it works"): measure what it gets, once, from this phone: its limits, book reads, and the live feed.
            // (Not while a scan runs on the same key: its live feed shares the key's `stream` bucket.)
            if (report.message != null && report.error == null && !c.runner.running && !_state.value.status.scanning) {
                val lines = try {
                    withContext(Dispatchers.IO) {
                        NovigLiveCheck(c.http, c.json, c.readKeyClient(conn), feed = { s -> NovigStream(c.http, s, viewModelScope) }).run { step ->
                            _state.update { it.copy(novig = it.novig.copy(busy = true, message = report.message + "\n\n" + step)) }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    listOf("The speed check couldn't run: ${e.message ?: e.javaClass.simpleName}")
                }
                _state.update { it.copy(novig = it.novig.copy(busy = false, message = report.message + "\n\n" + lines.joinToString("\n"))) }
            }
        }
    }

    /** Runs one key check: null when Novig accepted it, else why not. */
    private suspend fun echo(call: suspend () -> Unit): Throwable? = try {
        call()
        null
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    /** Forgets the key on this phone. It stays registered on Novig until revoked in Novig's profile. */
    fun disconnectNovig() {
        viewModelScope.launch {
            val conn = _state.value.novig.connection
            c.useConnection(null)
            c.novigConnection.clear()
            conn?.let { KeystoreVault.delete(it.readAlias) }
            // The saved management key stays (Settings › Forget removes it): connecting again needs no typing.
            _state.update { it.copy(novig = NovigUi(message = "Disconnected. Back to Novig's public prices.", managementKey = it.novig.managementKey), betting = BettingUi(), betSheet = null) }
        }
    }

    /** Saves a settings change and re-prices from the last scan. Never touches the network. */
    fun updateSettings(transform: (ScanSettings) -> ScanSettings) {
        viewModelScope.launch { applySettings(transform) }
    }

    /**
     * Pauses every scan, or resumes them (Tj, 2026-09-28: "Make an option in the app to pause all scanning"): the
     * top bars' and the widget's button. Settings' switch is the same setting.
     */
    fun setPaused(paused: Boolean) {
        // The kill switch is not a Pause: ▶ can't undo it (only its own Resume can).
        if (!paused && _state.value.settings.killed) {
            _toasts.tryEmit(KILLED_TOAST)
            return
        }
        viewModelScope.launch {
            applySettings { it.copy(pausedByHand = paused) }
            _toasts.tryEmit(if (paused) "Scanning paused: nothing is read until you resume" else RESUMED_TOAST)
        }
    }

    /**
     * The STOP button (Tj, 2026-10-05: "a stop button kill switch … immediately stops all scanning, all auto betting, all auto bidding, and all background
     * scan … until I press resume"): [KillSwitch.engage] on the app's own scope, so it finishes (bids taken down) even if this screen goes away. The screen
     * shows it stopped at once, before the bids are confirmed down.
     */
    fun killAll() {
        val now = System.currentTimeMillis()
        // On screen at once: the red bar, the paused look of every tab (the saved settings follow in a moment).
        _state.update { if (it.settings.killed) it else it.copy(settings = it.settings.copy(killed = true, killedAtMs = now)) }
        c.appScope.launch {
            val result = KillSwitch.engage(getApplication(), c, "the Stop button", now)
            _toasts.tryEmit(result.toast)
        }
    }

    /** Resume after the kill switch: everything Tj had switched on runs again (his switches were kept). */
    fun resumeAfterKill() {
        c.appScope.launch {
            val next = runCatching { KillSwitch.release(getApplication(), c, "the Resume button") }.getOrNull()
            if (next == null) {
                _toasts.tryEmit("Couldn't save the Resume: try again")
                return@launch
            }
            _state.update { it.copy(settings = it.settings.copy(killed = false, killedAtMs = null)) }
            rescheduleReprice()
            _toasts.tryEmit("Resumed: ${KillBarText.resumedList(next)}")
        }
    }

    /**
     * Resumes a paused scanner, as ▶ Resume does, then does what Tj asked ([then]) (Tj, 2026-10-02: "If I press check odds now, or pull to refresh,
     * and the scanner is paused, automatically resume the scanner"). [then] runs after the setting is saved, so it sees the scanner on.
     */
    private fun resumeThen(then: () -> Unit) {
        // A pull to refresh, Check odds now or a Scan never lifts the kill switch: only Resume on the red bar does (Tj, 2026-10-05).
        if (_state.value.settings.killed) {
            _toasts.tryEmit(KILLED_TOAST)
            return
        }
        viewModelScope.launch {
            applySettings { it.copy(pausedByHand = false) }
            _toasts.tryEmit(RESUMED_TOAST)
            then()
        }
    }

    /**
     * The widget's scanner switch: CNO only, Both, or Vigilant only (Tj, 2026-09-27). Switching
     * Vigilant's scan on starts one when the last is missing or old.
     */
    fun setScanner(mode: com.tjshea.vigilant.data.scanner.ScannerMode) {
        viewModelScope.launch {
            applySettings { it.copy(scanner = mode) }
            if (mode == com.tjshea.vigilant.data.scanner.ScannerMode.CNO) return@launch
            if (_state.value.settings.leagues.isEmpty()) {
                _toasts.tryEmit("Pick leagues in Settings for Vigilant's scan")
            } else if (WidgetRescan.scanOnSwitch(_state.value.status.scannedAtMs, System.currentTimeMillis())) {
                scan()
            }
        }
    }

    /** When the last scan was started by [rescanWhileWatched] (its own clock: a failing scan waits a full interval too). */
    private var rescanStartedAtMs: Long? = null

    /**
     * Scans again every [ScanSettings.widgetRescanMinutes] while CNO's list or the floating widget
     * (in any mode) is on screen (run alongside [CnoFeed.watch]), when that option is on and
     * Vigilant's scan is too.
     */
    private suspend fun rescanWhileWatched() {
        state.map { s -> s.settings.widgetRescanMinutes.takeIf { s.settings.vigilantOn && s.settings.leagues.isNotEmpty() } ?: 0 }
            .distinctUntilChanged()
            .collectLatest { minutes ->
                while (true) {
                    val s = _state.value
                    val wait = WidgetRescan.dueInMs(minutes, s.status.scannedAtMs, rescanStartedAtMs, System.currentTimeMillis())
                        ?: kotlinx.coroutines.awaitCancellation()
                    if (wait > 0) {
                        kotlinx.coroutines.delay(wait)
                        continue
                    }
                    if (c.runner.running || s.status.rechecking || s.status.scanning || s.checkingOdds) {
                        kotlinx.coroutines.delay(5_000)
                        continue
                    }
                    rescanStartedAtMs = System.currentTimeMillis()
                    scan()
                    kotlinx.coroutines.delay(1_000)
                }
            }
    }

    private suspend fun applySettings(transform: (ScanSettings) -> ScanSettings) {
        val next = settingsMutex.withLock {
            // If the write fails, keep the change for this session rather than crash.
            runCatching { c.settingsStore.update(transform) }.getOrElse { transform(_state.value.settings) }
        }
        val before = _state.value.settings
        // The settings are on screen at once (Pause, a scanner switch); the feed under them follows, built off the main thread ([refeed]).
        _state.update { if (next.leagues.isEmpty()) it.copy(settings = next, result = null, feed = emptyList()) else it.copy(settings = next) }
        // CNO only now: Vigilant is asleep. A scan running now (Tj's own or a background cycle's) stops so it spends no more API credits, and a
        // "scan done" note would name bets no screen shows any more.
        if (before.vigilantOn && !next.vigilantOn) {
            c.runner.stop()
            ScanService.cancelDone(getApplication())
        }
        // Paused: a scan running now stops (CNO's reads stop by the watch's hold, auto-scan by its service).
        if (!before.paused && next.paused) c.runner.stop()
        // Resumed: background auto-scan starts again at once, from the widget too (Android lets an app showing an
        // overlay start it); from the app, MainActivity starts it as well, which is harmless.
        if (before.activeAutoScan == com.tjshea.vigilant.data.scanner.AutoScanMode.OFF && next.activeAutoScan != com.tjshea.vigilant.data.scanner.AutoScanMode.OFF && !AutoScanService.running) {
            AutoScanService.start(getApplication())
        }
        if (next.leagues.isNotEmpty()) {
            _state.refeed()
            rescheduleReprice()
        }
    }

    /** The re-pricing under the newest settings, started now: an earlier one still waiting is dropped (eight quick switches are one re-pricing). */
    private var repricing: Job? = null

    /**
     * Re-prices from cache in the background, never awaited by the change that asked: the scanner is held by a running scan for all of it (minutes),
     * and Pause, a switch or a filter must not wait for that to be told it's done (Tj, 2026-10-03: "I pressed pause and even that took a while to
     * register"; a scan's end re-prices again if the settings changed meanwhile: [applyReport]).
     */
    private fun rescheduleReprice() {
        repricing?.cancel()
        repricing = viewModelScope.launch { repriceNow(_state.value.settings) }
    }

    fun toggleLeague(league: String) = updateSettings { s ->
        s.copy(leagues = if (league in s.leagues) s.leagues - league else s.leagues + league)
    }

    fun keysFor(provider: ApiProvider): List<String> = _state.value.keysOf(provider)

    /** Adds a key at the end of the rotation (keys are tried in order: key 1 first). */
    fun addKey(provider: ApiProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed in keysFor(provider)) return
        setKeys(provider, keysFor(provider) + trimmed)
        // A new ParlayAPI key: its plan and credits from the key itself, at once (free).
        if (provider == ApiProvider.PARLAY) refreshBalances(force = true)
    }

    /**
     * ParlayAPI's keys asked what they have left (free), so the meters show the provider's own figures (Tj, 2026-09-30); with [history]
     * (Settings › API usage & keys on screen) its day-by-day usage log too, for the chart under its meter (free, at most once a minute).
     */
    fun refreshBalances(force: Boolean = false, history: Boolean = false) {
        if (keysFor(ApiProvider.PARLAY).isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.parlayAccount.refresh(force) }
            if (history) runCatching { c.parlayAccount.refreshHistory(force) }
        }
    }

    fun removeKey(provider: ApiProvider, key: String) = setKeys(provider, keysFor(provider) - key)

    /** Moves a key one place earlier in the rotation. */
    fun moveKeyUp(provider: ApiProvider, key: String) {
        val list = keysFor(provider).toMutableList()
        val i = list.indexOf(key)
        if (i <= 0) return
        list.removeAt(i)
        list.add(i - 1, key)
        setKeys(provider, list)
    }

    private fun setKeys(provider: ApiProvider, keys: List<String>) {
        viewModelScope.launch {
            val saved = runCatching { c.keyStore.setKeys(provider, keys) }.isSuccess
            if (!saved) _toasts.tryEmit("Couldn't save the key")
            val clean = c.keyStore.getKeys(provider)
            _state.update { it.withKeys(provider, clean) }
        }
    }

    /** Writes every key to a file Tj picked (survives even an uninstall). */
    fun exportKeys(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = c.keyStore.exportJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }.isSuccess
            _toasts.tryEmit(if (ok) "Keys saved to the file" else "Couldn't write that file")
        }
    }

    /** Adds the keys from an exported file to the ones already here. */
    fun importKeys(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                c.keyStore.importJson(text)
            }
            val keys = ApiProvider.entries.associateWith { c.keyStore.getKeys(it) }
            _state.update { keys.entries.fold(it) { s, (p, k) -> s.withKeys(p, k) } }
            _toasts.tryEmit(
                result.fold(
                    onSuccess = { n -> if (n == 0) "No new keys in that file" else "Added $n key${if (n == 1) "" else "s"}" },
                    onFailure = { e -> e.message ?: "Couldn't read that file" },
                ),
            )
        }
    }


    fun trackBet(o: Opportunity, stake: Double) {
        viewModelScope.launch {
            val record = runCatching { BetRecord.opportunity(_state.value, o, com.tjshea.vigilant.data.tracker.AtBet.HOW_MARKED, System.currentTimeMillis(), stake = stake) }.getOrNull()
            val ok = runCatching { c.tracker.track(o, stake, atBet = record) }.getOrNull() != null
            _toasts.tryEmit(if (ok) "Tracked ${o.selection} · ${com.tjshea.vigilant.app.ui.Format.money(stake)}" else "Couldn't save the bet")
        }
    }

    /**
     * Settles open bets whose games are over from their final scores (the Tracker tab calls it when
     * shown). Costs nothing when no bet is due; [BetSettler] runs one pass at a time. [force] ("Grade now")
     * looks at every open bet again and [announce]s what came of it (Tj, 2026-09-29: some bets stayed open
     * after the game was final): graded, not over yet, and the ones that need a tap, each with its reason on the bet.
     */
    /**
     * Novig's own prices for the open bets, for the Tracker's "Novig only" filter (Tj, 2026-10-02 ~18:50Z: "Make sure it is smart and doesn't waste any api
     * usage on other sports books … if I already just scanned without using this filter and there is still fresh novig odds for all my bets, it doesn't
     * need to rescan"): reads only Novig's books, only for the bets whose Novig price is missing or older than [NovigNow.FRESH_MS] (all of them when
     * [force]: Check Novig now), one book per market. With every price fresh, nothing is read.
     */
    fun refreshNovigOnly(force: Boolean = false) {
        if (!AppBook.isNovig || _state.value.readingNovig) return
        _state.update { it.copy(readingNovig = true) }
        viewModelScope.launch {
            val done = try {
                withContext(Dispatchers.IO + NonCancellable) {
                    // Bets logged without Novig's ids are looked up in Novig's own catalog first, so they're read too (NovigIds; Tj, 2026-10-02 20:06Z).
                    val looked = lookUpNovigIds(force)
                    val now = System.currentTimeMillis()
                    val r = com.tjshea.vigilant.data.tracker.NovigNow.read(
                        c.tracker.all(), now, force,
                        books = { ids -> runCatching { c.novig.books(ids).books }.getOrDefault(emptyMap()) },
                        market = { id -> c.locks.market(id) },
                    )
                    c.tracker.recordNovig(r.prices, System.currentTimeMillis(), r.why)
                    if (r.due > 0) c.eventLog.count("novigOnly.read")
                    looked to r
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                _state.update { it.copy(readingNovig = false) }
            }
            val (looked, read) = done ?: run { if (force) _toasts.tryEmit("Couldn't read Novig's prices just now."); return@launch }
            com.tjshea.vigilant.app.ui.TrackerText.novigReadToast(looked, read, force)?.let { _toasts.tryEmit(it) }
        }
    }

    /**
     * Novig's market and side for open bets logged without them ([com.tjshea.vigilant.data.tracker.NovigIds]: a CNO or ParlayAPI ✓ whose one lookup when
     * logged failed), from Novig's own catalog and nothing else; [force] looks again at every one, else only those not looked at lately. Written on the bets.
     */
    private suspend fun lookUpNovigIds(force: Boolean): com.tjshea.vigilant.data.tracker.NovigIds.Found? {
        if (!AppBook.isNovig) return null
        val missing = com.tjshea.vigilant.data.tracker.NovigIds.missing(c.tracker.all(), System.currentTimeMillis(), force)
        if (missing.isEmpty()) return null
        val found = com.tjshea.vigilant.data.tracker.NovigIds.find(missing) { row, outcome -> c.betFinder.locate(row, outcome) }
        c.tracker.recordIds(found.ids, found.why, System.currentTimeMillis())
        c.eventLog.info("NOVIG", "Novig ids looked up for ${missing.size} open bets: ${found.ids.size} found, ${found.why.size} not on Novig now")
        return found
    }

    /**
     * The locks on offer on Tj's open API bets (Tj, 2026-10-02 ~18:50Z: "it finds proper arbitrage opportunities based on the bets I already placed"): one
     * Novig book per market the subaccount holds, nothing else. When the Tracker opens, after Check odds now, and after a lock.
     */
    fun scanLocks() {
        if (!AppBook.isNovig) return
        viewModelScope.launch(Dispatchers.IO) {
            val views = runCatching { c.locks.scan(c.tracker.all()) }.getOrNull() ?: return@launch
            _state.update { it.copy(locks = views) }
        }
    }

    /**
     * Locks in [marketId]'s profit (the Tracker's Lock button, after Tj confirmed [confirmed] dollars): placed only if it still pays at least that
     * (a price that moved against him is refused with "look again"), through [ApiBetPlacer.placeLock].
     */
    // ---- the Bids tab: make orders (Tj, 2026-10-03; RESEARCH.md §70) ------------------------------------------------------------

    /** The last pass and the bids each line would get ([MakerRunner.status]). */
    val makerStatus get() = c.maker.status

    /** Every bid on record ([com.tjshea.vigilant.data.novig.trading.maker.MakerStore]); null until first read. */
    val makerBids get() = c.makerStore.flow

    /** The Vigilant wallet's latest balance, for the strip above the tabs ([WalletBalance.flow]). */
    val wallet get() = c.wallet.flow

    /**
     * Tj's open bets and resting bids by game, for the small "$21.30 in game" button on every listed bet (Tj, 2026-10-04; [GameBets]). Rebuilt off the main
     * thread only when the bets or the bids change (a scan's progress changes neither), and only while a screen collects it. Lazy: nothing is read until asked.
     */
    val gameBets: StateFlow<com.tjshea.vigilant.data.tracker.GameBets> by lazy {
        combine(_state.map { it.bets }.distinctUntilChanged(), c.makerStore.flow.map { it.orEmpty() }.distinctUntilChanged()) { bets, bids ->
            com.tjshea.vigilant.data.tracker.GameBets.of(bets, bids)
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.tjshea.vigilant.data.tracker.GameBets.EMPTY)
    }

    /** Reads the wallet again when the last reading is older than [maxAgeMs] (0: now, Tj's tap on the strip). */
    fun refreshWallet(maxAgeMs: Long = WalletBalance.FRESH_MS) {
        if (!c.wallet.isSetUp) return
        viewModelScope.launch(Dispatchers.IO) { runCatching { c.wallet.fresh(maxAgeMs) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it } }
    }

    /** The bids the latest scan's lines would get, posting nothing (the tab opening). */
    fun makerPreview() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { c.makerStore.all(); c.makerDenials.all(); c.maker.preview() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    /** "Run a pass now": fills read, bids posted, moved and cancelled as the rules say (only fills read with bids off). */
    fun makerRun() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.maker.run("Bids tab") } }
            r.exceptionOrNull()?.let { _toasts.tryEmit("Bids: ${it.message ?: it.javaClass.simpleName}") }
            r.getOrNull()?.let { rep ->
                _toasts.tryEmit(
                    rep.stopped?.let { "Bids: $it (${rep.cancelled} cancelled)" }
                        ?: "Bids: ${rep.placed} posted, ${rep.cancelled} moved or cancelled, ${rep.fills.size} filled, ${rep.resting} resting",
                )
            }
        }
    }

    fun makerPost(outcomeId: String) {
        viewModelScope.launch {
            val why = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.maker.post(outcomeId) }.getOrElse { it.message ?: it.javaClass.simpleName } }
            _toasts.tryEmit(why?.let { "Not posted: $it" } ?: "Bid posted (post-only: it rests until someone takes it)")
        }
    }

    fun makerCancel(orderId: String) {
        viewModelScope.launch {
            val why = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.maker.cancel(orderId) }.getOrElse { it.message ?: it.javaClass.simpleName } }
            _toasts.tryEmit(why?.let { "Not cancelled: $it" } ?: "Bid cancelled; that side is denied until its game (undo under Denied)")
        }
    }

    /** Every side Tj denied ([com.tjshea.vigilant.data.novig.trading.maker.MakerDenials]); null until first read. */
    val makerDenied get() = c.makerDenials.flow

    fun makerDeny(outcomeId: String) {
        viewModelScope.launch {
            val why = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.maker.deny(outcomeId) }.getOrElse { it.message ?: it.javaClass.simpleName } }
            _toasts.tryEmit(why?.let { "Not denied: $it" } ?: "Denied: no bid on that side until its game (undo under Denied)")
        }
    }

    fun makerUndoDeny(outcomeId: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { c.maker.undoDeny(outcomeId) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it } }
    }

    fun makerCancelAll() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO + NonCancellable) { runCatching { c.maker.cancelAll("Cancelled by you") } }
            _toasts.tryEmit(n.getOrNull()?.let { "Cancelled $it bid${if (it == 1) "" else "s"}" } ?: "Couldn't cancel: ${n.exceptionOrNull()?.message ?: "betting isn't set up"}")
        }
    }

    fun lockIn(marketId: String, confirmed: Double) {
        val view = _state.value.locks[marketId] ?: return
        val market = view.market ?: return
        if (_state.value.locking != null) return
        val placer = c.autoBetPlacer() ?: run { _toasts.tryEmit("Betting through Novig's API isn't set up (Settings › Betting & Novig account)."); return }
        _state.update { it.copy(locking = marketId) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO + NonCancellable) {
                runCatching {
                    placer.placeLock(view.holding, market, maxOf(com.tjshea.vigilant.data.novig.trading.LockIn.MIN_PROFIT, confirmed - 0.005), view.pushable, auto = false)
                }.getOrElse { com.tjshea.vigilant.data.novig.trading.PlaceResult.Failed(it.message ?: it.javaClass.simpleName) }
            }
            _state.update { it.copy(locking = null) }
            _toasts.tryEmit(
                when (r) {
                    is com.tjshea.vigilant.data.novig.trading.PlaceResult.Placed -> "Locked: ${r.bet.contracts} contracts of ${view.otherName} bought at ${r.bet.american?.let { com.tjshea.vigilant.engine.Odds.formatAmerican(it) } ?: "?"}"
                    is com.tjshea.vigilant.data.novig.trading.PlaceResult.NotFilled -> "Not locked: the price moved before it could fill. Nothing was bought."
                    is com.tjshea.vigilant.data.novig.trading.PlaceResult.Refused ->
                        if (r.reason.contains("a lock needs") || r.reason.contains("minimum profit")) "Not locked: the price moved. Look again." else "Not locked: ${r.reason}"
                    is com.tjshea.vigilant.data.novig.trading.PlaceResult.Failed -> "Not locked: ${r.message}"
                    is com.tjshea.vigilant.data.novig.trading.PlaceResult.Unconfirmed -> r.message
                },
            )
            c.eventLog.info("LOCK", "lock by hand: ${r.javaClass.simpleName}")
            scanLocks()
        }
    }

    fun settleBets(force: Boolean = false, announce: Boolean = false) {
        if (announce) {
            if (_state.value.gradingBets) return
            _state.update { it.copy(gradingBets = true) }
        }
        viewModelScope.launch {
            val report = try {
                withContext(Dispatchers.IO) { gradeAll(force) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                if (announce) _state.update { it.copy(gradingBets = false) }
            }
            if (report == null) {
                if (announce) _toasts.tryEmit("Couldn't read the final scores")
                return@launch
            }
            if (announce) {
                _toasts.tryEmit(
                    when {
                        report.stopped && report.settled == 0 -> "The score feeds (ESPN, MLB) didn't answer: try again in a minute"
                        report.asked == 0 -> "No open bet is due a result yet (a game has to be an hour old)"
                        else -> listOfNotNull(
                            "Graded ${report.settled} of ${report.asked} bet${if (report.asked == 1) "" else "s"} from final scores",
                            "${report.waiting} game${if (report.waiting == 1) "" else "s"} not over yet".takeIf { report.waiting > 0 },
                            "${report.manual} need${if (report.manual == 1) "s" else ""} a tap (each says why)".takeIf { report.manual > 0 },
                            "feeds stopped answering".takeIf { report.stopped },
                        ).joinToString(" · ")
                    },
                )
            } else if (report.settled > 0) {
                _toasts.tryEmit("Settled ${report.settled} bet${if (report.settled == 1) "" else "s"} from final scores")
            }
        }
    }

    /**
     * One grading pass: the bets placed through Novig's API from Novig's own ledger ([ApiSettler], when betting is set up), then every other bet
     * from final scores ([BetSettler]). One report that adds both up.
     */
    private suspend fun gradeAll(force: Boolean, forceCloses: Boolean = false): com.tjshea.vigilant.data.tracker.BetSettler.Report {
        // Fills Novig has that the Tracker doesn't (an order placed just as the app closed): added before grading, so nothing waits for a tap.
        c.apiSync?.let { sync ->
            try {
                sync.run()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Novig unreachable: the next pass looks again.
            }
        }
        val api = c.apiSettler?.let { s ->
            try {
                s.run().also {
                    if (it.reopened > 0) {
                        c.eventLog.warn("SETTLE", "${it.reopened} grade(s) taken back: a market held on both sides can't lose on both (Novig's silence isn't a loss there)")
                        c.eventLog.count("settle.reopened")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val scores = c.settler.run(force)
        // Closing lines of games that started while Vigilant wasn't running (ESPN, Novig's trade history): for CLV.
        c.backfillCloses(force = forceCloses)
        return if (api == null) scores else scores.copy(
            asked = scores.asked + api.asked, settled = scores.settled + api.settled, waiting = scores.waiting + api.waiting,
            manual = scores.manual + api.manual,
        )
    }

    /** "Grade automatically": an open bet whose result Tj tapped and undid goes back to the score feeds. */
    fun regradeBet(id: String) {
        viewModelScope.launch {
            if (runCatching { c.tracker.regrade(id) }.isFailure) _toasts.tryEmit("Couldn't save") else settleBets(force = true)
        }
    }

    /** Corrects the price a bet was filled at (American odds); its cost, EV and profit follow. */
    fun setPrice(id: String, american: Int) {
        viewModelScope.launch { if (runCatching { c.tracker.setPrice(id, american) }.isFailure) _toasts.tryEmit("Couldn't save") }
    }

    /**
     * Re-reads one open bet's odds now (the sheet's "Re-read books" / "Price now", and, for a CNO bet, when it opens on old odds): a CNO
     * bet's CNO game page; a Vigilant bet (no page), or a CNO bet whose page couldn't be read, is priced from Vigilant's own fair odds
     * ([OpenBetPricer]). [quiet]: no word when it couldn't be read.
     */
    fun rereadBooks(id: String, quiet: Boolean = false) {
        if (_state.value.rereadingBet != null) return
        if (_state.value.settings.paused) {
            if (!quiet) _toasts.tryEmit(PAUSED_TOAST)
            return
        }
        _state.update { it.copy(rereadingBet = id) }
        val settings = _state.value.settings
        viewModelScope.launch {
            var why: String? = null
            val ok = try {
                withContext(Dispatchers.IO) {
                    val bet = c.tracker.all().firstOrNull { it.id == id }
                    if (bet?.gameUrl != null && c.recheck.checkOne(id)) {
                        true
                    } else {
                        // Tj's tap: priced whatever the scanner choice, as Check odds now is (Tj, 2026-10-02).
                        val priced = c.betPricer?.run(settings, listOf(id), anyScanner = true)?.priced == 1
                        if (!priced) why = c.tracker.all().firstOrNull { it.id == id }?.nowNote
                        priced
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                _state.update { it.copy(rereadingBet = null) }
            }
            if (!ok && !quiet) _toasts.tryEmit(why?.let { "Couldn't price this bet: $it" } ?: "Couldn't read this bet's odds: CrazyNinjaOdds didn't answer, or the bet has left its page")
        }
    }

    /** Remembers the Novig outcome a bet's link turned out to be (Replace found it), so the next tap needs no lookup. */
    fun rememberOutcome(id: String, outcomeId: String) {
        viewModelScope.launch {
            runCatching { c.tracker.edit(id) { if (it.outcomeId.isBlank()) it.copy(outcomeId = outcomeId) else it } }
            // Its market too (the side alone left the bet out of every Novig price read: Tj, 2026-10-02 20:06Z).
            val bet = runCatching { c.tracker.all().firstOrNull { it.id == id } }.getOrNull() ?: return@launch
            if (!AppBook.isNovig || bet.marketId.isNotBlank()) return@launch
            val found = runCatching { withContext(Dispatchers.IO) { com.tjshea.vigilant.data.tracker.NovigIds.find(listOf(bet)) { row, o -> c.betFinder.locate(row, o) } } }.getOrNull() ?: return@launch
            runCatching { c.tracker.recordIds(found.ids, found.why, System.currentTimeMillis()) }
        }
    }

    fun setReplacing(id: String?) = _state.update { it.copy(replacingBet = id) }

    /** Settings › Diagnostics & about: one page of settings, the last scan, API usage, the background scan and the Tracker, to copy (Tj, 2026-09-29). */
    /** The heap and what the app holds in it, for Diagnostics (counts, not bytes: a scan result, the boards kept between scans, the books pages). */
    private fun memoryNow(): Diagnostics.Memory {
        val r = _state.value.result
        val held = (c.scanner as? com.tjshea.vigilant.data.scanner.Scanner)?.holdings()
        val lines = listOfNotNull(
            "Scan result: ${r?.opportunities?.size ?: 0} priced sides in ${r?.games?.size ?: 0} games" + if (r?.partial == true) " (partial: a scan is running)" else "",
            held?.let { "Kept between scans: ${it.snapshots} fair-odds boards (${it.events} games, ${it.bookMarkets} book lines) · ${it.books} Novig books + ${it.cachedBooks} in the books cache" },
            "CrazyNinjaOdds: ${_state.value.books.size} game pages with every book's odds · Tracker: ${_state.value.bets.size} bets, ${_state.value.bets.sumOf { it.books.size }} book lines",
        )
        return Diagnostics.Memory(com.tjshea.vigilant.data.MemoryGuard.usedMb(), com.tjshea.vigilant.data.MemoryGuard.maxMb(), lines)
    }

    fun showDiagnostics() {
        buildDiagnostics()
        // ParlayAPI's own figures (free) and the report again with them, while it's still open.
        if (keysFor(ApiProvider.PARLAY).isEmpty()) return
        viewModelScope.launch {
            val answered = withContext(Dispatchers.IO) { runCatching { c.parlayAccount.refresh(force = true) }.getOrDefault(0) }
            if (answered > 0 && _state.value.report?.title == "Diagnostics") buildDiagnostics()
        }
    }

    private fun buildDiagnostics() {
        viewModelScope.launch {
            val inputs = gatherDiag()
            // The meter as it stands this moment (a balance just read may not have reached the state yet).
            val text = withContext(Dispatchers.Default) { DiagnosticsFile.build(_state.value.copy(usage = c.usage.flow.value), diagnosticsExtras(inputs), System.currentTimeMillis()) }
            _state.update { it.copy(report = ReportUi("Diagnostics", text)) }
        }
    }

    /** What a diagnostics file needs that isn't state in memory: files and the system log, read off the main thread. */
    private class DiagInputs(
        val problems: List<com.tjshea.vigilant.data.diag.Problem>,
        val cycles: com.tjshea.vigilant.data.diag.CycleBook,
        val logcat: List<com.tjshea.vigilant.data.diag.LogcatTail.Line>,
        val storage: List<Pair<String, Long>>,
        val previous: com.tjshea.vigilant.data.diag.Snap?,
        /** Read from the file, not the store's flow: the flow is null until something reads it (bids off, the Bids tab not opened). */
        val makerBids: List<com.tjshea.vigilant.data.novig.trading.maker.MakerBid>,
        /** The scan study's counts: read from its journal off the main thread. */
        val study: com.tjshea.vigilant.data.study.ScanStudy.Overview?,
        /** Why the real-money burst trader is locked, or null when the recorder has proved it ([VigilantApp.burstProof]). */
        val burstProofReason: String?,
        /** The leagues the recorder has proved on their own windows ([VigilantApp.burstProof]). */
        val burstProvedLeagues: Set<String>,
    )

    private suspend fun gatherDiag(): DiagInputs = withContext(Dispatchers.IO) {
        DiagInputs(
            problems = runCatching { c.problems.recent() }.getOrDefault(emptyList()),
            cycles = runCatching { c.cycleLog.summary() }.getOrDefault(com.tjshea.vigilant.data.diag.CycleBook()),
            logcat = com.tjshea.vigilant.data.diag.LogcatTail.read(android.os.Process.myPid()),
            storage = runCatching { DiagnosticsShare.storage(getApplication()) }.getOrDefault(emptyList()),
            previous = runCatching { c.diagHistory.all().lastOrNull() }.getOrNull(),
            makerBids = runCatching { c.makerStore.all() }.getOrDefault(emptyList()),
            study = runCatching { c.study.overview() }.getOrNull(),
            burstProofReason = runCatching { c.burstProof().reason }.getOrDefault("the proof could not be read"),
            burstProvedLeagues = runCatching { c.burstProof().leagues }.getOrDefault(emptySet()),
        )
    }

    /** One-shot hand-offs to the activity that must start something (the share sheet): buffered, so one made while the screen is away isn't lost. */
    private val shares = kotlinx.coroutines.channels.Channel<android.content.Intent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val shareRequests: kotlinx.coroutines.flow.Flow<android.content.Intent> = shares.receiveAsFlow()

    /**
     * Settings › Diagnostics & about › Share with Claude (Tj, 2026-10-02): makes the file ([DiagnosticsFile.build]), keeps this report's numbers so the next one can be compared
     * with it, and hands Android's share sheet to the screen. Never throws: a failure is a toast.
     */
    fun shareDiagnostics() {
        viewModelScope.launch {
            _toasts.tryEmit("Making the diagnostics file…")
            val intent = try {
                withContext(Dispatchers.IO) {
                    val inputs = gatherDiag()
                    val now = System.currentTimeMillis()
                    val extras = diagnosticsExtras(inputs)
                    val st = _state.value.copy(usage = c.usage.flow.value)
                    val text = DiagnosticsFile.build(st, extras, now)
                    val file = DiagnosticsShare.write(getApplication(), text, DiagnosticsFile.fileName(extras.versionName, now))
                    // A copy in Downloads/Vigilant too (Tj, 2026-10-02 17:01Z), whatever happens to the share.
                    runCatching { DiagnosticsShare.saveToDownloads(getApplication<Application>().contentResolver, file) }
                        .onSuccess { _toasts.tryEmit("Saved to ${DiagnosticsShare.DOWNLOADS_DIR}/${file.name}"); c.eventLog.info("DIAG", "diagnostics file saved to ${DiagnosticsShare.DOWNLOADS_DIR}") }
                        .onFailure { e -> _toasts.tryEmit("Couldn't save it to Downloads (${e.message ?: e.javaClass.simpleName})"); c.eventLog.warn("DIAG", "couldn't save the diagnostics file to Downloads: ${e.message}") }
                    c.diagHistory.add(Advisor.snap(st, extras, now, Advisor.findings(st, extras, now)))
                    c.eventLog.info("DIAG", "diagnostics file made (${file.length() / 1024} KB)")
                    c.eventLog.flush(force = true)
                    DiagnosticsShare.intent(getApplication(), file, extras.versionName)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                c.eventLog.error("DIAG", "couldn't make the diagnostics file", e)
                null
            }
            if (intent == null) _toasts.tryEmit("Couldn't make the diagnostics file") else shares.send(intent)
        }
    }

    /** The scan study's line for Settings › Diagnostics & about (how many bets are logged, graded and closed): read when that page opens. */
    fun refreshStudy() {
        viewModelScope.launch(Dispatchers.IO) {
            val o = runCatching { c.study.overview() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull() ?: return@launch
            _state.update { it.copy(studyNote = StudyText.note(o, System.currentTimeMillis())) }
        }
    }

    /** The burst recorder's line for the Diagnostics page: what it is doing and what it has recorded. */
    fun refreshBurst() {
        viewModelScope.launch(Dispatchers.IO) {
            val note = runCatching { BurstText.note(c.burst.status.value, c.burstJournal.readAll()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull() ?: return@launch
            val proof = runCatching { c.burstProof(force = true) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            val trade = c.burstTrader.status.value
            _state.update { it.copy(burstNote = note, burstProofReason = proof?.reason ?: if (proof == null) "the proof could not be read" else null, burstProofRead = true, burstProvedLeagues = proof?.leagues.orEmpty(), burstTrade = trade) }
        }
    }

    /** Reads the live feed test's line for Settings › Diagnostics & about (the page asks every few seconds while it is open). */
    fun refreshFeedRace() {
        viewModelScope.launch(Dispatchers.IO) {
            val note = runCatching { FeedRaceText.note(c.feedRace.status.value, System.currentTimeMillis()) }.getOrNull() ?: return@launch
            _state.update { it.copy(feedRaceNote = note) }
        }
    }

    /** Settings › Diagnostics & about › Share live feed test with Claude (Tj, 2026-10-07): the verdict, the table, every score that moved Novig and the raw tape, as one file saved to Downloads/Vigilant and shared. */
    fun shareFeedRace() {
        viewModelScope.launch {
            _toasts.tryEmit("Making the live feed test file…")
            val intent = try {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val app = getApplication<Application>()
                    val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                    val report = c.feedRace.makeReport()
                    val status = c.feedRace.status.value
                    val meta = com.tjshea.vigilant.data.live.FeedRaceExport.Meta(
                        info?.versionName ?: "?", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", status.sports, c.feedRace.running,
                    )
                    val file = DiagnosticsShare.writeFeedRace(app, com.tjshea.vigilant.data.live.FeedRaceExport.fileName(meta.versionName, now)) { w ->
                        com.tjshea.vigilant.data.live.FeedRaceExport.write(w, report, status, meta, c.feedRaceJournal, now)
                    }
                    runCatching { DiagnosticsShare.saveToDownloads(app.contentResolver, file) }
                        .onSuccess { _toasts.tryEmit("Saved to ${DiagnosticsShare.DOWNLOADS_DIR}/${file.name}") }
                        .onFailure { e -> _toasts.tryEmit("Couldn't save it to Downloads (${e.message ?: e.javaClass.simpleName})") }
                    c.eventLog.info("DIAG", "live feed test file made (${file.length() / 1024} KB)")
                    DiagnosticsShare.feedRaceIntent(app, file, meta.versionName)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                c.eventLog.error("DIAG", "couldn't make the live feed test file", e)
                null
            }
            if (intent == null) _toasts.tryEmit("Couldn't make the live feed test file") else shares.send(intent)
        }
    }

    /** Settings › Diagnostics & about › Share live burst study with Claude (Tj, 2026-10-06): the recorder's report and every window, as one file, saved to Downloads/Vigilant and shared like the scan study. */
    fun shareBurstStudy() {
        viewModelScope.launch {
            _toasts.tryEmit("Making the live burst study file…")
            val intent = try {
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val app = getApplication<Application>()
                    val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                    val s = _state.value.settings
                    val meta = com.tjshea.vigilant.data.novig.burst.BurstExport.Meta(
                        info?.versionName ?: "?", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", s.apiMaxStake, s.burstLeagues,
                    )
                    val records = c.burstJournal.readAll()
                    val file = DiagnosticsShare.writeBurst(app, com.tjshea.vigilant.data.novig.burst.BurstExport.fileName(meta.versionName, now)) { w ->
                        com.tjshea.vigilant.data.novig.burst.BurstExport.write(w, records, c.burst.latency.note(), meta, now, runCatching { c.burstTradeJournal.readAll() }.getOrDefault(emptyList()))
                    }
                    runCatching { DiagnosticsShare.saveToDownloads(app.contentResolver, file) }
                        .onSuccess { _toasts.tryEmit("Saved to ${DiagnosticsShare.DOWNLOADS_DIR}/${file.name}") }
                        .onFailure { e -> _toasts.tryEmit("Couldn't save it to Downloads (${e.message ?: e.javaClass.simpleName})") }
                    c.eventLog.info("DIAG", "live burst study file made (${file.length() / 1024} KB)")
                    DiagnosticsShare.burstIntent(app, file, meta.versionName)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                c.eventLog.error("DIAG", "couldn't make the live burst study file", e)
                null
            }
            if (intent == null) _toasts.tryEmit("Couldn't make the live burst study file") else shares.send(intent)
        }
    }

    /**
     * Settings › Diagnostics & about › Share scan study with Claude (Tj, 2026-10-03): the games that ended since the last pass are graded first (a minute at
     * most), then the whole log is written as one file for Claude ([com.tjshea.vigilant.data.study.StudyExport]: the goal, a dictionary, the sums, a line per
     * bet), saved to Downloads/Vigilant like the diagnostics file, and Android's share sheet is handed to the screen. Never throws: a failure is a toast.
     */
    fun shareScanStudy() {
        viewModelScope.launch {
            _toasts.tryEmit("Making the scan study file…")
            val intent = try {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(STUDY_SETTLE_WAIT_MS) { c.settleStudy() }
                    c.study.flush()
                    val now = System.currentTimeMillis()
                    val app = getApplication<Application>()
                    val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
                    val meta = com.tjshea.vigilant.data.study.StudyExport.Meta(
                        versionName = info?.versionName ?: "?",
                        versionCode = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it).toInt() } ?: 0,
                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                        rules = com.tjshea.vigilant.data.scanner.PresetRules.of(_state.value.settings).summary(),
                        wide = StudyText.wideNote(c.cno.wide.value, c.cno.state.value.snapshot, _state.value.settings.scanStudyHidden, now).takeIf { _state.value.settings.scanStudyHidden },
                    )
                    val tracked = runCatching { c.tracker.all() }.getOrDefault(emptyList())
                    val bids = runCatching { c.makerStore.all() }.getOrDefault(emptyList())
                    val file = DiagnosticsShare.writeStudy(app, com.tjshea.vigilant.data.study.StudyExport.fileName(meta.versionName, now)) { w ->
                        com.tjshea.vigilant.data.study.StudyExport.write(w, c.study.journal, tracked, meta, now, java.io.File(app.cacheDir, "study-export.tmp"), bids = bids)
                    }
                    runCatching { DiagnosticsShare.saveToDownloads(app.contentResolver, file) }
                        .onSuccess { _toasts.tryEmit("Saved to ${DiagnosticsShare.DOWNLOADS_DIR}/${file.name}") }
                        .onFailure { e -> _toasts.tryEmit("Couldn't save it to Downloads (${e.message ?: e.javaClass.simpleName})"); c.eventLog.warn("DIAG", "couldn't save the scan study file to Downloads: ${e.message}") }
                    c.eventLog.info("DIAG", "scan study file made (${file.length() / 1024} KB)")
                    c.eventLog.flush(force = true)
                    DiagnosticsShare.studyIntent(app, file, meta.versionName)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                c.eventLog.error("DIAG", "couldn't make the scan study file", e)
                null
            }
            if (intent == null) _toasts.tryEmit("Couldn't make the scan study file") else shares.send(intent)
        }
    }

    /** What Android allows Vigilant on this phone, for Diagnostics' health checks (each null where it couldn't be read). */
    private fun phoneNow(app: Application): Diagnostics.Phone {
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val network = caps?.let {
            listOfNotNull(
                "Wi-Fi".takeIf { _ -> it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) },
                "mobile".takeIf { _ -> it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) },
                "VPN".takeIf { _ -> it.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) },
            ).joinToString(" + ").ifEmpty { null }
        }
        return Diagnostics.Phone(
            notifications = runCatching { androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled() }.getOrNull(),
            exactAlarms = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 31) app.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms() else true
            }.getOrNull(),
            batteryUnrestricted = runCatching { app.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) }.getOrNull(),
            overlay = runCatching { android.provider.Settings.canDrawOverlays(app) }.getOrNull(),
            dataSaver = runCatching { cm?.restrictBackgroundStatus?.let { it == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED } }.getOrNull(),
            batterySaver = runCatching { app.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode }.getOrNull(),
            dozing = runCatching { app.getSystemService(android.os.PowerManager::class.java).isDeviceIdleMode }.getOrNull(),
            standbyBucket = runCatching { Diagnostics.bucketName(app.getSystemService(android.app.usage.UsageStatsManager::class.java).appStandbyBucket) }.getOrNull(),
            online = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: (cm != null).takeIf { !it },
            network = network,
            batteryPct = runCatching { app.getSystemService(android.os.BatteryManager::class.java).getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } }.getOrNull(),
            charging = runCatching { app.getSystemService(android.os.BatteryManager::class.java).isCharging }.getOrNull(),
            thermal = runCatching { Diagnostics.thermalName(app.getSystemService(android.os.PowerManager::class.java).currentThermalStatus) }.getOrNull(),
        )
    }

    private fun diagnosticsExtras(g: DiagInputs): Diagnostics.Extras {
        val app = getApplication<Application>()
        val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        return Diagnostics.Extras(
            versionName = info?.versionName ?: "?",
            versionCode = info?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it).toInt() } ?: 0,
            installedAtMs = info?.lastUpdateTime?.takeIf { it > 0 },
            device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            autoScan = c.autoScan.status.value,
            autoBet = c.autoBet.status.value,
            makerBids = g.makerBids,
            maker = c.maker.status.value,
            memory = memoryNow(),
            autoScanServiceRunning = AutoScanService.running,
            keepAwakeHeld = AutoScanService.keepAwakeHeld,
            kalshiPace = runCatching { c.kalshiPaceNote() }.getOrNull(),
            feedRace = runCatching {
                val st = c.feedRace.status.value
                (c.feedRace.lastReport ?: c.feedRace.computeReport()).takeIf { st.readings > 0 || it.sightings > 0 }?.let { r ->
                    listOf(FeedRaceText.note(st, System.currentTimeMillis())) + r.lines()
                }
            }.getOrNull(),
            sharpFeeds = runCatching { com.tjshea.vigilant.data.reference.SharpBooks.feedsAmong(c.referenceSources(_state.value.settings, background = true)) }.getOrDefault(emptyList()),
            lowUsagePlan = _state.value.settings.takeIf { it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE }?.let { runCatching { c.lowUsagePlan(it) }.getOrNull() },
            burstReport = runCatching {
                BurstText.diagnostics(
                    c.burst.status.value, c.burstJournal.readAll(), c.burst.latency.note(), _state.value.settings, c.burst.running,
                    trades = c.burstTradeJournal.readAll(), trader = c.burstTrader.status.value, proofReason = g.burstProofReason, provedLeagues = g.burstProvedLeagues,
                )
            }.getOrNull(),
            sharpCalls = c.sharp.calls,
            sharpFailures = c.sharp.failures,
            sharpAnswers = c.sharp.answeredBy,
            cycles = g.cycles,
            net = c.netStats.snapshot(),
            events = c.eventLog.events(),
            counters = c.eventLog.counters(),
            eventsSinceMs = c.eventLog.sinceMs(),
            perf = c.perf.summaries(),
            coldStartMs = c.perf.coldStartMs,
            frames = c.frames.snapshot(),
            scanCpu = c.scanCpu,
            logcat = g.logcat,
            storage = g.storage,
            previous = g.previous,
            lastScan = c.lastScanCost,
            lastCheck = c.lastCheckCost,
            closingAlarmAtMs = ClosingAlarm.nextAtMs,
            backfill = c.lastBackfill,
            study = g.study,
            studyProblem = c.study.lastProblem,
            studyWide = StudyText.wideNote(c.cno.wide.value, c.cno.state.value.snapshot, _state.value.settings.scanStudyHidden, System.currentTimeMillis()),
            novigTradeBytes = c.novigCloses.bytesRead,
            keyStanddowns = c.novig.keyStanddowns(),
            keyDownNow = c.novig.keyDown(System.currentTimeMillis()),
            parlayCloseRequests = c.parlayCloses.requests,
            parlayAccounts = c.parlayAccount.last,
            parlayExtras = mapOf(
                "injuries" to c.parlayInjuries.requests, "movers" to c.parlayMovers.requests,
                "second opinions" to c.parlayVerdicts.requests, "picks" to c.parlayBestBets.requests,
            ),
            injuryReports = c.injuries.book.value.size,
            phone = phoneNow(app),
            problems = g.problems,
            exits = runCatching { AppExits.recent(app) }.getOrDefault(emptyList()),
        )
    }

    /** Settings › Diagnostics & about › Grading check: what Novig's ledger and positions say about each API bet, beside what the Tracker did (Tj, 2026-09-29). */
    fun showGradingCheck() {
        val connection = _state.value.novig.connection ?: return
        val trading = c.trading
        if (trading == null) {
            _toasts.tryEmit("Betting through the API isn't set up on this phone")
            return
        }
        _state.update { it.copy(report = ReportUi(GRADING_CHECK, "Reading Novig's ledger and positions…", busy = true)) }
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) { com.tjshea.vigilant.data.tracker.ApiGradingCheck(c.tracker, trading, connection.subaccountKeyId).report() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't build the report: ${e.message ?: e.javaClass.simpleName}"
            }
            _state.update { s -> if (s.report?.title == GRADING_CHECK) s.copy(report = ReportUi(GRADING_CHECK, text)) else s }
        }
    }

    fun dismissReport() = _state.update { it.copy(report = null) }

    fun reportCopied() {
        _toasts.tryEmit("Copied: paste it to Claude")
    }

    /**
     * "Check odds now" (Tj, 2026-09-27; every open bet since 2026-09-29): each open bet's EV now, against the price it was bet at.
     * CNO's bets have their CNO game page read again (a page every half second, three at once); Vigilant's own bets (no CNO page)
     * are priced from Vigilant's own fair odds by [OpenBetPricer] at the same time, and so is any bet CNO couldn't read, whatever the scanner
     * choice (Tj, 2026-10-02: a Vigilant bet stayed "as of 2h ago" after a check with the scanner on CNO only). The count on
     * the button covers both; the toast counts every open bet ([BetRecheck.Report.summary]), and a bet that couldn't be priced
     * says why on its own card. Finished games' results are graded in the same tap.
     */
    fun checkOdds(resume: Boolean = true) {
        val start = _state.value
        if (start.checkingOdds) return
        // Reading every bet's CNO page is CNO reading, so a pause can't hold it back: Tj's tap resumes the scanner first ([resumeThen]).
        if (start.settings.paused) {
            if (resume) resumeThen { checkOdds(resume = false) } else _toasts.tryEmit(PAUSED_TOAST)
            return
        }
        val began = System.currentTimeMillis()
        // The counter starts again from 0: only bets re-read from here on count.
        _state.update { it.copy(checkingOdds = true, checkProgress = null, checkStartedAtMs = began) }
        // The focus (Tj, 2026-10-01: "pause other parts of the app such as the cno scanner so that it focuses on refreshing the current odds and EV and
        // stats"): the background cycle (auto-bet with it), the CNO list's refresh, scans, the widget's rescans and the movers wait until this ends.
        c.focus.begin()
        val settings = start.settings
        val usageBefore = c.usage.flow.value
        viewModelScope.launch {
            // A scan under way (Tj's, a background cycle's) stops, as the Pause switch stops it.
            if (c.runner.running) {
                c.runner.stop()
                ScanService.cancelDone(getApplication())
            }
            runCatching { c.lastCheck.update { com.tjshea.vigilant.data.tracker.LastCheck(began) } }
            var report: com.tjshea.vigilant.data.tracker.BetRecheck.Report? = null
            // The finished games' results are graded at the same time (the score feeds are ESPN and MLB, not CNO, so it costs no time):
            // one tap covers every open bet, the ones still to play and the ones already over.
            val grading = async(Dispatchers.IO) {
                try {
                    // Every closing line that exists is collected in the same tap (Tj, 2026-10-01: "make sure it gets all available closing line data").
                    gradeAll(force = true, forceCloses = true)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            try {
                // Vigilant's own pricing runs whatever the scanner choice (Tj, 2026-10-02: "I want the check odds now to refresh the current odds and EV
                // for every single open bet regardless of scanner"); only Vigilant MGM has none (not Novig's).
                val pricer = c.betPricer
                val plan = c.recheck.preview()
                val cnoTotal = plan.todo.size
                val ownBets = plan.vigilantBets.map { it.id }
                // Every open bet still to start is priced from Vigilant's own fair odds too, CNO's included (Tj, 2026-09-30: "always scan
                // relevant vigilant odds in addition to the cno scan ... always get full updates on all of my bets and an accurate stats
                // reading"): one bets-only pass beside CNO's page reads, then each bet's two reads are combined (BetTracker.mergeReads).
                // Games under way too, up to 4 hours in, as CNO's pages are read (Tj, 2026-09-30: "every single open bet refreshed regardless of
                // what scanner found the bet").
                val open = if (pricer != null) c.tracker.all().filter { it.status == BetStatus.PENDING && com.tjshea.vigilant.data.tracker.BetsScope.readable(it, began) } else emptyList()
                val everyBet = open.map { it.id }
                // Bets with no CNO page (Vigilant's own, ParlayAPI's, synced from Novig) get every book's read too, as CNO's bets get CNO's page.
                val noPage = open.filter { it.gameUrl == null }.map { it.id }
                val cnoDone = java.util.concurrent.atomic.AtomicInteger()
                val ownDone = java.util.concurrent.atomic.AtomicInteger()
                val booksDone = java.util.concurrent.atomic.AtomicInteger()
                // "n of N read", at most every CHECK_PROGRESS_MS (each bet read used to redraw the whole app) and always when all are read.
                val lastShown = java.util.concurrent.atomic.AtomicLong(0L)
                fun publish() {
                    val total = cnoTotal + everyBet.size + noPage.size
                    val done = cnoDone.get() + ownDone.get() + booksDone.get()
                    val now = System.currentTimeMillis()
                    val last = lastShown.get()
                    if (done < total && now - last < CHECK_PROGRESS_MS) return
                    if (!lastShown.compareAndSet(last, now) && done < total) return
                    _state.update { it.copy(checkProgress = if (total > 0) done to total else null) }
                }
                publish()
                report = kotlinx.coroutines.coroutineScope {
                    val own = if (pricer != null && everyBet.isNotEmpty()) async(Dispatchers.IO) {
                        // Bets logged without Novig's ids are looked up in Novig's catalog first, so this pass (and Novig's own price) covers them too.
                        runCatching { lookUpNovigIds(force = true) }
                        pricer.run(settings, everyBet, alongside = true, anyScanner = true).also { ownDone.set(everyBet.size); publish() }
                    } else null
                    val books = if (noPage.isNotEmpty()) async(Dispatchers.IO) {
                        c.recheck.readWithoutPage(noPage) { done, _ -> booksDone.set(done); publish() }.also { booksDone.set(noPage.size); publish() }
                    } else null
                    val cno = async(Dispatchers.IO) { c.recheck.run { done, _ -> cnoDone.set(done); publish() } }
                    var r = cno.await()
                    books?.await()
                    val priced = own?.await()
                    if (pricer != null) {
                        // What each bet shows and counts: both reads averaged, else whichever it got, else why not. Saved even if the screen went.
                        val merged = withContext(NonCancellable + Dispatchers.IO) {
                            c.tracker.mergeReads(everyBet, since = began, cnoSince = began - com.tjshea.vigilant.data.tracker.BetRecheck.FRESH_MS, reasons = priced?.reasons.orEmpty())
                        }
                        r = r.withEveryRead(merged, ownBets)
                    }
                    r
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                report = null
            } finally {
                _state.update { it.copy(checkingOdds = false, checkProgress = null) }
                // Everything that waited goes on: the CNO list refreshes again (the watch's hold follows checkingOdds), and a background cycle that was
                // skipped runs now rather than a whole interval from now.
                c.focus.end()
                c.autoScan.resumed()
                if (_state.value.settings.activeAutoScan != com.tjshea.vigilant.data.scanner.AutoScanMode.OFF) runCatching { AutoScanService.start(getApplication()) }
            }
            // What the round cost each API and how it went, for Settings › Diagnostics & about.
            report?.let { r ->
                c.lastCheckCost = RoundCost(
                    began, System.currentTimeMillis() - began, UsageDelta.between(usageBefore, c.usage.flow.value),
                    note = r.roundNote(c.betPricer != null, c.cno.state.value.lastPause?.takeIf { (c.cno.state.value.lastPauseAtMs ?: 0L) >= began }),
                )
            }
            val graded = grading.await()
            // The closes the same tap went looking for: what was found, and what is still missing and why.
            val closes = graded?.let { CloseText.summary(c.lastBackfill?.takeIf { it.forced }, CloseText.missing(c.tracker.all(), System.currentTimeMillis())) }
            _toasts.tryEmit((report?.summary(graded = graded) ?: "Couldn't check the odds") + (closes?.let { " · $it" } ?: ""))
            // And the locks on the API bets, from Novig's books now.
            scanLocks()
        }
    }

    fun setStake(id: String, stake: Double) {
        if (!(stake > 0.0) || stake > 1_000_000.0) return
        viewModelScope.launch { if (runCatching { c.tracker.setStake(id, stake) }.isFailure) _toasts.tryEmit("Couldn't save") }
    }

    fun settleBet(id: String, status: BetStatus) {
        viewModelScope.launch { if (runCatching { c.tracker.settle(id, status) }.isFailure) _toasts.tryEmit("Couldn't save") }
    }

    fun deleteBet(id: String) {
        viewModelScope.launch { if (runCatching { c.tracker.delete(id) }.isFailure) _toasts.tryEmit("Couldn't save") }
    }
}

private const val GRADING_CHECK = "Grading check"

/** What Scan, Recheck and Refresh say while scanning is paused ([ScanSettings.paused]). */
internal const val PAUSED_TOAST = "Scanning is paused: tap ▶ Resume to scan again"

internal const val RESUMED_TOAST = "Scanning resumed"

/** What a Pause ▶, a pull to refresh, Scan or Check odds now says while the kill switch is on: they can't lift it. */
internal const val KILLED_TOAST = "Everything is stopped by the STOP button: tap Resume on the red bar to start again"

/** CNO's own reads (the list, its books, its teams) wait: nothing is loaded yet, scanning is paused, or Check odds now holds the focus ([FocusGate]). */
internal fun cnoReadsHeld(s: UiState): Boolean = !s.loaded || s.settings.paused || s.checkingOdds

/** What Scan, Recheck and Refresh say while Check odds now holds the focus ([FocusGate]). */
internal const val CHECKING_TOAST = "Check odds now is running: scanning goes on when it's done"

/** How often, at most, a running scan's newest state reaches the screen ([followThrottled]). */
internal const val SCAN_MIRROR_MS = 350L

/** How often, at most, the API usage count reaches the screen (the meters in Settings). */
internal const val USAGE_MIRROR_MS = 1_000L

/**
 * Hands [runs]' newest value to [onRun] no more often than every [everyMs]. A StateFlow keeps only its latest value while the collector
 * waits, so whatever happened meanwhile arrives as one state and the last one (a scan's end) always gets through.
 */
internal suspend fun <T> followThrottled(runs: Flow<T>, everyMs: Long, onRun: suspend (T) -> Unit) {
    // conflate(): only the newest value waits while [onRun] and the pause run (a StateFlow already works that way).
    runs.conflate().collect { run ->
        onRun(run)
        kotlinx.coroutines.delay(everyMs)
    }
}

/** How long Share scan study waits for the grading of games that ended since the last pass before it writes the file anyway. */
internal const val STUDY_SETTLE_WAIT_MS = 45_000L

/** How often, at most, the Tracker's saved bets reach the screen: a Check odds now saves every few bets (Tj, 2026-10-01: "very laggy"). */
internal const val TRACKER_MIRROR_MS = 300L

/** How often, at most, a Check odds now's "n of N read" reaches the screen. */
internal const val CHECK_PROGRESS_MS = 300L

/**
 * [refresh] for the current [sports] at once and then every [everyMs] while Vigilant is [onScreen]; again at once when either changes (back on
 * screen: never minutes stale); nothing at all off screen, so no timer wakes a phone in a pocket. No sports: [refresh] with none once (drops
 * what was read).
 */
internal suspend fun refreshWhileOnScreen(
    sports: kotlinx.coroutines.flow.Flow<List<String>>,
    onScreen: kotlinx.coroutines.flow.Flow<Boolean>,
    everyMs: Long,
    refresh: suspend (List<String>) -> Unit,
) {
    combine(sports.distinctUntilChanged(), onScreen.distinctUntilChanged()) { s, on -> s to on }.collectLatest { (s, on) ->
        if (s.isEmpty()) {
            refresh(emptyList())
            return@collectLatest
        }
        if (!on) return@collectLatest
        while (true) {
            refresh(s)
            kotlinx.coroutines.delay(everyMs)
        }
    }
}
