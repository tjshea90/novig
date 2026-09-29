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
            payouts = trading.ledger(subaccountKeyId, "SETTLEMENT", todo.minOf { it.startsTs } - 1, todo.maxOf { it.startsTs } + 1)
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
        for (bet in todo) {
            val contracts = bet.contracts ?: continue
            val win = contracts * EvMath.CONTRACT_PAYOUT_DOLLARS
            val paid = bet.paid ?: (bet.stake - (bet.fee ?: 0.0))
            val payout = payouts.filter { it.ref == bet.marketId }.sumOf { it.amount }
            val held = positions.any { it.marketId == bet.marketId && it.outcomeId == bet.outcomeId && it.qty > 0 }
            when {
                payout > 1e-6 -> {
                    val (status, value, evidence) = classify(payout, win, paid, contracts)
                    settle(changes, bet, status, value, evidence, now)
                    settled++
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
