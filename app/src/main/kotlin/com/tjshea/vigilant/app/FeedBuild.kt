package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.scanner.ScanResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The +EV feed is built off the main thread, always (Tj, 2026-10-03, with the v0.56.1 Diagnostics: "it got so laggy I almost couldn't use it and I
 * pressed pause and even that took a while to register"; Android's own record: "not responding", the main thread in [UiState.feedOf]).
 *
 * Building it is a filter and a sort over every priced side, then a check of each row against every bet Tj already has
 * ([com.tjshea.vigilant.data.tracker.PlacedIndex.visible]). It used to run inside `_state.update { ... }` on the main thread, which also re-runs its
 * lambda whenever another thread wins the swap: each scanner switch, the Pause button, a scan's end, a recheck and every ✓ mark rebuilt the whole feed
 * there, and Tj's eight scanner switches in forty seconds queued eight rebuilds ahead of his Pause.
 *
 * So the main thread only ever swaps the answer in: the feed is built on [build] from a snapshot of the state, and published only if what it was built
 * from (the result, the settings, the marks) is still what the state holds; else it is built again from what is current. Each function returns once its
 * feed is on screen.
 */

/**
 * Shows [r] (a scan's or a re-pricing's result) with its feed, and whatever else [also] changes in the same swap (cheap copies only: it runs on the
 * caller's thread, maybe more than once).
 */
internal suspend fun MutableStateFlow<UiState>.publishResult(
    r: ScanResult?,
    build: CoroutineDispatcher = Dispatchers.Default,
    also: (UiState) -> UiState = { it },
) {
    while (true) {
        val before = value
        val feed = withContext(build) { before.feedOf(r) }
        var shown = false
        update { s ->
            shown = s.settings == before.settings && s.placedIndex === before.placedIndex
            if (shown) also(s.copy(result = r, feed = feed)) else s
        }
        if (shown) return
    }
}

/** The feed of the state's own result, built again (the settings changed). The result it was built for must still be the one shown. */
internal suspend fun MutableStateFlow<UiState>.refeed(build: CoroutineDispatcher = Dispatchers.Default) {
    while (true) {
        val before = value
        val feed = withContext(build) { before.feedOf(before.result) }
        var shown = false
        update { s ->
            shown = s.result === before.result && s.settings == before.settings && s.placedIndex === before.placedIndex
            if (shown) s.copy(feed = feed) else s
        }
        if (shown) return
    }
}

/**
 * [change] to the state (new bets from the Tracker, new ✓ marks: cheap copies only), with the placed index rebuilt from it and the feed with that,
 * both built off the main thread and swapped in together with the change, so a bet Tj just placed never shows in the list for a moment first.
 */
internal suspend fun MutableStateFlow<UiState>.reindex(build: CoroutineDispatcher = Dispatchers.Default, change: (UiState) -> UiState) {
    while (true) {
        val before = value
        val built = withContext(build) { change(before).indexed() }
        var shown = false
        update { s ->
            shown = s.placed === before.placed && s.bets === before.bets && s.result === before.result && s.settings == before.settings
            if (shown) change(s).copy(placedIndex = built.placedIndex, feed = built.feed) else s
        }
        if (shown) return
    }
}
