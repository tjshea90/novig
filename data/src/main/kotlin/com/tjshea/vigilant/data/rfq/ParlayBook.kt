package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The parlay hub's data (MK15, Tj 2026-11-19: "a parlay hub, where i can see resting parlays and their legs plus if any legs are already won ..."). Two small files:
 *  - [FillRecord]s: what the engine remembers of each quote that was FILLED (its legs: what they are, which game, when it starts, the market to read the result from, our fair), written when the fill
 *    happens and carried from job to job in `rfq-fills.json` on `cloud-feed`, because Novig's executions list names only the outcome ids and a settled market leaves its catalog;
 *  - the [ParlayBook] (`parlays.json`): every parlay the maker holds or held, open and settled, with each leg's result as far as Novig has graded it. The phone reads it and [ParlayHub] words it.
 * Pure data and codecs; nothing here talks to Novig.
 */
@Serializable
data class FillLeg(
    val outcomeId: String,
    val marketId: String = "",
    val label: String = "",
    /** The event as Novig names it, "Away @ Home". */
    val desc: String = "",
    val league: String = "",
    val type: String = "",
    val startsMs: Long = 0L,
    /** Our fair probability that the leg wins, at quote time. */
    val p: Double = 0.0,
    /** A result known from outside Novig's catalog (the seed's legs, graded from final scores): WIN, LOSS or PUSH for the buyer. */
    val result: String? = null,
)

@Serializable
data class FillRecord(
    val rfqId: String,
    val quotedMs: Long = 0L,
    val price: Double = 0.0,
    /** Our fair probability that the whole parlay wins (the buyer's side), at quote time. */
    val fair: Double = 0.0,
    val wager: Double = 0.0,
    val legs: List<FillLeg> = emptyList(),
)

object FillBook {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(fills: Collection<FillRecord>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FillRecord.serializer()), fills.toList())

    /** Never throws: an unreadable file is an empty book. */
    fun decode(text: String?): List<FillRecord> =
        runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(FillRecord.serializer()), text.orEmpty()) }.getOrDefault(emptyList())

    /** [add] over [have], by rfq id: a record with legs is never replaced by one without; two with legs keep the older (the quote's own moment). */
    fun merge(have: Collection<FillRecord>, add: Collection<FillRecord>): List<FillRecord> {
        val m = LinkedHashMap<String, FillRecord>()
        for (r in have) m[r.rfqId] = r
        for (r in add) {
            val old = m[r.rfqId]
            m[r.rfqId] = if (old == null || old.legs.isEmpty()) r else old
        }
        return m.values.toList()
    }
}

/** One leg of a parlay the maker holds: [result] is the BUYER's leg result (WIN, LOSS, PUSH) once Novig has graded it, else null. */
@Serializable
data class BookLeg(
    val outcomeId: String = "",
    val label: String = "",
    val desc: String = "",
    val league: String = "",
    val type: String = "",
    val startsMs: Long = 0L,
    val p: Double = 0.0,
    val result: String? = null,
)

/** One parlay the maker filled. Money in dollars; [result] is the buyer's result of the whole parlay (win, loss, push, fmv) once Novig settled it. */
@Serializable
data class BookParlay(
    val rfqId: String,
    val filledMs: Long = 0L,
    /** The price we quoted: the probability the buyer paid for. */
    val price: Double = 0.0,
    /** The buyer's stake. */
    val wager: Double = 0.0,
    /** Our collateral for it: what we pay if the buyer wins (the stake comes back to the buyer on top). */
    val collateral: Double = 0.0,
    /** Our fair probability that the buyer's parlay wins, when the quote's record is known. */
    val fair: Double? = null,
    val settled: Boolean = false,
    val result: String? = null,
    val fmv: Double? = null,
    val settledMs: Long? = null,
    val nLegs: Int = 0,
    val legs: List<BookLeg> = emptyList(),
)

