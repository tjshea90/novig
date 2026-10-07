package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.CheckOddsStats
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.TrackerStats
import com.tjshea.vigilant.engine.Odds
import kotlin.math.abs

/** The Tracker's sentences, free of Compose so they're testable: what an open bet is waiting for, what a result means. */
object TrackerText {

    /** What the outlier rule leaves out (Tj, 2026-09-27) and what it doesn't (Tj, 2026-10-05): money is every bet. */
    fun outlierNote(outliers: Int): String =
        "$outliers outlier bet${if (outliers == 1) "" else "s"} (over ±${Format.percent(com.tjshea.vigilant.data.tracker.BetTracker.OUTLIER_EV, 0)} EV when bet) " +
            "left out of the record, the edge and closing-line numbers, so one odd bet can't skew them. Profit counts ${if (outliers == 1) "it" else "them"}: it's real money. " +
            "${if (outliers == 1) "It's" else "They're"} under Bets."

    /**
     * What the "Novig only" filter is showing: EV and closing lines from Novig's own prices, and how fresh they are (Tj, 2026-10-02 ~18:50Z: "show the
     * percent EV compared only from novig odds, filtering out other sports books").
     */
    fun novigOnlyNote(bets: List<com.tjshea.vigilant.data.tracker.TrackedBet>, now: Long): String {
        // Every open bet at Novig counts, Novig's ids on record or not (Tj, 2026-10-02 20:06Z: "many open bets are not finding the current novig odds").
        val open = com.tjshea.vigilant.data.tracker.NovigNow.open(bets, now)
        if (open.isEmpty()) return "Novig's odds only: EV is Novig's odds now against the odds you bet at, CLV against Novig's closing odds."
        val priced = open.filter { com.tjshea.vigilant.data.tracker.NovigNow.note(it) == null }
        val notOnNovig = open.count { it.novigWhy != null && com.tjshea.vigilant.data.tracker.NovigNow.note(it) == it.novigWhy }
        val unread = open.size - priced.size - notOnNovig
        val oldest = priced.mapNotNull { it.novigAtMs }.minOrNull()
        return "Novig's odds only (EV: Novig's odds now against the odds you bet at): ${priced.size} of ${open.size} open bets priced" +
            (oldest?.let { " (oldest ${Format.age(it, now)})" } ?: "") +
            (if (notOnNovig > 0) " · $notOnNovig with no Novig price now (why on each bet)" else "") +
            (if (unread > 0) " · $unread not read yet" else "") + "."
    }


    /**
     * What a "Novig only" read did, for its toast: prices read (Novig only), bets found on Novig by name, and bets with no Novig price now (each says why).
     * Null for a read on its own (the Tracker opening) that had nothing to do.
     */
    fun novigReadToast(looked: com.tjshea.vigilant.data.tracker.NovigIds.Found?, read: com.tjshea.vigilant.data.tracker.NovigNow.Read, force: Boolean): String? {
        val found = looked?.ids?.size ?: 0
        val notFound = looked?.why?.size ?: 0
        if (read.due == 0 && found == 0 && notFound == 0) return if (force) "No open bet to price on Novig." else null
        val s = { n: Int -> if (n == 1) "" else "s" }
        return listOfNotNull(
            "Novig's prices read for ${read.prices.size} of ${read.due} open bet${s(read.due)} (Novig only: no other book asked)".takeIf { read.due > 0 },
            "${read.all - read.due} already fresh".takeIf { !force && read.due in 1 until read.all },
            "$found found on Novig by name".takeIf { found > 0 },
            (notFound + read.why.size).takeIf { it > 0 }?.let { "$it with no Novig price now (why on each bet)" },
        ).joinToString("; ") + "."
    }

    // ---- locks (Tj, 2026-10-02 20:06Z: "remove arbitraged locked bets out of stats and bet trackers") ------------------------------------------

