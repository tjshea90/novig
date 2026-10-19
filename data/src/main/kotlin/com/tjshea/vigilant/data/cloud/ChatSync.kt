package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream

/** What the phone keeps of the app chat (`chat_v2.json`). */
@Serializable
data class ChatState(
    /** Both sides' messages read from GitHub (and Tj's once GitHub has them), the last [ChatSync.KEEP_DAYS] days. */
    val msgs: List<CloudChat.Msg> = emptyList(),
    /** Tj's messages Claude's session has seen (their receipts). */
    val seen: List<String> = emptyList(),
    /** Tj's messages not on GitHub yet, oldest first. */
    val outbox: List<CloudChat.Outgoing> = emptyList(),
    /** Day folder -> its git sha when it was last read in full: an unchanged folder is not read again. */
    val days: Map<String, String> = emptyMap(),
    /** The oldest day read; older days only when asked ("Show earlier"). Blank = nothing read yet. */
    val fromDay: String = "",
    /** The oldest day GitHub has. */
    val oldestDay: String = "",
    /** The `chat` branch's commit this phone has read in full. */
    val headSha: String = "",
    /** Claude's messages up to this time are read (the Claude tab's red number counts the rest). */
    val readUpTo: Long = 0L,
    /** Claude's messages already announced by a notification (one each, never again). */
    val notified: List<String> = emptyList(),
    /** The first full read is done: what was there before is not announced. */
    val primed: Boolean = false,
    /** Tj's "New chat" times: the screen starts after the newest one, the earlier messages one tap away. */
    val breaks: List<Long> = emptyList(),
    /** "Clear chat": messages before this are not shown on this phone (GitHub keeps them). */
    val hiddenBefore: Long = 0L,
) {
    val unread: Int get() = CloudChat.unread(msgs, readUpTo)
}

/**
 * The phone's side of the app chat (CR, Tj 2026-10-19: "work like a real chat without the problems I mentioned"). Two loops use it ([CloudLink][com.tjshea.vigilant]):
 *  - the sender ([sendPending]): Tj's message is on this phone (with its attachments copied into the app's storage) from the moment he taps Send, and is tried again with a growing pause until GitHub
 *    has it; the attachments go first, the message last, each a file of its own that is created, never replaced, so a try whose answer was lost is found by its id and nothing is sent twice;
 *  - the reader ([poll]): one look at the `chat` branch's head (a free 304 while nothing changed), then only the day folders whose git sha changed, and in them only the files not read before,
 *    all at that one commit. A read that fails is read again next time; a receipt is only a file's name. The seat (who answers) is read while the chat is open.
 * Nothing is dropped by age: an unanswered message stays shown as waiting until Claude answers it, and Claude's tools answer it however late (tools/chat.py).
 */
