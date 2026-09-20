package com.tjshea.vigilant.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.data.novig.NovigRepository
import com.tjshea.vigilant.data.novig.SampleNovigRepository
import com.tjshea.vigilant.data.reference.ReferenceOddsRepository
import com.tjshea.vigilant.data.reference.SampleReferenceOddsRepository
import com.tjshea.vigilant.data.scanner.EvScanner
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvOpportunity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanUiState {
    data object Loading : ScanUiState
    data class Loaded(val opportunities: List<EvOpportunity>, val isLiveData: Boolean) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

/**
 * Defaults to the sample repositories — real credentials for either leg (RESEARCH.md §4.1/§4.3)
 * plug in here once Tj has them (see the top-level status message and BRIEF.md for what that
 * needs). [isLiveData] exists specifically so the UI never presents sample data as if it were a
 * real scan (see [ScannerViewModel]'s doc comment for why that matters).
 */
class ScannerViewModel(
    private val novigRepository: NovigRepository = SampleNovigRepository(),
    private val referenceOddsRepository: ReferenceOddsRepository = SampleReferenceOddsRepository(),
    private val sportKey: String = "sample",
    private val devigMethod: DevigMethod = DevigMethod.MULTIPLICATIVE,
    private val isLiveData: Boolean = novigRepository !is SampleNovigRepository,
) : ViewModel() {

    private val scanner = EvScanner(novigRepository, referenceOddsRepository, sportKey, devigMethod)

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Loading)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading
            _uiState.value = try {
                ScanUiState.Loaded(scanner.scan(), isLiveData)
            } catch (e: Exception) {
                ScanUiState.Error(e.message ?: e::class.java.simpleName)
            }
        }
    }
}
