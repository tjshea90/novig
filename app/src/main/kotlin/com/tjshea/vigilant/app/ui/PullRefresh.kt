package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    /** False: no pull at all, the content is drawn as it is (a screen whose pages only some refresh). */
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) { Box(modifier, content = content); return }
    var pulled by remember { mutableStateOf(false) }
    val busyNow by rememberUpdatedState(busy)
    LaunchedEffect(pulled) {
        if (!pulled) return@LaunchedEffect
        val started = withTimeoutOrNull(START_WAIT_MS) { snapshotFlow { busyNow }.first { it } } != null
        if (started && holdWhileBusy) withTimeoutOrNull(MAX_HOLD_MS) { snapshotFlow { busyNow }.first { !it } }
        pulled = false
    }
    val step = LocalRefreshStep.current
    Box(modifier) {
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            pulled = true
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
        state = state,
        content = content,
    )
    if (step.isNotEmpty()) RefreshStatusBar(step, Modifier.align(Alignment.TopCenter))
    }
}

/** What the pull in flight is reading ("" = none), provided once at the top of the app from [com.tjshea.vigilant.app.CloudLink.Ui.refreshStep]. */
val LocalRefreshStep = compositionLocalOf { "" }

/** A thin strip with a moving line and the words of the read in flight; gone the moment the pull ends. */
@Composable
fun RefreshStatusBar(step: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth().testTag("refreshStatusBar")) {
        Column {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            Text(step, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}

/** How long a pull waits for its work to start before letting go. */
const val START_WAIT_MS = 1_200L

/** The longest the arrow stays up: it flashes and goes (Tj, 2026-10-21: "show the refresh icon then quickly it disappears and a status bar telling me what is loading"); the work is shown by [LocalRefreshStep]'s bar. */
const val MAX_HOLD_MS = 1_000L
