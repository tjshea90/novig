package com.tjshea.vigilant.app

/** The words of Settings › Unabated (UNABATED_API.md). */
object UnabatedText {
    const val INTRO = "Unabated's FREE API: DraftKings, FanDuel, MGM, Caesars, theScore Bet and Fanatics' game lines (with alternates) and player props, at no credit cost, 15 seconds behind the books and read once per 5 seconds. It has no Pinnacle or Circa, so it is a fallback, not a sharp price: it sits LAST in line, and a line is priced from it only when no better feed sent that book's price (a feed out of credits, down, or without the league)."
    const val SWITCH_TITLE = "Use Unabated (free)"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: nothing changes. On: Unabated fills the lines the other feeds did not send."
    const val NOTES = "Nothing else rests when this is on, because it costs nothing. Boards are cached about 2 minutes and refreshed in the background, so a scan never waits for it. Free access depends on Unabated's content-partner terms and can be withdrawn (the key then answers 401)."
}

/** The words of Settings › OddsPapi Free (v4) (ODDSPAPI_API.md addendum 2026-10-11). */
object Op4Text {
    const val INTRO = "OddsPapi's FREE (v4) plan is a different product from OddsPapi (v5, a trial or contract): its own key, from your account page at oddspapi.io. The free plan has a small monthly request allowance, so Vigilant does not scan with it. It uses it for one thing it does best: Pinnacle's closing line for CLV on your bets. It finds the game and the market list with a few cached requests, then reads the closing price from OddsPapi's free, unmetered historical odds."
    const val SWITCH_TITLE = "Use OddsPapi Free for closing lines"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: nothing changes. On: closing lines OddsPapi (v5) and SportsGameOdds did not have are asked here before ParlayAPI's credits are."
    const val NOTES = "Test key reads your plan's real allowance (an unmetered call). The app always keeps the last 10 requests, spends at most an even share of the rest per day, and never asks for player props (the free plan has none). Put a v5 key under OddsPapi (v5) instead; the test tells you if you pasted it in the wrong place."
}
