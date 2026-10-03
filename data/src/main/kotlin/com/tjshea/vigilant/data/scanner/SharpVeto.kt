package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.tracker.BetGrader
import java.util.Locale

/** What kind of bet a line is: which books are sharpest for it ([SharpVeto]), which kinds the auto-bet takes ([ScanSettings.autoBetKinds]), and Diagnostics' splits. */
enum class BetKind(val label: String) {
    PROP("Player props"), MONEYLINE("Moneylines"), SPREAD("Spreads"), TOTAL("Game totals"), TEAM_TOTAL("Team totals"),
    PERIOD("1st half / inning / set lines"), OTHER("Other");

    companion object {
        /** [market] ("Player Receiving Yards", "Total Points") and [bet] ("Juwan Johnson Under 39.5") as CNO or Novig name them. */
        fun of(market: String, bet: String): BetKind = when (val pick = BetGrader.pickOf(market, bet)) {
            is BetGrader.Pick.Prop -> PROP
            is BetGrader.Pick.Moneyline -> MONEYLINE
            is BetGrader.Pick.Spread -> if (pick.period.wholeGame()) SPREAD else PERIOD
            is BetGrader.Pick.Total -> if (pick.period.wholeGame()) TOTAL else PERIOD
            is BetGrader.Pick.TeamTotal -> TEAM_TOTAL
            is BetGrader.Pick.FirstSet -> PERIOD
            // The grader gives up on whatever it can't grade from a score (sacks, pitcher outs, quarters): told by the market's words instead.
            null -> byWords(market.lowercase(Locale.US))
        }

        /** A set spread or total sets covers the whole match. */
        private fun BetGrader.Period.wholeGame() = this == BetGrader.Period.GAME || this == BetGrader.Period.SETS

        private fun byWords(m: String): BetKind = when {
            THREE_WAY.containsMatchIn(m) -> OTHER
            PERIOD_WORDS.containsMatchIn(m) -> PERIOD
            m.contains("team total") -> TEAM_TOTAL
            PLAYER_WORDS.containsMatchIn(m) -> PROP
            m.contains("moneyline") || m == "money" -> MONEYLINE
            SPREAD_WORDS.containsMatchIn(m) -> SPREAD
            m.contains("total") -> TOTAL
            else -> OTHER
        }

        private val THREE_WAY = Regex("3-way|3 way|1x2|\\bdraw\\b")
        private val PERIOD_WORDS = Regex("quarter|\\bq[1-4]\\b|period|half|\\b[12]h\\b|inning|\\bf5\\b|first 5|1st 5|1st set|first set|\\bset [1-5]\\b")
        private val PLAYER_WORDS = Regex("player|batter|pitcher|goalie|goalscorer|anytime|to score|to record|touchdown")
        private val SPREAD_WORDS = Regex("spread|run line|puck line|handicap")
    }
}

/**
 * The sharp veto (Tj, 2026-10-02 17:01Z: "sharp veto instead of requirement. Only skip a bet if the sharpest book for that market says it is not +ev.
 * This must separate types of bets by which books are sharpest for those bet types"), pure. RESEARCH.md §66.
 *
 * For each kind of bet and sport, the books whose price is the sharpest, best first ([ranking]). The first of them that prices both sides on the bet's
 * book page (CNO's game page: free, already read for the book check) decides: its own two prices devigged worst case ([CnoBooks.fairFor], as the book
 * check does) and judged at Novig's price; an edge under the bar ([ScanSettings.sharpVetoMinEv], 1% by default; zero or less always) is a veto. When
 * none of them prices both sides there is no veto: the bet goes on its other criteria. A sister site counts as its company ("FDYW" for FanDuel).
 *
 * Why a bar over zero (RESEARCH.md §72): what a +EV bet keeps at the close is about the sharp book's own edge, not the consensus's. On 48,394 soccer
 * matches with Pinnacle's early and closing prices, bets the consensus called +2.5% or better kept +0.8% CLV when Pinnacle's own price gave them 0-1%
 * (not distinguishable from zero), +1.6% at 1-2%, +2.8% at 2-4% and +5.7% at 4%+; with one soft book's price, the consensus added nothing once the
 * sharp book's edge was known (CLV ≈ 0.46 × the sharp edge − 0.08 × the consensus edge).
 */
object SharpVeto {

    /** The bar's default: the sharpest book must give Novig's price at least a 1% edge. */
    const val DEFAULT_MIN_EV = 0.01

    enum class Sport { FOOTBALL, COLLEGE_FOOTBALL, BASKETBALL, COLLEGE_BASKETBALL, BASEBALL, HOCKEY, SOCCER, TENNIS, OTHER }

    /** The sport of a league as CNO or Novig names it ("NFL", "NCAAF", "MLB", "EPL", "ATP"). */
    fun sportOf(league: String): Sport {
        val key = Leagues.byNovigName(NovigBetFinder.novigLeague(league) ?: league.trim())?.oddsApiSportKey.orEmpty()
        val name = league.uppercase(Locale.US)
        return when {
            key.endsWith("_ncaaf") || name.contains("NCAAF") || name.contains("CFB") -> Sport.COLLEGE_FOOTBALL
            key.startsWith("americanfootball") || name.contains("NFL") -> Sport.FOOTBALL
            key.endsWith("_ncaab") || name.contains("NCAAB") || name.contains("CBB") -> Sport.COLLEGE_BASKETBALL
            key.startsWith("basketball") || name.contains("NBA") -> Sport.BASKETBALL
            key.startsWith("baseball") || name.contains("MLB") -> Sport.BASEBALL
            key.startsWith("icehockey") || name.contains("NHL") -> Sport.HOCKEY
            key.startsWith("soccer") || SOCCER_WORDS.any { name.contains(it) } -> Sport.SOCCER
            key.startsWith("tennis") || name.contains("ATP") || name.contains("WTA") || name.contains("TENNIS") -> Sport.TENNIS
            else -> Sport.OTHER
        }
    }

