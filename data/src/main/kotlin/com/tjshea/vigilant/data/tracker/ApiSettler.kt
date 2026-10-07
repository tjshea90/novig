package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.Picks
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
 *    only [INFER_LOSS_AFTER_MS] after the start. **Never for a market held on both sides** ([bothSidesHeld]): one side of it won, so silence
 *    alone isn't a loss there, and neither is a second "lost" for a market whose other side already lost ([reopenBothLost] takes such a grade back).
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
    /** [stopped]: Novig couldn't be read, so nothing was decided. [reopened]: wrong grades taken back ([reopenBothLost]). */
    data class Report(val asked: Int, val settled: Int, val waiting: Int, val manual: Int, val stopped: Boolean = false, val reopened: Int = 0)

    private val mutex = Mutex()

    /** API bets to look at now: open, the game at least [BetSettler.AFTER_START_MS] old, not tapped by Tj. */
    fun due(bets: List<TrackedBet>, now: Long): List<TrackedBet> = bets
        .filter { it.status == BetStatus.PENDING && it.orderId != null && it.settledBy != BetSettler.BY_YOU && now - it.startsTs >= BetSettler.AFTER_START_MS }
        .sortedBy { it.startsTs }

    suspend fun run(): Report = mutex.withLock {
        val now = clock()
        val reopened = reopenBothLost(now)
        val all = tracker.all()
        val todo = due(all, now)
        if (todo.isEmpty()) return@withLock Report(0, 0, 0, 0, reopened = reopened)
        // Markets held on both sides: one side of each wins, so "both lost" is never an answer ([bothSidesHeld]).
        val bothHeld = bothSidesHeld(all)
        val lostNow = HashSet<String>()
        // Legs graded lost THIS pass from a score feed's own words (id to the feed's evidence): proof for the other leg of a half-point two-way market ([wonByOtherLeg]).
        val lostByFeed = HashMap<String, String>()
        val payouts: List<LedgerRow>
        val positions: List<NovigPosition>
        try {
            // The ledger filters by Novig's own scheduled start, which a bet's start (CrazyNinjaOdds') can differ from by a while: a wide window.
            payouts = trading.ledger(subaccountKeyId, "SETTLEMENT", todo.minOf { it.startsTs } - START_SLACK_MS, todo.maxOf { it.startsTs } + START_SLACK_MS)
            positions = trading.positions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Report(todo.size, 0, 0, 0, stopped = true, reopened = reopened)
        }
        val changes = LinkedHashMap<String, (TrackedBet) -> TrackedBet>()
        var settled = 0
        var waiting = 0
        var manual = 0
        for ((marketId, group) in todo.filter { it.contracts != null }.groupBy { it.marketId }) {
            // A row can name the market (it covers every bet Tj has on it) or one bet's own fill or order.
            val marketPaid = payouts.filter { it.ref == marketId }.sumOf { it.amount }
            val sides = group.map { it.outcomeId }.distinct()
            // Both sides held so the payout can't say which won (every lock): worked out once for the market ([bothSides]).
            var both: Pair<Map<String, BetStatus>, String>? = null
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
                                else -> {
                                    val v = both ?: bothSides(group, marketPaid).also { both = it }
                                    val status = v.first[bet.outcomeId]
                                    if (status != null) {
                                        settle(changes, bet, status, null, v.second, now); settled++
                                    } else {
                                        note(changes, bet, "You hold both sides of this market and Novig's payout (${money(marketPaid)}) doesn't say which won: check it in the Novig app", now, manual = true); manual++
                                    }
                                }
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
                        val heldBoth = bothHeld[bet.marketId]
                        when (val feed = scoreGrade(bet)) {
                            is BetGrader.Grade.Result ->
                                if (feed.status == BetStatus.LOST) {
                                    // Both sides held: the other side already lost, so this one can't have (a feed misread one of them).
                                    if (heldBoth != null && heldBoth.any { it.id != bet.id && it.outcomeId != bet.outcomeId && (it.status == BetStatus.LOST || it.id in lostNow) }) {
                                        note(changes, bet, OTHER_SIDE_LOST, now, manual = true); manual++
                                    } else {
                                        settle(changes, bet, BetStatus.LOST, null, "$SILENT_LOSS (${feed.evidence})", now); settled++; lostNow += bet.id; lostByFeed[bet.id] = feed.evidence
                                    }
                                } else {
                                    note(changes, bet, "Novig shows no payout, but the score feeds say ${feed.status.name.lowercase()} (${feed.evidence}): check Novig", now, manual = true); manual++
                                }
                            else ->
                                if (now - bet.startsTs >= INFER_LOSS_AFTER_MS) {
                                    // Silence alone is never a loss when both sides are held (Tj, 2026-10-05: Ollie Gordon's Over 29.5 won with 100 yards and was
                                    // graded lost, so the locked market read as losing both legs): left to the payout, the feeds or a tap.
                                    if (heldBoth != null) {
                                        // Unless the market is a half-point two-way one whose other leg a score feed graded lost: then this leg won (Tj, 2026-10-07, rec 11).
                                        val proof = wonByOtherLeg(bet, heldBoth, lostByFeed)
                                        if (proof != null) {
                                            settle(changes, bet, BetStatus.WON, null, "$INFERRED_WON ($proof)", now); settled++
                                        } else {
                                            note(changes, bet, BOTH_HELD_SILENT, now, manual = true); manual++
                                        }
                                    } else {
                                        settle(changes, bet, BetStatus.LOST, null, SILENT_LOSS, now); settled++; lostNow += bet.id
                                    }
                                } else {
                                    note(changes, bet, "Novig hasn't paid this market yet", now); waiting++
                                }
                        }
                    }
                }
            }
        }
        if (changes.isNotEmpty()) tracker.editMany(changes)
        Report(todo.size, settled, waiting, manual, reopened = reopened)
    }

    /**
     * The score feeds' evidence that [bet] won, or null: the market is held on both sides ([heldBoth]), every leg is on a half-point line (so nothing can push and the
     * market has exactly two outcomes), and another leg, on the other outcome, was graded lost WITH a score feed's own words ([SILENT_LOSS] plus the evidence in brackets, now or
     * earlier this pass in [lostByFeed]), never from Novig's silence alone and never by a tap of Tj's. One side of such a market won, so the leg whose own wording the feeds
     * can't read (an imported lock's "Over 29.5") won. Whole-number lines, moneylines and three-way markets are left to the payout or a tap, as before.
     */
    private fun wonByOtherLeg(bet: TrackedBet, heldBoth: List<TrackedBet>, lostByFeed: Map<String, String>): String? {
        if (heldBoth.any { !halfPointLine(it.selection) }) return null
        for (other in heldBoth) {
            if (other.id == bet.id || other.outcomeId == bet.outcomeId) continue
            lostByFeed[other.id]?.let { return it }
            val note = other.gradeNote ?: continue
            if (other.status == BetStatus.LOST && other.settledBy == BetSettler.BY_NOVIG && note.startsWith("$SILENT_LOSS (") && note.endsWith(")")) {
                return note.removePrefix("$SILENT_LOSS (").removeSuffix(")")
            }
        }
        return null
    }

    /**
     * A market held on both sides ([bothSidesHeld]) pays one side whichever wins, so every leg graded lost is a wrong grade. The ones worked out
     * from Novig's silence alone ([SILENT_LOSS], no score feed behind them) are the doubtful ones: taken back, with a note to check, and the rule
     * above keeps them from being graded lost again. A result Tj tapped is left. Returns how many were taken back.
     */
    private suspend fun reopenBothLost(now: Long): Int {
        val ids = bothSidesHeld(tracker.all()).values
            .filter { legs -> legs.all { it.status == BetStatus.LOST } }
            .flatMap { legs -> legs.filter { it.settledBy == BetSettler.BY_NOVIG && it.gradeNote == SILENT_LOSS } }
            .map { it.id }
        if (ids.isEmpty()) return 0
        tracker.editMany(ids.associateWith { { b: TrackedBet ->
            if (b.status != BetStatus.LOST || b.settledBy != BetSettler.BY_NOVIG || b.gradeNote != SILENT_LOSS) b
            else b.copy(status = BetStatus.PENDING, settledAtMs = null, settleValue = null, settledBy = null, gradeNote = BOTH_LOST_REOPENED, gradeAtMs = now, gradeManual = true)
        } })
        return ids.size
    }

    /**
     * A market held on both sides whose one payout fits more than one side's win (Tj, 2026-10-02 full tests: equal holdings, every lock, pay the same
     * whichever side wins, so Novig's ledger alone can never grade them): every side's cost paid back is a push; otherwise the score feeds say which
     * side won, and it's taken only when Novig's payout is exactly that side's win. Each side's status and the evidence; empty when neither can say.
     */
    private suspend fun bothSides(group: List<TrackedBet>, marketPaid: Double): Pair<Map<String, BetStatus>, String> {
        val sides = group.map { it.outcomeId }.distinct()
        val paidAll = group.sumOf { it.paid ?: (it.stake - (it.fee ?: 0.0)) }
        if (kotlin.math.abs(marketPaid - paidAll) <= TOLERANCE) {
            return sides.associateWith { BetStatus.PUSH } to "Novig paid back the ${money(paidAll)} both sides cost: a push"
        }
        if (sides.size != 2) return emptyMap<String, BetStatus>() to ""
        // The picks first: a lock's own wording is Novig's outcome name, which the feeds read less surely.
        for (bet in group.sortedBy { it.isLock }) {
            val feed = scoreGrade(bet) as? BetGrader.Grade.Result ?: continue
            val winner = when (feed.status) {
                BetStatus.WON -> bet.outcomeId
                BetStatus.LOST -> sides.first { it != bet.outcomeId }
                else -> return emptyMap<String, BetStatus>() to ""
            }
            val win = group.filter { it.outcomeId == winner }.sumOf { (it.contracts ?: 0L) * EvMath.CONTRACT_PAYOUT_DOLLARS }
            if (kotlin.math.abs(marketPaid - win) > TOLERANCE) return emptyMap<String, BetStatus>() to ""
            return sides.associateWith { if (it == winner) BetStatus.WON else BetStatus.LOST } to
                "Novig paid ${money(marketPaid)} for the market (you hold both sides, so that fits either); the score feeds say which won: ${feed.evidence}"
        }
        return emptyMap<String, BetStatus>() to ""
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
        /**
         * The API bets of each market where exactly two outcomes are held, in any amounts (a lock's equal holdings, or a pick with a small hedge on
         * the other side, as Tj's Bhayshul Tuten 53.5: 232 contracts of the Under and 1 of the Over). Such a market is binary, so one side won. A market
         * with a "Draw" or "Tie" side is three-way (two sides can both lose) and is left out; so is a void.
         */
        fun bothSidesHeld(bets: List<TrackedBet>): Map<String, List<TrackedBet>> = bets
            .filter { it.orderId != null && (it.contracts ?: 0L) > 0L && it.marketId.isNotBlank() && it.outcomeId.isNotBlank() && it.status != BetStatus.VOID }
            .groupBy { it.marketId }
            .filterValues { legs -> legs.map { it.outcomeId }.distinct().size == 2 && legs.none { threeWay(it.selection) } }

        /** True when [selection] ends in a half-point line ("Over 29.5", "Ollie Gordon Under 53.5", "Team A -3.5"): nothing on it can push. */
        internal fun halfPointLine(selection: String): Boolean {
            val line = Picks.split(selection).second ?: return false
            val n = Regex("""\d+(?:\.\d+)?""").find(line)?.value?.toDoubleOrNull() ?: return false
            return kotlin.math.abs(n % 1.0 - 0.5) < 1e-9
        }

        private fun threeWay(selection: String): Boolean = Regex("\\b(draw|tie)\\b", RegexOption.IGNORE_CASE).containsMatchIn(selection)

        /** The words of a loss taken from Novig's silence: no payout, no position left. */
        const val SILENT_LOSS = "Novig paid nothing for it and no longer holds the position: a loss"

        /** The words of a win worked out from the other leg: a half-point two-way market held on both sides, the other leg graded lost by a score feed. */
        const val INFERRED_WON = "You hold both sides of this half-point market and a score feed graded the other side lost, so this one won (Novig shows no payout for it yet)"

        const val OTHER_SIDE_LOST = "You hold both sides of this market and the other side lost, so this one can't have: Novig shows no payout for it yet, check it in the Novig app"

        const val BOTH_HELD_SILENT = "You hold both sides of this market, so one of them won: Novig shows no payout for either and the score feeds can't say which, check it in the Novig app"

        const val BOTH_LOST_REOPENED = "Graded lost, but you hold both sides of this market and the other side lost too, which can't be: taken back, check it in the Novig app"

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
