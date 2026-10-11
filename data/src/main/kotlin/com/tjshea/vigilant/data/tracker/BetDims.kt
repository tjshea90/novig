package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.BetRating
import java.util.Locale

/**
 * The ways a bet or bid can be looked at (Tj, 2026-10-11: "stats depending on the type of bet and rating of the bet ... a drop down box with a smart filter"), one place for the Stats filters,
 * the breakdown card and the file shared with Claude, so all three say the same thing about the same bet. [valueOf] is a bet's group on a dimension; [order] is how a dimension's groups read
 * (the natural order, not the biggest first). Pure.
 */
enum class BetDim(val label: String) {
    MADE("Bet or bid"),
    HOW("Auto or manual"),
    KIND("Type of bet"),
    SPORT("Sport"),
    STARS("Star rating"),
    SHARP("Sharp book"),
    BOOKS("Books behind the fair"),
    AGREE("Books agreeing"),
    EV("EV when placed"),
    LEAD("Time before the game"),
    LIQUIDITY("Liquidity"),
    ODDS("Odds"),
    SCANNER("Scanner"),
    RESULT("Result"),
    CLOSE("Against the close"),
    CLOSE_SOURCE("Close basis"),
    ;
}

object BetDims {
    const val UNRATED = "Not rated"
    const val UNKNOWN = "Not recorded"

    /** The star rating of [b] and whether it was estimated afterwards: what the record kept, else worked out from its as-placed facts ([BetRating.of]). */
    fun stars(b: TrackedBet): Pair<Double, Boolean>? = BetRating.of(b.atBet)

    /** Companies behind the fair line when [b] was placed: the book check's two-sided count, else the books the fair line used (one a company). */
    fun books(b: TrackedBet): Int? {
        val a = b.atBet ?: return null
        return a.twoSided?.takeIf { it > 0 } ?: a.fairBooks.map { com.tjshea.vigilant.engine.BookCompany.of(it) }.distinct().size.takeIf { it > 0 }
    }

    /** "agree" / "against" / "none" (no sharp book priced it) / null (not recorded). */
    fun sharp(b: TrackedBet): String? {
        val a = b.atBet ?: return null
        BetRating.sharpOf(a)?.let { return if (it) "agree" else "against" }
        return if (a.sharpVerdict == "NO_SHARP" || a.fairSharp.isEmpty() && a.twoSided != null) "none" else if (a.sharpVerdict != null) "none" else null
    }

    fun sharpLabel(b: TrackedBet): String = when (sharp(b)) {
        "agree" -> "A sharp book agreed"
        "against" -> "A sharp book said no"
        "none" -> "No sharp book priced it"
        else -> UNKNOWN
    }

    /** The type of bet ("Player props", "Moneylines", "Spreads", "Game totals"…): the record's own, else worked out from the market and pick, so a bet with no record still has one. */
    fun kindLabel(b: TrackedBet): String =
        b.atBet?.kind?.takeIf { it.isNotEmpty() }?.let { k -> runCatching { BetKind.valueOf(k).label }.getOrDefault(k) } ?: BetKind.of(b.marketLabel, b.selection).label

    /** The sport ("Football", "Basketball", "Tennis"…): the record's own, else from the league. */
    fun sportLabel(b: TrackedBet): String =
        (b.atBet?.sport?.takeIf { it.isNotEmpty() } ?: com.tjshea.vigilant.data.scanner.SharpVeto.sportOf(b.league).name).lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')

    /** Minutes between placing and the start (the record's own, else the two times). */
    fun leadMinutes(b: TrackedBet): Long? = b.atBet?.minutesToStart ?: b.startsTs.takeIf { it > 0 }?.let { (it - b.createdAtMs) / 60_000L }

    fun starBand(stars: Double): String = BetRating.label(stars) + "★"

    /** Every star value, best first, for ordering. */
    val STAR_ORDER: List<String> = listOf(5.0, 4.5, 4.0, 3.5, 3.0, 2.5, 2.0, 1.5, 1.0).map(::starBand) + UNRATED

