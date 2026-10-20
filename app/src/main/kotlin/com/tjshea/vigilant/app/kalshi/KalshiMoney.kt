package com.tjshea.vigilant.app.kalshi

import com.tjshea.vigilant.data.kalshitrade.KalshiAutoMode
import com.tjshea.vigilant.data.tracker.BetStatus
import java.util.TimeZone

/**
 * Kalshi's money at a glance for the Engine tab's Money view (CH38, Tj 2026-10-20): the same few numbers the Novig card shows (open bets, bids resting, placed today, settled), read from the
 * phone's own Kalshi record ([KalshiUi]) and never mixed into Novig's. Pure: draws from values, places nothing.
 */
data class KalshiMoney(
    val kalshiOn: Boolean,
    val cash: Double?,
    val portfolio: Double?,
    val open: Int,
    val atRisk: Double,
    val placedToday: Int,
    val settledProfit: Double,
    val won: Int,
    val lost: Int,
    val profitToday: Double,
    val bidsResting: Int,
    val bidsDollars: Double,
    val bidLine: String,
    val ordersOn: Boolean,
    val autoMode: KalshiAutoMode,
    val bidsSwitchOn: Boolean,
) {
    companion object {
        fun of(ui: KalshiUi, now: Long): KalshiMoney {
            val eastern = TimeZone.getTimeZone("America/New_York")
            val dayStart = java.util.Calendar.getInstance(eastern).apply { timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
            val live = ui.bets
            val open = live.filter { it.status == BetStatus.PENDING }
            val settled = live.filter { it.status != BetStatus.PENDING }
            val today = settled.filter { (it.settledAtMs ?: 0L) >= dayStart }
            val bs = ui.bidStatus
            val line = when {
                !ui.config.scan.maker -> "Kalshi bids are off (Kalshi › Setup › Bids)."
                !ui.config.tradingEnabled -> "Kalshi bids need \"Place orders on Kalshi\" switched on (Kalshi › Setup)."
                bs == null -> if (ui.result == null) "No Kalshi bid pass yet: scan Kalshi first (pull to refresh on +EV with Kalshi on)." else "No Kalshi bid pass yet."
                bs.stopped.isNotBlank() -> "Kalshi bids stopped: ${bs.stopped}"
                else -> bs.note
            }
            return KalshiMoney(
                kalshiOn = ui.venues.kalshi,
                cash = ui.balance,
                portfolio = ui.portfolioValue,
                open = open.size,
                atRisk = open.sumOf { it.stake },
                placedToday = live.count { it.createdAtMs >= dayStart },
                settledProfit = settled.sumOf { it.profit ?: 0.0 },
                won = settled.count { it.status == BetStatus.WON },
                lost = settled.count { it.status == BetStatus.LOST },
                profitToday = today.sumOf { it.profit ?: 0.0 },
                bidsResting = bs?.resting ?: ui.bids.size,
                bidsDollars = bs?.restingDollars ?: 0.0,
                bidLine = line,
                ordersOn = ui.config.tradingEnabled,
                autoMode = ui.config.autoMode,
                bidsSwitchOn = ui.config.scan.maker,
            )
        }
    }
}
