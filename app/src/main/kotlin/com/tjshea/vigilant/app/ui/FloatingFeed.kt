package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.app.FloatingWidget
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
    val onRefresh: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onRecheck: () -> Unit = {},
    /** Tapped a bet: open it in Novig. */
    val onOpenBet: (MiniWindow.Item) -> Unit = {},
    /** Its ✓ button: placed, hide it. */
    val onPlaced: (MiniWindow.Item) -> Unit = {},
    /** Its ✕ button: not bet, but gone from the list all the same. */
    val onHidden: (MiniWindow.Item) -> Unit = {},
    val onUndoPlaced: (String) -> Unit = {},
    val onLoadBooks: (CnoRow) -> Unit = {},
    /** The top bar's switch: which scanner the widget lists (Both, Vigilant only, CNO only). */
    val onScanner: (com.tjshea.vigilant.data.scanner.ScannerMode) -> Unit = {},
    /** The top bar's start-time switch: only games starting within this many hours (0 = any time). */
    val onStartsWithin: (Int) -> Unit = {},
)

/** Height of one bet in the floating widget: a comfortable touch target. */
private val FLOAT_ROW = 40.dp

/**
 * The floating widget over Novig (Tj, 2026-09-26): the same bets as the picture-in-picture
 * window, but it takes touches, so
 *  - Up and Down are always at the bottom and scroll a page at a time (a finger scrolls too);
 *  - tapping a bet opens that bet in Novig's bet slip;
 *  - each bet's ✓ marks it placed and hides it for good, its ✕ removes it without betting it
 *    (Tj, 2026-09-27), both with Undo for a few seconds;
 *  - holding a CNO bet (or Books) shows every book's odds for it;
 *  - a green ✓ before a pick means several books agree it's +EV, and player bets show the team.
 * − shrinks it to a bubble, ✕ closes it (which stops CNO's reads). Moving and resizing are the
 * window's ([com.tjshea.vigilant.app.FloatingWidget]); [FloatingWindow] draws the frame and corner
 * handles they use.
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
        // The last bet marked placed (✓) or removed (✕), for Undo.
        var lastMarked by remember { mutableStateOf<Marked?>(null) }
        LaunchedEffect(lastMarked) {
            if (lastMarked != null) {
                delay(UNDO_MS)
                lastMarked = null
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
            // The top bar drags the widget (the window handles that): a grip in the middle says so.
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), CircleShape),
                )
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 4.dp)
                    .height(FloatingWidget.HEADER_DP.dp - 4.dp),
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
                ScannerSwitch(state.settings.scanner, onPick = actions.onScanner)
                StartsWithinSwitch(state.settings.startsWithinHours, onPick = actions.onStartsWithin)
                if (items.isNotEmpty()) {
                    Text("${items.size} +EV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Edge.colors.positive, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
                }
                HeaderButton(painterResource(R.drawable.ic_open), "Open Vigilant", actions.onOpenApp)
                HeaderButton(painterResource(R.drawable.ic_minimize), "Shrink to a bubble", actions.onMinimize)
                HeaderButton(null, "Close the widget", actions.onClose, close = true)
            }
            }

            // ---- The bets ---------------------------------------------------------------------
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    booksIndex != null -> {
                        val item = items[booksIndex]
                        LaunchedEffect(item.key) { item.cno?.let { actions.onLoadBooks(it.row) } }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            MiniBooks(item, state.booksAt(item.cno?.row?.key, now), "${booksIndex + 1}/${items.size}", MiniWindow.showsVigilant(state.settings), item.cno?.row?.let { state.priceReadAtMs(it, now) })
                        }
                    }
                    items.isEmpty() -> Text(
                        emptyText(state, floating = true, now = now),
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
                                        onClickLabel = "Open in ${AppBook.name}",
                                        onLongClickLabel = "Every book's odds",
                                        onLongClick = { if (item.cno != null) booksKey = item.key },
                                        onClick = { actions.onOpenBet(item) },
                                    )
                                    .padding(start = 8.dp),
                            ) {
                                MiniRow(item, showTag = !cnoOnly, height = FLOAT_ROW) {
                                    Box(Modifier.size(width = 34.dp, height = FLOAT_ROW), contentAlignment = Alignment.Center) {
                                        if (opening == item.key) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Box(
                                                Modifier.fillMaxSize()
                                                    .clickable(onClickLabel = "Mark placed") {
                                                        lastMarked = Marked(item, hidden = false)
                                                        actions.onPlaced(item)
                                                    }
                                                    .semantics { contentDescription = "I placed ${item.title}: hide it" },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    // ✕: gone from the list without betting it (Tj, 2026-09-27).
                                    Box(
                                        Modifier.size(width = 30.dp, height = FLOAT_ROW)
                                            .clickable(onClickLabel = "Remove") {
                                                lastMarked = Marked(item, hidden = true)
                                                actions.onHidden(item)
                                            }
                                            .semantics { contentDescription = "Remove ${item.title} from the list" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                lastMarked?.let { (p, hidden) ->
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                    ) {
                        Row(Modifier.padding(start = 10.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (hidden) "Removed: " else "Placed: ") + p.title,
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
                                    lastMarked = null
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
            val showsCno = MiniWindow.showsCno(state.settings)
            val labels = (if (cnoOnly) listOf("Refresh") else listOf("Scan", "Recheck")) +
                if (showsCno) listOf(if (booksIndex != null) "List" else "Books") else emptyList()
            BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
            // Labels only when they all fit ("Books" was cut to "Bo" on a narrow widget, Tj 2026-09-27).
            val labelled = barFits(labels, constraints.maxWidth)
            Row(
                Modifier.fillMaxSize().padding(horizontal = BAR_PAD),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (cnoOnly) {
                    // The same circling arrows as the CNO tab's Refresh.
                    BarButton("Refresh", painterResource(R.drawable.ic_scan), enabled = !state.cno.refreshing, labelled = labelled, onClick = actions.onRefresh)
                } else {
                    BarButton("Scan", painterResource(R.drawable.ic_scan), enabled = !status.scanning && !status.rechecking, labelled = labelled, onClick = actions.onScan)
                    BarButton("Recheck", painterResource(R.drawable.ic_recheck), enabled = !status.scanning && !status.rechecking && state.feed.isNotEmpty(), labelled = labelled, onClick = actions.onRecheck)
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
                if (showsCno) {
                    BarButton(
                        if (booksIndex != null) "List" else "Books",
                        painterResource(R.drawable.ic_books),
                        enabled = booksIndex != null || items.any { it.cno != null },
                        labelled = labelled,
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
            }
            }
        }
    }
}

/** A bet just marked placed (✓) or removed without betting it (✕, [hidden]), for Undo. */
private data class Marked(val item: MiniWindow.Item, val hidden: Boolean)

