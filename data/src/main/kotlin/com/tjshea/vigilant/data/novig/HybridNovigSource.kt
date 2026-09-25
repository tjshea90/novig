package com.tjshea.vigilant.data.novig

import com.tjshea.vigilant.data.novig.stream.NovigStream
import com.tjshea.vigilant.data.novig.stream.StreamState

/**
 * Books from Novig's websocket when it's live, from the public REST routes otherwise.
 *
 * With a key connected and the stream up, a refresh reads every book from memory: no network,
 * so the scanner can re-price every couple of seconds for almost nothing. A market the stream
 * doesn't have yet (just subscribed, or resyncing after a gap) falls back to REST, but each such
 * market at most once per [restFallbackMs], so a slow snapshot can't turn into polling.
 */
class HybridNovigSource(
    private val rest: NovigPublicClient,
    private val stream: () -> NovigStream?,
    private val restFallbackMs: Long = 15_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : NovigSource {

    private val lastRest = HashMap<String, Long>()

    val streaming: Boolean get() = stream()?.state?.value is StreamState.Live

    override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) =
        rest.events(leagues, statuses, startsBefore)

    override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) =
        rest.markets(leagues, marketTypes, eventStatuses, startsBefore)

    override suspend fun market(marketId: String) = rest.market(marketId)

    override fun focus(eventIds: Set<String>) {
        stream()?.setEvents(eventIds)
    }

    override suspend fun books(marketIds: Collection<String>): BookBatch {
        val s = stream()
        if (s == null || s.state.value !is StreamState.Live) return rest.books(marketIds)

        val now = clock()
        val fromStream = HashMap<String, NovigBook>()
        val missing = ArrayList<String>()
        for (id in marketIds) {
            val b = s.book(id)
            if (b != null) fromStream[id] = b else missing += id
        }
        val due = synchronized(lastRest) { missing.filter { now - (lastRest[it] ?: 0L) >= restFallbackMs } }
        val restBatch = if (due.isNotEmpty()) rest.books(due) else null
        synchronized(lastRest) { due.forEach { lastRest[it] = now } }
        val cachedRest = missing.filter { it !in due }.mapNotNull { id -> rest.cached(id)?.let { id to it } }.toMap()
        return BookBatch(
            books = fromStream + cachedRest + (restBatch?.books ?: emptyMap()),
            notModified = (restBatch?.notModified ?: 0) + fromStream.size,
            fetched = restBatch?.fetched ?: 0,
            failed = restBatch?.failed ?: 0,
            retryAfterSeconds = restBatch?.retryAfterSeconds,
            lastError = restBatch?.lastError,
        )
    }
}
