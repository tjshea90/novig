package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.keys.KeyRotator
import com.tjshea.vigilant.data.novig.NovigRepository
import com.tjshea.vigilant.data.novig.SampleNovigRepository
import com.tjshea.vigilant.data.novig.SharpApiClient
import com.tjshea.vigilant.data.reference.ReferenceOddsRepository
import com.tjshea.vigilant.data.reference.SampleReferenceOddsRepository
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.EvScanner
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvOpportunity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

sealed interface ScanUiState {
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
 * Sport is hardcoded to NFL for this pass — The Odds API charges credits per sport queried
 * (RESEARCH.md §4.3's free tier is ~16 calls/day total), so scanning every sport by default would
 * burn through it fast. A sport picker is a reasonable follow-up, not solved here.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore: ApiKeyStore = EncryptedApiKeyStore(application)
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Loading)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading
            _uiState.value = try {
                val sharpKeys = apiKeyStore.getKeys(ApiProvider.SHARP_API)
                val oddsApiKeys = apiKeyStore.getKeys(ApiProvider.THE_ODDS_API)

                val novigRepository: NovigRepository = if (sharpKeys.isNotEmpty()) {
                    SharpApiClient(httpClient, KeyRotator(sharpKeys), json)
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
                    sportKey = DEFAULT_SPORT_KEY,
                    devigMethod = DevigMethod.MULTIPLICATIVE,
                )

                ScanUiState.Loaded(
                    opportunities = scanner.scan(),
                    novigIsLive = sharpKeys.isNotEmpty(),
                    referenceIsLive = oddsApiKeys.isNotEmpty(),
                )
            } catch (e: Exception) {
                ScanUiState.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }

    private companion object {
        const val DEFAULT_SPORT_KEY = "americanfootball_nfl"
    }
}
