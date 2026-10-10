package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.novig.trading.maker.BidMode
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDecision
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules as BidRules
import com.tjshea.vigilant.data.novig.trading.maker.MakerSetup
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.ClosingLine
import com.tjshea.vigilant.data.tracker.TrackedBet
import java.util.Locale

/**
 * Everything the Bids tab shows (Tj, 2026-10-03: "build the system in the app … It may need a separate section in the app"; RESEARCH.md §70), made at
 * the wiring site from the app's flows: the settings, whether betting through the API is set up and Vigilant's scanner is on, every bid on record,
 * the bids each line of the latest scan would get ([decisions]), the last pass, and the Tracker's bets (a fill's CLV).
 */
@Immutable
data class MakerUi(
    val settings: ScanSettings,
    val setUp: Boolean,
    val vigilantOn: Boolean,
    val bids: List<MakerBid>,
    val decisions: List<MakerDecision>,
    val scanAtMs: Long?,
    val lastPassAtMs: Long?,
    val running: Boolean,
    val problem: String?,
    val bets: List<TrackedBet>,
    val now: Long,
    /** Sides Tj denied (no bid there before the game). */
    val denied: List<com.tjshea.vigilant.data.novig.trading.maker.DeniedBid> = emptyList(),
    /** The background scan runs Vigilant's scan (fresh fairs for bids without Tj scanning). */
    val backgroundFeeds: Boolean = true,
    /** The last pass's bids wanted but not posted, by why ([com.tjshea.vigilant.data.novig.trading.maker.MakerDesk.Report.waiting]). */
    val waiting: Map<String, Int> = emptyMap(),
    /** What the last pass did, in words ("3 posted, 1 cancelled · scan running"); null before the first. */
    val lastPass: String? = null,
    /** Bids priced from CrazyNinjaOdds ([ScanSettings.makerSource]): what the lane reads for them and why it stops them; null with bids priced from Vigilant's scan. */
    val cno: com.tjshea.vigilant.data.novig.trading.maker.CnoBidLane.Status? = null,
) {
    /** Bids are priced from CrazyNinjaOdds ([ScanSettings.makerSource]). */
    val fromCno: Boolean get() = settings.makerSource == com.tjshea.vigilant.data.scanner.BidSource.CNO

    val mode: BidMode get() = BidMode.of(settings)

    /** Bids up on Novig, and ones on their way down (a fill can still land until Novig confirms). */
    val resting: List<MakerBid> get() = lists.resting
    val filled: List<MakerBid> get() = lists.filled

    /** The bids the latest scan wants that aren't up: what Post would send. */
    val ready: List<MakerDecision.Post> get() = lists.ready

    /** Why the other lines get no bid, most common first. */
    val skipped: List<Pair<String, Int>> get() = lists.skipped

    /** The lists above, worked out once for these [bids] and [decisions] (not on every recomposition of every state). */
    private val lists: MakerLists get() = MakerLists.of(bids, decisions)
}

/**
 * What the Bids tab lists, worked out from the bids on record and the latest pass's decisions (a busy slate judges thousands of lines). The tab used
 * to filter, sort and group them again for every state the app published (a scan publishes three a second) and several times within each, on the main
 * thread: part of Tj's "so laggy I almost couldn't use it" while auto-bid ran (2026-10-03, v0.56.1 Diagnostics). The last answer is kept, found by the
 * identity of the two lists: both come from state flows that hand out the same list until something changes.
 */
private class MakerLists private constructor(private val bids: List<MakerBid>, private val decisions: List<MakerDecision>) {
    val resting: List<MakerBid> by lazy { bids.filter { it.active }.sortedBy { it.startsTs } }
    val filled: List<MakerBid> by lazy { bids.filter { it.filled > 0 }.sortedByDescending { it.postedAtMs } }

    val ready: List<MakerDecision.Post> by lazy {
        val up = resting.mapTo(HashSet()) { it.outcomeId }
        decisions.filterIsInstance<MakerDecision.Post>().filter { it.line.outcomeId !in up }.sortedWith(compareBy({ it.price }, { -it.evAtFair }))
    }

    val skipped: List<Pair<String, Int>> by lazy {
        decisions.filterIsInstance<MakerDecision.Skip>().groupingBy { MakerText.reasonGroup(it.why) }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    companion object {
        @Volatile private var last: MakerLists? = null

        fun of(bids: List<MakerBid>, decisions: List<MakerDecision>): MakerLists {
            last?.takeIf { it.bids === bids && it.decisions === decisions }?.let { return it }
            return MakerLists(bids, decisions).also { last = it }
        }
    }
}

/** The tab's buttons: settings changes, a pass now, Post / Cancel one, Cancel all, and the Betting settings page. */
@Immutable
data class MakerActions(
    val onUpdate: ((ScanSettings) -> ScanSettings) -> Unit = {},
    val onRunNow: () -> Unit = {},
    val onPost: (String) -> Unit = {},
    val onCancel: (String) -> Unit = {},
    val onCancelAll: () -> Unit = {},
    val onOpenBetting: () -> Unit = {},
    val onDeny: (String) -> Unit = {},
    val onUndoDeny: (String) -> Unit = {},
)

/** The tab's words, free of Compose so they're tested. */
object MakerText {
    const val INTRO =
        "Vigilant posts bids under its fair price on Novig and waits for someone to take them (a make order: no fee). Each bid is post-only (it never " +
            "takes), never rests longer than the fair it was priced from stays fresh, and comes down at once when the fair moves against it, the game is " +
            "about to start, scanning is paused, or the wallet can no longer cover it beside the other bids (a bet by hand, an auto-bet or a fill took the money: the " +
            "least valuable bids come down first). With auto-make off, Vigilant recommends bids for you to approve or deny. A filled bid is a bet in the Tracker."

    const val CONFIRM =
        "Vigilant will post and move bids by itself from the Vigilant wallet, within these rules, while each scan runs and every background cycle: real money, " +
            "nobody confirming each one (the same way auto-bet places bets). A phone restart switches it off."

    const val RESEARCH =
        "Research (60 days of Novig's trades, RESEARCH.md §70): bids 4% under the fair on player props filled on 30-45% of sides and beat Novig's close by " +
            "+1.3% to +4% a fill; game lines only pay with a fair that leads Novig, so they're off by default."

    const val NEEDS_VIGILANT = "Bids need Vigilant's own scan for their fair prices (Settings › Scanner: Both or Vigilant only): CrazyNinjaOdds only lists bets to take."

    const val NEEDS_CNO = "Bids priced from CrazyNinjaOdds need its scanner and its background scan (Settings › Scanner: Both or CNO only; Auto-scan: CNO or CNO + Vigilant). Picking Recommend or Fully automatic turns them on; Vigilant's own scan is not used."

    /** Under "Bids priced from": what each source reads and how old its data may be. */
    fun sourceNote(s: ScanSettings): String = when (s.makerSource) {
        com.tjshea.vigilant.data.scanner.BidSource.VIGILANT ->
            "Vigilant's own scan prices every bid (the fair of the books it reads, sharp books weighted), as bids always did. CrazyNinjaOdds is not read for bids."
        com.tjshea.vigilant.data.scanner.BidSource.CNO ->
            "CrazyNinjaOdds prices every bid and Vigilant's scan is not used (it can be off). Each background cycle reads CNO's list, a wider list (sides at 0% EV and up, not only the +EV top 50) and the game pages of the " +
                "games worth a bid, every book's price for the bet and its other side; a sharp book on the page must agree, and the bid sits under the lower of CNO's fair and the sharp book's own. A bid rests only while " +
                "the data behind it is under ${s.makerCnoMaxAgeSeconds} s old by CNO's own clock (an odds age CNO doesn't state counts as old), is read again about every minute, and comes down at once when CNO pauses, goes late or can't be read."
    }

