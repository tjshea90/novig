package com.tjshea.vigilant.app.ui.kalshi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.kalshi.KalshiHub
import com.tjshea.vigilant.app.kalshi.KalshiUi
import com.tjshea.vigilant.app.ui.EmptyState
import com.tjshea.vigilant.app.ui.Format
import com.tjshea.vigilant.app.ui.SectionTitle
import com.tjshea.vigilant.data.kalshitrade.ArbOpportunity
import com.tjshea.vigilant.data.kalshitrade.KalshiAutoMode
import com.tjshea.vigilant.data.scanner.AutoBetStake
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import java.util.Locale

// ---- Arbitrage ------------------------------------------------------------------------------------------------

@Composable
internal fun ArbPage(hub: KalshiHub, ui: KalshiUi) {
    var confirm by remember { mutableStateOf<ArbOpportunity?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ui.arbNote ?: "Compares Novig's prices with Kalshi's for the same line.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("arbNote"))
            OutlinedButton(onClick = hub::findArbs, enabled = !ui.arbBusy) { Text("Find") }
        }
        if (ui.arbs.isEmpty()) {
            EmptyState(
                "No sure profit right now",
                "A sure profit is a bet on one side at Novig and the opposite side at Kalshi that together cost under \$1 after both fees. Scan both venues; they appear when prices differ enough.",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("arbList")) {
                items(ui.arbs, key = { it.key }) { a -> ArbCard(a) { confirm = a } }
            }
        }
    }
    confirm?.let { a ->
        val payout = minOf(ui.config.arbMaxPayout, a.maxPayout).let { Math.floor(it) }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Place both sides?") },
            text = {
                Text(
                    "Buys ${a.novig.selection} at Novig and ${a.kalshi.selection} at Kalshi, up to ${Format.money(payout)} of payout (at most ${Format.money(KalshiHubClip)} at a time), " +
                        "about ${Format.money(payout * a.cost)} in all, locking in about ${Format.money(payout * a.profitPerDollar)}. Both prices are read again first; if the pair no longer pays, nothing is bought. " +
                        "Novig goes first; if Kalshi then cannot fill, the Novig side stays open and the app tells you.",
                )
            },
            confirmButton = { TextButton(onClick = { hub.placeArb(a); confirm = null }, enabled = ui.config.tradingEnabled) { Text(if (ui.config.tradingEnabled) "Place both" else "Orders are off") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private const val KalshiHubClip = 10.0

@Composable
private fun ArbCard(a: ArbOpportunity, onPlace: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+${String.format(Locale.US, "%.2f", a.profitPerDollar * 100)}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text("  on cost · ${a.label} · ${a.event.league}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(a.event.description, style = MaterialTheme.typography.bodyMedium)
            Text("Novig: ${a.novig.selection} at ${Format.percent(a.novig.cost)}", style = MaterialTheme.typography.bodySmall)
            Text("Kalshi: ${a.kalshi.selection} at ${Format.percent(a.kalshi.cost)} (fee included)", style = MaterialTheme.typography.bodySmall)
            Text(
                "Up to ${Format.money(a.maxPayout)} of payout · starts ${Format.startTime(a.event.startsTs)}" + if (a.safe) "" else " · rules can differ between venues",
                style = MaterialTheme.typography.labelSmall, color = if (a.safe) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            Button(onClick = onPlace, modifier = Modifier.padding(top = 4.dp)) { Text("Place both sides") }
        }
    }
}

// ---- Bids -----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KalshiBidsPage(hub: KalshiHub, ui: KalshiUi) {
    val c = ui.config
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionTitle("Bids on Kalshi")
        Text(
            "A bid is a resting order placed under the fair price that waits to be filled (post-only: Kalshi refuses it rather than let it take, and it expires by itself in a few minutes). It is the same bid desk as Novig's: " +
                "fair odds from your sharp books, an edge margin, Kelly sizing, a per-game cap, a guard that pauses bids when fills are being picked off. It runs while this app is open.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchLine("Post bids", "Needs \"Place orders on Kalshi\" in Setup. Off: every bid comes down.", c.scan.maker, "kalshiBidsOn") { v -> hub.update { it.copy(scan = it.scan.copy(maker = v)) } }
        if (c.scan.maker && !c.tradingEnabled) Text("Bids are on but orders are off (Setup › Place orders on Kalshi): nothing is posted.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Chips("Edge under the fair", listOf(0.02, 0.03, 0.04, 0.06, 0.08), c.scan.makerMargin, { Format.percent(it, 0) }) { v -> hub.update { it.copy(scan = it.scan.copy(makerMargin = v)) } }
        Chips("Stake per bid", listOf(AutoBetStake.SIXTEENTH_KELLY, AutoBetStake.EIGHTH_KELLY, AutoBetStake.QUARTER_KELLY, AutoBetStake.HALF_KELLY, AutoBetStake.CUSTOM), c.scan.makerStakeMode, { it.label }) { v -> hub.update { it.copy(scan = it.scan.copy(makerStakeMode = v)) } }
        if (c.scan.makerStakeMode == AutoBetStake.CUSTOM) Chips("My amount", listOf(1.0, 2.0, 5.0, 10.0), c.scan.makerStake, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(makerStake = v)) } }
        Chips("Most per bid", listOf(2.0, 5.0, 10.0, 25.0), c.scan.makerMaxStake, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(makerMaxStake = v)) } }
        Chips("Most bids up at once", listOf(5, 10, 20, 40, 80), c.scan.makerMaxBids, { it.toString() }) { v -> hub.update { it.copy(scan = it.scan.copy(makerMaxBids = v)) } }
        Chips("Most dollars resting", listOf(25.0, 50.0, 100.0, 250.0, 500.0), c.scan.makerMaxDollars, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(makerMaxDollars = v)) } }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ui.bidStatus?.note ?: "No bid pass yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag("kalshiBidNote"))
            OutlinedButton(onClick = hub::cancelBids, enabled = ui.bids.any { it.resting }) { Text("Cancel all") }
        }
        val active = ui.bids.filter { it.resting }
        val filled = ui.bids.filter { it.filled > 0 }.sortedByDescending { it.firstFillAtMs ?: 0L }.take(20)
        if (active.isNotEmpty()) {
            SectionTitle("Resting")
            active.sortedBy { it.startsTs }.forEach { b -> BidRow(b, now) }
        }
        if (filled.isNotEmpty()) {
            SectionTitle("Filled")
            filled.forEach { b -> BidRow(b, now) }
        }
        if (active.isEmpty() && filled.isEmpty()) Text("No bids yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        Text(" ", Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun BidRow(b: com.tjshea.vigilant.data.novig.trading.maker.MakerBid, now: Long) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(b.selection, fontWeight = FontWeight.SemiBold)
            Text("${b.eventName} · ${b.marketLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${b.contracts / 100} contracts at ${Format.percent(b.price, 0)} · fair ${Format.percent(b.fair, 1)} · " +
                    (if (b.filled > 0) "filled ${b.filled / 100} · " else "") + "posted ${Format.age(b.postedAtMs, now)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ---- Bets -----------------------------------------------------------------------------------------------------

@Composable
internal fun BetsPage(ui: KalshiUi, now: Long) {
    val settled = ui.bets.filter { it.status != BetStatus.PENDING }
    val profit = settled.sumOf { b ->
        when (b.status) {
            BetStatus.WON -> (b.contracts ?: 0L) * 0.01 - b.stake
            BetStatus.LOST -> -b.stake
            else -> 0.0
        }
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("kalshiBets")) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Kalshi profit (settled by Kalshi)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Format.signedMoney(profit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${ui.bets.count { it.status == BetStatus.PENDING }} open · ${settled.size} settled" + (ui.balance?.let { " · cash ${Format.money(it)}" } ?: "") + (ui.portfolioValue?.let { " · positions ${Format.money(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    ui.balanceNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (ui.bets.isEmpty()) item { EmptyState("No Kalshi bets yet", "Bets you place or the auto-bet places on Kalshi are listed here, and settled from Kalshi's own record.") }
        items(ui.bets.sortedByDescending { it.createdAtMs }, key = { it.id }) { b -> BetRow(b, now) }
        if (ui.paperBets.isNotEmpty()) {
            item { SectionTitle("Paper (not placed)") }
            items(ui.paperBets.sortedByDescending { it.createdAtMs }, key = { "p-" + it.id }) { b -> BetRow(b, now) }
        }
    }
}

@Composable
private fun BetRow(b: TrackedBet, now: Long) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.selection, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    when (b.status) { BetStatus.PENDING -> "OPEN"; BetStatus.WON -> "WON"; BetStatus.LOST -> "LOST"; BetStatus.PUSH -> "PUSH"; BetStatus.VOID -> "VOID"; BetStatus.FMV -> "FMV" },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (b.status) { BetStatus.WON -> MaterialTheme.colorScheme.primary; BetStatus.LOST -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                )
            }
            Text("${b.eventName} · ${b.marketLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${Format.money(b.stake)} at ${Format.percent(b.price)} (${b.american?.let { Format.american(b.price) } ?: ""}) · placed ${Format.age(b.createdAtMs, now)}" + (b.evPercentAtBet?.let { " · ${Format.evPercent(it)} EV" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ---- Auto-bet -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutoPage(hub: KalshiHub, ui: KalshiUi) {
    val c = ui.config
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionTitle("Auto-bet on Kalshi")
        Text(
            "Uses the same rules as the Novig auto-bet: pregame only, fresh prices, books that agree, your edge and odds limits, one bet a market, and your per-bet, per-day and per-game limits. " +
                "Paper records what it would place at Kalshi's price now and sends nothing. It runs while this app is open; the GitHub engine runs unattended once Kalshi is added there.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Chips("Mode", KalshiAutoMode.entries.toList(), c.autoMode, { when (it) { KalshiAutoMode.OFF -> "Off"; KalshiAutoMode.PAPER -> "Paper"; KalshiAutoMode.LIVE -> "Real money" } }) { m -> hub.update { it.copy(autoMode = m) } }
        if (c.autoMode == KalshiAutoMode.LIVE && !c.tradingEnabled) Text("Real orders also need \"Place orders on Kalshi\" in Setup.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Chips("Smallest edge", listOf(0.01, 0.02, 0.03, 0.05, 0.08), c.scan.autoBetMinEv, { Format.percent(it, 0) }) { v -> hub.update { it.copy(scan = it.scan.copy(autoBetMinEv = v)) } }
        Chips("Stake", listOf(AutoBetStake.SIXTEENTH_KELLY, AutoBetStake.EIGHTH_KELLY, AutoBetStake.QUARTER_KELLY, AutoBetStake.HALF_KELLY, AutoBetStake.ONE_DOLLAR), c.scan.autoBetStake, { it.label }) { v -> hub.update { it.copy(scan = it.scan.copy(autoBetStake = v)) } }
        Chips("Books that must agree", listOf(2, 3, 4, 5), c.scan.autoBetBooks, { it.toString() }) { v -> hub.update { it.copy(scan = it.scan.copy(autoBetBooks = v)) } }
        Chips("Most per bet", listOf(2.0, 5.0, 10.0, 25.0, 50.0), c.scan.autoBetMaxStake, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(autoBetMaxStake = v, apiMaxStake = maxOf(v, it.scan.apiMaxStake))) } }
        Chips("Most per day", listOf(25.0, 50.0, 100.0, 250.0, 500.0), c.scan.apiMaxPerDay, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(apiMaxPerDay = v)) } }
        Chips("Most on one game", listOf(10.0, 25.0, 50.0, 100.0, 0.0), c.scan.apiMaxPerGame, { if (it == 0.0) "no limit" else Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(apiMaxPerGame = v)) } }
        Chips("Bankroll for Kelly", listOf(50.0, 100.0, 250.0, 500.0, 1000.0), c.scan.bankroll, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(bankroll = v)) } }
        ui.autoNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
        Text(" ", Modifier.padding(bottom = 24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Chips(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
        }
    }
}

@Composable
internal fun SwitchLine(title: String, sub: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

// ---- Setup ----------------------------------------------------------------------------------------------------

@Composable
internal fun SetupPage(hub: KalshiHub, ui: KalshiUi, onOpenKeySettings: () -> Unit) {
    val c = ui.config
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        VenueSwitchCard()
        SectionTitle("Your Kalshi account")
        Text(
            if (ui.hasKey) "Key saved. " + (ui.balance?.let { "Cash ${Format.money(it)}" + (ui.portfolioValue?.let { p -> ", positions ${Format.money(p)}" } ?: "") } ?: (ui.balanceNote ?: "Reading the balance…"))
            else "No key saved yet. Orders need your Kalshi Key ID and private key." + (ui.balanceNote?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = onOpenKeySettings) { Text(if (ui.hasKey) "Key & socket settings" else "Add the Kalshi key") }
            OutlinedButton(onClick = hub::refreshBalance) { Text("Check key") }
        }
        EngineCard(hub, ui)
        SwitchLine("Place orders on Kalshi", "Off: the app only reads. On: the Bet button and the auto-bet (when set to real money) can spend your Kalshi cash.", c.tradingEnabled, "kalshiTrading") { v -> hub.update { it.copy(tradingEnabled = v) } }
        SwitchLine("Scan by itself while the app is open", "Every ${c.autoScanSeconds} seconds.", c.autoScan, "kalshiAutoScan") { v -> hub.update { it.copy(autoScan = v) } }
        Chips("Scan every", listOf(30, 60, 90, 180, 300), c.autoScanSeconds, { "${it}s" }) { v -> hub.update { it.copy(autoScanSeconds = v) } }
        SectionTitle("Arbitrage")
        Chips("Smallest sure profit", listOf(0.0025, 0.005, 0.01, 0.02), c.arbMinProfit, { Format.percent(it, 2) }) { v -> hub.update { it.copy(arbMinProfit = v) } }
        Chips("Most payout per arbitrage", listOf(5.0, 10.0, 25.0, 50.0, 100.0), c.arbMaxPayout, { Format.money(it) }) { v -> hub.update { it.copy(arbMaxPayout = v) } }
        SwitchLine("Include props and other lines", "Player props, halves and sets can settle differently at the two venues (a player who does not play); off lists game lines only.", c.arbIncludeRisky, "kalshiArbRisky") { v -> hub.update { it.copy(arbIncludeRisky = v) } }
        SectionTitle("Fair odds")
        Text("Kalshi uses the same fair-odds sources as Novig (your switches and keys in Settings › APIs), and borrows what the Novig scan already read instead of asking again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Chips("Smallest edge listed", listOf(0.005, 0.01, 0.02, 0.03), c.scan.minEvPercent, { Format.percent(it, 1) }) { v -> hub.update { it.copy(scan = it.scan.copy(minEvPercent = v)) } }
        Chips("Bet amount to start the sheet with", listOf(2.0, 5.0, 10.0, 25.0), c.scan.slipCustomStake, { Format.money(it) }) { v -> hub.update { it.copy(scan = it.scan.copy(slipCustomStake = v)) } }
        Text(" ", Modifier.padding(bottom = 24.dp))
    }
}


// ---- The GitHub engine's Kalshi lane --------------------------------------------------------------------------

@Composable
private fun EngineCard(hub: KalshiHub, ui: KalshiUi) {
    val c = ui.config
    var confirmLive by remember { mutableStateOf(false) }
    SectionTitle("GitHub engine")
    Text(
        "The GitHub engine can run Kalshi for you while the phone is off: it prices Kalshi from the boards its Novig scan already read (no second call to the same API), runs the auto-bet and bids below under these settings, " +
            "and lists the sure profits. Paper records what it would bet and orders nothing; Real orders with your Kalshi key, which the app sends to GitHub with your other keys (Settings › Cloud scan › Send my API keys to GitHub).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Chips("Engine", KalshiAutoMode.entries.toList(), c.engineMode, { when (it) { KalshiAutoMode.OFF -> "Off"; KalshiAutoMode.PAPER -> "Paper"; KalshiAutoMode.LIVE -> "Real money" } }) { m ->
        if (m == KalshiAutoMode.LIVE) confirmLive = true else hub.setEngineMode(m)
    }
    ui.engineNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    val e = ui.engine
    if (c.engineMode != KalshiAutoMode.OFF) {
        if (e == null) Text("Waiting for the engine's first Kalshi pass (it runs after each of its Novig scans).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        else {
            Text(
                "Engine: ${e.mode.name.lowercase()} · ${e.note}" + (e.cash?.let { " · cash ${Format.money(it)}" } ?: "") + if (!e.keyOnGitHub && e.mode == KalshiAutoMode.LIVE) " · NO KEY ON GITHUB: it is only papering" else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp).testTag("kalshiEngineNote"),
            )
            if (e.autoNote.isNotEmpty()) Text("Auto-bet: ${e.autoNote}", style = MaterialTheme.typography.labelSmall)
            if (e.bidNote.isNotEmpty()) Text("Bids: ${e.bidNote}", style = MaterialTheme.typography.labelSmall)
            e.arbs.take(3).forEach { a -> Text("Sure profit ${Format.percent(a.profit, 2)}: ${a.selectionNovig} at Novig + ${a.selectionKalshi} at Kalshi (${a.event})", style = MaterialTheme.typography.labelSmall) }
        }
    }
    if (confirmLive) {
        AlertDialog(
            onDismissRequest = { confirmLive = false },
            title = { Text("Let the GitHub engine bet on Kalshi?") },
            text = { Text("With this on, the engine places real orders on your Kalshi account (the auto-bet when its mode is Real money, and bids when they are on) using the key you sent to GitHub, within the limits in the Auto and Bids pages. It runs 08:00 to 01:00 Eastern and stops when you switch it off here or press STOP ALL.") },
            confirmButton = { TextButton(onClick = { hub.setEngineMode(KalshiAutoMode.LIVE); confirmLive = false }) { Text("Real money") } },
            dismissButton = { TextButton(onClick = { confirmLive = false }) { Text("Cancel") } },
        )
    }
}
