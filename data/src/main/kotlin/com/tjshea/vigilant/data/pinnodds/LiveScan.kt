package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefQuote
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.LineKey
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import com.tjshea.vigilant.engine.DevigMethod
import com.tjshea.vigilant.engine.EvMath
import com.tjshea.vigilant.engine.FairLine
import com.tjshea.vigilant.engine.FairSource
import com.tjshea.vigilant.engine.TakeLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real-time live lane of the Feed (Tj, 2026-10-11): "If I toggle on live games to show up in the scanner, automatically make the app match the games that are live at that instant and keep them
 * linked with novig and pinnodds web sockets both active ... Only keep bets in the scanner list if the odds are within the last 5 seconds fresh. The fair value must be determined from fresh odds
 * from pinnodds in real time. After 5 seconds, automatically drop bets from the list."
 *
 * Both prices are pushed, never polled: a Pinnacle line (fair) from the Pinnodds socket, Novig's take ladder from its book socket, and the EV is worked out from those two alone, at the moment either one
 * changes (the runner re-judges every 100 ms). A row is "as of" the OLDER of the two feeds' last word about the game ([LiveScanRows.asOf]) and is on the list only while that is within
 * [MAX_AGE_MS]; a stalled or dropped socket therefore empties the list within five seconds. With the switch off the board is never written and the Feed is what it always was.
 */
object LiveScan {
    /** A row older than this (the older of the Pinnacle and Novig feeds' last word about its game) is dropped (Tj, 2026-10-11). */
    const val MAX_AGE_MS = 5_000L

    /** A row's displayed stamps are refreshed no more often than this while its prices are unchanged (the list is not rebuilt on every frame); they only ever lag the truth, never lead it. */
    const val STAMP_SLACK_MS = 1_000L
}

/** What the live lane is doing, for the Feed's one-line status. [reason] says why it shows nothing when it cannot (fixed words, from the runner's feed checks). */
data class LiveScanStatus(
    val on: Boolean = false,
    val games: Int = 0,
    val watched: Int = 0,
    val shown: Int = 0,
    /** Novig books held right now among the markets watched (the socket delivering). */
    val books: Int = 0,
    /** Rows that went off the list for being older than [LiveScan.MAX_AGE_MS] (or for a feed problem), since the lane started. */
    val dropped: Long = 0,
    /** How old the oldest shown row's feeds are right now (null with no rows). */
    val oldestMs: Long? = null,
    val reason: String? = null,
)

/** Where the runner leaves the live rows; the Feed collects [rows]. */
class LiveScanBoard {
    private val _rows = MutableStateFlow<List<Opportunity>>(emptyList())
    val rows: StateFlow<List<Opportunity>> = _rows.asStateFlow()
    private val _status = MutableStateFlow(LiveScanStatus())
    val status: StateFlow<LiveScanStatus> = _status.asStateFlow()

    fun publish(rows: List<Opportunity>, status: LiveScanStatus) {
        // Same rows (the very same objects, same order) = nothing to tell the screen.
        val old = _rows.value
        if (old.size != rows.size || old.indices.any { old[it] !== rows[it] }) _rows.value = rows
        if (_status.value != status) _status.value = status
    }

    fun clear(reason: String? = null) = publish(emptyList(), LiveScanStatus(on = false, reason = reason))
}

/** One side of a live line, ready for the Feed, and what it was built from (so an unchanged line is not rebuilt). */
class LiveScanRow(val opportunity: Opportunity, val lineVersion: Long, val bookSeq: Long, val pinnAsOfMs: Long, val novigAsOfMs: Long)

/** Pure: Pinnacle's live fair against Novig's take ladder for one matched market, as Feed rows. The same inputs give the same rows. */
object LiveScanRows {
    /** The age of a row's data: how long ago the older of the two feeds last spoke about the game. */
    fun age(pinnAsOfMs: Long, novigAsOfMs: Long, nowMs: Long): Long = nowMs - minOf(pinnAsOfMs, novigAsOfMs)

    /** Why [t]'s line may not be priced now (fixed words, counted by the runner), or null when it may. Pinnacle's own guards: open, live, out of its danger zone, settled, a tight margin, a real limit. */
    fun refusal(t: LiveTarget, pe: PinnEvent, line: PinnLine, nowMs: Long, rules: LiveRules): String? = when {
        t.event.status != NovigEvent.STATUS_LIVE -> LiveSkip.PREGAME
        !pe.live -> LiveSkip.PREGAME
        !line.open -> LiveSkip.CLOSED
        nowMs < pe.volatileUntilMs -> LiveSkip.VOLATILE
        nowMs - line.changedAtMs < rules.settleMs -> LiveSkip.SETTLING
        line.overround > rules.maxOverround -> LiveSkip.OVERROUND
        line.maxRisk?.let { it < rules.minPinnLimit } == true -> LiveSkip.LIMIT
        else -> null
    }

