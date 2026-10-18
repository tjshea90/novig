package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.data.novig.trading.maker.MakerDesk
import com.tjshea.vigilant.data.novig.trading.maker.MakerGuard
import com.tjshea.vigilant.data.novig.trading.maker.MakerLine
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.novig.trading.maker.MakerStatus
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TrapGuard
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.RepeatGuard
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.engine.EvMath
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** What the engine's bid desk says about itself in the heartbeat. */
data class BidStatus(
    /** The engine is posting and watching bids (the phone's own desk stays down). */
    val active: Boolean,
    val note: String,
    val resting: Int = 0,
    val restingDollars: Double = 0.0,
    val stopped: String = "",
    val atMs: Long = 0L,
)

/** The engine's view of its bid desk: candidate lines in, a pass under the phone's latest settings, an orderly stop. */
interface EngineBids {
    fun put(b: CloudBids)
    suspend fun pass(s: ScanSettings, now: Long): BidStatus?
    /** Takes every bid the engine has up down and stops posting ([why] in words). */
    suspend fun stop(why: String)
}

/**
 * The GitHub engine's bid desk (Tj, 2026-10-16, TCL31: "make vigilant on the GitHub cloud do auto bids in addition to bets, with the same functionality that is already in the android app").
 *
 * It is the phone's [MakerRunner]-pass on the engine, nothing rewritten: the lines are [CloudBidLane]'s (the candidates the engine makes from its own scan, given Novig's books as new as the lane wants), the
 * decisions are [MakerLines]/[com.tjshea.vigilant.data.novig.trading.maker.MakerQuote]/[com.tjshea.vigilant.data.novig.trading.maker.MakerPlan] through the unchanged [MakerDesk.cycle] (post-only orders with
 * a ttl, a lost answer never re-sent, the wallet and per-game limits, the deeper rung, the wallet deployment and the riskier bids), under [MakerRules.of] the phone's mirrored settings. The differences:
 *  - the wallet is the engine's port's cash ([CloudOrderPort.cash]); a refused key or location stops the desk like the trader ([CloudOrderPort.blocked]);
 *  - the picked-off guard halts the DESK (not a phone setting): [HALT_MS] later it starts again from the fills after it, and Tj is told;
 *  - a market the engine's auto-bet holds an open position in gets no bid (the bid comes down), and the trader counts the bids as positions ([positions]);
 *  - the desk never cancels an order it has no record of (the phone's and the live desks' bids rest under the same subaccount) and its client ids carry [MakerDesk.ENGINE_PREFIX];
 *  - every fill becomes a [TradeEntry] in the engine's trade ledger ([toEntry], via [onFills]) so the grader, the Kelly risk caps and the phone's screens see bids and bets as one record.
 */
