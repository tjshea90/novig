package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerGuard
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** What the Kalshi bid desk says about itself. */
data class KalshiBidStatus(val note: String, val resting: Int = 0, val restingDollars: Double = 0.0, val stopped: String = "", val atMs: Long = 0L)

/**
 * Kalshi bids (KV4): resting post-only orders under the fair price, on the unchanged Novig bid machinery ([MakerDesk] with its [com.tjshea.vigilant.data.novig.trading.maker.MakerQuote] decisions, plan, wallet and
 * per-game limits, the deeper rung and the picked-off guard) over a Kalshi client in Novig's tongue ([KalshiAsNovig]). One pass is the GitHub engine's [com.tjshea.vigilant.data.cloud.CloudBidRunner] pass
 * with the Kalshi scan's lines in place of the engine's candidates: a bid's price comes from the fair alone, Kalshi only refuses one that would take (post-only). Every bid rests at most its
 * rules' lifetime and Kalshi removes it itself, so a closed app leaves nothing stale. Bids posted here carry [MakerDesk.ENGINE_PREFIX]-free client ids of their own desk.
 */
class KalshiBidRunner(
    private val desk: MakerDesk,
    private val outcomes: KalshiOutcomes,
    private val cash: suspend () -> Double?,
    /** New or grown fills with their bids, once per pass. */
    private val onFills: suspend (List<TrackedBet>, List<MakerBid>) -> Unit = { _, _ -> },
    private val unavailable: (com.tjshea.vigilant.data.scanner.Opportunity) -> String? = { null },
    private val tell: suspend (String, String) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    @Volatile private var haltedUntil = 0L
    @Volatile private var haltedWhy = ""
    @Volatile private var guardFromMs = 0L
    @Volatile var status: KalshiBidStatus? = null
        private set
    @Volatile private var up = false

    suspend fun bids(): List<MakerBid> = desk.bids()

    /** One pass on [result] under [s] (the Kalshi scan's settings with the bid switches). [stopReason]: why nothing may be posted now (the kill switch, a pause), else null. */
    suspend fun pass(s: ScanSettings, result: ScanResult?, stopReason: String?, now: Long = clock()): KalshiBidStatus? {
        if (!s.maker) {
            if (up) stop("Kalshi bids are switched off")
            return null.also { status = null }
        }
        up = true
        // A Kalshi bid rests on Kalshi on its own: a lifetime keeps one a closed app can no longer watch from going stale.
        val rules = MakerRules.of(s).let { it.copy(ttlMs = minOf(it.ttlMs, BID_TTL_MS), refreshBeforeMs = maxOf(it.refreshBeforeMs, REFRESH_BEFORE_MS)) }
        val halted = if (haltedUntil > now) haltedWhy else ""
        val stop = stopReason ?: halted.ifEmpty { null }
        val wallet = cash()
        var placed = 0
        var cancelled = 0
        val fills = ArrayList<TrackedBet>()
        var problems = emptyList<String>()
        var waiting = ""
        try {
            if (result != null) for (o in result.opportunities) outcomes.learn(o.market)
            val mine = desk.bids().filter { it.active || (it.filled > 0 && it.fairAtFill == null) }.mapTo(HashSet()) { it.outcomeId }
            if (result == null && stop == null) {
                val r = desk.fit(rules, s.apiMaxPerDay, wallet)
                fills.addAll(r.fills); problems = r.problems; cancelled = r.cancelled
            } else {
                val lines = MakerLines.from(result, s, now, always = mine, unavailable = unavailable)
                val r = desk.cycle(lines, rules, stop, s.apiMaxPerDay, wallet, denied = emptySet(), autoPost = true, partial = result?.partial == true, keepPosting = { true }, reachUntilMs = null)
                fills.addAll(r.fills); problems = r.problems; placed = r.placed; cancelled = r.cancelled
                waiting = r.waiting.entries.maxByOrNull { it.value }?.let { " · ${r.waiting.values.sum()} waiting: ${it.key}" }.orEmpty()
                if (stop == null && result?.partial != true && !rules.volume) {
                    val d = desk.cycle(lines, rules.copy(margin = rules.margin + MakerDesk.DEEP_MARGIN), stop, s.apiMaxPerDay, wallet, denied = emptySet(), autoPost = true, partial = false, keepPosting = { true }, rung = 1)
                    fills.addAll(d.fills); placed += d.placed; cancelled += d.cancelled; problems = problems + d.problems
                }
                guard(s, now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val text = e.message ?: e.javaClass.simpleName
            log("kalshi bid desk: $text")
            return KalshiBidStatus("bid pass failed: ${text.take(160)}", atMs = now).also { status = it }
        }
        if (fills.isNotEmpty()) runCatching { onFills(fills, desk.bids()) }.onFailure { if (it is CancellationException) throw it }
        val resting = desk.bids().filter { it.resting }
        val dollars = resting.sumOf { it.restingDollars }
        val note = buildString {
            append("${resting.size} bids resting (${String.format(Locale.US, "$%.2f", dollars)})")
            if (placed > 0 || cancelled > 0 || fills.isNotEmpty()) append(" · this pass: $placed posted, $cancelled taken down, ${fills.size} filled")
            append(waiting)
            problems.firstOrNull()?.let { append(" · problem: ${it.take(120)}") }
            stop?.let { append(" · STOPPED: ${it.take(140)}") }
        }
        if (placed > 0 || fills.isNotEmpty() || cancelled > 0) log("kalshi bids: $note")
        return KalshiBidStatus(note, resting.size, dollars, stop.orEmpty(), now).also { status = it }
    }

    /** Takes every bid down ([why]) and stops posting. */
    suspend fun stop(why: String) {
        up = false
        runCatching { desk.cancelAll(why) }.onFailure { if (it is CancellationException) throw it; log("kalshi bids not all taken down: ${it.javaClass.simpleName}") }
        status = KalshiBidStatus("bids down: $why", atMs = clock())
    }

    private suspend fun guard(s: ScanSettings, now: Long) {
        if (!s.makerGuard || haltedUntil > now) return
        val verdict = MakerGuard.check(desk.bids(), maxOf(s.makerGuardFromMs, guardFromMs, now - JUDGE_WINDOW_MS), s.makerAnchorSharp)
        if (!verdict.tripped) return
        haltedWhy = "picked off: " + verdict.text
        haltedUntil = now + HALT_MS
        guardFromMs = now + HALT_MS
        runCatching { desk.cancelAuto("Stopped by the picked-off guard") }
        log("kalshi bids stopped by the picked-off guard for ${HALT_MS / 60_000L} min: ${verdict.text}")
        runCatching { tell("Kalshi bids paused: fills are being picked off", "${MakerGuard.haltedText(verdict).replace("Then Resume bids on the Bids tab", "")} Bids start again in ${HALT_MS / 60_000L} minutes from the fills after this one.") }
    }

    companion object {
        const val BID_TTL_MS = 8 * 60_000L
        const val REFRESH_BEFORE_MS = 3 * 60_000L
        const val HALT_MS = 3_600_000L
        const val JUDGE_WINDOW_MS = 8 * 3_600_000L
    }
}
