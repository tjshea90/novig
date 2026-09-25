package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.Side
import kotlin.math.abs

/** How a Novig outcome's fair probability comes out of a reference line. */
sealed interface OutcomeTarget {
    /** The outcome IS this side (moneyline team, spread team, over/under). */
    data class Is(val side: Side) : OutcomeTarget

    /** A 3-way Yes/No market's "Yes": this side happens. */
    data class Yes(val side: Side) : OutcomeTarget

    /** A 3-way Yes/No market's "No": any other result. Not the other team (NOVIG_API.md §7). */
    data class No(val side: Side) : OutcomeTarget
}

/** One reference line: every book's quote for the same event, market kind, and point. */
data class LineKey(val refEventId: String, val kind: LineKind, val line: Double?, val threeWay: Boolean) {
    /** The order outcomes are fed to the devig math in. */
    val sides: List<Side>
        get() = when (kind) {
            LineKind.MONEYLINE -> if (threeWay) listOf(Side.HOME, Side.AWAY, Side.DRAW) else listOf(Side.HOME, Side.AWAY)
            LineKind.SPREAD -> listOf(Side.HOME, Side.AWAY)
            LineKind.TOTAL -> listOf(Side.OVER, Side.UNDER)
        }
}

data class PlannedOutcome(val outcome: NovigOutcome, val target: OutcomeTarget, val selection: String)

data class PlannedMarket(
    val league: League,
    val event: NovigEvent,
    val refEvent: RefEvent?,
    val market: NovigMarket,
    val kind: LineKind,
    /** Null for a Novig-only market shown without a fair price (no reference match). */
    val lineKey: LineKey?,
    val label: String,
    val outcomes: List<PlannedOutcome>,
)

/**
 * A Novig event and the reference quotes found for it. [refEvent] merges every provider that
 * matched (Pinnacle, Polymarket, Kalshi, The Odds API) into one set of book quotes, oriented like
 * the first provider's feed; [refSwapped] says whether that orientation is Novig's home/away
 * reversed. [providers] lists who matched, for the UI.
 */
data class EventMatch(
    val league: League,
    val event: NovigEvent,
    val refEvent: RefEvent?,
    val refSwapped: Boolean,
    val providers: List<String> = emptyList(),
)

data class Plan(
    val markets: List<PlannedMarket>,
    val events: List<EventMatch>,
) {
    val marketIds: List<String> get() = markets.map { it.market.marketId }
    val matchedEvents: Int get() = events.count { it.refEvent != null }
}

/**
 * Decides which Novig markets to price and how: matches each Novig event to a reference event,
 * works out which outcome is which side, and keeps only markets whose line a reference book
 * actually quotes. That filter matters for battery and data: an NFL game has ~55 game-line
 * markets on Novig (every alternate spread and total), but the books quote one or two lines,
 * so only those few books get fetched every refresh.
 */
object Planner {

    private const val MIN_TEAM_SIMILARITY = 0.5

    /**
     * Both teams must clear [MIN_TEAM_SIMILARITY] AND together reach this. Two city-only matches
     * (Yankees/Mets + Cubs/White Sox = 0.5 + 0.5) never pair a game with a different game.
     */
    private const val MIN_EVENT_SIMILARITY = 1.5
    /**
     * Games with no reference match still show Novig's moneyline on the Games tab, but each costs a
     * book request and can never be +EV, so only the next few are fetched.
     */
    private const val NOVIG_ONLY_EVENT_CAP = 20

    fun eligibleEvents(events: List<NovigEvent>, settings: ScanSettings, now: Long): List<NovigEvent> {
        val horizon = now + settings.daysAhead.coerceAtLeast(1) * 24L * 3600 * 1000
        return events.filter { e ->
            val pregameOk = e.status == NovigEvent.STATUS_PREGAME &&
                // The catalog refreshes every few minutes, so a game can still read "pregame"
                // after it has started. Past its start time it's live in practice: in-game fees
                // apply and the sportsbooks' pregame lines no longer describe it.
                (settings.includeLive || e.startsTs > now - STARTED_GRACE_MS)
            e.league in settings.leagues &&
                e.startsTs <= horizon &&
                (pregameOk || (settings.includeLive && e.status == NovigEvent.STATUS_LIVE))
        }
    }

    private const val STARTED_GRACE_MS = 2 * 60_000L

