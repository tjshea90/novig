package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun MessagesPage(inbox: InboxState, onMarkRead: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(30_000)
    Column(modifier.testTag("messages"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (inbox.messages.isEmpty()) {
            Text("Nothing yet. Recommendations (which API to refill or buy), bets the engine placed and settings Claude changed show up here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (inbox.unread > 0) OutlinedButton(onClick = onMarkRead, modifier = Modifier.fillMaxWidth().testTag("messagesMarkRead")) { Text("Mark all ${inbox.unread} as read") }
            val read = inbox.readIds.toSet()
            inbox.messages.forEach { m ->
                val unread = m.id !in read
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("message-${m.id}")) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
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
