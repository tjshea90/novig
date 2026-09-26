package com.tjshea.vigilant.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tjshea.vigilant.app.ui.CnoScreen
import com.tjshea.vigilant.app.ui.FeedScreen
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.LocalOpenNovig
import com.tjshea.vigilant.app.ui.MiniFeed
import com.tjshea.vigilant.app.ui.OpportunitySheet
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.feedMarketIds
import com.tjshea.vigilant.data.scanner.MiniSource
import com.tjshea.vigilant.data.scanner.Opportunity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /** The system's "allow notifications?" prompt; the answer only affects the scan notifications. */
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // No refresh loop for Novig or the odds providers: nothing is fetched until Tj taps Scan
        // or pulls to refresh (his rule, 2026-09-25). A scan he starts keeps running if he
        // switches apps (ScanService), then everything stops.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.toasts.collect { android.widget.Toast.makeText(this@MainActivity, it, android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
        // The one exception (Tj, 2026-09-26): CrazyNinjaOdds' list stays current while Vigilant is
        // started, which includes the mini window over Novig (a paused, still-visible activity).
        // Leaving stops it; CnoFeed paces it to one read per 30 s at most.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.watchCno() }
        }

        // The mini window (picture-in-picture over Novig): its buttons, its "am I small?" state, and
        // parameters kept in step with the scan so Android can shrink Vigilant on the way out.
        inMiniWindow = isInPictureInPictureMode
        addOnPictureInPictureModeChangedListener { info ->
            inMiniWindow = info.isInPictureInPictureMode
            if (!inMiniWindow) miniPage = 0
        }
        ContextCompat.registerReceiver(this, miniButtons, IntentFilter(MiniWindow.ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state
                    .map { Triple(autoEnter(it), it.status.scanning, cnoOnly(it)) }
                    .distinctUntilChanged()
                    .collect { (auto, scanning, cnoOnly) -> updateMiniWindow(auto, scanning, cnoOnly) }
            }
        }

        setContent {
            VigilantTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                if (inMiniWindow) {
                    MiniFeed(state, miniPage)
                } else {
                    CompositionLocalProvider(LocalOpenNovig provides { openNovig() }) {
                        VigilantRoot(state, vm, onScan = { scan() }, onMiniWindow = { enterMiniWindow(); Unit }.takeIf { MiniWindow.supported(this) })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(miniButtons) }
        super.onDestroy()
    }

    private var inMiniWindow by mutableStateOf(false)

    /** Taps on the mini window's Next button; each shows the next page of bets. */
    private var miniPage by mutableIntStateOf(0)

    /** The mini window's Scan / Recheck / Refresh / Next buttons (PendingIntents back to this app only). */
    private val miniButtons = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(MiniWindow.EXTRA_BUTTON, 0)) {
                MiniWindow.SCAN -> {
                    vm.scan()
                    // Showing both lists: Scan freshens CNO's too (if its 30 s have passed).
                    if (MiniWindow.showsCno(vm.state.value.settings)) vm.refreshCno(quiet = true)
                }
                MiniWindow.RECHECK -> vm.recheck(feedMarketIds(vm.state.value))
                MiniWindow.REFRESH -> vm.refreshCno()
                MiniWindow.NEXT -> miniPage++
            }
        }
    }

    /** Whether leaving Vigilant shrinks it to the mini window: something to watch, and the switch on. */
    private fun autoEnter(s: UiState): Boolean =
        MiniWindow.shouldAutoEnter(s.settings, s.status, MiniWindow.items(s, System.currentTimeMillis()).size)

    /** The mini window lists CNO alone, so its buttons are Refresh and Next. */
    private fun cnoOnly(s: UiState): Boolean = s.settings.cnoEnabled && s.settings.miniSource == MiniSource.CNO

    private fun updateMiniWindow(autoEnter: Boolean, scanning: Boolean, cnoOnly: Boolean) {
        if (!MiniWindow.supported(this)) return
        runCatching { setPictureInPictureParams(MiniWindow.params(this, autoEnter, scanning, cnoOnly)) }
    }

    /** Shrinks to the mini window now. False if this phone or its settings don't allow it. */
    private fun enterMiniWindow(): Boolean {
        val s = vm.state.value
        val ok = MiniWindow.supported(this) &&
            runCatching { enterPictureInPictureMode(MiniWindow.params(this, autoEnter(s), s.status.scanning, cnoOnly(s))) }
                .getOrDefault(false)
        if (!ok) {
            android.widget.Toast.makeText(this, "Picture-in-picture is off for Vigilant (Android Settings › Apps › Special app access)", android.widget.Toast.LENGTH_LONG).show()
        }
        return ok
    }

    /** Android 12+ shrinks to the mini window by itself (auto-enter); Android 11 needs asking. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val s = vm.state.value
            if (MiniWindow.supported(this) && autoEnter(s)) {
                runCatching { enterPictureInPictureMode(MiniWindow.params(this, true, s.status.scanning, cnoOnly(s))) }
            }
        }
    }

    /** Novig's app (or site), with Vigilant floating over it when the mini window is on. */
    private fun openNovig() {
        val s = vm.state.value
        if (s.settings.miniWindow && MiniWindow.supported(this)) {
            runCatching { enterPictureInPictureMode(MiniWindow.params(this, true, s.status.scanning, cnoOnly(s))) }
        }
        runCatching { startActivity(MiniWindow.novigIntent(this)) }
    }

    override fun onStart() {
        super.onStart()
        (application as VigilantApp).container.onScreen = true
    }

    override fun onStop() {
        (application as VigilantApp).container.onScreen = false
        super.onStop()
    }

    /**
     * Scan, and the first time, ask to allow notifications (Android 13+): the progress while Tj is
     * in another app and the "scan done" note need it. The scan runs either way.
     */
    private fun scan() {
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (!ScanService.canNotify(this) && !prefs.getBoolean(ASKED_NOTIFICATIONS, false)) {
            prefs.edit().putBoolean(ASKED_NOTIFICATIONS, true).apply()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.scan()
    }

    private companion object {
        const val ASKED_NOTIFICATIONS = "asked_notifications"
    }
}

