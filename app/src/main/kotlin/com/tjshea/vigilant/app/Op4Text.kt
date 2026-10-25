package com.tjshea.vigilant.app

/** The words of Settings › Unabated (UNABATED_API.md). */
object Op4Text {
    const val INTRO = "OddsPapi's FREE (v4) plan is a different product from OddsPapi (v5, a trial or contract): its own key, from your account page at oddspapi.io. The free plan has a small monthly request allowance, so Vigilant does not scan with it. It uses it for one thing it does best: Pinnacle's closing line for CLV on your bets. It finds the game and the market list with a few cached requests, then reads the closing price from OddsPapi's free, unmetered historical odds."
    const val SWITCH_TITLE = "Use OddsPapi Free (v4) for closing lines"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: nothing changes. On: closing lines OddsPapi (v5) and SportsGameOdds did not have are asked here before ParlayAPI's credits are."
    const val FAIR_HINT = "OddsPapi Free reads a whole game, every book, in ONE request, so a game costs 1 of the plan's requests. Pick the most a day the app may spend; whatever you pick it also spends at most an even share of what the month has left, reads the games closest to starting first, a game at most every 10 minutes, and never touches the last 35 requests of the month (kept for closing lines). With 0 it is closing lines only. Each key you add brings its own allowance; the next key is used when one is spent."
    const val NOTES = "Test key reads your plan's real allowance (an unmetered call). The app always keeps the last 10 requests, spends at most an even share of the rest per day, and never asks for player props (the free plan has none). Put a v5 key under OddsPapi (v5) instead; the test tells you if you pasted it in the wrong place."
}
