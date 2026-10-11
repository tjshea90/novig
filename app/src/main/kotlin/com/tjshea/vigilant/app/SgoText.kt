package com.tjshea.vigilant.app

/** The words of Settings › SportsGameOdds Pro (SPORTSGAMEODDS_API.md). */
object SgoText {
    const val INTRO = "SportsGameOdds Pro is one feed for most of what Vigilant reads: Pinnacle, Circa, bet365 and the US books, game lines, every alternate line, 1st-half lines and player props, each price with the time it was last seen, plus closing lines and final scores. Add your key (several are fine: they are tried in order), tap Test key, then switch it on."
    const val SWITCH_TITLE = "Use SportsGameOdds Pro"
    fun switchSub(hasKey: Boolean) = if (!hasKey) "Add a key first. Until you do, nothing changes." else "Off: SportsGameOdds is never called. On: it is read for everything it can, beside the other APIs you have on."
    const val REPLACES = "While it is on and answering, SportsGameOdds is read beside every other API you have switched on: none of them rests for it, and each is asked for every league and kind of data it carries (switch an API off in Settings › APIs and it is never called). Pinnacle: with Pinnodds switched on and a key saved (Settings › Pinnodds live), its fresh Pinnacle board is asked first. If SportsGameOdds stops answering, the others carry on without it until it does."
}