    /** One line under the status: what the CNO lane has read for bids ("CrazyNinjaOdds' list 14 s old · 12 game pages held, the oldest 41 s · 3 read last cycle"). */
    fun cnoLine(c: com.tjshea.vigilant.data.novig.trading.maker.CnoBidLane.Status, limitSeconds: Int): String {
        val list = when {
            c.listAgeUnknown -> "its list doesn't say how old it is"
            c.listAgeSec != null -> "its list ${c.listAgeSec} s old"
            else -> "its list not read yet"
        }
        val pages = if (c.pages == 0) "no game page read yet" else "${c.pages} game page${if (c.pages == 1) "" else "s"} held" + (c.oldestPageSec?.let { ", the oldest line's data $it s old" } ?: "")
        val read = c.pagesRead.takeIf { it > 0 }?.let { " · $it read last cycle" }.orEmpty()
        val bad = if (c.failed > 0) " · ${c.failed} failed${c.lastError?.let { ": $it" }.orEmpty()}" else ""
        return "CrazyNinjaOdds: $list · $pages$read$bad · ${c.candidates} candidates of ${c.rows} rows · limit $limitSeconds s"
    }

    /** What each mode does, under the choice. */
    fun modeText(mode: BidMode): String = when (mode) {
        BidMode.OFF -> "No bids. Pick Recommend to approve each bid yourself, or Fully automatic to let Vigilant post them."
        BidMode.RECOMMEND -> "Vigilant recommends bids (here and as notifications) for you to approve or deny; it posts nothing by itself."
        BidMode.AUTOMATIC -> "Fully automatic: Vigilant posts, moves and cancels bids by itself within the rules, while each scan runs and every background cycle. Nothing to tap."
    }

    /** "Bids need these, turned on now: …" (or, before switching, what it will turn on). */
    fun turnedOn(list: List<String>): String = "Turned on for bids: " + list.joinToString("; ") + "."

    /** Fully automatic's confirmation, with what else it turns on and whether auto-bet starts with the background scan. */
    fun confirmText(ui: MakerUi): String {
        val change = MakerSetup.set(ui.settings, BidMode.AUTOMATIC)
        return CONFIRM + "\n\n" + MakerRulesText.summary(ui.settings) +
            (if (change.turnedOn.isEmpty()) "" else "\n\nIt also turns on: " + change.turnedOn.joinToString("; ") + ".") +
            (if (change.startsAutoBet) "\n\n" + STARTS_AUTO_BET else "")
    }

    const val STARTS_AUTO_BET = "Auto-bet is on: with the background scan running it will also place bets by itself (the Auto-bet tab)."

    /** What a pass did: "3 posted, 1 cancelled, scan running" ("nothing to change" when it changed nothing). */
    fun passLine(r: com.tjshea.vigilant.data.novig.trading.maker.MakerDesk.Report): String {
        val parts = listOfNotNull(
            r.placed.takeIf { it > 0 }?.let { "$it posted" },
            r.cancelled.takeIf { it > 0 }?.let { "$it cancelled" },
            r.fills.size.takeIf { it > 0 }?.let { "$it filled" },
        ).ifEmpty { listOf("nothing to change") }
        return parts.joinToString(", ") + (if (r.partial) ", scan running" else "")
    }

    /** "12 ready bids wait: the most bids up at once (20) is reached" (the reasons most common first); null when none wait. */
    fun waitingLine(waiting: Map<String, Int>): String? {
        val n = waiting.values.sum()
        if (n == 0) return null
        return "$n ready bid${if (n == 1) "" else "s"} wait${if (n == 1) "s" else ""}: " + waiting.entries.sortedByDescending { it.value }.joinToString("; ") { (why, k) -> if (waiting.size == 1) why else "$k because $why" }
    }

    const val NEEDS_BETTING = "Set up betting through Novig's API first: bids are posted from the Vigilant wallet."

    /** "12 resting · $58.20 held · 3 filled today · last pass 2m ago". */
    fun status(ui: MakerUi): String {
        val resting = ui.resting
        val held = resting.sumOf { it.restingDollars }
        val today = ui.bids.count { it.filled > 0 && (it.endedAtMs ?: it.postedAtMs) >= ui.now - 24 * 3_600_000L }
        val pass = ui.lastPassAtMs?.let { " · last pass ${Format.age(it, ui.now)}" + (ui.lastPass?.let { p -> " ($p)" } ?: "") }.orEmpty()
        return "${resting.size} resting · ${Format.money(held)} held · $today filled in the last 24 h$pass"
    }

    /** "Bid +105 · fair −102 · +4.0% EV at the fair · $4.85 (1,000 contracts)". */
    fun bidLine(price: Double, fair: Double, ev: Double, contracts: Long): String =
        "Bid ${Format.american(price)} · fair ${Format.american(fair)} · ${Format.evPercentShort(ev)} EV at the fair · " +
            "${Format.money(contracts * price / 100.0)} (${String.format(Locale.US, "%,d", contracts)} contracts)"

    /** "expires in 24 min · starts in 3 h 10 min". */
    fun timing(expiresAtMs: Long?, startsTs: Long, now: Long): String =
        listOfNotNull(expiresAtMs?.let { "expires in ${span(it - now)}" }, "starts in ${span(startsTs - now)}").joinToString(" · ")

    fun span(ms: Long): String {
        val m = (ms.coerceAtLeast(0) + 59_999) / 60_000
        return when {
            m < 60 -> "$m min"
            m < 24 * 60 -> "${m / 60} h${if (m % 60 > 0) " ${m % 60} min" else ""}"
            else -> "${m / (24 * 60)} d"
        }
    }

