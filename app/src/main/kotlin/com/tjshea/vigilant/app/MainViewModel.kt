package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.signing.NovigConnection
import com.tjshea.vigilant.data.novig.signing.NovigSetup
import com.tjshea.vigilant.data.novig.stream.StreamState
import com.tjshea.vigilant.app.data.KeystoreVault
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.RefreshKind
import com.tjshea.vigilant.data.scanner.RefreshReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext

/** What the status line under the title shows. */
data class ScanStatus(
    val refreshing: Boolean = false,
    val novigAtMs: Long? = null,
    val referenceAtMs: Long? = null,
    val creditsRemaining: Int? = null,
    val errors: List<String> = emptyList(),
    val hasOddsKey: Boolean = false,
    val backoffSeconds: Int? = null,
    val booksFetched: Int = 0,
    val booksNotModified: Int = 0,
)

/** The Novig API key section of Settings, and the stream's health. */
data class NovigUi(
    val connection: NovigConnection? = null,
    val streamEnabled: Boolean = true,
    val stream: StreamState = StreamState.Off,
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
    val bets: List<TrackedBet> = emptyList(),
    val loaded: Boolean = false,
    val novig: NovigUi = NovigUi(),
)

/**
 * The whole app's state. Network only happens in [runLiveLoop] (while the screen is visible,
 * driven by the Activity's lifecycle) and [refreshNow] (pull-to-refresh). Settings changes
 * re-price from what's cached, so they're instant and free.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val STREAM_TICK_SECONDS = 2
        const val STREAM_RETRY_MS = 60_000L
    }

    private val c = (application as VigilantApp).container

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Serializes settings writes so two fast taps can't interleave read-modify-write. */
    private val settingsMutex = Mutex()

    init {
        viewModelScope.launch {
            val settings = c.settingsStore.read()
            val keys = c.keyStore.getKeys(ApiProvider.THE_ODDS_API)
            val bets = c.tracker.all()
            val connection = c.novigConnection.load()
            val streamOn = c.novigConnection.streamEnabled()
            if (connection != null && streamOn) c.useConnection(connection)
            _state.update {
                it.copy(
                    settings = settings, oddsApiKeys = keys, bets = bets, loaded = true,
                    status = it.status.copy(hasOddsKey = keys.isNotEmpty()),
                    novig = it.novig.copy(connection = connection, streamEnabled = streamOn),
                )
            }
        }
        viewModelScope.launch {
            c.tracker.flow.filterNotNull().collect { bets -> _state.update { it.copy(bets = bets) } }
        }
    }

    /**
     * Refreshes Novig every [ScanSettings.novigRefreshSeconds] until cancelled. The Activity runs
     * this inside `repeatOnLifecycle(STARTED)`, so it stops the moment the app leaves the screen:
     * no background polling, no wake locks, nothing running while the phone is in a pocket.
     */
    suspend fun runLiveLoop() {
        while (!_state.value.loaded) delay(50)
        val watch = viewModelScope.launch { watchStream() }
        try {
            c.stream?.connect()
            var failedAt = 0L
            while (currentCoroutineContext().isActive) {
                val report = refresh(RefreshKind.AUTO)
                val stream = c.stream
                val streamState = stream?.state?.value
                // A dropped stream reconnects after a pause, never in a tight loop.
                if (stream != null && streamState is StreamState.Failed) {
                    if (failedAt == 0L) failedAt = System.currentTimeMillis()
                    if (System.currentTimeMillis() - failedAt >= STREAM_RETRY_MS) {
                        failedAt = 0L
                        stream.close()
                        stream.connect()
                    }
                } else {
                    failedAt = 0L
                }
                // With the stream live every book is already in memory, so re-pricing every 2s
                // costs no network. Without it, poll at the user's REST interval.
                val base = if (streamState is StreamState.Live) STREAM_TICK_SECONDS else _state.value.settings.novigRefreshSeconds.coerceAtLeast(5)
                delay(maxOf(base, report?.retryAfterSeconds ?: 0) * 1000L)
            }
        } finally {
            watch.cancel()
            // Off screen: drop the socket. Nothing stays connected in the background.
            c.stream?.close()
        }
    }

    private suspend fun watchStream() {
        while (true) {
            val s = c.stream?.state?.value ?: StreamState.Off
            if (_state.value.novig.stream != s) _state.update { it.copy(novig = it.novig.copy(stream = s)) }
            delay(1_000)
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
                if (_state.value.novig.streamEnabled) c.useConnection(conn)
                _state.update {
                    it.copy(novig = it.novig.copy(connection = conn, busy = false, message = "Connected. Prices now stream live from Novig while the app is open."))
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

    fun setStreamEnabled(on: Boolean) {
        viewModelScope.launch {
            c.novigConnection.setStreamEnabled(on)
            c.useConnection(if (on) _state.value.novig.connection else null)
            if (on) c.stream?.connect()
            _state.update { it.copy(novig = it.novig.copy(streamEnabled = on)) }
        }
    }

    /** Forgets the key on this phone. It stays registered on Novig until revoked in Novig's profile. */
    fun disconnectNovig() {
        viewModelScope.launch {
            val conn = _state.value.novig.connection
            c.useConnection(null)
            c.novigConnection.clear()
            conn?.let { KeystoreVault.delete(it.readAlias) }
            _state.update { it.copy(novig = NovigUi(streamEnabled = it.novig.streamEnabled, message = "Disconnected. Back to Novig's public prices.")) }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh(RefreshKind.FULL) }
    }

    private suspend fun refresh(kind: RefreshKind): RefreshReport? {
        val settings = _state.value.settings
        if (settings.leagues.isEmpty()) return null
        _state.update { it.copy(status = it.status.copy(refreshing = true)) }
        val report = withContext(Dispatchers.Default) {
            c.scanner.refresh(settings, kind, c.referenceFor(_state.value.oddsApiKeys))
        }
        val result = report.result
        _state.update { s ->
            s.copy(
                result = result ?: s.result,
                feed = (result ?: s.result)?.feed(s.settings) ?: emptyList(),
                status = ScanStatus(
                    refreshing = false,
                    novigAtMs = result?.computedAtMs ?: s.status.novigAtMs,
                    referenceAtMs = s.settings.selectedLeagues.mapNotNull { report.referenceAtMs[it.oddsApiSportKey] }.minOrNull(),
                    creditsRemaining = report.creditsRemaining,
                    errors = report.errors,
                    hasOddsKey = report.hasReferenceSource,
                    backoffSeconds = report.retryAfterSeconds,
                    booksFetched = report.booksFetched,
                    booksNotModified = report.booksNotModified,
                ),
            )
        }
        result?.let { c.tracker.observe(it) }
        return report
    }

    fun updateSettings(transform: (ScanSettings) -> ScanSettings) {
        viewModelScope.launch {
            val before = _state.value.settings
            val next = settingsMutex.withLock { c.settingsStore.update(transform) }
            _state.update { it.copy(settings = next, feed = it.result?.feed(next) ?: emptyList()) }
            val needsFetch = next.leagues != before.leagues || next.includeLive != before.includeLive ||
                next.daysAhead != before.daysAhead || next.referenceBooks != before.referenceBooks
            if (needsFetch) {
                refresh(if (next.referenceBooks != before.referenceBooks) RefreshKind.FULL else RefreshKind.AUTO)
            } else {
                val repriced = withContext(Dispatchers.Default) { c.scanner.reprice(next) }
                if (repriced != null) _state.update { it.copy(result = repriced, feed = repriced.feed(next)) }
            }
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

    private fun setOddsApiKeys(keys: List<String>) {
        viewModelScope.launch {
            c.keyStore.setKeys(ApiProvider.THE_ODDS_API, keys)
            _state.update { it.copy(oddsApiKeys = keys, status = it.status.copy(hasOddsKey = keys.isNotEmpty())) }
        }
    }

    fun trackBet(o: Opportunity, stake: Double) {
        viewModelScope.launch { c.tracker.track(o, stake) }
    }

    fun settleBet(id: String, status: BetStatus) {
        viewModelScope.launch { c.tracker.settle(id, status) }
    }

    fun deleteBet(id: String) {
        viewModelScope.launch { c.tracker.delete(id) }
    }
}
