package com.tjshea.vigilant.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Chat between Tj (in the phone app) and whichever Claude session is working (TCL29, Tj 2026-10-15: "chat via the repo ... each Claude session actively look for new messages ... little lag").
 * One small file per message under `chat/` on `cloud-config`, both directions in one folder so the thread is ordered by time: Tj's messages have `from = "tj"`, a session's reply has
 * `from = "claude:<account tag>"` and `replyTo` = the id it answers. Words only: never a key (the repo is public).
 */
object CloudChat {
    const val DIR = "chat"
    const val BRANCH = "cloud-config"
    const val LEASE_BRANCH = "claude-coord"
    const val FILES_DIR = "chat-files"
    const val KEEP_DIR = "chat-keep"
    const val FILES_KEEP_HOURS = 24

    /** The path of an attachment named [name] on message [msgId]: unique, and the cleanup finds the message from its prefix. */
    fun filePath(msgId: String, name: String): String = "$FILES_DIR/$msgId-" + name.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }.joinToString("").takeLast(60)

    @Serializable
    data class Msg(
        val id: String, val at: Long, val from: String, val text: String, val replyTo: String? = null,
        /** A read receipt (Tj, 2026-10-16: a thumbs-up when Claude has read a message): [ack] is the id of Tj's message a session has read; the message itself is never shown. */
        val ack: String? = null,
        /** Attachments Tj added (screenshots, files): paths on the branch under [FILES_DIR]. They are deleted after [FILES_KEEP_HOURS] hours unless a session flags the message important (`chat-keep/<id>`). */
        val files: List<String> = emptyList(),
    ) {
        val fromTj: Boolean get() = from == "tj"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(m: Msg): String = json.encodeToString(Msg.serializer(), m)
    fun decode(text: String): Msg? = runCatching { json.decodeFromString(Msg.serializer(), text) }.getOrNull()?.takeIf { it.id.isNotBlank() && (it.text.isNotBlank() || it.files.isNotEmpty() || it.ack != null) }

    /** The messages to show: read receipts are not messages. */
    fun visible(ms: Collection<Msg>): List<Msg> = thread(ms).filter { it.ack == null }

    /** Tj's message ids a session has read, with when. */
    fun acked(ms: Collection<Msg>): Map<String, Long> = ms.filter { it.ack != null }.groupBy { it.ack!! }.mapValues { (_, l) -> l.minOf { it.at } }
    fun fileName(m: Msg): String = String.format(java.util.Locale.US, "%013d-%s.json", m.at, m.id.filter { it.isLetterOrDigit() || it == '-' }.take(24))

    /** The thread, oldest first. */
    fun thread(ms: Collection<Msg>): List<Msg> = ms.distinctBy { it.id }.sortedBy { it.at }

    /** Tj's messages no reply answers yet (a reply names it in [Msg.replyTo]); the sessions' watcher prints these. */
    fun unanswered(ms: Collection<Msg>): List<Msg> {
        val answered = ms.mapNotNull { it.replyTo }.toSet()
        return thread(ms).filter { it.fromTj && it.ack == null && it.id !in answered }
    }

    /** What the app says under the thread: who will answer, from the lease on `claude-coord` (holder, kind, heartbeat age in seconds), or null when it could not be read. */
    fun whoLine(holder: String?, kind: String?, heartbeatAgeS: Long?, leaseS: Long = 20 * 60, nextSlot: String? = null): String {
        if (holder.isNullOrBlank() || heartbeatAgeS == null || heartbeatAgeS > leaseS) {
            return "No Claude session is running. Your message waits on GitHub and the next one to start answers it" + (nextSlot?.let { " (next scheduled run $it)" } ?: "") + "."
        }
        val who = holder.substringBefore('/')
        val age = if (heartbeatAgeS < 90) "just now" else "${heartbeatAgeS / 60} min ago"
        return "$who is working (${kind ?: "session"}, last seen $age). It reads new messages within about a minute."
    }

    /** Words only. */
    fun looksSecret(text: String): Boolean = CloudMessages.looksSecret(text)
}