    fun kindOf(type: PinnLineType): LineKind = when (type) {
        PinnLineType.MONEYLINE -> LineKind.MONEYLINE
        PinnLineType.SPREAD -> LineKind.SPREAD
        PinnLineType.TOTAL -> LineKind.TOTAL
    }

    /** The market's name and the selection as the Tracker shows them ("Moneyline" / "Alabama", "Spread" / "Alabama -3.5", "Total" / "Over 45.5"). */
    fun describe(t: LiveTarget, side: PinnSide, outcomeId: String): kotlin.Pair<String, String> {
        val outcomeName = t.market.outcomes.firstOrNull { it.outcomeId == outcomeId }?.name ?: ""
        val matchup = t.event.matchup
        val novigHome = (side == PinnSide.HOME) != t.swapped
        val team = if (novigHome) matchup?.home else matchup?.away
        return when (t.type) {
            PinnLineType.MONEYLINE -> "Moneyline" to (team ?: outcomeName)
            PinnLineType.SPREAD -> "Spread" to "${team ?: outcomeName.substringBeforeLast(' ')} ${signed(if (novigHome) t.strike!! else -t.strike!!)}"
            PinnLineType.TOTAL -> "Total" to outcomeName
        }
    }

    private fun signed(x: Double): String = (if (x > 0) "+" else "") + (if (x == Math.floor(x)) x.toInt().toString() else x.toString())

    private fun decimal(american: Double): Double? = when {
        american >= 100.0 -> 1.0 + american / 100.0
        american <= -100.0 -> 1.0 + 100.0 / -american
        else -> null
    }

    /**
     * Both sides of [t]'s line as rows, or an empty list when the line cannot be priced (no Novig fee, a side with no offer, a fair outside the live engine's trusted band, an unknown league).
     * [asOf]: the older feed's last word about the game, which stamps the rows' fair and book times. Fair is Pinnacle's devigged probability (the method the engine's book was built with:
     * conservative [DevigMethod.WORST_CASE] unless Tj changed it), the price is Novig's cheapest take level and the fee is the live taker fee.
     */
    fun build(
        t: LiveTarget,
        pe: PinnEvent,
        line: PinnLine,
        book: NovigBook,
        pinnAsOfMs: Long,
        novigAsOfMs: Long,
        /** When this market's own Novig book last changed (null: not since it was first read). */
        novigMarketAtMs: Long? = null,
        method: DevigMethod,
        rules: LiveRules,
        settings: ScanSettings,
        /** When Pinnacle's feed last spoke about this game, and Novig's about its books (for the row's own age line; default: the feed stamps). */
        pinnFeedMs: Long = pinnAsOfMs,
        novigFeedMs: Long = novigAsOfMs,
        league: League? = Leagues.ALL.firstOrNull { it.novigName == t.event.league },
    ): List<LiveScanRow> {
        league ?: return emptyList()
        val fee = t.market.fee ?: return emptyList()
        val matchup = t.event.matchup ?: return emptyList()
        val kind = kindOf(t.type)
        // Novig's HOME side is Pinnacle's AWAY when the game is swapped.
        fun pinnFor(novigSide: Side): PinnSide = when (novigSide) {
            Side.HOME -> if (t.swapped) PinnSide.AWAY else PinnSide.HOME
            Side.AWAY -> if (t.swapped) PinnSide.HOME else PinnSide.AWAY
            Side.OVER -> PinnSide.OVER
            Side.UNDER -> PinnSide.UNDER
        }
        val sides = if (t.type == PinnLineType.TOTAL) listOf(Side.OVER, Side.UNDER) else listOf(Side.HOME, Side.AWAY)
        val fairs = sides.map { line.fair[pinnFor(it)] ?: return emptyList() }
        val decimals = sides.map { decimal(line.american[pinnFor(it)] ?: return emptyList()) ?: return emptyList() }
        if (fairs.any { it < rules.minFair || it > rules.maxFair }) return emptyList()
        val outcomeIds = sides.map { side -> t.outcomeBySide[pinnFor(side)] ?: return emptyList() }
        val ladders = outcomeIds.map { id -> book.takeLadder(t.market, id).sortedBy { it.price }.filter { it.price > 0.0 && it.price < 1.0 && it.contracts > 0L } }
        val bestTakes = ladders.mapNotNull { it.firstOrNull()?.price }
        val width = if (bestTakes.size == 2) bestTakes.sum() - 1.0 else null

        val lineValue = if (t.type == PinnLineType.MONEYLINE) null else t.strike
        val refId = "pinnacle-live-${pe.id}"
        val quotes = sides.mapIndexed { i, s ->
            val point = when (t.type) {
                PinnLineType.MONEYLINE -> null
                PinnLineType.SPREAD -> if (s == Side.HOME) t.strike else t.strike?.let { -it }
                PinnLineType.TOTAL -> t.strike
            }
            RefQuote(s, decimals[i], point)
        }
        val ref = RefEvent(
            id = refId, sportKey = league.oddsApiSportKey, commenceMs = t.event.startsTs, home = matchup.home, away = matchup.away,
            markets = listOf(RefBookMarket("pinnacle", "Pinnacle", kind, quotes, pinnAsOfMs)),
        )
        val key = LineKey(refId, kind, lineValue)
        val prices = BookPrices("pinnacle", "Pinnacle", decimals, pinnAsOfMs)
        val fairLine = FairLine(
            probabilities = fairs, requestedSource = FairSource.SHARP, sourceUsed = FairSource.SHARP, method = method,
            perBook = listOf(BookFair(prices, fairs, line.overround, true)), sharpBooksUsed = listOf("Pinnacle"), averageBooksUsed = emptyList(),
        )
        return sides.mapIndexedNotNull { i, s ->
            val ladder: List<TakeLevel> = ladders[i]
            val best = ladder.firstOrNull() ?: return@mapIndexedNotNull null
            val p = fairs[i]
            // Novig charges the taker its fee once a game is live; this lane only prices live games.
            val quote = EvMath.quote(p, best.price, fee, true)
            val depth = EvMath.positiveDepth(ladder, p, fee, true)
            val stake = if (quote.evPercent > 0) EvMath.suggestedStake(quote, settings.bankroll, settings.kellyMultiplier, depth.dollarCost) else null
            val outcomeId = outcomeIds[i]
            val outcome = t.market.outcomes.first { it.outcomeId == outcomeId }
            val (label, selection) = describe(t, pinnFor(s), outcomeId)
            LiveScanRow(
                Opportunity(
                    league = league, event = t.event, market = t.market, outcome = outcome, marketLabel = label, kind = kind, selection = selection,
                    fair = fairLine, fairProbability = p, quote = quote, ladder = ladder, depth = depth, suggestedStake = stake, novigWidth = width,
                    bestBid = book.bestBid(outcomeId)?.price, bidLevels = book.bidsByOutcome[outcomeId].orEmpty(), bookFetchedAtMs = novigAsOfMs,
                    fairUpdatedMs = line.changedAtMs, liveAges = com.tjshea.vigilant.data.scanner.LiveAges(line.changedAtMs, pinnFeedMs, novigMarketAtMs, novigFeedMs), refEvent = ref, lineKey = key, target = OutcomeTarget.Is(s), fairAsOfMs = pinnAsOfMs,
                ),
                lineVersion = line.version, bookSeq = book.seq, pinnAsOfMs = pinnAsOfMs, novigAsOfMs = novigAsOfMs,
            )
        }
    }
}

