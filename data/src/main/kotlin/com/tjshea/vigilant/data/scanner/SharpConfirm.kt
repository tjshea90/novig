package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.diag.CycleLog
import java.util.Locale

/**
 * Sharp-book confirmation (Tj, 2026-10-02: "require bets to be proven positive EV by a current, devigged sharp book such as Pinnacle … fresh (within
 * the last few minutes) odds from the sharp book(s) devigged and compared to the current novig odds for the same exact bet"), pure: whether a sharp
 * book's own two-sided price for the exact line and side, devigged, makes Novig's price +EV, from a quote that is fresh enough. Where the quotes come from
 * ([com.tjshea.vigilant.data.reference.SharpBooks]) is I/O and not here; the verdict is decided here, so the auto-bet, the alerts and the tests agree.
 * RESEARCH.md §60.
 *
 * The rule: some sharp book's fresh quote, devigged worst case ([CnoBooks.fairFor]: the lowest fair chance of multiplicative, additive, power and
 * Shin, so the claim is never rosier than the book's own vig allows), makes the bet +EV at Novig's price by at least [Rules.minEv]; and no other fresh
 * sharp quote says it isn't (Pinnacle's no is not outvoted by Circa's yes). Quotes with no time, or older than [Rules.maxAgeMs], prove nothing.
 */
object SharpConfirm {

    /** A quote's time may be this far ahead of the phone's clock (the feed's clock and the phone's differ by seconds) and still be "now". */
    const val CLOCK_SKEW_MS = 60_000L

    /** What counts as a sharp quote, how old it may be and how much edge it must show. */
    data class Rules(
        /** CrazyNinjaOdds' column codes of the sharp books ([SharpBookChoice.codes]). */
        val codes: Set<String>,
        /** "Pinnacle" or "Pinnacle or Circa", for the words. */
        val label: String,
        val maxAgeMs: Long,
        val minEv: Double,
        /** CNO's game page may supply the sharp quote too (free; no time for the book's own quote, only the page's). */
        val viaCno: Boolean,
    )

    /** The rules for the auto-bet ([autoBet]) or the alerts, from [s]; null unless that one is set to [SharpMode.CONFIRM]. */
    fun rules(s: ScanSettings, autoBet: Boolean): Rules? {
        if ((if (autoBet) s.sharpAutoBet else s.sharpAlerts) != SharpMode.CONFIRM) return null
        return Rules(
            codes = s.sharpConfirmBooks.codes,
            label = s.sharpConfirmBooks.displayName,
            maxAgeMs = minOf(s.sharpConfirmMaxAgeSeconds.coerceAtLeast(30) * 1_000L, Freshness.MAX_QUOTE_AGE_MS),
            minEv = s.sharpConfirmMinEv.coerceAtLeast(0.0),
            viaCno = s.sharpConfirmViaCno,
        )
    }

    /** One sharp book's price for the bet and its other side, with its own time when the source gave one and where it came from. */
    data class Quote(val code: String, val odds: Int, val otherOdds: Int, val atMs: Long?, val via: String)

    /** [quote] judged against Novig's price: its fair chance (devigged), the EV, its age, and whether it is fresh enough. */
    data class Judged(val quote: Quote, val fair: Double, val ev: Double, val ageMs: Long?, val fresh: Boolean) {
        val name: String get() = CnoBooks.name(quote.code)

        fun text(): String =
            "$name ${percent(ev)} (devigged, ${ageMs?.let { CycleLog.span(it.coerceAtLeast(0)) + " old" } ?: "age unknown"}, via ${quote.via})"
    }

    enum class Verdict {
        /** A fresh sharp quote shows +EV at Novig's price, and none contradicts it. */
        CONFIRMED,

        /** A fresh sharp quote says the bet is not +EV (enough), or the sharp books disagree. */
        NOT_CONFIRMED,

        /** Sharp quotes exist but none is fresh enough (or none has a time). */
        STALE,

        /** No sharp book prices both sides of this exact bet. */
        NO_QUOTE,

        /** No source could be asked (no key, credits held back, no answer). */
        UNAVAILABLE,
    }

