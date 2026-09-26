package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSetup
import com.tjshea.vigilant.app.data.KeystoreVault
import com.tjshea.vigilant.data.cno.CnoBooksState
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoConfig
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoScreened
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.cno.CnoWatch
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SourceReport
import com.tjshea.vigilant.data.teams.PlayerTeams
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.PlacedBet
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
    val sources: List<SourceReport> = emptyList(),
    /** Leagues picked since the last scan: nothing to show for them until the next one. */
    val unscanned: Set<String> = emptySet(),
    /** A recheck (a few Novig prices re-read, no fair-odds calls) is running. */
    val rechecking: Boolean = false,
)

/** The Novig API key section of Settings. */
data class NovigUi(
    val connection: NovigConnection? = null,
    val busy: Boolean = false,
    /** Progress or result text for setup/test. */
    val message: String? = null,
    val error: String? = null,
)

data class UiState(
    val settings: ScanSettings = ScanSettings(),
    val result: ScanResult? = null,
    val feed: List<Opportunity> = emptyList(),
    val status: ScanStatus = ScanStatus(),
    val oddsApiKeys: List<String> = emptyList(),
    val pinnapiKeys: List<String> = emptyList(),
    /** Every provider's usage ledger, updated after each call (the meters). */
    val usage: UsageBook = UsageBook(),
    val bets: List<TrackedBet> = emptyList(),
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
    /** CNO is being kept current right now: its tab or a widget is on screen. */
    val cnoLive: Boolean = false,
) {
    /** [placed]'s keys, for hiding them. */
    val placedKeys: Set<String> by lazy { placed.mapTo(HashSet()) { it.key } }

    /** [placed]'s families (the same bet at another line), for the "placed O5.5" tag. */
    val placedFamilies: Map<String, PlacedBet> by lazy { placed.filter { it.family.isNotEmpty() }.associateBy { it.family } }

    /** The CNO view to read: Tj's saved link, or Novig with CNO's defaults. */
    val cnoUrl: String get() = CnoView.normalize(settings.cnoViewUrl) ?: CnoView.DEFAULT

    val cnoConfig: CnoConfig get() = CnoConfig(loaded && settings.cnoOn, cnoUrl, settings.cnoRefreshSeconds, settings.cnoFilters)

    /**
     * CNO's list for the current link, through the app's own checks with the current filters
     * (so a changed filter applies at once, before CNO's next read). Null when CNO is off or
     * nothing was read for this link yet.
     */
    fun cnoPicks(now: Long): CnoScreened? =
        cno.snapshot?.takeIf { settings.cnoOn && it.url == cnoUrl }?.let { CnoChecks.screen(it, settings.cnoFilters, now) }

    /** [cnoPicks] without the bets Tj marked placed: what the CNO tab, its badge and the widgets list. */
    fun cnoShown(now: Long): List<com.tjshea.vigilant.data.cno.CnoPick> =
        cnoPicks(now)?.picks?.filter { MiniWindow.cnoKey(it.row) !in placedKeys }.orEmpty()
}

