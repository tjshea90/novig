package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Settles tracked bets from each game's final score (Tj, 2026-09-27: "keep track whether each bet
 * was a win or a loss … a background scores system"). v0.15.6 asked Novig's public catalog, which
 * the 2026-09-27 full test found drops a game and its markets once it's over (TASKS.md N7): it
 * never settled anything. Scores now come from free feeds ([ScoreSource]: ESPN, MLB's Stats API),
 * and [BetGrader] reads each bet (Vigilant's wording or CNO's) against the final score or the box
 * score.
 *
 * Only open bets whose game started at least [AFTER_START_MS] ago are looked at, oldest first; one
 * scoreboard read covers every bet of that league and day. A game not over yet is looked at again
 * after [RETRY_MS]; a bet that can't be graded (a market it can't read, a player missing from the
 * box score, a postponed game) after [RETRY_UNGRADABLE_MS], and it can always be tapped. A result
 * tapped in the Tracker ([BetTracker.settle], "you") is never overwritten, and neither is an undo.
 */
class BetSettler(
    private val tracker: BetTracker,
    private val scores: ScoreSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Report(val asked: Int, val settled: Int, val stopped: Boolean)

    private val mutex = Mutex()

    /** When each bet may be looked at again (kept while the app runs). */
    private val nextTry = HashMap<String, Long>()

    /** Bets due a look now. */
    fun due(bets: List<TrackedBet>, now: Long = clock()): List<TrackedBet> = bets
        .filter { it.status == BetStatus.PENDING && it.settledBy != BY_YOU }
        .filter { now - it.startsTs >= AFTER_START_MS && now - it.startsTs <= GIVE_UP_MS }
        .filter { (nextTry[it.id] ?: 0L) <= now }
        .sortedBy { it.startsTs }

    /** One pass: every due bet, oldest first. Runs one pass at a time; a second caller waits for it. */
    suspend fun run(): Report = mutex.withLock {
        var asked = 0
        var settled = 0
        for (bet in due(tracker.all()).take(MAX_PER_RUN)) {
            asked++
            val pick = BetGrader.pickOf(bet)
            if (pick == null) {
                later(bet, RETRY_UNGRADABLE_MS)
                continue
            }
            val found = when (val f = findGame(bet)) {
                Lookup.Unreachable -> return@withLock Report(asked, settled, stopped = true)
                Lookup.NotFound -> { later(bet, RETRY_UNGRADABLE_MS); continue }
                is Lookup.Found -> f.game
            }
            if (found.called) { later(bet, RETRY_UNGRADABLE_MS); continue }
            if (!found.final) { later(bet, RETRY_MS); continue }
            val players = if (pick is BetGrader.Pick.Prop) {
                scores.players(found) ?: return@withLock Report(asked, settled, stopped = true)
            } else {
                null
            }
            val status = BetGrader.grade(pick, found, players)
            if (status == null) { later(bet, RETRY_UNGRADABLE_MS); continue }
            val now = clock()
            tracker.edit(bet.id) {
                // Tapped (or undone) while this pass ran: the tap wins.
                if (it.status != BetStatus.PENDING || it.settledBy == BY_YOU) it
                else it.copy(status = status, settledAtMs = now, settleValue = null, settledBy = BY_SCORES)
            }
            nextTry.remove(bet.id)
            settled++
        }
        Report(asked, settled, stopped = false)
    }

    private fun later(bet: TrackedBet, afterMs: Long) {
        nextTry[bet.id] = clock() + afterMs
    }

    private sealed interface Lookup {
        data class Found(val game: GameScore) : Lookup
        data object NotFound : Lookup
        data object Unreachable : Lookup
    }

    /**
     * The bet's game in its league's scoreboard for its Eastern date (and the days either side, for
     * a late listing). A bet with no league (imported from an old ✓ mark) is looked for in every
     * league the feeds cover.
     */
    private suspend fun findGame(bet: TrackedBet): Lookup {
        val leagues = bet.league.trim().uppercase().takeIf { it.isNotEmpty() }?.let { l -> listOf(l).filter(scores::covers) } ?: ALL_LEAGUES
        if (leagues.isEmpty()) return Lookup.NotFound
        val day = FreeScores.etDate(bet.startsTs)
        var readAny = false
        for (date in listOf(day, day.minusDays(1), day.plusDays(1))) {
            for (league in leagues) {
                val games = scores.games(league, date) ?: continue
                readAny = true
                BetGrader.gameOf(bet, games)?.let { return Lookup.Found(it) }
            }
            // The game's own day answered and it isn't there: the next days only for a late listing.
            if (!readAny) return Lookup.Unreachable
        }
        return Lookup.NotFound
    }

    companion object {
        /** Settled from Novig's catalog (v0.15.6's way; kept so old files read the same). */
        const val BY_NOVIG = "novig"

        /** Settled from the game's final score or box score. */
        const val BY_SCORES = "scores"

        /** Tapped in the Tracker. */
        const val BY_YOU = "you"

        /** Looked at only once the game is at least this old. */
        const val AFTER_START_MS = 60 * 60_000L

        /** A game not over yet is looked at again after this. */
        const val RETRY_MS = 30 * 60_000L

        /** A bet that can't be graded yet (missing player line, postponed game, unreadable market). */
        const val RETRY_UNGRADABLE_MS = 6 * 60 * 60_000L

        /** Bets older than this are left to a tap. */
        const val GIVE_UP_MS = 30L * 24 * 60 * 60_000L

        /** Bets per pass at most; the rest go next pass. */
        const val MAX_PER_RUN = 200

        /** Where a bet with no league is looked for, most likely first. */
        val ALL_LEAGUES = listOf("NFL", "NCAAF", "MLB", "WNBA", "NBA", "NHL", "NCAAB")
    }
}
