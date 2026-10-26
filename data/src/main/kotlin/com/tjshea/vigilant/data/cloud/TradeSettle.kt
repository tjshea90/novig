package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.engine.EvMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The results of the engine's own orders (TCL20e). The daily grader grades every bet the engine's scan study logged; an order of [CloudTrader] is the same Novig outcome, so its result is that bet's result and its
 * profit follows from what the order really cost: a win pays 1¢ a contract, a loss pays nothing, a push or a void gives the cost back, a fair-market settlement pays its value per contract. Pure.
 */
object TradeSettle {
    /** What the grader found for one Novig outcome: its status and, for a fair-market settlement, the value per $1 contract. */
    data class Outcome(val status: BetStatus, val settleValue: Double? = null)

    @Serializable
    data class Settled(val id: String, val status: String, val pnl: Double)

    @Serializable
    data class File(val atMs: Long, val settled: List<Settled>) {
        companion object {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            fun parse(text: String): File? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
            fun text(f: File): String = json.encodeToString(serializer(), f)
        }
    }

    /**
     * The settlements of every entry whose game has a result, given [outcome] for a (market, outcome) pair; entries with no result yet are left out. An entry the engine has already marked is settled again to the same
     * answer: the file is a standing list, so an engine that was off when it was written still finds every result on its next start.
     */
    fun settle(entries: List<TradeEntry>, outcome: (marketId: String, outcomeId: String) -> Outcome?): List<Settled> = entries.mapNotNull { e ->
        val o = outcome(e.marketId, e.outcomeId) ?: return@mapNotNull null
        when (o.status) {
            BetStatus.WON -> Settled(e.id, "WON", round(e.payout - e.cost))
            BetStatus.LOST -> Settled(e.id, "LOST", round(-e.cost))
            BetStatus.PUSH, BetStatus.VOID -> Settled(e.id, o.status.name, 0.0)
            BetStatus.FMV -> o.settleValue?.let { v -> Settled(e.id, "FMV", round(e.contracts * EvMath.CONTRACT_PAYOUT_DOLLARS * v - e.cost)) }
            BetStatus.PENDING -> null
        }
    }

    /**
     * Novig's own word on an outcome ([com.tjshea.vigilant.data.novig.NovigOutcome.status]: TBD, WIN, LOSS, PUSH, or a decimal value per $1 contract for a fair-market settlement) as a result; TBD or a word
     * it does not know is no result yet. PX14 (Tj 2026-10-26: "results for my placed bets and bids with vast majority or 100% of my bets and bids covered"): every order is a Novig outcome, so Novig's
     * settlement covers the orders the scan study never logged (most bid fills: 274 of 1,188 finished orders were still open on 2026-10-26).
     */
    fun fromNovig(status: String?): Outcome? = when (status?.trim()?.uppercase()) {
        null, "", "TBD" -> null
        "WIN", "WON" -> Outcome(BetStatus.WON)
        "LOSS", "LOST", "LOSE" -> Outcome(BetStatus.LOST)
        "PUSH" -> Outcome(BetStatus.PUSH)
        "VOID", "CANCELLED", "CANCELED" -> Outcome(BetStatus.VOID)
        else -> status.trim().toDoubleOrNull()?.takeIf { it in 0.0..1.0 }?.let { Outcome(BetStatus.FMV, it) }
    }

    /** The orders Novig's record is asked for: still open in the ledger, no result in the study, and the game started at least [ASK_AFTER_START_MS] ago. */
    fun askNovig(entries: List<TradeEntry>, studied: (marketId: String, outcomeId: String) -> Boolean, now: Long): List<TradeEntry> =
        entries.filter { it.status == "OPEN" && it.marketId.isNotBlank() && it.outcomeId.isNotBlank() && now - it.startsTs >= ASK_AFTER_START_MS && !studied(it.marketId, it.outcomeId) }

    /** Orders settled on an earlier run from Novig's record (the study has no result for them): kept as the ledger has them, so the standing file never loses one and Novig is not asked again. */
    fun carried(entries: List<TradeEntry>, studied: (marketId: String, outcomeId: String) -> Boolean): List<Settled> =
        entries.filter { it.status in SETTLED && it.pnl != null && !studied(it.marketId, it.outcomeId) }.map { Settled(it.id, it.status, it.pnl!!) }

