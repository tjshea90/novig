package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.cloud.DashboardModel
import com.tjshea.vigilant.data.cloud.DashboardModel.Tone
import com.tjshea.vigilant.data.rfq.ParlayBook
import com.tjshea.vigilant.data.rfq.ParlayHub
import com.tjshea.vigilant.data.rfq.RfqConfig
import com.tjshea.vigilant.data.rfq.RfqStatus
import java.util.Locale

/**
 * The Engine tab's Parlays section (MK15, Tj 2026-11-19: "a parlay hub ... make the gui intuitive and easy to read"): the HUB first (the money, the open parlays with their legs, the closed ones) and the
 * maker's own page (research tiles and controls, [ParlayMakerView]) a tap away. Everything is from the maker's side; [ParlayHub] words it, this file only draws.
 */
@Composable
fun ParlayHubSection(rfq: RfqStatus?, book: ParlayBook?, now: Long, onSet: (RfqConfig, (String?) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf("hub") }
    Column(modifier.testTag("parlaySection"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = page == "hub", onClick = { page = "hub" }, label = { Text("My parlays") }, modifier = Modifier.testTag("parlayPage-hub"))
            FilterChip(selected = page == "maker", onClick = { page = "maker" }, label = { Text("Maker & controls") }, modifier = Modifier.testTag("parlayPage-maker"))
        }
        if (page == "hub") ParlayHubView(rfq, book, now) else ParlayMakerView(rfq, onSet)
    }
}

