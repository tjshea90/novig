package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.tracker.BetInsight
import com.tjshea.vigilant.data.tracker.BetSettler
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.TrackedBet
import com.tjshea.vigilant.data.tracker.TrackerStats
import com.tjshea.vigilant.engine.Odds
import kotlin.math.abs

/** The Tracker's sentences, free of Compose so they're testable: what an open bet is waiting for, what a result means. */
object TrackerText {

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

    /** "101 open · $101.00 at risk · pays $96.40 · 22 started · 9 need a tap · odds read on 61 of 79 upcoming". */
    fun openSummary(open: List<TrackedBet>, now: Long): String {
        if (open.isEmpty()) return "No open bets"
        val started = open.count { now >= it.startsTs }
        val needTap = open.count { now >= it.startsTs && (it.gradeManual || it.autoGradeOff) }
        val upcoming = open.filter { now < it.startsTs }
        return listOfNotNull(
            "${open.size} open",
            "${Format.money(open.sumOf { it.stake })} at risk",
            "pays ${Format.money(open.sumOf { it.profitIfWon })}",
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
        val placed = b.american?.let { " at your ${Odds.formatAmerican(it)}" }.orEmpty()
        val headline = (if (fresh) "now " else "") + Format.evPercentShort(ev) + " EV" + placed
        val detail = listOfNotNull(
            b.nowFair?.let { "fair ${if (fresh) "now" else "then"} ${Format.american(it)}" },
            when (b.nowVia) {
                BetTracker.VIA_CNO -> "CNO's books"
                BetTracker.VIA_VIGILANT -> "Vigilant's fair odds"
                else -> null
            },
            b.nowBooks?.let { "$it book${if (it == 1) "" else "s"}" },
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
        if (note != null && (b.nowNoteAtMs ?: 0L) >= (b.nowAtMs ?: 0L)) return "Not priced: $note (tried ${Format.age(b.nowNoteAtMs, now)})"
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
        return when {
            abs(move) < 0.0005 -> "The fair price hasn't moved since you placed it."
            move > 0 -> "The market has moved $pts points toward your bet since you placed it."
            else -> "The market has moved $pts points against your bet since you placed it."
        }
    }

    /** Whether the price bet at still beats the price that breaks even against fair now ("Break-even … is +127: your +150 still clears it"). */
    fun breakEvenSentence(i: BetInsight): String? {
        val be = i.breakEvenOdds ?: return null
        val clears = i.betOdds != 0 && Odds.americanToDecimal(i.betOdds) >= Odds.americanToDecimal(be) - 1e-9
        return "Break-even against today's fair price is ${Odds.formatAmerican(be)}: ${if (clears) "your ${Odds.formatAmerican(i.betOdds)} still clears it" else "your ${Odds.formatAmerican(i.betOdds)} no longer clears it"}."
    }
}