    /** A fill: "Filled 1,000 of 1,000 at +105 · fair −102 when posted (+4.0%) · CLV +1.8% · Won". */
    fun fillLine(bid: MakerBid, bet: TrackedBet?, now: Long): String {
        val parts = ArrayList<String>()
        parts += "Filled ${String.format(Locale.US, "%,d", bid.filled)} of ${String.format(Locale.US, "%,d", bid.contracts)} at ${Format.american(bid.price)}"
        parts += "fair ${Format.american(bid.fair)} when posted (${Format.evPercentShort(bid.evAtFair)})"
        bet?.let { b -> ClosingLine.clv(b, now)?.let { parts += "CLV ${Format.evPercentShort(it)}" } }
        bet?.status?.takeIf { it != BetStatus.PENDING }?.let { parts += it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return parts.joinToString(" · ")
    }

    /** The fills' numbers: "8 fills · +4.2% EV at the fair · CLV +1.9% on 5 with a close". */
    fun fillSummary(ui: MakerUi): String? {
        val filled = ui.filled
        if (filled.isEmpty()) return null
        val ev = filled.map { it.evAtFair }.average()
        val bets = filled.mapNotNull { b -> ui.bets.firstOrNull { it.id == b.betId || (b.orderId != null && it.orderId == b.orderId) } }
        val clvs = bets.mapNotNull { ClosingLine.clv(it, ui.now) }
        val clv = if (clvs.isEmpty()) "" else " · CLV ${Format.evPercentShort(clvs.average())} on ${clvs.size} with a close"
        return "${filled.size} fill${if (filled.size == 1) "" else "s"} · ${Format.evPercentShort(ev)} EV at the fair$clv"
    }

    /** One skip reason as a group (the line-specific numbers taken out): "A bid at 76.5% is outside …" → "Outside the price window". */
    fun reasonGroup(why: String): String = when {
        why.startsWith("A bid at") && why.contains("outside the price window") -> "Outside the price window"
        why.startsWith("Novig already offers it") -> "Novig already offers it under the bid: take it instead (the +EV feed)"
        why.startsWith("Only ") && why.contains("book") -> "Too few books behind the fair price"
        why.startsWith("Starts within") -> "Starts too soon"
        why.startsWith("The sharp books and the blend are") -> "Small market: the sharp books and the blend disagree"
        why.startsWith("A sharp book's own price gives this small-market bid") -> "Small market: a sharp book's own edge is too thin"
        else -> why
    }

    fun kindLabel(k: BetKind): String = when (k) {
        BetKind.PROP -> "Props"
        BetKind.PERIOD -> "1st half / inning"
        BetKind.TEAM_TOTAL -> "Team totals"
        BetKind.MONEYLINE -> "Moneylines"
        BetKind.SPREAD -> "Spreads"
        BetKind.TOTAL -> "Game totals"
        BetKind.OTHER -> "Other"
    }

    fun statusLabel(b: MakerBid): String = when (b.status) {
        MakerStatus.RESTING -> if (b.filled > 0) "Resting (part filled)" else "Resting"
        else -> b.status.label
    }
}

/**
 * The Bids tab: make orders on Novig ([MakerUi], [MakerActions]). A plain content composable: everything it shows comes in [ui], every tap goes out
 * through [actions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakerScreen(ui: MakerUi, actions: MakerActions) {
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    var skippedOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bids", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val resting = ui.resting
        val ready = ui.ready
        val filled = ui.filled
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("makerScreen"),
        ) {
            item(key = "head") { MakerHead(ui, actions) }
            item(key = "rules") {
                SectionTitle("Rules")
                // Plain left-aligned text, not a TextButton: a button centres and crops a long wrapped summary (Tj's screenshot: first and last words cut off).
                Text(
                    if (rulesOpen) "Hide the rules" else MakerRulesText.summary(ui.settings),
                    Modifier.fillMaxWidth().clickable { rulesOpen = !rulesOpen }.padding(vertical = 10.dp).testTag("makerRulesToggle"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                )
                if (rulesOpen) MakerRules(ui.settings, actions.onUpdate)
            }
            item(key = "restingTitle") { SectionTitle("Resting now (${resting.size})") }
            if (resting.isEmpty()) item(key = "restingNone") { Muted("No bids up right now.") }
            items(resting, key = { "r-" + it.clientId }) { b -> RestingRow(b, ui.now, actions.onCancel) }
            item(key = "readyTitle") {
                SectionTitle(if (ui.settings.maker) "Next to post (${ready.size})" else "Recommended: approve or deny (${ready.size})")
                Muted(
                    when {
                        ui.fromCno && ui.scanAtMs == null -> "No CrazyNinjaOdds list read yet: the background scan (CNO) reads it and the games' pages, then prices the bids."
                        ui.fromCno && ui.settings.maker -> "From CrazyNinjaOdds' list ${Format.age(ui.scanAtMs!!, ui.now)}. Fully automatic posts these by itself at each background cycle, cheapest (underdog) first; Post now doesn't wait."
                        ui.fromCno -> "From CrazyNinjaOdds' list ${Format.age(ui.scanAtMs!!, ui.now)}. Approve re-checks the bid on the latest prices before posting it; Deny skips that side until its game."
                        ui.scanAtMs == null -> "No Vigilant scan yet: scan (or let the background scan run) to price lines to bid on."
                        ui.settings.maker -> "From the scan ${Format.age(ui.scanAtMs, ui.now)}. Fully automatic posts these by itself at the next pass (every 20-30 s), cheapest (underdog) first; Post now doesn't wait."
                        else -> "From the scan ${Format.age(ui.scanAtMs, ui.now)}. Approve re-checks the bid on the latest prices before posting it; Deny skips that side until its game."
                    },
                )
                if (ui.settings.maker) MakerText.waitingLine(ui.waiting)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.testTag("makerWaiting")) }
            }
            items(ready.take(MAX_READY), key = { "p-" + it.line.outcomeId }) { d -> ReadyRow(d, ui.now, ui.setUp, ui.settings.maker, actions) }
            if (ready.size > MAX_READY) item(key = "readyMore") { Muted("+${ready.size - MAX_READY} more (cheapest first)") }
            val skipped = ui.skipped
            if (skipped.isNotEmpty()) {
                item(key = "skipped") {
                    TextButton(onClick = { skippedOpen = !skippedOpen }, modifier = Modifier.testTag("makerSkippedToggle")) {
                        Text("${skipped.sumOf { it.second }} lines get no bid: ${if (skippedOpen) "hide why" else "why"}")
                    }
                    if (skippedOpen) skipped.forEach { (why, n) -> Muted("$n · $why") }
                }
            }
            if (ui.denied.isNotEmpty()) {
                item(key = "deniedTitle") { SectionTitle("Denied (${ui.denied.size})") }
                items(ui.denied, key = { "d-" + it.outcomeId }) { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${d.selection.ifBlank { "A side" }} · no bid until its game (starts in ${MakerText.span(d.startsTs - ui.now)})",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { actions.onUndoDeny(d.outcomeId) }, modifier = Modifier.testTag("undoDeny-${d.outcomeId}")) { Text("Undo") }
                    }
                }
            }
            item(key = "filledTitle") {
                SectionTitle("Filled (${filled.size})")
                MakerText.fillSummary(ui)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("makerFillSummary")) }
                if (filled.isEmpty()) Muted("None yet. A filled bid is a bet in the Tracker, with its CLV once the game starts.")
            }
            items(filled, key = { "f-" + it.clientId }) { b ->
                FilledRow(b, ui.bets.firstOrNull { it.id == b.betId || (b.orderId != null && it.orderId == b.orderId) }, ui.now)
            }
            item(key = "end") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

private const val MAX_READY = 40

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun MakerHead(ui: MakerUi, actions: MakerActions) {
    var confirming by rememberSaveable { mutableStateOf<BidMode?>(null) }
    // What the last switch turned on, said once under the choice (Tj, 2026-10-03: "the app will automatically toggle on everything it needs").
    var turnedOn by rememberSaveable { mutableStateOf<String?>(null) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    fun apply(mode: BidMode) {
        val change = MakerSetup.set(ui.settings, mode)
        turnedOn = change.turnedOn.takeIf { it.isNotEmpty() }?.let(MakerText::turnedOn)
        actions.onUpdate { MakerSetup.set(it, mode).settings }
    }
    confirming?.let { mode ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(if (mode == BidMode.AUTOMATIC) "Post bids automatically?" else "Start the background scan?") },
            text = { Text(if (mode == BidMode.AUTOMATIC) MakerText.confirmText(ui) else MakerText.STARTS_AUTO_BET) },
            confirmButton = {
                TextButton(onClick = { confirming = null; apply(mode) }, modifier = Modifier.testTag("makerConfirmOn")) { Text("Switch on") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
    Column(Modifier.padding(top = 4.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("makerMode")) {
            BidMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = ui.mode == m,
                    onClick = {
                        when {
                            m == ui.mode -> Unit
                            // Fully automatic asks first (real money, nobody confirming each bid), as does a switch that starts auto-bet with the background scan.
                            m == BidMode.AUTOMATIC || MakerSetup.set(ui.settings, m).startsAutoBet -> confirming = m
                            else -> apply(m)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, BidMode.entries.size),
                    enabled = m == BidMode.OFF || ui.setUp || ui.mode == m,
                    modifier = Modifier.testTag("makerMode-${m.name}"),
                ) { Text(m.short, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Text(MakerText.modeText(ui.mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        turnedOn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.positive, modifier = Modifier.padding(top = 4.dp).testTag("makerTurnedOn")) }
        if (!ui.setUp) Banner(MakerText.NEEDS_BETTING, Modifier.padding(top = 10.dp), action = "Set up", onAction = actions.onOpenBetting)
        // A mode that bids, with something it needs switched off since (or before this version): one tap turns it all back on.
        val needs = if (ui.mode == BidMode.OFF) emptyList() else MakerSetup.set(ui.settings, ui.mode).turnedOn
        if (needs.isNotEmpty()) {
            Banner(
                "Bids need: " + needs.joinToString("; ") + ". Until then " + (if (ui.settings.paused) "every bid is down." else "bids are only priced while you scan."),
                Modifier.padding(top = 10.dp).testTag("makerNeeds"), action = "Turn on",
                onAction = { if (MakerSetup.set(ui.settings, ui.mode).startsAutoBet) confirming = ui.mode else apply(ui.mode) },
            )
        } else if (ui.fromCno && ui.mode == BidMode.OFF && !ui.settings.cnoOn) {
            Muted(MakerText.NEEDS_CNO)
        } else if (!ui.fromCno && !ui.vigilantOn && ui.mode == BidMode.OFF) {
            Muted(MakerText.NEEDS_VIGILANT + " Picking Recommend or Fully automatic turns it on.")
        }
        // Bids priced from CrazyNinjaOdds: what its lane has read, and why every bid from it is down when it is (paused, late, unreadable).
        if (ui.fromCno) {
            ui.cno?.stop?.takeIf { ui.mode != BidMode.OFF }?.let { why ->
                Banner(why, Modifier.padding(top = 10.dp).testTag("makerCnoStop"), color = MaterialTheme.colorScheme.error)
            }
            ui.cno?.let { Text(MakerText.cnoLine(it, ui.settings.makerCnoMaxAgeSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp).testTag("makerCnoLine")) }
        }
        // The picked-off guard stopped the bids (Tj, 2026-10-05; RESEARCH.md §88.3): said first, with the way back.
        ui.settings.makerHalted?.takeIf { ui.settings.maker }?.let { why ->
            Banner(
                why, Modifier.padding(top = 10.dp).testTag("makerHalted"), color = MaterialTheme.colorScheme.error, action = "Resume bids",
                onAction = { actions.onUpdate { it.copy(makerHalted = null, makerGuardFromMs = System.currentTimeMillis()) } },
            )
        }
        Text(MakerText.status(ui), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp).testTag("makerStatus"))
        ui.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.negative, modifier = Modifier.padding(top = 4.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = actions.onRunNow, enabled = ui.setUp && !ui.running, modifier = Modifier.testTag("makerRunNow")) {
                Text(if (ui.running) "Working…" else if (ui.settings.maker) "Run a pass now" else "Check my bids now")
            }
            if (ui.resting.isNotEmpty()) {
                OutlinedButton(onClick = actions.onCancelAll, modifier = Modifier.testTag("makerCancelAll")) { Text("Cancel all") }
            }
        }
        TextButton(onClick = { aboutOpen = !aboutOpen }, modifier = Modifier.testTag("makerAbout")) { Text(if (aboutOpen) "Hide how bids work" else "How bids work") }
        if (aboutOpen) {
            Text(MakerText.INTRO, style = MaterialTheme.typography.bodyMedium)
            Text(MakerText.RESEARCH, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The rules' words, free of Compose. */
object MakerRulesText {
    /** The chip for [ScanSettings.makerMaxBids]: a number, or "Unlimited". */
    fun bidsLabel(n: Int): String = if (n >= ScanSettings.NO_LIMIT) "Unlimited" else n.toString()

