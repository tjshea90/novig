package com.tjshea.vigilant.data.livebid

import com.tjshea.vigilant.data.pinnodds.LiveTrigger
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * Live autopilot (Tj, 2026-10-10: "One feature that can handle all of the following simultaneously: look for stale or mispriced live odds across all live games and auto bet them; make automatic bids
 * on live games that are under the fair odds ... rugged guards; fill up with bids up to the wallet balance"). One tap sets ALL THREE live engines to work together on the same Pinnacle feed:
 *  - the live TAKER (Settings › Pinnodds live) buys, immediate-or-cancel, a Novig price that lags Pinnacle's fair by 4% or more after Novig's fee, after a score OR when the ask is an order left
 *    up at Pinnacle's earlier price ([LiveTrigger.EITHER]), across every live game it can match;
 *  - the live BID desk posts post-only bids at 3% under Pinnacle's fair on the main lines ([LiveBidPresets.FILL]), as many as the wallet carries ([LiveBidLimits.fillWallet]), each pulled the
 *    moment a score, a danger frame, silence, a fading edge or a moving Novig middle says so.
 *  - the live TAIL bettor ([ScanSettings.tailLive]) buys far strikes the late-game model calls decided (ESPN score and clock, Novig's own prices): it needs no outside price, so it carries on after the
 *    Pinnodds trial ends.
 * It touches the rules and the switches only. Tj's money limits (stakes, per-game and per-day caps, the halt on a day's loss, the reserve kept in the wallet) are his and stay.
 */
object LiveAutopilot {
    const val TAKER_MIN_EV = 0.04
    const val TAKER_MIN_MOVE = 0.02

    /** [s] with the autopilot's rules applied and both engines on. [real]: both place real orders (the halts of each cleared); false: both only write down what they would do. */
    fun apply(s: ScanSettings, real: Boolean): ScanSettings =
        LiveBidPresets.apply(s, LiveBidPresets.FILL).copy(
            liveBid = true, liveBidReal = real, liveBidHalted = null,
            liveBidLimits = s.liveBidLimits.copy(fillWallet = true),
            pinnLive = true, pinnLiveBet = real, pinnLiveHalted = null,
            tailLive = s.tailLive.copy(on = true, bet = real, halted = null),
            pinnLiveTrigger = LiveTrigger.EITHER, pinnLiveMinEv = TAKER_MIN_EV, pinnLiveMinMove = TAKER_MIN_MOVE,
        )

    /** Whether both engines are on with the autopilot's rules (the page says "In force"). */
    fun inForce(s: ScanSettings): Boolean =
        s.liveBid && s.pinnLive && s.tailLive.on && s.liveBidLimits.fillWallet && LiveBidPresets.matches(s, LiveBidPresets.FILL) &&
            s.pinnLiveTrigger == LiveTrigger.EITHER && s.pinnLiveMinEv <= TAKER_MIN_EV + 1e-9

    /** Whether any of the two engines is placing real orders. */
    fun real(s: ScanSettings): Boolean = (s.liveBid && s.liveBidReal) || (s.pinnLive && s.pinnLiveBet) || (s.tailLive.on && s.tailLive.bet)

    /** Both engines off (real ones included); their rules and limits stay as they are. */
    fun off(s: ScanSettings): ScanSettings = s.copy(liveBid = false, liveBidReal = false, pinnLive = false, pinnLiveBet = false, tailLive = s.tailLive.copy(on = false, bet = false))
}