    /** Single-feed form, keyed by sport: kept for callers that only ever had one provider. */
    fun plan(
        events: List<NovigEvent>,
        markets: List<NovigMarket>,
        references: Map<String, RefSnapshot>,
        settings: ScanSettings,
        now: Long,
    ): Plan = plan(events, markets, references.values, settings, now)

    fun plan(
        events: List<NovigEvent>,
        markets: List<NovigMarket>,
        references: Collection<RefSnapshot>,
        settings: ScanSettings,
        now: Long,
    ): Plan {
        val eligible = eligibleEvents(events, settings, now)
        val matches = matchEvents(eligible, references)
        val marketsByEvent = markets.groupBy { it.eventId }
        val types = settings.novigMarketTypes.toSet()

        val planned = ArrayList<PlannedMarket>()
        var novigOnlyEvents = 0
        for (m in matches.sortedBy { it.event.startsTs }) {
            val eventMarkets = marketsByEvent[m.event.eventId].orEmpty().filter { it.isOpen && it.fee != null && it.marketType in types }
            if (m.refEvent == null) {
                // No fair price possible, but still show Novig's live moneyline for the game.
                if (novigOnlyEvents >= NOVIG_ONLY_EVENT_CAP) continue
                val ml = eventMarkets.filter { it.marketType == "MONEY" }.mapNotNull { planMarket(m, it) }
                if (ml.isNotEmpty()) novigOnlyEvents++
                planned += ml
                continue
            }
            val quoted = ArrayList<Pair<PlannedMarket, LineStats>>()
            for (market in eventMarkets) {
                val p = planMarket(m, market) ?: continue
                val key = p.lineKey ?: continue
                val stats = lineStats(m.refEvent, key) ?: continue // no reference book quotes this line
                quoted += p to stats
            }
            // Every quoted line costs one Novig book request per scan. The exchanges quote dozens
            // of alternates per game, so spreads and totals are capped at the best-covered lines:
            // most books first, then closest to a coin flip (the main line).
            planned += quoted.filter { it.first.kind == LineKind.MONEYLINE }.map { it.first }
            for (kind in listOf(LineKind.SPREAD, LineKind.TOTAL)) {
                planned += quoted.filter { it.first.kind == kind }
                    .sortedWith(compareByDescending<Pair<PlannedMarket, LineStats>> { it.second.books }.thenBy { it.second.imbalance })
                    .take(settings.linesPerGame.coerceAtLeast(1))
                    .map { it.first }
            }
        }
        return Plan(planned, matches)
    }

    private data class LineStats(val books: Int, val imbalance: Double)

    /** How many books quote [key]'s line, and how far from 50/50 it is. Null when none do. */
    private fun lineStats(ref: RefEvent, key: LineKey): LineStats? {
        val quotes = ref.markets.filter { mk ->
            mk.kind == key.kind && mk.quotes.any { q -> q.side == Side.DRAW } == key.threeWay && sameLine(mk.line, key.line)
        }
        if (quotes.isEmpty()) return null
        val first = quotes.first().quotes
        val imbalance = if (first.size == 2) abs(1 / first[0].decimalOdds - 1 / first[1].decimalOdds) else 0.0
        return LineStats(quotes.distinctBy { it.bookKey }.size, imbalance)
    }

    fun matchEvents(events: List<NovigEvent>, references: Map<String, RefSnapshot>): List<EventMatch> =
        matchEvents(events, references.values)

    /**
     * Pairs every Novig event with its best event in each reference snapshot (one-to-one within a
     * snapshot), then merges what matched. Snapshots earlier in [references] come first in the
     * merge, so when two feeds carry the same book (Pinnacle direct and via The Odds API), the
     * earlier one's copy is the one priced.
     */
    fun matchEvents(events: List<NovigEvent>, references: Collection<RefSnapshot>): List<EventMatch> {
        data class Found(val ref: RefEvent, val swapped: Boolean, val provider: String)

        val found = LinkedHashMap<String, MutableList<Found>>()
        val byLeague = events.groupBy { it.league }
        for ((leagueName, leagueEvents) in byLeague) {
            val league = Leagues.byNovigName(leagueName) ?: continue
            for (snap in references) {
                if (snap.sportKey != league.oddsApiSportKey) continue
                for ((eventId, pair) in matchOne(league, leagueEvents, snap.events)) {
                    found.getOrPut(eventId) { ArrayList() } += Found(pair.first, pair.second, snap.provider.ifEmpty { "reference" })
                }
            }
        }
        val out = ArrayList<EventMatch>(events.size)
        for (e in events) {
            val league = Leagues.byNovigName(e.league) ?: continue
            val list = found[e.eventId]
            if (list.isNullOrEmpty()) {
                out += EventMatch(league, e, null, false)
                continue
            }
            val primary = list.first()
            val merged = if (list.size == 1) {
                primary.ref
            } else {
                // Orient every other feed like the first one before pooling their quotes.
                val markets = list.flatMap { f -> if (f.swapped == primary.swapped) f.ref.markets else f.ref.markets.map { it.flipped() } }
                primary.ref.copy(id = list.joinToString("+") { it.ref.id }, markets = markets)
            }
            out += EventMatch(league, e, merged, primary.swapped, list.map { it.provider }.distinct())
        }
        return out
    }

