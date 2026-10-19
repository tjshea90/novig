package com.tjshea.vigilant.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * The app chat with Claude, rebuilt from scratch (CR, Tj 2026-10-19: "It misses some of my messages or ignores them, it sends me stale messages, sometimes it sends me back to back messages that are
 * almost identical, sometimes it doesn't get my attachments. It must be totally rebuilt ... work like a real chat"). Why, with the evidence: research/coordination/CHAT_REBUILD_2026-10-19.md; the
 * other side is tools/chat.py, whose JSON this matches.
 *
 * The store is the `chat` branch, never rewritten: `msgs/<UTC day>/<id>.json` one immutable file per message (Tj's ids `t<ms>-<hex>`, Claude's `c<ms>-<hex>`; a reply names the ids it answers in
 * `replyTo`), `msgs/<day>/<id>.seen.json` the receipt the chat account's watcher writes when it showed the message to the session (only its name matters), `files/<message id>/<n>-<name>` the
 * attachments, uploaded BEFORE the message that names them. `seat.json` on `chat-seat` says whether the chat account is there (heartbeat), what it is doing and the last wake. Only acct-5d1e2d
 * (mirage) answers (research/coordination/chat.json). Words only: never a key (the repo is public).
 */
object CloudChat {
    const val BRANCH = "chat"
    const val SEAT_BRANCH = "chat-seat"
    const val MSGS = "msgs"
    const val FILES = "files"
    const val SEAT_FILE = "seat.json"
    const val SEEN_SUFFIX = ".seen.json"

    /** Attachments one message may carry, and the most one may be (the contents API takes 100 MB; a body of 20 MB is what a phone should hold in memory at once). */
    const val MAX_FILES = 5
    const val MAX_FILE_BYTES = 20_000_000L

    /** The chat account's watcher beats every 45 s; after this it is gone (tools/chat.py STALE_S). */
    const val SEAT_STALE_MS = 150_000L

    /** A message on GitHub (either side). [kind] is Claude's: reply, done (a thumbs-up, no bubble), say (Claude's own), system (from GitHub); null for Tj's. */
    @Serializable
    data class Msg(
        val id: String, val at: Long, val from: String, val text: String = "",
        val replyTo: List<String> = emptyList(),
        val kind: String? = null,
        val files: List<FileRef> = emptyList(),
        /** The answering account's name (mirage). */
        val name: String? = null,
        /** A reply to a message written in the old app: Tj's words it answers. */
        val quote: String? = null,
    ) {
        val fromTj: Boolean get() = from == "tj"
        val isDone: Boolean get() = !fromTj && kind == "done"
    }

    /** An attachment on GitHub: [path] on [BRANCH]. */
    @Serializable
    data class FileRef(val path: String, val name: String = "", val size: Long = 0L, val type: String? = null)

    /** An attachment kept on this phone: copied into the app's own storage when it was picked, so a restart or a lost connection loses nothing. */
    @Serializable
    data class LocalFile(val path: String, val name: String, val size: Long, val type: String? = null)

    /** A message of Tj's not on GitHub yet: it is sent again and again until GitHub has it (a lost answer is found by its id: never sent twice). */
    @Serializable
    data class Outgoing(
        val id: String, val at: Long, val text: String,
        val files: List<LocalFile> = emptyList(),
        /** Attachments already on GitHub (their index in [files]): a retry does not send them again. */
        val uploaded: List<Int> = emptyList(),
        val tries: Int = 0,
        val nextTryAt: Long = 0L,
        /** Why the last try failed, in words. */
        val problem: String? = null,
        /** It stopped trying by itself (the token was refused, a file is gone): it waits for Retry. */
        val stuck: Boolean = false,
    )

    /** What tools/chat.py writes on `chat-seat`: who answers (heartbeat), what it is doing, the last wake. */
    data class Seat(
        val holder: String? = null, val name: String? = null, val since: Long = 0L, val beat: Long = 0L,
        val state: String = "", val note: String = "",
        val wakeAt: Long = 0L, val wakeResult: String = "", val fires: List<Long> = emptyList(),
    ) {
        fun live(now: Long): Boolean = !holder.isNullOrBlank() && now - beat <= SEAT_STALE_MS
    }

    // ---- files and ids ------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    fun dayOf(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate().toString()

    /** Tj's message ids: `t<13-digit ms>-<4 hex>` (tools/chat.py TJ_ID). */
    fun newId(at: Long, random: java.util.Random = java.security.SecureRandom()): String = String.format(Locale.US, "t%013d-%04x", at, random.nextInt(0x10000))

    fun msgPath(id: String, at: Long): String = "$MSGS/${dayOf(at)}/$id.json"

    /** Attachment [n] (from 1) of message [id]. */
    fun filePath(id: String, n: Int, name: String): String = "$FILES/$id/$n-${safeName(name)}"

    fun safeName(name: String): String =
        name.map { if ((it.isLetterOrDigit() && it.code < 128) || it == '.' || it == '-' || it == '_') it else '_' }.joinToString("").takeLast(80).ifBlank { "file" }

    /** Words only (the repo is public). */
    fun looksSecret(text: String): Boolean = CloudMessages.looksSecret(text)

    // ---- reading and writing ------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Tj's message as tools/chat.py reads it. */
    fun encode(o: Outgoing): String = buildJsonObject {
        put("v", 2); put("id", o.id); put("at", o.at); put("from", "tj"); put("text", o.text)
        if (o.files.isNotEmpty()) putJsonArray("files") {
            o.files.forEachIndexed { i, f -> addJsonObject { put("path", filePath(o.id, i + 1, f.name)); put("name", f.name); put("size", f.size); f.type?.let { put("type", it) } } }
        }
    }.toString()

    /** What GitHub has for an outgoing message once it is there. */
    fun sent(o: Outgoing): Msg = Msg(o.id, o.at, "tj", o.text, files = o.files.mapIndexed { i, f -> FileRef(filePath(o.id, i + 1, f.name), f.name, f.size, f.type) })

    /** One message file, read forgivingly (a list or a single id in replyTo; attachments as objects or paths); null when it is not a message. */
    fun decode(text: String): Msg? = runCatching {
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        val id = str("id")?.takeIf { it.isNotBlank() } ?: return null
        val at = (o["at"] as? JsonPrimitive)?.longOrNull ?: return null
        val from = str("from") ?: return null
        val replyTo = when (val r = o["replyTo"]) {
            is JsonArray -> r.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> listOfNotNull(r.contentOrNull?.takeIf(String::isNotBlank))
            else -> emptyList()
        }
        val files = (o["files"] as? JsonArray).orEmpty().mapNotNull { f -> fileRef(f) }
        Msg(id, at, from, str("text").orEmpty(), replyTo, str("kind"), files, str("name"), str("quote")?.takeIf { it.isNotBlank() })
    }.getOrNull()

    private fun fileRef(f: JsonElement): FileRef? = when (f) {
        is JsonObject -> (f["path"] as? JsonPrimitive)?.contentOrNull?.let { p ->
            FileRef(p, (f["name"] as? JsonPrimitive)?.contentOrNull ?: p.substringAfterLast('/'), (f["size"] as? JsonPrimitive)?.longOrNull ?: 0L, (f["type"] as? JsonPrimitive)?.contentOrNull)
        }
        is JsonPrimitive -> f.contentOrNull?.let { FileRef(it, it.substringAfterLast('/')) }
        else -> null
    }

    /** seat.json; an empty seat when it has none; null when the text is not JSON. */
    fun parseSeat(text: String?): Seat? = runCatching {
        if (text.isNullOrBlank()) return Seat()
        val o = Json.parseToJsonElement(text) as? JsonObject ?: return null
        val s = o["seat"] as? JsonObject
        val w = o["wake"] as? JsonObject
        fun JsonObject?.str(k: String) = (this?.get(k) as? JsonPrimitive)?.contentOrNull
        fun JsonObject?.num(k: String) = (this?.get(k) as? JsonPrimitive)?.longOrNull ?: 0L
        Seat(
            s.str("holder")?.takeIf { it.isNotBlank() }, s.str("name"), s.num("since"), s.num("beat"), s.str("state").orEmpty(), s.str("note").orEmpty(),
            w.num("at"), w.str("result").orEmpty(), (w?.get("fires") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.longOrNull },
        )
    }.getOrNull()

    // ---- the thread as the screen draws it ---------------------------------------------------------------------------------------------------------------------------------------------------

    /** Where one of Tj's messages is: on this phone (sending / waiting to try again / stopped), on GitHub, seen by Claude's session, answered. */
    enum class Delivery { SENDING, RETRYING, FAILED, SENT, SEEN, ANSWERED }

    /** One bubble, oldest first. */
    data class Row(
        val id: String, val at: Long, val fromTj: Boolean, val text: String,
        val files: List<String> = emptyList(),
        val kind: String? = null,
        /** "You", "Claude (mirage)", "GitHub", or the old app's sender. */
        val author: String = "",
        val delivery: Delivery? = null,
        /** A thumbs-up from Claude (a `done`: read, nothing to say). */
        val liked: Boolean = false,
        /** A reply to a message that is not right above it: the words it answers. */
        val quote: String? = null,
        /** Why an outgoing message is not on GitHub yet. */
        val problem: String? = null,
        /** The old app's chat (read only). */
        val old: Boolean = false,
    )

    private fun author(m: Msg): String = when {
        m.fromTj -> "You"
        m.kind == "system" -> "GitHub"
        else -> "Claude" + (m.name?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
    }

    private fun words(m: Msg): String = m.text.trim().ifBlank { m.files.joinToString(", ") { it.name }.ifBlank { "your message" } }

    /**
     * The thread: Tj's messages with where each one is, Claude's replies (a `done` is a thumbs-up on what it names, not a bubble), and the messages still on this phone. A reply sorts after what it
     * answers even when the two clocks disagree, and quotes it when it is not the message right above.
     */
    fun thread(msgs: Collection<Msg>, seen: Set<String>, outbox: List<Outgoing> = emptyList()): List<Row> {
        val all = msgs.distinctBy { it.id }
        val byId = all.associateBy { it.id }
        val answers = HashMap<String, MutableList<Msg>>()
        for (m in all) if (!m.fromTj) m.replyTo.forEach { answers.getOrPut(it) { ArrayList() } += m }
        fun order(m: Msg): Long = if (m.fromTj) m.at else maxOf(m.at, m.replyTo.maxOfOrNull { (byId[it]?.at ?: Long.MIN_VALUE) + 1 } ?: Long.MIN_VALUE)
        val sorted = all.filterNot { it.isDone }.sortedWith(compareBy<Msg>({ order(it) }, { it.id }))
        val rows = ArrayList<Row>(sorted.size + outbox.size)
        val run = LinkedHashSet<String>()   // Tj's messages since Claude last spoke
        for (m in sorted) {
            if (m.fromTj) {
                val got = answers[m.id].orEmpty()
                val d = when { got.isNotEmpty() -> Delivery.ANSWERED; m.id in seen -> Delivery.SEEN; else -> Delivery.SENT }
                rows += Row(m.id, m.at, true, m.text, m.files.map { it.name }, null, author(m), d, liked = got.any { it.isDone })
                run += m.id
            } else {
                val away = m.replyTo.firstOrNull { it !in run && byId[it]?.fromTj == true }
                val quote = away?.let { words(byId.getValue(it)) } ?: m.quote?.takeIf { m.replyTo.isEmpty() }
                rows += Row(m.id, order(m), false, m.text, m.files.map { it.name }, m.kind, author(m), quote = quote?.take(160))
                run.clear()
            }
        }
        val onGitHub = byId.keys
        for (o in outbox.sortedBy { it.at }) {
            if (o.id in onGitHub) continue
            val d = when { o.stuck -> Delivery.FAILED; o.tries > 0 -> Delivery.RETRYING; else -> Delivery.SENDING }
            rows += Row(o.id, o.at, true, o.text, o.files.map { it.name }, null, "You", d, problem = o.problem)
        }
        return rows
    }

    /** Claude's messages (not a thumbs-up) written after [readUpTo]: the Claude tab's red number counts only these (Tj, CH16: "only ... new Claude CHAT responses"). */
    fun unread(msgs: Collection<Msg>, readUpTo: Long): Int = msgs.count { !it.fromTj && !it.isDone && it.at > readUpTo }

    /** Claude's newest message time: what "read" moves up to when the chat is looked at. */
    fun newestFromClaude(msgs: Collection<Msg>): Long = msgs.filter { !it.fromTj && !it.isDone }.maxOfOrNull { it.at } ?: 0L

    // ---- the line above the thread -----------------------------------------------------------------------------------------------------------------------------------------------------------

    enum class Tone { ONLINE, WORKING, WAKING, OFFLINE, PROBLEM, UNKNOWN }

    /** Who is there, in a few words; [replying] = Claude has seen a message of Tj's and has not answered it yet (the "typing" bubble). */
    data class Presence(val tone: Tone, val line: String, val replying: Boolean = false)

    /** From the seat and the thread (pure). [seat] null = not read yet. */
    fun presence(seat: Seat?, rows: List<Row>, now: Long): Presence {
        if (seat == null) return Presence(Tone.UNKNOWN, "Checking whether Claude is there")
        val waiting = rows.filter { it.fromTj && !it.old && (it.delivery == Delivery.SENT || it.delivery == Delivery.SEEN) }
        if (seat.live(now)) {
            val replying = waiting.any { it.delivery == Delivery.SEEN }
            return when {
                seat.state == "working" && seat.note.isNotBlank() -> Presence(Tone.WORKING, "working: ${seat.note}", replying)
                replying -> Presence(Tone.ONLINE, "online, replying", true)
                else -> Presence(Tone.ONLINE, "online")
            }
        }
        val gone = if (seat.beat > 0) " (last here ${DashboardModel.ago(now - seat.beat)})" else ""
        if (waiting.isEmpty()) return Presence(Tone.OFFLINE, "offline$gone: your next message wakes it")
        val first = waiting.minOf { it.at }
        // Fires since a session last took the seat that brought nobody (tools/chat_wake.py backs off after 3 in an hour).
        val idle = seat.fires.count { it > seat.since && now - it < 3_600_000L && it >= first - 3_600_000L }
        return when {
            seat.wakeAt >= first - 5_000L && seat.wakeResult.startsWith("failed") -> Presence(Tone.PROBLEM, "could not be woken: " + seat.wakeResult.removePrefix("failed: ").take(120))
            idle >= 3 -> Presence(Tone.PROBLEM, "not answering 3 wakes this hour (out of usage?). Your messages wait on GitHub and are answered when it is back")
            seat.wakeAt >= first - 5_000L && seat.wakeResult.startsWith("fired") -> Presence(Tone.WAKING, "waking up (woken ${DashboardModel.ago(now - seat.wakeAt)})")
            else -> Presence(Tone.WAKING, "offline$gone: your message wakes it")
        }
    }

    // ---- the old app's chat (before 2026-10-19), kept on the phone and shown read only --------------------------------------------------------------------------------------------------------

    /** A message of the old chat (`chat/` on `cloud-config`), as the old app stored it on the phone. */
    @Serializable
    data class OldMsg(val id: String, val at: Long, val from: String, val text: String, val replyTo: String? = null, val ack: String? = null, val files: List<String> = emptyList()) {
        val fromTj: Boolean get() = from == "tj"
    }

    fun oldRows(old: Collection<OldMsg>): List<Row> {
        val acked = old.mapNotNullTo(HashSet()) { it.ack }
        return old.filter { it.ack == null }.distinctBy { it.id }.sortedBy { it.at }.map { m ->
            Row(
                "old-" + m.id, m.at, m.fromTj, m.text, m.files.map { it.substringAfterLast('/').substringAfter('-') },
                author = if (m.fromTj) "You" else m.from.removePrefix("claude:"), liked = m.fromTj && m.id in acked, old = true,
            )
        }
    }
}
