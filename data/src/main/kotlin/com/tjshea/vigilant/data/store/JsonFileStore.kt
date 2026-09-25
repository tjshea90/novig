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
import java.io.File

/**
 * One JSON document on disk (settings, tracked bets), with three guarantees:
 *
 *  - **Atomic writes.** Written to `<name>.tmp`, then renamed over the real file. A process killed
 *    mid-write leaves the old file intact, never a half-written one.
 *  - **Serialized updates.** [update] holds a mutex across read-modify-write, so two quick taps
 *    (e.g. "track bet" twice) can't lose one of the writes.
 *  - **Corruption-tolerant reads.** An unreadable file is moved aside to `<name>.corrupt` (kept,
 *    not deleted) and the default is used, instead of crashing the app on launch.
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
                file.renameTo(File(file.parentFile, file.name + ".corrupt"))
                default()
            }
        }
        state.value = loaded
        return loaded
    }

    private suspend fun writeLocked(value: T) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse rename-over; fall back to delete + rename.
            file.delete()
            check(tmp.renameTo(file)) { "Could not save ${file.name}" }
        }
    }
}
