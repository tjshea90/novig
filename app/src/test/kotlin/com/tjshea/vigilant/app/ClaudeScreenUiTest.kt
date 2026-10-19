package com.tjshea.vigilant.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.ChatPage
import com.tjshea.vigilant.app.ui.MessagesPage
import com.tjshea.vigilant.app.ui.VigilantTheme
import com.tjshea.vigilant.data.cloud.ChatState
import com.tjshea.vigilant.data.cloud.CloudChat
import com.tjshea.vigilant.data.cloud.CloudMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rebuilt chat (CR, Tj 2026-10-19: "work like a real chat"): each message says where it is, a message that did not go has Retry and Delete, a reply quotes what it answers when it is not right
 * above, Claude's thumbs-up, who is there, New chat and Clear chat, the old app's chat on request; and the Messages' trash can (Tj, 2026-10-16).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1600dp-xxhdpi")
class ClaudeScreenUiTest {
    @get:Rule val compose = createComposeRule()

    private val now = System.currentTimeMillis()
    private fun tj(id: String, at: Long, text: String) = CloudChat.Msg(id, at, "tj", text)
    private fun claude(id: String, at: Long, text: String, vararg to: String, kind: String = "reply") = CloudChat.Msg(id, at, "claude", text, to.toList(), kind, name = "mirage")
    private val live = CloudChat.Seat("acct-5d1e2d/x", "mirage", now - 600_000, now - 10_000)

