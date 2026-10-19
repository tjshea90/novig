package com.tjshea.vigilant.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tjshea.vigilant.data.cloud.CloudChat
import com.tjshea.vigilant.data.cloud.CloudClient
import com.tjshea.vigilant.data.cloud.CloudMessages
import com.tjshea.vigilant.data.cloud.CloudOverrides
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File

/** Everything the phone keeps of the GitHub side's talk: Messages from Claude (and the engine) and which settings requests it has applied. */
@Serializable
data class InboxState(
    val messages: List<CloudMessages.Message> = emptyList(),
    val seenNames: List<String> = emptyList(),
    val readIds: List<String> = emptyList(),
    val seenOverrideNames: List<String> = emptyList(),
    val appliedRequests: List<String> = emptyList(),
    /** Messages written after this have not been announced yet: a push notification goes out for the important ones. -1 = first run (nothing old is announced). */
    val notifiedUpToMs: Long = -1L,
    /** The old app's chat thread (TCL29, `chat/` on `cloud-config`, before 2026-10-19): kept as it was and shown read only under the new chat ([com.tjshea.vigilant.data.cloud.ChatSync]). */
    val chat: List<CloudChat.OldMsg> = emptyList(),
) {
    /** The messages worth listing (no bets or bids, no quiet info): what the screen, the unread count and the notifications use. */
    val shown: List<CloudMessages.Message> get() = messages.filter(CloudMessages::worthShowing)
    val unread: Int get() = shown.count { it.id !in readIds.toSet() }
}

/**
 * The phone's side of "Claude tells Tj" and "settings mirror both ways" (Tj, 2026-10-15). It lists `messages/` and `overrides/` on the `cloud-config` branch (one small file per item, a free 304 when nothing is new), keeps
 * what it reads, announces important messages as notifications, and applies each settings request from the GitHub side ONCE to this phone's settings (the engine or a Claude session turning an API on or off): the
 * settings then go back to GitHub through the usual push, so both sides show the same, and a change Tj makes afterwards on the phone stands until a newer request arrives.
 */
class CloudInbox(private val c: AppContainer, private val client: CloudClient) {
    private val store = JsonFileStore(File(c.app.filesDir, "claude_messages.json"), InboxState.serializer(), { InboxState() }, c.json)
    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()
    private var messagesEtag: String? = null
    private var overridesEtag: String? = null

    suspend fun load() { _state.value = runCatching { store.read() }.getOrDefault(InboxState()) }

    private suspend fun save(s: InboxState) {
        _state.value = s
        runCatching { store.update { s } }
    }

    suspend fun markAllRead() {
        val s = _state.value
        save(s.copy(readIds = s.messages.map { it.id }))
    }

    /** Reads new messages from GitHub; returns how many arrived. */
    suspend fun pollMessages(now: Long = System.currentTimeMillis()): Int {
        val names = when (val p = client.listDir(CloudMessages.BRANCH, CloudMessages.DIR, messagesEtag)) {
            is CloudClient.Poll.Fresh -> { messagesEtag = p.etag; p.value }
            else -> return 0
        }
        var s = _state.value
        val fresh = CloudMessages.unseen(names, s.seenNames.toSet()).takeLast(MAX_PER_ROUND)
        if (fresh.isEmpty()) return 0
        val got = ArrayList<CloudMessages.Message>()
        val doneNames = ArrayList<String>()
        for (n in fresh) {
            val text = client.readFile(CloudMessages.BRANCH, "${CloudMessages.DIR}/$n") ?: continue
            doneNames += n
            CloudMessages.decode(text)?.let { got += it }
        }
        if (doneNames.isEmpty()) return 0
        val merged = CloudMessages.newestFirst((s.messages + got).distinctBy { it.id }).take(MAX_KEPT)
        val firstRun = s.notifiedUpToMs < 0
        val upTo = maxOf(s.notifiedUpToMs, got.maxOfOrNull { it.at } ?: 0L)
        if (!firstRun) got.filter { CloudMessages.worthShowing(it) && it.priority >= 1 && it.at > s.notifiedUpToMs }.takeLast(3).forEach { notify(it) }
        s = s.copy(messages = merged, seenNames = (s.seenNames + doneNames).takeLast(MAX_NAMES), notifiedUpToMs = upTo)
        save(s)
        return got.size
    }

