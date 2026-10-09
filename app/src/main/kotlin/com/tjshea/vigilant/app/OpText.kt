package com.tjshea.vigilant.app

/** The words of Settings › OddsPapi (ODDSPAPI_API.md). */
object OpText {
    const val INTRO = "OddsPapi (v5) is a second paid feed for most of what Vigilant reads: Pinnacle, Circa, bet365 and the US books with each price's own change time, every alternate line, 1st-half lines and player props, plus the closing price of any bet (so CLV works for bets that are not graded yet, however old) and final scores. A trial or contract is arranged by email to contact@55-tech.com; ask for the v5 API with US books, the NFL/NCAAF/NBA/NCAAB/WNBA/MLB/NHL feeds and props. Add the key (several are fine: they are tried in order), tap Test key, then switch it on. A key from the self-serve v4 plan builder is a different product and is refused."
    const val SWITCH_TITLE = "Use OddsPapi"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: Vigilant reads its usual feeds. On: it reads OddsPapi for everything it can."
    const val REPLACES = "While it is on and answering, these rest for every league OddsPapi carries (NFL, NCAAF, MLB, NHL, NBA, NCAAB, WNBA): PropLine, The Odds API and ParlayAPI's odds, props and 1st-half calls, and PinnWire/pinnapi unless a Pinnodds key is saved. Tennis keeps them. Kalshi, Polymarket, CrazyNinjaOdds and ESPN are free and stay, and so does Pinnodds: with a key saved (Settings › Pinnodds live) its fresh Pinnacle board is asked first for every price. With SportsGameOdds Pro also on, both are read and SportsGameOdds wins a book both send. If OddsPapi stops answering, the others come back by themselves until it does. The trial's request quota is not published: if a key runs out the others carry on."
}
