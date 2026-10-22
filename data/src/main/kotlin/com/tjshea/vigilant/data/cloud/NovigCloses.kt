package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigCandle
import kotlinx.serialization.Serializable

/**
 * Novig's OWN closing price of a side the engine ordered or bid on (AF7, `GET /v3/history/markets/{id}/candles`, changelog 2026-10-20): the last trade before the game's start, in the
 * side's own frame, with the hour's volume behind it. Journaled (`novig-close-<day>`) so the studies can judge the engine's fills against Novig's own close as well as Pinnacle's.
 */
@Serializable
data class NovigClose(
    val atMs: Long,
    val marketId: String,
    val outcomeId: String,
    val eventId: String,
    val league: String,
    val startsTs: Long,
    /** The side's last traded price before the start (null: no trade in the window). */
    val close: Double? = null,
    val closeAtMs: Long? = null,
    /** Contracts traded on the market in the hour before the start. */
    val lastHourVolume: Long = 0,
    /** What the engine paid (its average fill price), for the CLV at Novig: close - paid. */
    val paid: Double? = null,
    val origin: String = "",
)

object NovigCloses {
    /** How long before the start the candles are read (1-minute candles). */
    const val WINDOW_MS = 3 * 3_600_000L

    /** A game is read this long after its start (the history needs a moment) and at most this long after it. */
    const val AFTER_START_MS = 15 * 60_000L
    const val MAX_AFTER_START_MS = 47 * 3_600_000L

    /** The close of [outcomeId] from [series] (outcome -> candles): its own series, or the other outcome's complement when it has none. */
    fun close(series: Map<String, List<NovigCandle>>, outcomeId: String, startsTs: Long): Pair<NovigCandle?, Boolean> {
        val own = series[outcomeId]?.filter { it.t < startsTs }?.maxByOrNull { it.t }
        if (own != null) return own to false
        val other = series.entries.firstOrNull { it.key != outcomeId }?.value?.filter { it.t < startsTs }?.maxByOrNull { it.t } ?: return null to false
        return other.copy(o = other.o?.let { 1 - it }, h = other.l?.let { 1 - it }, l = other.h?.let { 1 - it }, c = 1 - other.c) to true
    }

    fun row(now: Long, e: TradeEntry, series: Map<String, List<NovigCandle>>): NovigClose {
        val (c, _) = close(series, e.outcomeId, e.startsTs)
        val vol = series.values.firstOrNull()?.filter { it.t >= e.startsTs - 3_600_000L && it.t < e.startsTs }?.sumOf { it.volume } ?: 0L
        return NovigClose(now, e.marketId, e.outcomeId, e.eventId, e.league, e.startsTs, c?.c, c?.t, vol, e.avgPrice.takeIf { it > 0.0 }, if (e.note.startsWith("bid")) "bid" else "bet")
    }
}