    /** Beside the "Hide locked bets" chip: how many bets it hides (or would). */
    fun hiddenLocked(count: Int, hidden: Boolean): String {
        val bets = "$count locked bet${if (count == 1) "" else "s"}"
        return if (hidden) "$bets hidden from the lists and stats (cashed out: paid whatever happens)." else "$bets shown and counted."
    }

    /**
     * What the Bets / Bids chip is showing (Tj, 2026-10-07): which records every number and list below covers, and how many the choice leaves out. A bid is a make
     * order Vigilant posted under its fair price that a taker filled; the ones nobody filled are not Tracker records (the Bids tab and Diagnostics count them).
     */
    fun madeCaption(made: MadeFilter, shown: Int, left: Int): String = when (made) {
        MadeFilter.ALL -> ""
        MadeFilter.BIDS -> "Bids only: $shown bid${if (shown == 1) "" else "s"} a taker filled (make orders Vigilant posted under its fair price). Every number below is for them alone" +
            (if (left > 0) "; $left bet${if (left == 1) "" else "s"} left out" else "") + ". EV is the edge when the bid was posted. Bids nobody filled are on the Bids tab and in Diagnostics."
        MadeFilter.BETS -> "Bets only: $shown taker bet${if (shown == 1) "" else "s"} (your taps, the Bet sheet, the auto-bet, locks). Every number below is for them alone" +
            (if (left > 0) "; $left filled bid${if (left == 1) "" else "s"} left out" else "") + "."
    }

    /** The Stats tab's empty state: nothing tracked at all, or nothing of this kind (or in this period). */
    fun emptyStats(made: MadeFilter, allTime: Boolean, nothingTracked: Boolean): String = when {
        nothingTracked -> if (allTime) "No bets tracked yet" else "No bets in this period"
        made == MadeFilter.ALL -> if (allTime) "No bets tracked yet" else "No bets in this period"
        else -> "No ${made.noun} " + if (allTime) "tracked yet" else "in this period"
    }

    /** The Bets tab's empty state for [filter] ("open", "settled", "all"). */
    fun emptyList(made: MadeFilter, filter: String, nothingTracked: Boolean): String =
        if (nothingTracked) "No bets tracked yet" else "No $filter ${if (made == MadeFilter.ALL) "bets" else made.noun}"

    /** Under the lock card's numbers: what they count, what's paid, what's still riding, and whether the rest of the Tracker counts them. */
    fun lockCaption(s: com.tjshea.vigilant.data.tracker.LockStats, hidden: Boolean): String = listOfNotNull(
        if (s.markets > 0) {
            "${s.lockedBets} of ${s.bets} bet${if (s.bets == 1) "" else "s"} locked in across ${s.markets} market${if (s.markets == 1) "" else "s"} (both sides held " +
                "equally, so each pays the same whichever side wins): ${Format.signedMoney(s.profit)} on ${Format.money(s.staked)} staked on both sides, fees included" +
                when (s.graded) {
                    s.markets -> ", all graded."
                    0 -> ", paid once the games are graded."
                    else -> "; ${Format.signedMoney(s.paid)} of it graded (${s.graded} market${if (s.graded == 1) "" else "s"}), the rest paid once graded."
                }
        } else "No market fully locked in yet.",
        "${s.partly} more market${if (s.partly == 1) " is" else "s are"} partly locked (one side held more: still riding).".takeIf { s.partly > 0 },
        if (hidden) "Hidden from the other numbers and the Bets list (Hide locked bets is on)." else "Also counted in the other numbers and listed under Bets.",
    ).joinToString(" ")

    // ---- closing line value (Tj, 2026-09-29) --------------------------------------------------------------------------

