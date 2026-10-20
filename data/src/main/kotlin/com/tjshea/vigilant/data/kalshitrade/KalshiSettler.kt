package com.tjshea.vigilant.data.kalshitrade

import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import kotlinx.coroutines.CancellationException

/**
 * Settles the Kalshi side's bets from Kalshi's own record (KV8): a pending bet's order says which market and which side it bought; Kalshi's settlements list says how the market ended.
 * `yes` or `no` is a win or a loss for the side held; any other end (a scalar value, a voided market) is a void and gives the stake back. A bet is only settled by a settlement
 * Kalshi reports, never guessed from a score, so the Kalshi P/L is what Kalshi paid.
 */
class KalshiSettler(
    private val trading: KalshiTradingClient,
    private val tracker: BetTracker,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sideOf = HashMap<String, Pair<String, String>>()

    /** Settles what Kalshi has settled; returns how many bets it ended. */
    suspend fun run(): Int {
        val now = clock()
        val pending = tracker.all().filter { it.status == BetStatus.PENDING && it.orderId != null && it.startsTs <= now }
        if (pending.isEmpty()) return 0
        val settlements = try {
            trading.settlements(sinceMs = pending.minOf { it.createdAtMs } - 24 * 3_600_000L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return 0
        }
        if (settlements.isEmpty()) return 0
        val byTicker = settlements.associateBy { it.ticker }
        var ended = 0
        for (bet in pending) {
            val orderId = bet.orderId ?: continue
            val (ticker, side) = sideOf[orderId] ?: run {
                val o = try { trading.order(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null } ?: return@run null
                (o.ticker to o.outcomeSide).also { sideOf[orderId] = it }
            } ?: continue
            val s = byTicker[ticker] ?: continue
            val status = when (s.result) {
                side -> BetStatus.WON
                "yes", "no" -> BetStatus.LOST
                else -> BetStatus.VOID
            }
            tracker.edit(bet.id) {
                it.copy(
                    status = status, settledAtMs = s.settledMs ?: clock(), settledBy = "kalshi", books = emptyList(),
                    gradeNote = "Settled by Kalshi: ${s.result.ifBlank { "no result" }}", gradeAtMs = clock(), gradeManual = false,
                )
            }
            ended++
        }
        return ended
    }
}
