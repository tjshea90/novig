package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjshea.vigilant.app.MiniWindow
import com.tjshea.vigilant.app.R
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cno.CnoRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the floating widget's buttons and rows do; [com.tjshea.vigilant.app.MainActivity] wires them. */
class FloatingActions(
    val onClose: () -> Unit = {},
    val onMinimize: () -> Unit = {},
    val onExpand: () -> Unit = {},
    /** Back to the full app. */
    val onOpenApp: () -> Unit = {},
    /** The header was dragged by this many pixels (moves the window). */
    val onDrag: (Float, Float) -> Unit = { _, _ -> },
    /** A move or resize ended (the window saves where it is). */
    val onDragEnd: () -> Unit = {},
    /** The corner grip was dragged by this many pixels (resizes the window). */
    val onResize: (Float, Float) -> Unit = { _, _ -> },
    val onRefresh: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onRecheck: () -> Unit = {},
    /** Tapped a bet: open it in Novig. */
    val onOpenBet: (MiniWindow.Item) -> Unit = {},
    /** Its ✓ button: placed, hide it. */
    val onPlaced: (MiniWindow.Item) -> Unit = {},
    val onUndoPlaced: (String) -> Unit = {},
    val onLoadBooks: (CnoRow) -> Unit = {},
)

/** Height of one bet in the floating widget: a comfortable touch target. */
private val FLOAT_ROW = 40.dp

