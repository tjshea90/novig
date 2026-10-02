package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.LivePrice
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.PresetRules
import com.tjshea.vigilant.data.scanner.Presets
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.Serializable

/**
 * Everything about a bet as it was placed (Tj, 2026-10-02 17:01Z: "record all types of information on the bet as placed, such as odds, books in agreement, time
 * before game start, percent EV, and more. The more information logged the better … Remember the goal is profit and positive EV and clv"). Kept on the bet
 * ([TrackedBet.atBet]) and never changed by a re-check (those write [TrackedBet.books] and the "now" fields), so Diagnostics can split the close and the
 * results by what the bet looked like when it was made. RESEARCH.md §66.4. Null fields: not known for that kind of bet.
 */
@Serializable
data class AtBet(
    val atMs: Long,
    /** The app's version (BuildConfig.VERSION_NAME). */
    val version: String? = null,
    /** How it was placed: [HOW_AUTO] (auto-bet), [HOW_SHEET] (Bet sheet, through the API), [HOW_MARKED] (✓ on a card), [HOW_ALERT] (✓ on a notification). */
    val how: String,
    /** CNO, Vigilant or ParlayAPI. */
    val scanner: String,
    /** The preset in force ([Presets.active]), or the last one applied with " (changed)". */
    val preset: String? = null,
    /** The auto-bet, veto, alert and CNO rules in force ([PresetRules.summary]). */
    val rules: String? = null,
    val league: String = "",
    /** [SharpVeto.Sport] name. */
    val sport: String = "",
    /** [BetKind] name. */
    val kind: String = "",
    val minutesToStart: Long? = null,
    val live: Boolean = false,
    /** Novig's price taken, American, and the other side's when known. */
    val american: Int? = null,
    val otherAmerican: Int? = null,
    /** Dollars Novig had at that price. */
    val available: Double? = null,
    /** How old Novig's price was. */
    val novigAgeSec: Long? = null,
    /** The EV the card showed at Novig's price (CNO's fair, or Vigilant's), and that fair (probability and American). */
    val ev: Double? = null,
    val fair: Double? = null,
    val fairAmerican: Int? = null,
    /** CNO's own row: its book count, one-way flag, and how old its list was. */
    val cnoBooks: Int? = null,
    val cnoOneWay: Boolean? = null,
    val cnoListAgeSec: Long? = null,
    /** The app's own book check ([CnoBooks.check]): fair, EV, companies pricing both sides, one side only, agreeing, verdict, and the page's age. */
    val checkFair: Double? = null,
    val checkEv: Double? = null,
    val twoSided: Int? = null,
    val oneSided: Int? = null,
    val agreeing: Int? = null,
    val verdict: String? = null,
    val pageAgeSec: Long? = null,
    /** Every book on the bet's page: both prices, its own fair and the EV it gives Novig's price. */
    val books: List<AtBetBook> = emptyList(),
    /** The books (names) pricing both sides whose own fair says it isn't +EV. */
    val dissent: List<String> = emptyList(),
    /** The sharp veto's verdict ([SharpVeto.Verdict] name), the book that decided and the EV it gave. */
    val sharpVerdict: String? = null,
    val sharpBook: String? = null,
    val sharpEv: Double? = null,
    /** A sharp confirmation's words, when that mode is on. */
    val sharpConfirm: String? = null,
    /** Vigilant's own fair line: its method, the sharp books and the books used, how many. */
    val fairMethod: String? = null,
    val fairBooks: List<String> = emptyList(),
    val fairSharp: List<String> = emptyList(),
    /** How old the fair line's quotes were (newest, oldest). */
    val fairAgeSec: Long? = null,
    /** The stake rule, the full-Kelly share of the bankroll this edge calls for, the stake, the bankroll and the wallet. */
    val stakeRule: String? = null,
    val fullKelly: Double? = null,
    val stake: Double? = null,
    val bankroll: Double? = null,
    val wallet: Double? = null,
) {
    companion object {
        const val HOW_AUTO = "auto"
        const val HOW_SHEET = "sheet"
        const val HOW_MARKED = "marked"
        const val HOW_ALERT = "alert"
    }
}

/** One book's prices for the bet as placed. */
@Serializable
data class AtBetBook(val book: String, val odds: Int? = null, val other: Int? = null, val fair: Double? = null, val ev: Double? = null)

/** Builds [AtBet] for each way a bet is made. Pure. */
object AtBets {

    private fun secs(from: Long?, now: Long): Long? = from?.let { ((now - it) / 1000L).coerceAtLeast(0L) }

    private fun minutes(startsAtMs: Long?, now: Long): Long? = startsAtMs?.let { (it - now) / 60_000L }

    private fun price(american: Int): Double = 1.0 / Odds.americanToDecimal(american)

    private fun fullKelly(fair: Double?, cost: Double): Double? =
        fair?.takeIf { cost in 0.0..0.9999 }?.let { ((it - cost) / (1.0 - cost)).coerceAtLeast(0.0) }

    /** The preset in force or last applied, and the rules in force. */
    private fun preset(s: ScanSettings): Pair<String?, String> =
        (Presets.active(s)?.name ?: s.presetName?.let { "$it (changed)" }) to PresetRules.of(s).summary()

