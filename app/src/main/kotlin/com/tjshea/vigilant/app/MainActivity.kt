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
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.tjshea.vigilant.app.ui.FloatingActions
import com.tjshea.vigilant.app.ui.FloatingFeed
import com.tjshea.vigilant.app.ui.GamesScreen
import com.tjshea.vigilant.app.ui.LocalOpenNovig
import com.tjshea.vigilant.app.ui.MiniFeed
import com.tjshea.vigilant.app.ui.OpportunitySheet
import com.tjshea.vigilant.app.ui.SettingsScreen
import com.tjshea.vigilant.app.ui.TrackerScreen
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.app.ui.feedMarketIds
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.scanner.ScannerMode
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
        // The one exception (Tj, 2026-09-26): CrazyNinjaOdds' list stays current while someone is
        // looking at it: the CNO tab (Vigilant started), the picture-in-picture window, or the
        // floating widget with the screen on. Each says so to vm.watchCno; closing them stops it.
        widget = FloatingWidget(this, onWatching = { vm.watchCno("overlay", it) }) { w -> FloatingContent(w) }

        // The mini window (picture-in-picture over Novig): its buttons, its "am I small?" state, and
        // parameters kept in step with the scan so Android can shrink Vigilant on the way out.
        inMiniWindow = isInPictureInPictureMode
        addOnPictureInPictureModeChangedListener { info ->
            inMiniWindow = info.isInPictureInPictureMode
            if (!inMiniWindow) {
                miniPage = 0
                miniBooks = false
                miniBookKey = null
            }
        }
        ContextCompat.registerReceiver(this, miniButtons, IntentFilter(MiniWindow.ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state
                    .map { MiniParams(autoEnter(it) && !useFloating(it), it.status.scanning, cnoOnly(it), it.settings.cnoRefreshSeconds == 0) }
                    .distinctUntilChanged()
                    .collect { updateMiniWindow() }
            }
        }

        setContent {
            VigilantTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                if (inMiniWindow) {
                    // The picture-in-picture window counts as looking at CNO's list while it shows it.
                    if (MiniWindow.showsCno(state.settings)) {
                        LifecycleStartEffect(Unit) {
                            vm.watchCno("pip", true)
                            onStopOrDispose { vm.watchCno("pip", false) }
                        }
                    }
                    MiniFeed(
                        state, miniPage,
                        booksKey = miniBookKey.takeIf { miniBooks },
                        onLoadBooks = { vm.loadBooks(it) },
                        onPage = { miniPage = it },
                    )
                } else {
                    CompositionLocalProvider(LocalOpenNovig provides { openNovig() }) {
                        VigilantRoot(
                            state, vm,
                            onScan = { scan() },
                            onMiniWindow = { showWidget(moveBack = true); Unit }.takeIf { MiniWindow.supported(this) || state.settings.cnoOn },
                            onOpenInNovig = ::openInNovig,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(miniButtons) }
        // Vigilant closed (backed out, or swiped away in Recents): the widget goes with it, and
        // with it every CNO read (Tj, 2026-09-26: "nothing is refreshing in the background").
        widget.hide()
        super.onDestroy()
    }

    /** The floating widget over other apps (null only before onCreate). */
    private lateinit var widget: FloatingWidget

    /** What the picture-in-picture parameters depend on. */
    private data class MiniParams(val autoEnter: Boolean, val scanning: Boolean, val cnoOnly: Boolean, val tapsOnly: Boolean)

    /**
     * Android's "Display over other apps" for Vigilant, read when Vigilant comes to the front
     * (the only time it can have changed: Tj flips it in Android's settings, then comes back).
     */
    private var overlayAllowed = false

    /** The widget is the floating one: CNO on, the setting on, and Android's permission given. */
    private fun useFloating(s: UiState): Boolean =
        s.settings.cnoOn && s.settings.floatingWidget && overlayAllowed

    /** The floating widget's content: the app's state, and what its buttons and rows do. */
    @androidx.compose.runtime.Composable
    private fun FloatingContent(w: FloatingWidget) {
        VigilantTheme {
            val state by vm.state.collectAsStateWithLifecycle()
            FloatingFeed(
                state,
                FloatingActions(
                    onClose = { w.hide() },
                    onMinimize = { w.minimize(); w.save() },
                    onExpand = { w.expand() },
                    onOpenApp = { openApp() },
                    onDrag = { dx, dy -> w.moveBy(dx, dy) },
                    onDragEnd = { w.save() },
                    onResize = { dw, dh -> w.resizeBy(dw, dh) },
                    onRefresh = { vm.refreshCno(quiet = true) },
                    onScan = {
                        vm.scan()
                        vm.refreshCno(quiet = true)
                    },
                    onRecheck = { vm.recheck(feedMarketIds(vm.state.value)) },
                    onOpenBet = { item -> openBet(item) },
                    onPlaced = { item -> vm.markPlaced(item) },
                    onUndoPlaced = { key -> vm.unmarkPlaced(key) },
                    onLoadBooks = { row -> vm.loadBooks(row) },
                ),
                androidx.compose.ui.Modifier.fillMaxSize(),
                minimized = w.minimized,
                opening = w.opening,
            )
        }
    }

    /** Vigilant back in front (the widget's "open" button). */
    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    /**
     * A widget bet in Novig's app, in its bet slip: CNO's link for a CNO bet (looked up once,
     * then cached), Novig's own outcome link for Vigilant's. Novig's home when there's no link.
     */
    private fun openBet(item: MiniWindow.Item) {
        val row = item.cno?.row
        if (row == null) {
            launchNovig(MiniWindow.novigLink(item))
            return
        }
        widget.opening = item.key
        lifecycleScope.launch {
            val link = runCatching { vm.novigLink(row) }.getOrNull()
            if (widget.opening == item.key) widget.opening = null
            launchNovig(link)
        }
    }

    /** Opens [link] in Novig's app (never a browser when the app is installed), else Novig itself. */
    private fun launchNovig(link: String?) {
        val installed = packageManager.getLaunchIntentForPackage(MiniWindow.NOVIG_PACKAGE) != null
        val opened = link != null && runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))
                    .apply { if (installed && link.startsWith("novigapp://")) setPackage(MiniWindow.NOVIG_PACKAGE) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) runCatching { startActivity(MiniWindow.novigIntent(this)) }
    }

    /**
     * Shrinks to the widget: the floating one when it's in use (Vigilant steps back, the widget
     * stays over whatever is under it), else picture-in-picture. False if neither is possible.
     */
    private fun showWidget(moveBack: Boolean): Boolean {
        val s = vm.state.value
        if (s.settings.cnoOn && s.settings.floatingWidget) {
            overlayAllowed = FloatingWidget.allowed(this)
            if (overlayAllowed) {
                if (widget.show()) {
                    if (moveBack) moveTaskToBack(true)
                    return true
                }
            } else if (moveBack) {
                // Asked for the widget by its button: send Tj to the switch, once per tap.
                android.widget.Toast.makeText(this, "Allow Vigilant to display over other apps for the floating widget (or switch it off in Settings for picture-in-picture)", android.widget.Toast.LENGTH_LONG).show()
                runCatching { startActivity(FloatingWidget.permissionIntent(this)) }
                return false
            }
        }
        return enterMiniWindow()
    }

    private var inMiniWindow by mutableStateOf(false)

    /** Taps on the mini window's Next button; each shows the next page of bets. */
    private var miniPage by mutableIntStateOf(0)

    /** The mini window shows one CNO bet's books (its Books button) instead of the list. */
    private var miniBooks by mutableStateOf(false)

    /**
     * The bet the Books view shows, by key, so a refresh that reorders the list doesn't swap it
     * for another; Next moves to the bet below it.
     */
    private var miniBookKey by mutableStateOf<String?>(null)

    /** The mini window's Scan / Recheck / Refresh / Books / Next buttons (PendingIntents back to this app only). */
    private val miniButtons = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(MiniWindow.EXTRA_BUTTON, 0)) {
                MiniWindow.SCAN -> {
                    vm.scan()
                    // Showing both lists: Scan freshens CNO's too (if its 30 s have passed).
                    if (MiniWindow.showsCno(vm.state.value.settings)) vm.refreshCno(quiet = true)
                }
                MiniWindow.RECHECK -> vm.recheck(feedMarketIds(vm.state.value))
                MiniWindow.REFRESH -> {
                    vm.refreshCno()
                    // In the Books view, re-read that bet's books too.
                    if (miniBooks) miniItems().firstOrNull { it.key == miniBookKey }?.cno?.let { vm.loadBooks(it.row, force = true) }
                }
                MiniWindow.BOOKS -> {
                    miniBooks = !miniBooks
                    if (miniBooks) miniBookKey = miniItems().firstOrNull()?.key
                    updateMiniWindow()
                }
                MiniWindow.NEXT -> if (miniBooks) stepBook(+1) else miniPage++
                // CNO alone: Up / Down a page (the list stops at its ends), or a bet in the Books view.
                MiniWindow.UP -> if (miniBooks) stepBook(-1) else miniPage = (miniPage - 1).coerceAtLeast(0)
                MiniWindow.DOWN -> if (miniBooks) stepBook(+1) else miniPage++
            }
        }
    }

    private fun miniItems() = MiniWindow.items(vm.state.value, System.currentTimeMillis())

    /** The Books view moves to the bet [by] rows down (wrapping, as Next always has). */
    private fun stepBook(by: Int) {
        val items = miniItems()
        if (items.isEmpty()) return
        val at = items.indexOfFirst { it.key == miniBookKey }.coerceAtLeast(0)
        miniBookKey = items[(at + by).mod(items.size)].key
    }

    /** Whether leaving Vigilant shrinks it to the mini window: something to watch, and the switch on. */
    private fun autoEnter(s: UiState): Boolean =
        MiniWindow.shouldAutoEnter(s.settings, s.status, MiniWindow.items(s, System.currentTimeMillis()).size)

    /** CNO only: the mini window lists CNO alone, with Refresh, Books and Next. */
    private fun cnoOnly(s: UiState): Boolean = s.settings.scanner == ScannerMode.CNO

    /** The picture-in-picture parameters for now: auto-enter only when the floating widget isn't the one in use. */
    private fun pipParams(auto: Boolean = true): android.app.PictureInPictureParams {
        val s = vm.state.value
        val cno = cnoOnly(s)
        return MiniWindow.params(this, auto && autoEnter(s) && !useFloating(s), s.status.scanning, cno, miniBooks && cno, tapsOnly = s.settings.cnoRefreshSeconds == 0)
    }

    private fun updateMiniWindow() {
        if (!MiniWindow.supported(this)) return
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    /** Shrinks to the mini window now. False if this phone or its settings don't allow it. */
    private fun enterMiniWindow(): Boolean {
        val ok = MiniWindow.supported(this) &&
            runCatching { enterPictureInPictureMode(pipParams()) }
                .getOrDefault(false)
        if (!ok) {
            android.widget.Toast.makeText(this, "Picture-in-picture is off for Vigilant (Android Settings › Apps › Special app access)", android.widget.Toast.LENGTH_LONG).show()
        }
        return ok
    }

    /**
     * Leaving Vigilant (Home, Recents): the floating widget comes up by itself when it's the one
     * in use and there's something to watch; otherwise Android 12+ shrinks to picture-in-picture
     * by itself (auto-enter), and Android 11 needs asking.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val s = vm.state.value
        if (useFloating(s)) {
            if (autoEnter(s)) widget.show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && MiniWindow.supported(this) && autoEnter(s)) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    /**
     * A CNO bet's game in Novig's app (CNO's deeplink resolves to `novigapp://events/<id>`), with
     * Vigilant floating over it when the mini window is on. Falls back to Novig's app or site.
     */
    private fun openInNovig(row: CnoRow) {
        lifecycleScope.launch {
            val link = vm.novigLink(row)
            floatOverNovig()
            launchNovig(link)
        }
    }

    /** Novig's app (or site), with Vigilant floating over it when the mini window is on. */
    private fun openNovig() {
        floatOverNovig()
        runCatching { startActivity(MiniWindow.novigIntent(this)) }
    }

    /** Before opening Novig: the widget over it, when the mini window setting is on. */
    private fun floatOverNovig() {
        val s = vm.state.value
        if (!s.settings.miniWindow) return
        if (useFloating(s)) {
            widget.show()
        } else if (MiniWindow.supported(this)) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as VigilantApp).container.onScreen = true
        // The full app is back in front: no need for the widget over it.
        if (::widget.isInitialized) widget.hide()
        val allowed = FloatingWidget.allowed(this)
        if (allowed != overlayAllowed) {
            overlayAllowed = allowed
            updateMiniWindow()
        }
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
    ;

    /** Whether the tab exists in this scanner mode: CNO only hides what needs Vigilant's scanner. */
    fun shownIn(mode: ScannerMode): Boolean = when (this) {
        EV, GAMES -> mode != ScannerMode.CNO
        CNO -> mode != ScannerMode.VIGILANT
        TRACKER, SETTINGS -> true
    }
}

