package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.LiveMaster
import com.tjshea.vigilant.app.MainViewModel
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.livebid.LiveBid
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The Live tab (Tj, 2026-10-14: "make a live tab with all of the features and well organized and easy to understand"). Top to bottom: the one switch for every live feature and socket; what is running now;
 * Live light (the lane that finds obviously wrong live prices: its switches, real money, limits, the games it watches, the bids it has up); live games in the feed; the sockets; and, last and fenced off,
 * the engines that need a Pinnodds socket. No Pinnodds is needed for anything above that section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    state: UiState,
    light: MainViewModel.LightUi,
    now: Long,
    onUpdate: ((ScanSettings) -> ScanSettings) -> Unit,
    onShown: () -> Unit,
    onOpenPinnodds: () -> Unit,
    onOpenLiveBids: () -> Unit,
) {
    val s = state.settings
    val shown by rememberUpdatedState(onShown)
    // The status lines under the switches ([UiState.tailTrade], [UiState.labStatus]) are asked for every second or two while this tab shows.
    LaunchedEffect(Unit) { while (true) { shown(); kotlinx.coroutines.delay(1_500) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp).testTag("liveScreen"),
        ) {
            LiveMasterRow(s, onUpdate)
            val on = LiveMaster.isOn(s)

            SectionTitle("What is running")
            LiveNote(summary(state, light, s, on))

            SectionTitle("Live light")
            LiveNote("Finds live prices that are obviously wrong (a team up 25 offered near 50/50, an alternate line out of line with its own ladder) from the score, the clock and Novig's live feed, and bets them at once. It needs only your Novig key and ESPN: no Pinnodds.")
            LiveSwitch(
                "Run it on GitHub", "GitHub's engine runs Live light, the tail bets and their bids (paper, or real when the engine's trading is LIVE and you buy with real money below); this phone only holds the switches and shows what it does. If the engine goes quiet, the phone takes the lane back.",
                s.tailLive.engine, "liveEngineSwitch",
            ) { v -> onUpdate { it.copy(tailLive = it.tailLive.copy(engine = v)) } }
            state.cloudUi.status?.takeIf { s.engineLive && (it.liveNote.isNotBlank() || it.liveStopped.isNotBlank()) }?.let { st ->
                LiveNote("GitHub's live lane" + (if (st.liveLive(now)) " is running: " else " last said: ") + st.liveNote + (st.liveStopped.takeIf { it.isNotBlank() }?.let { "\nStopped: $it" } ?: ""))
            }
            TailLiveBlock(state, onUpdate)
            LightStatus(s, light, now)

            SectionTitle("Live games in the feed")
            LiveSwitch(
                "Include live games", "Lists games already under way (with Novig's live fee priced in) in the +EV feed. Off = pregame only.",
                s.includeLive, "liveIncludeSwitch",
            ) { v -> onUpdate { it.copy(includeLive = v) } }

            SectionTitle("Sockets")
            LiveNote("A socket keeps a connection open for real-time prices. Off with the API on = the app asks by request instead (slower).")
            LiveSwitch("Novig market feed", "Novig's own live prices: what Live light, the scan and the bids read.", s.gates.novigSocket, "liveSocketNovig") { v -> onUpdate { it.copy(gates = it.gates.copy(novigSocket = v)) } }
            LiveSwitch("BoltOdds", "Soft-book odds pushed live: Live light's second opinion on a decided game.", s.gates.boltSocket, "liveSocketBolt") { v -> onUpdate { it.copy(gates = it.gates.copy(boltSocket = v)) } }
            LiveSwitch("Kalshi", "The exchange's real-time prices (needs your Kalshi API key).", s.gates.kalshiSocket, "liveSocketKalshi") { v -> onUpdate { it.copy(gates = it.gates.copy(kalshiSocket = v)) } }
            LiveSwitch("Pinnodds", "Pinnacle's live push feed. Needs a Pinnodds subscription; leave it off if you have none.", s.gates.pinnSocket, "liveSocketPinn") { v -> onUpdate { it.copy(gates = it.gates.copy(pinnSocket = v)) } }

            SectionTitle("Live taker and live bids")
            LiveNote("With the GitHub engine on and trading (paper or live), \"Run it on GitHub\" above also runs these there, priced from BoltOdds' soft-book average (no Pinnodds needed); real orders on that average stay off until you allow them on their pages in Settings.")
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenPinnodds, modifier = Modifier.testTag("liveOpenPinnodds")) { Text("Pinnodds live") }
                OutlinedButton(onClick = onOpenLiveBids, modifier = Modifier.testTag("liveOpenLiveBids")) { Text("Pinnodds live bids") }
            }
        }
    }
}

