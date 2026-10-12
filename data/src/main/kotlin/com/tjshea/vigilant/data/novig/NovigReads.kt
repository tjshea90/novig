package com.tjshea.vigilant.data.novig

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Who asked Novig for order books (Tj's v0.89.34 file: 651,623 `/catalog/markets/{id}` reads in a day and a half, 2,597 refusals, and nothing in the file said which part of the app made them).
 * A caller wraps its read in `withContext(NovigReadTag("scan"))`; [NovigPublicClient.books] counts the requests it is about to make under that tag, and Diagnostics prints them
 * ([NovigReads.line]) so the next file names the caller to cut. A read with no tag counts as "other".
 */
class NovigReadTag(val tag: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<NovigReadTag>
}

/**
 * A read that only needs each outcome's best price (the Tracker pricing open bets, CNO's live check): [NovigPublicClient.books] may serve it from a market the live feed holds as its best bid
 * only ([NovigBook.partial], NOVIG_API.md §25). Any read without it wants complete books: a market held as its best bid is read the long way and moved to the full book channel for the next read.
 */
class NovigScreening : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<NovigScreening>
}

object NovigReads {
    private val counts = ConcurrentHashMap<String, LongAdder>()
    private val since = System.currentTimeMillis()

    fun add(tag: String, requests: Int) {
        if (requests > 0) counts.computeIfAbsent(tag) { LongAdder() }.add(requests.toLong())
    }

    /** Requests by tag, most first. */
    fun snapshot(): List<Pair<String, Long>> = counts.map { it.key to it.value.sum() }.sortedByDescending { it.second }

    fun reset() = counts.clear()

    /** "Novig book reads by caller since the app opened (31,204 in all): scan 21,000 · cno-live 6,200 …"; null before any. */
    fun line(): String? {
        val all = snapshot()
        if (all.isEmpty()) return null
        return "Novig book reads by caller since the app opened (${"%,d".format(all.sumOf { it.second })} requests): " + all.joinToString(" · ") { "${it.first} ${"%,d".format(it.second)}" }
    }
}