private enum class Tab(val label: String, val icon: ImageVector? = null, val drawable: Int? = null) {
    EV("+EV", Icons.Filled.Star),
    CNO("CNO", drawable = R.drawable.ic_cno),
    GAMES("Games", Icons.Filled.DateRange),
    TRACKER("Tracker", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun TabIcon(t: Tab) {
    val description = if (t == Tab.CNO) "CrazyNinjaOdds" else t.label
    when {
        t.icon != null -> Icon(t.icon, contentDescription = description)
        t.drawable != null -> Icon(painterResource(t.drawable), contentDescription = description)
    }
}

@Composable
private fun VigilantRoot(state: UiState, vm: MainViewModel, onScan: () -> Unit, onMiniWindow: (() -> Unit)?) {
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
                            val count = when (t) {
                                Tab.EV -> state.feed.size
                                Tab.CNO -> if (state.settings.cnoEnabled) state.cno.snapshot?.takeIf { it.url == state.cnoUrl }?.rows?.size ?: 0 else 0
                                else -> 0
                            }
                            if (count > 0) {
                                BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) { TabIcon(t) }
                            } else {
                                TabIcon(t)
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
                    onScan = onScan,
                    onToggleLeague = vm::toggleLeague,
                    onOpenSettings = { tab = Tab.SETTINGS.ordinal },
                    onTrack = vm::trackBet,
                    onSort = { sort -> vm.updateSettings { it.copy(feedSort = sort) } },
                    onRecheck = vm::recheck,
                    onMiniWindow = onMiniWindow,
                )
                Tab.CNO -> CnoScreen(
                    state,
                    onRefresh = { vm.refreshCno() },
                    onOpenSettings = { tab = Tab.SETTINGS.ordinal },
                    onMiniWindow = onMiniWindow,
                )
                Tab.GAMES -> GamesScreen(state, onOpen = { detail = it }, onToggleLeague = vm::toggleLeague, onScan = onScan)
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
        OpportunitySheet(
            live, state.settings,
            onDismiss = { detail = null },
            onTrack = { stake -> vm.trackBet(live, stake); detail = null },
            onRecheck = { vm.recheck(listOf(live.market.marketId)) }.takeIf { !state.status.scanning },
            rechecking = state.status.rechecking,
        )
    }
}
