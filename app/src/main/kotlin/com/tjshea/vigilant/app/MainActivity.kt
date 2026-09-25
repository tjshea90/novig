package com.tjshea.vigilant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.OpportunitySheet
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.scanner.Opportunity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // No refresh loop: nothing is fetched until Tj taps Scan or pulls to refresh (his rule,
        // 2026-09-25). Nothing runs in the background, and no request goes out on its own.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.toasts.collect { android.widget.Toast.makeText(this@MainActivity, it, android.widget.Toast.LENGTH_SHORT).show() }
            }
        }

        setContent {
            VigilantTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                VigilantRoot(state, vm)
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    EV("+EV", Icons.Filled.Star),
    GAMES("Games", Icons.Filled.DateRange),
    TRACKER("Tracker", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun VigilantRoot(state: UiState, vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Opportunity?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            val count = if (t == Tab.EV) state.feed.size else 0
                            if (count > 0) {
                                BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) { Icon(t.icon, contentDescription = t.label) }
                            } else {
                                Icon(t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()
        androidx.compose.foundation.layout.Box(modifier) {
            when (Tab.entries[tab]) {
                Tab.EV -> FeedScreen(
                    state = state,
                    onScan = vm::scan,
                    onToggleLeague = vm::toggleLeague,
                    onOpenSettings = { tab = Tab.SETTINGS.ordinal },
                    onTrack = vm::trackBet,
                )
                Tab.GAMES -> GamesScreen(state, onOpen = { detail = it }, onToggleLeague = vm::toggleLeague, onScan = vm::scan)
                Tab.TRACKER -> TrackerScreen(state, onSettle = vm::settleBet, onDelete = vm::deleteBet)
                Tab.SETTINGS -> SettingsScreen(
                    state,
                    onUpdate = vm::updateSettings,
                    keys = com.tjshea.vigilant.app.ui.KeyActions(
                        add = vm::addKey,
                        remove = vm::removeKey,
                        moveUp = vm::moveKeyUp,
                        exportTo = vm::exportKeys,
                        importFrom = vm::importKeys,
                    ),
                    onNovigConnect = vm::connectNovig,
                    onNovigTest = vm::testNovig,
                    onNovigDisconnect = vm::disconnectNovig,
                )
            }
        }
    }

    detail?.let { o ->
        val live = state.result?.opportunities?.firstOrNull { it.key == o.key } ?: o
        OpportunitySheet(live, state.settings, onDismiss = { detail = null }, onTrack = { stake -> vm.trackBet(live, stake); detail = null })
    }
}
