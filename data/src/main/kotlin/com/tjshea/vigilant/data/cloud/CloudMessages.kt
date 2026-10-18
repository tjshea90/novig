package com.tjshea.vigilant.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What Claude (a session of any of the three accounts, or the engine itself) wants Tj to know, shown in the phone app (Tj, 2026-10-15: "The vigilant phone app should be able to tell me anything Claude wants
 * me to know, including recommendations, bets placed, etc."). One small file per message on the `cloud-config` branch under `messages/`, so two writers never overwrite each other and the history is kept
 * by git; the phone lists the folder (one request, free when nothing is new) and reads what it has not seen. A message carries words only: never a key, a token or a personal identifier (public repo).
 */
object CloudMessages {
    const val DIR = "messages"
    const val BRANCH = "cloud-config"

    @Serializable
    data class Message(
        val id: String,
        /** When it was written (ms). */
        val at: Long,
        /** Who: "engine" or "claude:<account tag>". */
        val from: String,
        /** recommendation, bet, alert, shipped, info: how the phone groups and colours it. */
        val kind: String = "info",
        val title: String,
        val body: String = "",
        /** 0 = quiet (listed only); 1 = worth a push notification (a bet placed, an API out, something to do). */
        val priority: Int = 0,
        /** The Settings page to open from the message (a `SettingsPage` name), if any. */
        val action: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(m: Message): String = json.encodeToString(Message.serializer(), m)
    fun decode(text: String): Message? = runCatching { json.decodeFromString(Message.serializer(), text) }.getOrNull()?.takeIf { it.id.isNotBlank() && it.title.isNotBlank() }

    /** The file name: time first so the folder sorts by time, then the id. */
    fun fileName(m: Message): String = String.format(java.util.Locale.US, "%013d-%s.json", m.at, m.id.filter { it.isLetterOrDigit() || it == '-' }.take(24))

    /**
     * What the Messages list and the notifications show (Tj, 2026-10-18, TCL122-1: "only include important messages and API errors or recommendations. do not include bets and bids"): never a bet or bid
     * fill, never a quiet `info` line. Alerts, recommendations, shipped notices and anything pushed (priority 1) stay.
     */
    fun worthShowing(m: Message): Boolean = m.kind != "bet" && !(m.kind == "info" && m.priority < 1)

    /** Newest first. */
    fun newestFirst(ms: Collection<Message>): List<Message> = ms.sortedByDescending { it.at }

    /** The file names in a listing that the phone has not read yet, oldest first (`<ms>-<id>.json`: sorting the names sorts by time). */
    fun unseen(names: Collection<String>, seenNames: Set<String>): List<String> = names.filter { it.endsWith(".json") && it !in seenNames }.sorted()

    /** Words only: a value that looks like a key or a token never goes out (the repository is public). */
    fun looksSecret(text: String): Boolean = Regex("""(sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|[A-Fa-f0-9]{40,})""").containsMatchIn(text)
}