    val BOOKS_ORDER = listOf("1 book", "2 books", "3 books", "4-5 books", "6-9 books", "10 or more books", UNKNOWN)
    val AGREE_ORDER = listOf("Every book agreed", "All but one agreed", "Half or more agreed", "Under half agreed", UNKNOWN)
    val LIQ_ORDER = listOf("Under $10", "$10-25", "$25-100", "$100-500", "$500 or more", UNKNOWN)
    val EV_ORDER = listOf("Under 1%", "1–2%", "2–3%", "3–4%", "4–6%", "6% and up", "No EV on record")
    val RESULT_ORDER = listOf("Won", "Lost", "Pushed", "Open", "Void / other")
    val CLOSE_ORDER = listOf("Beat the close", "Missed the close", "No true close yet")

    fun valueOf(b: TrackedBet, dim: BetDim, now: Long): String = when (dim) {
        BetDim.MADE -> BetOrBid.of(b).group
        BetDim.HOW -> when {
            b.isLock -> "Lock (profit locked in)"
            b.auto -> "Auto"
            b.atBet?.how == AtBet.HOW_STUDY -> "Auto"
            else -> "Manual"
        }
        BetDim.KIND -> kindLabel(b)
        BetDim.SPORT -> sportLabel(b)
        BetDim.STARS -> stars(b)?.first?.let(::starBand) ?: UNRATED
        BetDim.SHARP -> sharpLabel(b)
        BetDim.BOOKS -> books(b)?.let { n -> if (n >= 10) "10 or more books" else if (n >= 6) "6-9 books" else if (n >= 4) "4-5 books" else if (n == 1) "1 book" else "$n books" } ?: UNKNOWN
        BetDim.AGREE -> b.atBet?.let { a ->
            val t = a.twoSided ?: return@let null
            val n = a.agreeing ?: return@let null
            if (t <= 0) null else if (n >= t) "Every book agreed" else if (n == t - 1) "All but one agreed" else if (n * 2 >= t) "Half or more agreed" else "Under half agreed"
        } ?: UNKNOWN
        BetDim.EV -> b.evPercentAtBet.let { ev -> if (ev == null) "No EV on record" else if (ev < 0.01) "Under 1%" else if (ev < 0.02) "1–2%" else if (ev < 0.03) "2–3%" else if (ev < 0.04) "3–4%" else if (ev < 0.06) "4–6%" else "6% and up" }
        BetDim.LEAD -> BetLedger.keyOf(b, BetLedger.Split.LEAD)
        BetDim.LIQUIDITY -> b.atBet?.available?.let { if (it < 10) "Under $10" else if (it < 25) "$10-25" else if (it < 100) "$25-100" else if (it < 500) "$100-500" else "$500 or more" } ?: UNKNOWN
        BetDim.ODDS -> TrackerBreakdown.keyOf(b, TrackerBreakdown.By.ODDS)
        BetDim.SCANNER -> TrackerBreakdown.keyOf(b, TrackerBreakdown.By.SCANNER)
        BetDim.RESULT -> when (b.status) {
            BetStatus.WON -> "Won"
            BetStatus.LOST -> "Lost"
            BetStatus.PUSH -> "Pushed"
            BetStatus.PENDING -> "Open"
            else -> "Void / other"
        }
        BetDim.CLOSE -> ClosingLine.clv(b, now)?.let { if (it > 0) "Beat the close" else "Missed the close" } ?: "No true close yet"
        BetDim.CLOSE_SOURCE -> if (b.status == BetStatus.VOID) "No true close yet" else ClosingLine.closeOf(b, now)?.second?.let { BetHistory.closeBasis(b, it) } ?: "No true close yet"
    }

    /** The natural order of a dimension's groups; groups it doesn't list sort after, by name. */
    fun order(dim: BetDim): List<String> = when (dim) {
        BetDim.STARS -> STAR_ORDER
        BetDim.BOOKS -> BOOKS_ORDER
        BetDim.AGREE -> AGREE_ORDER
        BetDim.LIQUIDITY -> LIQ_ORDER
        BetDim.EV -> EV_ORDER
        BetDim.LEAD -> BetLedger.LEAD_ORDER
        BetDim.RESULT -> RESULT_ORDER
        BetDim.CLOSE -> CLOSE_ORDER
        else -> emptyList()
    }