    /** Novig event id → (matched reference event, whether home/away are reversed), for one feed. */
    private fun matchOne(league: League, leagueEvents: List<NovigEvent>, refs: List<RefEvent>): Map<String, Pair<RefEvent, Boolean>> {
        data class Candidate(val event: NovigEvent, val ref: RefEvent, val score: Double, val closeness: Double, val swapped: Boolean, val gap: Long)

        val maxGap = league.maxStartGapHours * 3_600_000L
        val candidates = ArrayList<Candidate>()
        for (e in leagueEvents) {
            val matchup = e.matchup ?: continue
            for (r in refs) {
                if (!sameStart(e, r, league, maxGap)) continue
                val gap = abs(e.startsTs - r.commenceMs)
                val aa = TeamMatcher.similarity(matchup.away, r.away)
                val hh = TeamMatcher.similarity(matchup.home, r.home)
                val ah = TeamMatcher.similarity(matchup.away, r.home)
                val ha = TeamMatcher.similarity(matchup.home, r.away)
                val straight = if (aa >= MIN_TEAM_SIMILARITY && hh >= MIN_TEAM_SIMILARITY && aa + hh >= MIN_EVENT_SIMILARITY) aa + hh else 0.0
                val swapped = if (ah >= MIN_TEAM_SIMILARITY && ha >= MIN_TEAM_SIMILARITY && ah + ha >= MIN_EVENT_SIMILARITY) ah + ha else 0.0
                val best = maxOf(straight, swapped)
                if (best <= 0.0) continue
                val isSwapped = swapped > straight
                val close = if (isSwapped) {
                    TeamMatcher.closeness(matchup.away, r.home) + TeamMatcher.closeness(matchup.home, r.away)
                } else {
                    TeamMatcher.closeness(matchup.away, r.away) + TeamMatcher.closeness(matchup.home, r.home)
                }
                candidates += Candidate(e, r, best, close, isSwapped, gap)
            }
        }
        val result = HashMap<String, Pair<RefEvent, Boolean>>()
        val usedRefs = HashSet<String>()
        for (c in candidates.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.closeness }.thenBy { it.gap })) {
            if (c.event.eventId in result || c.ref.id in usedRefs) continue
            result[c.event.eventId] = c.ref to c.swapped
            usedRefs += c.ref.id
        }
        return result
    }

    /**
     * Whether two listings start close enough to be the same game. A feed that only knows the
     * Eastern date (Kalshi's football and basketball codes) must agree on that date; football,
     * with its loose placeholder times, may be a day off either way.
     */
    private fun sameStart(e: NovigEvent, r: RefEvent, league: League, maxGap: Long): Boolean {
        val date = r.etDate ?: return abs(e.startsTs - r.commenceMs) <= maxGap
        val novigDate = java.time.LocalDate.parse(KalshiClient.etDate(e.startsTs))
        val refDate = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return false
        val days = abs(java.time.temporal.ChronoUnit.DAYS.between(novigDate, refDate))
        return days == 0L || (days == 1L && league.maxStartGapHours >= 24)
    }

    private fun sameLine(a: Double?, b: Double?): Boolean =
        (a == null && b == null) || (a != null && b != null && abs(a - b) < 1e-9)

    /** Maps a Novig-side (away/home) to the reference feed's side, honoring a home/away swap. */
    private fun refSide(novigAway: Boolean, swapped: Boolean): Side =
        if (novigAway != swapped) Side.AWAY else Side.HOME

    private fun planMarket(m: EventMatch, market: NovigMarket): PlannedMarket? {
        val matchup = m.event.matchup ?: return null
        val refId = m.refEvent?.id ?: ""
        fun teamName(isAway: Boolean) = if (isAway) matchup.away else matchup.home

        return when (market.marketType) {
            "MONEY" -> {
                if (market.outcomes.size != 2) return null
                val (o1, o2) = market.outcomes
                val firstAway = TeamMatcher.firstLabelIsAway(o1.name, o2.name, matchup.away, matchup.home) ?: return null
                PlannedMarket(
                    m.league, m.event, m.refEvent, market, LineKind.MONEYLINE,
                    LineKey(refId, LineKind.MONEYLINE, null, threeWay = false).takeIf { m.refEvent != null },
                    "Moneyline",
                    listOf(
                        PlannedOutcome(o1, OutcomeTarget.Is(refSide(firstAway, m.refSwapped)), teamName(firstAway)),
                        PlannedOutcome(o2, OutcomeTarget.Is(refSide(!firstAway, m.refSwapped)), teamName(!firstAway)),
                    ),
                )
            }

            "SPREAD" -> {
                if (market.outcomes.size != 2) return null
                val (o1, o2) = market.outcomes
                val (l1, p1) = NovigText.parseSpreadOutcome(o1.name) ?: return null
                val (l2, p2) = NovigText.parseSpreadOutcome(o2.name) ?: return null
                if (abs(p1 + p2) > 1e-9) return null
                val firstAway = TeamMatcher.firstLabelIsAway(l1, l2, matchup.away, matchup.home) ?: return null
                val side1 = refSide(firstAway, m.refSwapped)
                // The reference line is the HOME side's handicap in the reference feed's orientation.
                val refHomePoint = if (side1 == Side.HOME) p1 else p2
                PlannedMarket(
                    m.league, m.event, m.refEvent, market, LineKind.SPREAD,
                    LineKey(refId, LineKind.SPREAD, refHomePoint, threeWay = false).takeIf { m.refEvent != null },
                    "Spread",
                    listOf(
                        PlannedOutcome(o1, OutcomeTarget.Is(side1), "${teamName(firstAway)} ${signed(p1)}"),
                        PlannedOutcome(o2, OutcomeTarget.Is(refSide(!firstAway, m.refSwapped)), "${teamName(!firstAway)} ${signed(p2)}"),
                    ),
                )
            }

            "TOTAL" -> {
                if (market.outcomes.size != 2) return null
                val parsed = market.outcomes.map { o -> NovigText.parseTotalOutcome(o.name)?.let { o to it } ?: return null }
                val point = parsed.first().second.second
                if (parsed.any { abs(it.second.second - point) > 1e-9 } || parsed.map { it.second.first }.toSet().size != 2) return null
                PlannedMarket(
                    m.league, m.event, m.refEvent, market, LineKind.TOTAL,
                    LineKey(refId, LineKind.TOTAL, point, threeWay = false).takeIf { m.refEvent != null },
                    "Total",
                    parsed.map { (o, p) ->
                        PlannedOutcome(o, OutcomeTarget.Is(if (p.first) Side.OVER else Side.UNDER), "${if (p.first) "Over" else "Under"} ${fmt(point)}")
                    },
                )
            }

            "MONEYLINE_3_WAY_WIN", "MONEYLINE_3_WAY_DRAW" -> {
                val draw = market.marketType == "MONEYLINE_3_WAY_DRAW"
                val side: Side
                val subject: String
                if (draw) {
                    side = Side.DRAW
                    subject = "Draw"
                } else {
                    val team = NovigText.threeWayTeam(market.description) ?: return null
                    val isAway = TeamMatcher.labelIsAway(team, matchup.away, matchup.home) ?: return null
                    side = refSide(isAway, m.refSwapped)
                    subject = "${teamName(isAway)} win"
                }
                val yes = market.outcomes.firstOrNull { it.name.equals("Yes", true) } ?: return null
                val no = market.outcomes.firstOrNull { it.name.equals("No", true) } ?: return null
                PlannedMarket(
                    m.league, m.event, m.refEvent, market, LineKind.MONEYLINE,
                    LineKey(refId, LineKind.MONEYLINE, null, threeWay = true).takeIf { m.refEvent != null },
                    "3-way",
                    listOf(
                        PlannedOutcome(yes, OutcomeTarget.Yes(side), "$subject: Yes"),
                        PlannedOutcome(no, OutcomeTarget.No(side), "$subject: No"),
                    ),
                )
            }

            else -> null
        }
    }

    fun signed(p: Double): String = if (p > 0) "+${fmt(p)}" else fmt(p)

    fun fmt(p: Double): String = if (p == Math.floor(p)) p.toLong().toString() else p.toString()
}
