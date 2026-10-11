package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The live sniper (Tj, 2026-10-11: "Build the sniper"; TASKS.md TF): ONE switch for everything that hunts a live price Novig has wrong, and puts up bids beside it:
 *  - the [LiveAutopilot]'s three engines: the TAKER (a Novig price lagging Pinnacle's live fair, after a score or an order left at the old price), the TAIL bettor (a far strike the late-game model calls
 *    decided, e.g. an under at a total the score has all but passed: it needs no outside price) and the BID desk (post-only bids under the live fair, the maker side, up at the same time);
 *  - the COVER taker ([ScanSettings.burstTrade]): both legs of a ladder cover bought at once, with the burst recorder it needs ([ScanSettings.burstRecorder]) switched on. It stays idle until the recorder's own
 *    verdict says covers fill on this phone ([com.tjshea.vigilant.data.novig.burst.BurstStudy]), so it can only help.
 * The bid desk runs at the Safety/Volume slider's position ([LiveBidSlider]); with no position yet (never moved, or a preset or hand edits), [DEFAULT_LEVEL]. Tj's money limits are his and never change here.
 * Paper first: [apply] with real = false switches every engine on and sends nothing.
 */
object LiveSniper {
    /** "More fills" on the slider: the autopilot's bids were always the thin-edge, many-fills kind, and 75 keeps the 30 s edge check and the pull line. */
    const val DEFAULT_LEVEL = 75

    fun apply(s: ScanSettings, real: Boolean): ScanSettings {
        val level = if (s.liveBidQuality.level in LiveBidSlider.MIN..LiveBidSlider.MAX) s.liveBidQuality.level else DEFAULT_LEVEL
        val base = LiveAutopilot.apply(s, real)
        return base.copy(
            liveBidQuality = LiveBidSlider.apply(s.liveBidQuality, level),
            burstRecorder = true, burstTrade = real, burstTradeHalted = null,
        )
    }

    /** Whether the engines are on together. The bids' rules are the slider's, so only the switches (and the taker's rules) are compared. */
    fun inForce(s: ScanSettings): Boolean =
        s.liveBid && s.pinnLive && s.tailLive.on && s.burstRecorder && s.liveBidLimits.fillWallet &&
            s.pinnLiveTrigger == com.tjshea.vigilant.data.pinnodds.LiveTrigger.EITHER && s.pinnLiveMinEv <= LiveAutopilot.TAKER_MIN_EV + 1e-9

    /** Whether any engine of the sniper is placing real orders. */
    fun real(s: ScanSettings): Boolean = LiveAutopilot.real(s) || (s.burstTrade && s.burstRecorder)

    /** Every engine off, real ones included; rules and limits stay. The burst recorder is left as it was (Research mode may be using it). */
    fun off(s: ScanSettings): ScanSettings = LiveAutopilot.off(s).copy(burstTrade = false)
}