    fun sortKey(dim: BetDim, value: String): Int = order(dim).indexOf(value).let { if (it < 0) Int.MAX_VALUE else it }
}

/**
 * A filter over bets and bids: for each dimension the groups to keep (a bet passes when, on every dimension that has a choice, its group is among them). Empty = everything.
 * The Stats dropdown builds one from a preset or the custom chips ([BetSmartFilters]).
 */
data class BetFilterSpec(val keep: Map<BetDim, Set<String>> = emptyMap()) {
    val isEmpty: Boolean get() = keep.values.all { it.isEmpty() }

    fun matches(b: TrackedBet, now: Long): Boolean = keep.all { (dim, values) -> values.isEmpty() || BetDims.valueOf(b, dim, now) in values }

    fun apply(bets: List<TrackedBet>, now: Long): List<TrackedBet> = if (isEmpty) bets else bets.filter { matches(it, now) }

    /** [this] with [dim] set to [values] (an empty set clears it). */
    fun with(dim: BetDim, values: Set<String>): BetFilterSpec = BetFilterSpec(if (values.isEmpty()) keep - dim else keep + (dim to values))

    /** "Props · 4★ and up · Sharp agreed", for the dropdown's label. */
    fun label(): String = if (isEmpty) "All bets" else keep.filter { it.value.isNotEmpty() }.entries.sortedBy { it.key.ordinal }.joinToString(" · ") { (dim, v) ->
        if (v.size <= 2) v.sortedBy { BetDims.sortKey(dim, it) }.joinToString(" / ") else "${dim.label}: ${v.size} chosen"
    }

    companion object {
        val ALL = BetFilterSpec()
        fun of(dim: BetDim, vararg values: String) = BetFilterSpec(mapOf(dim to values.toSet()))
    }
}

/** A named choice in the Stats "smart filter" dropdown ([BetSmartFilters.choices]). */
data class SmartChoice(val title: String, val note: String, val spec: BetFilterSpec, val n: Int)

/**
 * The Stats dropdown's smart choices (Tj, 2026-10-11: "a drop down box with a smart filter setting"). Fixed ones (each kind of bet, the high ratings, bets with a sharp book behind them, auto and manual)
 * and ones found in the bets themselves: the groups with the best and the worst closing-line value among those with enough bets to say anything ([findings]). Pure.
 */
object BetSmartFilters {

    /** A group needs this many bets with a true close before it is called best or worst. */
    const val MIN_CLOSED = 30

    /** A group's CLV must be this many standard errors from zero to be called best or worst. */
    const val MIN_T = 2.0

    fun fixed(): List<Pair<String, BetFilterSpec>> = listOf(
        "Everything" to BetFilterSpec.ALL,
        "Bets only" to BetFilterSpec.of(BetDim.MADE, BetOrBid.BET.group),
        "Bids only" to BetFilterSpec.of(BetDim.MADE, BetOrBid.BID.group),
        "Auto-bets and auto-bids" to BetFilterSpec.of(BetDim.HOW, "Auto"),
        "Manual bets" to BetFilterSpec.of(BetDim.HOW, "Manual"),
        "4★ and better" to BetFilterSpec.of(BetDim.STARS, *listOf(5.0, 4.5, 4.0).map(BetDims::starBand).toTypedArray()),
        "3★ and worse" to BetFilterSpec.of(BetDim.STARS, *listOf(3.0, 2.5, 2.0, 1.5, 1.0).map(BetDims::starBand).toTypedArray()),
        "A sharp book agreed" to BetFilterSpec.of(BetDim.SHARP, "A sharp book agreed"),
        "Player props" to BetFilterSpec.of(BetDim.KIND, BetKind.PROP.label),
        "Moneylines" to BetFilterSpec.of(BetDim.KIND, BetKind.MONEYLINE.label),
        "Spreads" to BetFilterSpec.of(BetDim.KIND, BetKind.SPREAD.label),
        "Game totals" to BetFilterSpec.of(BetDim.KIND, BetKind.TOTAL.label),
        "Beat the close" to BetFilterSpec.of(BetDim.CLOSE, "Beat the close"),
        "Missed the close" to BetFilterSpec.of(BetDim.CLOSE, "Missed the close"),
        "Won" to BetFilterSpec.of(BetDim.RESULT, "Won"),
        "Lost" to BetFilterSpec.of(BetDim.RESULT, "Lost"),
    )