class CloudBidRunner(
    private val desk: MakerDesk,
    private val lane: CloudBidLane,
    private val port: CloudOrderPort,
    /** The markets the engine's auto-bet holds an open position in (a bid there comes down). */
    private val tradeMarkets: () -> Set<String>,
    /** New or grown fills with their bids, once per pass. */
    private val onFills: suspend (List<TrackedBet>, List<MakerBid>) -> Unit,
    /** A line for Tj (Messages) when the guard halts the desk. */
    private val tell: suspend (String, String) -> Unit = { _, _ -> },
    /** A market's newest trades, for the trap guard's move rule on game-line bids. */
    private val recentTrades: suspend (String) -> List<TrapGuard.Trade> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) : EngineBids {
    private val moveReads = ConcurrentHashMap<String, Pair<Long, List<TrapGuard.Trade>>>()
    @Volatile private var haltedUntil = 0L
    @Volatile private var haltedWhy = ""
    @Volatile private var guardFromMs = 0L
    @Volatile private var lastStatus: BidStatus? = null
    @Volatile private var up = false

    override fun put(b: CloudBids) = lane.put(b)

    fun status(): BidStatus? = lastStatus

    /** The resting and filled bids as positions for the trader's repeat guard (resting bids count as filled: they may fill any moment). */
    suspend fun positions(): List<RepeatGuard.Pos> = desk.bids().filter { it.active || it.filled > 0 }.map { b ->
        val n = if (b.active) maxOf(b.contracts, b.filled) else b.filled
        RepeatGuard.Pos(b.marketId, b.outcomeId, n * b.price * EvMath.CONTRACT_PAYOUT_DOLLARS, n * EvMath.CONTRACT_PAYOUT_DOLLARS, family = RepeatGuard.family(b.eventName, b.startsTs, b.marketLabel, b.selection))
    }

    override suspend fun pass(s: ScanSettings, now: Long): BidStatus? {
        if (!s.engineBids) {
            // Switched off (or no longer live): whatever the engine has up comes down once, then the desk is quiet.
            if (up) stop("the engine's bids are switched off on the phone")
            return null.also { lastStatus = null }
        }
        up = true
        val sb = CloudEngine.forCloud(s)
        // Tj, 2026-10-17: "if the engine isn't running the auto bids posted [must not] get stale and picked off at bad prices". A bid rests on Novig on its own, so one the engine can no longer watch (the runner lost, the
        // JVM halted by the watchdog) lives only as long as [ENGINE_BID_TTL_MS]: Novig removes it by itself. A live engine re-posts it from the current fair [ENGINE_REFRESH_BEFORE_MS] before it ends.
        val rules = MakerRules.of(sb).let { it.copy(ttlMs = minOf(it.ttlMs, ENGINE_BID_TTL_MS), refreshBeforeMs = maxOf(it.refreshBeforeMs, ENGINE_REFRESH_BEFORE_MS)) }
        val halted = if (haltedUntil > now) haltedWhy else ""
        val stop = when {
            s.killed -> "Stopped by the kill switch"
            s.paused -> "Scanning is paused"
            port.blocked != null -> "Novig refused the engine's key or place: ${port.blocked}"
            halted.isNotEmpty() -> halted
            else -> null
        }
        val wallet = port.cash()?.let { c -> if (s.liveBid) c * (1.0 - s.liveBidLimits.pregameLeavesShare.coerceIn(0.0, 0.95)) else c }
        val cloud = lane.current?.takeIf { now - it.atMs <= MAX_LIST_AGE_MS }
        val mine = desk.bids().filter { it.active || (it.filled > 0 && it.fairAtFill == null) }.mapTo(HashSet()) { it.outcomeId }
        var placed = 0; var cancelled = 0; var fills = ArrayList<TrackedBet>()
        var problems = emptyList<String>()
        var waiting = ""
        try {
            if (stop == null && cloud != null) runCatching { lane.refresh(now, READS_PER_PASS) }.onFailure { if (it is CancellationException) throw it }
            if (cloud == null && stop == null) {
                // No fresh candidates (the list is old: a scan is slow or the engine just started): the bids up stay, within the wallet; fills and expiries are read.
                val r = desk.fit(rules, s.apiMaxPerDay, wallet)
                fills.addAll(r.fills); problems = r.problems; cancelled = r.cancelled
            } else {
                val held = tradeMarkets()
                val lines = lane.lines(now, rules, keep = mine).filter { it.marketId !in held }
                val moved = withMoves(lines, rules, now, read = stop == null)
                val r = desk.cycle(
                    moved, rules, stop, s.apiMaxPerDay, wallet, denied = emptySet(), autoPost = true, partial = cloud?.partial == true, keepPosting = { true }, reachUntilMs = null,
                )
                fills.addAll(r.fills); problems = r.problems; placed = r.placed; cancelled = r.cancelled
                waiting = r.waiting.entries.maxByOrNull { it.value }?.let { " · ${r.waiting.values.sum()} waiting: ${it.key}" }.orEmpty()
                // Still under the wallet target: the DEEPER second bids (the phone's second rung), only while the bids up are under it.
                if (stop == null && cloud?.partial != true && !rules.volume) {
                    val d = desk.cycle(
                        moved, rules.copy(margin = rules.margin + MakerDesk.DEEP_MARGIN), stop, s.apiMaxPerDay, wallet, denied = emptySet(), autoPost = true, partial = false, keepPosting = { true }, rung = 1,
                    )
                    fills.addAll(d.fills); placed += d.placed; cancelled += d.cancelled; problems = problems + d.problems
                }
                guard(sb, now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val text = e.message ?: e.javaClass.simpleName
            log("bid desk: $text")
            return BidStatus(true, "bid pass failed: ${text.take(160)}", stopped = "", atMs = now).also { lastStatus = it }
        }
        if (fills.isNotEmpty()) runCatching { onFills(fills, desk.bids()) }.onFailure { if (it is CancellationException) throw it; log("bid fills not recorded: ${it.javaClass.simpleName}") }
        val bids = desk.bids()
        val resting = bids.filter { it.resting }
        val dollars = resting.sumOf { it.restingDollars }
        val note = buildString {
            append("${resting.size} bids resting (${money(dollars)})")
            if (placed > 0 || cancelled > 0 || fills.isNotEmpty()) append(" · this pass: $placed posted, $cancelled taken down, ${fills.size} filled")
            if (cloud == null && stop == null) append(" · no fresh candidate list: the bids up are kept")
            append(waiting)
            problems.firstOrNull()?.let { append(" · problem: ${it.take(120)}") }
            stop?.let { append(" · STOPPED: ${it.take(140)}") }
        }
        if (placed > 0 || fills.isNotEmpty() || cancelled > 0) log("bids: $note")
        return BidStatus(true, note, resting.size, dollars, stop.orEmpty(), now).also { lastStatus = it }
    }

    override suspend fun stop(why: String) {
        up = false
        runCatching { desk.cancelAll(why) }.onFailure { if (it is CancellationException) throw it; log("bids not all taken down: ${it.javaClass.simpleName}") }
        lastStatus = BidStatus(false, "bids down: $why", atMs = clock())
    }

    /** The picked-off guard: when the newest fills were mostly taken at a price the fair had already left, the desk stops for [HALT_MS] and Tj is told; it then starts from the fills after it. */
    private suspend fun guard(s: ScanSettings, now: Long) {
        if (!s.makerGuard || haltedUntil > now) return
        val verdict = MakerGuard.check(desk.bids(), maxOf(s.makerGuardFromMs, guardFromMs, now - JUDGE_WINDOW_MS), s.makerAnchorSharp)
        if (!verdict.tripped) return
        haltedWhy = "picked off: " + verdict.text
        haltedUntil = now + HALT_MS
        guardFromMs = now + HALT_MS
        runCatching { desk.cancelAuto("Stopped by the picked-off guard") }
        log("bids stopped by the picked-off guard for ${HALT_MS / 60_000L} min: ${verdict.text}")
        runCatching { tell("Bids paused: fills are being picked off", MakerGuard.haltedText(verdict).replace("Then Resume bids on the Bids tab", "") + " The engine starts again in ${HALT_MS / 60_000L} minutes from the fills after this one.") }
    }

    private suspend fun withMoves(lines: List<MakerLine>, rules: MakerRules, now: Long, read: Boolean): List<MakerLine> {
        val wanted = MakerLines.moveWanted(lines, rules, now)
        if (wanted.isEmpty()) return lines
        moveReads.entries.removeIf { now - it.value.first > MOVE_READ_MS }
        val unread = HashSet<String>()
        if (read) {
            for (id in wanted.sortedBy { it.startsTs }.map { it.marketId }.distinct().filter { !moveReads.containsKey(it) }.take(MAX_MOVE_READS)) {
                val trades = try { recentTrades(id) } catch (e: CancellationException) { throw e } catch (e: Exception) { unread += id; continue }
                moveReads[id] = now to trades
            }
        }
        return MakerLines.withMoves(lines, wanted, moveReads.mapValues { it.value.second }, now, unread)
    }

    companion object {
        /** An engine bid rests at most this long on Novig (it removes it itself), so a dead engine leaves nothing stale; a live one re-posts it [ENGINE_REFRESH_BEFORE_MS] before the end. */
        const val ENGINE_BID_TTL_MS = 8 * 60_000L
        const val ENGINE_REFRESH_BEFORE_MS = 3 * 60_000L

        /** The candidate list is used for this long after it was made; older, the bids up stay until their own expiry. */
        const val MAX_LIST_AGE_MS = 30 * 60_000L
        const val READS_PER_PASS = 60
        /** A pause of an hour (was 3): the guard then judges only NEW fills, six at least, so a long pause adds nothing but lost volume (Tj, 2026-10-16: "is it really necessary to stop bids?"). */
        const val HALT_MS = 3_600_000L
        /** Only fills of the last 8 hours are judged: older ones were priced against a different fair (feeds come and go) and cannot say how the bids do now. */
        const val JUDGE_WINDOW_MS = 8 * 3_600_000L
        const val MOVE_READ_MS = 15 * 60_000L
        const val MAX_MOVE_READS = 12

        private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

        /** A bid's fills as the engine's ledger entry (id = the bid's client id, so a grown fill updates the same entry and the grader settles it like a bet). */
        fun toEntry(bid: MakerBid, bet: TrackedBet?, now: Long): TradeEntry {
            val payout = EvMath.CONTRACT_PAYOUT_DOLLARS
            val contracts = maxOf(bid.filled, bet?.contracts ?: 0L)
            val avg = if (contracts > 0 && bid.paid > 0) bid.paid / (contracts * payout) else bid.price
            val fee = bet?.fee ?: 0.0
            val cost = bet?.stake ?: (contracts * avg * payout + fee)
            return TradeEntry(
                id = bid.clientId, atMs = bid.firstFillAtMs ?: bid.postedAtMs.takeIf { it > 0 } ?: now, mode = "LIVE", marketId = bid.marketId, outcomeId = bid.outcomeId, eventId = bid.eventId, event = bid.eventName,
                league = bid.league, kind = bid.kind.label, marketLabel = bid.marketLabel, selection = bid.selection, startsTs = bid.startsTs, contracts = contracts, avgPrice = avg, cost = cost, fee = fee,
                fair = bid.fair, evPercent = if (avg > 0) bid.fair / (avg + fee / maxOf(contracts, 1L) / payout) - 1.0 else bid.evAtFair, stars = null, fairAgeS = bid.fairAgeSec?.toLong(), books = bid.books,
                note = "bid" + (if (bid.overflow) " (riskier, keeps the wallet working)" else "") + (if (bid.rung > 0) " (deeper)" else "") + " · posted at ${String.format(Locale.US, "%.0f", bid.price * 100)}c, ${bid.contracts} wanted",
                orderId = bid.orderId,
            )
        }
    }
}

/** What the phone reads of the engine's desk (`desk.json`): the bids with their fills and the summary, nothing it cannot show. */
@Serializable
data class DeskFile(
    val atMs: Long = 0L,
    val note: String = "",
    val stopped: String = "",
    val bids: List<MakerBid> = emptyList(),
    /** The bids' fills as the tracker keeps them: carried to the next job so repeat/exposure guards stay whole. */
    val fills: List<TrackedBet> = emptyList(),
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
        fun parse(text: String): DeskFile? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(f: DeskFile): String = json.encodeToString(serializer(), f)

        /** What stays in the file: active bids, ended ones for [KEEP_ENDED_MS] (15 min: a 3 h window held ~12,000 cancelled and expired bids, a 12.5 MB file the phone could not load, so the Engine tab showed 0 resting bids on 2026-10-18), filled ones for a week ([com.tjshea.vigilant.data.novig.trading.maker.MakerStore.KEEP_FILLED_MS]). */
        const val KEEP_ENDED_MS = 15 * 60_000L

        fun trim(bids: List<MakerBid>, now: Long): List<MakerBid> = bids.filter { b ->
            !b.status.ended || (if (b.filled > 0) true else (b.endedAtMs ?: b.postedAtMs) >= now - KEEP_ENDED_MS)
        }.filter { !com.tjshea.vigilant.data.novig.trading.maker.MakerStore.expired(it, now) }

        val bidsSerializer = ListSerializer(MakerBid.serializer())
    }
}
