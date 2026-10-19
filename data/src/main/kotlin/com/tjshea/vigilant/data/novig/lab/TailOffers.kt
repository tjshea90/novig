package com.tjshea.vigilant.data.novig.lab

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.tracker.AtBet
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

/** Turns a tail or Live light candidate into what the taker needs to place it and the Tracker to record it. */
object TailOffers {
    /** The Novig market it is on, the Tracker's record of it, the market's own fee. Null when the market or its fee can't be found. */
    fun build(ev: NovigEvent, markets: List<NovigMarket>, points: List<LadderPoint>, st: GameState, c: TailCandidate, now: Long): TailOffer? {
        val market = markets.firstOrNull { it.marketId == c.marketId } ?: return null
        val fee = points.firstOrNull { it.line.marketId == c.marketId }?.line?.fee ?: return null
        val total = c.side == "OVER" || c.side == "UNDER"
        val marketLabel = if (total) "Total" else if (c.strike == 0.0) "Moneyline" else "Spread"
        val selection = "${c.label} ${c.side.lowercase()}"
        val american = Odds.probabilityToAmerican(c.ask.coerceIn(0.001, 0.999))
        val light = c.rule == LiveLightScan.RULE || c.rule == LiveLightCurve.RULE || c.rule == LiveLightScan.MID
        val curve = c.rule == LiveLightCurve.RULE
        val target = BetTarget(
            market = market, outcomeId = c.outcomeId, league = ev.league, eventName = ev.description, startsTs = ev.startsTs, marketLabel = marketLabel, selection = selection,
            fair = c.fair, fairAsOfMs = now, source = BetTracker.SOURCE_TAIL,
            basis = FairBasis(FairBasis.SOURCE_TAIL, emptyList(), 0), auto = true,
            atBet = AtBet(
                atMs = now, how = AtBet.HOW_AUTO, scanner = if (light) "Live light" else "Live tail", league = ev.league, live = true, american = american, ev = c.edge, fair = c.fair,
                fairAmerican = Odds.probabilityToAmerican(c.fair.coerceIn(0.001, 0.999)),
                fairMethod = if (curve) "Live light ladder curve (the ladder's liquid lines fitted to a normal, this line left out)" else if (light) "Live light model (ESPN score and clock, bounded against the bet)" else "Late-game tail model (ESPN score and clock, Novig's own centre)",
                fairBooks = emptyList(), fairSharp = emptyList(),
            ),
        )
        val note = "${st.homeScore}-${st.awayScore}, ${"%.0f".format(Locale.US, st.fractionLeft * 100)}% left" + if (light) "" else ", centre ${"%.1f".format(Locale.US, c.centre)}"
        return TailOffer(c, target, fee, note, now)
    }
}
