package com.tjshea.vigilant.data.study

import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The scan study's records (Tj, 2026-10-03: "on every cno scan, the vigilant app saves logs on all kinds of information such as … odds at the time of scan,
 * type of bet, percent EV, amount of books that agree, percentage of books that agree, time before the game begins … when those bets are final, it logs
 * whether they won or lost or pushed and their closing line odds"). Everything is one append-only line per event in a day's journal ([StudyJournal]); a bet is
 * the fold of its lines ([StudyBet]).
 */

/** One look at a listed bet: where it was seen ([k]) and what the price and the books were then. Compact: a busy day has tens of thousands. */
@Serializable
data class Sight(
    /**
     * [CNO] the app's CNO list carried it (CNO's read under Tj's filters), [WIDE] the study's wide read found it (CNO's filters opened right up: every row CNO finds, the
     * app's list's rows too), [VIGILANT] a Vigilant scan listed it, [CHECK] its book page was read, [GONE_CNO]/[GONE_WIDE]/[GONE_VIGILANT] that read no longer lists it.
     */
    val k: String,
    /** Novig's price for the bet then (American): CNO's own price, or Novig's live one when it had been read in the last minute; Vigilant's quote. */
    val o: Int? = null,
    /** The EV the list showed at that price, and the fair probability behind it. */
    val ev: Double? = null,
    val f: Double? = null,
    /** Books behind the fair: CNO's own count, or the books Vigilant's fair line used. */
    val b: Int? = null,
    /** Dollars Novig had at the price. */
    val a: Double? = null,
    /** The app's own book check ([CHECK] sights): books agreeing, companies pricing both sides, the check's EV, the sharp veto's verdict. */
    val g: Int? = null,
    val n: Int? = null,
    val ce: Double? = null,
    val sv: String? = null,
) {
    companion object {
        const val CNO = "c"
        const val WIDE = "w"
        const val VIGILANT = "v"
        const val CHECK = "k"
        const val GONE_CNO = "xc"
        const val GONE_WIDE = "xw"
        const val GONE_VIGILANT = "xv"

        /** The sight kinds that carry a price (a listing), as opposed to a check or a disappearance. */
        fun isListing(k: String) = k == CNO || k == VIGILANT || k == WIDE
        fun isGone(k: String) = k == GONE_CNO || k == GONE_VIGILANT || k == GONE_WIDE

        /** The same, for the lists the app itself shows (CNO's list under Tj's filters, Vigilant's feed): what "listed" meant before the wide read. */
        fun isAppListing(k: String) = k == CNO || k == VIGILANT
        fun isAppGone(k: String) = k == GONE_CNO || k == GONE_VIGILANT
    }
}

/** What grading and the close lookups found for a bet, as an event: only what changed is kept when folded ([StudyBet.applied]). */
@Serializable
data class StudyResult(
    val status: BetStatus? = null,
    val settledAtMs: Long? = null,
    val settledBy: String? = null,
    val settleValue: Double? = null,
    val gradeNote: String? = null,
    val gradeAtMs: Long? = null,
    val gradeManual: Boolean = false,
    val closeFair: Double? = null,
    val closeVia: String? = null,
    val closeNote: String? = null,
    val closeLookedAtMs: Long? = null,
    val closeFinal: Boolean = false,
    val closeAskedOf: List<String>? = null,
    /** Copied from a Tracker bet on the same line: the close read just before the start, and Novig's own close. */
    val closingFair: Double? = null,
    val closingSeenAtMs: Long? = null,
    val novigClose: Double? = null,
    val novigCloseAtMs: Long? = null,
    /** "tracker" when it was copied from Tj's own bet on the same line (nothing was looked up for it). */
    val from: String? = null,
)

/** One line of a day's journal. [e] says which of the fields it carries ([BET], [SIGHT], [CHECK], [VIG], [CNO_REC], [CNO_COLS], [RES], [IDS]). */
@Serializable
data class Line(
    /** [BET] a bet first listed, [SIGHT], [CHECK] its book check, [VIG] Vigilant's own record of it, [RES] a grading or close result. */
    val e: String,
    val id: String,
    val t: Long,
    val b: TrackedBet? = null,
    /**
     * On [BET]: why the app's own CNO list would not show the row at the first look: a [com.tjshea.vigilant.data.cno.CnoChecks.Reason] name (the app's own screen under
     * Tj's filters), or [NOT_LISTED] (the screen passes it, but CNO's read under his filters didn't carry it: a filter CNO applies itself, or the row limit); null = it shows it.
     */
    val sc: String? = null,
    val s: Sight? = null,
    val a: AtBet? = null,
    val r: StudyResult? = null,
    /** On [IDS]: the Novig market and outcome the bet turned out to be, once its link was found (Novig's trade history is read by them). */
    val m: String? = null,
    val o: String? = null,
    /** On [CNO_COLS]: every column CNO printed for the row, by its header, and the row's `data-*` attributes ([com.tjshea.vigilant.data.cno.CnoRow.cols]). */
    val c: Map<String, String>? = null,
) {
    companion object {
        const val NOT_LISTED = "NOT_LISTED"
        const val CNO_COLS = "cols"
        const val IDS = "ids"
        const val CNO_REC = "cnorec"
        const val BET = "bet"
        const val SIGHT = "s"
        const val CHECK = "chk"
        const val VIG = "vig"
        const val RES = "res"
    }
}

