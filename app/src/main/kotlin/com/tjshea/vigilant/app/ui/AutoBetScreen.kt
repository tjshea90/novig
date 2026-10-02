package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The Auto-bet tab (Tj, 2026-10-02 ~17:55Z: "maybe make the auto bet feature its own section instead of buried in the settings"): everything about the
 * auto-bet in one place, in the order it's used ([AutoBetSection]), with the presets under its switch since they mostly set its rules. Shown for Novig
 * while CrazyNinjaOdds is on (auto-bet bets CNO's list). [onOpenSettings] opens a Settings page (Betting, to connect the key and wallet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBetScreen(
    state: UiState,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    onOpenSettings: (SettingsPage) -> Unit = {},
    notificationsBlocked: String? = null,
    onTestNotification: () -> Boolean = { true },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-bet", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .testTag("autoBetScreen"),
        ) {
            AutoBetSection(
                state,
                notificationsBlocked = notificationsBlocked,
                onTestNotification = onTestNotification,
                onOpenSettings = onOpenSettings,
                presets = { Column { PresetsTab(state.settings, onUpdate) } },
                onUpdate = onUpdate,
            )
        }
    }
}