    /** The chip for [ScanSettings.makerMaxDollars]: dollars, or "No limit". */
    fun dollarsLabel(d: Double): String = if (d >= ScanSettings.MAKER_NO_DOLLAR_LIMIT) "No limit" else Format.money(d)

    const val UNLIMITED_NOTE =
        "No cap on how many bids rest at once: the wallet, the dollars limit, the per-game limit and the day's limit still hold every one, and a pass sends at most " +
            "60 new bids (Novig takes 8 orders a second); the rest go up on the next pass."

    /** What the longest-odds setting does, for the line under its chips ([ScanSettings.makerMaxOdds], 0 = no limit). */
    fun maxOddsNote(maxOdds: Int, lowUsage: Boolean = false): String =
        if (lowUsage && maxOdds <= 0) {
            "No limit picked, so Low API usage bids use their own default: nothing longer than ${com.tjshea.vigilant.engine.Odds.formatAmerican(com.tjshea.vigilant.data.scanner.LowUsageBids.MAX_ODDS)} (a price under " +
                "${String.format(Locale.US, "%.1f", BidRules.priceAtOdds(com.tjshea.vigilant.data.scanner.LowUsageBids.MAX_ODDS) * 100)}¢). Pick a limit and it is the one that runs, shorter or longer."
        } else if (maxOdds <= 0) "No limit: any bid the price window below allows is posted, however long its odds."
        else "No bid is posted at longer than ${com.tjshea.vigilant.engine.Odds.formatAmerican(maxOdds)} (a price under " +
            "${String.format(Locale.US, "%.1f", BidRules.priceAtOdds(maxOdds) * 100)}¢), however good its edge; a bid already up at such a price comes down at the next pass. " +
            "Favorites always pass. The odds are the bid's own price, a margin under the fair, so a fair of +157 with a 4% margin posts near +170."

    /** What the shortest-odds setting does, for the line under its box ([ScanSettings.makerMinOdds], 0 = no limit). */
    fun minOddsNote(minOdds: Int): String = when {
        minOdds == 0 -> "No limit: favorites of any size are bid on, if the price window below allows."
        minOdds < 0 -> "No bid is posted at shorter than ${com.tjshea.vigilant.engine.Odds.formatAmerican(minOdds)} (a price over " +
            "${String.format(Locale.US, "%.1f", com.tjshea.vigilant.data.novig.trading.maker.MakerRules.priceAtShortest(minOdds) * 100)}¢); a bid already up at such a price comes down at the next pass."
        else -> "Underdogs only: no bid at shorter than ${com.tjshea.vigilant.engine.Odds.formatAmerican(minOdds)} (a price over " +
            "${String.format(Locale.US, "%.1f", com.tjshea.vigilant.data.novig.trading.maker.MakerRules.priceAtShortest(minOdds) * 100)}¢); every favorite is skipped."
    }

    const val ALL_BIDS_NOTE = "Every bid the rules below allow. Quick & likely to win keeps only the ones most likely to fill soon and to win."

    /** "4% under the fair · $5 a bid · Props, 1st half / inning, Team totals · expire after 30 min". */
    fun summary(s: ScanSettings): String =
        if (s.makerSource == com.tjshea.vigilant.data.scanner.BidSource.CNO) "priced from CrazyNinjaOdds (data under ${s.makerCnoMaxAgeSeconds} s old) · " + summaryOf(s) else summaryOf(s)

