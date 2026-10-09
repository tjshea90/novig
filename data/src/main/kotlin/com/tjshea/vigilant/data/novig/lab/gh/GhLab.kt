package com.tjshea.vigilant.data.novig.lab.gh

import com.tjshea.vigilant.data.novig.lab.BidLab
import com.tjshea.vigilant.data.novig.lab.LabLine
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.reference.SgoBooks
import com.tjshea.vigilant.data.reference.SgoConvert
import com.tjshea.vigilant.data.reference.SgoGamesSource
import com.tjshea.vigilant.data.reference.SgoOdd
import com.tjshea.vigilant.data.reference.SportsGameOddsClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Opportunity
import kotlin.math.abs

/** A scanned Novig outcome as a paper-bid line (the GitHub lab's counterpart of the phone's `labLineOf(MakerLine)`): null when it has no usable fair or nothing is on offer. */
fun labLineOf(op: Opportunity, now: Long, liveMaxAgeSec: Int = 90): LabLine? {
    val fair = op.fairProbability ?: return null
    val line = op.fair ?: return null
    if (op.fairIsOld(now) || line.booksUsed.size < 2) return null
    val offer = op.quote?.price ?: return null
    val age = op.fairAsOfMs?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: return null
    // A live fair goes old in seconds: the live recipes only get one SGO saw a minute or so ago.
    if (op.isLive && age > liveMaxAgeSec) return null
    return LabLine(op.outcome.outcomeId, op.market.marketId, op.event.eventId, op.eventName, op.league.novigName, op.kind.name, op.selection, op.event.startsTs, op.isLive, fair, offer, op.bestBid, line.booksUsed.size, age)
}

/** Writes an [EdgeRow] when an outcome's fair or ask moved enough (or [REPEAT_MS] passed), so the log is the changes, not every scan. */
class EdgeLog(private val journal: DayJournal<EdgeRow>) {
    private val last = HashMap<String, EdgeRow>()
    @Volatile var written = 0L
        private set

    fun observe(ops: List<Opportunity>, now: Long): Int {
        val out = ArrayList<EdgeRow>()
        for (op in ops) {
            val fair = op.fairProbability ?: continue
            val ask = op.quote?.price
            val age = op.fairAsOfMs?.let { ((now - it) / 1000L).toInt().coerceAtLeast(0) } ?: -1
            val row = EdgeRow(now, op.league.novigName, op.event.eventId, op.eventName, op.event.startsTs, op.isLive, op.kind.name, op.selection, op.market.marketId, op.outcome.outcomeId, fair, ask, op.bestBid, op.quote?.evPercent, op.fair?.booksUsed?.size ?: 0, age)
            val prev = last[op.key]
            val moved = prev == null || abs(fair - prev.fair) >= FAIR_STEP || (ask ?: -1.0).let { a -> (prev.ask ?: -1.0).let { b -> abs(a - b) >= ASK_STEP } } || now - prev.atMs >= REPEAT_MS
            if (!moved) continue
            last[op.key] = row
            out += row
        }
        journal.appendAll(out)
        written += out.size
        return out.size
    }

    companion object {
        const val FAIR_STEP = 0.002
        const val ASK_STEP = 0.005
        const val REPEAT_MS = 10 * 60_000L
    }
}

/**
 * The SGO TAPE: every cycle, SportsGameOdds' main game lines (moneyline, spread, total; no alternates, so the read is small and fast) for the lab's leagues, written when a book's price or its update time
 * changes. It is the measurement of what the Pro subscription really delivers: how often each book refreshes, how old a price is when read, and (against the EDGE LOG) how quickly Novig follows.
 */
class SgoTape(private val client: SportsGameOddsClient, private val journal: DayJournal<SgoTick>, private val closes: DayJournal<SgoCloseRow>) {
    private val last = HashMap<String, SgoTick>()
    private val closed = HashSet<String>()

    /** Per book: reads, price changes, the age of the price at each read (seconds). */
    class BookStats { var reads = 0L; var changes = 0L; var ageSum = 0L; var ageMax = 0L; var intervalSum = 0L; var intervals = 0L }
    val books = java.util.concurrent.ConcurrentHashMap<String, BookStats>()
    @Volatile var ticks = 0L
        private set

    /** Loads what earlier runs already wrote, so a restart does not write a close twice. */
    fun resume(existingCloses: List<SgoCloseRow>) { existingCloses.forEach { closed += "${it.eventId}|${it.oddId}|${it.book}" } }

