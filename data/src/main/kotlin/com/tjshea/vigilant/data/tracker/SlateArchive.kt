package com.tjshea.vigilant.data.tracker

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * The daily numbers of game days whose bets the phone no longer keeps (DG6, Tj 2026-11-16: "the app doesn't need to save each bet, only the daily numbers for the stats, charts, and daily and total
 * profit and loss ... the large amount of bets from the past should only need to be downloaded one time ... cached permanently, so only future data needs to be downloaded").
 *
 * [record] is called with a day's FINISHED figures, computed from Novig's money, just before that day's bets are dropped from the phone; it REPLACES the day (so doing it twice never counts it twice).
 * [SlateProfit.book] adds [days] for every date with no bet left on the phone. A day is only recorded when every bet of that date is going, so a date is never half here and half there.
 */
object SlateArchive {
    @Serializable
    data class DaySummary(
        val date: String, val firstStartMs: Long, val endMs: Long, val profit: Double, val staked: Double,
        val settled: Int, val won: Int, val lost: Int, val settledStaked: Double,
    ) {
        fun toDay() = SlateProfit.Day(LocalDate.parse(date), firstStartMs, endMs, profit, staked, settled, 0, won, lost, settledStaked)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var file: File? = null
    @Volatile var days: List<SlateProfit.Day> = emptyList()
        private set

    /** Where the summaries live; loads what was saved. */
    @Synchronized
    fun attach(f: File) {
        file = f
        days = runCatching { json.decodeFromString(ListSerializer(DaySummary.serializer()), f.readText()).map { it.toDay() } }.getOrDefault(emptyList())
    }

    /** Replaces (or adds) the days of [fresh], oldest first, saved before this returns. */
    @Synchronized
    fun record(fresh: List<SlateProfit.Day>) {
        if (fresh.isEmpty()) return
        val merged = (days.filter { d -> fresh.none { it.date == d.date } } + fresh).sortedBy { it.date }
        file?.let { f ->
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(DaySummary.serializer()), merged.map { DaySummary(it.date.toString(), it.firstStartMs, it.endMs, it.profit, it.staked, it.settled, it.won, it.lost, it.settledStaked) }))
            if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
        }
        days = merged
    }

    /** For tests. */
    @Synchronized fun reset() { file = null; days = emptyList() }
}
