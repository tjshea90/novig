package com.tjshea.vigilant.data.novig.lab

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One paper bet of the live research records (Tj, 2026-10-20, CH31: "I want to see the paper records and how well they are doing"): a side Live light's shadow tier (the model 75-89% sure) or the
 * score-event record would have bought, at the price and fee it saw. [kind] is the ladder's ("MARGIN" or "TOTAL"), [ref] the margin ladder's reference team, [threshold] its number, [side] YES/NO or OVER/UNDER.
 */
@Serializable
data class PaperRecord(
    val key: String,
    val tier: String,
    val eventId: String,
    val event: String,
    val league: String,
    val home: String,
    val away: String,
    val kind: String,
    val ref: String,
    val threshold: Double,
    val side: String,
    val selection: String,
    val ask: Double,
    val fee: Double,
    val fair: Double,
    val atMs: Long,
    /** "" while open; WIN, LOSS or PUSH once the game is final. */
    val result: String = "",
) {
    val cost: Double get() = ask + fee
    /** Dollars per contract: a win pays 1 less the price and fee, a loss is the price and fee, a push is refunded. */
    val profit: Double get() = when (result) { "WIN" -> 1.0 - ask - fee; "LOSS" -> -(ask + fee); else -> 0.0 }
}

/** What one tier of paper bets has done so far. */
data class PaperTierStats(val tier: String, val open: Int, val wins: Int, val losses: Int, val pushes: Int, val expectedWinRate: Double, val roi: Double) {
    val settled: Int get() = wins + losses
    val winRate: Double get() = if (settled == 0) 0.0 else wins.toDouble() / settled
    val total: Int get() = open + wins + losses + pushes
}

/** Whether the paper tiers bet real money and with what stake (the settings, the wallet read now). [kelly] above 0 sizes a bet as that fraction of Kelly on [bankroll]; otherwise [dollars]. */
data class PaperStake(val shadowReal: Boolean = false, val scoreReal: Boolean = false, val dollars: Double = 0.10, val kelly: Double = 0.0, val bankroll: Double? = null) {
    /** The dollars to stake on a side whose chance is [fair] when one contract costs [cost] (price and fee) and pays 1; null when Kelly finds no edge. */
    fun stakeFor(fair: Double, cost: Double): Double? {
        if (kelly <= 0.0 || bankroll == null || bankroll <= 0.0) return dollars
        val b = 1.0 / cost - 1.0
        if (b <= 0.0) return null
        val f = (fair * (b + 1.0) - 1.0) / b
        return if (f <= 0.0) null else kelly * f * bankroll
    }

    companion object { const val MIN = 0.10; const val MAX = 25.0 }
}

object PaperGrade {
    /** WIN, LOSS or PUSH for [r] once [g] is final; null while the game is not over or its teams cannot be matched. */
    fun result(r: PaperRecord, g: EspnGame): String? {
        if (!g.done) return null
        val yes = r.side == "YES" || r.side == "OVER"
        if (r.kind == "TOTAL") {
            val total = (g.homeScore + g.awayScore).toDouble()
            if (total == r.threshold) return "PUSH"
            return if ((total > r.threshold) == yes) "WIN" else "LOSS"
        }
        val m = g.marginOf(r.ref)?.toDouble() ?: return null
        if (m == r.threshold) return "PUSH"   // a tied moneyline's credit is left out of both sides
        return if ((m > r.threshold) == yes) "WIN" else "LOSS"
    }
}

/** The paper bets, kept in memory and saved through [save] (at most every [SAVE_MS]); [load] gives the saved text back after a restart. */
class PaperBook(private val load: () -> String? = { null }, private val save: (String) -> Unit = {}, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    private val byKey = LinkedHashMap<String, PaperRecord>()
    private var dirty = false
    private var savedAt = 0L

    init {
        runCatching { load()?.let { json.decodeFromString<List<PaperRecord>>(it) } }.getOrNull()?.forEach { byKey[it.key] = it }
    }

    /** Adds [r] unless its tier already holds a bet on that outcome (the first sighting is the bet). */
    @Synchronized
    fun add(r: PaperRecord): Boolean {
        if (byKey.containsKey(r.key)) return false
        byKey[r.key] = r
        if (byKey.size > MAX) byKey.values.firstOrNull { it.result.isNotEmpty() }?.let { byKey.remove(it.key) }
        dirty = true
        return true
    }

    @Synchronized fun all(): List<PaperRecord> = byKey.values.toList()

    @Synchronized fun open(): List<PaperRecord> = byKey.values.filter { it.result.isEmpty() }

    @Synchronized
    fun settle(key: String, result: String) {
        val r = byKey[key] ?: return
        if (r.result.isNotEmpty()) return
        byKey[key] = r.copy(result = result)
        dirty = true
    }

    @Synchronized
    fun stats(): List<PaperTierStats> = byKey.values.groupBy { it.tier }.map { (tier, rs) ->
        val done = rs.filter { it.result == "WIN" || it.result == "LOSS" }
        val cost = done.sumOf { it.cost }
        PaperTierStats(
            tier, open = rs.count { it.result.isEmpty() }, wins = rs.count { it.result == "WIN" }, losses = rs.count { it.result == "LOSS" }, pushes = rs.count { it.result == "PUSH" },
            expectedWinRate = if (done.isEmpty()) 0.0 else done.map { it.fair }.average(), roi = if (cost <= 0.0) 0.0 else done.sumOf { it.profit } / cost,
        )
    }.sortedBy { it.tier }

    @Synchronized
    fun flush(force: Boolean = false) {
        val now = clock()
        if (!dirty || (!force && now - savedAt < SAVE_MS)) return
        runCatching { save(json.encodeToString(byKey.values.toList())) }
        dirty = false
        savedAt = now
    }

    companion object {
        const val SHADOW = "shadow"
        const val SCORE_EVENT = "score-event"
        const val SAVE_MS = 10_000L
        const val MAX = 3000
    }
}
