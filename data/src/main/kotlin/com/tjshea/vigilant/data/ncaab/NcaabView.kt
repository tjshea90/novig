package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.OutcomeTarget
import kotlin.math.abs

/** One spread number on Novig: the home team's handicap and the two sides (each an [Opportunity] the bet button takes). */
data class SpreadLine(val handicap: Double, val home: Opportunity, val away: Opportunity) {
    val homeCost: Double? get() = home.ladder.firstOrNull()?.price
    val awayCost: Double? get() = away.ladder.firstOrNull()?.price
}

/** One total number on Novig. */
data class TotalLine(val line: Double, val over: Opportunity, val under: Opportunity) {
    val overCost: Double? get() = over.ladder.firstOrNull()?.price
    val underCost: Double? get() = under.ladder.firstOrNull()?.price
}

/** A signal's pick made concrete: the Novig side to take, what it costs, and what the signal's history would make of it (`rate / cost - 1`). */
data class NcaabPick(
    val reading: NcaabSignals.Reading,
    /** Null when the line the signal read is not one Novig lists (ESPN's fallback line): nothing to bet. */
    val opportunity: Opportunity?,
    /** Who or what is being backed, in words ("Davidson -4.5", "Over 154.5"). */
    val text: String,
    val cost: Double?,
    val systemEv: Double?,
)

data class NcaabGameView(
    val game: SlateGame,
    val event: NovigEvent?,
    val home: NcaabTeam?,
    val away: NcaabTeam?,
    val moneyline: Pair<Opportunity, Opportunity>?,
    val spreads: List<SpreadLine>,
    val totals: List<TotalLine>,
    val mainSpread: SpreadLine?,
    val mainTotal: TotalLine?,
    /** The home handicap and total the signals read, and whose they are ("Novig" or "ESPN"). */
    val homeSpread: Double?,
    val total: Double?,
    val lineSource: String,
    val readings: List<NcaabSignals.Reading>,
    val picks: List<NcaabPick>,
    /** Novig's home team is ESPN's away team (a neutral-site game some feeds list the other way round): Novig's spread sides then belong to the other ESPN team. */
    val swapped: Boolean = false,
) {
    val id: String get() = game.id

    /** The Novig spread side that belongs to ESPN's home ([espnHome] true) or away team. */
    fun spreadFor(espnHome: Boolean): Opportunity? = mainSpread?.let { if (espnHome != swapped) it.home else it.away }
    fun spreadCost(espnHome: Boolean): Double? = spreadFor(espnHome)?.ladder?.firstOrNull()?.price
    val live: Boolean get() = game.state == "in"
    val final: Boolean get() = game.state == "post"
    val bettable: Boolean get() = event != null && game.state == "pre"
    val bestPick: NcaabPick? get() = picks.filter { it.reading.backed }.maxByOrNull { NcaabSignals.score(it.reading) }
}

object NcaabView {
    private val NEAR_EVEN = 0.30..0.70

    /** Games on screen: a game that started more than this long ago and is not final is still shown (a delayed one), anything older drops. */
    private const val KEEP_AFTER_START_MS = 6 * 3_600_000L

    fun build(slate: NcaabSlate?, ratings: NcaabRatings?, board: NcaabBoardFeed?, now: Long): List<NcaabGameView> {
        val rows = board?.rows.orEmpty()
        val events = rows.map { it.event }.distinctBy { it.eventId }
        val byEvent = rows.groupBy { it.event.eventId }
        val games = slate?.games.orEmpty().filter { it.t > now - KEEP_AFTER_START_MS || it.state != "post" }
        val matched = NcaabMatch.match(games, events)
        val used = matched.values.map { it.event.eventId }.toSet()
        // Novig lists a game ESPN's schedule has not (yet): it still gets a card, with no ratings.
        val extras = events.filter { it.eventId !in used }.mapNotNull { e -> e.matchup?.let { m ->
            SlateGame(id = e.eventId, t = e.startsTs, d = "", state = "pre", h = SlateTeam(id = "novig-" + e.eventId + "-h", n = m.home, dn = m.home), a = SlateTeam(id = "novig-" + e.eventId + "-a", n = m.away, dn = m.away))
        } }
        val out = ArrayList<NcaabGameView>()
        for (g in games + extras) {
            val found = matched[g.id]
            val event = found?.event ?: events.firstOrNull { it.eventId == g.id }
            out += one(g, event, found?.swapped == true, event?.let { byEvent[it.eventId] }.orEmpty(), ratings)
        }
        return out.sortedWith(compareBy({ it.game.t }, { it.game.id }))
    }