class ChatSync(
    private val client: CloudClient,
    private val store: JsonFileStore<ChatState>,
    /** Where picked attachments are kept (`<message id>/<n>-<name>`). */
    private val filesDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: java.util.Random = java.security.SecureRandom(),
) {
    /** One of Tj's picked attachments: its name, type and a way to read it (the screen's content resolver). */
    class Pick(val name: String, val type: String?, val open: () -> InputStream?)

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private val _seat = MutableStateFlow<CloudChat.Seat?>(null)
    val seat: StateFlow<CloudChat.Seat?> = _seat.asStateFlow()
    private val _problem = MutableStateFlow<String?>(null)
    /** Why the last read did not get through, in words (null = it did). */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** Set by the chat screen: while it is open the thread is looked at every few seconds, the seat with it, and what arrives is read at once. */
    @Volatile var open: Boolean = false
        set(v) { field = v; if (v) { readWake.tryEmit(Unit) } }

    private var headEtag: String? = null
    private var seatEtag: String? = null
    private val sendLock = Mutex()
    private val readLock = Mutex()
    private val sendWake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val readWake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    suspend fun load() {
        _state.value = store.read()
        prune()
    }

    private suspend fun update(f: (ChatState) -> ChatState): ChatState = store.update(f).also { _state.value = it }

    // ---- sending -------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Puts a message on this phone's send queue (it shows at once as Sending) and wakes the sender. Null = queued; otherwise why not (nothing was queued): a key-shaped text, an attachment too big
     * or unreadable.
     */
    suspend fun send(text: String, picks: List<Pick> = emptyList()): String? {
        val t = text.trim()
        if (t.isEmpty() && picks.isEmpty()) return "Nothing to send."
        if (CloudChat.looksSecret(t)) return "That looks like a key or token. Keys must never go in the chat (the repo is public)."
        if (picks.size > CloudChat.MAX_FILES) return "At most ${CloudChat.MAX_FILES} attachments a message."
        val now = clock()
        val id = CloudChat.newId(now, random)
        val dir = File(filesDir, id)
        val files = ArrayList<CloudChat.LocalFile>()
        val why = withContext(Dispatchers.IO) {
            picks.forEachIndexed { i, p ->
                val dest = File(dir, "${i + 1}-${CloudChat.safeName(p.name)}")
                val size = runCatching { copyCapped(p, dest) }.getOrElse { -1L }
                when {
                    size < 0 -> return@withContext "Could not read ${p.name}."
                    size > CloudChat.MAX_FILE_BYTES -> return@withContext "${p.name} is over ${CloudChat.MAX_FILE_BYTES / 1_000_000} MB."
                }
                files += CloudChat.LocalFile(dest.path, p.name.ifBlank { dest.name }, size, p.type)
            }
            null
        }
        if (why != null) { dir.deleteRecursively(); return why }
        update { s -> s.copy(outbox = s.outbox + CloudChat.Outgoing(id, now, t, files)) }
        sendWake.tryEmit(Unit)
        return null
    }

    /** Copies at most one byte over the limit (so an oversized file is known without reading all of it); the size, or -1 when it could not be opened. */
    private fun copyCapped(p: Pick, dest: File): Long {
        dest.parentFile?.mkdirs()
        val input = p.open() ?: return -1L
        var total = 0L
        input.use { inp ->
            dest.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total > CloudChat.MAX_FILE_BYTES) break
                }
            }
        }
        return total
    }

    /** Tap Retry: tries again now, stopped or not. */
    suspend fun retry(id: String) {
        update { s -> s.copy(outbox = s.outbox.map { if (it.id == id) it.copy(stuck = false, nextTryAt = 0L) else it }) }
        sendWake.tryEmit(Unit)
    }

    /** Tap Delete on a message that is not on GitHub yet: it leaves this phone. A message GitHub has stays (Claude may already be reading it). */
    suspend fun discard(id: String) {
        val gone = state.value.outbox.firstOrNull { it.id == id } ?: return
        update { s -> s.copy(outbox = s.outbox.filterNot { it.id == id }) }
        withContext(Dispatchers.IO) { File(filesDir, gone.id).deleteRecursively() }
    }

    /**
     * Sends what is due, oldest first: a message waiting for its next try holds the later ones back (so Claude reads them in order), a stopped one does not. Returns when the next try is due
     * (0 = nothing waits).
     */
    suspend fun sendPending(): Long = sendLock.withLock {
        for (o0 in state.value.outbox) {
            val o = state.value.outbox.firstOrNull { it.id == o0.id } ?: continue
            if (o.stuck) continue
            if (o.nextTryAt > clock()) break
            if (!sendOne(o)) break
        }
        state.value.outbox.filter { !it.stuck }.minOfOrNull { it.nextTryAt } ?: 0L
    }

    /** One message: its attachments, then itself. True = it is on GitHub (or stopped and out of the way); false = it waits for its next try. */
    private suspend fun sendOne(o: CloudChat.Outgoing): Boolean {
        var cur = o
        for (i in o.files.indices) {
            if (i in cur.uploaded) continue
            val f = o.files[i]
            val bytes = withContext(Dispatchers.IO) { runCatching { File(f.path).readBytes() }.getOrNull() }
            if (bytes == null) { failed(o.id, CloudClient.Create.Failed("${f.name} is no longer on the phone: delete this message and send it again", retry = false)); return true }
            when (val r = client.createFile(CloudChat.BRANCH, CloudChat.filePath(o.id, i + 1, f.name), bytes, "chat: attachment ${i + 1} of ${o.id}")) {
                CloudClient.Create.Created, CloudClient.Create.Exists -> cur = update { s -> s.copy(outbox = s.outbox.map { if (it.id == o.id) it.copy(uploaded = (it.uploaded + i).distinct()) else it }) }
                    .outbox.firstOrNull { it.id == o.id } ?: return true
                is CloudClient.Create.Failed -> { failed(o.id, r); return !r.retry }
            }
        }
        return when (val r = client.createFile(CloudChat.BRANCH, CloudChat.msgPath(o.id, o.at), CloudChat.encode(o).toByteArray(Charsets.UTF_8), "chat: Tj ${o.id}")) {
            CloudClient.Create.Created, CloudClient.Create.Exists -> {
                update { s -> s.copy(outbox = s.outbox.filterNot { it.id == o.id }, msgs = (s.msgs.filterNot { it.id == o.id } + CloudChat.sent(o))) }
                readWake.tryEmit(Unit)   // the receipt and the answer come next
                true
            }
            is CloudClient.Create.Failed -> { failed(o.id, r); !r.retry }
        }
    }

    private suspend fun failed(id: String, r: CloudClient.Create.Failed) {
        val now = clock()
        update { s ->
            s.copy(outbox = s.outbox.map {
                if (it.id != id) it else {
                    val tries = it.tries + 1
                    val pause = r.retryAfterMs ?: backoff(tries)
                    it.copy(tries = tries, nextTryAt = now + pause.coerceIn(1_000L, MAX_PAUSE_MS), problem = r.why, stuck = !r.retry)
                }
            })
        }
    }

    /** 2 s, 4 s, 8 s ... at most a minute: a phone that lost its connection sends within a minute of getting it back. */
    private fun backoff(tries: Int): Long = (1_000L shl tries.coerceIn(1, 6)).coerceAtMost(MAX_PAUSE_MS)

    /** Waits until something is to be sent, or [ms]. */
    suspend fun napSender(ms: Long) { if (ms > 0) withTimeoutOrNull(ms) { sendWake.first() } }

    // ---- reading -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** What a read brought: how many messages, and Claude's new ones to announce (none while the chat is open, none on the first read). */
    data class Read(val messages: Int, val announce: List<CloudChat.Msg>)

    suspend fun poll(withSeat: Boolean = open): Read = readLock.withLock {
        if (withSeat) readSeat()
        val head = when (val p = client.headSha(CloudChat.BRANCH, headEtag)) {
            CloudClient.Poll.NotModified -> { _problem.value = null; return@withLock Read(0, emptyList()) }
            is CloudClient.Poll.Failed -> { _problem.value = p.why; return@withLock Read(0, emptyList()) }
            is CloudClient.Poll.Fresh -> p
        }
        if (head.value == state.value.headSha && state.value.primed) { headEtag = head.etag; _problem.value = null; return@withLock Read(0, emptyList()) }
        val got = readAt(head.value)
        if (got == null) return@withLock Read(0, emptyList())   // something failed: the next look reads again (no ETag kept)
        headEtag = head.etag
        _problem.value = null
        val s = state.value
        val claude = got.filter { !it.fromTj && !it.isDone }
        val announce = if (!s.primed || open) emptyList() else claude.filter { it.id !in s.notified }
        update { st ->
            st.copy(
                primed = true, headSha = head.value,
                notified = (st.notified + claude.map { it.id }).distinct().takeLast(MAX_NOTIFIED),
                readUpTo = if (open || !st.primed) maxOf(st.readUpTo, CloudChat.newestFromClaude(st.msgs)) else st.readUpTo,
            )
        }
        Read(got.size, announce)
    }

    /** Reads the changed day folders at [head]; the new messages, or null when a read failed (what was read is kept). */
    private suspend fun readAt(head: String): List<CloudChat.Msg>? {
        val days = when (val p = client.listEntries(head, CloudChat.MSGS)) {
            is CloudClient.Poll.Fresh -> p.value.filter { it.type == "dir" && DAY.matches(it.name) }.sortedBy { it.name }
            is CloudClient.Poll.Failed -> { _problem.value = p.why; return null }
            CloudClient.Poll.NotModified -> return emptyList()
        }
        val s0 = state.value
        val from = s0.fromDay.ifBlank { days.takeLast(INITIAL_DAYS).firstOrNull()?.name ?: CloudChat.dayOf(clock()) }
        update { it.copy(fromDay = from, oldestDay = days.firstOrNull()?.name.orEmpty()) }
        val got = ArrayList<CloudChat.Msg>()
        var complete = true
        for (d in days) {
            if (d.name < from || state.value.days[d.name] == d.sha) continue
            val entries = when (val p = client.listEntries(head, "${CloudChat.MSGS}/${d.name}")) {
                is CloudClient.Poll.Fresh -> p.value
                is CloudClient.Poll.Failed -> { _problem.value = p.why; complete = false; continue }
                CloudClient.Poll.NotModified -> continue
            }
            val known = state.value.msgs.mapTo(HashSet()) { it.id }
            val seen = entries.filter { it.name.endsWith(CloudChat.SEEN_SUFFIX) }.map { it.name.removeSuffix(CloudChat.SEEN_SUFFIX) }
            val fresh = ArrayList<CloudChat.Msg>()
            var dayDone = true
            for (e in entries) {
                if (e.type != "file" || !e.name.endsWith(".json") || e.name.endsWith(CloudChat.SEEN_SUFFIX)) continue
                if (e.name.removeSuffix(".json") in known) continue
                val text = client.readFile(head, "${CloudChat.MSGS}/${d.name}/${e.name}")
                if (text == null) { dayDone = false; continue }
                CloudChat.decode(text)?.let { fresh += it }
            }
            if (!dayDone) { complete = false; _problem.value = "some messages could not be read; trying again" }
            update { st ->
                st.copy(
                    msgs = (st.msgs + fresh).distinctBy { it.id },
                    seen = (st.seen + seen).distinct(),
                    days = if (dayDone) st.days + (d.name to d.sha) else st.days,
                )
            }
            got += fresh
        }
        return if (complete) got else null.also { if (got.isNotEmpty()) pendingAnnounce += got }
            ?: run { null }
    }

    /** Messages read in a pass that did not finish: announced with the next one that does. */
    private val pendingAnnounce = ArrayList<CloudChat.Msg>()

    private suspend fun readSeat() {
        when (val p = client.readText(CloudChat.SEAT_BRANCH, CloudChat.SEAT_FILE, seatEtag)) {
            is CloudClient.Poll.Fresh -> { seatEtag = p.etag; _seat.value = CloudChat.parseSeat(p.value) ?: _seat.value }
            else -> Unit
        }
    }

    /** "Show earlier messages": reads the [INITIAL_DAYS] days before the oldest one read. */
    suspend fun loadEarlier() {
        val s = state.value
        if (s.fromDay.isBlank() || s.oldestDay.isBlank() || s.oldestDay >= s.fromDay) return
        val earlier = java.time.LocalDate.parse(s.fromDay).minusDays(INITIAL_DAYS.toLong()).toString()
        update { it.copy(fromDay = maxOf(earlier, it.oldestDay), headSha = "") }
        headEtag = null
        readWake.tryEmit(Unit)
    }

    /** Waits until the chat is opened, a message was sent, or [ms]. */
    suspend fun napReader(ms: Long) { withTimeoutOrNull(ms) { readWake.first() } }

    /** Wakes both loops (the app came to the front, a pull to refresh). */
    fun poke() { readWake.tryEmit(Unit); sendWake.tryEmit(Unit) }

    /** How long the reader may rest: a few seconds while the chat is open or an answer is awaited, longer otherwise. Every look is a free 304 while nothing changed. */
    fun pace(onScreen: Boolean, now: Long = clock()): Long {
        val s = state.value
        val awaited = s.outbox.isNotEmpty() || s.msgs.any { it.fromTj && now - it.at < AWAIT_MS && s.msgs.none { c -> !c.fromTj && it.id in c.replyTo } }
        return when {
            open -> OPEN_MS
            awaited -> if (onScreen) AWAIT_ON_SCREEN_MS else AWAIT_OFF_SCREEN_MS
            onScreen -> ON_SCREEN_MS
            else -> OFF_SCREEN_MS
        }
    }

    // ---- the screen's own --------------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** The chat was looked at: Claude's messages so far are read (the red number goes). */
    suspend fun markRead() {
        val newest = CloudChat.newestFromClaude(state.value.msgs)
        if (newest > state.value.readUpTo) update { it.copy(readUpTo = newest) }
    }

    /** "New chat": the screen starts empty; what came before is one tap away. */
    suspend fun newChat() {
        val now = clock()
        val s = state.value
        if (s.msgs.none { it.at >= (s.breaks.maxOrNull() ?: 0L) && it.at >= s.hiddenBefore } && s.outbox.isEmpty()) return
        update { it.copy(breaks = (it.breaks + now).takeLast(50)) }
    }

    /** "Clear chat": nothing before now is shown on this phone (GitHub keeps it all). */
    suspend fun clear() {
        val now = clock()
        update { it.copy(hiddenBefore = now, breaks = emptyList()) }
    }

    /** Keeps the phone's copy to [KEEP_DAYS] days (GitHub keeps everything) and attachments of sent messages to [KEEP_FILES_DAYS] days. */
    private suspend fun prune() {
        val now = clock()
        val cut = now - KEEP_DAYS * 86_400_000L
        val s = state.value
        if (s.msgs.any { it.at < cut }) update { st ->
            val msgs = st.msgs.filter { it.at >= cut }
            val ids = msgs.mapTo(HashSet()) { it.id }
            val cutDay = CloudChat.dayOf(cut)
            st.copy(msgs = msgs, seen = st.seen.filter { it in ids }, days = st.days.filterKeys { it >= cutDay }, fromDay = if (st.fromDay.isNotBlank() && st.fromDay < cutDay) cutDay else st.fromDay)
        }
        withContext(Dispatchers.IO) {
            val waiting = state.value.outbox.mapTo(HashSet()) { it.id }
            filesDir.listFiles()?.forEach { d ->
                val at = Regex("""^t(\d{13})-""").find(d.name)?.groupValues?.get(1)?.toLongOrNull() ?: return@forEach
                if (d.name !in waiting && now - at > KEEP_FILES_DAYS * 86_400_000L) d.deleteRecursively()
            }
        }
    }

    /** The phone's own copy of an attachment of Tj's (for a thumbnail), or null. */
    fun localFile(id: String, n: Int, name: String): File? = File(File(filesDir, id), "$n-${CloudChat.safeName(name)}").takeIf { it.isFile }

    companion object {
        val DAY = Regex("""^\d{4}-\d{2}-\d{2}$""")
        /** Days read on a phone that has nothing yet; earlier ones on "Show earlier". */
        const val INITIAL_DAYS = 3
        const val KEEP_DAYS = 60L
        const val KEEP_FILES_DAYS = 30L
        const val MAX_NOTIFIED = 500
        const val MAX_PAUSE_MS = 60_000L
        const val OPEN_MS = 3_000L
        const val AWAIT_MS = 2 * 3_600_000L
        const val AWAIT_ON_SCREEN_MS = 5_000L
        const val AWAIT_OFF_SCREEN_MS = 15_000L
        const val ON_SCREEN_MS = 15_000L
        const val OFF_SCREEN_MS = 60_000L
    }
}
