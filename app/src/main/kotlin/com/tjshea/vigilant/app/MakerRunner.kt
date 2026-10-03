package com.tjshea.vigilant.app

import android.app.Application
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDecision
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerQuote
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Make orders in the app (Tj, 2026-10-03: "build the system in the app … It may need a separate section in the app"; RESEARCH.md §70): runs the
 * [MakerDesk] on Vigilant's latest scan. Called (suspending; the caller owns each pass) after every finished Vigilant scan and by every background
 * cycle ([AutoScanner.cycle]), and by the Make tab's buttons; the container takes every bid down the moment bids are switched off or scanning is
 * paused ([AppContainer] collects [ScanSettings.makerNow]). Each fill posts a notification and is in the Tracker as a bet ([TrackedBet.maker]).
 */
class MakerRunner(
    private val app: Application,
    private val c: AppContainer,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Vigilant's latest scan (its result is what bids are judged on). */
    private val scan: () -> com.tjshea.vigilant.data.scanner.ScanRun = { c.runner.state.value },
    /** The desk on the Vigilant wallet; null when betting through the API isn't set up. */
    private val desk: () -> MakerDesk? = { c.makerDesk() },
) {
    /** What the Make tab shows: the last pass and the bids each line would get now. */
    data class Status(
        val running: Boolean = false,
        val lastAtMs: Long? = null,
        val lastReport: MakerDesk.Report? = null,
        val problem: String? = null,
        val decisions: List<MakerDecision> = emptyList(),
        val decisionsAtMs: Long? = null,
        /** When the scan behind [decisions] was priced; null = no scan yet. */
        val scanAtMs: Long? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** One pass at a time (the desk's own order lock keeps orders apart from bets; this keeps two passes from judging the same lines). */
    private val passes = Mutex()

    /**
     * One pass: with bids on, the bids Vigilant's latest scan wants are posted, moved and cancelled; with them off or scanning paused, every bid comes
     * down; with no scan yet, only fills and expiries are read. Null when betting through the API isn't set up. [why] is logged.
     */
    suspend fun run(why: String): MakerDesk.Report? = passes.withLock {
        val desk = desk() ?: return@withLock null
        val s = c.currentSettings()
        val any = desk.bids().any { it.active }
        if (!s.maker && !any) {
            preview(s)
            return@withLock null
        }
        val run = scan()
        val result = run.result
        _status.update { it.copy(running = true) }
        try {
            val now = clock()
            val stop = stopReason(s)
            // No scan yet, or one still running (its lines come in a league at a time): only fills and expiries until it's done.
            if ((result == null || run.scanning || result.partial) && stop == null) {
                val fills = desk.settleOnly()
                notifyFills(fills, desk.bids())
                _status.update { it.copy(running = false, lastAtMs = now, problem = null) }
                return@withLock null
            }
            val wallet = runCatching { c.wallet.fresh()?.dollars }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            val report = desk.cycle(MakerLines.from(result, s, now), MakerRules.of(s), stop, s.apiMaxPerDay, wallet)
            notifyFills(report.fills, desk.bids())
            if (report.placed > 0 || report.cancelled > 0 || report.fills.isNotEmpty()) {
                c.eventLog.info("MAKER", "$why: ${report.placed} posted, ${report.cancelled} cancelled, ${report.fills.size} filled, ${report.resting} resting" + (report.stopped?.let { " ($it)" } ?: ""))
            }
            report.problems.forEach { p -> runCatching { c.problems.add("Make orders", p) } }
            _status.update {
                it.copy(
                    running = false, lastAtMs = now, lastReport = report, problem = report.problems.firstOrNull(),
                    decisions = report.decisions, decisionsAtMs = now, scanAtMs = result?.computedAtMs,
                )
            }
            report
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            val text = (e as? NovigApiException)?.advice ?: e.message ?: e.javaClass.simpleName
            runCatching { c.problems.add("Make orders", text) }
            _status.update { it.copy(running = false, problem = text) }
            null
        }
    }

    /** The bids each line of the latest scan would get now, posting nothing (the Make tab with bids off, or between passes). */
    suspend fun preview(s: ScanSettings? = null) {
        val settings = s ?: c.currentSettings()
        val now = clock()
        val held = c.tracker.all().filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId }
        val resting = desk()?.bids()?.filter { it.active }?.mapTo(HashSet()) { it.outcomeId }.orEmpty()
        val run = scan()
        val decisions = MakerQuote.decideAll(MakerLines.from(run.result, settings, now), MakerRules.of(settings), now, held - resting)
        _status.update { it.copy(decisions = decisions, decisionsAtMs = now, scanAtMs = run.result?.computedAtMs) }
    }

    /** Tj's Post on one line: posted now if it still gets a bid. Null when posted, else why not. */
    suspend fun post(outcomeId: String): String? {
        val desk = desk() ?: return "Betting through Novig's API isn't set up (Settings › Betting & Novig account)"
        val s = c.currentSettings()
        if (s.paused) return "Scanning is paused: resume it to post bids"
        val now = clock()
        val held = c.tracker.all().filter { it.status == BetStatus.PENDING && it.outcomeId.isNotBlank() }.mapTo(HashSet()) { it.outcomeId }
        val line = MakerLines.from(scan().result, s, now).firstOrNull { it.outcomeId == outcomeId } ?: return "That line isn't in the latest scan any more"
        val rules = MakerRules.of(s)
        return when (val d = MakerQuote.decide(line, rules, now, held)) {
            is MakerDecision.Skip -> d.why
            is MakerDecision.Post -> desk.post(d, rules).also { if (it == null) preview(s) }
        }
    }

    /** Tj's Cancel on one bid. */
    suspend fun cancel(orderId: String): String? = desk()?.cancel(orderId).also { preview() }

    /** Every bid down ([why] on each). How many cancels Novig took; null when betting isn't set up. */
    suspend fun cancelAll(why: String): Int? {
        val desk = desk() ?: return null
        if (desk.bids().none { it.active }) return 0
        return desk.cancelAll(why).also {
            c.eventLog.info("MAKER", "every bid cancelled: $why")
            preview()
        }
    }

    private fun stopReason(s: ScanSettings): String? = when {
        !AppBook.isNovig -> "Make orders are for Novig"
        !s.maker -> "Bids are switched off"
        s.paused -> "Scanning is paused"
        else -> null
    }

    private fun notifyFills(bets: List<TrackedBet>, bids: List<MakerBid>) {
        for (bet in bets) {
            val bid = bids.firstOrNull { it.betId == bet.id || it.orderId == bet.orderId } ?: continue
            MakerNotes.filled(app, bet, bid)
            c.eventLog.count("maker.filled")
        }
    }
}

/** The notification for a filled bid (its own each: the bet's id is the tag). */
object MakerNotes {
    private const val ID_FILLED = 5_100_000

    /** "Bid filled $4.85 · +4.0% EV · Player Over 50.5". */
    fun title(bet: TrackedBet): String {
        val ev = bet.evPercentAtBet?.let { String.format(Locale.US, " · %+.1f%% EV", it * 100) }.orEmpty()
        return "Bid filled ${String.format(Locale.US, "$%.2f", bet.stake)}$ev · ${bet.selection}"
    }

    /** "+105 (bid) · fair +96 · 1,000 of 1,000 contracts · Player Receiving Yards · A @ B". */
    fun text(bet: TrackedBet, bid: MakerBid): String {
        val odds = bet.american?.let { com.tjshea.vigilant.engine.Odds.formatAmerican(it) } ?: "?"
        val fair = com.tjshea.vigilant.engine.Odds.formatAmerican(com.tjshea.vigilant.engine.Odds.probabilityToAmerican(bid.fair.coerceIn(0.001, 0.999)))
        return "$odds (your bid) · fair $fair · ${String.format(Locale.US, "%,d", bet.contracts ?: 0)} of ${String.format(Locale.US, "%,d", bid.contracts)} contracts · ${bet.marketLabel} · ${bet.eventName}"
    }

    fun filled(app: Application, bet: TrackedBet, bid: MakerBid) {
        if (!ScanService.canNotify(app)) return
        AutoBetNotes.ensureChannel(app)
        val text = text(bet, bid)
        val n = NotificationCompat.Builder(app, AutoBetNotes.CHANNEL_BET)
            .setSmallIcon(R.drawable.ic_scan)
            .withWallet(app)
            .setContentTitle(title(bet))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(EvAlerts.openVigilant(app, ID_FILLED))
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(bet.id, ID_FILLED, n) }
    }
}
