package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.match.Picks
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.TrackedBet

/**
 * Which listed and open prop bets get an injury tag (Tj, 2026-09-30, PARLAY_API.md §6.1): Vigilant's +EV props, CNO's player bets and the
 * Tracker's open prop bets, each looked up in the [InjuryBook] by its player, sport and game. Pure: the screens read [tags]' map by each
 * item's own key ([Want.key]: an [Opportunity.key], [cnoKey], or [betKey]).
 */
object InjuryTags {
    /** One bet to look up: its key, its sport (ParlayAPI's key), its player and the game's teams (and his, when known). */
    data class Want(val key: String, val sportKey: String, val player: String, val teams: List<String>)

    fun cnoKey(row: CnoRow): String = "cno:${row.key}"

    fun betKey(bet: TrackedBet): String = "bet:${bet.id}"

    /** An open bet's game this long under way still has its tag looked up (a player ruled out at kickoff still matters to it). */
    private const val OPEN_AFTER_START_MS = 6 * 3_600_000L

    private fun sportOf(league: String): String? = Leagues.byNovigName(league.trim())?.takeIf { it.oddsApiListed }?.oddsApiSportKey
        ?: Leagues.ALL.firstOrNull { it.displayName.equals(league.trim(), ignoreCase = true) && it.oddsApiListed }?.oddsApiSportKey

    private fun teamsOf(event: String): List<String> = NovigText.parseMatchup(event)?.let { listOf(it.away, it.home) } ?: Picks.sides(event)

    /** The prop bets among [feed], [cnoRows] (with ESPN's [cnoTeams] by row key) and [bets] (open ones) to look up. */
    fun wants(
        feed: List<Opportunity>,
        cnoRows: List<CnoRow>,
        cnoTeams: Map<String, String>,
        bets: List<TrackedBet>,
        now: Long,
        /** ParlayAPI's picks, as the rows Novig re-priced ([ParlayPlay.row]): keyed "parlay:<row key>". */
        parlayRows: List<CnoRow> = emptyList(),
    ): List<Want> {
        val out = ArrayList<Want>()
        for (o in feed) {
            if (o.kind != LineKind.PLAYER_PROP || !o.league.oddsApiListed) continue
            val player = o.lineKey?.subject ?: (BetGrader.pickOf(o.marketLabel, o.selection) as? BetGrader.Pick.Prop)?.player ?: continue
            out += Want(o.key, o.league.oddsApiSportKey, player, teamsOf(o.event.description))
        }
        for (r in cnoRows) {
            if (!r.market.trim().startsWith("Player", ignoreCase = true)) continue
            val player = Picks.split(r.bet).first.trim().takeIf { it.isNotEmpty() } ?: continue
            val sport = sportOf(r.league) ?: continue
            out += Want(cnoKey(r), sport, player, listOfNotNull(cnoTeams[r.key]) + teamsOf(r.event))
        }
        for (r in parlayRows) {
            val player = Picks.split(r.bet).first.trim().takeIf { it.isNotEmpty() } ?: continue
            val sport = sportOf(r.league) ?: continue
            out += Want(ParlayPlay.KEY_PREFIX + r.key, sport, player, teamsOf(r.event))
        }
        for (b in bets) {
            if (b.status != BetStatus.PENDING || now - b.startsTs > OPEN_AFTER_START_MS) continue
            val pick = BetGrader.pickOf(b) as? BetGrader.Pick.Prop ?: continue
            val sport = sportOf(b.league) ?: continue
            out += Want(betKey(b), sport, pick.player, teamsOf(b.eventName))
        }
        return out
    }

    /** Each wanted bet's report, only where the player may not play ([Injury.tag] not null). */
    fun tags(book: InjuryBook, wants: List<Want>, now: Long): Map<String, Injury> {
        val out = HashMap<String, Injury>()
        for (w in wants) book.find(w.sportKey, w.player, w.teams, now)?.takeIf { it.tag != null }?.let { out[w.key] = it }
        return out
    }

    /** The players no report or /injuries read covers, by sport: what [ParlayInjuries.fill] is asked about. */
    fun uncovered(book: InjuryBook, wants: List<Want>, now: Long): Map<String, Set<String>> =
        wants.filter { it.sportKey in ParlayInjuries.SPORTS && !book.covers(it.sportKey, it.player, now) }
            .groupBy({ it.sportKey }, { it.player }).mapValues { it.value.toSet() }
}
