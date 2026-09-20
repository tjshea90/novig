package com.tjshea.vigilant.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tjshea.vigilant.app.data.EncryptedApiKeyStore
import com.tjshea.vigilant.data.keys.ApiKeyStore
import com.tjshea.vigilant.data.keys.ApiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProviderKeys(val provider: ApiProvider, val keys: List<String>)

/**
 * Backs the Settings screen: add/view/remove multiple keys per provider (Tj's own request,
 * 2026-09-20 — "Give me options to add multiple keys"). [EncryptedApiKeyStore] is where these
 * actually persist; [com.tjshea.vigilant.app.ScannerViewModel] reads the same store to build its
 * repositories, so a key added here takes effect the next time a scan runs.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore: ApiKeyStore = EncryptedApiKeyStore(application)

    private val _uiState = MutableStateFlow<List<ProviderKeys>>(emptyList())
    val uiState: StateFlow<List<ProviderKeys>> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = ApiProvider.entries.map { ProviderKeys(it, apiKeyStore.getKeys(it)) }
        }
    }

    fun addKey(provider: ApiProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = apiKeyStore.getKeys(provider)
            if (trimmed !in current) {
                apiKeyStore.setKeys(provider, current + trimmed)
            }
            refresh()
        }
    }

    fun removeKey(provider: ApiProvider, key: String) {
        viewModelScope.launch {
            val current = apiKeyStore.getKeys(provider)
            apiKeyStore.setKeys(provider, current - key)
            refresh()
        }
    }
}