    private fun summaryOf(s: ScanSettings): String =
        if (s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE && s.makerSource == com.tjshea.vigilant.data.scanner.BidSource.VIGILANT) LowUsageText.summary(s) else
        "${pct(s.makerMargin)} under the fair${if (s.makerAnchorSharp) " (sharp book's if lower)" else ""} · ${stake(s)} · " +
            (if (s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY) "quick & likely to win${if (s.makerObscureFill) " (small markets fill the rest)" else ""}: " else "") +
            "${BetKind.entries.filter { it in s.makerKinds }.joinToString(", ") { MakerText.kindLabel(it) }.ifEmpty { "no kinds" }} · " +
            "up to ${s.makerTtlMinutes} min (less if the fair goes old)" + (if (s.trapEarlyHours > 0) " · games within ${s.trapEarlyHours} h" else "") +
                (if (s.makerMaxOdds > 0) " · no bid longer than ${com.tjshea.vigilant.engine.Odds.formatAmerican(s.makerMaxOdds)}" else "")

    /** "¼ Kelly, up to $10 a bid" / "$5 a bid". */
    fun stake(s: ScanSettings): String {
        val max = Format.money(minOf(s.makerMaxStake, s.apiMaxStake))
        return when (s.makerStakeMode) {
            com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM -> "${Format.money(minOf(s.makerStake, s.makerMaxStake, s.apiMaxStake))} a bid"
            com.tjshea.vigilant.data.scanner.AutoBetStake.ONE_DOLLAR -> "\$1 a bid"
            else -> "${s.makerStakeMode.label} of ${Format.money(s.bankroll)}, up to $max a bid"
        }
    }

    /** "4%", "3.5%", "3.25%": as many decimals as the number has (to two), never rounded to another choice. */
    fun pct(v: Double): String = String.format(Locale.US, "%.2f", v * 100).trimEnd('0').trimEnd('.') + "%"

    /** The trap guard's early rule as it touches bids (RESEARCH.md §71). */
    fun earlyNote(hours: Int): String =
        if (hours <= 0) "Off: bids go up on games however far off." else
            "No bid on a game more than $hours h off: the fair that far out is the least reliable, and most of Novig's prop takers (71% of the dollars) " +
                "trade in the last 6 h, so the wallet goes where the fills are."

    /**
     * [earlyNote] in Low API usage: the same hours also set how far its scan reads ([com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours]), because a game the scan never reads can never get
     * a bid. A wider window means more games and leagues asked of ParlayAPI (3 credits a league a scan), said here so the choice is made knowing it.
     */
    fun earlyNoteLowUsage(s: ScanSettings): String {
        val window = com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(s)
        val cost = if (window > com.tjshea.vigilant.data.scanner.LowUsageBids.WINDOW_HOURS)
            " Wider than the usual ${com.tjshea.vigilant.data.scanner.LowUsageBids.WINDOW_HOURS} h reads more games and more leagues each scan: more ParlayAPI credits (3 a league a scan; about +11% a day at 8 h, +30% at 12 h, +85% at 24 h)." else ""
        return (if (s.trapEarlyHours <= 0) "Off: no limit of its own, so this mode's scan reads as far ahead as Settings › Scanning says (Days ahead, Starts within): $window h now, and bids go up on every game it reads."
        else "Bids go up only on games starting within ${s.trapEarlyHours} h, and the scan reads exactly that far (${window} h now; Starts within or Days ahead can only make it shorter).") + cost
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakerRules(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val fromCno = s.makerSource == com.tjshea.vigilant.data.scanner.BidSource.CNO
    // Low API usage is Vigilant's scan on a few books: with bids priced from CrazyNinjaOdds it means nothing, and its panel and rules are not shown.
    val lowUsage = s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE && !fromCno
    Column(Modifier.testTag("makerRules")) {
        RuleChips("Bids priced from", com.tjshea.vigilant.data.scanner.BidSource.entries.toList(), s.makerSource, { it.displayName }) { v -> onUpdate { it.copy(makerSource = v) } }
        Text(MakerText.sourceNote(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("makerSourceNote"))
        if (fromCno) {
            RuleChips("Oldest data a bid may rest on (CNO's own clock, the older of its list and the game page)", ScanSettings.MAKER_CNO_MAX_AGE_CHOICES, s.makerCnoMaxAgeSeconds, { "$it s" }) { v -> onUpdate { it.copy(makerCnoMaxAgeSeconds = v) } }
        }
        if (!lowUsage) {
            RuleChips("Under the fair (the EV each bid is posted at): more fills at 2-2.5%, more per fill at 4% and up", ScanSettings.MAKER_MARGIN_CHOICES, s.makerMargin, MakerRulesText::pct) { v -> onUpdate { it.copy(makerMargin = v) } }
            TypedPercentField(NumberSpecs.percent("under the fair", ScanSettings.MAKER_MARGIN_MIN * 100, ScanSettings.MAKER_MARGIN_MAX * 100), s.makerMargin, "makerMarginField") { v -> onUpdate { it.copy(makerMargin = v) } }
        }
        RuleChips(
            "Size of each bid (a filled bid is a bet): fractional Kelly on your ${Format.money(s.bankroll)} bankroll, like auto-bet and the pros (RESEARCH.md §69)",
            com.tjshea.vigilant.data.scanner.AutoBetStake.entries.toList(), s.makerStakeMode, { it.label },
        ) { v -> onUpdate { it.copy(makerStakeMode = v) } }
        if (s.makerStakeMode == com.tjshea.vigilant.data.scanner.AutoBetStake.CUSTOM) {
            RuleChips("My amount", ScanSettings.MAKER_STAKE_CHOICES, s.makerStake, Format::money) { v -> onUpdate { it.copy(makerStake = v) } }
            TypedDollarField(NumberSpecs.dollars("amount per bid"), s.makerStake, "makerStakeField") { v -> onUpdate { it.copy(makerStake = v) } }
        }
        RuleChips("Most one bid may cost (never over your ${Format.money(s.apiMaxStake)} per-bet limit)", ScanSettings.MAKER_STAKE_CHOICES, s.makerMaxStake, Format::money) { v -> onUpdate { it.copy(makerMaxStake = v) } }
        TypedDollarField(NumberSpecs.dollars("most per bid"), s.makerMaxStake, "makerMaxStakeField") { v -> onUpdate { it.copy(makerMaxStake = v) } }
        RuleChips("Most bids up at once", ScanSettings.MAKER_MAX_BIDS_CHOICES, s.makerMaxBids, MakerRulesText::bidsLabel) { v -> onUpdate { it.copy(makerMaxBids = v) } }
        TypedIntField(NumberSpecs.count("bids", 1, 1000), s.makerMaxBids, "makerMaxBidsField", none = { it >= ScanSettings.NO_LIMIT || it <= 0 }) { v -> onUpdate { it.copy(makerMaxBids = v) } }
        if (s.makerMaxBids == ScanSettings.NO_LIMIT) {
            Text(MakerRulesText.UNLIMITED_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("makerUnlimitedNote"))
        }
        RuleChips("Most dollars up at once (the wallet must cover them)", ScanSettings.MAKER_MAX_DOLLARS_CHOICES, s.makerMaxDollars, MakerRulesText::dollarsLabel) { v -> onUpdate { it.copy(makerMaxDollars = v) } }
        TypedDollarField(NumberSpecs.dollars("most dollars up", 1.0, 1_000_000.0), if (s.makerMaxDollars >= ScanSettings.MAKER_NO_DOLLAR_LIMIT) 0.0 else s.makerMaxDollars, "makerMaxDollarsField") { v -> onUpdate { it.copy(makerMaxDollars = v) } }
        RuleChips("Which bids go up", com.tjshea.vigilant.data.scanner.BidFocus.entries.filter { !fromCno || it != com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE }, s.makerFocus, { it.displayName }) { v -> onUpdate { it.copy(makerFocus = v) } }
        Text(
            when (s.makerFocus) {
                com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY -> com.tjshea.vigilant.data.novig.trading.maker.QuickLikely.EXPLAINER
                com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE -> if (fromCno) MakerRulesText.ALL_BIDS_NOTE else com.tjshea.vigilant.data.novig.trading.maker.LowUsage.EXPLAINER
                else -> MakerRulesText.ALL_BIDS_NOTE
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("makerFocusNote"),
        )
        if (lowUsage) LowUsagePanel(s, onUpdate)
        if (s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY) {
            RuleChips("Smallest market for quick bids: fewest books that price the line (a line fewer books price is a small market)", ScanSettings.MAKER_QUICK_MIN_BOOKS_CHOICES, s.makerQuickMinBooks, { "$it+" }) { v -> onUpdate { it.copy(makerQuickMinBooks = v) } }
            TypedIntField(NumberSpecs.count("books", 1, 20), s.makerQuickMinBooks, "makerQuickMinBooksField", none = { false }) { v -> onUpdate { it.copy(makerQuickMinBooks = v) } }
        }
        if (s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY || lowUsage) {
            SwitchRow(
                "Fill leftover money with small markets",
                com.tjshea.vigilant.data.novig.trading.maker.QuickLikely.obscureNote(s),
                s.makerObscureFill, "makerObscureFill", noteTag = "makerObscureNote",
            ) { on -> onUpdate { it.copy(makerObscureFill = on) } }
            if (s.makerObscureFill) {
                RuleChips("Small-market bids: at least this far under the fair", ScanSettings.MAKER_OBSCURE_MARGIN_CHOICES, s.makerObscureMargin, MakerRulesText::pct) { v -> onUpdate { it.copy(makerObscureMargin = v) } }
                TypedPercentField(NumberSpecs.percent("under the fair", ScanSettings.MAKER_MARGIN_MIN * 100, ScanSettings.MAKER_MARGIN_MAX * 100), s.makerObscureMargin, "makerObscureMarginField") { v -> onUpdate { it.copy(makerObscureMargin = v) } }
                RuleChips("Every sharp book must give a small-market bid at least this edge on its own", ScanSettings.MAKER_OBSCURE_SHARP_MIN_EV_CHOICES, s.makerObscureSharpMinEv, MakerRulesText::pct) { v -> onUpdate { it.copy(makerObscureSharpMinEv = v) } }
                TypedPercentField(NumberSpecs.percent("sharp edge", 0.1, 20.0), s.makerObscureSharpMinEv, "makerObscureSharpMinEvField") { v -> onUpdate { it.copy(makerObscureSharpMinEv = v) } }
                RuleChips("The sharp books and the blend must sit within (points of probability)", ScanSettings.MAKER_OBSCURE_AGREE_CHOICES, s.makerObscureAgreePoints, MakerRulesText::pct) { v -> onUpdate { it.copy(makerObscureAgreePoints = v) } }
                TypedPercentField(NumberSpecs.percent("points apart", 0.1, 20.0), s.makerObscureAgreePoints, "makerObscureAgreeField") { v -> onUpdate { it.copy(makerObscureAgreePoints = v) } }
                RuleChips("Fewest books that must price a small-market line", ScanSettings.MAKER_OBSCURE_MIN_BOOKS_CHOICES, s.makerObscureMinBooks, { "$it+" }) { v -> onUpdate { it.copy(makerObscureMinBooks = v) } }
                TypedIntField(NumberSpecs.count("books", 1, 20), s.makerObscureMinBooks, "makerObscureMinBooksField", none = { false }) { v -> onUpdate { it.copy(makerObscureMinBooks = v) } }
                RuleChips("A small-market bid stakes this share of a popular bid's", ScanSettings.MAKER_OBSCURE_STAKE_CHOICES, s.makerObscureStake, MakerRulesText::pct) { v -> onUpdate { it.copy(makerObscureStake = v) } }
                TypedPercentField(NumberSpecs.percent("share of the stake", 5.0, 100.0), s.makerObscureStake, "makerObscureStakeField") { v -> onUpdate { it.copy(makerObscureStake = v) } }
            }
        }
        if (!lowUsage) {
            Text("Kinds of bet", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BetKind.entries.filter { it != BetKind.OTHER }.forEach { k ->
                    FilterChip(
                        selected = k in s.makerKinds,
                        onClick = { onUpdate { st -> st.copy(makerKinds = if (k in st.makerKinds) st.makerKinds - k else st.makerKinds + k) } },
                        label = { Text(MakerText.kindLabel(k)) },
                        modifier = Modifier.testTag("makerKind-${k.name}"),
                    )
                }
            }
            Text(
                "Game lines (moneylines, spreads, totals) only pay with a fair that leads Novig: the research's bids on them lost to the close with Novig's own price as the fair.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MakerMaxOdds(s, onUpdate)
        MakerMinOdds(s, onUpdate)
        RuleChips("Books that must each say the bid is +EV on their own", listOf(1, 2, 3, 4, 5), s.makerMinBooks, { "$it+" }) { v -> onUpdate { it.copy(makerMinBooks = v) } }
        TypedIntField(NumberSpecs.count("books", 1, 12), s.makerMinBooks, "makerMinBooksField", none = { false }) { v -> onUpdate { it.copy(makerMinBooks = v) } }
        RuleChips("Each bid expires after (re-posted while it's still good)", ScanSettings.MAKER_TTL_CHOICES, s.makerTtlMinutes, { if (it < 60) "$it min" else "${it / 60} h" }) { v -> onUpdate { it.copy(makerTtlMinutes = v) } }
        TypedIntField(NumberSpecs.time("minutes", 1, 1440), s.makerTtlMinutes, "makerTtlField", none = { false }) { v -> onUpdate { it.copy(makerTtlMinutes = v) } }
        RuleChips("No bids this close to the start", ScanSettings.MAKER_STOP_CHOICES, s.makerStopMinutes, { "$it min" }) { v -> onUpdate { it.copy(makerStopMinutes = v) } }
        TypedIntField(NumberSpecs.time("minutes", 1, 720), s.makerStopMinutes, "makerStopField", none = { false }) { v -> onUpdate { it.copy(makerStopMinutes = v) } }
        RuleChips(
            "Trap guard: only games starting within (shared with auto-bet and alerts)", com.tjshea.vigilant.data.scanner.TrapGuard.EARLY_CHOICES, s.trapEarlyHours,
            TrapGuardText::hoursLabel,
        ) { v -> onUpdate { it.copy(trapEarlyHours = v) } }
        TrapEarlyHoursField(s.trapEarlyHours, "maker") { v -> onUpdate { it.copy(trapEarlyHours = v) } }
        Text(
            if (lowUsage) MakerRulesText.earlyNoteLowUsage(s) else MakerRulesText.earlyNote(s.trapEarlyHours), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("makerTrapEarlyNote"),
        )
        // Game-line bids get the trap guard's move rule (v0.56.0, RESEARCH.md §72.3): the auto-bet's same switch, shown here once game lines get bids.
        if (s.makerKinds.any { it in com.tjshea.vigilant.data.novig.trading.maker.MakerRules.GAME_LINES }) {
            SwitchRow("Skip game lines Novig just moved (shared with auto-bet)", TrapGuardText.moveNote(s.trapNovigMove), s.trapNovigMove, "makerTrapMove", noteTag = "makerTrapMoveNote") { on ->
                onUpdate { it.copy(trapNovigMove = on) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Both sides of a market", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Both filling locks in the two margins; off: only the cheaper side (the underdog's bid earns the most).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.makerBothSides, onCheckedChange = { on -> onUpdate { it.copy(makerBothSides = on) } }, modifier = Modifier.testTag("makerBothSides"))
        }
        if (!lowUsage) SwitchRow(
            "Price under the sharp book's fair",
            "A bid's margin is taken from the lower of Vigilant's blended fair and the sharpest book's own fair (Pinnacle, Circa, an exchange, devigged the worst way), so the margin is a real edge against the book that moves first. The blend is partly soft books that follow the sharp ones; a bid that fills is more likely one the sharp book disagrees with (RESEARCH.md §88.3). With no sharp book in the fair, nothing changes.",
            s.makerAnchorSharp, "makerAnchorSharp",
        ) { on -> onUpdate { it.copy(makerAnchorSharp = on) } }
        SwitchRow(
            "Stop bids when fills are picked off",
            "Each fill is checked against the fair on the next scan. When half or more of the last ${ScanSettings.MAKER_GUARD_FILLS} fills were filled above the fair then (the market had already moved away), bids stop themselves and tell you, until you tap Resume bids (RESEARCH.md §88.3).",
            s.makerGuard, "makerGuard",
        ) { on -> onUpdate { it.copy(makerGuard = on) } }
        if (!lowUsage) SwitchRow(
            "Sharp-book veto",
            "Skip a bid that a sharp book in the fair (Pinnacle, Circa, the exchanges) gives " +
                (if (s.sharpVetoMinEv <= 0.0) "no edge" else "under ${AutoBetText.evLabel(s.sharpVetoMinEv)}") +
                " on its own price (the Auto-bet tab's veto bar: a filled bid keeps about the sharp book's edge).",
            s.makerSharpVeto, "makerSharpVeto",
        ) { on -> onUpdate { it.copy(makerSharpVeto = on) } }
        if (!lowUsage && s.makerFocus != com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY) SwitchRow(
            "Popular markets first",
            "When the wallet or the most bids can't take every bid, the ones on the kinds of market Novig's takers trade most (touchdowns, rushing attempts, receptions, pitcher outs, shots on goal) go up before the obscure ones (longest reception, hits, assists), after the ones that lead their side. Measured on Novig's own volume (RESEARCH.md §81.4).",
            s.makerPopularFirst, "makerPopularFirst",
        ) { on -> onUpdate { it.copy(makerPopularFirst = on) } }
        if (!lowUsage && s.makerFocus != com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY) SwitchRow(
            "Require a sharp book to agree",
            "No bid unless a sharp book (Pinnacle, Circa, an exchange) prices the line both ways and agrees it is +EV. Off: a bid on a prop no sharp book prices is allowed (the veto still stops one a sharp book says no to). Too little data yet to say it pays (RESEARCH.md §81.4).",
            s.makerRequireSharp, "makerRequireSharp",
        ) { on -> onUpdate { it.copy(makerRequireSharp = on) } }
        if (!lowUsage && s.makerSource == com.tjshea.vigilant.data.scanner.BidSource.VIGILANT) SwitchRow(
            "Long-run saver",
            com.tjshea.vigilant.data.scanner.LongRunBids.EXPLAINER,
            s.makerLongRun, "makerLongRun",
        ) { on -> onUpdate { it.copy(makerLongRun = on) } }
        SwitchRow(
            "Recommend bids when auto-make is off", "A notification for each new bid worth posting, with Approve and Deny (a few a cycle at most).",
            s.makerRecommend, "makerRecommend",
        ) { on -> onUpdate { it.copy(makerRecommend = on) } }
        if (lowUsage) Text(
            LowUsageText.priceNote(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).testTag("lowUsagePriceNote"),
        ) else Text(
            "Bids are priced between ${Format.american(s.makerMinPrice)} and ${Format.american(s.makerMaxPrice)}" +
                (if (s.makerMaxOdds > 0) ", and never at longer odds than ${com.tjshea.vigilant.engine.Odds.formatAmerican(s.makerMaxOdds)}" else "") +
                " (favorites shorter than that almost never fill), with at least " +
                "${s.makerMinBooks} books each pricing the bid +EV on their own, game lines only with a sharp book in the fair" +
                (if (s.trapNovigMove) " and not on a line Novig just moved" else "") + ", pregame only.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The low-usage bids' controls (Tj, 2026-10-05; RESEARCH.md §92): the 2-3 sharp prop books the fair is built from, how often Vigilant's own scan runs while it is on, and
 * how far under the fair each bid goes (2.5% by default; 1.5% or an amount typed in, never under 0.5%). Chips are the same look as every other rule here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LowUsagePanel(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    val picked = com.tjshea.vigilant.data.scanner.LowUsageBids.books(s)
    Column(Modifier.testTag("lowUsagePanel")) {
        Text("Sharp prop books (pick 2 or 3)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.tjshea.vigilant.data.scanner.LowUsageBids.BOOKS.forEach { b ->
                FilterChip(
                    selected = b.key in picked,
                    onClick = { onUpdate { st -> st.copy(lowUsageBooks = com.tjshea.vigilant.data.scanner.LowUsageBids.toggled(st, b.key)) } },
                    label = { Text(b.title) },
                    modifier = Modifier.testTag("lowUsageBook-${b.key}"),
                )
            }
        }
        Text(LowUsageText.booksNote(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lowUsageBooksNote"))
        Text("How often Vigilant's scan runs", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.tjshea.vigilant.data.scanner.LowUsageBids.PACE_CHOICES.forEach { m ->
                FilterChip(selected = m == s.lowUsagePace, onClick = { onUpdate { it.copy(lowUsagePace = m) } }, label = { Text(LowUsageText.paceLabel(m)) }, modifier = Modifier.testTag("lowUsagePace-$m"))
            }
        }
        TypedIntField(NumberSpecs.time("minutes", com.tjshea.vigilant.data.scanner.LowUsageBids.MIN_MINUTES, 240), s.lowUsagePace, "lowUsagePaceField", none = { it == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO }) { v -> onUpdate { it.copy(lowUsagePace = v) } }
        Text(LowUsageText.paceNote(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lowUsagePaceNote"))
        Text("Under the fair (the +EV each bid is posted at): more bids and fills at 1.5%, more per fill higher", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.tjshea.vigilant.data.scanner.LowUsageBids.MARGIN_CHOICES.forEach { m ->
                FilterChip(selected = m == s.lowUsageMargin, onClick = { onUpdate { it.copy(lowUsageMargin = m) } }, label = { Text(MakerRulesText.pct(m)) }, modifier = Modifier.testTag("lowUsageMargin-${Math.round(m * 1000)}"))
            }
        }
        LowUsageMarginField(s.lowUsageMargin) { m -> onUpdate { it.copy(lowUsageMargin = m) } }
        Text(LowUsageText.marginNote(s.lowUsageMargin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lowUsageMarginNote"))
    }
}

/**
 * The margin under the fair typed in as a percent (Tj, 2026-10-06: "add options for minimum 1.5% positive EV or an amount I type in"), under the chips. The field always shows the saved
 * margin ([margin]); a typed value saves as soon as it is a percent from [com.tjshea.vigilant.data.scanner.LowUsageBids.MIN_MARGIN] to [com.tjshea.vigilant.data.scanner.LowUsageBids.MAX_MARGIN],
 * anything else saves nothing and says why.
 */
@Composable
private fun LowUsageMarginField(margin: Double, onSet: (Double) -> Unit) {
    var text by remember(margin) { mutableStateOf(com.tjshea.vigilant.data.scanner.LowUsageBids.marginText(margin)) }
    val bad = text.isNotEmpty() && com.tjshea.vigilant.data.scanner.LowUsageBids.parseMargin(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
            com.tjshea.vigilant.data.scanner.LowUsageBids.parseMargin(text)?.let(onSet)
        },
        label = { Text("Or type your own (% under the fair)") },
        isError = bad,
        supportingText = { if (bad) Text(LowUsageText.MARGIN_ERROR) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("lowUsageMarginField"),
    )
}

/** The low-usage bids' words, free of Compose. */
object LowUsageText {
    /** Shown under the margin field when what was typed is not a percent the mode takes. */
    const val MARGIN_ERROR = "A percent from 0.5 to 50"

    /** What the margin setting means at [margin] (the chips and the typed field set the same number). */
    fun marginNote(margin: Double): String {
        val m = margin.coerceIn(com.tjshea.vigilant.data.scanner.LowUsageBids.MIN_MARGIN, com.tjshea.vigilant.data.scanner.LowUsageBids.MAX_MARGIN)
        val low = m < com.tjshea.vigilant.data.scanner.LowUsageBids.DEFAULT_MARGIN - 1e-9
        return "Each bid is posted at least ${MakerRulesText.pct(m)} under the fair (the lowest prop-book fair too)" +
            if (low) ": under the 2.5% it started at, so more bids go up and fill, each worth less and closer to the fair's own error." else "."
    }

    fun paceLabel(minutes: Int): String = if (minutes == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO) "Auto" else "$minutes min"

    const val NOTHING_TO_READ_TITLE = "Low API usage: nothing to read right now"

    /** What the +EV tab says when the mode's scan had no market in its window (Tj, 2026-10-05: "it said it scanned but I don't think it did because it only took 1 second"). */
    fun nothingToRead(pace: Int, hours: Int, startsWithin: Int): String =
        "No game with a player-prop market on ${com.tjshea.vigilant.app.AppBook.name} starts in the next $hours hours (games already under way aren't bid on), so this scan asked no " +
            "feed and spent nothing: that is why it took a second. It reads again " +
            (if (pace == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO) "every few minutes" else "every $pace min at most") + " and starts as soon as a game comes inside the window. " + tabNote(hours, startsWithin)

    /**
     * This tab lists bets to TAKE at Novig's price now; Low API usage posts bids UNDER the fair, which are on the Bids tab and never listed here. The scan's reach is the trap guard's
     * hours ([hours], [LowUsageBids.windowHours]): 6 by default, any hours Tj picks, or as far as Starts within and Days ahead say when the trap guard is Off.
     */
    fun tabNote(hours: Int, startsWithin: Int): String = "Low API usage bids are on: this tab lists bets to take at Novig's price; the bids are on the Bids tab. " +
        "The scan reads $hours hours ahead: the Bids tab's trap guard hours set it (Off: as far as Settings › Scanning says), and Starts within or Days ahead can only make it shorter" +
        (if (startsWithin in 1 until hours) " (Starts within $startsWithin h is shorter right now)" else "") + "."

    /** [tabNote] for [s]. */
    fun tabNote(s: ScanSettings): String = tabNote(com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(s), s.startsWithinHours)

    /** A feed's name for the screens and Diagnostics. */
    fun feedName(feed: String): String = when (feed) {
        com.tjshea.vigilant.data.scanner.LowUsageBids.FEED_KALSHI -> "Kalshi (free)"
        com.tjshea.vigilant.data.scanner.LowUsageBids.FEED_PINNACLE -> "PinnWire / pinnapi (Pinnacle)"
        com.tjshea.vigilant.data.scanner.LowUsageBids.FEED_PROPLINE -> "PropLine props"
        com.tjshea.vigilant.data.scanner.LowUsageBids.FEED_PARLAY -> "ParlayAPI props (3 credits a league)"
        else -> feed
    }

    /** What each picked book is read through, and what that costs. */
    fun booksNote(s: ScanSettings): String {
        val picked = com.tjshea.vigilant.data.scanner.LowUsageBids.books(s)
        return com.tjshea.vigilant.data.scanner.LowUsageBids.BOOKS.filter { it.key in picked }.joinToString("\n") { "${it.title}: ${it.note}" }
    }

    /** How the pace trades against bids being up, in the app's own freshness rule (RESEARCH.md §93). */
    fun paceNote(s: ScanSettings): String {
        val skip = "A league with no game in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(s)} hours with a prop market on Novig isn't read at all."
        if (s.lowUsagePace == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO) {
            return "Auto: a scan starts ${com.tjshea.vigilant.data.scanner.LowUsageBids.NEAR_GAP_SECONDS / 60} min after the last while a game is inside 3 hours of its start (a bid ends when its books' prices are 5 minutes old, " +
                "so it is re-posted from the fresh scan before it does), ${com.tjshea.vigilant.data.scanner.LowUsageBids.FAR_GAP_SECONDS / 60} min while every game is further off (10-minute limit). Bids stay up. " +
                "With ParlayAPI carrying a picked book (ProphetX, Caesars) that is about 60 credits per league per hour in the short stretch (3 a scan). $skip"
        }
        val minutes = s.lowUsagePace.coerceAtLeast(com.tjshea.vigilant.data.scanner.LowUsageBids.MIN_MINUTES)
        val far = if (minutes * 60 <= com.tjshea.vigilant.data.scanner.LowUsageBids.FAR_GAP_SECONDS) "Games 3-6 hours out keep their bids up" else "Games 3-6 hours out are down part of the time too"
        return "A scan every $minutes min at most. Inside 3 hours of a start a bid ends when its books' prices are 5 minutes old (a few minutes after the scan that priced it), so at this " +
            "pace those bids are down part of every $minutes min, and all end together; $far (10-minute limit). Auto keeps them up. $skip"
    }

    /** One line for the rules' summary. */
    fun summary(s: ScanSettings): String =
        "low API usage: ${com.tjshea.vigilant.data.scanner.LowUsageBids.names(com.tjshea.vigilant.data.scanner.LowUsageBids.books(s))} · scan ${if (s.lowUsagePace == com.tjshea.vigilant.data.scanner.LowUsageBids.AUTO) "pace Auto" else "every ${s.lowUsagePace} min"} · " +
            "props in the next ${com.tjshea.vigilant.data.scanner.LowUsageBids.windowHours(s)} h · ${MakerRulesText.pct(s.lowUsageMargin.coerceAtLeast(com.tjshea.vigilant.data.scanner.LowUsageBids.MIN_MARGIN))} or more under the fair · " +
            "no bid longer than ${com.tjshea.vigilant.engine.Odds.formatAmerican(lowUsageMaxOdds(s))} · ${MakerRulesText.stake(s)}"

    /** The longest odds the mode posts at: the limit Tj picked, or +130 when he picked none. */
    fun lowUsageMaxOdds(s: ScanSettings): Int =
        com.tjshea.vigilant.data.novig.trading.maker.LowUsage.maxOdds(if (s.makerMaxOdds <= 0) 0 else s.makerMaxOdds.coerceAtLeast(BidRules.MIN_MAX_ODDS))

    /** The line at the foot of the rules in the mode: what the price window is. */
    fun priceNote(s: ScanSettings): String =
        "Bids are priced between ${Format.american(com.tjshea.vigilant.data.novig.trading.maker.MakerRules.of(s).maxPrice)} and ${Format.american(BidRules.priceAtOdds(lowUsageMaxOdds(s)))} (no longer than ${com.tjshea.vigilant.engine.Odds.formatAmerican(lowUsageMaxOdds(s))}; " +
            "favorites shorter than -150 almost never fill, so that is the band unless you pick a shortest odds), player props only, with at least 2 of the picked books each pricing the bid +EV on their own, pregame only."
}

/**
 * The longest odds a bid may be posted at, a preset or typed (Tj, 2026-10-05: "do not post bids longer than +140 odds"): chips as the Auto-bet tab's longest
 * odds, and a field for any other amount from +100 up. "No limit" (the default) leaves the price window as the only limit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakerMaxOdds(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    RuleChips("Longest odds a bid may be posted at", ScanSettings.MAKER_MAX_ODDS_CHOICES, s.makerMaxOdds, AutoBetText::oddsLabel) { v -> onUpdate { it.copy(makerMaxOdds = v) } }
    var oddsText by remember(s.makerMaxOdds) { mutableStateOf(if (s.makerMaxOdds > 0) "${s.makerMaxOdds}" else "") }
    val typed = oddsText.toIntOrNull()
    val bad = oddsText.isNotEmpty() && (typed == null || typed < BidRules.MIN_MAX_ODDS)
    OutlinedTextField(
        value = oddsText,
        onValueChange = { t ->
            oddsText = t.filter { it.isDigit() }.take(5)
            oddsText.toIntOrNull()?.takeIf { it >= BidRules.MIN_MAX_ODDS }?.let { v -> onUpdate { it.copy(makerMaxOdds = v) } }
        },
        label = { Text("Or type your own longest odds (+)") },
        isError = bad,
        supportingText = { if (bad) Text("+${BidRules.MIN_MAX_ODDS} (even money) or more; pick No limit for none") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("makerMaxOddsField"),
    )
    Text(
        MakerRulesText.maxOddsNote(s.makerMaxOdds, s.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("makerMaxOddsNote"),
    )
}

/**
 * The shortest odds a bid may be posted at (Tj, 2026-10-07: "anywhere there is a longest odds setting … make a shortest odds setting as well"): −200 = no bid priced over 66.7¢,
 * +110 = underdogs at least that long only, or any other value typed; No limit leaves the price window as the only limit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakerMinOdds(s: ScanSettings, onUpdate: ((ScanSettings) -> ScanSettings) -> Unit) {
    RuleChips("Shortest odds a bid may be posted at", ScanSettings.MAKER_MIN_ODDS_CHOICES, s.makerMinOdds, AutoBetText::minOddsLabel) { v -> onUpdate { it.copy(makerMinOdds = v) } }
    TypedNumberField(NumberSpecs.SHORTEST_ODDS, oddsShown(s.makerMinOdds), "makerMinOddsField") { v -> onUpdate { it.copy(makerMinOdds = v.toInt()) } }
    Text(MakerRulesText.minOddsNote(s.makerMinOdds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("makerMinOddsNote"))
    Shadowed.oddsRange(s.makerMinOdds, s.makerMaxOdds)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Edge.colors.warning, modifier = Modifier.testTag("makerOddsRange"))
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, tag: String, noteTag: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = if (noteTag != null) Modifier.testTag(noteTag) else Modifier)
        }
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> RuleChips(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
private fun RowCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) { Column(Modifier.padding(12.dp)) { content() } }
}

@Composable
private fun BetTitle(selection: String, sub: String) {
    Text(selection, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RestingRow(b: MakerBid, now: Long, onCancel: (String) -> Unit) {
    RowCard {
        BetTitle(b.selection, "${b.marketLabel} · ${b.eventName}")
        Text(MakerText.bidLine(b.price, b.fair, b.evAtFair, b.contracts - b.filled), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${MakerText.statusLabel(b)} · ${MakerText.timing(b.expiresAtMs, b.startsTs, now)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            val id = b.orderId
            if (id != null) TextButton(onClick = { onCancel(id) }, modifier = Modifier.testTag("cancelBid-$id")) { Text("Cancel") }
        }
    }
}

@Composable
private fun ReadyRow(d: MakerDecision.Post, now: Long, setUp: Boolean, auto: Boolean, actions: MakerActions) {
    val line = d.line
    RowCard {
        BetTitle(line.selection, "${line.marketLabel} · ${line.eventName}")
        Text(MakerText.bidLine(d.price, line.fair ?: d.price, d.evAtFair, d.contracts), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Text(
            listOfNotNull(
                line.offer?.let { "Novig offers ${Format.american(it)} now" },
                "fair good for ${MakerText.span(d.restUntilMs - now)}".takeIf { d.restUntilMs != Long.MAX_VALUE },
                "starts in ${MakerText.span(line.startsTs - now)}",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { actions.onDeny(line.outcomeId) }, modifier = Modifier.testTag("denyBid-${line.outcomeId}")) { Text("Deny") }
            TextButton(onClick = { actions.onPost(line.outcomeId) }, enabled = setUp, modifier = Modifier.testTag("postBid-${line.outcomeId}")) {
                Text(if (auto) "Post now" else "Approve")
            }
        }
    }
}

@Composable
private fun FilledRow(b: MakerBid, bet: TrackedBet?, now: Long) {
    RowCard {
        BetTitle(b.selection, "${b.marketLabel} · ${b.eventName}")
        Text(MakerText.fillLine(b, bet, now), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