    @Test
    fun `each message says where it is, and Claude's reply and thumbs-up show`() {
        val chat = ChatState(
            msgs = listOf(tj("t1", now - 90_000, "first"), tj("t2", now - 80_000, "second"), tj("t3", now - 70_000, "third"),
                claude("c1", now - 60_000, "an answer", "t1"), claude("c2", now - 50_000, "", "t2", kind = "done")),
            seen = listOf("t3"),
            outbox = listOf(CloudChat.Outgoing("t4", now - 1_000, "queued"), CloudChat.Outgoing("t5", now, "refused", tries = 1, stuck = true, problem = "GitHub refused the token (expired or wrong)")),
        )
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(chat, live, null, emptyList(), { _, _ -> null }, {}) } }
        compose.onNodeWithTag("chatMark-t1").assertTextContains("Answered", substring = true)
        compose.onNodeWithTag("chatMark-t3").assertTextContains("Seen", substring = true)
        compose.onNodeWithTag("chatMark-t4").assertTextContains("Sending", substring = true)
        compose.onNodeWithTag("chatMark-t5").assertTextContains("Not sent", substring = true)
        compose.onNodeWithTag("chatLiked-t2").assertIsDisplayed()
        compose.onAllNodesWithTag("chat-c2").assertCountEquals(0)
        compose.onNodeWithTag("chat-c1").assertIsDisplayed()
        compose.onNodeWithText("GitHub refused the token (expired or wrong)").assertIsDisplayed()
        compose.onNodeWithTag("chatWho").assertTextContains("replying", substring = true)
        compose.onNodeWithTag("chatTyping").assertIsDisplayed()
    }

    @Test
    fun `Retry and Delete on a message that did not go, and Send queues what was typed`() {
        val chat = ChatState(outbox = listOf(CloudChat.Outgoing("t5", now, "refused", tries = 1, stuck = true, problem = "no GitHub token saved")))
        val retried = ArrayList<String>()
        val deleted = ArrayList<String>()
        val sent = ArrayList<String>()
        compose.setContent {
            VigilantTheme(darkTheme = true) {
                ChatPage(chat, live, null, emptyList(), { t, _ -> sent += t; null }, {}, onRetry = { retried += it }, onDiscard = { deleted += it })
            }
        }
        compose.onNodeWithTag("chatRetry-t5").performClick()
        compose.onNodeWithTag("chatDelete-t5").performClick()
        assertEquals(listOf("t5"), retried)
        assertEquals(listOf("t5"), deleted)
        compose.onNodeWithTag("chatInput").performTextInput("hello Claude")
        compose.onNodeWithTag("chatSend").performClick()
        compose.waitForIdle()
        assertEquals(listOf("hello Claude"), sent)
        compose.onNodeWithTag("chatAttach").assertIsDisplayed()
    }

    @Test
    fun `a reply to a message that is not right above quotes it`() {
        val chat = ChatState(msgs = listOf(tj("t1", now - 9_000, "what about NHL?"), tj("t2", now - 8_000, "and tennis?"), claude("c2", now - 5_000, "tennis is on", "t2"), claude("c1", now - 4_000, "NHL too", "t1")))
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(chat, live, null, emptyList(), { _, _ -> null }, {}) } }
        compose.onNodeWithTag("chatQuote-c1").assertTextContains("what about NHL?", substring = true)
        compose.onAllNodesWithTag("chatQuote-c2").assertCountEquals(0)
    }

    @Test
    fun `who is there - waking when Claude is away and a message waits, a problem when the wake failed`() {
        val away = live.copy(beat = now - 30 * 60_000)
        val chat = ChatState(msgs = listOf(tj("t1", now - 20_000, "are you there?")))
        var seat by androidx.compose.runtime.mutableStateOf(away.copy(wakeAt = now - 10_000, wakeResult = "fired acct-5d1e2d (mirage)", fires = listOf(now - 10_000)))
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(chat, seat, null, emptyList(), { _, _ -> null }, {}) } }
        compose.onNodeWithTag("chatWho").assertTextContains("waking up", substring = true)
        compose.onNodeWithTag("chatDot-WAKING").assertIsDisplayed()
        seat = away.copy(wakeAt = now - 10_000, wakeResult = "failed: the wake link of acct-5d1e2d (mirage) is not saved on GitHub")
        compose.waitForIdle()
        compose.onNodeWithTag("chatWho").assertTextContains("could not be woken", substring = true)
        compose.onNodeWithTag("chatDot-PROBLEM").assertIsDisplayed()
    }

    @Test
    fun `a new chat starts empty with the earlier messages one tap away, and the menu asks for New chat and Clear chat`() {
        val chat = ChatState(msgs = listOf(tj("t1", now - 600_000, "old question"), tj("t2", now - 10_000, "fresh question")), breaks = listOf(now - 300_000))
        var newChats = 0
        var cleared = 0
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(chat, live, null, emptyList(), { _, _ -> null }, {}, onNewChat = { newChats++ }, onClear = { cleared++ }) } }
        compose.onNodeWithTag("chat-t2").assertIsDisplayed()
        compose.onAllNodesWithTag("chat-t1").assertCountEquals(0)
        compose.onNodeWithTag("chatEarlier").performClick()
        compose.onNodeWithTag("chat-t1").assertIsDisplayed()
        compose.onNodeWithTag("chatMenu").performClick()
        compose.onNodeWithTag("chatNew").performClick()
        compose.onNodeWithTag("chatMenu").performClick()
        compose.onNodeWithTag("chatClear").performClick()
        assertEquals(1, newChats)
        assertEquals(1, cleared)
    }

    @Test
    fun `the old app's chat is shown on request, read only`() {
        val old = listOf(CloudChat.OldMsg("t9", now - 86_400_000L, "tj", "from the old app"), CloudChat.OldMsg("r9", now - 86_000_000L, "claude:acct-5d1e2d (mirage)", "old answer", replyTo = "t9"))
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(ChatState(), live, null, old, { _, _ -> null }, {}) } }
        compose.onAllNodesWithTag("chat-old-t9").assertCountEquals(0)
        compose.onNodeWithTag("chatMenu").performClick()
        compose.onNodeWithTag("chatOld").performClick()
        compose.onNodeWithTag("chat-old-t9").assertIsDisplayed()
        compose.onNodeWithTag("chat-old-r9").assertIsDisplayed()
    }

    @Test
    fun `the chat says when GitHub cannot be reached, and opening it counts as reading it`() {
        var opened = false
        var read = 0
        val chat = ChatState(msgs = listOf(claude("c1", now, "news", kind = "say")))
        compose.setContent { VigilantTheme(darkTheme = true) { ChatPage(chat, live, "GitHub's rate limit", emptyList(), { _, _ -> null }, { opened = it }, onRead = { read++ }) } }
        compose.onNodeWithTag("chatProblem").assertIsDisplayed()
        compose.waitForIdle()
        assertTrue(opened)
        assertTrue(read >= 1)
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
}
