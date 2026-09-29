package com.tjshea.vigilant.data.tracker

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * tapped in the Tracker ([BetTracker.settle], "you") is never overwritten, and neither is an undo
 * (until "Grade automatically", [BetTracker.regrade]).
 *
 * Every bet a pass looks at ends with a note on it ([TrackedBet.gradeNote]): what settled it ("Final:
 * Mets 7, Nationals 1"), or exactly why it's still open (2026-09-29, Tj: "some bets are still pending in
 * the open bets tab that are final"): the game isn't over, the market can't be read, the player isn't
 * in the box score. So a bet that stays open never stays open silently.
 */
class BetSettler(
    private val tracker: BetTracker,
    private val scores: ScoreSource,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Bets placed through Novig's API are graded from Novig's own ledger ([ApiSettler], Tj, 2026-09-29), which is exact (fair-value voids
     * included): while this says so, the score feeds leave them alone. Off (betting not set up on this phone): they're graded from scores like any other.
     */
    private val leaveApiBets: () -> Boolean = { false },
) {
    /** [waiting]: games not over yet; [manual]: bets nothing the feeds carry can settle (each has its reason on the bet). */
    data class Report(val asked: Int, val settled: Int, val stopped: Boolean, val waiting: Int = 0, val manual: Int = 0)

    private val mutex = Mutex()

    /** When each bet may be looked at again (kept while the app runs). */
    private val nextTry = HashMap<String, Long>()

    /** Bets due a look now ([force]: every one, whenever it was last looked at). */
    fun due(bets: List<TrackedBet>, now: Long = clock(), force: Boolean = false): List<TrackedBet> = bets
        .filter { it.status == BetStatus.PENDING && it.settledBy != BY_YOU && !(it.orderId != null && leaveApiBets()) }
        .filter { now - it.startsTs >= AFTER_START_MS && now - it.startsTs <= GIVE_UP_MS }
        .filter { force || (nextTry[it.id] ?: 0L) <= now }
        .sortedBy { it.startsTs }

    /**
     * One pass: every due bet, oldest first ([force]: the "Grade now" button: ignore when a bet was last
     * tried). Runs one pass at a time; a second caller waits for it.
     */
    suspend fun run(force: Boolean = false): Report = mutex.withLock {
        val startedAt = clock()
        var asked = 0
        var settled = 0
        var waiting = 0
        var manual = 0
        val changes = LinkedHashMap<String, (TrackedBet) -> TrackedBet>()
        val all = tracker.all()

        // Bets left to a tap for good say so once, instead of sitting open with no word.
        for (bet in all) {
            if (bet.status != BetStatus.PENDING || bet.settledBy == BY_YOU || (bet.orderId != null && leaveApiBets())) continue
            if (startedAt - bet.startsTs > GIVE_UP_MS) note(changes, bet, TOO_OLD, startedAt)
        }

        suspend fun flush() {
            if (changes.isEmpty()) return
            val batch = LinkedHashMap(changes)
            changes.clear()
            // A cancelled pass keeps what it found.
            withContext(NonCancellable) { tracker.editMany(batch) }
        }

        try {
            for (bet in due(all, startedAt, force).take(MAX_PER_RUN)) {
                asked++
                val pick = BetGrader.pickOf(bet)
                if (pick == null) {
                    manual++
                    note(changes, bet, BetGrader.whyNot(bet.marketLabel, bet.selection), startedAt)
                    later(bet, RETRY_UNGRADABLE_MS)
                    continue
                }
                val found = when (val f = findGame(bet)) {
                    Lookup.Unreachable -> return@withLock Report(asked, settled, stopped = true, waiting = waiting, manual = manual)
                    is Lookup.NotFound -> {
                        manual++
                        note(changes, bet, f.reason, startedAt)
                        later(bet, RETRY_UNGRADABLE_MS)
                        continue
                    }
                    is Lookup.Found -> f.game
                }
                if (found.called) {
                    manual++
                    note(changes, bet, "${found.calledReason ?: "Called off"}: the score feeds have no result to grade with. Mark it yourself (Void if Novig refunded it)", startedAt)
                    later(bet, RETRY_UNGRADABLE_MS)
                    continue
                }
                if (!found.final) {
                    waiting++
                    note(changes, bet, "The game isn't over yet", startedAt, manual = false)
                    later(bet, RETRY_MS)
                    continue
                }
                val players = if (pick is BetGrader.Pick.Prop && !found.tennis) {
                    scores.players(found) ?: return@withLock Report(asked, settled, stopped = true, waiting = waiting, manual = manual)
                } else {
                    null
                }
                when (val grade = BetGrader.gradeDetailed(pick, found, players)) {
                    is BetGrader.Grade.Result -> {
                        val now = clock()
                        changes[bet.id] = {
                            // Tapped (or undone) while this pass ran: the tap wins.
                            if (it.status != BetStatus.PENDING || it.settledBy == BY_YOU) it
                            else it.copy(
                                status = grade.status, settledAtMs = now, settleValue = null, settledBy = BY_SCORES,
                                books = emptyList(), gradeNote = grade.evidence, gradeAtMs = now, gradeManual = false,
                            )
                        }
                        nextTry.remove(bet.id)
                        settled++
                    }
                    is BetGrader.Grade.Waiting -> {
                        waiting++
                        note(changes, bet, grade.reason, startedAt, manual = false)
                        later(bet, RETRY_MS)
                    }
                    is BetGrader.Grade.Manual -> {
                        manual++
                        note(changes, bet, grade.reason, startedAt)
                        later(bet, RETRY_UNGRADABLE_MS)
                    }
                }
                if (changes.size >= BATCH) flush()
            }
        } finally {
            flush()
        }
        Report(asked, settled, stopped = false, waiting = waiting, manual = manual)
    }

    /**
     * What the score feeds say about [bet] without touching it (null when they can't say: no readable market, no game, no box score): the
     * cross-check [ApiSettler] holds a ledger-inferred loss against.
     */
    suspend fun scoreGradeOf(bet: TrackedBet): BetGrader.Grade? {
        val pick = BetGrader.pickOf(bet) ?: return null
        val game = (findGame(bet) as? Lookup.Found)?.game ?: return null
        if (game.called) return null
        if (!game.final) return BetGrader.Grade.Waiting("The game isn't over yet")
        val players = if (pick is BetGrader.Pick.Prop && !game.tennis) scores.players(game) ?: return null else null
        return BetGrader.gradeDetailed(pick, game, players)
    }

    /** Puts [text] on [bet] when it's new (or the last look is old), so an unchanged answer never rewrites the file. */
    private fun note(changes: MutableMap<String, (TrackedBet) -> TrackedBet>, bet: TrackedBet, text: String, now: Long, manual: Boolean = true) {
        if (bet.gradeNote == text && bet.gradeManual == manual && bet.gradeAtMs != null && now - bet.gradeAtMs < NOTE_REFRESH_MS) return
        changes[bet.id] = { if (it.status != BetStatus.PENDING || it.settledBy == BY_YOU) it else it.copy(gradeNote = text, gradeAtMs = now, gradeManual = manual) }
    }

    private fun later(bet: TrackedBet, afterMs: Long) {
        nextTry[bet.id] = clock() + afterMs
    }

    private sealed interface Lookup {
        data class Found(val game: GameScore) : Lookup
        data class NotFound(val reason: String) : Lookup
        data object Unreachable : Lookup
    }

    /**
     * The bet's game in its league's scoreboard for its Eastern date (and the days either side, for
     * a late listing). A bet with no league (imported from an old ✓ mark) is looked for in every
     * league the feeds cover.
     */
    private suspend fun findGame(bet: TrackedBet): Lookup {
        val named = bet.league.trim().uppercase().takeIf { it.isNotEmpty() }
        val leagues = named?.let { l -> listOf(l).filter(scores::covers) } ?: ALL_LEAGUES
        if (leagues.isEmpty()) return Lookup.NotFound("No score feed covers ${named ?: bet.league}: mark it yourself")
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
        return Lookup.NotFound("Couldn't find this game on the ${leagues.singleOrNull() ?: "score"} scoreboard for $day: mark it yourself")
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

        /** Notes saved together (one file write) every this many bets. */
        const val BATCH = 20

        /** An unchanged note is re-stamped (so "checked …" stays honest) only this long after the last one. */
        const val NOTE_REFRESH_MS = 30 * 60_000L

        const val TOO_OLD = "Over 30 days old: the score feeds no longer look. Mark it yourself"

        /** Where a bet with no league is looked for, most likely first. */
        val ALL_LEAGUES = listOf("NFL", "NCAAF", "MLB", "WNBA", "NBA", "NHL", "NCAAB", "ATP", "WTA")
    }
}