    suspend fun cycle(leagues: Collection<String>, now: Long): Int {
        var n = 0
        for (name in leagues) {
            val league = Leagues.byNovigName(name) ?: continue
            val id = SgoBooks.leagueId(league) ?: continue
            val ids = SgoGamesSource.oddIds(setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL), SgoConvert.Sport.of(id))
            val events = runCatching { client.eventsAll(listOf("leagueID" to id, "oddsAvailable" to "true", "limit" to SportsGameOddsClient.PAGE.toString(), "oddID" to ids.joinToString(",")), maxPages = 3).events }.getOrNull() ?: continue
            val out = ArrayList<SgoTick>()
            for (e in events) for (o in e.odds) for ((book, lines) in o.byBook) {
                val l = lines.firstOrNull() ?: continue
                val st = books.getOrPut(book) { BookStats() }
                val age = l.updatedMs?.let { ((now - it) / 1000L).coerceAtLeast(0) }
                st.reads++
                if (age != null) { st.ageSum += age; if (age > st.ageMax) st.ageMax = age }
                val tick = SgoTick(now, name, e.eventId, e.live, o.oddId, book, l.american, l.point, l.updatedMs, l.available)
                val key = "${e.eventId}|${o.oddId}|$book"
                val prev = last[key]
                if (prev != null && prev.odds == tick.odds && prev.point == tick.point && prev.updatedMs == tick.updatedMs && prev.available == tick.available) continue
                if (prev?.updatedMs != null && tick.updatedMs != null && tick.updatedMs > prev.updatedMs) { st.intervalSum += (tick.updatedMs - prev.updatedMs) / 1000L; st.intervals++ }
                if (prev == null || prev.odds != tick.odds || prev.point != tick.point) st.changes++
                last[key] = tick
                out += tick
            }
            journal.appendAll(out)
            n += out.size
        }
        ticks += n
        return n
    }

    /** Closing prices of the games that started in the last [lookbackMs]: once per event, book and market. Returns the rows written. */
    suspend fun closes(leagues: Collection<String>, now: Long, lookbackMs: Long = 30 * 3_600_000L): Int {
        var n = 0
        for (name in leagues) {
            val league = Leagues.byNovigName(name) ?: continue
            val id = SgoBooks.leagueId(league) ?: continue
            val ids = SgoGamesSource.oddIds(setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD, MarketFamily.TOTAL), SgoConvert.Sport.of(id))
            val events = runCatching {
                client.eventsAll(listOf("leagueID" to id, "startsAfter" to (now - lookbackMs).toString(), "startsBefore" to now.toString(), "includeOpenCloseOdds" to "true", "limit" to SportsGameOddsClient.PAGE.toString(), "oddID" to ids.joinToString(",")), maxPages = 4).events
            }.getOrNull() ?: continue
            val out = ArrayList<SgoCloseRow>()
            for (e in events) {
                if (!e.started && !e.ended && !e.finalized) continue
                for (o in e.odds) for ((book, oc) in o.openClose) {
                    if (book in SgoBooks.EXCLUDED) continue
                    val key = "${e.eventId}|${o.oddId}|$book"
                    if (!closed.add(key)) continue
                    out += SgoCloseRow(now, name, e.eventId, e.home, e.away, e.startsMs, o.oddId, book, oc.openOdds, oc.closeOdds, oc.openPoint, oc.closePoint, o.score)
                }
            }
            closes.appendAll(out)
            n += out.size
        }
        return n
    }

    /** Lines for the STATUS section: per book, how fresh SGO's prices were when read and how often they refresh. */
    fun report(): List<String> = buildList {
        add("SGO tape: $ticks price changes written")
        books.entries.sortedByDescending { it.value.reads }.take(25).forEach { (b, s) ->
            val mean = if (s.reads == 0L) 0 else s.ageSum / s.reads
            val every = if (s.intervals == 0L) "n/a" else "${s.intervalSum / s.intervals}s"
            add("  $b: ${s.reads} reads, ${s.changes} price changes, price ${mean}s old when read (max ${s.ageMax}s), refreshes every $every on average")
        }
    }
}

/** A cycle's worth of the scanner's output handed to the paper bid lab and the edge log. */
fun feedLab(ops: List<Opportunity>, bidLab: BidLab, edge: EdgeLog, now: Long): Pair<Int, Int> {
    val lines = ops.mapNotNull { labLineOf(it, now) }
    bidLab.observe(lines, now)
    return lines.size to edge.observe(ops, now)
}
