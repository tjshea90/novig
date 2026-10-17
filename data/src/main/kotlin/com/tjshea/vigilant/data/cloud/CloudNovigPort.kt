package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.signing.NovigApiException
import com.tjshea.vigilant.data.novig.trading.NovigOrder
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.engine.EvMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The engine's orders on Novig (Tj, 2026-10-15; TCL20d): one immediate-or-cancel order per decision, from the Vigilant subaccount, signed with the trading key the phone sent ([CloudKeys.TRADE]). The money rules are
 * the phone's placer's, kept short because the decision (price, size, edge, caps) was made upstream by [CloudTrader]:
 *  - **one order per decision, never two.** The client id is the decision's ([CloudTrader.clientId], a UUID). An answer that is lost is looked up by that id in every status before anything is called failed; an order
 *    that cannot be found is [CloudOrderPort.Placed.Unconfirmed], never sent again;
 *  - the order is an `IOC` at the ladder's deepest price the stake reaches, so it fills what is there at that price or better and never rests;
 *  - a refusal that is about the account or the place (451 location / network, 401, 403, 423) is a [CloudOrderPort.Placed.Stop] and [blocked] says why, in words: the trader stops ordering and the phone tells Tj. The
 *    OTHER location check (the key holder's device: the Novig app opened on the phone within 3 days) is Tj's, never worked around;
 *  - [cancelResting] cancels only orders whose client id is one of this engine's own, because the phone's bid desk rests bids under the same subaccount and they are not the engine's to cancel.
 * Orders go by REST (an IOC takes about a second); the websocket route ([com.tjshea.vigilant.data.novig.trading.NovigOrderSocket]) is a latency choice for live games and not needed for pregame bets.
 */
class CloudNovigPort(
    private val trading: NovigTradingClient,
    /** The key id that addresses the subaccount for the balance (the live trading key's id, NOVIG_API.md §15). */
    private val subaccountKeyId: String,
    /** The client ids of the orders this engine has sent (its ledger), to know which resting orders are its own. */
    private val owned: () -> Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val log: (String) -> Unit = {},
) : CloudOrderPort {
    @Volatile override var blocked: String? = null
        private set

    private fun refusal(e: NovigApiException): String? =
        if (e.status in STOP_STATUSES && !e.betLocked) e.brief.take(300) else null

    override suspend fun cash(): Double? = try {
        trading.balance(subaccountKeyId).also { blocked = null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: NovigApiException) {
        blocked = refusal(e); log("balance read refused: ${e.status} ${e.code}"); null
    } catch (e: Exception) {
        log("balance read failed: ${e.javaClass.simpleName}"); null
    }

    override suspend fun place(entry: TradeEntry, limitPrice: Double): CloudOrderPort.Placed {
        val orderId: String = try {
            trading.placeOrder(entry.outcomeId, limitPrice, entry.contracts, "IOC", entry.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NovigApiException) {
            refusal(e)?.let { blocked = it; return CloudOrderPort.Placed.Stop(it) }
            // 400 (too small, price off the grid, market closed) and the rest: nothing was bought
            return CloudOrderPort.Placed.Missed("${e.status} ${e.code ?: ""} ${e.serverMessage ?: ""}".trim().take(160))
        } catch (e: Exception) {
            // The answer was lost: the order may be on Novig. Look for it by the decision's client id before anything is assumed.
            val found = withContext(NonCancellable) { findByClientId(entry.id, entry.outcomeId) }
                ?: return CloudOrderPort.Placed.Unconfirmed("Novig did not answer the order and its lists do not show it (${e.javaClass.simpleName}): kept as at risk, not sent again")
            found
        }
        return withContext(NonCancellable) { finish(entry, orderId) }
    }

    /** Waits for the order to end and reads what it filled. An IOC is over in about a second. */
    private suspend fun finish(entry: TradeEntry, orderId: String): CloudOrderPort.Placed {
        val started = clock()
        var order: NovigOrder? = null
        while (clock() - started < ORDER_WAIT_MS) {
            order = try { trading.order(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (order?.terminal == true) break
            pause(POLL_MS)
        }
        var fills = readFills(orderId)
        if (fills.isEmpty() && order?.status == "FILLED") { pause(1_000L); fills = readFills(orderId) }
        if (fills.isEmpty()) {
            return if (order?.terminal == true) CloudOrderPort.Placed.Missed("nothing filled: nobody was selling at that price any more")
            else CloudOrderPort.Placed.Unconfirmed("Novig took the order ($orderId) but had not finished it after ${ORDER_WAIT_MS / 1000}s: kept as at risk, not sent again")
        }
        val qty = fills.sumOf { it.qty }
        val dollars = fills.sumOf { it.cost }
        val fee = fills.sumOf { it.fee }
        return CloudOrderPort.Placed.Filled(qty, if (qty > 0) dollars / (qty * EvMath.CONTRACT_PAYOUT_DOLLARS) else 0.0, fee, orderId)
    }

    private suspend fun readFills(orderId: String) = try { trading.fills(orderId) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }

    /** The order carrying [clientId] on [outcomeId] in any status, looked for twice (an order of a lost answer can appear late). */
    private suspend fun findByClientId(clientId: String, outcomeId: String): String? {
        repeat(2) { round ->
            pause(if (round == 0) 1_500L else 3_000L)
            for (status in listOf("PENDING", "OPEN", "FILLED", "CANCELED", "REJECTED")) {
                val hit = try { trading.orders(status, outcomeId = outcomeId).firstOrNull { it.clientId == clientId } } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                if (hit != null) return hit.orderId
            }
        }
        return null
    }

    override suspend fun positions(): List<com.tjshea.vigilant.data.novig.trading.NovigPosition>? = try {
        trading.positions()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("positions read failed: ${e.javaClass.simpleName}"); null
    }

    override suspend fun cancelResting() {
        val mine = owned()
        if (mine.isEmpty()) return
        val open = try { trading.orders("OPEN") + trading.orders("PENDING") } catch (e: CancellationException) { throw e } catch (e: Exception) { log("could not list resting orders to cancel: ${e.javaClass.simpleName}"); return }
        val ids = open.filter { it.clientId != null && it.clientId in mine }.map { it.orderId }
        for (id in ids) {
            runCatching { trading.cancelOrder(id) }.onFailure { if (it is CancellationException) throw it; log("cancel $id failed: ${it.javaClass.simpleName}") }
        }
        if (ids.isNotEmpty()) log("window ended: cancelled ${ids.size} resting order(s) of the engine's own")
    }

    companion object {
        const val ORDER_WAIT_MS = 12_000L
        const val POLL_MS = 400L

        /** The refusals that stop ordering: 451 (location / network), 401 (key), 403 (scope, VPN, KYC, rate), 423 (locked / halted / self-excluded). */
        val STOP_STATUSES = setOf(451, 401, 403, 423)
    }
}