@Composable
fun ParlayHubView(rfq: RfqStatus?, book: ParlayBook?, now: Long, modifier: Modifier = Modifier) {
    val hub = remember(book, rfq?.config?.maxOpenCollateral, now / 30_000L) { ParlayHub.of(book, rfq, now) }
    val s = hub.summary
    var tab by rememberSaveable { mutableStateOf("open") }
    var showAllClosed by rememberSaveable { mutableStateOf(false) }
    Column(modifier.testTag("parlayHub"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HubHeadline(rfq, book, hub, now)
        if (hub.note.isNotBlank()) Text(hub.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("hubNote"))
        if (!hub.empty) {
            val tiles = hubTiles(hub, now)
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { t -> TileCard(t, Modifier.weight(1f)) }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
            Legend()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = tab == "open", onClick = { tab = "open" }, label = { Text("Open (${s.open})") }, modifier = Modifier.testTag("hubTab-open"))
                FilterChip(selected = tab == "closed", onClick = { tab = "closed" }, label = { Text("Closed (${s.settled})") }, modifier = Modifier.testTag("hubTab-closed"))
            }
            if (tab == "open") {
                if (hub.open.isEmpty()) Text("Nothing open: every parlay has settled.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                hub.open.forEach { OpenCard(it, now) }
            } else {
                if (hub.closed.isEmpty()) Text("No parlay has settled yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (if (showAllClosed) hub.closed else hub.closed.take(15)).forEach { ClosedCard(it) }
                if (!showAllClosed && hub.closed.size > 15) OutlinedButton(onClick = { showAllClosed = true }, modifier = Modifier.testTag("hubShowAll")) { Text("Show all ${hub.closed.size}") }
            }
        }
        Text(
            "The engine on GitHub keeps this book (the maker's fills from Novig, each parlay's legs and their results); this screen only reads it" +
                (book?.takeIf { it.atMs > 0L }?.let { " · updated ${DashboardModel.ago(now - it.atMs)}" } ?: "") + ".",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HubHeadline(rfq: RfqStatus?, book: ParlayBook?, hub: ParlayHub.Hub, now: Long) {
    val s = hub.summary
    val mode = rfq?.config?.mode?.name ?: book?.mode.orEmpty()
    val modeText = when (mode) {
        "LIVE" -> "Live: quoting with real money"
        "SHADOW" -> "Paper mode: no new quotes are sent. The parlays below were filled earlier, while it was live."
        "OFF" -> "Off"
        else -> ""
    }
    val tone = when { s.realizedPnl + s.expectedOpen > 0.005 -> Tone.GOOD; s.realizedPnl + s.expectedOpen < -0.005 -> Tone.BAD; else -> Tone.NEUTRAL }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Parlay maker profit and loss", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ParlayHub.signed(s.realizedPnl), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = toneColor(if (s.realizedPnl > 0.005) Tone.GOOD else if (s.realizedPnl < -0.005) Tone.BAD else Tone.NEUTRAL), modifier = Modifier.testTag("hubPnl"))
            Text(ParlayHub.headline(s), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("hubHeadline"))
            if (!hub.empty) Text(
                "With the open parlays valued at our odds: ${ParlayHub.signed(s.expectedTotal)} expected in total",
                style = MaterialTheme.typography.bodySmall, color = toneColor(tone), modifier = Modifier.testTag("hubExpectedTotal"),
            )
            if (modeText.isNotBlank()) Text(modeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hubTiles(hub: ParlayHub.Hub, now: Long): List<DashboardModel.Tile> {
    val s = hub.summary
    val zone = java.time.ZoneId.systemDefault()
    fun usd(x: Double) = "\$" + String.format(Locale.US, "%.2f", x)
    val tiles = ArrayList<DashboardModel.Tile>()
    tiles += DashboardModel.Tile(
        "Money tied up now", usd(s.collateral), if (s.open == 0) "no open parlays" else "${s.open} open parlays" + (s.maxOpenCollateral?.let { " · limit ${"\$%.0f".format(Locale.US, it)}" } ?: ""),
        s.maxOpenCollateral.let { lim -> if (lim != null && s.collateral > lim * 0.9) Tone.WARN else Tone.NEUTRAL },
    )
    tiles += DashboardModel.Tile("If every open parlay loses", ParlayHub.signed(s.ifAllBuyersLose), "the buyers' stakes are yours, and your ${usd(s.collateral)} comes back", if (s.ifAllBuyersLose > 0.005) Tone.GOOD else Tone.NEUTRAL)
    tiles += DashboardModel.Tile("Worst case", ParlayHub.signed(s.worstCase), "if every parlay still alive wins for its buyer", if (s.worstCase < -0.005) Tone.BAD else Tone.NEUTRAL)
    tiles += DashboardModel.Tile("Expected on the open ones", ParlayHub.signed(s.expectedOpen), "at our odds, after the legs already decided", if (s.expectedOpen > 0.005) Tone.GOOD else if (s.expectedOpen < -0.005) Tone.BAD else Tone.NEUTRAL)
    if (s.lockedInCount > 0) tiles += DashboardModel.Tile("Already won", ParlayHub.signed(s.lockedIn), "${s.lockedInCount} open parlays where the buyer lost a leg: yours when Novig settles them", Tone.GOOD)
    s.lastFinalMs?.let { last ->
        tiles += DashboardModel.Tile(
            "All open parlays final", ParlayHub.relative(last, now).removePrefix("in "), "about ${ParlayHub.clock(last, now, zone)}" + (s.nextFinalMs?.takeIf { it != last }?.let { " · the first ${ParlayHub.relative(it, now)}" } ?: "") + " (games' usual length)",
        )
    }
    tiles += DashboardModel.Tile("Settled", "${s.settled}", "${s.makerWon} won · ${s.makerLost} lost" + (if (s.settled > 0) " · last 24 h ${ParlayHub.signed(s.pnl24h)}" else ""), if (s.makerLost > s.makerWon) Tone.WARN else Tone.NEUTRAL)
    return tiles
}

@Composable
private fun Legend() {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { open = !open }.testTag("hubLegend")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (open) "How to read this (tap to close)" else "How to read this (tap)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (open) {
                Text("You are the maker: you sold each parlay to a buyer. The buyer's stake is yours if any leg loses; if every leg wins you pay the buyer, from the collateral you put up.", style = MaterialTheme.typography.bodySmall)
                Text("A leg that WON moves the buyer closer to winning, so it is amber (bad for you). A leg that LOST kills the buyer's parlay, so it is green (good for you). Grey legs have not started or are still being played.", style = MaterialTheme.typography.bodySmall)
                Text("Each parlay is final when its LAST leg's game ends (shown as an estimate from the game's usual length). Novig then settles it and the money moves.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun legColor(s: ParlayHub.LegState): Tone = when (s) {
    ParlayHub.LegState.WON -> Tone.WARN
    ParlayHub.LegState.LOST -> Tone.GOOD
    ParlayHub.LegState.PUSH -> Tone.NEUTRAL
    ParlayHub.LegState.IN_PLAY -> Tone.NEUTRAL
    ParlayHub.LegState.UPCOMING -> Tone.NEUTRAL
}

private fun legMark(s: ParlayHub.LegState) = when (s) {
    ParlayHub.LegState.WON -> "✓"
    ParlayHub.LegState.LOST -> "✗"
    ParlayHub.LegState.PUSH -> "="
    ParlayHub.LegState.IN_PLAY -> "▶"
    ParlayHub.LegState.UPCOMING -> "○"
}

@Composable
private fun legBarColor(s: ParlayHub.LegState): Color = when (s) {
    ParlayHub.LegState.WON -> Edge.colors.warning
    ParlayHub.LegState.LOST -> Edge.colors.positive
    ParlayHub.LegState.PUSH -> MaterialTheme.colorScheme.outline
    ParlayHub.LegState.IN_PLAY -> MaterialTheme.colorScheme.primary
    ParlayHub.LegState.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun LegBar(legs: List<ParlayHub.LegView>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        legs.forEach { Box(Modifier.weight(1f).height(6.dp).background(legBarColor(it.state), RoundedCornerShape(3.dp))) }
    }
}

@Composable
private fun LegRow(l: ParlayHub.LegView) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(22.dp).background(if (l.state == ParlayHub.LegState.UPCOMING) MaterialTheme.colorScheme.surfaceVariant else legBarColor(l.state), CircleShape), contentAlignment = Alignment.Center) {
            Text(legMark(l.state), style = MaterialTheme.typography.labelMedium, color = if (l.state == ParlayHub.LegState.UPCOMING) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(l.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (l.detail.isNotBlank()) Text(l.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(l.stateText + (l.chance?.takeIf { l.state == ParlayHub.LegState.UPCOMING || l.state == ParlayHub.LegState.IN_PLAY }?.let { " · our odds ${"%.0f".format(Locale.US, it * 100)}%" } ?: ""), style = MaterialTheme.typography.labelSmall, color = toneColor(legColor(l.state)))
        }
    }
}

@Composable
private fun OpenCard(v: ParlayHub.OpenView, now: Long) {
    var open by rememberSaveable(v.rfqId) { mutableStateOf(false) }
    val tone = when (v.status) {
        ParlayHub.Status.BUYER_DEAD -> Tone.GOOD
        ParlayHub.Status.ALL_WON -> Tone.BAD
        ParlayHub.Status.BUYER_AHEAD -> Tone.WARN
        ParlayHub.Status.WAITING -> Tone.NEUTRAL
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { open = !open }.testTag("hubOpen-${v.rfqId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
            LegBar(v.legs)
            Text(v.statusText, style = MaterialTheme.typography.bodySmall, color = toneColor(tone), fontWeight = FontWeight.Medium)
            Text(v.progress + " · " + v.finalText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Money("Stake in", "\$%.2f".format(Locale.US, v.stake), Modifier.weight(1f))
                Money("Our collateral", "\$%.2f".format(Locale.US, v.collateral), Modifier.weight(1f))
                Money("If buyer loses", ParlayHub.signed(v.ifBuyerLoses), Modifier.weight(1f), Tone.GOOD)
                Money("If buyer wins", ParlayHub.signed(v.ifBuyerWins), Modifier.weight(1f), Tone.BAD)
            }
            if (v.status != ParlayHub.Status.BUYER_DEAD) v.expected?.let { e -> v.buyerChance?.let { c -> Text("Expected ${ParlayHub.signed(e)} · the buyer still wins ${"%.0f".format(Locale.US, c * 100)}% of the time by our odds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    v.legs.forEach { LegRow(it) }
                    if (v.lastLegText.isNotBlank()) Text(v.lastLegText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v.filledText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else Text("Tap for the legs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Money(label: String, value: String, modifier: Modifier, tone: Tone = Tone.NEUTRAL) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSurface else toneColor(tone), maxLines = 1)
    }
}

@Composable
private fun ClosedCard(v: ParlayHub.ClosedView) {
    var open by rememberSaveable(v.rfqId) { mutableStateOf(false) }
    val tone = when (v.won) { true -> Tone.GOOD; false -> Tone.BAD; null -> Tone.NEUTRAL }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { open = !open }.testTag("hubClosed-${v.rfqId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
                Spacer8()
                Text(ParlayHub.signed(v.pnl), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = toneColor(tone))
            }
            Text(v.result, style = MaterialTheme.typography.bodySmall, color = toneColor(tone))
            Text(v.settledText + " · " + v.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) { v.legs.forEach { LegRow(it) } }
        }
    }
}

@Composable
private fun Spacer8() { Box(Modifier.width(8.dp)) }
