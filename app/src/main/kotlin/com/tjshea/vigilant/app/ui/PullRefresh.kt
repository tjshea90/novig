package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pull to refresh whose arrow always goes away (Tj's screenshot, 2026-09-26: the arrow sat half
 * way down the CNO tab, "sometimes it is getting stuck").
 *
 * Material3 1.3's box parks its arrow at the pull threshold after a full pull and only hides it
 * when `isRefreshing` goes true and then false again. Vigilant always said false (the top bar
 * shows the work instead), so the arrow stayed. Here a pull holds `isRefreshing` true until the
 * work it started ([busy]) has come and gone ([holdWhileBusy]; otherwise only until it starts),
 * or for [START_WAIT_MS] when nothing started (a read CNO's pacing skipped), then lets go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VigilantPullToRefresh(
    busy: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    holdWhileBusy: Boolean = true,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit,
) {
    var pulled by remember { mutableStateOf(false) }
    val busyNow by rememberUpdatedState(busy)
    LaunchedEffect(pulled) {
        if (!pulled) return@LaunchedEffect
        val started = withTimeoutOrNull(START_WAIT_MS) { snapshotFlow { busyNow }.first { it } } != null
        if (started && holdWhileBusy) withTimeoutOrNull(MAX_HOLD_MS) { snapshotFlow { busyNow }.first { !it } }
        pulled = false
    }
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = modifier,
        state = state,
        content = content,
    )
}

/** How long a pull waits for its work to start before letting go. */
const val START_WAIT_MS = 1_200L

/** The longest a pull keeps the arrow up while the work runs. */
const val MAX_HOLD_MS = 20_000L
