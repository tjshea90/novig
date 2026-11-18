package com.tjshea.vigilant.data.rfq

import kotlinx.serialization.Serializable

/**
 * The parlay maker's state as the phone shows it (MKV, Tj 2026-11-18: "I want it in vigilant"): published in the engine's `status.json` (`rfq`) every few seconds, so the Engine tab's Parlays view is
 * a monitor of what the GitHub engine does and never a second maker. [config] is the engine's APPLIED configuration (`rfq-maker.json` on `cloud-config`), which the phone edits and writes back.
 */
@Serializable
data class RfqStatus(
    val atMs: Long = 0L,
    val config: RfqConfig = RfqConfig(),
    val connected: Boolean = false,
    val makerId: String? = null,
    /** Why quoting stopped (a lockout, an insolvent collateral...), or null. */
    val halted: String? = null,
    val seen: Long = 0L,
    val priceable: Long = 0L,
    val tradesSeen: Long = 0L,
    /** Trades of RFQs the engine could price: the evidence base the LIVE rule counts (200 needed). */
    val pricedTrades: Int = 0,
    /** Median of (the price the market paid - our fair), in points of pot, over [pricedTrades]; null before the first. */
    val medianGapPoints: Double? = null,
    /** Of [pricedTrades], how many our quote at the configured rule would have won, and what each would have earned (points of pot) if our fair is true. */
    val wouldWin: Long = 0L,
    val wouldEarnPoints: Double? = null,
    val quotesSent: Long = 0L,
    val won: Long = 0L,
    val confirmed: Long = 0L,
    val filled: Long = 0L,
    /** The maker's collateral in unsettled parlays (Novig's `GET /rfq/v3/collateral`), dollars. */
    val openCollateral: Double = 0.0,
    /** What the maker's fills actually paid (Novig's executions): settled parlays, how many the maker won and lost, parlays still open, and the profit over all of them and over the last 24 hours; null = not read. */
    val settled: Int = 0,
    val makerWon: Int = 0,
    val makerLost: Int = 0,
    val openParlays: Int = 0,
    val realizedPnl: Double? = null,
    val realizedPnl24h: Double? = null,
    /** The commonest reasons an RFQ was not priced, as `reason xN`. */
    val declines: List<String> = emptyList(),
    /** The socket's last word when it is not connected. */
    val note: String = "",
) {
    companion object {
        /** The evidence rule's count: LIVE is only recommended from this many priced trades (research/novig_rfq_maker_2026-11-18.md). */
        const val EVIDENCE_TRADES = 200
    }
}
