package com.tjshea.vigilant.app

/** The words of Settings › OddsPapi (ODDSPAPI_API.md). */
object OpText {
    const val INTRO = "OddsPapi (v5) is a second paid feed for most of what Vigilant reads: Pinnacle, Circa, bet365 and the US books with each price's own change time, every alternate line, 1st-half lines and player props, plus the closing price of any bet (so CLV works for bets that are not graded yet, however old) and final scores. A trial or contract is arranged by email to contact@55-tech.com; ask for the v5 API with US books, the NFL/NCAAF/NBA/NCAAB/WNBA/MLB/NHL feeds and props. Add the key (several are fine: they are tried in order), tap Test key, then switch it on. A key from the self-serve v4 plan builder is a different product and is refused."
    const val SWITCH_TITLE = "Use OddsPapi"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: Vigilant reads its usual feeds. On: it reads OddsPapi for everything it can."
    const val REPLACES = "While it is on and answering, OddsPapi is read beside every other API you have switched on: none of them rests for it, and each is asked for every league and kind of data it carries (switch an API off in Settings › APIs and it is never called). With SportsGameOdds Pro also on, both are read and SportsGameOdds wins a book both send. If OddsPapi stops answering, the others carry on without it until it does. The trial's request quota is not published: if a key runs out the others carry on."
}
