package com.tjshea.vigilant.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects [this] like `collectAsStateWithLifecycle`, but Compose compares a new value with the old by REFERENCE, not by `equals`. The app's whole UI state is one data class holding scan results, books and lists of
 * thousands of items; Compose's default structural comparison ran its generated `equals` on the MAIN thread every time the state changed, and a re-scan that priced the same lines compared every opportunity and
 * every reference event (Tj's 2026-10-14 file: "not responding ... Waited 10000ms for MotionEvent", main thread in `UiState.equals`). The flow itself already drops equal values off the main thread; every value that
 * reaches here is a real change and recomposes, as it did before.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateByReference(): State<T> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val flow = this
    val state = remember(flow) { mutableStateOf(flow.value, referentialEqualityPolicy()) }
    LaunchedEffect(flow, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { state.value = it } }
    }
    return state
}
