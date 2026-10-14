package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpMode
import com.tjshea.vigilant.data.scanner.TrapGuard

/**
 * Every protection against bad bets and bids in one list (Tj, 2026-10-14: "How do I turn on all the safeguards you made against bad bets and bids" ... "Yes, add the safeguards screen"). Each is an [Item]: what
 * it protects against and why it exists (the evidence), whether it is on now, and how to switch it. [allOn] switches every one on at once. Pure.
 */
object Safeguards {
    class Item(val key: String, val title: String, val why: String, val isOn: (ScanSettings) -> Boolean, val set: (ScanSettings, Boolean) -> ScanSettings)

    private fun guards(f: (com.tjshea.vigilant.data.scanner.ScanGuards, Boolean) -> com.tjshea.vigilant.data.scanner.ScanGuards): (ScanSettings, Boolean) -> ScanSettings =
        { s, on -> s.copy(vigGuards = f(s.vigGuards, on)) }

    val ALL: List<Item> = listOf(
        Item(
            "propHaircut", "Cut player-prop fair prices (Overs 7 points, Unders 3)",
            "Your history: prop Overs won 39.7% against a 49.4% fair (t -4.2). The cut is applied before the edge, stars, auto-bet, bids and Bet sheet see a prop.",
            { it.propHaircut }, guards { g, on -> g.copy(propHaircut = on) },
        ),
        Item(
            "profitProven", "Profit-proven only",
            "Blocks the auto-bet and bids on the types of bet that lost money in your history, and lowers their stars.",
            { it.gates.profitProven }, { s, on -> s.copy(gates = s.gates.copy(profitProven = on)) },
        ),
        Item(
            "repeatGuard", "Repeat and hedge guard",
            "At most 2 bets/bids and \$100 on one side, one line per team or player per game, and the other side only when both sides together are a sure profit.",
            { it.gates.repeatGuard }, { s, on -> s.copy(gates = s.gates.copy(repeatGuard = on)) },
        ),
        Item(
            "gameLimit", "Most at risk on one game",
            "Every line, prop and bid of one game counts as one risk (default \$25): one team at +5, +6 and +10 is one bet that can lose three times.",
            { it.apiMaxPerGame > 0.0 }, { s, on -> s.copy(apiMaxPerGame = if (on) (if (s.apiMaxPerGame > 0.0) s.apiMaxPerGame else 25.0) else 0.0) },
        ),
        Item(
            "sharpCheck", "Sharp-book check on the auto-bet",
            "Skips a bet the sharpest book for its kind of bet gives less than a 1% edge: the edge a bet keeps by the close tracks the sharp book's own.",
            { it.sharpAutoBet != SharpMode.OFF }, { s, on -> s.copy(sharpAutoBet = if (on) (if (s.sharpAutoBet == SharpMode.OFF) SharpMode.VETO else s.sharpAutoBet) else SharpMode.OFF) },
        ),
        Item(
            "trapGuard", "Trap guard: games far from the start",
            "Bets and bids on games starting more than ${TrapGuard.DEFAULT_EARLY_HOURS} hours away lost to the close; they are left alone (Settings › Alerts and the Bids tab set the hours).",
            { it.trapEarlyHours > 0 }, { s, on -> s.copy(trapEarlyHours = if (on) (if (s.trapEarlyHours > 0) s.trapEarlyHours else TrapGuard.DEFAULT_EARLY_HOURS) else 0) },
        ),
        Item(
            "trapMove", "Trap guard: a price that just moved",
            "Before a moneyline, spread or total, Novig's own recent trades are read; a price that moved 2 cents or more as the other side was bought is skipped.",
            { it.trapNovigMove }, { s, on -> s.copy(trapNovigMove = on) },
        ),
        Item(
            "overExtra", "Prop Overs need extra edge",
            "A prop Over must beat the auto-bet's minimum by 3 more points (ignored while a minimum star rating decides).",
            { it.vigGuards.autoBetOverPropExtraEv > 0.0 }, guards { g, on -> g.copy(autoBetOverPropExtraEv = if (on) (if (g.autoBetOverPropExtraEv > 0.0) g.autoBetOverPropExtraEv else 0.03) else 0.0) },
        ),
        Item(
            "thinEarly", "Cut thin early shots-on-goal fair prices",
            "NHL shots-on-goal fair prices built from fewer than 5 books, more than 12 hours out, ran about 3 points above Pinnacle's close.",
            { it.thinEarlyHaircut }, guards { g, on -> g.copy(thinEarlyHaircut = on) },
        ),
        Item(
            "badOdds", "Ignore books with wrong-looking odds",
            "A book whose odds are an extreme outlier, or put the favourite on the other side, is left out of the fair line.",
            { it.ignoreBadOdds }, guards { g, on -> g.copy(ignoreBadOdds = on) },
        ),
        Item(
            "oneVote", "One vote per company",
            "Sister sites, and books on one odds provider, count once in every fair line, star rating and books-agree count.",
            { it.oneVotePerCompany }, guards { g, on -> g.copy(oneVotePerCompany = on) },
        ),
        Item(
            "bidGuard", "Bid guard",
            "Watches every resting bid and takes one down that has stopped being worth it.",
            { it.guards.bidGuard }, { s, on -> s.copy(guards = s.guards.copy(bidGuard = on)) },
        ),
    )

    fun allOn(s: ScanSettings): ScanSettings = ALL.fold(s) { acc, i -> i.set(acc, true) }

    /** The safeguards that are off now. */
    fun off(s: ScanSettings): List<Item> = ALL.filter { !it.isOn(s) }
}
