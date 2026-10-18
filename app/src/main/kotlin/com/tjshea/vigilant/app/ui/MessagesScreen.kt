package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.app.InboxState
import com.tjshea.vigilant.data.cloud.DashboardModel

/**
 * Settings › Messages from Claude (Tj, 2026-10-15: "The vigilant phone app should be able to tell me anything Claude wants me to know, including recommendations, bets placed"): newest first, unread in bold,
 * a button on a message that points at a settings page opens it. Written by the engine and by any Claude session through `messages/` on the cloud-config branch.
 */
@Composable
fun MessagesPage(inbox: InboxState, onMarkRead: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier, onDismiss: (String) -> Unit = {}, onClearAll: () -> Unit = {}) {
    val now = rememberNow(30_000)
    Column(modifier.testTag("messages"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (inbox.shown.isEmpty()) {
            Text("Nothing yet. Only what matters shows up here: alerts (the engine stopped, an API failing), recommendations (which API to refill or buy) and notices from Claude. Bets and bids are on the Engine tab and the Tracker.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (inbox.unread > 0) OutlinedButton(onClick = onMarkRead, modifier = Modifier.weight(1f).testTag("messagesMarkRead")) { Text("Mark ${inbox.unread} read") }
                OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f).testTag("messagesClearAll")) { Text("Clear all") }
            }
            val read = inbox.readIds.toSet()
            inbox.shown.forEach { m ->
                val unread = m.id !in read
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("message-${m.id}")) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDismiss(m.id) }, modifier = Modifier.testTag("messageDelete-${m.id}")) { Icon(Icons.Filled.Delete, contentDescription = "Delete this message") }
                        }
                        if (m.body.isNotBlank()) Text(m.body, style = MaterialTheme.typography.bodyMedium)
                        Text("${m.from} · ${DashboardModel.ago(now - m.at)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        m.action?.takeIf { it.isNotBlank() && runCatching { SettingsPage.valueOf(it) }.isSuccess }?.let { a ->
                            TextButton(onClick = { onOpen(a) }) { Text("Open ${SettingsPage.valueOf(a).title}") }
                        }
                    }
                }
            }
        }
    }
}