    private fun one(g: SlateGame, event: NovigEvent?, swapped: Boolean, rows: List<Opportunity>, ratings: NcaabRatings?): NcaabGameView {
        val teams = ratings?.teams.orEmpty()
        val home = teams[g.h.id]
        val away = teams[g.a.id]
        fun side(o: Opportunity): Side? = (o.target as? OutcomeTarget.Is)?.side
        // Novig's HOME is the event's second-named team; ESPN's can be the other one at a neutral site. The signals read ESPN's home (the ratings are keyed by its teams), so a swapped game flips the handicap.
        val spreads = rows.filter { it.kind == LineKind.SPREAD }.groupBy { it.market.marketId }.mapNotNull { (_, sides) ->
            val h = sides.firstOrNull { side(it) == Side.HOME } ?: return@mapNotNull null
            val a = sides.firstOrNull { side(it) == Side.AWAY } ?: return@mapNotNull null
            val strike = h.market.strike ?: return@mapNotNull null
            SpreadLine(strike, h, a)
        }.sortedBy { it.handicap }
        val totals = rows.filter { it.kind == LineKind.TOTAL }.groupBy { it.market.marketId }.mapNotNull { (_, sides) ->
            val o = sides.firstOrNull { side(it) == Side.OVER } ?: return@mapNotNull null
            val u = sides.firstOrNull { side(it) == Side.UNDER } ?: return@mapNotNull null
            val line = o.market.strike ?: return@mapNotNull null
            TotalLine(line, o, u)
        }.sortedBy { it.line }
        val ml = rows.filter { it.kind == LineKind.MONEYLINE }.groupBy { it.market.marketId }.values.firstNotNullOfOrNull { sides ->
            val h = sides.firstOrNull { side(it) == Side.HOME }
            val a = sides.firstOrNull { side(it) == Side.AWAY }
            if (h != null && a != null) h to a else null
        }
        val mainSpread = mainOf(spreads, g.sp?.let { if (swapped) -it else it }, { it.handicap }, { it.homeCost }, { it.awayCost })
        val mainTotal = mainOf(totals, g.tt, { it.line }, { it.overCost }, { it.underCost })
        fun flip(x: Double?) = if (swapped) x?.let { -it } else x
        val novigSpread = mainSpread?.handicap?.let { flip(it) }
        val homeSpread = novigSpread ?: g.sp
        val total = mainTotal?.line ?: g.tt
        val source = if (mainSpread != null || mainTotal != null) "Novig" else "ESPN"
        val neutral = g.neu == 1
        val readings = if (home != null && away != null && ratings != null) NcaabSignals.read(ratings.model, home, away, homeSpread, total, neutral) else emptyList()
        val picks = readings.map { r -> pickOf(r, g, swapped, mainSpread, mainTotal) }
        return NcaabGameView(g, event, home, away, ml, spreads, totals, mainSpread, mainTotal, homeSpread, total, source, readings, picks, swapped)
    }

    /** The number whose two sides cost closest to even money to take; the one nearest [espn] when no price is read; the middle one otherwise. */
    private fun <T> mainOf(lines: List<T>, espn: Double?, num: (T) -> Double, a: (T) -> Double?, b: (T) -> Double?): T? {
        if (lines.isEmpty()) return null
        // Only a number that looks like a real near-even market counts: with the books of only some numbers read (the public routes), the closest to even among them can still be a 12 cent / 95 cent alternate.
        lines.mapNotNull { l -> val x = a(l); val y = b(l); if (x != null && y != null && x in NEAR_EVEN && y in NEAR_EVEN) l to abs(x - 0.5) + abs(y - 0.5) else null }.minByOrNull { it.second }?.let { return it.first }
        if (espn != null) return lines.minByOrNull { abs(num(it) - espn) }
        return lines[lines.size / 2]
    }

    private fun pickOf(r: NcaabSignals.Reading, g: SlateGame, swapped: Boolean, spread: SpreadLine?, total: TotalLine?): NcaabPick {
        // ESPN's home/away of the reading -> Novig's side of the same team.
        val novigHome = (r.pick == NcaabSignals.Pick.HOME) != swapped
        val opp: Opportunity? = when (r.pick) {
            NcaabSignals.Pick.HOME, NcaabSignals.Pick.AWAY -> spread?.let { if (novigHome) it.home else it.away }
            NcaabSignals.Pick.OVER -> total?.over
            NcaabSignals.Pick.UNDER -> total?.under
        }
        val cost = opp?.ladder?.firstOrNull()?.price
        val text = opp?.selection ?: when (r.pick) {
            NcaabSignals.Pick.HOME -> g.h.n
            NcaabSignals.Pick.AWAY -> g.a.n
            NcaabSignals.Pick.OVER -> "Over"
            NcaabSignals.Pick.UNDER -> "Under"
        }
        return NcaabPick(r, opp, text, cost, cost?.let { NcaabSignals.evAt(r.rate, it) })
    }
}
