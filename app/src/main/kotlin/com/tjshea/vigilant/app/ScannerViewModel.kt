package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.KeyRotator
import com.tjshea.vigilant.data.novig.NovigGraphQlClient
import com.tjshea.vigilant.data.novig.NovigLeagues
import com.tjshea.vigilant.data.novig.NovigRepository
import com.tjshea.vigilant.data.novig.SampleNovigRepository
import com.tjshea.vigilant.data.reference.ReferenceOddsRepository
import com.tjshea.vigilant.data.reference.SampleReferenceOddsRepository
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.EvScanner
import com.tjshea.vigilant.data.scanner.Sport
import com.tjshea.vigilant.data.scanner.SportsCatalog
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvOpportunity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

sealed interface ScanUiState {
    /** Nothing has been scanned yet this app session — no sport picked, or picked but not yet run. */
    data object Idle : ScanUiState
    data object Loading : ScanUiState
    data class Loaded(val opportunities: List<EvOpportunity>, val novigIsLive: Boolean, val referenceIsLive: Boolean) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

/**
 * Builds its repositories fresh on every scan, from whatever keys are currently stored (Tj's own
 * request, 2026-09-20: type keys into the app, multiple per provider, automatic switching —
 * [KeyRotator] is the switching, this class is what turns stored keys into a live [EvScanner]).
 * A provider with no keys yet falls back to sample data for *that leg only* — [ScanUiState.Loaded]
 * carries live/sample status per leg so the UI never claims a leg is live when it isn't.
 *
 * No scan runs on app start, and none runs just because a sport gets selected — Tj's explicit
 * instruction, 2026-09-20: "do not load any odds at all for any sport until I select the sport or
 * sports and press refresh or pull down to refresh gesture." [toggleSport] only ever updates
 * [selectedSports]; [rescan] is the one path that actually calls a repository, and it's a no-op
 * (stays [ScanUiState.Idle]) until at least one sport is selected. Both the refresh button and the
 * pull-to-refresh gesture in [com.tjshea.vigilant.app.ui.OpportunitiesScreen] call [rescan].
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore: ApiKeyStore = EncryptedApiKeyStore(application)
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _selectedSports = MutableStateFlow<Set<Sport>>(emptySet())
    val selectedSports: StateFlow<Set<Sport>> = _selectedSports.asStateFlow()

    val availableSports: List<Sport> = SportsCatalog.ALL

    fun toggleSport(sport: Sport) {
        _selectedSports.update { current -> if (sport in current) current - sport else current + sport }
    }

    fun rescan() {
        val sports = _selectedSports.value
        if (sports.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading
            _uiState.value = try {
                val proxies = apiKeyStore.getKeys(ApiProvider.NOVIG_PROXY)
                val directModeEnabled = apiKeyStore.isNovigDirectModeEnabled()
                val oddsApiKeys = apiKeyStore.getKeys(ApiProvider.THE_ODDS_API)

                // Novig leagues are derived from whichever selected sports have a known mapping
                // (NovigLeagues — RESEARCH.md §4.4); a selected sport with no mapping just
                // contributes nothing to the Novig leg, same "unmapped -> skip" pattern EvScanner
                // already uses for market types.
                val leagues = sports.mapNotNull { NovigLeagues.forSportKey(it.key) }
                // Live either with configured proxies, or with the explicit "try it without a
                // proxy" opt-in (Tj's own request, 2026-09-22T05:38:31Z) — either way an explicit
                // choice, never a silent default; NovigGraphQlClient itself handles an empty
                // proxy list by connecting directly instead of refusing to run.
                val novigIsLive = (proxies.isNotEmpty() || directModeEnabled) && leagues.isNotEmpty()
                val novigRepository: NovigRepository = if (novigIsLive) {
                    NovigGraphQlClient(leagues, proxies, json)
                } else {
                    SampleNovigRepository()
                }
                val referenceRepository: ReferenceOddsRepository = if (oddsApiKeys.isNotEmpty()) {
                    TheOddsApiClient(httpClient, KeyRotator(oddsApiKeys), json)
                } else {
                    SampleReferenceOddsRepository()
                }

                val scanner = EvScanner(
                    novigRepository = novigRepository,
                    referenceOddsRepository = referenceRepository,
                    sportKeys = sports.map { it.key },
                    devigMethod = DevigMethod.MULTIPLICATIVE,
                )

                ScanUiState.Loaded(
                    opportunities = scanner.scan(),
                    novigIsLive = novigIsLive,
                    referenceIsLive = oddsApiKeys.isNotEmpty(),
                )
            } catch (e: Exception) {
                ScanUiState.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }
}