    /**
     * A CrazyNinjaOdds-shaped bet ([row]: CNO's or ParlayAPI's) at Novig's price [novig] (else the row's), with its book page [view] and the book
     * check [check] (worked out from [view] when not given) and the veto [veto] (judged here when not given).
     */
    fun cno(
        row: CnoRow,
        live: Boolean,
        view: CnoBooksView?,
        check: CnoBooks.Check?,
        novig: LivePrice?,
        listAtMs: Long?,
        s: ScanSettings,
        now: Long,
        how: String,
        scanner: String,
        version: String?,
        stake: Double? = null,
        wallet: Double? = null,
        veto: SharpVeto.Result? = null,
        sharpConfirm: String? = null,
    ): AtBet {
        val american = novig?.american ?: row.odds
        val priced = row.copy(odds = american)
        val c = check ?: view?.let { CnoBooks.check(it, priced, live) }
        val cost = price(american)
        val v = veto ?: SharpVeto.judge(view, row.league, row.market, row.bet, american, live)
        val usable = view?.prices.orEmpty().filter { CnoBooks.usableForFair(it.code) }
        val books = view?.prices.orEmpty().map { p ->
            val fair = if (p.twoSided) CnoBooks.fairFor(p.odds!!, p.otherOdds!!) else null
            AtBetBook(CnoBooks.name(p.code), p.odds, p.otherOdds, fair, fair?.let { CnoBooks.evAt(it, american, live) })
        }
        val dissent = usable.filter { it.twoSided }.mapNotNull { p ->
            val fair = CnoBooks.fairFor(p.odds!!, p.otherOdds!!) ?: return@mapNotNull null
            p.name.takeIf { CnoBooks.evAt(fair, american, live) <= 0.0 }
        }
        val fair = CnoChecks.fairProbability(row)
        val (preset, rules) = preset(s)
        return AtBet(
            atMs = now, version = version, how = how, scanner = scanner, preset = preset, rules = rules,
            league = row.league, sport = SharpVeto.sportOf(row.league).name, kind = BetKind.of(row.market, row.bet).name,
            minutesToStart = minutes(row.startsAtMs, now), live = live,
            american = american, otherAmerican = view?.prices?.firstOrNull { it.code == CnoBooks.NOVIG }?.otherOdds,
            available = novig?.available ?: row.available, novigAgeSec = secs(novig?.atMs, now),
            ev = novig?.ev ?: row.ev, fair = fair, fairAmerican = row.fairOdds,
            cnoBooks = row.books, cnoOneWay = row.oneWay, cnoListAgeSec = secs(listAtMs, now),
            checkFair = c?.fairProbability, checkEv = c?.ev, twoSided = c?.twoSided, oneSided = c?.oneSided, agreeing = c?.agreeing, verdict = c?.verdict?.name,
            pageAgeSec = secs(view?.dataAtMs, now),
            books = books, dissent = dissent,
            sharpVerdict = v.verdict.name, sharpBook = v.book?.let(CnoBooks::name), sharpEv = v.ev, sharpConfirm = sharpConfirm,
            stakeRule = s.autoBetStake.label.takeIf { how == AtBet.HOW_AUTO }, fullKelly = fullKelly(c?.fairProbability ?: fair, cost),
            stake = stake, bankroll = s.bankroll, wallet = wallet,
        )
    }

    /** One of Vigilant's own bets ([o]: its scan's line, priced against its own fair line). */
    fun opportunity(o: Opportunity, s: ScanSettings, now: Long, how: String, version: String?, stake: Double? = null, wallet: Double? = null): AtBet {
        val q = o.quote
        val f = o.fair
        val (preset, rules) = preset(s)
        val (newest, oldest) = f?.usedUpdates ?: (null to null)
        return AtBet(
            atMs = now, version = version, how = how, scanner = BetTracker.SOURCE_VIGILANT, preset = preset, rules = rules,
            league = o.league.displayName, sport = SharpVeto.sportOf(o.league.novigName).name, kind = BetKind.of(o.marketLabel, o.selection).name,
            minutesToStart = minutes(o.event.startsTs, now),
            american = q?.let { Odds.probabilityToAmerican(it.price.coerceIn(0.001, 0.999)) },
            available = o.depth?.dollarCost, novigAgeSec = secs(o.bookFetchedAtMs, now),
            ev = q?.evPercent, fair = o.fairProbability, fairAmerican = q?.fairAmerican,
            fairMethod = f?.let { "${it.sourceUsed}/${it.method}" }, fairBooks = f?.booksUsed.orEmpty(), fairSharp = f?.sharpBooksUsed.orEmpty(),
            fairAgeSec = secs(oldest ?: newest ?: o.fairUpdatedMs, now),
            fullKelly = q?.kellyFraction, stake = stake, bankroll = s.bankroll, wallet = wallet,
        )
    }

    /** A bet marked placed from a push alert's ✓: what the alert carried. */
    fun alert(a: com.tjshea.vigilant.data.alerts.EvAlert, now: Long, version: String? = null): AtBet = AtBet(
        atMs = now, version = version, how = AtBet.HOW_ALERT, scanner = if (a.isCno) BetTracker.SOURCE_CNO else BetTracker.SOURCE_VIGILANT,
        league = a.league, sport = SharpVeto.sportOf(a.league).name, kind = BetKind.of(a.market, a.bet).name,
        minutesToStart = minutes(a.startsAtMs, now), live = a.live, american = a.american, ev = a.ev, fair = a.fair,
        twoSided = a.books.takeIf { it > 0 }, agreeing = a.agreeing.takeIf { a.books > 0 },
        fullKelly = fullKelly(a.fair, price(a.american)), stake = a.stake,
    )
}
