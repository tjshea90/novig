package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.data.keys.ApiProvider
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
import kotlin.coroutines.coroutineContext

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

data class UiState(
    val settings: ScanSettings = ScanSettings(),
    val result: ScanResult? = null,
    val feed: List<Opportunity> = emptyList(),
    val status: ScanStatus = ScanStatus(),
    val oddsApiKeys: List<String> = emptyList(),
    val bets: List<TrackedBet> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * The whole app's state. Network only happens in [runLiveLoop] (while the screen is visible,
 * driven by the Activity's lifecycle) and [refreshNow] (pull-to-refresh). Settings changes
 * re-price from what's cached, so they're instant and free.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

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
            _state.update { it.copy(settings = settings, oddsApiKeys = keys, bets = bets, loaded = true, status = it.status.copy(hasOddsKey = keys.isNotEmpty())) }
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
        while (coroutineContext.isActive) {
            val report = refresh(RefreshKind.AUTO)
            val base = _state.value.settings.novigRefreshSeconds.coerceAtLeast(5)
            val wait = maxOf(base, report?.retryAfterSeconds ?: 0)
            delay(wait * 1000L)
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