/**
 * The whole floating window: the widget inside a frame (Tj, 2026-09-26: "easily accessed corners
 * that I can pull out to enlarge or in to shrink", "an easier way … to drag and move"). The frame
 * is a dark border all around, a big thing to grab and move, with a handle drawn at each corner;
 * the window turns drags on them (and two-finger pinches anywhere) into moves and resizes. The
 * bubble has no frame.
 */
@Composable
fun FloatingWindow(
    state: UiState,
    actions: FloatingActions,
    minimized: Boolean = false,
    opening: String? = null,
    modifier: Modifier = Modifier,
) {
    if (minimized) {
        FloatingFeed(state, actions, modifier, minimized = true, opening = opening)
        return
    }
    val frame = FloatingWidget.FRAME_DP.dp
    val handle = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxSize()) {
        // The frame: dark enough to see over any app, so it reads as something to hold.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(frame + 12.dp)))
        FloatingFeed(state, actions, Modifier.fillMaxSize().padding(frame), opening = opening)
        // A handle at each corner, following the frame's rounded corner.
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Drag a corner to resize; drag the frame or the top bar to move; two fingers to pinch or spread" }) {
            val inset = (frame / 2).toPx()
            val r = 12.dp.toPx()
            val arm = 16.dp.toPx()
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val w = size.width
            val h = size.height
            // Top-left, drawn once and mirrored to the other three corners.
            val corner = Path().apply {
                moveTo(inset, inset + r + arm)
                lineTo(inset, inset + r)
                arcTo(Rect(inset, inset, inset + 2 * r, inset + 2 * r), 180f, 90f, false)
                lineTo(inset + r + arm, inset)
            }
            drawPath(corner, handle, style = stroke)
            scale(-1f, 1f, pivot = Offset(w / 2, h / 2)) { drawPath(corner, handle, style = stroke) }
            scale(1f, -1f, pivot = Offset(w / 2, h / 2)) { drawPath(corner, handle, style = stroke) }
            scale(-1f, -1f, pivot = Offset(w / 2, h / 2)) { drawPath(corner, handle, style = stroke) }
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

/**
 * Which scanner the widget lists, flipped right there (Tj, 2026-09-27: "an option to also use the
 * regular scan in addition to cno"; then "on the regular vigilant scanner, make it also have a
 * widget"): each tap moves to the next of CNO only → Both → Vigilant only. Named like the Scanner
 * setting it flips.
 */
@Composable
private fun ScannerSwitch(mode: com.tjshea.vigilant.data.scanner.ScannerMode, onPick: (com.tjshea.vigilant.data.scanner.ScannerMode) -> Unit) {
    val next = nextScanner(mode)
    val description = "Showing ${scannerWords(mode)}. Tap for ${scannerWords(next)}"
    val both = mode == com.tjshea.vigilant.data.scanner.ScannerMode.BOTH
    Box(
        Modifier.padding(start = 4.dp).height(32.dp).clickable(onClickLabel = description) { onPick(next) }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (mode) {
                com.tjshea.vigilant.data.scanner.ScannerMode.BOTH -> "Both"
                com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT -> "Vigilant"
                com.tjshea.vigilant.data.scanner.ScannerMode.CNO -> "CNO only"
            },
            Modifier
                .background(if (both) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                .border(1.dp, if (mode != com.tjshea.vigilant.data.scanner.ScannerMode.CNO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (both) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The start-time window, flipped right there (Tj, 2026-09-27: "select the time periods 12h 24h 48h and
 * anytime for the cno scanner and cno widget as well"): each tap moves to the next of Any time → 12h →
 * 24h → 48h. The same setting as the +EV and CNO tabs' "Starts within" row.
 */
@Composable
private fun StartsWithinSwitch(hours: Int, onPick: (Int) -> Unit) {
    val next = nextStartsWithin(hours)
    val description = "Showing games starting ${startsWithinWords(hours)}. Tap for ${startsWithinWords(next)}"
    val on = hours > 0
    Box(
        Modifier.padding(start = 4.dp).height(32.dp).clickable(onClickLabel = description) { onPick(next) }.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            startsWithinLabel(hours),
            Modifier
                .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The widget's start-time switch order: Any time → 12h → 24h → 48h → Any time. */
internal fun nextStartsWithin(hours: Int): Int {
    val choices = com.tjshea.vigilant.data.scanner.ScanSettings.STARTS_WITHIN_CHOICES
    return choices[(choices.indexOf(hours).coerceAtLeast(0) + 1) % choices.size]
}

private fun startsWithinWords(hours: Int) = if (hours <= 0) "at any time" else "within $hours hours"

/** The widget switch's order: CNO only → Both → Vigilant only → CNO only. */
internal fun nextScanner(mode: com.tjshea.vigilant.data.scanner.ScannerMode) = when (mode) {
    com.tjshea.vigilant.data.scanner.ScannerMode.CNO -> com.tjshea.vigilant.data.scanner.ScannerMode.BOTH
    com.tjshea.vigilant.data.scanner.ScannerMode.BOTH -> com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT
    com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT -> com.tjshea.vigilant.data.scanner.ScannerMode.CNO
}

private fun scannerWords(mode: com.tjshea.vigilant.data.scanner.ScannerMode) = when (mode) {
    com.tjshea.vigilant.data.scanner.ScannerMode.BOTH -> "CNO and Vigilant's scan"
    com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT -> "Vigilant's scan only"
    com.tjshea.vigilant.data.scanner.ScannerMode.CNO -> "CNO only"
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

/** The bottom bar's side padding. */
private val BAR_PAD = 6.dp

/** The label style of the bottom bar's small buttons (measured by [barFits] exactly as drawn). */
@Composable
private fun barLabelStyle() = LocalTextStyle.current.merge(TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))

/**
 * Whether the bottom bar's buttons fit [maxWidthPx] with their labels: each small button's icon,
 * label and padding, plus Up and Down. When not, they show icons only (labels stay for TalkBack).
 */
@Composable
private fun barFits(labels: List<String>, maxWidthPx: Int): Boolean {
    val measurer = rememberTextMeasurer()
    val style = barLabelStyle()
    val density = LocalDensity.current
    val text = labels.sumOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
    val fixed = with(density) { (labels.size * (6 + 16 + 3 + 6) + 2 * (12 + 26 + 12) + 2 * BAR_PAD.value.toInt()).dp.roundToPx() }
    return text + fixed <= maxWidthPx
}

@Composable
private fun BarButton(label: String, icon: Painter, enabled: Boolean = true, big: Boolean = false, labelled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Row(
        Modifier.height(36.dp)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = if (big || !labelled) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(if (big) 26.dp else if (labelled) 16.dp else 20.dp), tint = color)
        if (!big && labelled) Text(label, Modifier.padding(start = 3.dp), style = barLabelStyle(), color = color, maxLines = 1, softWrap = false)
    }
}

/** The widget shrunk to a bubble: how many bets, tap to open it again, drag to move (the window's). */
@Composable
private fun MinimizedBubble(count: Int, busy: Boolean, actions: FloatingActions) {
    Row(
        Modifier
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
