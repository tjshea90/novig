package com.tjshea.vigilant.data.novig.trading

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The bid guard (Tj, 2026-10-11: "I accidentally left a bad bid up indefinitely, and someone filled the bid and I lost almost immediately"). A resting order with NO expiry is a standing offer to whoever knows more,
 * for as long as it lives; every bid Vigilant posts carries a time limit, so an order without one was posted some other way (Novig's own app, or a tool that sent none). The guard reads the subaccount's open
 * orders and takes down any that has no expiry and has rested longer than the limit Tj set (default 30 minutes), whoever posted it. Orders with an expiry are time-bounded already and are left to it.
 */
object BidGuard {
    /** Why [orders] should come down now, by order id: resting, no expiry, older than [maxRestMs]. Pure. */
    fun stale(orders: List<NovigOrder>, now: Long, maxRestMs: Long): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (o in orders) {
            if (o.terminal || o.remaining <= 0L || o.status != "OPEN") continue
            // Immediate-or-cancel and fill-or-kill orders never rest.
            if (o.tif.equals("IOC", true) || o.tif.equals("FOK", true)) continue
            val age = now - o.createdTs
            if (o.expiresTs == null && age > maxRestMs) out[o.orderId] = "no expiry, resting ${age / 60_000L} min (limit ${maxRestMs / 60_000L})"
        }
        return out
    }

    /** What the guard has done, for Diagnostics and Settings. */
    data class Status(val lastRunMs: Long = 0L, val cancelledTotal: Int = 0, val lastCancelMs: Long? = null, val lastNote: String? = null, val lastError: String? = null)

    /** The loop: every [everyMs] it reads the open orders and cancels the stale ones. Costs one read a pass when nothing is stale. */
    class Runner(
        private val scope: CoroutineScope,
        private val client: () -> NovigTradingClient?,
        private val enabled: () -> Boolean,
        private val maxRestMs: () -> Long,
        private val onCancelled: (List<Pair<NovigOrder, String>>) -> Unit = {},
        private val clock: () -> Long = System::currentTimeMillis,
        private val everyMs: Long = 30_000L,
    ) {
        private val state = MutableStateFlow(Status())
        val status: StateFlow<Status> = state.asStateFlow()
        private var job: kotlinx.coroutines.Job? = null

        fun start() {
            if (job?.isActive == true) return
            job = scope.launch {
                while (true) {
                    delay(everyMs)
                    try { pass() } catch (e: CancellationException) { throw e } catch (e: Exception) { state.value = state.value.copy(lastError = (e.message ?: e.javaClass.simpleName).take(120)) }
                }
            }
        }

        /** One pass; returns how many orders it cancelled. */
        suspend fun pass(): Int {
            val c = client()
            if (!enabled() || c == null) return 0
            val now = clock()
            val open = c.orders("OPEN")
            val stale = stale(open, now, maxRestMs())
            state.value = state.value.copy(lastRunMs = now, lastError = null)
            if (stale.isEmpty()) return 0
            val byId = open.associateBy { it.orderId }
            var cancelled = 0
            val done = ArrayList<Pair<NovigOrder, String>>()
            for (chunk in stale.keys.chunked(NovigTradingClient.MAX_BATCH)) {
                val r = c.cancelOrdersBatch(chunk)
                r.canceled.forEach { id -> byId[id]?.let { done += it to (stale[id] ?: "stale") }; cancelled++ }
            }
            if (cancelled > 0) {
                state.value = state.value.copy(cancelledTotal = state.value.cancelledTotal + cancelled, lastCancelMs = now, lastNote = "took down $cancelled order${if (cancelled == 1) "" else "s"}: ${done.firstOrNull()?.second}")
                onCancelled(done)
            }
            return cancelled
        }
    }
}
