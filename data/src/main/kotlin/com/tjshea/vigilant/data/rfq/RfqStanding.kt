package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The maker's standing as Novig reports it (`GET /rfq/v3/maker`, docs api/rfq/confirm-rate and api-reference/rfq/maker, MK13): [tier] (`basic` during the beta), the confirm rate it is judged by
 * ([counted] last looks in the last [lookbackMinutes], [confirmed] of them confirmed, against [threshold] once at least [minSample] were counted) and any [lock] on quoting with its [lockSource]
 * (`confirm_rate`, or `admin` by hand, which can last longer) and when it ends ([lockUntilMs]). Reading it replaces guessing the lock's length (the maker used to assume 5 minutes) and shows a missed
 * last look (our own wins are always confirmed: a rate under 100% means an answer came late or never) before the 99% bar locks the maker.
 */
data class RfqStanding(
    val tier: String?, val counted: Long, val confirmed: Long, val threshold: Double?, val minSample: Int, val lookbackMinutes: Int,
    val lockSource: String?, val lockUntilMs: Long?,
) {
    val locked: Boolean get() = lockSource != null
    /** Confirmed over counted, or null with nothing counted. */
    val rate: Double? get() = if (counted > 0) confirmed.toDouble() / counted else null

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The documented `MakerResponse`; null when the answer is not that object. */
        fun parse(text: String?): RfqStanding? {
            val o = runCatching { json.parseToJsonElement(text ?: return null) as? JsonObject }.getOrNull() ?: return null
            fun obj(k: String) = o[k] as? JsonObject
            fun str(x: JsonObject?, k: String) = (x?.get(k) as? JsonPrimitive)?.contentOrNull
            val cs = obj("confirmStanding") ?: return null
            fun long(k: String) = (cs[k] as? JsonPrimitive)?.longOrNull ?: 0L
            val lock = obj("lock")
            return RfqStanding(
                tier = str(o, "tier"), counted = long("counted"), confirmed = long("confirmed"), threshold = (cs["threshold"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() },
                minSample = long("minSample").toInt(), lookbackMinutes = long("lookbackMinutes").toInt(),
                lockSource = lock?.let { str(it, "source") ?: "unknown" }, lockUntilMs = (lock?.get("untilTs") as? JsonPrimitive)?.longOrNull,
            )
        }
    }
}
