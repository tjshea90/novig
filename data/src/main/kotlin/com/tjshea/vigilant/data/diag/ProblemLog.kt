package com.tjshea.vigilant.data.diag

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable

/** One kind of problem seen: where ([area]), what it said, when first and last, and how many times in a row. */
@Serializable
data class Problem(val area: String, val message: String, val firstAtMs: Long, val lastAtMs: Long = firstAtMs, val count: Int = 1)

@Serializable
data class ProblemBook(val items: List<Problem> = emptyList())

/**
 * The problems Vigilant ran into, kept across restarts (files/problems.json), for Diagnostics' "Recent problems" (Tj, 2026-09-30: "make the
 * diagnostics section in settings as smart as possible so that when I output it to Claude, Claude can run deep analysis on the app and know
 * what is working or broken"): a scan's errors, a fair-odds source that failed, CrazyNinjaOdds' errors, the background scan's, and every
 * failure message shown on screen. The same problem again within [MERGE_MS] adds to its count instead of a new line; the newest [KEEP] stay.
 * Never a key: messages are the app's own words, and anything shaped like a key is masked ([clean]).
 */
class ProblemLog(
    private val store: JsonFileStore<ProblemBook>,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Told of every problem as it is added (the event log's feed: Tj, 2026-10-02): the area and the masked text. */
    private val onAdd: ((String, String) -> Unit)? = null,
) {

    /** [atMs]: when it happened, when that was before now (a crash saved as the app went down). [maxLength]: a crash's stack keeps more. */
    suspend fun add(area: String, message: String, atMs: Long? = null, maxLength: Int = MAX_LENGTH) {
        val text = clean(message).take(maxLength)
        if (text.isBlank()) return
        runCatching { onAdd?.invoke(area, text) }
        val now = atMs ?: clock()
        store.update { book ->
            val items = book.items.toMutableList()
            val i = items.indexOfLast { it.area == area && it.message == text }
            if (i >= 0 && now - items[i].lastAtMs <= MERGE_MS) {
                val p = items.removeAt(i)
                items += p.copy(lastAtMs = now, count = p.count + 1)
            } else {
                items += Problem(area, text, now)
            }
            ProblemBook(items.takeLast(KEEP))
        }
    }

    /** Forgets every problem (Settings › Diagnostics › Reset). */
    suspend fun clear() {
        store.update { ProblemBook() }
    }

    /** Newest first. */
    suspend fun recent(): List<Problem> = store.read().items.asReversed().sortedByDescending { it.lastAtMs }

    /** A crash's stack: kept longer than other problems. */
    val crashLength: Int get() = CRASH_LENGTH

    companion object {
        const val KEEP = 60
        const val MERGE_MS = 6 * 60 * 60_000L
        const val MAX_LENGTH = 300
        const val CRASH_LENGTH = 1_500

        /** Long runs of letters and digits (a key, a token, a signature) masked to their last four; the app's words stay, and so do the line breaks (a stack). */
        fun mask(message: String): String = message.replace(Regex("[A-Za-z0-9_\\-]{24,}")) { "…" + it.value.takeLast(4) }

        /** [mask]ed, on one line. */
        fun clean(message: String): String = mask(message).replace(Regex("\\s+"), " ").trim()
    }
}
