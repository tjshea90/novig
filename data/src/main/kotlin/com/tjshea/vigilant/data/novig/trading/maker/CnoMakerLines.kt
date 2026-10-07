package com.tjshea.vigilant.data.novig.trading.maker

import com.tjshea.vigilant.data.cno.CnoBookPrice
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpVeto
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import java.util.Locale

/**
 * Bids priced from CrazyNinjaOdds alone, Vigilant's scan off (Tj, 2026-10-07: "find positive EV bids based on bets from cno and make bids for them automatically";
 * RESEARCH.md §112-§114). The bid desk ([MakerDesk]) takes any list of [MakerLine]s; this builds them from what CNO shows and what Novig's own book says, pure:
 *
 *  - **the fair** is what CNO's game page shows ([CnoBooks.check]'s own rule): each company's two-sided price devigged the worst way, one vote a company, the lower of their
 *    mean and median; for the side CNO listed it is also never above CNO's own fair for the row. The books that are SHARP for the kind of bet ([SharpVeto.ranking]: Kalshi and
 *    ProphetX, then FanDuel and Caesars for props; Pinnacle, Circa for sides) are [MakerLine.sharpFairs]: a bid sits under the lower of the blend and the sharpest of them
 *    ([MakerRules.anchorOf]) and needs one ([narrow]: `requireSharp`);
 *  - **both sides of a page** are lines: the side CNO listed (+EV at Novig's ask) and its complement, which is where most bids live (a bid is below the fair, so it is posted where
 *    the ask is NOT +EV; CNO's list holds +EV sides only);
 *  - **the age** is the OLDER of the list's and the page's own "Last Updated" ([CnoMakerLines.fairAsOf]); an unknown list age is NOT fresh (no bid, a resting one comes down), and a
 *    page that doesn't say its own is taken as published [UNKNOWN_PAGE_AGE_MS] before it was read. CNO's "Last Updated" is a lower bound on a price's age (§113), so the limit is a
 *    setting and short ([ScanSettings.makerCnoMaxAgeSeconds]);
 *  - **Novig's offer and queue** are its own book's ([MakerLine.offer], [MakerLine.bestBid], [MakerLine.bidLevels]); a side with no book read gets no line.
 */
object CnoMakerLines {

    /** Bids carry this source: the Tracker's Scanner filter, the Bids tab and the study tell them from Vigilant's. */
    const val SOURCE = BetTracker.SOURCE_CNO

    /**
     * A game page that says nothing of its own age is taken to have been published this long before it was read: above CNO's longest measured gap between publishes (33 s, §113),
     * so a page whose clock goes missing reads old, not fresh.
     */
    const val UNKNOWN_PAGE_AGE_MS = 45_000L

    /** CNO-priced bids live a minute or less beyond the limit, so a bid is judged and re-posted on shorter terms than a bid priced from Vigilant's scan (30 s earlier). */
    const val MIN_LIFE_MS = 45_000L
    const val REFRESH_BEFORE_MS = 45_000L

    /** The shortest and longest limit a saved setting can hold. */
    private const val MIN_MAX_AGE_S = 30
    private const val MAX_MAX_AGE_S = 300

    /** [rules] for bids priced from CNO: a sharp book on the page must price the line and agree, the margin is under the sharpest fair, and the timings are short. */
    fun narrow(rules: MakerRules): MakerRules = rules.copy(
        requireSharp = true, anchorSharp = true, sharpVeto = true,
        minLifeMs = MIN_LIFE_MS, refreshBeforeMs = REFRESH_BEFORE_MS, lowUsageBooks = emptySet(),
    )

    /** [ScanSettings.makerCnoMaxAgeSeconds] as the limit in ms. */
    fun maxAgeMs(s: ScanSettings): Long = s.makerCnoMaxAgeSeconds.coerceIn(MIN_MAX_AGE_S, MAX_MAX_AGE_S) * 1000L