/** The live lane's rows as the Feed lists them: the same bars every other bet has to clear (EV range, odds limits, kinds, leagues, 10 cents of room), in the Feed's order. */
object LiveScanFeed {
    /** Why the Feed does not list [o] under [settings] right now, in words; null when it lists it. */
    fun hiddenBecause(o: Opportunity, settings: ScanSettings, nowMs: Long): String? {
        val ev = o.evPercent ?: return "no price"
        val q = o.quote ?: return "no price"
        return when {
            o.league.novigName !in settings.leagues -> "${o.league.novigName} is not one of your picked leagues"
            ev <= 0.0 -> "not +EV"
            ev < settings.minEvPercent -> "under your ${String.format(java.util.Locale.US, "%.1f", settings.minEvPercent * 100)}% minimum EV"
            ev > settings.maxEvPercent -> "over your ${String.format(java.util.Locale.US, "%.0f", settings.maxEvPercent * 100)}% maximum EV (too good to be true)"
            o.depth?.let { it.dollarCost < com.tjshea.vigilant.data.scanner.ScanResult.MIN_LIQUIDITY_DOLLARS - 1e-9 } == true -> "too little money on the book"
            !settings.withinOdds(q.cost) -> "outside your odds limits"
            com.tjshea.vigilant.data.scanner.MarketFamily.entries.none { it in settings.families && o.market.marketType in it.novigTypes } -> "that kind of market is switched off"
            // The board drops a row at 5 s; this is the same rule at the moment the list is read, so a row cannot outlive its time between two board passes.
            o.bookFetchedAtMs == null || o.fairAsOfMs == null || nowMs - minOf(o.bookFetchedAtMs!!, o.fairAsOfMs!!) > LiveScan.MAX_AGE_MS + LiveScan.STAMP_SLACK_MS -> "its prices went stale"
            else -> null
        }
    }

    fun shown(rows: List<Opportunity>, settings: ScanSettings, nowMs: Long): List<Opportunity> {
        if (!settings.includeLive || rows.isEmpty()) return emptyList()
        return rows.filter { hiddenBecause(it, settings, nowMs) == null }
    }

    /** The +EV rows the lane found that the Feed hides, by reason ("2 · NBA is not one of your picked leagues"), most first. */
    fun hiddenReasons(rows: List<Opportunity>, settings: ScanSettings, nowMs: Long): List<Pair<String, Int>> =
        rows.filter { (it.evPercent ?: -1.0) > 0.0 }.mapNotNull { hiddenBecause(it, settings, nowMs) }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
}
