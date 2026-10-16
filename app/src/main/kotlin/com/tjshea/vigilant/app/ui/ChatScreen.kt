package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.cloud.CloudChat
import com.tjshea.vigilant.data.cloud.DashboardModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Chat with Claude (TCL29). The thread is files on GitHub; whichever Claude session holds the lease reads a new message within about a minute (`tools/chat_watch.sh`). The line under the
 * thread says who is answering, from the lease. Nothing here can place a bet or move money: Claude changes things only through the repo's usual, tested paths.
 */
@Composable
fun ChatPage(
    thread: List<CloudChat.Msg>, who: String, onSend: suspend (String, List<Pair<String, ByteArray>>) -> String?, onOpen: (Boolean) -> Unit, modifier: Modifier = Modifier,
    breaks: List<Long> = emptyList(), onNewChat: () -> Unit = {}, onClearChat: (Long, Long) -> Unit = { _, _ -> },
) {
    DisposableEffect(Unit) { onOpen(true); onDispose { onOpen(false) } }
    val now = rememberNow(15_000)
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    // Screenshots and files (Tj, 2026-10-16): picked here, uploaded with the message, deleted from GitHub after 24 hours unless Claude flags the message.
    var picked by remember { mutableStateOf<List<Pair<String, ByteArray>>>(emptyList()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            val read = withContext(kotlinx.coroutines.Dispatchers.IO) { uris.take(5).mapNotNull { u -> readAttachment(ctx, u) } }
            picked = (picked + read).takeLast(5)
        }
    }
    // The thread is split into chats at each break (Tj, 2026-10-16: clear chats or open new chats); the newest chat is open unless an earlier one is picked.
    val starts = remember(breaks) { listOf(0L) + breaks.sorted() }
    var picked0 by remember { mutableStateOf<Int?>(null) }
    val sel = (picked0 ?: (starts.size - 1)).coerceIn(0, starts.size - 1)
    val from = starts[sel]
    val to = starts.getOrNull(sel + 1) ?: Long.MAX_VALUE
    val inChat = remember(thread, from, to) { thread.filter { it.at >= from && it.at < to } }
    val acked = remember(thread) { CloudChat.acked(thread) }
    val shown = remember(inChat) { CloudChat.visible(inChat) }
    var menu by remember { mutableStateOf(false) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    // The newest message stays in view (also when the keyboard opens and the thread shrinks).
    // Always at the newest message (Tj, 2026-10-16: it sometimes came back scrolled to the middle): when it opens, when a message arrives, and whenever the room changes (keyboard, attachments).
    androidx.compose.runtime.LaunchedEffect(shown.size, picked.size, scroll.maxValue, sel) { scroll.scrollTo(scroll.maxValue) }
    Column(modifier.testTag("chat"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // The thread takes whatever room is left and scrolls; the box to type in is pinned under it (Tj, 2026-10-16: the screen broke when the keyboard opened).
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = { onNewChat(); picked0 = null }, modifier = Modifier.testTag("chatNew")) { Text("New chat") }
          OutlinedButton(onClick = { onClearChat(from, to); picked0 = null }, enabled = inChat.isNotEmpty(), modifier = Modifier.testTag("chatClear")) { Text("Clear chat") }
          if (starts.size > 1) Box {
              OutlinedButton(onClick = { menu = true }, modifier = Modifier.testTag("chatList")) { Text("Chats (${starts.size})") }
              androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                  starts.indices.reversed().forEach { i ->
                      val ms = CloudChat.visible(thread.filter { it.at >= starts[i] && it.at < (starts.getOrNull(i + 1) ?: Long.MAX_VALUE) })
                      val label = (ms.firstOrNull { it.fromTj }?.text ?: ms.firstOrNull()?.text ?: "(empty)").take(40)
                      androidx.compose.material3.DropdownMenuItem(text = { Text((if (i == sel) "• " else "") + label) }, onClick = { picked0 = i; menu = false })
                  }
              }
          }
      }
      Column(Modifier.weight(1f, fill = true).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(who.ifBlank { "Checking who is answering..." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("chatWho"))
        if (thread.isEmpty()) Text("No messages yet. Ask about the engine, tell Claude what to change, or ask for a status.", style = MaterialTheme.typography.bodyMedium)
        shown.takeLast(40).forEach { m ->
            Surface(
                color = if (m.fromTj) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(start = if (m.fromTj) 40.dp else 0.dp, end = if (m.fromTj) 0.dp else 40.dp).testTag("chat-${m.id}"),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (m.text.isNotBlank()) Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    m.files.forEach { f -> Text("📎 " + f.substringAfterLast('/').substringAfter('-'), style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("chatFile")) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (m.fromTj) "You" else m.from.removePrefix("claude:")) + " · " + DashboardModel.ago(now - m.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // A thumbs-up once a Claude session has read the message (Tj, 2026-10-16).
                        if (m.fromTj && m.id in acked) Text("👍", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("chatRead-${m.id}"))
                    }
                }
            }
        }
      }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        picked.forEachIndexed { i, (name, bytes) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📎 $name (${bytes.size / 1024} KB)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).testTag("chatPicked"))
                TextButton(onClick = { picked = picked.filterIndexed { j, _ -> j != i } }) { Text("Remove") }
            }
        }
        if (picked.isNotEmpty()) Text("Attachments are deleted from GitHub after 24 hours unless Claude marks them important.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedButton(onClick = { picker.launch("*/*") }, enabled = !sending, modifier = Modifier.testTag("chatAttach")) { Text("📎") }
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f).testTag("chatInput"), placeholder = { Text("Message Claude") }, maxLines = 5)
            Button(
                onClick = {
                    val t = text
                    val files = picked
                    sending = true
                    scope.launch { val r = onSend(t, files); error = r; if (r == null) { text = ""; picked = emptyList() }; sending = false }
                },
                enabled = (text.isNotBlank() || picked.isNotEmpty()) && !sending, modifier = Modifier.testTag("chatSend"),
            ) { Text(if (sending) "..." else "Send") }
        }
    }
}

/** The file behind [uri] as its display name and bytes, or null when it cannot be read (or is over the attachment limit). */
private fun readAttachment(ctx: android.content.Context, uri: android.net.Uri): Pair<String, ByteArray>? = runCatching {
    val name = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    name to bytes
}.getOrNull()
