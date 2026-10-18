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
fun EngineScreen(state: com.tjshea.vigilant.app.UiState, onOpenCloudSettings: () -> Unit, onRefresh: () -> Unit = {}, onReset: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf(EngineSection.OVERVIEW) }
    Scaffold(topBar = { TopAppBar(title = { Text("Engine", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // One row of views (Tj, 2026-10-18, TCL122-7): Overview, Money, APIs (the usage meters), Trends.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("engineSections")) {
                EngineSection.entries.forEachIndexed { i, x ->
                    SegmentedButton(selected = section == x, onClick = { section = x }, shape = SegmentedButtonDefaults.itemShape(i, EngineSection.entries.size), modifier = Modifier.testTag("engineSection-${x.name}")) { Text(x.label, maxLines = 1) }
                }
            }
            // Pull down for a TRUE refresh (TCL122-3): every engine file and every API's own usage figure is read now; the arrow stays until the reads have answered.
            VigilantPullToRefresh(busy = state.cloudUi.refreshing || state.usageBusy, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                // Each view keeps its own scroll, so switching views opens at the top.
                val scroll = androidx.compose.runtime.key(section) { rememberScrollState() }
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp).testTag("engineScreen")) {
                    DashboardPage(state, onOpenCloudSettings, section = section)
                    if (section == EngineSection.OVERVIEW) ResetEngineButton(onReset)
                }
            }
        }
    }
}

/** Reset engine: two taps (the first arms it). Ends the running GitHub job and starts a new one on a new runner; never touches keys, trading switches or Claude. */
@Composable
fun ResetEngineButton(onReset: () -> Unit) {
    var armed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(
        onClick = { if (armed) { armed = false; onReset() } else armed = true },
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("resetEngine"),
    ) { Text(if (armed) "Tap again to reset the engine" else "Reset engine (new GitHub runner)") }
}

/** Chat with Claude and the messages from Claude/the engine, side by side under one title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeScreen(
    state: com.tjshea.vigilant.app.UiState,
    onSend: suspend (String, List<Pair<String, ByteArray>>) -> String?,
    onChatOpen: (Boolean) -> Unit,
    onNewChat: () -> Unit,
    onClearChat: (Long, Long) -> Unit,
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
            // The chat lays itself out (thread above, input pinned below, both inside the room the keyboard leaves); only the messages list scrolls here.
            if (section == "chat") ChatPage(state.claudeInbox.chat, state.chatWho, onSend, onChatOpen, Modifier.weight(1f).fillMaxWidth(), breaks = state.claudeInbox.chatBreaks, onNewChat = onNewChat, onClearChat = onClearChat)
            else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MessagesPage(state.claudeInbox, onMarkRead = onMarkRead, onOpen = onOpenPage, onDismiss = onDismissMessage, onClearAll = onClearMessages)
            }
        }
    }
}