    /**
     * One CNO game page with what a bid on its two sides needs: [row] is the side CNO listed (or a stand-in for a side Vigilant has a bid on), [outcomeId] its outcome in [market],
     * [book] Novig's book for that market, [view] CNO's page for the bet, [listAtMs] when the list that showed the row was last updated on CNO's side (null = its age is unknown).
     */
    class Page(
        val row: CnoRow,
        val outcomeId: String,
        val market: NovigMarket,
        val book: NovigBook?,
        val view: CnoBooksView?,
        val listAtMs: Long?,
    )

    /** The lines built, and why pages gave none (reason → how many sides), for the Bids tab and Diagnostics. */
    data class Result(val lines: List<MakerLine>, val skipped: Map<String, Int>)

    /** The older of the list's and the page's data time, or null when the list's is unknown: the age of the fair ([MakerLine.fairAsOfMs]). */
    fun fairAsOf(listAtMs: Long?, pageAtMs: Long): Long? = listAtMs?.let { minOf(it, pageAtMs) }

    /** When [view]'s data was last updated on CNO's side; its read time less [UNKNOWN_PAGE_AGE_MS] when the page didn't say. */
    fun pageAt(view: CnoBooksView): Long = if (view.cnoAgeSeconds != null) view.dataAtMs else view.fetchedAtMs - UNKNOWN_PAGE_AGE_MS

    const val NO_PAGE = "CNO's game page for it hasn't been read"
    const val NO_BOOK = "Novig's book for it hasn't been read"
    const val NOT_ON_PAGE = "CNO's game page doesn't list this side"
    const val NO_PRICES = "No book on CNO's page prices both sides"
    const val NO_OTHER_SIDE = "Novig has no second outcome in this market"

    /**
     * The lines for [pages] at [now]: each page's listed side and, where Novig's market has a second outcome, its complement. [always]: sides a bid is up on (their lines are built
     * whatever the checks say; the desk then takes the bid down for the reason it finds). [unavailable]: why a side must not be bid on (a player who is out), or null.
     */
    fun from(
        pages: List<Page>,
        settings: ScanSettings,
        now: Long,
        unavailable: (CnoRow) -> String? = { null },
    ): Result {
        val limit = maxAgeMs(settings)
        val skipped = LinkedHashMap<String, Int>()
        fun skip(why: String, n: Int = 1) = skipped.merge(why, n, Int::plus)
        val out = ArrayList<MakerLine>()
        for (p in pages) {
            val view = p.view
            if (view == null) { skip(NO_PAGE); continue }
            val book = p.book
            if (book == null) { skip(NO_BOOK); continue }
            val first = nameEq(view.bet, p.row.bet)
            val second = !first && view.otherBet != null && nameEq(view.otherBet!!, p.row.bet)
            if (!first && !second) { skip(NOT_ON_PAGE); continue }
            val pageAt = pageAt(view)
            val asOf = fairAsOf(p.listAtMs, pageAt)
            val newest = p.listAtMs?.let { maxOf(it, pageAt) } ?: pageAt
            val old = asOf == null || now - asOf > limit
            // Side 0 is the row's own; side 1 its complement. A row that is the page's second line flips every price pair.
            for (side in 0..1) {
                val outcomeId = if (side == 0) p.outcomeId else p.market.otherOutcome(p.outcomeId)?.outcomeId
                if (outcomeId == null) { skip(NO_OTHER_SIDE); continue }
                val name = if (side == 0) p.row.bet else (if (first) view.otherBet else view.bet)
                if (name == null) { skip(NOT_ON_PAGE); continue }
                val flipped = (side == 0) != first
                val kind = BetKind.of(p.row.market, name)
                val fair = fairsOf(view, flipped, SharpVeto.ranking(kind, SharpVeto.sportOf(p.row.league)).toSet())
                val blend = fair.blend
                if (blend == null) { skip(NO_PRICES); continue }
                val own = if (side == 0) CnoChecks.fairProbability(p.row) else null
                val lineFair = if (own != null) minOf(blend, own) else blend
                val offer = book.takeLadder(p.market, outcomeId).minOfOrNull { it.price }
                val levels = book.bidsByOutcome[outcomeId].orEmpty()
                val league = Leagues.byNovigName(NovigBetFinder.novigLeague(p.row.league) ?: p.row.league.trim())?.displayName ?: p.row.league
                val starts = listOfNotNull(p.row.startsAtMs, p.market.startsTs.takeIf { it > 0 }).minOrNull() ?: p.market.startsTs
                out += MakerLine(
                    market = p.market, outcomeId = outcomeId, startsTs = starts, league = league, eventName = p.row.event, marketLabel = p.row.market, selection = name, kind = kind,
                    fair = lineFair, fairAsOfMs = asOf, fairNewestMs = newest, fairOld = old, books = fair.companies, fairBooks = fair.names,
                    bookFairs = fair.fairs, sharpFairs = fair.sharpFairs, offer = offer, bestBid = levels.firstOrNull()?.price,
                    live = p.row.startsAtMs?.let { it <= now } == true, source = SOURCE,
                    basis = FairBasis(FairBasis.SOURCE_CNO, sharp = fair.sharpNames, books = fair.companies),
                    gameUrl = p.row.gameUrl, bookAtMs = book.fetchedAtMs, bidLevels = levels,
                    unavailable = unavailable(p.row), fairMaxAgeMs = limit, listAtMs = p.listAtMs, pageAtMs = pageAt,
                )
            }
        }
        return Result(out, skipped)
    }

