package com.tjshea.vigilant.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * One JSON document on disk (settings, tracked bets), with three guarantees:
 *
 *  - **Atomic writes.** Written to `<name>.tmp`, then renamed over the real file. A process killed
 *    mid-write leaves the old file intact, never a half-written one.
 *  - **Serialized updates.** [update] holds a mutex across read-modify-write, so two quick taps
 *    (e.g. "track bet" twice) can't lose one of the writes.
 *  - **Corruption-tolerant reads.** An unreadable file is moved aside to `<name>.corrupt-<time>`
 *    (kept, not deleted; each one its own file, so a second bad read never replaces the first) and
 *    the default is used, instead of crashing the app on launch.
 *
 * Writes are flushed to the disk before the rename, so a power cut right after a save can't leave
 * an empty file where the old one was.
 */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    /**
     * Write-behind (XO2, the engine's bid desk): above 0, an [update] changes the in-memory document at once and writes the file only when [writeBehindMs] have passed since the last write (and on [flush]); 0 = every
     * update writes the whole file and syncs it, as always. The desk's store was rewritten whole and synced on every post, cancel and ended bid: at 95,000 bids a day that was about 700 whole-file writes of a
     * multi-megabyte document in each pass, so a pass took 60-136 s instead of its 25 s and two engine jobs were ended by the stall watchdog with a worker in the middle of that write. A crash loses at most [writeBehindMs]
     * of changes: for the engine's bids that is harmless (a bid is post-only with its own expiry and the job takes every bid down when it ends), and a store whose records must survive a crash keeps 0.
     */
    private val writeBehindMs: Long = 0L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    /** AU1: size of the file as last read or written; a big document is saved at most every [BIG_COALESCE_MS] (see [update]). */
    @Volatile private var fileBytes = 0L
    private var flushJob: kotlinx.coroutines.Job? = null
    init { synchronized(registry) { registry.add(this) } }
    private val state = MutableStateFlow<T?>(null)
    private var dirty = false
    private var lastWriteMs = 0L

    /** Null until the first [read]. */
    val flow: StateFlow<T?> = state.asStateFlow()

    suspend fun read(): T = mutex.withLock { loadLocked() }

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val next = transform(loadLocked())
        if (writeBehindMs <= 0L && fileBytes >= BIG_BYTES) {
            // AU1 (Tj 2026-11-17: "fix the app writing large files"): a document of half a megabyte or more (the tracked bets were 8 MB, rewritten and synced on every change) is written once the
            // changes have settled for a moment, and at once when the screen goes away ([flushAll]); the in-memory copy is always current.
            state.value = next
            dirty = true
            if (flushJob?.isActive != true) flushJob = scope.launch { delay(BIG_COALESCE_MS); flush() }
        } else if (writeBehindMs <= 0L) {
            writeLocked(next)
            state.value = next
        } else {
            state.value = next
            dirty = true
            if (clock() - lastWriteMs >= writeBehindMs) persistLocked(next)
        }
        next
    }

    /** Writes what a write-behind store has not written yet (a no-op for the others, or when nothing changed). Called when a job ends. */
    suspend fun flush() = mutex.withLock {
        if (dirty) state.value?.let { persistLocked(it) }
    }

    companion object {
        const val BIG_BYTES = 512L * 1024
        const val BIG_COALESCE_MS = 2_000L
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        private val registry = java.util.Collections.newSetFromMap(java.util.WeakHashMap<JsonFileStore<*>, Boolean>())

        /** Writes every store's unwritten changes now (the screen is leaving, memory is short). */
        suspend fun flushAll() {
            val all = synchronized(registry) { registry.toList() }
            for (st in all) runCatching { st.flush() }
        }
    }

    private suspend fun persistLocked(value: T) {
        writeLocked(value)
        dirty = false
        lastWriteMs = clock()
    }

    private suspend fun loadLocked(): T {
        state.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext default()
            runCatching { fileBytes = file.length(); file.inputStream().buffered(64 * 1024).use { json.decodeFromStream(serializer, it) } }.getOrElse {
                val aside = File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())
                // Kept either way: the next save must never write over the only copy.
                if (!file.renameTo(aside)) runCatching { file.copyTo(aside, overwrite = false) }
                default()
            }
        }
        state.value = loaded
        return loaded
    }

    private suspend fun writeLocked(value: T) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        // Straight to the file in a buffer, not through a whole String and then a whole ByteArray: a big document (a bid list) otherwise made two copies of itself on
        // every save, and the garbage collector's work was felt as lag across the whole app.
        java.io.FileOutputStream(tmp).use { fos ->
            val out = java.io.BufferedOutputStream(fos, 64 * 1024)
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            json.encodeToStream(serializer, value, out)
            out.flush()
            fos.fd.sync()   // before the stream is closed: a sync on a closed file fails
        }
        fileBytes = tmp.length()
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse rename-over; fall back to delete + rename.
            file.delete()
            check(tmp.renameTo(file)) { "Could not save ${file.name}" }
        }
    }
}