    /** One message leaves the list (the trash can); it does not come back, because the file is remembered as seen. */
    suspend fun dismissMessage(id: String) {
        val s = _state.value
        save(s.copy(messages = s.messages.filter { it.id != id }, readIds = s.readIds.filter { it != id }))
    }

    /** Clear all: every message leaves the list. */
    suspend fun clearMessages() {
        val s = _state.value
        save(s.copy(messages = emptyList(), readIds = emptyList()))
    }

    /** Applies new settings requests from the GitHub side once each; returns how many changed something. */
    suspend fun pollOverrides(now: Long = System.currentTimeMillis()): Int {
        val names = when (val p = client.listDir(CloudOverrides.BRANCH, CloudOverrides.DIR, overridesEtag)) {
            is CloudClient.Poll.Fresh -> { overridesEtag = p.etag; p.value }
            else -> return 0
        }
        var s = _state.value
        val fresh = CloudMessages.unseen(names, s.seenOverrideNames.toSet()).takeLast(MAX_PER_ROUND)
        if (fresh.isEmpty()) return 0
        var changed = 0
        val done = ArrayList<String>()
        val applied = ArrayList<String>()
        val notes = ArrayList<CloudMessages.Message>()
        for (n in fresh) {
            val text = client.readFile(CloudOverrides.BRANCH, "${CloudOverrides.DIR}/$n") ?: continue
            done += n
            val r = CloudOverrides.decode(text) ?: continue
            if (r.id in s.appliedRequests || now - r.at > MAX_AGE_MS) continue
            val before = c.settingsStore.flow.value ?: continue
            val what = CloudOverrides.describe(before, r)
            runCatching { c.settingsStore.update { CloudOverrides.apply(it, r) } }.onFailure { continue }
            applied += r.id
            if (what.isNotBlank()) {
                changed++
                c.eventLog.info("CLOUD", "settings changed from GitHub ($what) by ${r.by}: ${r.reason}")
                notes += CloudMessages.Message("ovr-${r.id}", now, "phone", "info", "Settings changed from GitHub: $what", "Asked by ${r.by}" + (r.reason.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") + ". It is also shown in Settings › APIs; switch it back there if you disagree.", 0, "APIS")
            }
        }
        if (done.isEmpty()) return 0
        s = s.copy(
            seenOverrideNames = (s.seenOverrideNames + done).takeLast(MAX_NAMES), appliedRequests = (s.appliedRequests + applied).takeLast(MAX_NAMES),
            messages = CloudMessages.newestFirst((s.messages + notes).distinctBy { it.id }).take(MAX_KEPT),
        )
        save(s)
        return changed
    }

    private fun notify(m: CloudMessages.Message) {
        val app: Application = c.app
        if (!ScanService.canNotify(app)) return
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Messages from Claude", NotificationManager.IMPORTANCE_DEFAULT))
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle(m.title)
                .setContentText(m.body.lineSequence().firstOrNull().orEmpty())
                .setStyle(NotificationCompat.BigTextStyle().bigText(m.body.ifBlank { m.title }))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .withWallet(app)
                .setContentIntent(EvAlerts.openVigilant(app, ID))
                .build()
            NotificationManagerCompat.from(app).notify(m.id, ID, n)
        }
    }

    companion object {
        const val CHANNEL = "claude_messages"
        const val ID = 7710
        const val MAX_PER_ROUND = 20
        const val MAX_KEPT = 200
        const val MAX_NAMES = 1_000
        /** A settings request older than this is not applied (the phone was off for a day: the world has moved). */
        const val MAX_AGE_MS = 24 * 3_600_000L
    }
}
