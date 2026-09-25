package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.data.keys.ApiProvider
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
    val creditsRemaining: Int? = null,
    val errors: List<String> = emptyList(),
    val backoffSeconds: Int? = null,
    val booksFetched: Int = 0,
    val booksFromCache: Int = 0,
    val booksViaKey: Int = 0,
    val sources: List<SourceReport> = emptyList(),
    /** Leagues picked since the last scan: nothing to show for them until the next one. */
    val unscanned: Set<String> = emptySet(),
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
    val bets: List<TrackedBet> = emptyList(),
    val loaded: Boolean = false,
    val novig: NovigUi = NovigUi(),
)

/**
 * The whole app's state. Network happens in exactly one place, [scan], and only when Tj taps
 * Scan or pulls to refresh (his rule, 2026-09-25): nothing fetches on launch, on a timer, on a
 * tab change, or when a setting changes. Settings changes re-price from what the last scan
 * fetched, so they're instant and free.
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
        }
        viewModelScope.launch {
            c.tracker.flow.filterNotNull().collect { bets -> _state.update { it.copy(bets = bets) } }
        }
    }

    /** One scan: Novig's board and prices plus fair odds from every enabled source. */
    fun scan() {
        val current = _state.value
        if (!current.loaded || current.status.scanning) return
        if (current.settings.leagues.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(status = it.status.copy(scanning = true, progress = ScanProgress("Starting"))) }
            try {
                val settings = _state.value.settings
                val sources = c.referenceSources(settings, _state.value.oddsApiKeys, _state.value.pinnapiKeys)
                val report = withContext(Dispatchers.Default) {
                    c.scanner.scan(settings, sources) { p -> _state.update { it.copy(status = it.status.copy(progress = p)) } }
                }
                applyReport(report, settings)
            } finally {
                _state.update { it.copy(status = it.status.copy(scanning = false, progress = null)) }
            }
        }
    }

    private suspend fun applyReport(report: ScanReport, settings: ScanSettings) {
        val result = report.result
        _state.update { s ->
            s.copy(
                result = result ?: s.result,
                feed = (result ?: s.result)?.feed(s.settings) ?: emptyList(),
                status = s.status.copy(
                    scannedAtMs = if (result != null) result.computedAtMs else s.status.scannedAtMs,
                    creditsRemaining = report.creditsRemaining ?: s.status.creditsRemaining,
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
        // Disk trouble (full storage) must never break a scan.
        result?.let { runCatching { c.tracker.observe(it) } }
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

    fun addOddsApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed in _state.value.oddsApiKeys) return
        setOddsApiKeys(_state.value.oddsApiKeys + trimmed)
    }

    fun removeOddsApiKey(key: String) = setOddsApiKeys(_state.value.oddsApiKeys - key)

    /** pinnapi allows one free key per person; a new one replaces the old. */
    fun setPinnapiKey(key: String?) {
        val keys = listOfNotNull(key?.trim()?.takeIf { it.isNotEmpty() })
        viewModelScope.launch {
            c.keyStore.setKeys(ApiProvider.PINNAPI, keys)
            _state.update { it.copy(pinnapiKeys = keys) }
        }
    }

    private fun setOddsApiKeys(keys: List<String>) {
        viewModelScope.launch {
            c.keyStore.setKeys(ApiProvider.THE_ODDS_API, keys)
            _state.update { it.copy(oddsApiKeys = keys) }
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