    /** Why a started bet shows no close yet, and whether one is still being looked for (ESPN, Novig's trades; [CloseBackfill]). */
    fun closeMissing(b: TrackedBet, now: Long): String {
        val note = b.closeNote
        return when {
            b.createdAtMs >= b.startsTs -> "No closing line: bet after the start."
            b.isLock -> "No closing line: a lock buys the other side of a bet you hold, so it isn't a pick and has no CLV."
            b.closeFinal -> "No closing line found" + (note?.let { ": $it" } ?: "") + "."
            note != null -> "Closing line not found yet ($note): looked ${Format.age(b.closeLookedAtMs, now)}, looked again every few hours."
            else -> "Closing line: looked for after the start (Pinnacle's close via ParlayAPI, ESPN's closing odds, then Novig's trades the next morning)."
        }
    }

    /** The CLV card's numbers in one line (its accessibility text, and Diagnostics'). */
    fun clvLine(s: com.tjshea.vigilant.data.tracker.ClvStats): String =
        "Beat the close " + (s.beatShare?.let { "${Format.percent(it, 0)} (${s.beat} of ${s.closed})" } ?: "–") +
            " · avg vs close " + (s.averageClv?.let(Format::evPercentShort) ?: "–") +
            " · avg EV at bet " + (s.averageEvAtBet?.let(Format::evPercentShort) ?: "–")

