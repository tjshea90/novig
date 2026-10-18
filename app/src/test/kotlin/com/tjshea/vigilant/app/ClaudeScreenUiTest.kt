package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ChatPage
import com.tjshea.vigilant.app.ui.MessagesPage
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cloud.CloudChat
import com.tjshea.vigilant.data.cloud.CloudMessages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tj, 2026-10-16: a thumbs-up under messages Claude has read, attachments in the chat, a trash can per message and Clear all. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class ClaudeScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    @Test
    fun `a message a session has read shows a thumbs-up, one it has not does not, and the receipt itself is no message`() {
        val thread = listOf(
            CloudChat.Msg("t1", now - 60_000, "tj", "first", files = listOf("chat-files/t1-screen.png")),
            CloudChat.Msg("t2", now - 30_000, "tj", "second"),
            CloudChat.Msg("a1", now - 20_000, "claude:acct-1", "\uD83D\uDC4D", ack = "t1"),
        )
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(thread, "who", { _, _ -> null }, {}) } }
        compose.onNodeWithTag("chatRead-t1").assertIsDisplayed()
        compose.onAllNodesWithTag("chatRead-t2").assertCountEquals(0)
        compose.onAllNodesWithTag("chat-a1").assertCountEquals(0)
        compose.onNodeWithTag("chatFile").assertIsDisplayed()
        compose.onNodeWithTag("chatAttach").assertIsDisplayed()
    }

    @Test
    fun `the trash can deletes one message and Clear all clears them`() {
        val inbox = InboxState(messages = listOf(CloudMessages.Message("m1", now, "engine", "alert", "One", "", 0, null), CloudMessages.Message("m2", now, "engine", "alert", "Two", "", 0, null)))
        val deleted = ArrayList<String>()
        var cleared = 0
        compose.setContent { VigilantTheme(darkTheme = true) { MessagesPage(inbox, {}, {}, onDismiss = { deleted += it }, onClearAll = { cleared++ }) } }
        compose.onNodeWithTag("messageDelete-m2").performClick()
        compose.onNodeWithTag("messagesClearAll").performClick()
        assertEquals(listOf("m2"), deleted)
        assertEquals(1, cleared)
    }

    @Test
    fun `a new chat starts empty, the earlier chat is under Chats, and New chat and Clear chat ask for what they say`() {
        val thread = listOf(CloudChat.Msg("t1", now - 600_000, "tj", "old question"), CloudChat.Msg("t2", now - 10_000, "tj", "fresh question"))
        var newChats = 0
        var cleared: Pair<Long, Long>? = null
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(thread, "who", { _, _ -> null }, {}, breaks = listOf(now - 300_000), onNewChat = { newChats++ }, onClearChat = { a, b -> cleared = a to b }) } }
        compose.onNodeWithTag("chat-t2").assertIsDisplayed()
        compose.onAllNodesWithTag("chat-t1").assertCountEquals(0)
        compose.onNodeWithTag("chatList").assertIsDisplayed()
        compose.onNodeWithTag("chatNew").performClick()
        assertEquals(1, newChats)
        compose.onNodeWithTag("chatClear").performClick()
        assertEquals(now - 300_000 to Long.MAX_VALUE, cleared)
    }
}
