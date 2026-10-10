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
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<T?>(null)

    /** Null until the first [read]. */
    val flow: StateFlow<T?> = state.asStateFlow()

    suspend fun read(): T = mutex.withLock { loadLocked() }

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val next = transform(loadLocked())
        writeLocked(next)
        state.value = next
        next
    }

    private suspend fun loadLocked(): T {
        state.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext default()
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrElse {
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
            java.io.BufferedOutputStream(fos, 64 * 1024).use { out -> @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class) json.encodeToStream(serializer, value, out) }
            fos.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse rename-over; fall back to delete + rename.
            file.delete()
            check(tmp.renameTo(file)) { "Could not save ${file.name}" }
        }
    }
}
