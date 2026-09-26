package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjshea.vigilant.app.MiniWindow
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.scanner.Opportunity

/** Height of one bet in the mini window: two short lines. */
private val ROW = 30.dp

/**
 * What the mini window (picture-in-picture) shows: the scan's status and as many of the feed's
 * best bets as fit, best first. It takes no touches, so "Next" (one of its buttons) pages through
 * the rest. Small type on purpose: the window starts small, and a pinch or double-tap enlarges it,
 * which fits more rows.
 */
@Composable
fun MiniFeed(state: UiState, next: Int, modifier: Modifier = Modifier) {
    val now = rememberNow(15_000)
    val status = state.status
    val feed = state.feed
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(6.dp).background(
                    if (status.scanning || status.rechecking) MaterialTheme.colorScheme.primary else Edge.colors.positive,
                    CircleShape,
                ),
            )
            Text(
                miniStatus(state, now),
                Modifier.weight(1f).padding(start = 4.dp),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (feed.isNotEmpty()) {
                Text("${feed.size} +EV", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = Edge.colors.positive)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
            if (feed.isEmpty()) {
                Text(
                    emptyText(state),
                    Modifier.align(Alignment.Center),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val fit = (maxHeight / ROW).toInt().coerceAtLeast(1)
                val rows = MiniWindow.page(feed.size, fit, next)
                Column(verticalArrangement = Arrangement.Top) {
                    rows.forEach { i -> MiniRow(feed[i], now) }
                }
                if (rows.count() < feed.size) {
                    Text(
                        "${rows.first + 1}–${rows.last + 1}/${feed.size}",
                        Modifier.align(Alignment.BottomEnd),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniRow(o: Opportunity, now: Long) {
    val q = o.quote ?: return
    Row(Modifier.fillMaxWidth().height(ROW), verticalAlignment = Alignment.CenterVertically) {
        Text(
            Format.evPercent(q.evPercent).replace(Regex("(\\.\\d)\\d%$"), "$1%"),
            Modifier.width(40.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Edge.colors.positive,
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(o.selection, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${o.marketLabel} · ${o.eventName}",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Format.american(q.cost),
            Modifier.padding(start = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            // An old Novig price is shown in the warning color: recheck before betting it.
            color = if (o.priceIsOld(now)) Edge.colors.warning else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Novig prices 40/120", "Rechecking…", "Scanned 2m ago". */
fun miniStatus(state: UiState, now: Long): String {
    val s = state.status
    val p = s.progress
    return when {
        s.scanning && p != null && p.total > 0 -> "${p.step.substringAfterLast("· ").trim()} ${p.done}/${p.total}"
        s.scanning -> "Scanning…"
        s.rechecking -> "Rechecking…"
        s.scannedAtMs == null -> "Vigilant"
        else -> "Scanned ${Format.age(s.scannedAtMs, now)}"
    }
}

private fun emptyText(state: UiState): String = when {
    state.status.scanning -> "Scanning… bets show here as they're found"
    state.result == null -> "Tap the window, then Scan"
    else -> "No +EV right now"
}
