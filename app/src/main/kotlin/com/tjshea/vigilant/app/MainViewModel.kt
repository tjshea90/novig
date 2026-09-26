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
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SourceReport
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
)

/**
 * The whole app's state. Network happens in exactly one place, [scan], and only when Tj taps
 * Scan or pulls to refresh (his rule, 2026-09-25): nothing fetches on launch, on a timer, on a
 * tab change, or when a setting changes. Settings changes re-price from what the last scan
 * fetched, so they're instant and free.
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
    }

    /**
     * One scan: Novig's board and prices plus fair odds from every enabled source. It runs in the
     * app's runner under a foreground service, so it keeps going if Tj switches apps.
     */
    fun scan() {
        val current = _state.value
        if (!current.loaded || c.runner.running || current.status.rechecking) return
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
        if (!current.loaded || c.runner.running || current.status.rechecking || marketIds.isEmpty()) return
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

    /** One-shot messages for a toast ("Tracked", save errors). */
    private val _toasts = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: kotlinx.coroutines.flow.SharedFlow<String> = _toasts

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
