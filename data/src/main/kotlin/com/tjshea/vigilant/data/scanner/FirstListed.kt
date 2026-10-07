package com.tjshea.vigilant.data.scanner

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * When each bet was FIRST listed (Tj, 2026-10-07, proposal 2 of the v0.70.1 analysis: "remember a bet's first-listed time"). The trap guard looks at the clock NOW
 * ([TrapGuard.early]); the study found that bets already listed more than 6 h before the start which later enter the window close at about +0.07% against +1.29% for ones
 * first listed inside it. So each listed bet (a CNO row's key, a Vigilant opportunity's key) is written down the first time it is seen, kept in files/first_listed.json so
 * a restart doesn't make old listings look fresh, and the guard ([TrapGuard.listedEarly]) skips the auto-bet and alerts on a listing first seen too far before the start.
 *
 * A bet first seen after this existed counts from when the app first saw it: listings that were up before the app started looking aren't known to be old (the guard
 * errs toward letting them through). The entry is kept until its game is over.
 */
class FirstListed(file: File) {

    @Serializable
    data class Entry(val key: String, val startsTs: Long, val firstMs: Long)

    private val store = JsonFileStore(file, ListSerializer(Entry.serializer()), { emptyList() })
    private val seen = ConcurrentHashMap<String, Long>()

    @Volatile private var loaded = false

    /** The persisted first-seen times, read once (before the first [note]; [snapshot] calls it too). */
    suspend fun load() {
        if (loaded) return
        for (e in store.read()) seen.putIfAbsent(e.key, e.firstMs)
        loaded = true
    }

    /** Every key's first-seen time right now (a copy: the cycle judges one fixed picture). */
    suspend fun snapshot(): Map<String, Long> {
        load()
        return HashMap(seen)
    }

    /** The first-seen time of [key], null when never seen (or not loaded yet). */
    fun at(key: String): Long? = seen[key]

    /**
     * Writes down each of [items] (key and the game's start, 0 = unknown) not seen before as first seen at [now]; the games that ended more than [KEEP_MS] ago are dropped.
     * Writes the file only when something new was added or dropped. Returns how many were new.
     */
    suspend fun note(items: List<Pair<String, Long>>, now: Long): Int {
        load()
        val fresh = ArrayList<Entry>()
        for ((key, startsTs) in items) {
            if (key.isBlank()) continue
            if (seen.putIfAbsent(key, now) == null) fresh += Entry(key, startsTs, now)
        }
        val dropBefore = now - KEEP_MS
        var dropped = false
        if (fresh.isNotEmpty() || now - lastPrune >= PRUNE_EVERY_MS) {
            lastPrune = now
            store.update { list ->
                val all = list + fresh
                val kept = all.filter { (if (it.startsTs > 0L) it.startsTs else it.firstMs + UNKNOWN_START_KEEP_MS) >= dropBefore }
                if (kept.size != all.size) dropped = true
                if (dropped || fresh.isNotEmpty()) {
                    seen.keys.retainAll(kept.mapTo(HashSet()) { it.key })
                    kept
                } else list
            }
        }
        return fresh.size
    }

    @Volatile private var lastPrune = 0L

    companion object {
        /** A game's entry is kept this long after its start. */
        const val KEEP_MS = 12 * 60 * 60_000L

        /** An entry whose game start isn't known is kept this long after it was first seen. */
        const val UNKNOWN_START_KEEP_MS = 3 * 24 * 60 * 60_000L

        /** The file is pruned at least this often (when nothing new arrives). */
        const val PRUNE_EVERY_MS = 60 * 60_000L
    }
}
