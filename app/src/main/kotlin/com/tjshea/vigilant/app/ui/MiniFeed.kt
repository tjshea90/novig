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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.tjshea.vigilant.app.MiniWindow
import com.tjshea.vigilant.app.UiState
import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoBooksState

/** Height of one bet in the mini window: two short lines. */
private val ROW = 30.dp

/**
 * What the mini window (picture-in-picture) shows: the scan's status and as many of the best bets
 * as fit, best first: Vigilant's, CrazyNinjaOdds' (tagged CNO), or both (Settings). It takes no
 * touches, so "Next" (one of its buttons) pages through the rest. Small type on purpose: the
 * window starts small, and a pinch or double-tap enlarges it, which fits more rows.
 *
 * [booksKey] (CNO only, the Books button): instead of the list, that bet with every book's odds
 * and Vigilant's check of it (the top bet if it's gone); Next moves to the next bet. Its books
 * are asked for ([onLoadBooks]) as soon as it shows.
 */
@Composable
fun MiniFeed(
    state: UiState,
    next: Int,
    modifier: Modifier = Modifier,
    booksKey: String? = null,
    onLoadBooks: (com.tjshea.vigilant.data.cno.CnoRow) -> Unit = {},
) {
    val now = rememberNow(15_000)
    val status = state.status
    val items = MiniWindow.items(state, now)
    val busy = status.scanning || status.rechecking || (MiniWindow.showsCno(state.settings) && state.cno.refreshing)
    // The window draws this straight into the theme, with nothing behind it to set a text color: a
    // Surface does, or text without its own color comes out black on the dark window (Tj's
    // screenshot, 2026-09-26: every pick's name was unreadable).
    androidx.compose.material3.Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(6.dp).background(
                        if (busy) MaterialTheme.colorScheme.primary else Edge.colors.positive,
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
                if (items.isNotEmpty()) {
                    Text("${items.size} +EV", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = Edge.colors.positive)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
                if (booksKey != null && items.isNotEmpty()) {
                    val i = items.indexOfFirst { it.key == booksKey }.coerceAtLeast(0)
                    val item = items[i]
                    androidx.compose.runtime.LaunchedEffect(item.key) { item.cno?.let { onLoadBooks(it.row) } }
                    MiniBooks(item, state.books[item.cno?.row?.key], "${i + 1}/${items.size}", MiniWindow.showsVigilant(state.settings))
                } else if (items.isEmpty()) {
                    Text(
                        emptyText(state),
                        Modifier.align(Alignment.Center),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val fit = (maxHeight / ROW).toInt().coerceAtLeast(1)
                    val rows = MiniWindow.page(items.size, fit, next)
                    Column(verticalArrangement = Arrangement.Top) {
                        // The CNO tag only matters when both lists are mixed.
                        val tag = MiniWindow.showsVigilant(state.settings)
                        rows.forEach { i -> MiniRow(items[i], tag) }
                    }
                    if (rows.count() < items.size) {
                        Text(
                            "${rows.first + 1}–${rows.last + 1}/${items.size}",
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
}


/** One CNO bet with every book's odds, sized for the mini window. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MiniBooks(item: MiniWindow.Item, books: CnoBooksState?, position: String, tag: Boolean) {
    val pick = item.cno
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        MiniRow(item, tag)
        val view = books?.view
        val check = if (pick != null && view != null) CnoBooks.check(view, pick.row, pick.live) else null
        val judged = pick?.row?.book?.let { CnoBooks.codeFor(it) } ?: CnoBooks.NOVIG
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    pick == null -> "Books are for CNO bets"
                    check != null -> verdictLabel(check).first + (check.ev?.let { " · ${Format.evPercentShort(it)}" } ?: "")
                    books?.loading == true || books == null -> "Reading books…"
                    else -> books.error ?: "No books"
                },
                Modifier.weight(1f),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = check?.let { verdictLabel(it).second } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(position, fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (view != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                view.prices.forEach { p ->
                    val novig = p.code == judged
                    Text(
                        "${p.code} ${p.odds?.let { MiniWindow.american(it) } ?: "—"}/${p.otherOdds?.let { MiniWindow.american(it) } ?: "—"}",
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (novig) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            novig -> MaterialTheme.colorScheme.primary
                            p.twoSided && CnoBooks.usableForFair(p.code, judged) -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniRow(item: MiniWindow.Item, showTag: Boolean = true) {
    val tag = MaterialTheme.colorScheme.tertiary
    Row(Modifier.fillMaxWidth().height(ROW), verticalAlignment = Alignment.CenterVertically) {
        Text(
            Format.evPercentShort(item.ev),
            Modifier.width(40.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Edge.colors.positive,
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            // The pick itself, what Tj taps in Novig: full contrast, always, and its side and line
            // ("Under 69.5") never cut off; only the name shortens in a narrow window.
            val (fullName, line) = MiniWindow.splitPick(item.title)
            val pickStyle = TextStyle(fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Full name if it fits beside the line, else "J. Jefferson" / "Cowboys", else cut with "…".
            val lineWidth = line?.let { measurer.measure((if (fullName.isNotEmpty()) " " else "") + it, pickStyle).size.width } ?: 0
            val name = if (fullName.isEmpty() || measurer.measure(fullName, pickStyle).size.width + lineWidth <= constraints.maxWidth) {
                fullName
            } else {
                MiniWindow.shortName(fullName, player = item.subtitle.startsWith("Player"))
            }
            Row(
                Modifier.clearAndSetSemantics { text = AnnotatedString(item.title) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (name.isNotEmpty()) {
                    Text(
                        name,
                        Modifier.weight(1f, fill = false),
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (line != null) {
                    Text(
                        (if (name.isNotEmpty()) " " else "") + line,
                        fontSize = 12.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            }
            Text(
                buildAnnotatedString {
                    if (item.fromCno && showTag) {
                        withStyle(SpanStyle(color = tag, fontWeight = FontWeight.Bold)) { append("CNO ") }
                    }
                    append(item.subtitle)
                },
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.padding(start = 4.dp), horizontalAlignment = Alignment.End) {
            Text(
                item.price,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                // An old price is shown in the warning color: recheck or refresh before betting it.
                color = if (item.old) Edge.colors.warning else MaterialTheme.colorScheme.onSurface,
            )
            item.available?.let {
                Text(it, fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** "Novig prices 40/120", "Rechecking…", "Scanned 2m ago", then CNO's part: "CNO 40s". */
fun miniStatus(state: UiState, now: Long): String {
    val s = state.status
    val p = s.progress
    val showsVigilant = MiniWindow.showsVigilant(state.settings)
    val ours = when {
        !showsVigilant -> null
        s.scanning && p != null && p.total > 0 -> "${p.step.substringAfterLast("· ").trim()} ${p.done}/${p.total}"
        s.scanning -> "Scanning…"
        s.rechecking -> "Rechecking…"
        s.scannedAtMs == null -> if (MiniWindow.showsCno(state.settings) && state.cno.snapshot != null) null else "Vigilant"
        else -> "Scanned ${Format.age(s.scannedAtMs, now)}"
    }
    val cno = state.cno
    val snap = cno.snapshot
    val theirs = when {
        !MiniWindow.showsCno(state.settings) -> null
        cno.refreshing -> "CNO reading…"
        cno.error != null && showsVigilant -> "CNO error"
        cno.error != null -> cno.error.orEmpty()
        snap != null -> "CNO " + Format.age(snap.dataAtMs, now).removeSuffix(" ago")
        showsVigilant -> null
        else -> "CrazyNinjaOdds"
    }
    return listOfNotNull(ours, theirs).joinToString(" · ").ifEmpty { "Vigilant" }
}

private fun emptyText(state: UiState): String {
    val cno = state.cno
    return when {
        state.status.scanning && MiniWindow.showsVigilant(state.settings) -> "Scanning… bets show here as they're found"
        !MiniWindow.showsVigilant(state.settings) -> when {
            cno.refreshing -> "Reading CrazyNinjaOdds…"
            cno.snapshot != null -> "No +EV on CrazyNinjaOdds right now"
            cno.error != null -> cno.error.orEmpty()
            else -> "Tap the window, then Refresh"
        }
        state.result == null && cno.snapshot == null -> "Tap the window, then Scan"
        else -> "No +EV right now"
    }
}