    /** One group of bets on one dimension, with the numbers the findings rank by. */
    data class Group(val dim: BetDim, val value: String, val n: Int, val closed: Int, val meanClv: Double, val tStat: Double?)

    /**
     * Every single-dimension group (not Result or Close, which are outcomes, nor Scanner/Source noise) with at least [MIN_CLOSED] bets that have a true close, with its mean CLV
     * and a t-statistic (mean over its standard error: about 2 or more is more than luck). Outliers and locks are left out as everywhere.
     */
    fun groups(bets: List<TrackedBet>, now: Long, minClosed: Int = MIN_CLOSED): List<Group> {
        val usable = bets.filter { !it.isOutlier && !it.isLock && it.status != BetStatus.VOID }
        val out = ArrayList<Group>()
        for (dim in BetDim.entries) {
            if (dim == BetDim.RESULT || dim == BetDim.CLOSE || dim == BetDim.CLOSE_SOURCE) continue
            for ((value, grp) in usable.groupBy { BetDims.valueOf(it, dim, now) }) {
                if (value == BetDims.UNKNOWN || value == BetDims.UNRATED) continue
                val clvs = grp.mapNotNull { ClosingLine.clv(it, now) }
                if (clvs.size < minClosed) continue
                val mean = clvs.average()
                val sd = kotlin.math.sqrt(clvs.sumOf { (it - mean) * (it - mean) } / (clvs.size - 1).coerceAtLeast(1))
                out += Group(dim, value, grp.size, clvs.size, mean, if (sd > 1e-12) mean / (sd / kotlin.math.sqrt(clvs.size.toDouble())) else null)
            }
        }
        return out
    }

    /** The groups that beat the close the most and the ones that gave it away the most, strongest evidence first (by t-statistic, then size). */
    fun findings(bets: List<TrackedBet>, now: Long, take: Int = 3): Pair<List<Group>, List<Group>> {
        val g = groups(bets, now).filter { it.tStat != null }
        // About 60 groups are screened at once, so some reach |t| of 2 by luck alone: a group is shown only at |t| >= 2 and the dropdown says it is a lead, not proof.
        val best = g.filter { it.meanClv > 0 && it.tStat!! >= MIN_T }.sortedByDescending { it.tStat!! }.take(take)
        val worst = g.filter { it.meanClv < 0 && it.tStat!! <= -MIN_T }.sortedBy { it.tStat!! }.take(take)
        return best to worst
    }

    /** All the dropdown's choices for [bets]: the fixed ones that have a bet in them, then the found best and worst groups. */
    fun choices(bets: List<TrackedBet>, now: Long): List<SmartChoice> {
        val fixed = fixed().map { (title, spec) -> SmartChoice(title, "", spec, spec.apply(bets, now).size) }.filter { it.n > 0 || it.spec.isEmpty }
        val (best, worst) = findings(bets, now)
        fun pct(v: Double) = String.format(Locale.US, "%+.1f%%", v * 100)
        fun found(prefix: String, g: Group) = SmartChoice("$prefix: ${g.value}", "${g.dim.label} · CLV ${pct(g.meanClv)} on ${g.closed} closed, t=${String.format(Locale.US, "%.1f", g.tStat ?: 0.0)} (a lead, not proof)", BetFilterSpec.of(g.dim, g.value), g.n)
        return fixed + best.map { found("Best", it) } + worst.map { found("Worst", it) }
    }
}
