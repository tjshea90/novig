package com.tjshea.vigilant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tjshea.vigilant.app.ui.OpportunitiesScreen
import com.tjshea.vigilant.app.ui.VigilantTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VigilantTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                OpportunitiesScreen(uiState = uiState, onRescan = viewModel::rescan)
            }
        }
    }
}