    /**
     * PX14 (2026-10-26): the orders the study never graded, settled from the subaccount's own ledger, the way the phone's [com.tjshea.vigilant.data.tracker.ApiSettler] grades API bets. The public catalog cannot do it:
     * a settled market leaves it (all 198 markets asked on 2026-10-26 answered 404). [payouts] are the ledger's SETTLEMENT rows (ref = the market, an order or a fill), [positions] what Novig still holds.
     * Per market of open LIVE entries whose game started [ASK_AFTER_START_MS] ago: a payout that is exactly the entries' win is WON, exactly their cost is PUSH, otherwise (one side only) a fair-market value;
     * entries on both sides are told apart only when the payout is exactly one side's win. No payout, nothing held on any of their outcomes, one side only and [INFER_LOSS_AFTER_MS] after the start: LOST (a
     * loss moves no money, so it leaves no row). Anything else waits. Positions not read (null) = no loss is ever inferred.
     */
    fun fromLedger(entries: List<TradeEntry>, payouts: List<com.tjshea.vigilant.data.novig.trading.LedgerRow>, positions: List<com.tjshea.vigilant.data.novig.trading.NovigPosition>?, now: Long): List<Settled> {
        val due = entries.filter { it.mode == "LIVE" && (it.status == "OPEN" || it.status == "UNCONFIRMED") && it.contracts > 0 && it.marketId.isNotBlank() && now - it.startsTs >= ASK_AFTER_START_MS }
        val out = ArrayList<Settled>()
        for ((marketId, group) in due.groupBy { it.marketId }) {
            val refs = group.mapNotNullTo(HashSet()) { it.orderId } + marketId
            val paid = payouts.filter { it.kind == "SETTLEMENT" && it.ref in refs }.sumOf { it.amount }
            val sides = group.groupBy { it.outcomeId }
            fun win(es: List<TradeEntry>) = es.sumOf { it.payout }
            fun near(a: Double, b: Double) = kotlin.math.abs(a - b) <= LEDGER_TOLERANCE
            if (paid > 1e-6) {
                if (sides.size == 1) {
                    val w = win(group)
                    val cost = group.sumOf { it.cost }
                    val status = when {
                        near(paid, w) -> "WON"
                        near(paid, cost) || near(paid, cost - group.sumOf { it.fee }) -> "PUSH"
                        paid < w -> "FMV"
                        else -> null // more than these entries can win: another order of the subaccount shares the market; left to the study
                    } ?: continue
                    for (e in group) out += when (status) {
                        "WON" -> Settled(e.id, "WON", round(e.payout - e.cost))
                        "PUSH" -> Settled(e.id, "PUSH", 0.0)
                        else -> Settled(e.id, "FMV", round(e.payout * (paid / w) - e.cost))
                    }
                } else {
                    val winners = sides.filter { (_, es) -> near(paid, win(es)) }.keys
                    if (winners.size != 1) continue
                    for (e in group) out += if (e.outcomeId in winners) Settled(e.id, "WON", round(e.payout - e.cost)) else Settled(e.id, "LOST", round(-e.cost))
                }
            } else if (positions != null && sides.size == 1 && group.all { now - it.startsTs >= INFER_LOSS_AFTER_MS } &&
                positions.none { p -> p.marketId == marketId && p.qty > 0 }) {
                for (e in group) out += Settled(e.id, "LOST", round(-e.cost))
            }
        }
        return out
    }

    /** With no payout and nothing held, a loss is taken this long after the start ([com.tjshea.vigilant.data.tracker.ApiSettler.INFER_LOSS_AFTER_MS]). */
    const val INFER_LOSS_AFTER_MS = 6 * 3_600_000L
    /** Ledger amounts are 5-decimal dollars: within a hundredth of a cent is the same amount. */
    private const val LEDGER_TOLERANCE = 1e-4

    const val ASK_AFTER_START_MS = 3 * 3_600_000L
    private val SETTLED = setOf("WON", "LOST", "PUSH", "VOID", "FMV")

    private fun round(v: Double) = Math.round(v * 100.0) / 100.0
}
