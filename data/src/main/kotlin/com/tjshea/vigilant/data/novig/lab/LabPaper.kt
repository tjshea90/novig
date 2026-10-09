package com.tjshea.vigilant.data.novig.lab

import kotlinx.serialization.Serializable
import java.util.Locale

/** What kind of would-be bet a [LabRecord] is. */
object LabKind {
    const val TAIL = "TAIL"
    const val ALT = "ALT"
    const val COVER = "COVER"
}

/**
 * One paper bet of the lab recorder (RESEARCH.md §120.6): what it would have bought, at what price, and why. [fair] is the probability the bet wins by the scan's own (conservative) reckoning;
 * [books] is how many outside books stood behind it (alternate lines; 0 for the tail model). A cover has two legs: [ask] is the cost of both, [fair] 1.0, and its [outcomeId] the lower line's YES leg.
 */
@Serializable
data class LabRecord(
    val id: String,
    val atMs: Long,
    val kind: String,
    val eventId: String,
    val event: String,
    val league: String,
    val marketId: String,
    val outcomeId: String,
    val label: String,
    val side: String,
    val strike: Double,
    val ask: Double,
    val fair: Double,
    val edge: Double,
    val contracts: Long,
    val books: Int = 0,
    val note: String = "",
)

/** The result of one record, read from Novig's own settled market: [result] is WIN, LOSS or PUSH, or a decimal payout per $1 contract for a fair-market-value void. */
@Serializable
data class LabGrade(val id: String, val atMs: Long, val result: String)

object LabPaper {
    /** The payout per $1 contract of an outcome's settled status, or null while it is not settled. */
    fun payout(status: String?): Double? = when (status?.trim()?.uppercase(Locale.US)) {
        null, "", "TBD" -> null
        "WIN" -> 1.0
        "LOSS" -> 0.0
        "PUSH" -> null   // stake back: handled by the caller as a zero-profit result
        else -> status.trim().toDoubleOrNull()
    }

    /** Profit per $1 of stake of a [r] that settled as [result]: a win pays (1/ask - 1), a loss -1, a push 0. */
    fun profitPerDollar(r: LabRecord, result: String): Double? = when (result.trim().uppercase(Locale.US)) {
        "WIN" -> 1.0 / r.ask - 1.0
        "LOSS" -> -1.0
        "PUSH" -> 0.0
        else -> result.trim().toDoubleOrNull()?.let { it / r.ask - 1.0 }
    }

    /** Diagnostics: per kind, how many would-be bets, their listed edge, and the graded W-L with ROI at $1 a bet and what the edge said it should be. */
    fun report(records: List<LabRecord>, grades: List<LabGrade>): List<String> {
        if (records.isEmpty()) return listOf("no would-be bets recorded yet")
        val gradeOf = grades.associateBy { it.id }
        return listOf(LabKind.TAIL, LabKind.ALT, LabKind.COVER).mapNotNull { kind ->
            val rs = records.filter { it.kind == kind }
            if (rs.isEmpty()) return@mapNotNull null
            val graded = rs.mapNotNull { r -> gradeOf[r.id]?.let { r to it } }
            val wins = graded.count { it.second.result.equals("WIN", true) }
            val losses = graded.count { it.second.result.equals("LOSS", true) }
            val profits = graded.mapNotNull { (r, g) -> profitPerDollar(r, g.result) }
            val roi = if (profits.isEmpty()) "no result yet" else "ROI ${"%+.1f".format(Locale.US, 100 * profits.average())}% at $1 a bet on ${profits.size} graded (they listed ${"%+.1f".format(Locale.US, 100 * graded.map { it.first.edge }.average())}%)"
            "$kind: ${rs.size} would-be bet${if (rs.size == 1) "" else "s"} on ${rs.map { it.eventId }.distinct().size} game${if (rs.map { it.eventId }.distinct().size == 1) "" else "s"}, listed edge median ${"%+.1f".format(Locale.US, 100 * rs.map { it.edge }.sorted()[rs.size / 2])}%, $wins-$losses graded, $roi"
        }
    }
}