    /**
     * What the check found. [reason]: one general sentence with no numbers (the skip report counts bets by reason, and each price would be a reason of its
     * own), null when confirmed. [detail]: the numbers, for the pop-up and Diagnostics.
     */
    data class Result(val verdict: Verdict, val judged: List<Judged> = emptyList(), val reason: String? = null, val detail: String = "") {
        val confirmed: Boolean get() = verdict == Verdict.CONFIRMED
    }

    /** The sharp books' price pairs on CNO's game page for the bet, timed by the page's own update ([CnoBooksView.dataAtMs]). */
    fun fromCno(view: CnoBooksView?, rules: Rules): List<Quote> = view?.prices.orEmpty()
        .filter { it.code in rules.codes && it.twoSided }
        .map { Quote(it.code, it.odds!!, it.otherOdds!!, atMs = it.atMs ?: view!!.dataAtMs, via = "CNO's page") }

    /**
     * Judges [quotes] (the sharp books' prices for the bet and its other side) against Novig's price [novigOdds] ([live]: its taker fee applies).
     * [unavailable]: why no source could be asked, when [quotes] is empty for that reason.
     */
    fun judge(quotes: List<Quote>, novigOdds: Int, live: Boolean, rules: Rules, now: Long, unavailable: String? = null): Result {
        val mine = quotes.filter { it.code in rules.codes }
        if (mine.isEmpty()) {
            return if (unavailable != null) {
                Result(Verdict.UNAVAILABLE, reason = "couldn't get a fresh ${rules.label} price ($unavailable)", detail = unavailable)
            } else {
                Result(Verdict.NO_QUOTE, reason = "no ${rules.label} price for both sides of this exact bet", detail = "${rules.label} doesn't list this exact line and side")
            }
        }
        val judged = mine.mapNotNull { q ->
            val fair = CnoBooks.fairFor(q.odds, q.otherOdds) ?: return@mapNotNull null
            val age = q.atMs?.let { now - it }
            Judged(q, fair, CnoBooks.evAt(fair, novigOdds, live), age, fresh = age != null && age >= -CLOCK_SKEW_MS && age <= rules.maxAgeMs)
        }
        val fresh = judged.filter { it.fresh }
        if (fresh.isEmpty()) {
            val newest = judged.minByOrNull { it.ageMs ?: Long.MAX_VALUE }
            return Result(
                Verdict.STALE, judged,
                reason = "${rules.label}'s price for it is older than ${CycleLog.span(rules.maxAgeMs)} (or has no time)",
                detail = newest?.text() ?: "no usable price",
            )
        }
        val confirming = fresh.filter { it.ev > 0.0 && it.ev >= rules.minEv - 1e-9 }
        val against = fresh.filter { it.ev <= 0.0 }
        val detail = fresh.joinToString("; ") { it.text() }
        return when {
            confirming.isNotEmpty() && against.isEmpty() -> Result(Verdict.CONFIRMED, judged, detail = detail)
            confirming.isNotEmpty() -> Result(Verdict.NOT_CONFIRMED, judged, "the sharp books disagree about it (one says +EV, another doesn't)", detail)
            against.isNotEmpty() -> Result(Verdict.NOT_CONFIRMED, judged, "${rules.label}'s own devigged price doesn't show it +EV at Novig's price", detail)
            else -> Result(Verdict.NOT_CONFIRMED, judged, "${rules.label}'s own devigged price shows less than your ${percent(rules.minEv, sign = false)} minimum edge", detail)
        }
    }

    /**
     * The free first look (CNO's game page is already read for each candidate): a fresh sharp quote there that says the bet is NOT +EV skips it without
     * asking a feed (each ask costs credits or a day's allowance). Never confirms: that takes [judge] on a quote with its own time, unless [Rules.viaCno].
     * Null when CNO's page has nothing to say against it.
     */
    fun preVeto(cnoQuotes: List<Quote>, novigOdds: Int, live: Boolean, rules: Rules, now: Long): Result? {
        val r = judge(cnoQuotes, novigOdds, live, rules, now)
        return r.takeIf { it.verdict == Verdict.NOT_CONFIRMED && it.judged.any { j -> j.fresh && j.ev <= 0.0 } }
    }

    fun percent(v: Double, sign: Boolean = true): String = String.format(Locale.US, if (sign) "%+.1f%%" else "%.1f%%", v * 100)
}
