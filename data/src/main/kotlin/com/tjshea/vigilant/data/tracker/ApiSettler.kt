package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.LedgerRow
import com.tjshea.vigilant.data.novig.trading.NovigPosition
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.engine.EvMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Grades the bets placed through Novig's API from Novig's own books (Tj, 2026-09-29: "include the API grading bets feature for the tracker
 * system"): the ground truth, fair-market-value voids included, where the score feeds ([BetSettler]) can only guess a prop's void. Reads the
 * subaccount's ledger (`SETTLEMENT` rows: what Novig paid for a market) and positions (what it still holds), NOVIG_API.md §14-15:
 *
 *  - a market paid `qty × 1¢`: **won**; paid back what the bet cost: **push**; paid something else: **settled at a fair value** (that payout);
 *  - nothing paid and the position still held: the market hasn't settled yet (a note, nothing changes);
 *  - nothing paid and the position gone: a **loss** (a loss moves no money, so it leaves no row), but only when the score feeds don't say
 *    otherwise: a feed that says won/pushed against Novig's silence is left to a tap ("check Novig"), and with no readable feed a loss is taken
 *    only [INFER_LOSS_AFTER_MS] after the start.
 * A ledger payout always wins over the feeds (Novig is the authority); a disagreement is noted. A result Tj tapped is never overwritten.
 */
class ApiSettler(
    private val tracker: BetTracker,
    private val trading: NovigTradingClient,
    private val subaccountKeyId: String,
    /** The score feeds' opinion of a bet, for the cross-check ([BetSettler.scoreGradeOf]). */
    private val scoreGrade: suspend (TrackedBet) -> BetGrader.Grade? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [stopped]: Novig couldn't be read, so nothing was decided. */
    data class Report(val asked: Int, val settled: Int, val waiting: Int, val manual: Int, val stopped: Boolean = false)

    private val mutex = Mutex()

    /** API bets to look at now: open, the game at least [BetSettler.AFTER_START_MS] old, not tapped by Tj. */
    fun due(bets: List<TrackedBet>, now: Long): List<TrackedBet> = bets
        .filter { it.status == BetStatus.PENDING && it.orderId != null && it.settledBy != BetSettler.BY_YOU && now - it.startsTs >= BetSettler.AFTER_START_MS }
        .sortedBy { it.startsTs }

    suspend fun run(): Report = mutex.withLock {
        val now = clock()
        val todo = due(tracker.all(), now)
        if (todo.isEmpty()) return@withLock Report(0, 0, 0, 0)
        val payouts: List<LedgerRow>
        val positions: List<NovigPosition>
        try {
            // The ledger filters by Novig's own scheduled start, which a bet's start (CrazyNinjaOdds') can differ from by a while: a wide window.
            payouts = trading.ledger(subaccountKeyId, "SETTLEMENT", todo.minOf { it.startsTs } - START_SLACK_MS, todo.maxOf { it.startsTs } + START_SLACK_MS)
            positions = trading.positions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Report(todo.size, 0, 0, 0, stopped = true)
        }
        val changes = LinkedHashMap<String, (TrackedBet) -> TrackedBet>()
        var settled = 0
        var waiting = 0
        var manual = 0
        for ((marketId, group) in todo.filter { it.contracts != null }.groupBy { it.marketId }) {
            // A row can name the market (it covers every bet Tj has on it) or one bet's own fill or order.
            val marketPaid = payouts.filter { it.ref == marketId }.sumOf { it.amount }
            val sides = group.map { it.outcomeId }.distinct()
            for (bet in group) {
                val contracts = bet.contracts ?: continue
                val win = contracts * EvMath.CONTRACT_PAYOUT_DOLLARS
                val paid = bet.paid ?: (bet.stake - (bet.fee ?: 0.0))
                val own = payouts.filter { it.ref == bet.orderId || it.ref in bet.fillIds }.sumOf { it.amount }
                val held = positions.any { it.marketId == bet.marketId && it.outcomeId == bet.outcomeId && it.qty > 0 }
                when {
                    own > 1e-6 -> {
                        val (status, value, evidence) = classify(own, win, paid, contracts)
                        settle(changes, bet, status, value, evidence, now); settled++
                    }
                    marketPaid > 1e-6 && group.size > 1 -> {
                        // Several bets share this market and Novig's row is for all of them, so it can't be handed to one: bets on one side share
                        // its result (judged on their total); bets on both sides are told apart only when the payout is exactly one side's win.
                        val mine = group.filter { it.outcomeId == bet.outcomeId }
                        val mineWin = mine.sumOf { (it.contracts ?: 0L) * EvMath.CONTRACT_PAYOUT_DOLLARS }
                        if (sides.size == 1) {
                            val (status, value, evidence) = classify(marketPaid, mineWin, mine.sumOf { it.paid ?: (it.stake - (it.fee ?: 0.0)) }, mine.sumOf { it.contracts ?: 0L })
                            settle(changes, bet, status, value, evidence, now); settled++
                        } else {
                            val winners = sides.filter { side -> kotlin.math.abs(marketPaid - group.filter { it.outcomeId == side }.sumOf { (it.contracts ?: 0L) * EvMath.CONTRACT_PAYOUT_DOLLARS }) <= TOLERANCE }
                            when {
                                winners.size == 1 && bet.outcomeId in winners ->
                                    { settle(changes, bet, BetStatus.WON, null, "Novig paid ${money(marketPaid)} for the market, which is what this side's ${mine.sumOf { it.contracts ?: 0L }} contracts win: a win", now); settled++ }
                                winners.size == 1 ->
                                    { settle(changes, bet, BetStatus.LOST, null, "Novig paid ${money(marketPaid)} for the market, which is the other side's win: a loss", now); settled++ }
                                else ->
                                    { note(changes, bet, "You hold both sides of this market and Novig's payout (${money(marketPaid)}) doesn't say which won: check it in the Novig app", now, manual = true); manual++ }
                            }
                        }
                    }
                    marketPaid > 1e-6 -> {
                        val (status, value, evidence) = classify(marketPaid, win, paid, contracts)
                        settle(changes, bet, status, value, evidence, now); settled++
                    }
                    held -> {
                        // Still Novig's to settle. Well past the game it's worth a look by hand.
                        val late = now - bet.startsTs > STUCK_AFTER_MS
                        note(changes, bet, if (late) "Novig still lists this position a day after the game: check it in the Novig app" else "Novig hasn't settled this market yet", now, manual = late)
                        if (late) manual++ else waiting++
                    }
                    else -> {
                        val loss = "Novig paid nothing for it and no longer holds the position: a loss"
                        when (val feed = scoreGrade(bet)) {
                            is BetGrader.Grade.Result ->
                                if (feed.status == BetStatus.LOST) {
                                    settle(changes, bet, BetStatus.LOST, null, "$loss (${feed.evidence})", now); settled++
                                } else {
                                    note(changes, bet, "Novig shows no payout, but the score feeds say ${feed.status.name.lowercase()} (${feed.evidence}): check Novig", now, manual = true); manual++
                                }
                            else ->
                                if (now - bet.startsTs >= INFER_LOSS_AFTER_MS) {
                                    settle(changes, bet, BetStatus.LOST, null, loss, now); settled++
                                } else {
                                    note(changes, bet, "Novig hasn't paid this market yet", now); waiting++
                                }
                        }
                    }
                }
            }
        }
        if (changes.isNotEmpty()) tracker.editMany(changes)
        Report(todo.size, settled, waiting, manual)
    }

    /** What [payout] dollars means for a bet of [contracts] that paid [paid] dollars and wins [win]: status, fair-value payout per $1 contract, evidence. */
    private fun classify(payout: Double, win: Double, paid: Double, contracts: Long): Triple<BetStatus, Double?, String> = when {
        kotlin.math.abs(payout - win) <= TOLERANCE -> Triple(BetStatus.WON, null, "Novig paid ${money(payout)} on $contracts contracts: a win")
        kotlin.math.abs(payout - paid) <= TOLERANCE -> Triple(BetStatus.PUSH, null, "Novig paid back the ${money(paid)} it cost: a push")
        else -> Triple(BetStatus.FMV, payout / win, "Novig settled it at a fair value: paid ${money(payout)} on $contracts contracts (a full win is ${money(win)})")
    }

    private fun settle(changes: MutableMap<String, (TrackedBet) -> TrackedBet>, bet: TrackedBet, status: BetStatus, value: Double?, evidence: String, now: Long) {
        changes[bet.id] = {
            // Tapped (or undone) while this pass ran: the tap wins.
            if (it.status != BetStatus.PENDING || it.settledBy == BetSettler.BY_YOU) it
            else it.copy(status = status, settledAtMs = now, settleValue = value, settledBy = BetSettler.BY_NOVIG, books = emptyList(), gradeNote = evidence, gradeAtMs = now, gradeManual = false)
        }
    }

    private fun note(changes: MutableMap<String, (TrackedBet) -> TrackedBet>, bet: TrackedBet, text: String, now: Long, manual: Boolean = false) {
        if (bet.gradeNote == text && bet.gradeManual == manual && bet.gradeAtMs != null && now - bet.gradeAtMs < BetSettler.NOTE_REFRESH_MS) return
        changes[bet.id] = { if (it.status != BetStatus.PENDING || it.settledBy == BetSettler.BY_YOU) it else it.copy(gradeNote = text, gradeAtMs = now, gradeManual = manual) }
    }

    private fun money(v: Double) = String.format(Locale.US, "$%.2f", v)

    companion object {
        /** How far either way of a bet's start the ledger is asked about. */
        const val START_SLACK_MS = 12 * 60 * 60_000L

        /** Ledger amounts are 5-decimal dollars; a payout within a hundredth of a cent of a win or of the cost is that. */
        const val TOLERANCE = 1e-4

        /** With no readable score feed, Novig's silence counts as a loss this long after the start (a settlement never takes this long). */
        const val INFER_LOSS_AFTER_MS = 6 * 60 * 60_000L

        /** A position still held this long after the start is odd enough to say so. */
        const val STUCK_AFTER_MS = 24 * 60 * 60_000L
    }
}

/**
 * Adds fills Novig has that the Tracker doesn't (Tj, 2026-09-29): an order whose answer was lost, or one placed while the app was closed.
 * They arrive with no fair odds on record (no EV is claimed). Only orders newer than [sinceMs] unless it's null: a bet Tj deleted stays deleted.
 */
class ApiBetSync(
    private val tracker: BetTracker,
    private val trading: NovigTradingClient,
    private val novig: NovigSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** How many bets were added; [unknown]: fills whose market Novig no longer lists (nothing to name the bet by). */
    data class Report(val added: Int, val unknown: Int)

    suspend fun run(sinceMs: Long? = clock() - RECENT_MS): Report {
        val fills = trading.fills(null, 500)
        val known = tracker.all().mapNotNullTo(HashSet()) { it.orderId }
        var added = 0
        var unknown = 0
        for ((orderId, group) in fills.groupBy { it.orderId }) {
            if (orderId in known) continue
            if (sinceMs != null && group.maxOf { it.ts } < sinceMs) continue
            val first = group.first()
            val market = try {
                novig.market(first.marketId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (market == null) { unknown++; continue }
            val event = try {
                novig.event(market.eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val target = BetTarget(
                market = market,
                outcomeId = first.outcomeId,
                league = event?.league.orEmpty(),
                eventName = event?.description ?: market.description,
                startsTs = market.startsTs,
                marketLabel = market.description,
                selection = market.outcomes.firstOrNull { it.outcomeId == first.outcomeId }?.name ?: "Novig outcome",
                fair = 0.0,
                fairAsOfMs = null,
                source = BetTracker.SOURCE_VIGILANT,
            )
            if (tracker.logApi(target, orderId, group, imported = true) != null) added++
        }
        return Report(added, unknown)
    }

    companion object {
        /** The window a lost answer's order is looked for in on every sync. */
        const val RECENT_MS = 60 * 60_000L
    }
}
