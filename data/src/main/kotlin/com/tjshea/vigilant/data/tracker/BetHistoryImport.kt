package com.tjshea.vigilant.data.tracker

/**
 * Brings the Tracker's history back from the `phone-data` backup (Tj, 2026-10-28: the old phone was lost; Novig's fills only restore open bets, because
 * a settled market is no longer listed). The backup files are [BetHistory.Row] lines, one per bet or bid fill, written by [BetArchive].
 *
 * Only SETTLED rows are brought back (an open bet is the Novig sync's job), and a row the Tracker already has is skipped: same id, or the same outcome
 * bought within [SAME_MS] of the same time for the same contracts. Nothing is ever overwritten or deleted. Fair-odds detail the row did not keep
 * (every book's price) stays empty; the bet, its price, stake, result, EV, fair odds and closing line come back.
 */
object BetHistoryImport {
    const val SAME_MS = 10 * 60_000L

    fun parse(text: String): List<BetHistory.Row> =
        text.lineSequence().filter { it.isNotBlank() }.mapNotNull { runCatching { BetHistory.json.decodeFromString<BetHistory.Row>(it) }.getOrNull() }.toList()

    private fun status(result: String): BetStatus? = when (result.lowercase()) {
        "won" -> BetStatus.WON
        "lost" -> BetStatus.LOST
        "push" -> BetStatus.PUSH
        "void" -> BetStatus.VOID
        "fmv" -> BetStatus.FMV
        else -> null
    }

    fun toTracked(r: BetHistory.Row): TrackedBet? {
        val st = status(r.result) ?: return null
        if (r.cost <= 0.0 || r.stake < 0.0) return null
        val cno = r.scanner.contains("cno", ignoreCase = true) || r.scanner.contains("crazy", ignoreCase = true)
        return TrackedBet(
            id = r.id, createdAtMs = r.placedMs, league = r.league, eventName = r.event, startsTs = r.startsMs, marketLabel = r.market, selection = r.selection,
            marketId = r.marketId.orEmpty(), outcomeId = r.outcomeId.orEmpty(), price = r.cost, cost = r.cost, fairAtBet = r.fair, evPercentAtBet = r.ev, stake = r.stake,
            status = st, settledAtMs = r.settledMs ?: r.startsMs, closeFair = r.closeFair, closeFinal = r.closeFair != null,
            source = if (cno) BetTracker.SOURCE_CNO else BetTracker.SOURCE_VIGILANT, american = r.odds,
            contracts = r.contracts, paid = r.paid, fee = r.fee, settledBy = r.settledBy, imported = true,
            gradeNote = r.gradeNote ?: "Restored from the GitHub backup", maker = r.made == "bid", lockFor = r.lockFor,
            settleValue = settleValueOf(r),
        )
    }

    /**
     * A fair-market-value settlement pays [BetStatus.FMV] `stake * (settleValue / cost - 1)`: the value per $1 contract that makes the recorded profit come out. Without it every FMV bet counts as zero profit
     * (found 2026-10-29: 107 imported FMV bets hid +$132 of profit).
     */
    fun settleValueOf(r: BetHistory.Row): Double? =
        if (status(r.result) == BetStatus.FMV && r.profit != null && r.stake > 0.0 && r.cost > 0.0) r.cost * (1.0 + r.profit / r.stake) else null

    /** Settle values for FMV bets the Tracker already holds without one, from [rows] (matched by id). */
    fun fmvRepairs(rows: List<BetHistory.Row>, have: List<TrackedBet>): Map<String, Double> {
        val missing = have.filter { it.status == BetStatus.FMV && it.settleValue == null }.associateBy { it.id.take(8) }
        if (missing.isEmpty()) return emptyMap()
        val out = HashMap<String, Double>()
        for (r in rows) {
            val b = missing[r.id.take(8)] ?: continue
            settleValueOf(r)?.let { out[b.id] = it }
        }
        return out
    }

    /** The bets [rows] would add to a Tracker that holds [have]; each at most once. */
    fun missing(rows: List<BetHistory.Row>, have: List<TrackedBet>): List<TrackedBet> {
        val ids = have.mapTo(HashSet()) { it.id.take(8) }
        val byOutcome = have.filter { it.outcomeId.isNotBlank() }.groupBy { it.outcomeId }
        val out = ArrayList<TrackedBet>()
        for (r in rows.sortedBy { it.placedMs }) {
            val b = toTracked(r) ?: continue
            if (!ids.add(r.id.take(8))) continue
            val dup = byOutcome[b.outcomeId].orEmpty().any { kotlin.math.abs(it.createdAtMs - b.createdAtMs) < SAME_MS && (it.contracts == null || b.contracts == null || it.contracts == b.contracts) }
            if (dup && b.outcomeId.isNotBlank()) continue
            out += b
        }
        return out
    }
}