@Serializable
data class ParlayBook(
    val atMs: Long = 0L,
    /** The maker's mode when the file was written (OFF, SHADOW, LIVE) and whether the ledger read behind it succeeded. */
    val mode: String = "",
    val ledgerAtMs: Long = 0L,
    val parlays: List<BookParlay> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun encode(b: ParlayBook): String = json.encodeToString(serializer(), b)
        fun parse(text: String?): ParlayBook? = runCatching { json.decodeFromString(serializer(), text.orEmpty()) }.getOrNull()

        /**
         * The book from the ledger ([execs], Novig's own list of the maker's fills), the legs the engine kept for each ([fills]), the legs' results as Novig grades them ([legResult]: outcome id to WIN, LOSS or
         * PUSH, null = not graded or no longer listed) and the previous book (results already known are kept, because a graded market leaves the catalog; parlays older than the ledger page are kept as they were).
         */
        fun build(
            execs: List<RfqLedger.Exec>,
            fills: Map<String, FillRecord>,
            legResult: (String) -> String?,
            previous: ParlayBook?,
            now: Long,
            mode: String,
            keepSettledForMs: Long = 90L * 86_400_000L,
        ): ParlayBook {
            val before = previous?.parlays.orEmpty().associateBy { it.rfqId }
            val out = LinkedHashMap<String, BookParlay>()
            for (e in execs) {
                val rec = fills[e.rfqId]
                val old = before[e.rfqId]
                val ids = if (e.outcomeIds.isNotEmpty()) e.outcomeIds else rec?.legs?.map { it.outcomeId }.orEmpty()
                val legs = ids.mapIndexed { i, id ->
                    val f = rec?.legs?.firstOrNull { it.outcomeId == id }
                    val o = old?.legs?.firstOrNull { it.outcomeId == id }
                    val r = legResult(id) ?: o?.result ?: f?.result
                    BookLeg(
                        outcomeId = id, label = f?.label?.ifBlank { null } ?: o?.label?.ifBlank { null } ?: "Leg ${i + 1}", desc = f?.desc ?: o?.desc.orEmpty(), league = f?.league ?: o?.league.orEmpty(),
                        type = f?.type ?: o?.type.orEmpty(), startsMs = f?.startsMs?.takeIf { it > 0L } ?: o?.startsMs ?: 0L, p = f?.p?.takeIf { it > 0.0 } ?: o?.p ?: 0.0, result = r,
                    )
                }
                out[e.rfqId] = BookParlay(
                    rfqId = e.rfqId, filledMs = e.ts, price = e.price, wager = e.userCost, collateral = e.cost, fair = rec?.fair?.takeIf { it > 0.0 } ?: old?.fair,
                    settled = e.settled, result = e.result, fmv = e.fmv, settledMs = e.settledTs, nLegs = ids.size, legs = legs,
                )
            }
            // Parlays the ledger page no longer lists stay as they were (a settled one is final), until they are old.
            for ((id, p) in before) if (id !in out && (!p.settled || now - (p.settledMs ?: p.filledMs) < keepSettledForMs)) out[id] = p
            return ParlayBook(atMs = now, mode = mode, ledgerAtMs = now, parlays = out.values.sortedByDescending { it.filledMs })
        }

        /**
         * Legs of ANY parlay in [book] (settled ones too: a settled parlay's legs still show their marks) that have no result and whose game began over 3 hours ago: what the score feeds can grade. Unlike [legsToGrade] this does
         * not need the leg's market id (a leg whose fair is gone has none), only its description, league and label (MK29b, Tj 2026-11-20: "It still is doing a poor job at grading parlay legs when the games are final").
         */
        fun legsToScore(book: ParlayBook?, now: Long): List<FillLeg> =
            book?.parlays.orEmpty().flatMap { p -> p.legs }
                .filter { it.result == null && it.startsMs > 0L && now - it.startsMs >= ParlayLegScores.MIN_AGE_MS && it.desc.isNotBlank() }
                .distinctBy { it.outcomeId }
                .map { FillLeg(outcomeId = it.outcomeId, label = it.label, desc = it.desc, league = it.league, type = it.type, startsMs = it.startsMs, p = it.p) }

        /** [book] with [results] (outcome id to WIN/LOSS/PUSH) laid on every leg that has none: the phone's own grading from final scores when the engine is not running. */
        fun withLegResults(book: ParlayBook, results: Map<String, String>): ParlayBook =
            if (results.isEmpty()) book else book.copy(parlays = book.parlays.map { p -> p.copy(legs = p.legs.map { l -> if (l.result == null) results[l.outcomeId]?.let { l.copy(result = it) } ?: l else l }) })

        /** The open parlays' outcome ids whose result is still wanted and whose game has begun: what the engine asks Novig's catalog about. */
        fun legsToGrade(book: ParlayBook?, fills: Map<String, FillRecord>, now: Long): List<FillLeg> {
            val out = ArrayList<FillLeg>()
            for (p in book?.parlays.orEmpty()) {
                if (p.settled) continue
                val rec = fills[p.rfqId] ?: continue
                for (l in rec.legs) {
                    if (l.marketId.isBlank() || l.startsMs <= 0L || l.startsMs > now) continue
                    if (p.legs.firstOrNull { it.outcomeId == l.outcomeId }?.result != null) continue
                    out += l
                }
            }
            return out.distinctBy { it.outcomeId }
        }

        /** Novig's outcome status as a leg result: WIN, LOSS, PUSH; a decimal (a fair-market-value void) is a PUSH for the leg; TBD and anything else is not graded. */
        fun gradeOf(status: String?): String? = when (val s = status?.trim()?.uppercase()) {
            "WIN", "LOSS", "PUSH" -> s
            null, "", "TBD", "OPEN" -> null
            else -> if (s.toDoubleOrNull() != null) "PUSH" else null
        }
    }
}
