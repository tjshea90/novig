package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.novig.trading.maker.QuickLikely

/**
 * The long-run saver (Tj, 2026-10-08: "leave the app on and background auto bid for hours … worried about api usage running out too fast … without sacrificing accuracy or
 * quality of bids"; RESEARCH.md §119). Two savings for a bid desk that runs all day, neither of which loosens a rule a bid is judged by (freshness, margin, sharp book, price band):
 *
 *  1. **A lean background scan** ([profile]): with "Quick & likely to win" bids, Vigilant's background scan reads only the families a bid can go on (player props, team totals).
 *     The game-line boards (ParlayAPI's `/odds` 3-5 credits a league, The Odds API's 1 per market, the 1st-half call 2) price moneylines, spreads and game totals, which
 *     Quick & likely never bids on; the families already key each feed's call ([Scanner]'s feed keys, `TheOddsApiClient.marketsFor`), so narrowing them stops those reads.
 *     Tj's own scan on the +EV tab, Check odds now and CNO are not narrowed.
 *  2. **A slow pace while every game is far off** ([gapSeconds]): a quote on a game over 3 h from its start may be 10 minutes old ([Freshness.FAR_OFF_AGE_MS]), so the 4-minute
 *     background pace re-reads what has not gone old; with no game inside the 3-hour reach the scan waits the 8 minutes the low-usage mode already proved
 *     ([LowUsageBids.FAR_GAP_SECONDS], `LowUsagePaceTest`). The near pace is the usual one.
 *
 * Both are on by default ([ScanSettings.makerLongRun]) because they save credits without changing a bid; the price is told on the switch: Vigilant's background scan then
 * alerts on props and team totals only, and a bid on a game over 3 hours away is re-priced every 8 minutes, not every 4.
 */
object LongRunBids {

    /** The families a bid of [kind] can go on: a prop is read with the player-props call, a team total with the team-total board. Other kinds are not Quick & likely's. */
    fun family(kind: BetKind): MarketFamily? = when (kind) {
        BetKind.PROP -> MarketFamily.PLAYER_PROPS
        BetKind.TEAM_TOTAL -> MarketFamily.TEAM_TOTAL
        else -> null
    }

    /** Vigilant's own scan prices bids (the saver is about its credits), bids are on, and it is not the low-usage mode (which has its own, narrower scan and pace). */
    fun active(s: ScanSettings): Boolean =
        s.makerLongRun && (s.maker || s.makerRecommend) && s.makerSource == BidSource.VIGILANT && s.makerFocus != BidFocus.LOW_USAGE

    /** The families the background scan keeps: Tj's families that Quick & likely's kinds (the ones Tj left on) can go on. */
    fun keep(s: ScanSettings): Set<MarketFamily> =
        s.makerKinds.intersect(QuickLikely.KINDS).mapNotNull { family(it) }.toSet().intersect(s.families)

    /**
     * Whether the background scan is lean now: the saver is active, bids are Quick & likely to win (the only focus whose kinds are known to be props and team totals),
     * and there is a family to keep (an empty one, every kind turned off, leaves the scan as it was: nothing to narrow to).
     */
    fun leanApplies(s: ScanSettings): Boolean = active(s) && s.makerFocus == BidFocus.QUICK_LIKELY && keep(s).isNotEmpty()

    /**
     * [s] as the lean background scan reads it ([ScanSettings.effective]): the kept families only, over the hours a bid could be posted on ([LowUsageBids.windowHours]: the
     * trap guard's hours, never past Tj's own reach). Every other setting, the fair, the books and the feeds, are Tj's.
     */
    fun profile(s: ScanSettings): ScanSettings {
        val keep = keep(s)
        if (keep.isEmpty()) return s
        return s.copy(families = keep, startsWithinHours = LowUsageBids.windowHours(s), leanScan = true)
    }

    /** Whether the slow far pace applies: the saver is active (any focus but low usage). */
    fun slowFarApplies(s: ScanSettings): Boolean = active(s)

    /**
     * The gap between two background runs of Vigilant's own scan now: [LowUsageBids.FAR_GAP_SECONDS] while no game a bid could still go on ([ScanSettings.makerStopMinutes]
     * before its start) is inside the near reach ([LowUsageBids.autoGapSeconds]), else the usual [ScanSettings.vigilantGapSeconds]. [startsMs] null (no scan yet, or it
     * failed) = the usual one: nothing is known to be far.
     */
    fun gapSeconds(s: ScanSettings, startsMs: Collection<Long>?, now: Long): Int {
        val usual = s.vigilantGapSeconds
        if (!slowFarApplies(s) || startsMs == null) return usual
        val far = LowUsageBids.autoGapSeconds(startsMs, now, s.makerStopMinutes.coerceAtLeast(0) * 60_000L) == LowUsageBids.FAR_GAP_SECONDS
        return if (far) maxOf(usual, LowUsageBids.FAR_GAP_SECONDS) else usual
    }

    /** What Settings › Bids says under the switch. */
    const val EXPLAINER =
        "Keeps the background scan from spending credits on what bids never use. With Quick & likely to win, Vigilant's background scan reads only player props and team totals " +
            "(no moneyline, spread, game-total or 1st-half boards: 3 to 10 credits a league each scan), and while no game is inside 3 hours of its start it scans every 8 minutes " +
            "instead of 4 (a quote on a far game may be 10 minutes old, so nothing is priced from an older one). No bid rule changes. The price: Vigilant's background scan no " +
            "longer alerts on game lines, and a bid on a game over 3 hours away is re-priced every 8 minutes. Your own Scan, Check odds now and CrazyNinjaOdds are not affected."
}
