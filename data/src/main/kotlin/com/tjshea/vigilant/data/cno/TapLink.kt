package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where a tapped CNO bet opens in Novig (Tj, 2026-09-27: "sometimes they pull up the novig bet
 * slip, but sometimes they don't"), fastest first: the link read ahead of time (no network);
 * CNO's, asked now for at most [cnoMs]; the bet found in Novig's own catalog (at most [novigMs]),
 * or failing that its game. When CNO's list is failing, Novig's catalog is asked first, and CNO
 * only if Novig found no more than the game.
 */
object TapLink {

    /** A tapped bet's way into Novig: its bet slip ([exact]) or only its game. */
    data class Link(val link: String, val exact: Boolean)

    /** A tap waits this long for CNO's link… */
    const val CNO_MS = 5_000L

    /** …and this long for Novig's catalog. */
    const val NOVIG_MS = 8_000L

    suspend fun resolve(
        cached: String?,
        cnoFailing: Boolean,
        fromCno: suspend () -> String?,
        fromNovig: suspend () -> NovigBetFinder.Found?,
        cnoMs: Long = CNO_MS,
        novigMs: Long = NOVIG_MS,
    ): Link? {
        if (cached != null) return Link(cached, exact = true)
        suspend fun cno() = withTimeoutOrNull(cnoMs) { runCatchingNotCancel { fromCno() } }?.let { Link(it, exact = true) }
        suspend fun novig() = withTimeoutOrNull(novigMs) { runCatchingNotCancel { fromNovig() } }?.let { Link(it.link, exact = it is NovigBetFinder.Found.Bet) }
        if (!cnoFailing) return cno() ?: novig()
        val novig = novig()
        return if (novig?.exact == true) novig else cno() ?: novig
    }

    /** [block]'s value, null when it threw (cancellation still cancels). */
    private suspend fun <T> runCatchingNotCancel(block: suspend () -> T?): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
