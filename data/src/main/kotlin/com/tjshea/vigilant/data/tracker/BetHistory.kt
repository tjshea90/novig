package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.trading.maker.MakerBid
import com.tjshea.vigilant.engine.BookCompany
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One line per bet or bid for the files Tj shares with Claude and the daily copies saved to Downloads/Vigilant (Tj, 2026-10-11: "send every single bet and bid I ever made ... odds when placed,
 * bet or bid, auto or manual, EV when placed, time before the game starts, books behind the fair odds, whether a sharp book agreed, closing odds, CLV, won lost or pushed, liquidity, type of bet,
 * sport, rating ... plus any other information"). RESEARCH.md §132.
 *
 * [Row] holds the facts every analysis wants and stays small (about 1 KB a line), so a year of hundreds of bets a day is tens of megabytes in a few parts; [Detail] (the daily copies only)
 * adds each book's own price and the rules in force. Every number is the Tracker's own ([ClosingLine], [BetTracker], [BetDims]): a file can never disagree with the Stats screen. A field that
 * isn't known for a bet is left out of its line (never 0 or "").
 *
 * Times are America/New_York ISO-8601 with the offset (the app's own day boundary, [com.tjshea.vigilant.data.diag.DataKeeper]); `Ms` fields are epoch milliseconds. EV, CLV, ROI and
 * the fair are fractions (0.03 = +3%).
 */
object BetHistory {

    /** The file format's version, written in the README and each line's reader may check. */
    const val FORMAT = 1

    val ET: ZoneId = ZoneId.of("America/New_York")
    private val ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun iso(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ET).format(ISO)
    fun day(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ET).toLocalDate().toString()

    @Serializable
    data class Row(
        val id: String,
        val placed: String,
        val placedMs: Long,
        val day: String,
        /** "bet" (a taker order) or "bid" (a make order that filled). */
        val made: String,
        /** "auto" (the auto-bet or auto-bid) or "manual" (a tap, the Bet sheet, a ✓ mark, the Post button) or "lock". */
        val how: String,
        /** The record's own word for how: auto, sheet, marked, alert, bid, study, or imported. */
        val origin: String? = null,
        val scanner: String,
        val sport: String? = null,
        val league: String,
        /** Type of bet: Player props, Moneylines, Spreads, Game totals, Team totals, 1st half / inning / set lines, Other. */
        val kind: String,
        /** The grader's finer type: Moneyline, Spread, Total, Team total, Player props, 1st half / set spread, ... */
        val betType: String? = null,
        val market: String,
        val event: String,
        val selection: String,
        val starts: String,
        val startsMs: Long,
        /** The start the bet was logged with, when the game's real start turned out different (a delay, or a game seen in play before its listed start), and what happened. */
        val startsLoggedMs: Long? = null,
        val startsNote: String? = null,
        /** Minutes before the game starts when placed (negative: after it started). */
        val leadMin: Long? = null,
        val live: Boolean = false,
        /** American odds as placed, the price paid per $1 of payout (taker fee included), and the other side's odds. */
        val odds: Int? = null,
        val cost: Double,
        val otherOdds: Int? = null,
        /** EV when placed, the fair chance behind it and its American odds. */
        val ev: Double? = null,
        val fair: Double? = null,
        val fairOdds: Int? = null,
        /** Companies pricing both sides behind the fair when placed (sister sites counted once), and how many of them said +EV alone. */
        val books: Int? = null,
        val booksAgree: Int? = null,
        /** "agree": a sharp book priced it and said +EV; "against": one said it was not +EV; "none": no sharp book priced it; absent: not recorded. */
        val sharp: String? = null,
        val sharpBook: String? = null,
        val sharpEv: Double? = null,
        /** The app's 1-5 star rating in halves, and "recorded" (rated live when placed) or "estimated" (worked out afterwards from the record). */
        val stars: Double? = null,
        val starsBasis: String? = null,
        /** Dollars Novig had at the price when placed, and how old that read was. */
        val liquidity: Double? = null,
        val novigAgeSec: Long? = null,
        val stake: Double,
        val contracts: Long? = null,
        val paid: Double? = null,
        val fee: Double? = null,
        /** The close: the fair chance of the bet's side at the start, as American odds, where it came from, when it was read and how long before the start. */
        val closeFair: Double? = null,
        val closeOdds: Int? = null,
        val closeSource: String? = null,
        val closeReadMs: Long? = null,
        val closeLeadSec: Long? = null,
        /**
         * What the close is made of: `read:cno` (CrazyNinjaOdds' consensus read before the start), `read:vigilant` (Vigilant's own fair line), `read:both` (their average), `history:pinnacle`
         * (Pinnacle's price at the start from ParlayAPI, SGO or OddsPapi), `history:espn`, `history:novig-trades` (the last trades on Novig, own fills included) or `history:other`.
         */
        val closeBasis: String? = null,
        /** true: a price from a source other than the one that found the bet (Pinnacle or ESPN history); false: a read of the same consensus that priced the bet, or Novig's own trades. */
        val closeIndependent: Boolean? = null,
        /** Seconds between placing the bet and the read that became its close (a read must be 60+ s after the bet to count). */
        val closeReadAfterBetSec: Long? = null,
        /** Novig's own last price before the start (American), when it was read in the last 15 minutes before it; its age in seconds. */
        val novigCloseOdds: Int? = null,
        val novigCloseAgeSec: Long? = null,
        /** Closing-line value: closeFair / cost - 1. Present only for a true close ([ClosingLine]); beatClose is its sign. */
        val clv: Double? = null,
        val beatClose: Boolean? = null,
        /** won, lost, push, void, fmv or open; the profit in dollars, profit / stake, and when and by what it was settled. */
        val result: String,
        val profit: Double? = null,
        val roi: Double? = null,
        val settledMs: Long? = null,
        val settledBy: String? = null,
        val gradeNote: String? = null,
        /** An EV so far from the rest that the app leaves it out of its averages. */
        val outlier: Boolean = false,
        val preset: String? = null,
        val appVersion: String? = null,
        val fairMethod: String? = null,
        val fairAgeSec: Long? = null,
        val pageAgeSec: Long? = null,
        val dissent: Int? = null,
        val trap: String? = null,
        val fullKelly: Double? = null,
        val bankroll: Double? = null,
        val wallet: Double? = null,
        val marketId: String? = null,
        val outcomeId: String? = null,
        val lockFor: String? = null,
        /** A bid's own history (made = "bid"). */
        val bid: BidFacts? = null,
        /** The daily copies only. */
        val detail: Detail? = null,
    )

    /** What the bid that became this fill did: when it was posted, how fast it was taken, and whether the market walked away from it. */
    @Serializable
    data class BidFacts(
        val postedMs: Long,
        val postedLeadMin: Long? = null,
        val fillDelayMs: Long? = null,
        val margin: Double? = null,
        val evAtFair: Double? = null,
        val fairAtFill: Double? = null,
        /** EV at the fair of the first scan after the fill: negative = picked off. */
        val fillEv: Double? = null,
        val bestBidAtPost: Double? = null,
        val offerAtPost: Double? = null,
        val fairBooks: List<String> = emptyList(),
        val focus: String? = null,
        val obscure: Boolean = false,
        val source: String? = null,
    )

    @Serializable
    data class Detail(
        /** "Book: odds/other-side odds (fair chance)" for every book on the page when placed. */
        val bookPrices: List<String> = emptyList(),
        val dissent: List<String> = emptyList(),
        val fairBooks: List<String> = emptyList(),
        val fairSharp: List<String> = emptyList(),
        val rules: String? = null,
        val sharpConfirm: String? = null,
    )

    /** Every bid posted, filled or not, one line each (the Tracker holds only the fills). */
    @Serializable
    data class BidRow(
        val clientId: String,
        val postedMs: Long,
        val posted: String,
        val day: String,
        val scanner: String,
        val auto: Boolean,
        val league: String,
        val sport: String? = null,
        val kind: String,
        val market: String,
        val event: String,
        val selection: String,
        val startsMs: Long,
        val postedLeadMin: Long? = null,
        val price: Double,
        val odds: Int,
        val contracts: Long,
        val ev: Double,
        val fair: Double,
        val margin: Double,
        val books: Int,
        val status: String,
        val filled: Long,
        val paid: Double,
        val fillPct: Double,
        val endedMs: Long? = null,
        val why: String? = null,
        val fillDelayMs: Long? = null,
        val fairAtFill: Double? = null,
        val fillEv: Double? = null,
        val bestBidAtPost: Double? = null,
        val offerAtPost: Double? = null,
        val fairBooks: List<String> = emptyList(),
        val focus: String? = null,
        val obscure: Boolean = false,
        val betId: String? = null,
    )

    val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun fairOddsOf(p: Double?): Int? = p?.takeIf { it in 0.001..0.999 }?.let { Odds.probabilityToAmerican(it) }

    /** [b] as one [Row] as of [now]; [bid] is the make order it filled from (joined by [TrackedBet.id] = [MakerBid.betId]); [detail] adds the daily copies' extras. */
    fun row(b: TrackedBet, bid: MakerBid?, now: Long, detail: Boolean = false): Row {
        val a = b.atBet
        val clv = ClosingLine.clv(b, now)
        val close = ClosingLine.closeOf(b, now)
        val stars = BetDims.stars(b)
        val sharp = BetDims.sharp(b)
        val books = BetDims.books(b)
        val lead = BetDims.leadMinutes(b)
        val americanPlaced = b.american ?: a?.american
        val paid = b.paid
        val profit = b.profit
        // A voided bet never happened: no close, no CLV (as every in-app stat).
        val voided = b.status == BetStatus.VOID
        val captured = close?.second == ClosingLine.SOURCE_CAPTURED
        // A captured close knows when it was read; a history close is the price AT the start, so it has no read time (the back-fill's lookup time is days later and says nothing about the price).
        val readMs = if (captured) b.closingSeenAtMs else null
        val basis = close?.let { (_, src) -> closeBasis(b, src) }
        val novigAge = b.novigCloseAtMs?.takeIf { b.novigClose != null && b.startsTs - it in 0..ClosingLine.TRUE_CLOSE_MS && now >= b.startsTs }?.let { (b.startsTs - it) / 1000L }
        return Row(
            id = b.id.take(8), placed = iso(b.createdAtMs), placedMs = b.createdAtMs, day = day(b.createdAtMs),
            made = BetOrBid.of(b).word,
            how = if (b.isLock) "lock" else BetDims.valueOf(b, BetDim.HOW, now).lowercase(),
            origin = a?.how ?: if (b.imported) "imported" else null,
            scanner = TrackerBreakdown.keyOf(b, TrackerBreakdown.By.SCANNER),
            sport = BetDims.sportLabel(b).lowercase(),
            league = b.league, kind = BetDims.kindLabel(b),
            betType = TrackerBreakdown.marketOf(b),
            market = b.marketLabel, event = b.eventName, selection = b.selection,
            starts = iso(b.startsTs), startsMs = b.startsTs, startsLoggedMs = b.startsLoggedTs, startsNote = b.startsNote, leadMin = lead, live = a?.live == true || (lead != null && lead < 0),
            odds = americanPlaced, cost = b.cost, otherOdds = a?.otherAmerican,
            ev = b.evPercentAtBet, fair = b.fairAtBet, fairOdds = fairOddsOf(b.fairAtBet),
            books = books, booksAgree = a?.agreeing,
            sharp = sharp, sharpBook = a?.sharpBook, sharpEv = a?.sharpEv,
            stars = stars?.first, starsBasis = stars?.let { if (it.second) "estimated" else "recorded" },
            liquidity = a?.available, novigAgeSec = a?.novigAgeSec,
            stake = b.stake, contracts = b.contracts, paid = paid, fee = b.fee,
            closeFair = close?.first?.takeIf { !voided }, closeOdds = fairOddsOf(close?.first?.takeIf { !voided }), closeSource = close?.second?.takeIf { !voided },
            closeReadMs = readMs?.takeIf { !voided }, closeLeadSec = readMs?.takeIf { !voided }?.let { r -> (b.startsTs - r) / 1000L },
            closeBasis = basis?.takeIf { !voided }, closeIndependent = close?.takeIf { !voided }?.let { ClosingLine.independent(it.second) },
            closeReadAfterBetSec = readMs?.takeIf { !voided }?.let { r -> (r - b.createdAtMs) / 1000L },
            novigCloseOdds = novigAge?.let { fairOddsOf(b.novigClose) }.takeIf { !voided }, novigCloseAgeSec = novigAge.takeIf { !voided },
            clv = clv.takeIf { !voided }, beatClose = clv?.takeIf { !voided }?.let { it > 0.0 },
            result = if (b.status == BetStatus.PENDING) "open" else b.status.name.lowercase(),
            profit = profit, roi = profit?.takeIf { b.stake > 0 }?.let { it / b.stake },
            settledMs = b.settledAtMs, settledBy = b.settledBy,
            gradeNote = b.gradeNote?.takeIf { b.status != BetStatus.PENDING }?.take(160),
            outlier = b.isOutlier,
            preset = a?.preset, appVersion = a?.version, fairMethod = a?.fairMethod, fairAgeSec = a?.fairAgeSec, pageAgeSec = a?.pageAgeSec,
            dissent = a?.dissent?.size?.takeIf { a.twoSided != null }, trap = a?.novigMove?.substringBefore(" ·"),
            fullKelly = a?.fullKelly, bankroll = a?.bankroll, wallet = a?.wallet,
            marketId = b.marketId.ifBlank { null }, outcomeId = b.outcomeId.ifBlank { null }, lockFor = b.lockFor?.take(8),
            bid = bid?.let { m ->
                BidFacts(
                    postedMs = m.postedAtMs, postedLeadMin = (m.startsTs - m.postedAtMs) / 60_000L, fillDelayMs = m.fillDelayMs, margin = m.margin, evAtFair = m.evAtFair,
                    fairAtFill = m.fairAtFill, fillEv = m.fillEv(), bestBidAtPost = m.bestBidAtPost, offerAtPost = m.offerAtPost, fairBooks = m.fairBooks, focus = m.focus,
                    obscure = m.obscure, source = m.source,
                )
            },
            detail = if (!detail || a == null) null else Detail(
                bookPrices = a.books.map { bk ->
                    bk.book + ": " + (bk.odds?.let { Odds.formatAmerican(it) } ?: "—") + "/" + (bk.other?.let { Odds.formatAmerican(it) } ?: "—") + (bk.fair?.let { String.format(java.util.Locale.US, " (fair %.1f%%)", it * 100) } ?: "")
                },
                dissent = a.dissent, fairBooks = a.fairBooks, fairSharp = a.fairSharp, rules = a.rules, sharpConfirm = a.sharpConfirm,
            ),
        )
    }

    /** Where [b]'s close [source] ([ClosingLine.closeOf]) comes from, for [Row.closeBasis]. */
    fun closeBasis(b: TrackedBet, source: String): String {
        if (source == ClosingLine.SOURCE_CAPTURED) return ClosingLine.readBasis(b)
        val s = source.lowercase()
        return when {
            s.contains("pinnacle") || s.startsWith("parlayapi") || s.startsWith("sportsgameodds") || s.startsWith("oddspapi") -> "history:pinnacle"
            s.startsWith("espn") -> "history:espn"
            s.startsWith("novig") -> "history:novig-trades"
            else -> "history:other"
        }
    }

    fun bidRow(m: MakerBid, sport: String? = null): BidRow = BidRow(
        clientId = m.clientId.take(12), postedMs = m.postedAtMs, posted = iso(m.postedAtMs), day = day(m.postedAtMs), scanner = m.source, auto = m.auto,
        league = m.league, sport = sport, kind = m.kind.label, market = m.marketLabel, event = m.eventName, selection = m.selection, startsMs = m.startsTs,
        postedLeadMin = (m.startsTs - m.postedAtMs) / 60_000L, price = m.price, odds = Odds.probabilityToAmerican(m.price.coerceIn(0.001, 0.999)), contracts = m.contracts,
        ev = m.evAtFair, fair = m.fair, margin = m.margin, books = m.books, status = m.status.name.lowercase(), filled = m.filled, paid = m.paid,
        fillPct = if (m.contracts > 0) m.filled.toDouble() / m.contracts else 0.0, endedMs = m.endedAtMs, why = m.why?.take(160), fillDelayMs = m.fillDelayMs,
        fairAtFill = m.fairAtFill, fillEv = m.fillEv(), bestBidAtPost = m.bestBidAtPost, offerAtPost = m.offerAtPost, fairBooks = m.fairBooks, focus = m.focus,
        obscure = m.obscure, betId = m.betId?.take(8),
    )

    fun line(r: Row): String = json.encodeToString(r)
    fun line(r: BidRow): String = json.encodeToString(r)

    /** Companies in a list of book names, for "books behind the fair" when only names are kept. */
    fun companies(names: List<String>): Int = names.map { BookCompany.of(it) }.distinct().size
}