/**
 * The floating widget over Novig (Tj, 2026-09-26): the same bets as the picture-in-picture
 * window, but it takes touches, so
 *  - Up and Down are always at the bottom and scroll a page at a time (a finger scrolls too);
 *  - tapping a bet opens that bet in Novig's bet slip;
 *  - each bet's ✓ marks it placed and hides it for good (Undo for a few seconds);
 *  - holding a CNO bet (or Books) shows every book's odds for it;
 *  - a green ✓ before a pick means several books agree it's +EV, and player bets show the team.
 * The header drags the window, the corner grip resizes it, − shrinks it to a bubble, ✕ closes it
 * (which stops CNO's reads).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingFeed(
    state: UiState,
    actions: FloatingActions,
    modifier: Modifier = Modifier,
    minimized: Boolean = false,
    /** The bet whose Novig link is being looked up (a spinner instead of its ✓). */
    opening: String? = null,
) {
    val now = rememberNow(5_000)
    val items = MiniWindow.items(state, now)
    val status = state.status
    val busy = status.scanning || status.rechecking || (MiniWindow.showsCno(state.settings) && state.cno.refreshing)
    val shape = RoundedCornerShape(if (minimized) 20.dp else 12.dp)
    Surface(
        modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
    ) {
        if (minimized) {
            MinimizedBubble(items.size, busy, actions)
            return@Surface
        }
        var booksKey by remember { mutableStateOf<String?>(null) }
        var lastPlaced by remember { mutableStateOf<MiniWindow.Item?>(null) }
        LaunchedEffect(lastPlaced) {
            if (lastPlaced != null) {
                delay(UNDO_MS)
                lastPlaced = null
            }
        }
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val booksIndex = booksKey?.let { k -> items.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }
        // A bet that left the list (refreshed away, or placed) takes the Books view with it.
        if (booksKey != null && booksIndex == null) booksKey = null
        val cnoOnly = !MiniWindow.showsVigilant(state.settings)

        Column(Modifier.fillMaxSize()) {
            // ---- Header: drag to move -----------------------------------------------------
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = actions.onDragEnd) { change, drag ->
                            change.consume()
                            actions.onDrag(drag.x, drag.y)
                        }
                    }
                    .padding(start = 8.dp, end = 2.dp)
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(if (busy) MaterialTheme.colorScheme.primary else Edge.colors.positive, CircleShape))
                Text(
                    miniStatus(state, now),
                    Modifier.weight(1f).padding(start = 6.dp),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (items.isNotEmpty()) {
                    Text("${items.size} +EV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Edge.colors.positive, modifier = Modifier.padding(end = 2.dp))
                }
                HeaderButton(painterResource(R.drawable.ic_open), "Open Vigilant", actions.onOpenApp)
                HeaderButton(painterResource(R.drawable.ic_minimize), "Shrink to a bubble", actions.onMinimize)
                HeaderButton(null, "Close the widget", actions.onClose, close = true)
            }

            // ---- The bets ---------------------------------------------------------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    booksIndex != null -> {
                        val item = items[booksIndex]
                        LaunchedEffect(item.key) { item.cno?.let { actions.onLoadBooks(it.row) } }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            MiniBooks(item, state.books[item.cno?.row?.key], "${booksIndex + 1}/${items.size}", MiniWindow.showsVigilant(state.settings))
                        }
                    }
                    items.isEmpty() -> Text(
                        emptyText(state, floating = true),
                        Modifier.align(Alignment.Center).padding(12.dp),
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    else -> LazyColumn(Modifier.fillMaxSize(), state = list) {
                        itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                            if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Box(
                                Modifier.fillMaxWidth()
                                    .combinedClickable(
                                        onClickLabel = "Open in Novig",
                                        onLongClickLabel = "Every book's odds",
                                        onLongClick = { if (item.cno != null) booksKey = item.key },
                                        onClick = { actions.onOpenBet(item) },
                                    )
                                    .padding(start = 8.dp),
                            ) {
                                MiniRow(item, showTag = !cnoOnly, height = FLOAT_ROW) {
                                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        if (opening == item.key) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Box(
                                                Modifier.size(36.dp)
                                                    .clickable(onClickLabel = "Mark placed") {
                                                        lastPlaced = item
                                                        actions.onPlaced(item)
                                                    }
                                                    .semantics { contentDescription = "I placed ${item.title}: hide it" },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                lastPlaced?.let { p ->
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                    ) {
                        Row(Modifier.padding(start = 10.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Placed: ${p.title}",
                                Modifier.weight(1f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "UNDO",
                                Modifier.clickable {
                                    actions.onUndoPlaced(p.key)
                                    lastPlaced = null
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inversePrimary,
                            )
                        }
                    }
                }
            }

            // ---- Always-there buttons -------------------------------------------------------
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            val canUp by remember(booksIndex) { derivedStateOf { if (booksIndex != null) booksIndex > 0 else list.canScrollBackward } }
            val canDown by remember(booksIndex, items.size) { derivedStateOf { if (booksIndex != null) booksIndex < items.lastIndex else list.canScrollForward } }
            Row(
                Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (cnoOnly || !MiniWindow.showsVigilant(state.settings)) {
                    BarButton("Refresh", painterResource(R.drawable.ic_recheck), enabled = !state.cno.refreshing, onClick = actions.onRefresh)
                } else {
                    BarButton("Scan", painterResource(R.drawable.ic_scan), enabled = !status.scanning && !status.rechecking, onClick = actions.onScan)
                    BarButton("Recheck", painterResource(R.drawable.ic_recheck), enabled = !status.scanning && !status.rechecking && state.feed.isNotEmpty(), onClick = actions.onRecheck)
                }
                BarButton("Up", painterResource(R.drawable.ic_up), enabled = canUp, big = true) {
                    if (booksIndex != null) {
                        booksKey = items[(booksIndex - 1).coerceAtLeast(0)].key
                    } else {
                        scope.launch { list.animateScrollToItem(pageTarget(list, up = true, size = items.size)) }
                    }
                }
                BarButton("Down", painterResource(R.drawable.ic_down), enabled = canDown, big = true) {
                    if (booksIndex != null) {
                        booksKey = items[(booksIndex + 1).coerceAtMost(items.lastIndex)].key
                    } else {
                        scope.launch { list.animateScrollToItem(pageTarget(list, up = false, size = items.size)) }
                    }
                }
                if (MiniWindow.showsCno(state.settings)) {
                    BarButton(
                        if (booksIndex != null) "List" else "Books",
                        painterResource(R.drawable.ic_books),
                        enabled = booksIndex != null || items.any { it.cno != null },
                    ) {
                        booksKey = if (booksIndex != null) {
                            null
                        } else {
                            // The first bet showing (that CNO listed).
                            val first = list.firstVisibleItemIndex
                            (items.drop(first) + items.take(first)).firstOrNull { it.cno != null }?.key
                        }
                    }
                }
                ResizeGrip(actions)
            }
        }
    }
}

/** Where Up / Down scroll to: a page (the bets fully showing) at a time, one row overlapping. */
internal fun pageTarget(list: androidx.compose.foundation.lazy.LazyListState, up: Boolean, size: Int): Int {
    val info = list.layoutInfo
    val full = info.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset }
    val step = (full - 1).coerceAtLeast(1)
    val first = list.firstVisibleItemIndex
    return if (up) (first - step).coerceAtLeast(0) else (first + step).coerceAtMost((size - 1).coerceAtLeast(0))
}

@Composable
private fun HeaderButton(icon: Painter?, description: String, onClick: () -> Unit, close: Boolean = false) {
    Box(
        Modifier.size(32.dp).clickable(onClickLabel = description, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (close) Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarButton(label: String, icon: Painter, enabled: Boolean = true, big: Boolean = false, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        Modifier.height(36.dp)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = if (big) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(if (big) 26.dp else 16.dp), tint = color)
        if (!big) Text(label, Modifier.padding(start = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** The bottom-right corner: drag it to resize the window. */
@Composable
private fun ResizeGrip(actions: FloatingActions) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(28.dp)
            .semantics { contentDescription = "Drag to resize" }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = actions.onDragEnd) { change, drag ->
                    change.consume()
                    actions.onResize(drag.x, drag.y)
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Canvas(Modifier.size(14.dp).padding(2.dp)) {
            val w = size.width
            for (i in 1..3) {
                val d = w * i / 3f
                drawLine(color, Offset(w - d, w), Offset(w, w - d), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

/** The widget shrunk to a bubble: how many bets, tap to open it again, drag to move. */
@Composable
private fun MinimizedBubble(count: Int, busy: Boolean, actions: FloatingActions) {
    Row(
        Modifier
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = actions.onDragEnd) { change, drag ->
                    change.consume()
                    actions.onDrag(drag.x, drag.y)
                }
            }
            .clickable(onClickLabel = "Open the widget", onClick = actions.onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        Text(
            if (count > 0) "$count +EV" else "CNO",
            Modifier.padding(start = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) Edge.colors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How long Undo stays up after marking a bet placed. */
const val UNDO_MS = 6_000L
