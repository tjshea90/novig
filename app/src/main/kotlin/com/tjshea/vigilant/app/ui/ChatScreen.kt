package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tjshea.vigilant.data.cloud.ChatState
import com.tjshea.vigilant.data.cloud.CloudChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The chat with Claude (CR, Tj 2026-10-19: "work like a real chat"), rebuilt from scratch. A message shows the moment Send is tapped and says where it is (Sending, Sent, Seen, Answered, or Not sent
 * with Retry); the newest stays at the bottom, also when the keyboard opens; a reply quotes the message it answers when that is not right above it; the line at the top says whether Claude is there.
 * Only one Claude account (acct-5d1e2d, mirage) answers. This draws state and calls back; the send queue and the reading are [com.tjshea.vigilant.data.cloud.ChatSync]'s.
 */
@Composable
fun ChatPage(
    chat: ChatState,
    seat: CloudChat.Seat?,
    problem: String?,
    /** The old app's chat (before 2026-10-19), shown read only on request. */
    old: List<CloudChat.OldMsg>,
    onSend: suspend (String, List<android.net.Uri>) -> String?,
    onOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onRead: () -> Unit = {},
    onRetry: (String) -> Unit = {},
    onDiscard: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onClear: () -> Unit = {},
    onLoadEarlier: () -> Unit = {},
    /** The phone's own copy of attachment n of a message of Tj's (a thumbnail), or null. */
    localFile: (String, Int, String) -> File? = { _, _, _ -> null },
) {
    // Open = this chat is on screen and the app is in front: then it is read every few seconds and Claude's words are read at once; in the background a reply is a notification.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val open by rememberUpdatedState(onOpen)
    DisposableEffect(lifecycle) {
        val watch = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> open(true)
                Lifecycle.Event.ON_STOP -> open(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(watch)
        onDispose { lifecycle.removeObserver(watch); open(false) }
    }
    val now = rememberNow(10_000)
    val rows = remember(chat.msgs, chat.seen, chat.outbox) { CloudChat.thread(chat.msgs, chat.seen.toSet(), chat.outbox) }
    val presence = remember(seat, rows, now) { CloudChat.presence(seat, rows, now) }
    val newestClaude = rows.lastOrNull { !it.fromTj }?.id
    LaunchedEffect(newestClaude) { if (newestClaude != null) onRead() }

    var showEarlier by rememberSaveable { mutableStateOf(false) }
    var showOld by rememberSaveable { mutableStateOf(false) }
    val oldRows = remember(old) { CloudChat.oldRows(old) }
    val start = remember(chat.breaks, chat.hiddenBefore) { chat.breaks.filter { it > chat.hiddenBefore }.maxOrNull() ?: chat.hiddenBefore }
    val kept = remember(rows, chat.hiddenBefore) { rows.filter { it.at >= chat.hiddenBefore || it.delivery in OUTGOING } }
    val earlier = remember(kept, start) { kept.count { it.at < start && it.delivery !in OUTGOING } }
    val shown = remember(kept, start, showEarlier) { if (showEarlier) kept else kept.filter { it.at >= start || it.delivery in OUTGOING } }
    val moreOnGitHub = chat.oldestDay.isNotBlank() && chat.fromDay.isNotBlank() && chat.oldestDay < chat.fromDay
    val items = remember(shown, showOld, oldRows, earlier, showEarlier, moreOnGitHub, presence.replying) {
        buildItems(shown, if (showOld) oldRows else emptyList(), if (showEarlier) 0 else earlier, moreOnGitHub && (showEarlier || earlier == 0), presence.replying)
    }

    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    // The newest message stays in view when it arrives, unless Tj has scrolled up to read (then the arrow takes him down).
    val newestKey = items.firstOrNull()?.key
    LaunchedEffect(newestKey) { if (list.firstVisibleItemIndex <= 2) list.animateScrollToItem(0) }

    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val pickedNames = remember(picked) { picked.map { displayName(ctx, android.net.Uri.parse(it)) } }
    var error by remember { mutableStateOf<String?>(null) }
    var queuing by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()) { uris ->
        picked = (picked + uris.map { it.toString() }).distinct().take(CloudChat.MAX_FILES)
    }
    var menu by remember { mutableStateOf(false) }

    Column(modifier.testTag("chat"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot(presence.tone)).testTag("chatDot-${presence.tone.name}"))
            Column(Modifier.weight(1f)) {
                Text("Claude" + (seat?.name?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(presence.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.testTag("chatWho"))
            }
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.testTag("chatMenu")) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("New chat") }, onClick = { menu = false; showEarlier = false; onNewChat() }, modifier = Modifier.testTag("chatNew"))
                    DropdownMenuItem(text = { Text("Clear chat on this phone") }, onClick = { menu = false; showEarlier = false; onClear() }, modifier = Modifier.testTag("chatClear"))
                    if (oldRows.isNotEmpty()) DropdownMenuItem(
                        text = { Text(if (showOld) "Hide the old app's chat" else "Show the old app's chat (${oldRows.size})") }, onClick = { menu = false; showOld = !showOld }, modifier = Modifier.testTag("chatOld"),
                    )
                }
            }
        }
        problem?.let { Text("Can't reach GitHub right now: $it. Your messages wait on this phone and go by themselves.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("chatProblem")) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = list, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().testTag("chatList")) {
                if (items.isEmpty()) item(key = "empty") {
                    Text("No messages yet. Ask about the engine, tell Claude what to change, or ask for a status.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                }
                items(items, key = { it.key }, contentType = { it.type }) { it ->
                    when (it) {
                        is Item.Bubble -> Bubble(it.row, onRetry, onDiscard, localFile)
                        is Item.Day -> Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        Item.Typing -> TypingBubble()
                        is Item.Earlier -> TextButton(onClick = { showEarlier = true }, modifier = Modifier.fillMaxWidth().testTag("chatEarlier")) { Text("Show earlier messages (${it.count})") }
                        Item.MoreOnGitHub -> TextButton(onClick = onLoadEarlier, modifier = Modifier.fillMaxWidth().testTag("chatMore")) { Text("Load older messages from GitHub") }
                        Item.OldHeader -> Text("The old app's chat (before 19 Oct), read only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        Item.OldEnd -> Text("The new chat starts here", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
            }
            if (list.firstVisibleItemIndex > 2) SmallFloatingActionButton(onClick = { scope.launch { list.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("chatDown")) { Text("↓") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("chatError")) }
        pickedNames.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📎 $name", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).testTag("chatPicked"))
                TextButton(onClick = { picked = picked.filterIndexed { j, _ -> j != i } }) { Text("Remove") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedButton(onClick = { picker.launch("*/*") }, enabled = picked.size < CloudChat.MAX_FILES, modifier = Modifier.testTag("chatAttach")) { Text("📎") }
            OutlinedTextField(text, { text = it; error = null }, modifier = Modifier.weight(1f).testTag("chatInput"), placeholder = { Text("Message Claude") }, maxLines = 6)
            Button(
                onClick = {
                    val t = text
                    val files = picked.map(android.net.Uri::parse)
                    queuing = true
                    scope.launch {
                        val why = onSend(t, files)
                        queuing = false
                        error = why
                        if (why == null) { text = ""; picked = emptyList(); list.scrollToItem(0) }
                    }
                },
                enabled = (text.isNotBlank() || picked.isNotEmpty()) && !queuing, modifier = Modifier.testTag("chatSend"),
            ) { Text("Send") }
        }
    }
}

private val OUTGOING = setOf(CloudChat.Delivery.SENDING, CloudChat.Delivery.RETRYING, CloudChat.Delivery.FAILED)

/** One thing in the list, newest first (the list grows upwards from the bottom). */
private sealed interface Item {
    val key: String
    val type: Int
    data class Bubble(val row: CloudChat.Row) : Item { override val key get() = row.id; override val type get() = 0 }
    data class Day(val label: String, override val key: String) : Item { override val type get() = 1 }
    data class Earlier(val count: Int) : Item { override val key get() = "earlier"; override val type get() = 2 }
    data object Typing : Item { override val key get() = "typing"; override val type get() = 3 }
    data object MoreOnGitHub : Item { override val key get() = "more"; override val type get() = 2 }
    data object OldHeader : Item { override val key get() = "oldHeader"; override val type get() = 1 }
    data object OldEnd : Item { override val key get() = "oldEnd"; override val type get() = 1 }
}

private fun buildItems(rows: List<CloudChat.Row>, old: List<CloudChat.Row>, earlier: Int, more: Boolean, typing: Boolean): List<Item> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val out = ArrayList<Item>(rows.size + old.size + 16)
    fun days(list: List<CloudChat.Row>, tag: String) {
        var last: LocalDate? = null
        for (r in list) {
            val d = Instant.ofEpochMilli(r.at).atZone(zone).toLocalDate()
            if (d != last) { out += Item.Day(dayLabel(d, today), "day-$tag-$d"); last = d }
            out += Item.Bubble(r)
        }
    }
    if (more) out += Item.MoreOnGitHub
    if (old.isNotEmpty()) { out += Item.OldHeader; days(old, "old"); out += Item.OldEnd }
    if (earlier > 0) out += Item.Earlier(earlier)
    days(rows, "new")
    if (typing) out += Item.Typing
    return out.asReversed()
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()))
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Composable
private fun dot(t: CloudChat.Tone): Color = when (t) {
    CloudChat.Tone.ONLINE -> Color(0xFF2E7D32)
    CloudChat.Tone.WORKING -> MaterialTheme.colorScheme.primary
    CloudChat.Tone.WAKING -> Color(0xFFF9A825)
    CloudChat.Tone.PROBLEM -> MaterialTheme.colorScheme.error
    CloudChat.Tone.OFFLINE -> MaterialTheme.colorScheme.outline
    CloudChat.Tone.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun Bubble(r: CloudChat.Row, onRetry: (String) -> Unit, onDiscard: (String) -> Unit, localFile: (String, Int, String) -> File?) {
    if (r.kind == "system") {
        Text(r.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).testTag("chat-${r.id}"))
        return
    }
    val mine = r.fromTj
    val link = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().alpha(if (r.old) 0.65f else 1f), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp, 16.dp, if (mine) 4.dp else 16.dp, if (mine) 16.dp else 4.dp),
            modifier = Modifier.widthIn(max = 330.dp).testTag("chat-${r.id}"),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!mine) Text(r.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                r.quote?.let { q ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Text("↩ $q", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, modifier = Modifier.padding(6.dp).testTag("chatQuote-${r.id}"))
                    }
                }
                if (r.text.isNotBlank()) SelectionContainer { Text(remember(r.text, link) { linked(r.text, link) }, style = MaterialTheme.typography.bodyMedium) }
                r.files.forEachIndexed { i, name ->
                    val f = if (mine && !r.old && isImage(name)) remember(r.id, i, name) { localFile(r.id, i + 1, name) } else null
                    if (f != null) Thumb(f) else Text("📎 $name", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("chatFile"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                    Text(Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault()).format(TIME), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (mine) r.delivery?.let { d ->
                        Text(mark(d), style = MaterialTheme.typography.labelSmall, color = if (d == CloudChat.Delivery.FAILED) MaterialTheme.colorScheme.error else if (d == CloudChat.Delivery.ANSWERED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("chatMark-${r.id}"))
                    }
                    if (r.liked) Text("👍", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("chatLiked-${r.id}"))
                }
                if (r.delivery == CloudChat.Delivery.FAILED || r.delivery == CloudChat.Delivery.RETRYING) {
                    r.problem?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = { onRetry(r.id) }, modifier = Modifier.testTag("chatRetry-${r.id}")) { Text(if (r.delivery == CloudChat.Delivery.FAILED) "Retry" else "Try now") }
                        TextButton(onClick = { onDiscard(r.id) }, modifier = Modifier.testTag("chatDelete-${r.id}")) { Text("Delete") }
                    }
                }
            }
        }
    }
}

