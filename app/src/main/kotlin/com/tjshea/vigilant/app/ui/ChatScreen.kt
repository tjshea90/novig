package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

/**
 * Settings › Chat with Claude (TCL29). The thread is files on GitHub; whichever Claude session holds the lease reads a new message within about a minute (`tools/chat_watch.sh`). The line under the
 * thread says who is answering, from the lease. Nothing here can place a bet or move money: Claude changes things only through the repo's usual, tested paths.
 */
@Composable
fun ChatPage(thread: List<CloudChat.Msg>, who: String, onSend: suspend (String) -> String?, onOpen: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { onOpen(true); onDispose { onOpen(false) } }
    val now = rememberNow(15_000)
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    Column(modifier.testTag("chat"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(who.ifBlank { "Checking who is answering..." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("chatWho"))
        if (thread.isEmpty()) Text("No messages yet. Ask about the engine, tell Claude what to change, or ask for a status.", style = MaterialTheme.typography.bodyMedium)
        thread.takeLast(40).forEach { m ->
            Surface(
                color = if (m.fromTj) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(start = if (m.fromTj) 40.dp else 0.dp, end = if (m.fromTj) 0.dp else 40.dp).testTag("chat-${m.id}"),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    Text((if (m.fromTj) "You" else m.from.removePrefix("claude:")) + " · " + DashboardModel.ago(now - m.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f).testTag("chatInput"), placeholder = { Text("Message Claude") }, maxLines = 5)
            Button(
                onClick = {
                    val t = text
                    sending = true
                    scope.launch { val r = onSend(t); error = r; if (r == null) text = ""; sending = false }
                },
                enabled = text.isNotBlank() && !sending, modifier = Modifier.testTag("chatSend"),
            ) { Text(if (sending) "..." else "Send") }
        }
    }
}
