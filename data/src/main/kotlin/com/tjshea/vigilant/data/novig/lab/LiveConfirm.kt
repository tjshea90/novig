package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.boltodds.BoltBoard
import com.tjshea.vigilant.data.boltodds.BoltLiveConsensus
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.pinnodds.PinnLineType
import com.tjshea.vigilant.data.pinnodds.PinnSide
import com.tjshea.vigilant.data.scanner.Leagues

/**
 * A second opinion on a live game (Tj, 2026-10-14: Live light "can run with novig only ... or in combination with other sockets that offer live odds"). The score model alone is only trusted for an edge up to
 * [TailTradeLimits.MAX_EDGE]: a bigger gap between its answer and Novig's price is more likely a mismatched game than a gift. An independent live price that AGREES with the model lifts that cap.
 */
fun interface LiveConfirm {
    /** The chance the team Novig calls [ref] wins [ev], from an independent live source read within the last few seconds; null = no opinion (the source does not carry the game, or is stale or split). */
    fun moneyline(ev: NovigEvent, ref: String, now: Long): Double?
}

/** The soft-book consensus of BoltOdds' live socket ([BoltLiveConsensus]: a quorum of companies pushed within seconds, agreeing) as a second opinion on the moneyline. */
class BoltLiveConfirm(private val board: BoltBoard, private val rules: () -> BoltLiveConsensus.Rules = { BoltLiveConsensus.Rules() }) : LiveConfirm {
    override fun moneyline(ev: NovigEvent, ref: String, now: Long): Double? {
        val m = ev.matchup ?: return null
        val league = Leagues.byNovigName(ev.league)?.novigName ?: return null
        val game = BoltLiveConsensus.build(board.liveEntries(null), now, rules()).firstOrNull {
            it.league == league && TeamMatcher.similarity(it.home, m.home) >= 0.5 && TeamMatcher.similarity(it.away, m.away) >= 0.5
        } ?: return null
        val home = game.lines.firstOrNull { it.type == PinnLineType.MONEYLINE }?.fair?.get(PinnSide.HOME) ?: return null
        val h = TeamMatcher.similarity(ref, m.home)
        val a = TeamMatcher.similarity(ref, m.away)
        return when { h >= 0.5 && a < 0.5 -> home; a >= 0.5 && h < 0.5 -> 1.0 - home; else -> null }
    }
}
