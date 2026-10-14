package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cloud.CloudClient
import com.tjshea.vigilant.data.cloud.CloudConfig
import com.tjshea.vigilant.data.cloud.CloudLatest
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.zip.GZIPOutputStream

/** The GitHub token (Contents + Actions on this repository), sealed by the Keystore like the Kalshi key; [tail] is the last four characters, for the Settings line. */
@Serializable
data class CloudToken(val sealed: String = "", val tail: String = "")

/**
 * The phone's side of the GitHub cloud scan (Tj, 2026-10-14: "use the phone only to set the settings and retrieve the scanned results. GitHub will provide all the power", and "save bets and bids and
 * history permanently to GitHub and automatically so Claude can analyze it"). Three jobs, each a loop that sleeps when it has nothing to do:
 *
 *  1. **Read** the engine's list ([CloudClient.pollStatus], an ETag read every [POLL_MS]; the list itself only when its scan time changed) into [latest], where [com.tjshea.vigilant.data.cloud.CloudScanner]
 *     hands it to the Feed, the sheets and the auto-bet as a scan of their own. Only while Settings › Cloud scan is on.
 *  2. **Write** the settings the engine runs under (`cloud-config` branch) whenever they change, and start the engine when it is not running.
 *  3. **Save** every bet, bid and the scan study's day files to the `phone-data` branch of the repository (never rewritten), every [SAVE_EVERY_MS], whenever a token is saved: Claude reads them from there.
 *
 * The token is only ever sent to api.github.com. It is never logged, shown, or put in a backup.
 */
class CloudLink(private val c: AppContainer) {
    val latest = CloudLatest()

    data class Ui(
        val hasToken: Boolean = false,
        val tokenTail: String = "",
        /** One line for Settings: what the engine and the link are doing. */
        val line: String = "",
        val note: String? = null,
        val listAgeSec: Long? = null,
        val rows: Int = 0,
        val saveLine: String = "",
        val rateRemaining: Int? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    private val store = JsonFileStore(File(c.app.filesDir, "cloud_token.json"), CloudToken.serializer(), { CloudToken() }, c.json)
    @Volatile private var tokenPlain: String? = null
    private val client = CloudClient(c.http, { token() })
    private val uploaded = JsonFileStore(File(c.app.filesDir, "cloud_uploaded.json"), kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()), { emptyMap() }, c.json)
    private var lastPushedKey: String? = null
    private var lastRev = 0L
    private var lastStartAskedAt = 0L
    private var feedFetchedFor = 0L

    private fun token(): String? {
        tokenPlain?.let { return it }
        val t = store.flow.value?.takeIf { it.sealed.isNotBlank() } ?: return null
        return runCatching { c.openSecret(t.sealed) }.getOrNull()?.also { tokenPlain = it }
    }

    /** Saves [token] (sealed) and says what GitHub thinks of it. */
    suspend fun saveToken(token: String): String {
        val t = token.trim()
        if (t.length < 20) return "That does not look like a GitHub token."
        val sealed = runCatching { c.sealSecret(t) }.getOrNull() ?: return "This phone could not seal the token in its secure storage, so it was not saved."
        store.update { CloudToken(sealed, t.takeLast(4)) }
        tokenPlain = t
        _ui.update { it.copy(hasToken = true, tokenTail = t.takeLast(4)) }
        return test()
    }

    suspend fun removeToken() {
        store.update { CloudToken() }
        tokenPlain = null
        _ui.update { it.copy(hasToken = false, tokenTail = "", note = "Token removed.") }
    }

    /** Reads the engine's heartbeat once and says in words how it went. */
    suspend fun test(): String {
        val msg = when (val p = client.pollStatus(null)) {
            is CloudClient.Poll.Fresh -> engineLine(p.value, System.currentTimeMillis())
            is CloudClient.Poll.NotModified -> "Token works."
            is CloudClient.Poll.Failed -> if (p.why.startsWith("not found")) "The token works, but the engine has not published yet. Turn Cloud scan on to start it." else p.why
        }
        _ui.update { it.copy(note = msg) }
        return msg
    }