    private val SOCCER_WORDS = listOf("EPL", "MLS", "LIGA", "SERIE A", "BUNDESLIGA", "LIGUE 1", "UEFA", "CHAMPIONS", "PREMIER", "SOCCER", "FIFA")

    /**
     * The books, sharpest first, for [kind] in [sport] (CNO column codes). RESEARCH.md §66.2: player props: the exchanges Kalshi and ProphetX lead
     * (SmartStake's 600-million-move MLB props study), then the US books that originate prop numbers (FanDuel, Caesars on football and basketball;
     * DraftKings ahead of FanDuel on MLB props); Pinnacle and Circa post few props at low limits and aren't asked. Sides, totals, team totals and
     * period lines: Pinnacle, then Circa (Circa first in college); soccer and tennis: Pinnacle only.
     */
    fun ranking(kind: BetKind, sport: Sport): List<String> = when {
        kind == BetKind.PROP && sport == Sport.BASEBALL -> listOf("KI", "PX", "DK", "FD")
        kind == BetKind.PROP -> listOf("KI", "PX", "FD", "CZR")
        sport == Sport.SOCCER || sport == Sport.TENNIS -> listOf("PN")
        sport == Sport.COLLEGE_FOOTBALL || sport == Sport.COLLEGE_BASKETBALL -> listOf("CS", "PN")
        else -> listOf("PN", "CS")
    }

    enum class Verdict {
        /** The sharpest book on the page says +EV: no veto. */
        PASSED,

        /** The sharpest book on the page says it isn't +EV: skipped. */
        VETOED,

        /** None of the kind's sharp books prices both sides on the page: no veto. */
        NO_SHARP,
    }

    /** What the veto found: [book] (its code), its own fair chance and the EV it gives Novig's price. */
    data class Result(
        val verdict: Verdict,
        val kind: BetKind,
        val book: String? = null,
        val fair: Double? = null,
        val ev: Double? = null,
        /** The bar it was judged against ([ScanSettings.sharpVetoMinEv]). */
        val minEv: Double = 0.0,
    ) {
        val vetoed: Boolean get() = verdict == Verdict.VETOED

        /**
         * One general sentence (the auto-bet's skip report counts bets by reason: the book and the bar, never the bet's own numbers), null unless vetoed.
         * A sharp edge at or under zero says "isn't +EV"; a small one says it's under the bar.
         */
        val reason: String?
            get() = if (!vetoed) null else if ((ev ?: 0.0) <= 0.0) {
                "${CnoBooks.name(book!!)}, the sharpest book for ${kind.label.lowercase(Locale.US)}, says it isn't +EV at Novig's price"
            } else {
                "${CnoBooks.name(book!!)}, the sharpest book for ${kind.label.lowercase(Locale.US)}, gives Novig's price under the sharp veto's " +
                    "${SharpConfirm.percent(minEv, sign = false)} edge"
            }

        /** The numbers: "ProphetX −2.1% (devigged)", for the bet's record and Diagnostics. */
        val detail: String
            get() = if (book == null) "no sharp book for ${kind.label.lowercase(Locale.US)} prices both sides" else
                "${CnoBooks.name(book)} ${SharpConfirm.percent(ev ?: 0.0)} (devigged, fair ${String.format(Locale.US, "%.1f%%", (fair ?: 0.0) * 100)})"
    }

    /**
     * [view]: the bet's book page; [novigOdds]: Novig's price now (American); [live]: Novig's taker fee applies; [minEv]: the bar the sharpest book's own
     * edge must reach ([ScanSettings.sharpVetoMinEv]; 0 = any +EV); [judged]: the book being bet.
     */
    fun judge(view: CnoBooksView?, kind: BetKind, sport: Sport, novigOdds: Int, live: Boolean, minEv: Double, judged: String = CnoBooks.NOVIG): Result {
        val bar = minEv.coerceAtLeast(0.0)
        val prices = view?.prices.orEmpty().filter { it.twoSided && it.code != judged }
        for (code in ranking(kind, sport)) {
            val p = prices.firstOrNull { CnoBooks.company(it.code) == code } ?: continue
            val fair = CnoBooks.fairFor(p.odds!!, p.otherOdds!!) ?: continue
            val ev = CnoBooks.evAt(fair, novigOdds, live)
            return Result(if (passes(ev, bar)) Verdict.PASSED else Verdict.VETOED, kind, code, fair, ev, bar)
        }
        return Result(Verdict.NO_SHARP, kind, minEv = bar)
    }

    /** [judge] for a bet as CNO names it ([league], [market], [bet]). */
    fun judge(view: CnoBooksView?, league: String, market: String, bet: String, novigOdds: Int, live: Boolean, minEv: Double, judged: String = CnoBooks.NOVIG): Result =
        judge(view, BetKind.of(market, bet), sportOf(league), novigOdds, live, minEv, judged)

    /** A sharp book's own edge [ev] clears the bar [minEv]: above zero always, and at least the bar. */
    fun passes(ev: Double, minEv: Double): Boolean = ev > 0.0 && ev >= minEv - 1e-12
}
