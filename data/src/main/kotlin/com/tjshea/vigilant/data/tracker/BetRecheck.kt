package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.engine.Odds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * "Check odds now" in the Tracker (Tj, 2026-09-27: "scan for up to date average odds against sports
 * books for each of the bets I made … 'now +3% ev' … or 'now -2% ev'"; 2026-09-29: "it says it check
 * 40 out of 40 open bets, but I have 101 open bets. I want it to check all open bets."): each open
 * CNO bet's game page is read again (every book's price, through [books], which keeps CNO's pace and
 * pauses) and the books' fair probability now is judged against what the bet cost: [TrackedBet.nowEv].
 * Before the game starts it is also the closing line so far ([TrackedBet.closingFair], CLV). The books'
 * prices themselves are kept on the bet ([TrackedBet.books]) for its sheet.
 *
 * There is no cap: every open bet with a CNO page whose game isn't long over is read, soonest game
 * first, [gapMs] apart. The [Report] accounts for every open bet, so the count Tj is told always adds
 * up to the count he has: read, already current, couldn't be read, game already over (its result comes
 * from [BetSettler]), and Vigilant's own bets (no CNO page). Those are priced from Vigilant's own fair
 * odds by [OpenBetPricer] in the same tap (Tj, 2026-09-29: "update the EV for every single open bet,
 * including bets added from vigilant scanner"), and so is any bet CNO couldn't read
 * ([Report.unreadIds]); [Report.withPricing] folds that pass into the counts.
 */
class BetRecheck(
    private val tracker: BetTracker,
    /** The bet's game page on CNO, read now; null when it couldn't be read. */
    private val books: suspend (CnoRow) -> CnoBooksView?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** CNO asked for a pause: no more reads until it's over. */
    private val paused: () -> Boolean = { false },
    /**
     * Pages read at once. CNO's pace, not the wait for each answer, is what limits a pass (a page is two requests and every request
     * waits its turn), so a few at a time finish the hundred open bets in about a minute instead of five.
     */
    private val concurrency: Int = 1,
    /**
     * Every book's price for a bet from elsewhere when CNO doesn't answer, or answers without the bet at its line ([ParlayBooks], Tj
     * 2026-09-30: "if there is a good backup that does the same exact odds check, I think parlayapi can do this same odds check"): judged
     * with CNO's own check. Null: no backup, and a run stops when CNO does.
     */
    private val backup: (suspend (TrackedBet) -> CnoBooksView?)? = null,
) {
    /** Every open bet, in exactly one of the last five groups (they add up to [open]). */
    data class Report(
        val open: Int,
        /** Bets a read was tried for. */
        val checked: Int = 0,
        /** Read, and the books' fair odds now are on the bet. */
        val updated: Int = 0,
        /** Read within the last minute already: left as they are. */
        val current: Int = 0,
        /** Tried, but the page was gone or had no book pricing both sides. */
        val failed: Int = 0,
        /** Not tried: the run stopped early (CNO asked for a pause, or five reads in a row failed). */
        val skipped: Int = 0,
        /** Game started over [STALE_AFTER_START_MS] ago: nothing left to check, its result comes from the final score. */
        val over: Int = 0,
        /** No CNO page (Vigilant's own bets) and not priced by [OpenBetPricer] (yet): the Vigilant scanner is off, or it wasn't run. */
        val vigilantOnly: Int = 0,
        val stopped: Boolean = false,
        /** CNO's own part as it went, kept when [withPricing] rescues its misses (Tj's diagnostics, 2026-09-30: "CNO read 1" and no why). */
        val cnoTried: Int = 0,
        val cnoFailed: Int = 0,
        val cnoSkipped: Int = 0,
        /** No CNO page and the game has started: not priced (before 2026-09-30; now [OpenBetPricer] prices them, so a plan leaves this 0). */
        val started: Int = 0,
        /** [OpenBetPricer] gave these bets a current EV from Vigilant's own fair odds (Vigilant's own bets, and CNO bets CNO didn't read). */
        val priced: Int = 0,
        /** ...and these it couldn't price: each shows the reason ([TrackedBet.nowNote]). */
        val unpriced: Int = 0,
        /** The bets CNO tried and failed, or never tried: [OpenBetPricer]'s to take over. */
        val unreadIds: List<String> = emptyList(),
        /** Of [updated], priced from the backup's books (ParlayAPI) because CNO didn't answer or didn't list the bet at its line. */
        val viaBackup: Int = 0,
        /** CNO reads that got no answer at all (as opposed to a page without the bet at its line). */
        val cnoNoAnswer: Int = 0,
        /** CNO stopped answering (or asked for a pause) part-way: the rest went to the backup, or weren't tried without one. */
        val cnoStopped: Boolean = false,
        /** Read both ways in this check, CNO's page and Vigilant's own fair odds: their current EV is the two lines' average. */
        val both: Int = 0,
    ) {
        /** Open bets whose odds are now current. */
        val covered: Int get() = updated + current + priced

        /** This report after [p], a pricing pass over some of the bets it hadn't covered: Vigilant's own ([rescue] false) or CNO's unread ones. */
        fun withPricing(p: OpenBetPricer.Report, rescue: Boolean): Report {
            if (!rescue) return copy(priced = priced + p.priced, unpriced = unpriced + p.unpriced, vigilantOnly = (vigilantOnly - p.asked).coerceAtLeast(0))
            val fromFailed = minOf(failed, p.asked)
            val fromSkipped = minOf(skipped, p.asked - fromFailed)
            return copy(priced = priced + p.priced, unpriced = unpriced + p.unpriced, failed = failed - fromFailed, skipped = skipped - fromSkipped)
        }

        /**
         * This report once Vigilant's own fair odds have also priced every open bet beside CNO's reads ([merged]: [BetTracker.mergeReads]):
         * Vigilant's own bets ([ownIds], no CNO page) priced or not, CNO's misses that Vigilant's read covered, and how many were read both ways.
         * Every open bet stays in exactly one group.
         */
        fun withEveryRead(merged: BetTracker.Merged, ownIds: Collection<String>): Report {
            val own = ownIds.toHashSet()
            val ownPriced = own.count { it in merged.vigOnly || it in merged.both }
            val unread = unreadIds.toHashSet()
            val rescued = unread.count { it in merged.vigOnly || it in merged.both }
            return withPricing(OpenBetPricer.Report(own.size, ownPriced, own.size - ownPriced), rescue = false)
                .withPricing(OpenBetPricer.Report(unread.size, rescued, unread.size - rescued), rescue = true)
                .copy(both = merged.both.size)
        }

        /**
         * The round's line in Diagnostics: what CNO read of its bets (and why the rest weren't, [pauseWhy] being CNO's last pause), what
         * Vigilant priced, what nothing could.
         */
        fun roundNote(vigilantOn: Boolean, pauseWhy: String? = null): String {
            val cno = buildString {
                append("CNO read ${updated - viaBackup} of $cnoTried")
                if (viaBackup > 0) append(", ParlayAPI's books $viaBackup")
                if (cnoNoAnswer > 0) append(", $cnoNoAnswer CNO reads unanswered")
                val misses = listOfNotNull(
                    "$cnoFailed failed".takeIf { cnoFailed > 0 },
                    "$cnoSkipped not tried".takeIf { cnoSkipped > 0 },
                )
                if (misses.isNotEmpty()) append(" (").append(misses.joinToString(", "))
                    .append(if (stopped || cnoStopped) ": stopped early" + (pauseWhy?.let { ", $it" } ?: "") else "").append(")")
            }
            return "covered $covered of $open open bets: $cno, $priced priced from Vigilant's own fair odds" +
                (if (both > 0) ", $both read both ways (averaged)" else "") +
                (if (unreadIds.isNotEmpty() && vigilantOn) ", ${unreadIds.size} CNO didn't read went to a second pricing pass" else "") +
                (if (unpriced > 0) ", $unpriced couldn't be priced" else "") + (if (failed > 0) ", $failed failed" else "")
        }

        /**
         * What the toast says: the counts, all of them, in words. [graded]: the grading pass that ran beside the odds check (the
         * finished games' results, [BetSettler]); without one, finished games are only counted.
         */
        fun summary(vigilantOff: Boolean = false, graded: BetSettler.Report? = null): String {
            if (open == 0) return "No open bets to check"
            val parts = ArrayList<String>()
            parts += "Checked $covered of $open open bet${if (open == 1) "" else "s"}"
            if (both > 0) parts += "$both read by both CNO and Vigilant (their fair lines averaged)"
            if (viaBackup > 0) parts += "$viaBackup from ParlayAPI's books (CrazyNinjaOdds didn't have ${if (viaBackup == 1) "it" else "them"})"
            if (failed > 0) parts += "$failed couldn't be read (each bet says why)"
            if (unpriced > 0) parts += "$unpriced couldn't be priced (each bet says why)"
            if (skipped > 0) parts += "$skipped not tried"
            if (started > 0) parts += "$started game${if (started == 1) "" else "s"} in progress (results come from final scores)"
            if (over > 0 && graded == null) {
                parts += "$over game${if (over == 1) "" else "s"} already over (results come from final scores)"
            } else if (over > 0 || graded != null && graded.settled > 0) {
                val word = listOfNotNull(
                    "graded ${graded!!.settled} from final scores".takeIf { graded.settled > 0 },
                    "${graded.waiting} not over yet".takeIf { graded.waiting > 0 },
                    "${graded.manual} need${if (graded.manual == 1) "s" else ""} a tap (each says why)".takeIf { graded.manual > 0 },
                    "the score feeds didn't answer".takeIf { graded.stopped && graded.settled == 0 },
                ).joinToString(", ").ifEmpty { "nothing due yet" }
                parts += if (over > 0) "$over game${if (over == 1) "" else "s"} already over: $word" else word.replaceFirstChar { it.uppercase() }
            }
            if (vigilantOnly > 0) {
                val n = "$vigilantOnly Vigilant bet${if (vigilantOnly == 1) "" else "s"}"
                parts += if (vigilantOff) "$n not updated: the Vigilant scanner is off (Settings › Scanning)" else "$n update with each Vigilant scan"
            }
            val text = parts.joinToString(" · ")
            return when {
                covered == 0 && checked > 0 && stopped && cnoNoAnswer > 0 -> "CrazyNinjaOdds didn't answer: try again in a minute · $text"
                stopped -> "$text · stopped early: CrazyNinjaOdds asked for a pause, tap again in a minute"
                else -> text
            }
        }
    }

    /** How the open bets split for a run at [now] (no reads). */
    data class Plan(
        val open: Int,
        val todo: List<TrackedBet>,
        val current: Int,
        val over: Int,
        val vigilantOnly: Int,
        /** Bets with no CNO page (still to start or under way), not read within the last minute: [OpenBetPricer]'s to price. */
        val vigilantBets: List<TrackedBet> = emptyList(),
        /** Started games with no CNO page. */
        val started: Int = 0,
    )

    private val mutex = Mutex()

    /** Open bets whose game isn't long over and whose CNO page is known: the ones a run reads, soonest game first. */
    fun due(bets: List<TrackedBet>, now: Long = clock()): List<TrackedBet> = plan(bets, now, freshMs = 0L).todo

    /** Splits [bets]' open ones into what a run reads, what's already current, and what it can't read. */
    fun plan(bets: List<TrackedBet>, now: Long = clock(), freshMs: Long = FRESH_MS): Plan {
        val open = bets.filter { it.status == BetStatus.PENDING }
        val over = open.filter { now - it.startsTs >= STALE_AFTER_START_MS }
        val live = open - over.toSet()
        val readable = live.filter { it.gameUrl != null }
        val own = live - readable.toSet()
        fun fresh(b: TrackedBet) = freshMs > 0 && b.nowAtMs != null && now - b.nowAtMs < freshMs
        val current = readable.filter(::fresh)
        // Every one with no CNO page is Vigilant's to price, a game under way too (Tj, 2026-09-30: "every single open bet refreshed regardless of
        // what scanner found the bet"): [OpenBetPricer] prices games up to [STALE_AFTER_START_MS] after their start, as CNO's pages are read.
        val pricing = own.filterNot(::fresh)
        return Plan(
            open = open.size,
            todo = (readable - current.toSet()).sortedBy { it.startsTs },
            current = current.size + (own.size - pricing.size),
            over = over.size,
            vigilantOnly = pricing.size,
            vigilantBets = pricing.sortedBy { it.startsTs },
            started = 0,
        )
    }

    /** The bets a run would read now ([Plan.todo]) plus what it can't: for a progress line before the first read. */
    suspend fun preview(): Plan = plan(tracker.all())

    /** What [readAll] did: reads tried, bets updated, reads that failed, whether it stopped early. */
    private data class Tally(
        val checked: Int, val updated: Int, val failed: Int, val stopped: Boolean, val updatedIds: Set<String> = emptySet(),
        val viaBackup: Int = 0, val noAnswer: Int = 0, val cnoAsked: Int = 0, val cnoStopped: Boolean = false,
    )

    /** How one bet's read came out. [cnoAnswered]: null when CNO wasn't asked (it had stopped answering). */
    private sealed interface Outcome {
        val cnoAnswered: Boolean?
        class Updated(val change: (TrackedBet) -> TrackedBet, val viaBackup: Boolean, override val cnoAnswered: Boolean?) : Outcome
        class Failed(val note: String, override val cnoAnswered: Boolean?) : Outcome
    }

    /**
     * Reads [todo]'s books, [concurrency] at a time, soonest game first, saving results in batches (a cancelled read keeps what it
     * got). Stops early when CNO asks for a pause or [MAX_FAILS_IN_ROW] reads in a row fail (reads already under way finish).
     * [onProgress] gets (read so far, of how many).
     */
    private suspend fun readAll(todo: List<TrackedBet>, onProgress: (Int, Int) -> Unit): Tally {
        var updated = 0
        var failed = 0
        var checked = 0
        var failedInARow = 0
        var viaBackup = 0
        var noAnswer = 0
        var cnoAsked = 0
        val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
        // CNO stopped answering (or paused): the rest go to the backup alone, or the run stops without one.
        val cnoDown = java.util.concurrent.atomic.AtomicBoolean(false)
        val pending = LinkedHashMap<String, (TrackedBet) -> TrackedBet>()
        val updatedIds = HashSet<String>()
        val lock = Mutex()
        // Callers hold [lock].
        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = LinkedHashMap(pending)
            pending.clear()
            // A cancelled pass still saves what it has read.
            withContext(NonCancellable) { tracker.editMany(batch) }
        }
        val queue = Channel<TrackedBet>(Channel.UNLIMITED).apply { todo.forEach { trySend(it) }; close() }
        try {
            onProgress(0, todo.size)
            coroutineScope {
                repeat(concurrency.coerceIn(1, todo.size.coerceAtLeast(1))) {
                    launch {
                        for (bet in queue) {
                            if (stopped.get()) break
                            if (paused()) cnoDown.set(true)
                            val askCno = !cnoDown.get()
                            if (!askCno && backup == null) { stopped.set(true); break }
                            val outcome = read(bet, askCno)
                            lock.withLock {
                                checked++
                                if (outcome.cnoAnswered != null) cnoAsked++
                                when (outcome.cnoAnswered) {
                                    false -> { failedInARow++; noAnswer++ }
                                    true -> failedInARow = 0
                                    null -> Unit
                                }
                                when (outcome) {
                                    is Outcome.Updated -> {
                                        pending[bet.id] = outcome.change
                                        updatedIds += bet.id
                                        updated++
                                        if (outcome.viaBackup) viaBackup++
                                    }
                                    is Outcome.Failed -> {
                                        failed++
                                        // Every open bet is priced or says why (Tj, 2026-09-29).
                                        val at = clock()
                                        pending[bet.id] = { b -> if (b.status != BetStatus.PENDING) b else b.copy(nowNote = outcome.note, nowNoteAtMs = at) }
                                    }
                                }
                                // CNO is down, refusing, or asked for a pause: don't hammer it for the other bets.
                                if (paused() || failedInARow >= MAX_FAILS_IN_ROW) {
                                    cnoDown.set(true)
                                    if (backup == null) stopped.set(true)
                                }
                                if (pending.size >= BATCH) flush()
                                onProgress(checked, todo.size)
                            }
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) { lock.withLock { flush() } }
        }
        return Tally(checked, updated, failed, stopped.get(), updatedIds, viaBackup, noAnswer, cnoAsked, cnoDown.get())
    }

    /** One pass over every open bet. Runs one pass at a time; a second caller waits for it. [onProgress] gets (read so far, to read). */
    suspend fun run(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Report = mutex.withLock {
        val p = plan(tracker.all())
        val t = readAll(p.todo, onProgress)
        Report(
            open = p.open, checked = t.checked, updated = t.updated, current = p.current, failed = t.failed,
            skipped = p.todo.size - t.checked, over = p.over, vigilantOnly = p.vigilantOnly, stopped = t.stopped,
            started = p.started, unreadIds = p.todo.map { it.id }.filter { it !in t.updatedIds },
            cnoTried = p.todo.size, cnoFailed = t.failed, cnoSkipped = p.todo.size - t.cnoAsked,
            viaBackup = t.viaBackup, cnoNoAnswer = t.noAnswer, cnoStopped = t.cnoStopped,
        )
    }

    /**
     * The closing line, kept (Tj, 2026-09-29: "how well my positive EV bets profit"): CLV, the best early sign an edge is real, is the
     * fair price read just before the game starts, and it's only recorded when something reads the bet's books then. The background
     * auto-scan calls this each cycle for the open bets starting within [withinMs] whose books weren't read in the last [freshMs]; the
     * last read before the start is the closing line ([TrackedBet.closingFair]). Returns how many bets were updated.
     */
    suspend fun captureClosing(withinMs: Long = CLOSING_WITHIN_MS, freshMs: Long = CLOSING_FRESH_MS): Int = mutex.withLock {
        val now = clock()
        val todo = tracker.all()
            .filter { b ->
                b.status == BetStatus.PENDING && b.gameUrl != null && b.startsTs > now && b.startsTs - now <= withinMs &&
                    (b.nowAtMs == null || now - b.nowAtMs < 0 || now - b.nowAtMs >= freshMs)
            }
            .sortedBy { it.startsTs }
        if (todo.isEmpty()) 0 else readAll(todo) { _, _ -> }.updated
    }

    /**
     * The closing capture's CNO half ([ClosingLine], Tj 2026-09-29: "finds the true closing odds for each of my bets"): reads the books of
     * [ids] that are CNO bets still to start, however recently they were read (the read just before the start is the close). Returns the ids
     * whose books were read.
     */
    suspend fun captureClosing(ids: Collection<String>): Set<String> = mutex.withLock {
        val now = clock()
        val wanted = ids.toHashSet()
        val todo = tracker.all().filter { it.id in wanted && it.status == BetStatus.PENDING && it.gameUrl != null && it.startsTs > now }.sortedBy { it.startsTs }
        if (todo.isEmpty()) emptySet() else readAll(todo) { _, _ -> }.updatedIds
    }

    /**
     * Every book's price for open bets with no CNO page (Vigilant's own, ParlayAPI's picks, fills synced from Novig; Tj 2026-09-30: "every
     * single open bet refreshed regardless of what scanner found the bet"): the backup's read ([ParlayBooks]: ParlayAPI's books judged with
     * CNO's own check), beside Vigilant's own fair odds ([BetTracker.mergeReads] combines the two), games under way too (up to
     * [STALE_AFTER_START_MS] after the start). Doesn't wait for a [run] (CNO's pages are another source). Returns the ids read; none without a backup.
     */
    suspend fun readWithoutPage(ids: Collection<String>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Set<String> {
        val b = backup ?: return emptySet()
        val now = clock()
        val wanted = ids.toHashSet()
        val todo = tracker.all()
            .filter { it.id in wanted && it.status == BetStatus.PENDING && it.gameUrl == null && now - it.startsTs < STALE_AFTER_START_MS }
            .sortedBy { it.startsTs }
        if (todo.isEmpty()) return emptySet()
        val changes = LinkedHashMap<String, (TrackedBet) -> TrackedBet>()
        try {
            todo.forEachIndexed { i, bet ->
                val view = try {
                    b(bet)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                view?.let { changeFor(bet, rowOf(bet), it, BetTracker.VIA_PARLAY) }?.let { changes[bet.id] = it }
                onProgress(i + 1, todo.size)
            }
        } finally {
            // A cancelled pass still saves what it has read.
            withContext(NonCancellable) { if (changes.isNotEmpty()) tracker.editMany(changes) }
        }
        return changes.keys.toSet()
    }

    /**
     * Re-reads one bet's books now, whatever else runs or was read a minute ago (the sheet's "Re-read books"); false when it couldn't
     * be read. It never waits for a whole [run] (minutes): CNO's own one-read-at-a-time queue is all it waits behind.
     */
    suspend fun checkOne(id: String): Boolean {
        val bet = tracker.all().firstOrNull { it.id == id && it.status == BetStatus.PENDING && it.gameUrl != null } ?: return false
        val outcome = read(bet, askCno = !paused())
        if (outcome !is Outcome.Updated) return false
        tracker.editMany(mapOf(id to outcome.change))
        return true
    }

    /**
     * [bet]'s books read now ([askCno]: CNO's game page first) and turned into what to change on it. CNO not answering, or answering
     * without the bet at its line (a line that moved, a prop pulled), goes to [backup]; each miss says why.
     */
    private suspend fun read(bet: TrackedBet, askCno: Boolean): Outcome {
        val row = rowOf(bet)
        var cnoAnswered: Boolean? = null
        var cnoMiss: String? = null
        if (askCno) {
            val view = try {
                books(row).also { cnoAnswered = true }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cnoAnswered = false
                null
            }
            val change = view?.let { changeFor(bet, row, it, BetTracker.VIA_CNO) }
            if (change != null) return Outcome.Updated(change, viaBackup = false, cnoAnswered = cnoAnswered)
            cnoMiss = when {
                cnoAnswered == false -> "CrazyNinjaOdds didn't answer"
                view == null -> "CrazyNinjaOdds' game page doesn't list this bet at your line now"
                else -> "No book on CrazyNinjaOdds' page prices both sides of this bet now"
            }
        } else {
            cnoMiss = "CrazyNinjaOdds had stopped answering"
        }
        val b = backup ?: return Outcome.Failed(cnoMiss, cnoAnswered)
        val other = try {
            b(bet)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val change = other?.let { changeFor(bet, row, it, BetTracker.VIA_PARLAY) }
        return if (change != null) Outcome.Updated(change, viaBackup = true, cnoAnswered = cnoAnswered)
        else Outcome.Failed("$cnoMiss, and ParlayAPI's books don't price it at your line", cnoAnswered)
    }

    /** What [view] (every book's price for [bet], from [via]) changes on it, or null when no book prices both sides. */
    private fun changeFor(bet: TrackedBet, row: CnoRow, view: CnoBooksView, via: String): ((TrackedBet) -> TrackedBet)? {
        val check = CnoBooks.check(view, row, preferListOdds = true)
        val fair = check.fairProbability ?: return null
        val now = clock()
        val lines = view.prices.map { BookLine(it.name, it.odds, it.otherOdds) }
        val ownCode = CnoBooks.codeFor(bet.book) ?: CnoBooks.NOVIG
        val ownNow = view.prices.firstOrNull { it.code == ownCode }?.odds
        return { b ->
            // Settled (tapped or graded) while this was being read: leave it as it is.
            if (b.status != BetStatus.PENDING) {
                b
            } else {
                val closing = now < b.startsTs
                b.copy(
                    nowFair = fair, nowEv = fair / b.cost - 1.0, nowAtMs = now, nowBooks = check.twoSided,
                    nowVia = via, nowNote = null, nowNoteAtMs = null,
                    // CNO's read on its own, for the check that also reads Vigilant's ([BetTracker.mergeReads]).
                    cnoFair = fair, cnoAtMs = now,
                    // Dated by when the page was read, so a cached page can't pass for a close, nor replace a fresher one.
                    closingFair = if (closing && ClosingLine.supersedes(b, minOf(now, view.fetchedAtMs))) fair else b.closingFair,
                    closingSeenAtMs = if (closing && ClosingLine.supersedes(b, minOf(now, view.fetchedAtMs))) minOf(now, view.fetchedAtMs) else b.closingSeenAtMs,
                    books = lines, booksAtMs = view.fetchedAtMs, otherSide = view.otherBet, nowAmerican = ownNow ?: b.nowAmerican,
                )
            }
        }
    }

    companion object {
        /** A game this far past its start is over, or nearly: nothing left to recheck. */
        const val STALE_AFTER_START_MS = 4 * 60 * 60_000L

        /** A bet read this recently is left alone by the next tap: its odds are current. */
        const val FRESH_MS = 60_000L

        /** Reads that fail one after another before a run stops (CNO is down or refusing). */
        const val MAX_FAILS_IN_ROW = 5

        /** Bets' results saved together (one file write) every this many reads. */
        const val BATCH = 5

        /** The auto-scan re-reads open bets starting within this ([captureClosing])… */
        const val CLOSING_WITHIN_MS = 60 * 60_000L

        /** …unless they were read this recently. */
        const val CLOSING_FRESH_MS = 5 * 60_000L

        /** The CNO row a tracked bet came from, as far as the Tracker kept it. */
        fun rowOf(b: TrackedBet) = CnoRow(
            ev = b.evPercentAtBet ?: 0.0,
            startsAtMs = b.startsTs,
            league = b.league,
            event = b.eventName,
            market = b.marketLabel,
            bet = b.selection,
            odds = b.american ?: Odds.probabilityToAmerican(b.price.coerceIn(0.001, 0.999)),
            book = b.book,
            gameUrl = b.gameUrl,
            betUrl = b.betUrl,
        )
    }
}