/** The tab's icon with its bet count. Its own clock, so only the badge ticks, not the whole screen. */
@Composable
private fun TabIconWithCount(t: Tab, state: UiState) {
    val count = when (t) {
        Tab.EV -> state.feed.size
        Tab.CNO -> {
            val now = com.tjshea.vigilant.app.ui.rememberNow(15_000)
            // Placed bets aren't counted: the tab doesn't list them.
            state.cnoShown(now).size
        }
        else -> 0
    }
    if (count > 0) {
        BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) { TabIcon(t) }
    } else {
        TabIcon(t)
    }
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
private fun VigilantRoot(
    state: UiState,
    vm: MainViewModel,
    onScan: () -> Unit,
    onMiniWindow: (() -> Unit)?,
    onOpenInNovig: (CnoRow) -> Unit = {},
) {
    val mode = state.settings.scanner
    val tabs = Tab.entries.filter { it.shownIn(mode) }
    var tabName by rememberSaveable { mutableStateOf<String?>(null) }
    // Until Tj picks a tab: CNO only opens on CNO's list, otherwise on the +EV feed.
    val tab = tabs.firstOrNull { it.name == tabName } ?: if (mode == ScannerMode.CNO) Tab.CNO else tabs.first()
    var detail by remember { mutableStateOf<Opportunity?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tabName = t.name },
                        icon = { TabIconWithCount(t, state) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()
        androidx.compose.foundation.layout.Box(modifier) {
            when (tab) {
                Tab.EV -> FeedScreen(
                    state = state,
                    onScan = onScan,
                    onToggleLeague = vm::toggleLeague,
                    onOpenSettings = { tabName = Tab.SETTINGS.name },
                    onTrack = vm::trackBet,
                    onSort = { sort -> vm.updateSettings { it.copy(feedSort = sort) } },
                    onRecheck = vm::recheck,
                    onMiniWindow = onMiniWindow,
                )
                Tab.CNO -> CnoTab(
                    state, vm,
                    onRefresh = { vm.refreshCno() },
                    onOpenSettings = { tabName = Tab.SETTINGS.name },
                    onMiniWindow = onMiniWindow,
                    onLoadBooks = vm::loadBooks,
                    onOpenInNovig = onOpenInNovig,
                    onScanner = { m -> vm.updateSettings { it.copy(scanner = m) } },
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

/** The CNO tab. CNO's list is read only while this is on screen and Vigilant is started. */
@Composable
private fun CnoTab(
    state: UiState,
    vm: MainViewModel,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onMiniWindow: (() -> Unit)?,
    onLoadBooks: (CnoRow, Boolean) -> Unit,
    onOpenInNovig: (CnoRow) -> Unit,
    onScanner: (ScannerMode) -> Unit,
) {
    LifecycleStartEffect(Unit) {
        vm.watchCno("tab", true)
        onStopOrDispose { vm.watchCno("tab", false) }
    }
    CnoScreen(
        state,
        onRefresh = onRefresh,
        onOpenSettings = onOpenSettings,
        onMiniWindow = onMiniWindow,
        onLoadBooks = onLoadBooks,
        onOpenInNovig = onOpenInNovig,
        onScanner = onScanner,
        onPlaced = vm::markPlaced,
        onUnplace = vm::unmarkPlaced,
    )
}