/** A bet and everything the journal says about it, folded: the bet as first listed (with the book check once it was made), its looks, its result. */
class StudyBet(
    val id: String,
    bet: TrackedBet,
    val screen: String?,
) {
    var bet: TrackedBet = bet
        private set

    /** Every look, in order, with when. */
    val sights = ArrayList<Pair<Long, Sight>>()

    /** Vigilant's own record of the bet, when it wasn't Vigilant that first listed it but a Vigilant scan listed it too. */
    var vig: AtBet? = null
        private set

    /** CNO's own record of the bet (its books, one-way flag, list age), when it wasn't CNO that first listed it but a CNO scan listed it too. */
    var cnoRec: AtBet? = null
        private set

    /** Every column CNO printed for the row (the wide read's [Line.CNO_COLS]), once. */
    var cols: Map<String, String>? = null
        private set

    /** When the last result line was written (nothing else says a bet was looked at). */
    var resultAtMs: Long? = null
        private set

    /** The result's source, "tracker" when copied from Tj's own bet. */
    var from: String? = null
        private set

    fun addSight(t: Long, s: Sight) {
        sights += t to s
    }

    /** The book check made after the first look: its fields go on the bet's record as placed, stamped with when it was made. */
    fun applyCheck(t: Long, check: AtBet) {
        val first = bet.atBet ?: check
        bet = bet.copy(
            atBet = first.copy(
                otherAmerican = check.otherAmerican ?: first.otherAmerican,
                checkFair = check.checkFair, checkEv = check.checkEv, twoSided = check.twoSided, oneSided = check.oneSided, agreeing = check.agreeing,
                verdict = check.verdict, pageAgeSec = check.pageAgeSec, books = check.books, dissent = check.dissent,
                sharpVerdict = check.sharpVerdict, sharpBook = check.sharpBook, sharpEv = check.sharpEv, sharpConfirm = check.sharpConfirm,
                checkAtMs = t,
            ),
        )
    }

    fun applyVig(a: AtBet) {
        if (vig == null) vig = a
    }

    fun applyCnoRec(a: AtBet) {
        if (cnoRec == null) cnoRec = a
    }

    fun applyCols(c: Map<String, String>) {
        if (cols == null) cols = c
    }

    fun applyIds(marketId: String?, outcomeId: String?) {
        bet = bet.copy(marketId = marketId?.takeIf { it.isNotEmpty() } ?: bet.marketId, outcomeId = outcomeId?.takeIf { it.isNotEmpty() } ?: bet.outcomeId)
    }

    fun applyResult(t: Long, r: StudyResult) {
        resultAtMs = t
        r.from?.let { from = it }
        bet = applied(bet, r)
    }

    companion object {
        /** [b] with [r]'s results on it (only what [r] says; a status of null leaves the bet's). */
        fun applied(b: TrackedBet, r: StudyResult): TrackedBet = b.copy(
            status = r.status ?: b.status,
            settledAtMs = r.settledAtMs ?: b.settledAtMs,
            settledBy = r.settledBy ?: b.settledBy,
            settleValue = r.settleValue ?: b.settleValue,
            gradeNote = r.gradeNote ?: b.gradeNote,
            gradeAtMs = r.gradeAtMs ?: b.gradeAtMs,
            gradeManual = if (r.gradeNote != null || r.status != null) r.gradeManual else b.gradeManual,
            closeFair = r.closeFair ?: b.closeFair,
            closeVia = r.closeVia ?: b.closeVia,
            closeNote = if (r.closeFair != null) null else (r.closeNote ?: b.closeNote),
            closeLookedAtMs = r.closeLookedAtMs ?: b.closeLookedAtMs,
            closeFinal = r.closeFinal || b.closeFinal,
            closeAskedOf = r.closeAskedOf ?: b.closeAskedOf,
            closingFair = r.closingFair ?: b.closingFair,
            closingSeenAtMs = r.closingSeenAtMs ?: b.closingSeenAtMs,
            novigClose = r.novigClose ?: b.novigClose,
            novigCloseAtMs = r.novigCloseAtMs ?: b.novigCloseAtMs,
        )

        /** What changed between [before] and [after] as an event; null when the results are the same (no line is written for nothing). */
        fun diff(before: TrackedBet, after: TrackedBet, from: String? = null): StudyResult? {
            val r = StudyResult(
                status = after.status.takeIf { it != before.status },
                settledAtMs = after.settledAtMs.takeIf { it != before.settledAtMs },
                settledBy = after.settledBy.takeIf { it != before.settledBy },
                settleValue = after.settleValue.takeIf { it != before.settleValue },
                gradeNote = after.gradeNote.takeIf { it != null && it != before.gradeNote },
                gradeAtMs = after.gradeAtMs.takeIf { it != before.gradeAtMs && after.gradeNote != before.gradeNote },
                gradeManual = after.gradeManual,
                closeFair = after.closeFair.takeIf { it != before.closeFair },
                closeVia = after.closeVia.takeIf { it != before.closeVia },
                closeNote = after.closeNote.takeIf { it != before.closeNote },
                closeLookedAtMs = after.closeLookedAtMs.takeIf { it != before.closeLookedAtMs },
                closeFinal = after.closeFinal && !before.closeFinal,
                closeAskedOf = after.closeAskedOf.takeIf { it != before.closeAskedOf },
                closingFair = after.closingFair.takeIf { it != before.closingFair },
                closingSeenAtMs = after.closingSeenAtMs.takeIf { it != before.closingSeenAtMs },
                novigClose = after.novigClose.takeIf { it != before.novigClose },
                novigCloseAtMs = after.novigCloseAtMs.takeIf { it != before.novigCloseAtMs },
                from = from,
            )
            return r.takeIf { it != StudyResult(gradeManual = after.gradeManual, from = from) }
        }
    }
}

/** How the journal's lines are written: short (no defaults, no nulls), tolerant of fields a later version adds. */
internal val StudyJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