/**
 * The whole app's state. Network happens in exactly one place, [scan], and only when Tj taps
 * Scan or pulls to refresh (his rule, 2026-09-25): nothing fetches on launch, on a timer, on a
 * tab change, or when a setting changes. Settings changes re-price from what the last scan
 * fetched, so they're instant and free. The one exception is CrazyNinjaOdds' list ([watchCno],
 * Tj 2026-09-26): read while Vigilant or its mini window is on screen, paced by [CnoFeed].
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

    init {
        viewModelScope.launch {
            val stored = c.settingsStore.read()
            val settings = stored.migrate()
            if (settings != stored) runCatching { c.settingsStore.update { settings } }
            runCatching { c.migrateKeys() }
            c.usage.load()
            val keys = c.keyStore.getKeys(ApiProvider.THE_ODDS_API)
            val pinn = c.keyStore.getKeys(ApiProvider.PINNAPI)
            val bets = c.tracker.all()
            val connection = c.novigConnection.load()
            c.useConnection(connection)
            _state.update {
                it.copy(
                    settings = settings, oddsApiKeys = keys, pinnapiKeys = pinn, bets = bets, loaded = true,
                    novig = it.novig.copy(connection = connection),
                )
            }
            // After the settings, so a scan still running (or finished) is shown under them.
            follow()
        }
        viewModelScope.launch {
            c.tracker.flow.filterNotNull().collect { bets -> _state.update { it.copy(bets = bets) } }
        }
        viewModelScope.launch {
            c.usage.flow.collect { u -> _state.update { it.copy(usage = u) } }
        }
        viewModelScope.launch {
            runCatching { c.cno.load() }
            c.cno.state.collect { cno -> _state.update { it.copy(cno = cno) } }
        }
        viewModelScope.launch {
            c.cno.books.collect { b -> _state.update { it.copy(books = b) } }
        }
        viewModelScope.launch {
            runCatching { c.placed.load() }
            c.placed.flow.filterNotNull().collect { b -> _state.update { it.copy(placed = b.bets) } }
        }
        viewModelScope.launch {
            runCatching { c.teams.load() }
            // Recomputed only when the list's rows, the switch or the rosters change.
            combine(c.teams.state, state.map { (if (it.settings.cnoPlayerTeams && it.settings.cnoOn) it.cno.snapshot?.rows else null) }.distinctUntilChanged()) { cache, rows ->
                rows?.mapNotNull { r -> PlayerTeams.playerOf(r)?.let { p -> cache.teamOf(r.league, r.event, p) }?.let { r.key to it } }?.toMap() ?: emptyMap()
            }.flowOn(Dispatchers.Default).collect { t -> _state.update { if (it.teams == t) it else it.copy(teams = t) } }
        }
        // CNO, its books lane and its teams lane run together, only while someone is looking.
        viewModelScope.launch {
            cnoWatch.runWhileWatched(onActive = { active -> _state.update { it.copy(cnoLive = active) } }) {
                launch { c.cno.watch(state.map { it.cnoConfig }) }
                launch { c.cno.keepBooksFresh(agreementRows()) }
                launch { c.teams.keepFresh(teamRows()) }
            }
        }
    }

    /** [MainActivity] and the widget say when they start and stop showing CNO's list. */
    fun watchCno(who: String, on: Boolean) = cnoWatch.set(who, on)

    /** The bets whose books the green check reads: the list's best, placed ones left out. */
    private fun agreementRows(): Flow<List<CnoRow>> = state.map { s ->
        if (!s.settings.cnoCheckBooks) emptyList()
        else s.cnoShown(System.currentTimeMillis()).map { it.row }
    }

    /** The rows whose players' teams are wanted (all of the list's player bets). */
    private fun teamRows(): Flow<List<CnoRow>> = state.map { s ->
        if (!s.settings.cnoPlayerTeams) emptyList() else s.cno.snapshot?.takeIf { it.url == s.cnoUrl }?.rows ?: emptyList()
    }

    /** Marks a widget or CNO-tab bet placed: hidden from then on, through refreshes and restarts. */
    fun markPlaced(item: MiniWindow.Item) {
        viewModelScope.launch {
            if (runCatching { c.placed.mark(MiniWindow.placed(item, System.currentTimeMillis())) }.isFailure) _toasts.tryEmit("Couldn't save that")
        }
    }

    /** Undo, or "not placed after all": the bet shows again. */
    fun unmarkPlaced(key: String) {
        viewModelScope.launch { if (runCatching { c.placed.unmark(key) }.isFailure) _toasts.tryEmit("Couldn't save that") }
    }

    /** Re-reads CNO now (Refresh, pull down, the mini window's button), if 3 s have passed. */
    fun refreshCno(quiet: Boolean = false) {
        val current = _state.value
        if (!current.loaded || !current.settings.cnoOn) return
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
    fun scan() {
        val current = _state.value
        // CNO only: Vigilant's scanner and every API behind it stay asleep.
        if (!current.loaded || !current.settings.vigilantOn || c.runner.running || current.status.rechecking) return
        if (current.settings.leagues.isEmpty()) return
        val settings = current.settings
        val now = System.currentTimeMillis()
        // Open bets' lines are priced even past the per-game cap, so their closing value updates.
        val pinned = current.bets.filter { it.status == BetStatus.PENDING && it.startsTs > now }.mapTo(HashSet()) { it.marketId }
        val started = c.runner.start(settings, c.referenceSources(settings), pinned) { report ->
            // Runs even with this screen gone. Disk trouble (full storage) must never break a scan.
            report?.result?.let { runCatching { c.tracker.observe(it) } }
            // Keyed calls saved as they happened; this saves the keyless request counters.
            runCatching { c.usage.flush() }
        }
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
        viewModelScope.launch {
            _state.update { it.copy(status = it.status.copy(rechecking = true)) }
            val outcome = runCatching { withContext(Dispatchers.IO) { c.scanner.recheck(_state.value.settings, marketIds) } }
            val report = outcome.getOrNull()
            _state.update { s ->
                val r = report?.result ?: s.result
                s.copy(result = r, feed = r?.feed(s.settings) ?: s.feed, status = s.status.copy(rechecking = false))
            }
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

    /** The Novig app link that opens a CNO bet in Novig's bet slip (`novigapp://events/<outcome>/cno`), or null. */
    suspend fun novigLink(row: CnoRow): String? = withContext(Dispatchers.IO) { c.cno.novigLink(row) }

    /** Mirrors the runner into the screen's state, for as long as this screen lives. */
    private suspend fun follow() {
        var seen = c.runner.state.value.finished
        var first = true
        c.runner.state.collect { run ->
            val report = run.report
            val ended = run.finished != seen
            seen = run.finished
            if (!run.scanning && report != null && (ended || first)) {
                // A scan just ended, or this screen opened after one did.
                applyReport(report, run.settings ?: _state.value.settings, run.result)
            } else {
                _state.update { s ->
                    val r = run.result ?: s.result
                    s.copy(
                        result = r,
                        feed = r?.feed(s.settings) ?: s.feed,
                        status = s.status.copy(scanning = run.scanning, progress = run.progress),
                    )
                }
            }
            first = false
        }
    }

    private suspend fun applyReport(report: ScanReport, settings: ScanSettings, result: ScanResult?) {
        _state.update { s ->
            val r = result ?: s.result
            s.copy(
                result = r,
                feed = r?.feed(s.settings) ?: emptyList(),
                status = s.status.copy(
                    scanning = false,
                    progress = null,
                    scannedAtMs = report.result?.computedAtMs ?: s.status.scannedAtMs,
                    errors = report.errors,
                    backoffSeconds = report.retryAfterSeconds,
                    booksFetched = report.booksFetched + report.booksNotModified,
                    booksFromCache = report.booksFromCache,
                    booksViaKey = report.booksViaKey,
                    sources = report.sources,
                    unscanned = emptySet(),
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
        _state.update {
            val r = repriced ?: it.result
            it.copy(result = r, feed = r?.feed(it.settings) ?: emptyList(), status = it.status.copy(unscanned = unscanned))
        }
    }

    fun connectNovig(managementKeyId: String, managementPem: String) {
        if (_state.value.novig.busy) return
        viewModelScope.launch {
            _state.update { it.copy(novig = it.novig.copy(busy = true, error = null, message = "Starting…")) }
            try {
                val setup = NovigSetup(c.http, c.json, KeystoreVault)
                val conn = withContext(Dispatchers.IO) {
                    setup.connect(managementKeyId, managementPem) { step ->
                        _state.update { it.copy(novig = it.novig.copy(message = step)) }
                    }
                }
                // Replace any earlier read key: its Keystore entry is no longer needed.
                _state.value.novig.connection?.let { old -> if (old.readAlias != conn.readAlias) KeystoreVault.delete(old.readAlias) }
                c.novigConnection.save(conn)
                c.useConnection(conn)
                _state.update {
                    it.copy(novig = it.novig.copy(connection = conn, busy = false, message = "Connected. Scans now read Novig prices through your key's own rate limit."))
                }
            } catch (e: NovigApiException) {
                _state.update { it.copy(novig = it.novig.copy(busy = false, message = null, error = e.advice)) }
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
            val result = runCatching { withContext(Dispatchers.IO) { c.readKeyClient(conn).echo() } }
            _state.update {
                it.copy(
                    novig = it.novig.copy(
                        busy = false,
                        message = if (result.isSuccess) "Novig accepted the key (signature, clock and network all OK)." else null,
                        error = result.exceptionOrNull()?.let { e -> (e as? NovigApiException)?.advice ?: e.message },
                    ),
                )
            }
        }
    }

    /** Forgets the key on this phone. It stays registered on Novig until revoked in Novig's profile. */
    fun disconnectNovig() {
        viewModelScope.launch {
            val conn = _state.value.novig.connection
            c.useConnection(null)
            c.novigConnection.clear()
            conn?.let { KeystoreVault.delete(it.readAlias) }
            _state.update { it.copy(novig = NovigUi(message = "Disconnected. Back to Novig's public prices.")) }
        }
    }

    /** Saves a settings change and re-prices from the last scan. Never touches the network. */
    fun updateSettings(transform: (ScanSettings) -> ScanSettings) {
        viewModelScope.launch {
            val next = settingsMutex.withLock {
                // If the write fails, keep the change for this session rather than crash.
                runCatching { c.settingsStore.update(transform) }.getOrElse { transform(_state.value.settings) }
            }
            _state.update {
                if (next.leagues.isEmpty()) it.copy(settings = next, result = null, feed = emptyList())
                else it.copy(settings = next, feed = it.result?.feed(next) ?: emptyList())
            }
            if (next.leagues.isNotEmpty()) repriceNow(next)
        }
    }

    fun toggleLeague(league: String) = updateSettings { s ->
        s.copy(leagues = if (league in s.leagues) s.leagues - league else s.leagues + league)
    }

    fun keysFor(provider: ApiProvider): List<String> = when (provider) {
        ApiProvider.THE_ODDS_API -> _state.value.oddsApiKeys
        ApiProvider.PINNAPI -> _state.value.pinnapiKeys
    }

    /** Adds a key at the end of the rotation (keys are tried in order: key 1 first). */
    fun addKey(provider: ApiProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed in keysFor(provider)) return
        setKeys(provider, keysFor(provider) + trimmed)
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
            _state.update {
                when (provider) {
                    ApiProvider.THE_ODDS_API -> it.copy(oddsApiKeys = clean)
                    ApiProvider.PINNAPI -> it.copy(pinnapiKeys = clean)
                }
            }
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
            val odds = c.keyStore.getKeys(ApiProvider.THE_ODDS_API)
            val pinn = c.keyStore.getKeys(ApiProvider.PINNAPI)
            _state.update { it.copy(oddsApiKeys = odds, pinnapiKeys = pinn) }
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
            val ok = runCatching { c.tracker.track(o, stake) }.getOrNull() != null
            _toasts.tryEmit(if (ok) "Tracked ${o.selection} · ${com.tjshea.vigilant.app.ui.Format.money(stake)}" else "Couldn't save the bet")
        }
    }

    fun settleBet(id: String, status: BetStatus) {
        viewModelScope.launch { if (runCatching { c.tracker.settle(id, status) }.isFailure) _toasts.tryEmit("Couldn't save") }
    }

    fun deleteBet(id: String) {
        viewModelScope.launch { if (runCatching { c.tracker.delete(id) }.isFailure) _toasts.tryEmit("Couldn't save") }
    }
}