    /** What the CLV card counts and doesn't: bets with a close, waiting for one, started without one, outliers. */
    fun clvCounts(s: com.tjshea.vigilant.data.tracker.ClvStats): String = listOfNotNull(
        "${s.closed} bet${if (s.closed == 1) "" else "s"} with a true close" +
            (s.bySource.entries.sortedByDescending { it.value }.takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")") { "${it.value} ${it.key}" } ?: ""),
        "${s.waiting} waiting for their close (game not started)".takeIf { s.waiting > 0 },
        "${s.missed} started with no close found yet".takeIf { s.missed > 0 },
        when {
            s.outliers == 0 -> null
            s.outliersLeftOut -> "${s.outliers} over ±${Format.percent(com.tjshea.vigilant.data.tracker.ClosingLine.OUTLIER_CLV, 0)} left out"
            else -> "${s.outliers} over ±${Format.percent(com.tjshea.vigilant.data.tracker.ClosingLine.OUTLIER_CLV, 0)} included"
        },
    ).joinToString(" · ")

    // ---- the "Check odds now" counter (Tj, 2026-09-29) -----------------------------------------------------------------

    /** "12 +EV · 5 −EV · 71% +EV": open bets re-priced in this check, by whether they're +EV at the price placed. */
    fun checkCounts(s: CheckOddsStats): String =
        "${s.positive} +EV · ${s.negative} −EV" + (if (s.even > 0) " · ${s.even} even" else "") +
            " · " + (s.positiveShare?.let { Format.percent(it, 0) } ?: "–") + " +EV"

    /** "Avg +1.3% EV": the plain average of this check's EVs, the ones over ±5% left out. */
    fun checkAverage(s: CheckOddsStats): String = "Avg " + (s.averageEv?.let(Format::evPercentShort) ?: "–") + " EV"

    /** What the counter is counting: this check as it goes ([progress]: read, of), or the last one and how long ago; what it leaves out. */
    fun checkCaption(s: CheckOddsStats, checking: Boolean, progress: Pair<Int, Int>?, startedAtMs: Long, now: Long): String = listOfNotNull(
        if (checking) "Open bets re-priced so far in this check" + (progress?.takeIf { it.second > 0 }?.let { " (${it.first}/${it.second} read)" } ?: "")
        else "Open bets re-priced in the check ${Format.age(startedAtMs, now)}",
        "${s.outliers} over ±${Format.percent(CheckOddsStats.OUTLIER_EV, 0)} left out of the average".takeIf { s.outliers > 0 },
        // In-play odds swing with every play: those bets' own cards show them, the counter doesn't (Tj, 2026-09-30).
        "${s.live} live game${if (s.live == 1) "" else "s"} left out".takeIf { s.live > 0 },
    ).joinToString(" · ")

    /** How to colour a bet's status line: [WAITING] is normal, [ATTENTION] needs a tap from Tj. */
    enum class Tone { INFO, WAITING, ATTENTION }

    data class Status(val text: String, val tone: Tone)

    /** "Starts in 3h 20m", "Starts Tue 7:00 PM" past a day, "Started 2h ago". */
    fun startsIn(startTs: Long, now: Long): String {
        val d = startTs - now
        return when {
            d <= 0 -> "Started ${Format.age(startTs, now)}"
            d < 3_600_000L -> "Starts in ${(d / 60_000L).coerceAtLeast(1)}m"
            d < 24 * 3_600_000L -> "Starts in ${d / 3_600_000L}h ${(d % 3_600_000L) / 60_000L}m"
            else -> "Starts ${Format.startTime(startTs)}"
        }
    }

    /**
     * What an open bet is waiting for (null before its game): "In progress", or why a bet whose game started
     * hasn't been graded: the game isn't over, or the reason only a tap can fix ([TrackedBet.gradeNote]).
     */
    fun awaiting(b: TrackedBet, now: Long): Status? {
        if (b.status != BetStatus.PENDING || now < b.startsTs) return null
        if (b.autoGradeOff) return Status("Auto-grading is off for this bet (you undid a result), so it stays open until you mark it or turn it back on", Tone.ATTENTION)
        val note = b.gradeNote
        if (note != null) {
            val checked = b.gradeAtMs?.let { " · checked ${Format.age(it, now)}" }.orEmpty()
            return Status(note + checked, if (b.gradeManual) Tone.ATTENTION else Tone.WAITING)
        }
        return if (now - b.startsTs < BetSettler.AFTER_START_MS) Status("In progress: graded from the final score once it's over", Tone.WAITING)
        else Status("Waiting for the final score…", Tone.WAITING)
    }

    /** The bets Tj should look at first: started and needing a tap, then the rest that started, then upcoming games soonest first. */
    fun openOrder(bets: List<TrackedBet>, now: Long): List<TrackedBet> = bets.sortedWith(
        compareBy<TrackedBet> { b ->
            when {
                now < b.startsTs -> 2
                b.gradeManual || b.autoGradeOff -> 0
                else -> 1
            }
        }.thenBy { it.startsTs },
    )

    /** "101 open · $101.00 at risk · +$96.40 if all win · 22 started · 9 need a tap · odds read on 61 of 79 upcoming". */
    fun openSummary(open: List<TrackedBet>, now: Long): String {
        if (open.isEmpty()) return "No open bets"
        val started = open.count { now >= it.startsTs }
        val needTap = open.count { now >= it.startsTs && (it.gradeManual || it.autoGradeOff) }
        val upcoming = open.filter { now < it.startsTs }
        return listOfNotNull(
            "${open.size} open",
            "${Format.money(open.sumOf { it.stake })} at risk",
            "+${Format.money(open.sumOf { it.profitIfWon })} if all win",
            "$started started".takeIf { started > 0 },
            "$needTap need a tap".takeIf { needTap > 0 },
            // Only games still to come have odds to check: a started game is waiting on its result instead.
            "current EV on ${upcoming.count { currentEv(it, now) }} of ${upcoming.size} upcoming".takeIf { upcoming.isNotEmpty() },
        ).joinToString(" · ")
    }

    /** [b]'s EV was read recently enough to call it "now": inside the age its fair odds may have ([Freshness.maxAgeMs]: 5 minutes, 10 for a far-off game). */
    fun currentEv(b: TrackedBet, now: Long): Boolean {
        val at = b.nowAtMs ?: return false
        return b.nowEv != null && now - at <= Freshness.maxAgeMs(b.startsTs, now)
    }

    /**
     * An open bet's EV line (Tj, 2026-09-29: "the current, up to date EV, which is devigged and compared to the actual odds that I placed the
     * bet at"): [headline] is the EV the fair odds ([TrackedBet.nowFair], devigged) give the price the bet was placed at; "now" only while the
     * read is young, otherwise it says how old it is ([stale]). [detail]: the fair odds, whose they are, how many books, when.
     */
    data class NowLine(val headline: String, val detail: String, val stale: Boolean)

    fun nowLine(b: TrackedBet, now: Long): NowLine? {
        val ev = b.nowEv ?: return null
        val fresh = currentEv(b, now)
        // Read once the game was under way: in-play odds, which the Check odds now counter leaves out.
        val inPlay = (b.nowAtMs ?: Long.MIN_VALUE) >= b.startsTs
        val placed = b.american?.let { " at your ${Odds.formatAmerican(it)}" }.orEmpty()
        val headline = (if (inPlay) "live " else "") + (if (fresh) "now " else "") + Format.evPercentShort(ev) + " EV" + placed
        val detail = listOfNotNull(
            b.nowFair?.let { (if (b.nowVia == com.tjshea.vigilant.data.tracker.NovigNow.VIA) "Novig" else "fair") + " ${if (fresh) "now" else "then"} ${Format.american(it)}" },
            when (b.nowVia) {
                BetTracker.VIA_CNO -> "CNO's books"
                BetTracker.VIA_VIGILANT -> "Vigilant's fair odds"
                BetTracker.VIA_PARLAY -> "ParlayAPI's books"
                // Both reads of this check, averaged: each one's EV, so a split between them shows.
                BetTracker.VIA_BOTH -> listOfNotNull(
                    b.cnoFair?.let { "CNO ${Format.evPercentShort(it / b.cost - 1.0)}" },
                    b.vigFair?.let { "Vigilant ${Format.evPercentShort(it / b.cost - 1.0)}" },
                ).joinToString(" + ", postfix = " averaged")
                else -> null
            },
            b.nowBooks?.let { "$it book${if (it == 1) "" else "s"}" },
            "in-play odds, not in the counter".takeIf { inPlay },
            if (fresh) "read ${Format.age(b.nowAtMs, now)}" else "as of ${Format.age(b.nowAtMs, now)}",
            "tap Check odds now".takeIf { !fresh && now < b.startsTs },
        ).joinToString(" · ")
        return NowLine(headline, detail, stale = !fresh)
    }

    /**
     * Why an upcoming open bet shows no current EV (null when its game has started, it's settled, or its EV is current): the reason the
     * last pricing attempt found no fair price ([TrackedBet.nowNote], while it's newer than the last number), or that nothing has priced it yet.
     */
    fun oddsNote(b: TrackedBet, now: Long): String? {
        if (b.status != BetStatus.PENDING || now >= b.startsTs) return null
        val note = b.nowNote
        if (note != null && (b.nowNoteAtMs ?: 0L) >= (b.nowAtMs ?: 0L)) return "Not priced: $note" + (b.nowNoteAtMs?.let { " (tried ${Format.age(it, now)})" } ?: "")
        if (b.nowEv != null) return null
        return if (b.gameUrl == null) "Not priced yet: tap Check odds now (Vigilant's own fair odds price this bet)" else "Odds not read yet: tap Check odds now"
    }

    /** What the gap between results and expectation means, in words (Tj: "how well my positive EV bets profit"). */
    fun luckMessage(s: TrackerStats): String {
        val z = s.luck
        if (z == null) {
            return "Needs ${TrackerStats.MIN_BETS_FOR_LUCK} settled bets with an EV on record to say whether results match the edges (${s.settledWithEv} so far). " +
                "A handful of bets says nothing: luck swings a lot more than a 3% edge."
        }
        val az = abs(z)
        val sigma = String.format(java.util.Locale.US, "%.1f", az)
        return when {
            az < 1.0 -> "Right around expectation (${if (z < 0) "−" else "+"}${sigma}σ): what a real edge looks like over ${s.settledWithEv} bets."
            az < 2.0 -> "${sigma}σ ${if (z > 0) "above" else "below"} expectation: normal luck over ${s.settledWithEv} bets, too soon to tell."
            z > 0 -> "${sigma}σ above expectation: running hot. Enjoy it, but don't expect it to last."
            else -> "${sigma}σ below expectation: unusually cold. Either bad luck, or the fair prices behind the edges are off (check CLV)."
        }
    }

    /** "You bet +150 (40.0% implied). Fair now +127 (44.0%): 4.0 points better than fair, +10.0% EV." */
    fun edgeSentence(i: BetInsight, current: Boolean = true): String {
        val bet = "You bet ${Odds.formatAmerican(i.betOdds)} (${Format.percent(i.betImplied)} implied${if (i.cost - i.betImplied > 0.0005) ", ${Format.percent(i.cost)} with Novig's fee" else ""})."
        if (i.novig) {
            // Novig only (Tj, 2026-10-02 ~21:35Z): Novig's odds now against the odds bet at, nothing else.
            val odds = i.fairNow ?: return "$bet Novig's odds for it haven't been read yet: tap Check Novig now."
            val p = i.edgePoints ?: 0.0
            val where = when {
                p > 0.0005 -> "${String.format(java.util.Locale.US, "%.1f", p * 100)} points shorter than you bet (your odds beat Novig's now)"
                p < -0.0005 -> "${String.format(java.util.Locale.US, "%.1f", -p * 100)} points longer than you bet (Novig's odds now beat yours)"
                else -> "the same odds you bet at"
            }
            return "$bet Novig ${if (current) "now" else "when last read"} ${Format.american(odds)} (${Format.percent(odds)}): $where, ${Format.evPercentShort(i.evNow ?: 0.0)} EV."
        }
        val fair = i.fairNow ?: return "$bet No fair price read yet: tap Re-read books."
        val pts = i.edgePoints ?: 0.0
        val where = when {
            pts > 0.0005 -> "${String.format(java.util.Locale.US, "%.1f", pts * 100)} points better than fair"
            pts < -0.0005 -> "${String.format(java.util.Locale.US, "%.1f", -pts * 100)} points worse than fair"
            else -> "right at fair"
        }
        return "$bet Fair ${if (current) "now" else "when last read"} ${Format.american(fair)} (${Format.percent(fair)}): $where, ${Format.evPercentShort(i.evNow ?: 0.0)} EV."
    }

    /** "The market has moved 2.0 points toward your bet since you placed it." (null when either fair price is missing). */
    fun moveSentence(i: BetInsight): String? {
        val move = i.fairMove ?: return null
        val pts = String.format(java.util.Locale.US, "%.1f", abs(move) * 100)
        if (i.novig) return when {
            abs(move) < 0.0005 -> "Novig's odds haven't moved since you placed it."
            move > 0 -> "Novig's odds have moved $pts points toward your bet since you placed it."
            else -> "Novig's odds have moved $pts points against your bet since you placed it."
        }
        return when {
            abs(move) < 0.0005 -> "The fair price hasn't moved since you placed it."
            move > 0 -> "The market has moved $pts points toward your bet since you placed it."
            else -> "The market has moved $pts points against your bet since you placed it."
        }
    }

    /** Whether the price bet at still beats the price that breaks even against fair now ("Break-even … is +127: your +150 still clears it"). */
    fun breakEvenSentence(i: BetInsight): String? {
        // Novig only: Novig's odds now are the break-even, already said ([edgeSentence]).
        if (i.novig) return null
        val be = i.breakEvenOdds ?: return null
        val clears = i.betOdds != 0 && Odds.americanToDecimal(i.betOdds) >= Odds.americanToDecimal(be) - 1e-9
        return "Break-even against today's fair price is ${Odds.formatAmerican(be)}: ${if (clears) "your ${Odds.formatAmerican(i.betOdds)} still clears it" else "your ${Odds.formatAmerican(i.betOdds)} no longer clears it"}."
    }
}
