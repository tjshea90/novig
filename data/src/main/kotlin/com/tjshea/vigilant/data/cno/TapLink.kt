package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where a tapped CNO bet opens in Novig (Tj, 2026-09-27: "sometimes they pull up the novig bet
 * slip, but sometimes they don't"; then "make it so vigilant can open the bet in novig even if it
 * can't reach cno servers"). The link read ahead of time opens at once. Otherwise CNO's link and
 * Novig's own catalog are asked at the same time and the first exact link wins, so a CNO that
 * hangs costs the tap nothing: Novig's catalog names the exact outcome for nearly every CNO bet
 * (60 of 60 in a live check, each the same outcome CNO's own link opens; RESEARCH.md §20.3). Only
 * when neither names the bet is its game opened instead. When CNO's list is failing, Novig's
 * catalog is asked alone first; when CNO asked for a pause ([fromCno] null), CNO isn't asked.
 */
object TapLink {

    /** A tapped bet's way into Novig: its bet slip ([exact]) or only its game. */
    data class Link(val link: String, val exact: Boolean)

    /** The longest a tap waits for CNO's link… */
    const val CNO_MS = 5_000L

    /** …and for Novig's catalog. */
    const val NOVIG_MS = 8_000L

    suspend fun resolve(
        cached: String?,
        cnoFailing: Boolean,
        fromCno: (suspend () -> String?)?,
        fromNovig: suspend () -> NovigBetFinder.Found?,
        cnoMs: Long = CNO_MS,
        novigMs: Long = NOVIG_MS,
    ): Link? {
        if (cached != null) return Link(cached, exact = true)
        suspend fun cno(): Link? = fromCno?.let { ask -> withTimeoutOrNull(cnoMs) { quietly { ask() } }?.let { Link(it, exact = true) } }
        suspend fun novig(): Link? = withTimeoutOrNull(novigMs) { quietly { fromNovig() } }?.let { Link(it.link, exact = it is NovigBetFinder.Found.Bet) }
        if (cnoFailing || fromCno == null) {
            val novig = novig()
            return if (novig?.exact == true) novig else cno() ?: novig
        }
        // Both at once: the first exact link wins, the other is cancelled.
        return coroutineScope {
            val pending = mutableListOf(async { cno() }, async { novig() })
            var game: Link? = null
            while (pending.isNotEmpty()) {
                val (done, link) = select { pending.forEach { d -> d.onAwait { d to it } } }
                pending.remove(done)
                if (link?.exact == true) {
                    pending.forEach { it.cancel() }
                    return@coroutineScope link
                }
                if (link != null) game = link
            }
            game
        }
    }

    /** [block]'s value, null when it threw (cancellation still cancels). */
    private suspend fun <T> quietly(block: suspend () -> T?): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
