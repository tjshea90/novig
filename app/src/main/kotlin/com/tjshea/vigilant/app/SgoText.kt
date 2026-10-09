package com.tjshea.vigilant.app

/** The words of Settings › SportsGameOdds Pro (SPORTSGAMEODDS_API.md). */
object SgoText {
    const val INTRO = "SportsGameOdds Pro is one feed for most of what Vigilant reads: Pinnacle, Circa, bet365 and the US books, game lines, every alternate line, 1st-half lines and player props, each price with the time it was last seen, plus closing lines and final scores. Add your key (several are fine: they are tried in order), tap Test key, then switch it on."
    const val SWITCH_TITLE = "Use SportsGameOdds Pro"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: Vigilant reads its usual feeds. On: it reads SportsGameOdds for everything it can."
    const val REPLACES = "While it is on and answering, these rest for every league SportsGameOdds carries (NFL, NCAAF, MLB, NHL, NBA, NCAAB, WNBA): PinnWire and pinnapi (Pinnacle), PropLine, The Odds API and ParlayAPI's odds, props and 1st-half calls. Tennis keeps them (SportsGameOdds has no feed Vigilant uses for it). Kalshi, Polymarket, CrazyNinjaOdds and ESPN are free and stay. If SportsGameOdds stops answering, the others come back by themselves until it does."
}