private fun mark(d: CloudChat.Delivery): String = when (d) {
    CloudChat.Delivery.SENDING -> "Sending…"
    CloudChat.Delivery.RETRYING -> "Not sent yet, trying again"
    CloudChat.Delivery.FAILED -> "Not sent"
    CloudChat.Delivery.SENT -> "✓ Sent"
    CloudChat.Delivery.SEEN -> "✓✓ Seen"
    CloudChat.Delivery.ANSWERED -> "✓✓ Answered"
}

/** "Claude is replying": it has seen Tj's message and not answered yet. */
@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), modifier = Modifier.testTag("chatTyping")) {
            Text("Claude is replying…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

private val URL = Regex("""https?://[^\s<>()"']+[^\s<>()"'.,;:!?]""")

/** Links in a message open in the browser (a release link from Claude is tapped, not copied). */
private fun linked(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in URL.findAll(text)) {
        append(text.substring(i, m.range.first))
        withLink(LinkAnnotation.Url(m.value, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)))) { append(m.value) }
        i = m.range.last + 1
    }
    append(text.substring(i))
}

private fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.US) in setOf("png", "jpg", "jpeg", "webp", "gif", "heic")

/** A picture of Tj's own, from the phone's copy: decoded small, off the main thread. */
@Composable
private fun Thumb(f: File) {
    val bmp by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { decodeSmall(f) } }
    bmp?.let { Image(it, contentDescription = f.name, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).testTag("chatThumb")) }
        ?: Text("📎 " + f.name.substringAfter('-'), style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("chatFile"))
}

private fun decodeSmall(f: File): ImageBitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(f.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 480 || bounds.outHeight / (sample * 2) >= 480) sample *= 2
    android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** The name a picked file shows under the box (the provider's display name). */
private fun displayName(ctx: android.content.Context, uri: android.net.Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