    /** Asks GitHub to start the engine now (Settings › Cloud scan › Start). */
    suspend fun startEngine(): String {
        val why = client.startEngine()
        val msg = why ?: "Asked GitHub to start the engine. It publishes its first list in a few minutes."
        lastStartAskedAt = System.currentTimeMillis()
        _ui.update { it.copy(note = msg) }
        return msg
    }

    private fun engineLine(s: com.tjshea.vigilant.data.cloud.CloudStatus, now: Long): String {
        val beat = ((now - s.heartbeatMs) / 1000L).coerceAtLeast(0L)
        return when {
            s.idle -> "Engine idle (the switch is off)."
            beat > ENGINE_DEAD_S -> "Engine last heard ${beat / 60} min ago."
            else -> "Engine running: ${s.rows} bets, pass ${s.passes}, ${s.sources.size} feeds" + (s.problem?.let { ", problem: $it" } ?: "") + "."
        }
    }

    private fun hasToken() = token() != null

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching { store.read() }
            store.flow.value?.takeIf { it.sealed.isNotBlank() }?.let { t -> _ui.update { it.copy(hasToken = true, tokenTail = t.tail) } }
            runCatching { uploaded.read() }
        }
        // 1. read
        scope.launch(Dispatchers.IO) {
            var etag: String? = null
            while (isActive) {
                val s = c.settingsStore.flow.value
                if (s == null || !s.cloudScan || !hasToken()) { delay(3_000L); continue }
                var wait = POLL_MS
                when (val p = client.pollStatus(etag)) {
                    is CloudClient.Poll.Fresh -> {
                        etag = p.etag
                        latest.status = p.value
                        latest.why = null
                        if (p.value.computedAtMs > 0L && p.value.computedAtMs != feedFetchedFor) fetch(p.value.computedAtMs)
                        maybeStart(p.value, s)
                    }
                    is CloudClient.Poll.NotModified -> latest.why = null
                    is CloudClient.Poll.Failed -> {
                        latest.why = p.why
                        if (p.why.startsWith("not found")) maybeStart(null, s)
                        wait = (p.retryAfterMs ?: 20_000L).coerceIn(5_000L, 15 * 60_000L)
                    }
                }
                publishUi(s)
                delay(wait)
            }
        }
        // 2. write
        scope.launch(Dispatchers.IO) {
            c.settingsStore.flow.filterNotNull().debounce(1_500L).collect { s -> pushSettings(s) }
        }
        scope.launch(Dispatchers.IO) {
            // A failed write is tried again; settings are also re-sent every 10 minutes so the engine always has them (a new job reads them first thing).
            while (isActive) { delay(RETRY_PUSH_MS); c.settingsStore.flow.value?.let { pushSettings(it) } }
        }
        // 3. save
        scope.launch(Dispatchers.IO) {
            delay(150_000L)
            while (isActive) {
                if (hasToken() && (c.settingsStore.flow.value?.cloudSave != false)) runCatching { saveHistory() }
                delay(SAVE_EVERY_MS)
            }
        }
    }

    private suspend fun fetch(computedAt: Long) {
        when (val f = client.fetchFeed()) {
            is CloudClient.Poll.Fresh -> {
                feedFetchedFor = f.value.computedAtMs
                latest.put(f.value, System.currentTimeMillis())
                c.adoptCloud()
            }
            is CloudClient.Poll.Failed -> latest.why = f.why
            else -> {}
        }
    }

    private fun publishUi(s: ScanSettings) {
        val now = System.currentTimeMillis()
        val f = latest.feed
        val st = latest.status
        val line = when {
            latest.why != null && f == null -> latest.why!!
            st != null -> engineLine(st, now)
            else -> "Waiting for the engine."
        }
        _ui.update { it.copy(line = line, listAgeSec = f?.let { x -> ((now - x.computedAtMs) / 1000L).coerceAtLeast(0L) }, rows = f?.rows?.size ?: 0, rateRemaining = client.rateRemaining) }
    }

    /** Starts the engine when the switch is on and no heartbeat has been heard for a while (once in 15 minutes at most; the engine's own schedule is the backstop). */
    private suspend fun maybeStart(st: com.tjshea.vigilant.data.cloud.CloudStatus?, s: ScanSettings) {
        val now = System.currentTimeMillis()
        val dead = st == null || now - st.heartbeatMs > ENGINE_DEAD_S * 1000L
        if (dead && now - lastStartAskedAt > 15 * 60_000L) {
            lastStartAskedAt = now
            val why = client.startEngine()
            c.eventLog.info("CLOUD", "engine not heard from; asked GitHub to start it" + (why?.let { ": $it" } ?: ""))
        }
    }

    /** A switch-off the engine has not been told yet (kept in a file, so a restart before it was sent does not leave the engine running for a phone that is off). */
    private val offPending = File(c.app.filesDir, "cloud_off_pending")
    private var lastOn: Boolean? = null

    private suspend fun pushSettings(s: ScanSettings) {
        if (!hasToken()) return
        if (lastOn == true && !s.cloudScan) runCatching { offPending.writeText("1") }
        lastOn = s.cloudScan
        // Off and nothing owed: the engine is left alone (no request is made for a phone that never used the cloud scan).
        if (!s.cloudScan && !offPending.exists()) return
        val key = (if (s.cloudScan) "on" else "off") + c.json.encodeToString(ScanSettings.serializer(), s).hashCode()
        if (key == lastPushedKey) return
        val rev = maxOf(System.currentTimeMillis(), lastRev + 1)
        val why = client.writeConfig(CloudConfig(rev = rev, on = s.cloudScan, settings = s, device = android.os.Build.MODEL.orEmpty().take(40)))
        if (why == null) {
            lastPushedKey = key; lastRev = rev
            if (!s.cloudScan) runCatching { offPending.delete() }
            _ui.update { it.copy(note = if (s.cloudScan) "Settings sent to the engine." else "Cloud scan off: the engine will stop.") }
        } else { c.eventLog.warn("CLOUD", "settings not sent: $why"); _ui.update { it.copy(note = "Settings not sent: $why") } }
    }

    /** Saves the bet and bid day files and the scan study's days to `phone-data`; each file is sent again only when it changed. */
    suspend fun saveHistory(): String {
        if (!hasToken()) return "no token"
        c.syncBetArchive(false)
        var sent = 0
        var failed: String? = null
        for (f in c.betArchive.pendingMirror("github").take(MAX_PER_PASS)) {
            val why = client.putFile(CloudClient.DATA_BRANCH, "phone/bets/${f.name}", f.readBytes(), "${f.name} ${java.time.Instant.now()}")
            if (why != null) { failed = why; break }
            c.betArchive.markMirrored(f, "github"); sent++
            delay(1_200L)
        }
        if (failed == null) {
            val known = runCatching { uploaded.read() }.getOrDefault(emptyMap())
            val studyDir = File(c.app.filesDir, "study")
            val files = studyDir.listFiles { x -> x.isFile && x.name.startsWith("study-") && x.name.endsWith(".jsonl") }?.sortedBy { it.name }.orEmpty()
            for (f in files) {
                if (sent >= MAX_PER_PASS) break
                val sig = "${f.length()}:${f.lastModified()}"
                if (known[f.name] == sig) continue
                val gz = withContext(Dispatchers.IO) { gzip(f) }
                if (gz.size > MAX_FILE_BYTES) continue
                val why = client.putFile(CloudClient.DATA_BRANCH, "phone/study/${f.name}.gz", gz, "${f.name} ${java.time.Instant.now()}")
                if (why != null) { failed = why; break }
                uploaded.update { it + (f.name to sig) }; sent++
                delay(1_200L)
            }
        }
        val line = "Saved to GitHub: $sent file(s) just now" + (failed?.let { ", stopped: $it" } ?: "") + " (${java.time.LocalTime.now().withNano(0)})"
        _ui.update { it.copy(saveLine = line) }
        if (sent > 0 || failed != null) c.eventLog.info("CLOUD", line)
        return line
    }

    private fun gzip(f: File): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        GZIPOutputStream(out).use { g -> f.inputStream().use { it.copyTo(g) } }
        return out.toByteArray()
    }

    companion object {
        const val POLL_MS = 5_000L
        const val SAVE_EVERY_MS = 15 * 60_000L
        const val RETRY_PUSH_MS = 10 * 60_000L
        const val ENGINE_DEAD_S = 240L
        const val MAX_PER_PASS = 40
        const val MAX_FILE_BYTES = 20_000_000
    }
}
