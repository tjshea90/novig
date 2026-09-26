package com.tjshea.vigilant.data.cno

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Who is looking at CNO's list right now: the CNO tab, the picture-in-picture window, the
 * floating widget. CNO's reads (the list, the books lane, the teams lane) run only while at least
 * one is (Tj, 2026-09-26: "make sure if I close the cno scanner or the app that nothing is
 * refreshing in the background").
 */
class CnoWatch {
    private val who = MutableStateFlow<Set<String>>(emptySet())

    /** Who is watching. */
    val watchers: StateFlow<Set<String>> = who.asStateFlow()

    fun set(name: String, on: Boolean) = who.update { if (on) it + name else it - name }

    val watched: Boolean get() = who.value.isNotEmpty()

    /**
     * Runs [work] whenever someone starts watching and cancels it the moment nobody is (a
     * request in flight is cancelled with it). [onActive] hears each change. Never returns.
     */
    suspend fun runWhileWatched(onActive: (Boolean) -> Unit = {}, work: suspend CoroutineScope.() -> Unit) {
        // Only on/off matters: the tab handing over to the widget doesn't restart anything.
        who.map { it.isNotEmpty() }.distinctUntilChanged().collectLatest { active ->
            onActive(active)
            if (active) coroutineScope { work() }
        }
    }
}