/** What is running now, in one or two lines. */
internal fun summary(state: UiState, light: MainViewModel.LightUi, s: ScanSettings, masterOn: Boolean): String {
    if (!masterOn) return "Everything live is OFF (the switch above). Nothing live is read or sent."
    val l = light.status
    val parts = ArrayList<String>()
    parts += if (s.tailLive.on) {
        "Live light ${if (s.tailLive.bet) "REAL money" else "paper"}" + (if (l.running) ": ${l.games} games watched, ${l.offers} bets offered" else ": waiting for the Novig live feed or a live game") + (if (s.tailLive.bids) ", bids on" else "")
    } else "Live light off"
    parts += if (s.includeLive) "live games in the feed" else "feed is pregame only"
    val sockets = listOf(s.gates.novigSocket, s.gates.boltSocket, s.gates.kalshiSocket, s.gates.pinnSocket).count { it }
    parts += "$sockets of 4 sockets on"
    return parts.joinToString(" · ")
}

/** Live light's games and bids (what the Bids tab showed before): the lane's counts, one line per game, the bids up, the last that ended. */
@Composable
internal fun LightStatus(s: ScanSettings, light: MainViewModel.LightUi, now: Long) {
    val t = s.tailLive
    val l = light.status
    if (!(t.on && t.light)) return
    Text("What it is doing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    if (!l.running) {
        LiveNote("Starting: it needs the Novig key's live feed and a live game on a picked league.")
        return
    }
    LiveNote("${l.games} live games watched (${l.withScore} with a score) · ${l.pushes} pushes from Novig · ${l.scans} judgments (the last ${l.lastScanTookMs} ms after its push) · ${l.offers} bets offered to the bettor" + (if (t.bids) " · ${l.bidWants} bids asked for" else ""))
    l.gameLines.values.forEach { LiveNote(it) }
    if (!t.bids) return
    val up = light.bids.filter { it.active }
    val ended = light.bids.filter { !it.active }.takeLast(5).reversed()
    Text("Live light bids up (${up.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp).testTag("lightBidsTitle"))
    if (up.isEmpty()) LiveNote("None up right now: a bid goes up only on a side the score and clock call at least 85% decided, 7% under its fair and under what Novig offers. Most live sides are not that clear.")
    up.forEach { LightBidRow(it, now) }
    if (ended.isNotEmpty()) {
        Text("Last ended", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        ended.forEach { LightBidRow(it, now) }
    }
}

@Composable
private fun LightBidRow(b: LiveBid, now: Long) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Text(b.selection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${b.marketLabel} · ${b.eventName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${if (b.real) "REAL" else "PAPER"} · bid ${"%.1f".format(java.util.Locale.US, b.price * 100)}¢ · model ${"%.1f".format(java.util.Locale.US, b.fair * 100)}% · EV ${"%+.1f".format(java.util.Locale.US, b.ev * 100)}% · ${b.contracts} contracts",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                b.status.label + (if (b.filled > 0) " · ${b.filled} filled" else "") + " · ${Format.age(b.postedAtMs, now)}" + (b.why?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun LiveSwitch(title: String, sub: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
