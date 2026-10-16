package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tjshea.vigilant.data.cloud.CloudChat

/**
 * The two sections that used to sit inside Settings and are now central (Tj, 2026-10-16: "move major features out of the settings and into their own sections that are now central to the app, such as the
 * engine monitor and messaging with and notifications from Claude. this app is a monitoring and central control app, make the UI match").
 */

/** The home: what the GitHub engine is doing, live from GitHub (state, readings, APIs, bets, bids, trends). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(state: com.tjshea.vigilant.app.UiState, onOpenCloudSettings: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Engine", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("engineScreen")) {
            DashboardPage(state, onOpenCloudSettings)
        }
    }
}

/** Chat with Claude and the messages from Claude/the engine, side by side under one title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeScreen(
    state: com.tjshea.vigilant.app.UiState,
    onSend: suspend (String, List<Pair<String, ByteArray>>) -> String?,
    onChatOpen: (Boolean) -> Unit,
    onMarkRead: () -> Unit,
    onDismissMessage: (String) -> Unit,
    onClearMessages: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf("chat") }
    val unread = state.claudeInbox.unread
    Scaffold(topBar = { TopAppBar(title = { Text("Claude", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("claudeSections")) {
                SegmentedButton(selected = section == "chat", onClick = { section = "chat" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Chat") }
                SegmentedButton(selected = section == "messages", onClick = { section = "messages" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(if (unread > 0) "Messages ($unread)" else "Messages") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (section == "chat") ChatPage(state.claudeInbox.chat, state.chatWho, onSend, onChatOpen)
                else MessagesPage(state.claudeInbox, onMarkRead = onMarkRead, onOpen = onOpenPage, onDismiss = onDismissMessage, onClearAll = onClearMessages)
            }
        }
    }
}