    /** One side's fairs from a page: the blend ([CnoBooks.consensus] of one fair a company), the companies' own fairs, and the sharp ones among them. */
    class Fairs(
        val blend: Double?,
        val fairs: List<Double>,
        val names: List<String>,
        val sharpFairs: List<Double>,
        val sharpNames: List<String>,
    ) {
        val companies: Int get() = fairs.size
    }

    /**
     * Every book on [view] that prices both sides (Novig itself and the pick'em apps aside: [CnoBooks.usableForFair]), each company's sites averaged into one fair for the side
     * ([flipped]: the page's second line), worst-case devigged. [sharpCodes]: the book columns that count as sharp for this kind of bet.
     */
    fun fairsOf(view: CnoBooksView, flipped: Boolean, sharpCodes: Set<String>): Fairs {
        class One(val price: CnoBookPrice, val fair: Double)
        val ones = view.prices.filter { CnoBooks.usableForFair(it.code) && it.twoSided }.mapNotNull { p ->
            val f = (if (flipped) CnoBooks.fairFor(p.otherOdds!!, p.odds!!) else CnoBooks.fairFor(p.odds!!, p.otherOdds!!)) ?: return@mapNotNull null
            One(p, f)
        }
        val byCompany = ones.groupBy { CnoBooks.company(it.price.code) }
        val fairs = byCompany.values.map { v -> v.map { it.fair }.average() }
        val names = byCompany.values.map { v -> CnoBooks.name(v.first().price.code) }
        val sharp = byCompany.values.mapNotNull { v -> v.filter { it.price.code in sharpCodes }.takeIf { it.isNotEmpty() } }
        return Fairs(
            blend = CnoBooks.consensus(fairs), fairs = fairs, names = names,
            sharpFairs = sharp.map { v -> v.map { it.fair }.average() }, sharpNames = sharp.map { v -> CnoBooks.name(v.first().price.code) },
        )
    }

    private fun nameEq(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

    /** "12 s" for the Bids tab: the age of [atMs] at [now]. */
    fun ageText(atMs: Long?, now: Long): String = atMs?.let { String.format(Locale.US, "%d s", ((now - it) / 1000L).coerceAtLeast(0L)) } ?: "unknown"
}
