package com.tjshea.vigilant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tjshea.vigilant.app.ui.OpportunitiesScreen
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.VigilantTheme

class MainActivity : ComponentActivity() {

    private val scannerViewModel: ScannerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VigilantTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }

                if (showSettings) {
                    val providerKeys by settingsViewModel.uiState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        providerKeys = providerKeys,
                        onAddKey = settingsViewModel::addKey,
                        onRemoveKey = settingsViewModel::removeKey,
                        onBack = {
                            showSettings = false
                            scannerViewModel.rescan()
                        },
                    )
                } else {
                    val uiState by scannerViewModel.uiState.collectAsStateWithLifecycle()
                    OpportunitiesScreen(
                        uiState = uiState,
                        onRescan = scannerViewModel::rescan,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
