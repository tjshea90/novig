package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cloud.CloudClient
import com.tjshea.vigilant.data.cloud.CloudConfig
import com.tjshea.vigilant.data.cloud.CloudKeys
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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
        /** The engine's last heartbeat, when the phone asked for a scan last (its own clock), and why nothing arrives, for the Feed's cloud bar ([com.tjshea.vigilant.data.cloud.CloudText]). */
        val status: com.tjshea.vigilant.data.cloud.CloudStatus? = null,
        val requestedAtMs: Long = 0L,
        /** When this phone last pulled the PropLine +EV screen down (0 = never); the engine's echo is `status.propLineServedAtMs`. */
        val propLineRequestedAtMs: Long = 0L,
        val why: String? = null,
        /** What the last "Send my API keys" said (words only). */
        val keysNote: String? = null,
        /** When the read-only Novig key made for the engine was made (this phone's clock); 0 = none was made. */
        val novigKeyMadeAtMs: Long = 0L,
        /** When the Vigilant subaccount's trading key was made for sharing with the engine (0 = none) and whether it is on GitHub as secrets (TCL20c). */
        val tradeKeyMadeAtMs: Long = 0L,
        val tradeKeyOnGitHub: Boolean = false,
        val rateRemaining: Int? = null,
        /** The manual Run / Stop in words ([com.tjshea.vigilant.data.cloud.CloudManual.words]), or null when the schedule alone decides. */
        val manualWords: String? = null,
        /** When this phone last read the engine's status successfully (its clock; 0 = never): the age of everything the dashboard shows ([com.tjshea.vigilant.data.cloud.DashboardModel.link]). */
        val statusOkAtMs: Long = 0L,
        /** When the last status arrived STRAIGHT from the engine over the live link ([com.tjshea.vigilant.data.cloud.CloudLive]); 0 = not now (GitHub only). */
        val liveDirectAtMs: Long = 0L,
        /** The newest settings revision this phone wrote to GitHub and when (its clock), for the "is GitHub using my settings" line ([com.tjshea.vigilant.data.cloud.CloudText.settings]). */
        val pushedRev: Long = 0L,
        val pushedAtMs: Long = 0L,
        /** The engine's own orders and results (`trades.json`) as of the phone's last read, with the clock of that read; null before the first read. */
        val trades: com.tjshea.vigilant.data.cloud.TradeLedger? = null,
        val tradesReadAtMs: Long = 0L,
        /** The engine's bids (`desk.json`) as of the phone's last read, and when (this phone's clock). */
        val desk: com.tjshea.vigilant.data.cloud.DeskFile? = null,
        val deskReadAtMs: Long = 0L,
        /** PropLine's +EV lines at Novig, as the engine's last sweep found them (`propline-ev.json.gz`, TCL56); null = none yet. */
        val propLineEv: com.tjshea.vigilant.data.reference.PlEvFeed? = null,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /** The dashboard's graphs (TCL24b): one point per scan the engine finished, kept on the phone (a day's worth) so the lines are there when the screen opens. */
    private val historyFile = File(c.app.filesDir, "cloud_history.json")
    private val _history = MutableStateFlow(runCatching { com.tjshea.vigilant.data.cloud.DashboardModel.decode(historyFile.readText()) }.getOrDefault(emptyList()))
    val history: StateFlow<List<com.tjshea.vigilant.data.cloud.DashboardModel.Point>> = _history.asStateFlow()
    private fun recordHistory(st: com.tjshea.vigilant.data.cloud.CloudStatus) {
        val next = com.tjshea.vigilant.data.cloud.DashboardModel.record(_history.value, st)
        if (next !== _history.value) {
            _history.value = next
            runCatching { historyFile.writeText(com.tjshea.vigilant.data.cloud.DashboardModel.encode(next)) }
        }
    }

    private val store = JsonFileStore(File(c.app.filesDir, "cloud_token.json"), CloudToken.serializer(), { CloudToken() }, c.json)
    @Volatile private var tokenPlain: String? = null
    private val client = CloudClient(c.http, { token() })
    /** Claude's messages to Tj and the settings requests from the GitHub side (TCL28). */
    val inbox = CloudInbox(c, client)
    private val uploaded = JsonFileStore(File(c.app.filesDir, "cloud_uploaded.json"), kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), kotlinx.serialization.serializer<String>()), { emptyMap() }, c.json)
    private var lastPushedKey: String? = null
    private var lastRev = 0L
    private var lastStartAskedAt = 0L
    private var lastRestartAt = 0L
    /** The phone's clock when it last asked for a scan (pull to refresh, the Scan button); written into every config so a later settings push never forgets it. */
    @Volatile private var refreshRequestedAt = 0L
    /** Pull to refresh on the PropLine +EV screen (TCL89): the phone's clock when it asked for a PropLine-only sweep. */
    @Volatile private var propLineRequestedAt = 0L
    private var feedFetchedFor = 0L
    private var bidsFetchedFor = 0L
    private var evFetchedFor = 0L

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
        // The engine is told to stand down while the token still works: without it nothing could ever say so, and it would run for hours for a phone that no longer reads it.
        if (hasToken()) runCatching {
            val st = c.currentSettings()
            val rev = maxOf(System.currentTimeMillis(), lastRev + 1)
            client.writeConfig(CloudConfig(rev = rev, on = false, settings = st.copy(gates = st.gates.copy(cloudScan = false)), device = "token removed"))
            lastRev = rev
        }
        runCatching { wantedFile.writeText("0"); offPending.delete() }
        lastWanted = false
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

    /** The manual Run / Stop (Tj, 2026-10-17), kept in a file so a restart of the app does not forget it: "run,stop" as epoch milliseconds. Both lapse by themselves ([com.tjshea.vigilant.data.cloud.CloudManual]). */
    private val manualFile = File(c.app.filesDir, "cloud_manual")
    @Volatile private var manualRunUntil = 0L
    @Volatile private var manualStopUntil = 0L
    @Volatile private var manualLoaded = false
    /** Reset engine (Tj, 2026-10-17, TCL85): the phone's clock of the last request; part of the config so the engine that reads a newer value ends and a fresh runner starts. */
    @Volatile private var resetRequestedAt = 0L

    private fun loadManual() {
        if (manualLoaded) return
        manualLoaded = true
        runCatching { manualFile.readText().split(',').let { manualRunUntil = it[0].toLong(); manualStopUntil = it[1].toLong() } }
    }

    private fun manualWords() = com.tjshea.vigilant.data.cloud.CloudManual.words(CloudConfig(manualRunUntilMs = manualRunUntil, manualStopUntilMs = manualStopUntil), System.currentTimeMillis())

    private suspend fun setManual(run: Long, stop: Long): String {
        loadManual()
        manualRunUntil = run; manualStopUntil = stop
        runCatching { manualFile.writeText("$run,$stop") }
        _ui.update { it.copy(manualWords = manualWords()) }
        c.settingsStore.flow.value?.let { pushSettings(it, force = true) }
        return _ui.value.note ?: ""
    }

    /** Run now, even outside the engine's hours (Settings › Cloud scan): the engine counts as open until the next opening or 12 hours at most, and GitHub is asked to start it. The schedule is untouched. */
    suspend fun runOutsideHours(): String {
        val s = c.settingsStore.flow.value
        if (s?.cloudDrives != true) return "Switch on Settings › Cloud scan first (and un-pause scanning): the engine only runs for a phone that uses it.".also { m -> _ui.update { it.copy(note = m) } }
        val (run, stop) = com.tjshea.vigilant.data.cloud.CloudManual.runFields(System.currentTimeMillis())
        setManual(run, stop)
        val msg = startEngine()
        return if (run == 0L) msg else "Running outside its hours until " + (manualWords()?.substringAfter("until ")?.substringBefore(" (") ?: "later") + ". " + msg
    }

    /** Stop now: the engine ends, takes its own bids down and stays off until the next scheduled opening (tomorrow 8 am Eastern when it is inside its hours); a manual run outside the hours just ends. */
    suspend fun stopUntilNextOpening(): String {
        val (run, stop) = com.tjshea.vigilant.data.cloud.CloudManual.stopFields(System.currentTimeMillis())
        setManual(run, stop)
        val msg = if (stop > 0L) "Stopping the engine. It stays off until the next scheduled opening and then starts by itself." else "Ended the manual run. The engine starts again by itself at its next opening (8 am Eastern)."
        _ui.update { it.copy(note = msg) }
        return msg
    }

    /**
     * Reset engine: the running job ends (its own bids come down; keys, switches and Claude are untouched) and GitHub starts a new one on a new runner, so a new address. Without a running job the start request
     * alone does it. Needs the saved token; the engine's hours still decide whether the new job runs.
     */
    suspend fun resetEngine(): String {
        resetRequestedAt = maxOf(System.currentTimeMillis(), resetRequestedAt + 1)
        c.settingsStore.flow.value?.let { pushSettings(it, force = true) }
        val msg = startEngine()
        val words = "Reset sent: the running engine ends within seconds and a new one starts on a new runner. " + msg
        _ui.update { it.copy(note = words) }
        syncNow()
        return words
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

    /** The saved GitHub token (for the app updater's reads of a private repo), or null. */
    fun githubToken(): String? = token()

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            runCatching { store.read() }
            store.flow.value?.takeIf { it.sealed.isNotBlank() }?.let { t -> _ui.update { it.copy(hasToken = true, tokenTail = t.tail) } }
            runCatching { uploaded.read() }
            // Whether the engine's keys are on GitHub (the Live trading switch needs it) is read at start, not only after a key button was tapped.
            if (hasToken()) runCatching { refreshNovigKey() }
        }
        // 1. read
        scope.launch(Dispatchers.IO) {
            var etag: String? = null
            while (isActive) {
                val s = c.settingsStore.flow.value
                if (s == null || !s.cloudDrives || !hasToken()) { delay(IDLE_MS); continue }
                // 3 s while a scan the phone asked for runs; 5 s with the app on screen; 20 s off screen (the background auto-scan and auto-bet still get a list well inside a scan's length, and a 304 is free).
                var wait = if (refreshRequestedAt > 0L && (latest.status?.lastScanForRefreshAtMs ?: 0L) < refreshRequestedAt) POLL_PENDING_MS else if (c.onScreen) POLL_MS else POLL_BACKGROUND_MS
                // While an app update downloads the phone has enough to do (Tj, 2026-10-17: "Vigilant isn't responding" at 62% of a download on the +EV tab): the status is read every 8 s, not 2.
                if (c.updater.state.value.progress != null) wait = maxOf(wait, 8_000L)
                when (val p = client.pollStatus(etag)) {
                    is CloudClient.Poll.Fresh -> {
                        etag = p.etag
                        // An answer from GitHub is up to ~10 s old: it never replaces a newer reading that came straight from the engine.
                        latest.status?.let { cur -> if (p.value.runId != cur.runId || p.value.heartbeatMs >= cur.heartbeatMs) latest.status = p.value } ?: run { latest.status = p.value }
                        latest.statusOkAtMs = System.currentTimeMillis()
                        recordHistory(p.value)
                        latest.why = null
                        if (p.value.computedAtMs > 0L && p.value.computedAtMs != feedFetchedFor) fetch(p.value.computedAtMs)
                        if (s.bidsFromCloud && p.value.bidsAtMs > 0L && p.value.bidsAtMs != bidsFetchedFor) fetchBids()
                        if (p.value.propLineEvAtMs > 0L && p.value.propLineEvAtMs != evFetchedFor) fetchPropLineEv()
                        maybeStart(p.value, s)
                    }
                    is CloudClient.Poll.NotModified -> { latest.why = null; latest.statusOkAtMs = System.currentTimeMillis() }
                    is CloudClient.Poll.Failed -> {
                        latest.why = p.why
                        if (p.why.startsWith("not found")) maybeStart(null, s)
                        wait = (p.retryAfterMs ?: 20_000L).coerceIn(5_000L, 15 * 60_000L)
                    }
                }
                publishUi(s)
                nap(wait)
            }
        }
        // 6. the live link to the engine (TCL80, Tj 2026-10-17: "tunnel yes"): while the engine publishes a tunnel address, its CURRENT status streams in once a second, straight from the runner; GitHub's polling goes on
        // beside it and takes over the moment the stream fails (liveDirectAtMs goes back to 0 and the chip says "via GitHub"). Only an address of the exact quick-tunnel shape is ever opened.
        scope.launch(Dispatchers.IO) {
            val live = okhttp3.OkHttpClient.Builder().connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS).readTimeout(12, java.util.concurrent.TimeUnit.SECONDS).build()
            while (isActive) {
                val s = c.settingsStore.flow.value
                val url = latest.status?.liveUrl.orEmpty()
                if (s == null || !s.cloudDrives || !hasToken() || !com.tjshea.vigilant.data.cloud.CloudLive.safeUrl(url)) { liveDirectAtMs = 0L; delay(5_000L); continue }
                runCatching {
                    live.newCall(okhttp3.Request.Builder().url("$url/stream").header("Accept", "text/event-stream").build()).execute().use { r ->
                        if (!r.isSuccessful) return@use
                        val reader = com.tjshea.vigilant.data.cloud.CloudLive.SseReader()
                        val src = r.body!!.source()
                        while (isActive && !src.exhausted()) {
                            val line = src.readUtf8Line() ?: break
                            reader.feed(line)?.let { msg -> com.tjshea.vigilant.data.cloud.CloudStatus.parse(msg)?.let { st -> applyLive(st, s) } }
                        }
                    }
                }
                liveDirectAtMs = 0L
                runCatching { publishUi(s) }
                delay(3_000L)
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
        // 4. messages from Claude / the engine and settings requests from the GitHub side (TCL28): 304s are free; once a minute on screen, every 5 minutes off screen.
        scope.launch(Dispatchers.IO) {
            runCatching { inbox.load() }
            while (isActive) {
                if (hasToken()) {
                    runCatching { inbox.pollOverrides() }
                    runCatching { inbox.pollMessages() }
                }
                nap(if (c.onScreen) INBOX_POLL_MS else INBOX_BACKGROUND_MS)
            }
        }
        // 5. the chat with Claude (TCL29): the thread every 8 s while the chat screen is open, every minute otherwise; the lease line with it.
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (hasToken()) {
                    runCatching { inbox.pollChat() }
                    if (inbox.chatOpen) runCatching { inbox.pollWho() }
                }
                // woken by syncNow() when the app comes back to the front (a phone switched off and on stalls a plain delay; Tj, 2026-10-16: the chat showed old messages until he scrolled)
                nap(if (inbox.chatOpen) CHAT_OPEN_POLL_MS else INBOX_POLL_MS)
            }
        }
        // 3. a key added or changed in the phone's key store goes to GitHub's secrets by itself (Tj, 2026-10-15: "If I add api keys inside my phone vigilant app, will it automatically upload them to GitHub ... If it doesn't already do this, make it do so"):
        // until now the upload rode only on a settings push, so a new key with no setting change waited for the next one. A changed fingerprint is the only trigger (no network when nothing changed), only while the cloud scan is on.
        scope.launch(Dispatchers.IO) {
            c.keyStore.flow.filterNotNull().debounce(3_000L).collect {
                if (c.settingsStore.flow.value?.cloudDrives == true && hasToken()) runCatching { sendKeys(auto = true) }
            }
        }
        // 4. the bid desk's Novig side (Tj, 2026-10-15): while bids are priced from GitHub's candidates, the candidates' order books are read over this phone's key and stream and a pass judges them. Sleeps otherwise.
        scope.launch(Dispatchers.IO) {
            var lastPassFor = 0L
            while (isActive) {
                val s = c.settingsStore.flow.value
                if (s == null || !s.bidsFromCloud || !hasToken() || c.cloudBids.current == null) { delay(IDLE_MS); continue }
                val read = runCatching { c.cloudBids.refresh(System.currentTimeMillis(), BIDS_READS_PER_ROUND) }.getOrDefault(0)
                val at = c.cloudBids.current?.atMs ?: 0L
                if (read > 0 || at != lastPassFor) {
                    lastPassFor = at
                    runCatching { c.maker.run("GitHub's candidates", minGapMs = BIDS_PASS_GAP_MS) }
                }
                delay(if (read > 0) BIDS_ROUND_MS else BIDS_IDLE_ROUND_MS)
            }
        }
        // 5. the engine's API usage (Tj, 2026-10-15), for the meters: read whenever a token is saved (the engine's spending today counts whether or not the scan is on GitHub right now); an unchanged file is a free 304.
        scope.launch(Dispatchers.IO) {
            var etag: String? = null
            while (isActive) {
                if (hasToken()) when (val p = client.fetchUsage(etag)) {
                    is CloudClient.Poll.Fresh -> { etag = p.etag; c.cloudUsage.value = p.value }
                    else -> {}
                }
                nap(USAGE_POLL_MS)
            }
        }
        // 6. what the engine did (Tj, 2026-10-15: opening the app syncs the automated system's bets and results): its order book, read whenever a token is saved and the engine has ever traded or trades now; an unchanged file is a free 304.
        scope.launch(Dispatchers.IO) {
            var etag: String? = null
            while (isActive) {
                if (hasToken()) when (val p = client.fetchTrades(etag)) {
                    is CloudClient.Poll.Fresh -> { etag = p.etag; _ui.update { it.copy(trades = p.value, tradesReadAtMs = System.currentTimeMillis()) } }
                    is CloudClient.Poll.NotModified -> _ui.update { it.copy(tradesReadAtMs = System.currentTimeMillis()) }
                    is CloudClient.Poll.Failed -> {}
                }
                nap(if (c.onScreen) TRADES_POLL_MS else TRADES_POLL_BACKGROUND_MS)
            }
        }
        // 6b. the engine's bids (TCL31), the same way: opening the app reads them at once ([syncNow]); an unchanged file is a free 304.
        scope.launch(Dispatchers.IO) {
            var etag: String? = null
            while (isActive) {
                if (hasToken()) when (val p = client.fetchDesk(etag)) {
                    is CloudClient.Poll.Fresh -> { etag = p.etag; _ui.update { it.copy(desk = p.value, deskReadAtMs = System.currentTimeMillis()) } }
                    is CloudClient.Poll.NotModified -> _ui.update { it.copy(deskReadAtMs = System.currentTimeMillis()) }
                    is CloudClient.Poll.Failed -> {}
                }
                nap(if (c.onScreen) TRADES_POLL_MS else TRADES_POLL_BACKGROUND_MS)
            }
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

    /** Every loop that waits is woken by this; a missed wake only means the loop looks a few seconds later. */
    private val wake = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private suspend fun nap(ms: Long) { kotlinx.coroutines.withTimeoutOrNull(ms) { wake.first() } }

    /**
     * Opening the app syncs everything at once (Tj, 2026-10-16: "when I open the android app, I want it to automatically sync to whatever GitHub is currently doing including all the status and bets"): the engine's
     * heartbeat and list, its bets and its bids, the messages and the usage are read now instead of at their next turn. Unchanged files answer 304 and cost nothing.
     */
    fun syncNow() { wake.tryEmit(Unit) }

    /**
     * Pull to refresh / the Scan button in cloud mode (Tj, 2026-10-14): tells the engine to scan now, in the settings file it reads every 3 s (settings and the request travel together, so the scan it starts
     * is under exactly what the phone shows). The engine acknowledges in its heartbeat and reports progress; [CloudText] says it in words. If no engine is running one is started. Null = sent, else why not.
     */
    suspend fun requestPropLineSweep(): String? {
        if (!hasToken()) { _ui.update { it.copy(note = "Save a GitHub token first (Settings › Cloud scan).") }; return "no token" }
        val now = System.currentTimeMillis()
        propLineRequestedAt = maxOf(now, propLineRequestedAt + 1)
        _ui.update { it.copy(propLineRequestedAtMs = propLineRequestedAt) }
        c.settingsStore.flow.value?.let { pushSettings(it, force = true) }
        return null
    }

    suspend fun requestScan(): String? {
        if (!hasToken()) { _ui.update { it.copy(note = "Save a GitHub token first (Settings › Cloud scan).") }; return "no token" }
        val s = c.currentSettings()
        if (!s.cloudDrives) return "the cloud scan is not driving the scan now"
        val now = System.currentTimeMillis()
        refreshRequestedAt = maxOf(now, refreshRequestedAt + 1)
        _ui.update { it.copy(requestedAtMs = refreshRequestedAt, note = null) }
        pushSettings(s, force = true)
        // A silent engine is started now rather than at the next 15-minute look.
        val st = latest.status
        if (st == null || st.idle || now - st.heartbeatMs > ENGINE_DEAD_S * 1000L) {
            if (now - lastStartAskedAt > 60_000L) { lastStartAskedAt = now; client.startEngine() }
        }
        return null
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

    /** GitHub's candidate lines for the bid desk: handed to the lane, which gives them Novig's books ([CloudBidLane]). */
    private suspend fun fetchBids() {
        when (val b = client.fetchBids()) {
            is CloudClient.Poll.Fresh -> { bidsFetchedFor = b.value.atMs; c.cloudBids.put(b.value) }
            is CloudClient.Poll.Failed -> latest.why = b.why
            else -> {}
        }
    }

    /** PropLine's +EV lines at Novig, for the +EV tab's PropLine screen. */
    private suspend fun fetchPropLineEv() {
        when (val b = client.fetchPropLineEv()) {
            is CloudClient.Poll.Fresh -> { evFetchedFor = b.value.atMs; _ui.update { it.copy(propLineEv = b.value) } }
            is CloudClient.Poll.Failed -> latest.why = b.why
            else -> {}
        }
    }

    @Volatile private var liveDirectAtMs = 0L

    /** A reading that arrived straight from the engine: it is the newest status there is. */
    private fun applyLive(st: com.tjshea.vigilant.data.cloud.CloudStatus, s: ScanSettings) {
        val now = System.currentTimeMillis()
        val cur = latest.status
        if (cur != null && st.runId == cur.runId && st.heartbeatMs < cur.heartbeatMs) return
        latest.status = st
        latest.statusOkAtMs = now
        liveDirectAtMs = now
        publishUi(s)
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
        _ui.update { it.copy(manualWords = run { loadManual(); manualWords() }, statusOkAtMs = latest.statusOkAtMs, liveDirectAtMs = liveDirectAtMs, status = st, why = latest.why, requestedAtMs = refreshRequestedAt, line = line, listAgeSec = f?.let { x -> ((now - x.computedAtMs) / 1000L).coerceAtLeast(0L) }, rows = f?.rows?.size ?: 0, rateRemaining = client.rateRemaining) }
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

    /**
     * Whether the engine was last told to run (kept in a file): a switch-off the engine has not heard of yet is retried until it has, and survives a restart of the app, so the engine is never left running for
     * a phone that is not using it (Settings › Cloud scan off, scanning paused, the kill switch, or a bid desk that needs the phone's own scan).
     */
    private val wantedFile = File(c.app.filesDir, "cloud_wanted")
    private val offPending = File(c.app.filesDir, "cloud_off_pending")
    private var lastWanted: Boolean? = null

    /** One writer at a time: the settings collector, the 10-minute retry and a pull to refresh all write the same config and share its revision counter. */
    private val pushMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun pushSettings(s: ScanSettings, force: Boolean = false) = pushMutex.withLock { pushLocked(s, force) }

    private suspend fun pushLocked(s: ScanSettings, force: Boolean) {
        if (!hasToken()) return
        val wanted = s.cloudDrives
        if (lastWanted == null) lastWanted = runCatching { wantedFile.readText() == "1" }.getOrNull()
        if (lastWanted == true && !wanted) runCatching { offPending.writeText("1") }
        lastWanted = wanted
        runCatching { wantedFile.writeText(if (wanted) "1" else "0") }
        // Not wanted and nothing owed: the engine is left alone (no request is made for a phone that never used the cloud scan).
        if (!wanted && !offPending.exists()) return
        val key = (if (wanted) "on" else "off") + c.json.encodeToString(ScanSettings.serializer(), s).hashCode() + "r$refreshRequestedAt" + "m$manualRunUntil,$manualStopUntil" + "x$resetRequestedAt" + "p$propLineRequestedAt"
        if (key == lastPushedKey && !force) return
        val rev = maxOf(System.currentTimeMillis(), lastRev + 1)
        loadManual()
        val why = client.writeConfig(CloudConfig(rev = rev, on = wanted, settings = s, device = android.os.Build.MODEL.orEmpty().take(40), refreshAtMs = refreshRequestedAt, manualRunUntilMs = manualRunUntil, manualStopUntilMs = manualStopUntil, resetAtMs = resetRequestedAt, propLineRefreshAtMs = propLineRequestedAt))
        if (why == null) {
            if (wanted) runCatching { sendKeys(auto = true) }
            lastPushedKey = key; lastRev = rev
            if (!wanted) runCatching { offPending.delete() }
            val why2 = if (s.cloudScan && !wanted) "scanning is paused" else null
            _ui.update { it.copy(pushedRev = rev, pushedAtMs = System.currentTimeMillis(), note = if (wanted) "Settings sent to the engine." else "The engine was told to stand down" + (why2?.let { w -> " ($w)" } ?: "") + ".") }
        } else { c.eventLog.warn("CLOUD", "settings not sent: $why"); _ui.update { it.copy(note = "Settings not sent: $why") } }
    }

    private val keysSent = File(c.app.filesDir, "cloud_keys_sent")
    private val tradeSent = File(c.app.filesDir, "cloud_trade_sent")
    @Volatile private var keysAttempt: String? = null

    /**
     * The keys this phone would send, by secret name, values joined by commas; empty providers are left out. Pinnodds never ([CloudKeys.PHONE_ONLY]). BoltOdds goes since Tj's word of 2026-10-15. And the
     * engine's own read-only Novig key ([CloudNovigKey]) when one was made: its id and its PEM, never the trading key or the management key.
     */
    private suspend fun keysToSend(): Map<String, String> {
        val m = CloudKeys.BY_PROVIDER.mapNotNull { (p, name) -> c.keyStore.current(p).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { name to it.joinToString(",") } }.toMap().toMutableMap()
        c.cloudNovigKeys.load()?.let { m[CloudKeys.NOVIG_KEY_ID] = it.keyId; m[CloudKeys.NOVIG_KEY_PEM] = it.pem }
        // Kalshi's key (Tj, 2026-10-16: "I want to send my kalshi key to GitHub so GitHub can use the socket"): the Key ID and the PEM opened from this phone's Keystore; the engine reads and streams with it, never trades.
        c.kalshiKeyStore.flow.value?.takeIf { it.complete }?.let { cred ->
            runCatching { c.openSecret(cred.pem) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { pem -> m[CloudKeys.KALSHI_KEY_ID] = cred.keyId.trim(); m[CloudKeys.KALSHI_KEY_PEM] = pem }
        }
        // The subaccount's trading key, only once Tj asked for it to go (tradeSent) and until he takes it off GitHub; never the management key.
        if (tradeSent.exists()) c.cloudTradeKeys.load()?.let { m[CloudKeys.TRADE_KEY_ID] = it.keyId; m[CloudKeys.TRADE_KEY_PEM] = it.pem }
        return m
    }

    private fun fingerprint(m: Map<String, String>): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(m.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Sends this phone's API keys to the repository's secrets for the engine to use (Tj, 2026-10-14: "make it so the app itself uploads the API keys to GitHub"). Each value is sealed to the
     * repository's public key on this phone ([com.tjshea.vigilant.data.cloud.SealedBox]), so GitHub stores it write-only: nothing, including this app, can read a secret back. Pinnodds and BoltOdds
     * keys and Kalshi's private key are never sent. Words only: no value is ever in the result, a log or an error. A running engine read its secrets when its job began, so it is stopped and started
     * again to pick them up.
     */
    private val keysMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun sendKeys(auto: Boolean = false): String = keysMutex.withLock { sendKeysLocked(auto) }

    private suspend fun sendKeysLocked(auto: Boolean): String {
        if (!hasToken()) return "Save the GitHub token first."
        val m = keysToSend()
        if (m.isEmpty()) return "No API keys are saved on this phone to send."
        val fp = fingerprint(m)
        if (auto && (fp == keysAttempt || runCatching { keysSent.readText() }.getOrNull() == fp)) return ""
        keysAttempt = fp
        val key = when (val k = client.secretsKey()) {
            is CloudClient.Poll.Fresh -> k.value
            is CloudClient.Poll.Failed -> return k.why.also { _ui.update { u -> u.copy(keysNote = "Keys not sent: $it") } }
            else -> return "GitHub's secrets key could not be read"
        }
        val sent = ArrayList<String>()
        for ((name, value) in m) {
            val why = client.putSecret(name, value, key)
            if (why != null) { val msg = "Keys not sent ($name): $why"; _ui.update { it.copy(keysNote = msg) }; c.eventLog.warn("CLOUD", msg); return msg }
            sent += name
            delay(300L)
        }
        val previous = runCatching { keysSent.readText() }.getOrNull()
        runCatching { keysSent.writeText(fp) }
        val msg = "Sent ${sent.size} key set(s) to GitHub secrets: ${sent.joinToString(", ")}. Pinnodds' key and Novig's management key stay on the phone."
        c.eventLog.info("CLOUD", msg)
        val tail = restartEngineFor(changed = previous != fp)
        _ui.update { it.copy(keysNote = msg + tail) }
        return msg + tail
    }

    /**
     * A running engine holds the secrets it read when its job began: it is stopped and started again to pick up [changed] keys, only when they changed (a second tap with the same keys must not throw away a
     * scan that is minutes in), and at most once in 10 minutes. Words for the note.
     */
    private suspend fun restartEngineFor(changed: Boolean): String {
        val st = latest.status
        val now = System.currentTimeMillis()
        val running = st != null && !st.idle && now - st.heartbeatMs < ENGINE_DEAD_S * 1000L
        return if (running && changed && c.settingsStore.flow.value?.cloudDrives == true && now - lastRestartAt > 10 * 60_000L) {
            lastRestartAt = now
            client.cancelRun(st!!.runId); delay(4_000L); lastStartAskedAt = System.currentTimeMillis(); client.startEngine()
            " The engine was restarted to use them."
        } else if (running && changed) " The running engine reads them the next time it starts (within 6 hours, or tap Start the engine now after switching it off and on)." else ""
    }

    private fun novigWords(e: Exception): String = when (e) {
        is com.tjshea.vigilant.data.novig.signing.NovigApiException -> "Novig refused (${e.status}${e.code?.let { " $it" }.orEmpty()})"
        else -> e.javaClass.simpleName + (e.message?.let { m -> ": " + m.take(80) } ?: "")
    }

    /**
     * Makes the engine's own read-only Novig key (once) and sends it to GitHub with the other keys (Tj, 2026-10-15: "allow me to send my novig key and boltodds key to GitHub for rapid scanning"). The key is a
     * `trading::read` key: it cannot place an order or move money. It is minted from the management key this phone already holds, so Novig sees one new key named "Vigilant cloud engine" on the Vigilant
     * subaccount; its private half is sealed by this phone and goes to a write-only GitHub secret. Words only: no key text is ever in the result, a log or an error.
     */
    suspend fun sendNovigKey(): String = keysMutex.withLock {
        fun say(m: String): String { _ui.update { it.copy(keysNote = m) }; return m }
        if (!hasToken()) return@withLock say("Save the GitHub token first.")
        if (c.cloudNovigKeys.load() == null) {
            val mgmt = c.managementKeys.load() ?: return@withLock say("Connect your Novig management key first (Settings › Betting & Novig account): it is what makes the read-only key.")
            val sub = c.connectedSubaccountKeyId ?: return@withLock say("The Vigilant subaccount is not connected yet (Settings › Betting & Novig account).")
            val minted = try {
                com.tjshea.vigilant.data.cloud.CloudNovigKey.mint(c.http, c.json, mgmt, sub)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock say("Novig did not make the key: ${novigWords(e)}").also { c.eventLog.warn("CLOUD", it) }
            }
            c.cloudNovigKeys.save(minted, sub)
            c.eventLog.info("CLOUD", "made a read-only Novig key for the engine (••••${minted.keyId.takeLast(4)}); it cannot trade")
        }
        refreshNovigKey()
        sendKeysLocked(auto = false)
    }

    /** Takes the Novig key off GitHub (both secrets) and forgets it here: the engine reads the public routes from its next start. The key stays registered in Novig (revoke it there if you like); it can only read. */
    suspend fun removeNovigKey(): String = keysMutex.withLock {
        fun say(m: String): String { _ui.update { it.copy(keysNote = m) }; return m }
        if (!hasToken()) return@withLock say("Save the GitHub token first.")
        for (name in CloudKeys.NOVIG) client.deleteSecret(name)?.let { why -> return@withLock say("Not removed ($name): $why") }
        c.cloudNovigKeys.clear()
        runCatching { keysSent.delete() }
        refreshNovigKey()
        say("The Novig key was removed from GitHub." + restartEngineFor(changed = true).replace(" to use them.", " to stop using it."))
    }

    /**
     * Gives the engine the Vigilant subaccount's trading key (Tj, 2026-10-15: "make it so I can push all needed keys, including secret keys, from the app on my phone to GitHub"; TASKS.md TCL20c). Novig allows one
     * live trading key per subaccount and the phone's lives in the Keystore, so the first time this REPLACES it ([CloudTradeKey.swap]: the management key revokes the old one and makes a new one the phone seals and
     * then signs with too). The management key never leaves the phone. Words only: no key text is ever in the result, a log or an error.
     */
    suspend fun sendTradeKey(): String = keysMutex.withLock {
        fun say(m: String): String { _ui.update { it.copy(keysNote = m) }; return m }
        if (!hasToken()) return@withLock say("Save the GitHub token first.")
        if (c.cloudTradeKeys.load() == null) {
            val mgmt = c.managementKeys.load() ?: return@withLock say("Connect your Novig management key first (Settings › Betting & Novig account): it is what replaces the trading key.")
            val conn = runCatching { c.novigConnection.load() }.getOrNull()
            val sub = conn?.subaccountKeyId?.takeIf { it.isNotBlank() } ?: return@withLock say("The Vigilant subaccount is not connected yet (Settings › Betting & Novig account).")
            val swapped = try {
                com.tjshea.vigilant.data.cloud.CloudTradeKey.swap(c.http, c.json, mgmt, sub)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock say("Novig did not make the trading key: ${novigWords(e)}. If the old key was already revoked, tap Enable betting in Settings › Betting & Novig account to get betting back.").also { c.eventLog.warn("CLOUD", it) }
            }
            c.cloudTradeKeys.save(swapped.key, sub)
            c.tradeVault.reload()
            val updated = conn.copy(tradingKeyId = swapped.key.keyId, tradingAlias = com.tjshea.vigilant.app.data.SharedTradeVault.SHARED)
            runCatching { c.novigConnection.save(updated) }
            c.useConnection(updated)
            c.eventLog.info("CLOUD", "replaced the subaccount's trading key with one the engine can hold (••••${swapped.key.keyId.takeLast(4)}), proved: ${swapped.verified}")
        }
        runCatching { tradeSent.writeText("1") }
        refreshNovigKey()
        sendKeysLocked(auto = false)
    }

    /** Takes the trading key off GitHub (both secrets). The phone keeps it and goes on betting with it; the engine can no longer order. */
    suspend fun removeTradeKey(): String = keysMutex.withLock {
        fun say(m: String): String { _ui.update { it.copy(keysNote = m) }; return m }
        if (!hasToken()) return@withLock say("Save the GitHub token first.")
        for (name in CloudKeys.TRADE) client.deleteSecret(name)?.let { why -> return@withLock say("Not removed ($name): $why") }
        runCatching { tradeSent.delete() }
        runCatching { keysSent.delete() }
        refreshNovigKey()
        say("The trading key was removed from GitHub; this phone still bets with it." + restartEngineFor(changed = true).replace(" to use them.", " to stop using it."))
    }

    /** Reads whether an engine key exists into the page's state. */
    suspend fun refreshNovigKey() {
        val at = c.cloudNovigKeys.saved()?.mintedAtMs ?: 0L
        val tradeAt = c.cloudTradeKeys.saved()?.mintedAtMs ?: 0L
        // The marker file is lost with the app's data (a reinstall), the secret on GitHub is not: ask GitHub, and keep the marker in step so the key is sent again with the rest.
        val onGitHub = client.secretExists(CloudKeys.TRADE_KEY_ID)
        if (onGitHub == true && !tradeSent.exists()) runCatching { tradeSent.writeText("1") }
        _ui.update { it.copy(novigKeyMadeAtMs = at, tradeKeyMadeAtMs = tradeAt, tradeKeyOnGitHub = onGitHub ?: tradeSent.exists()) }
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
 // Live light's decisions and the live taker's records (TCL100): one gzip per day file, sent again only when it changed.
        if (failed == null) {
            val known = runCatching { uploaded.read() }.getOrDefault(emptyMap())
            for ((dir, prefix) in listOf("live-light" to "live-light-", "pinn-live" to "tail-live-", "live-bids" to "lightbid-")) {
                val files = File(c.app.filesDir, dir).listFiles { x -> x.isFile && x.name.startsWith(prefix) && x.name.endsWith(".jsonl") }?.sortedBy { it.name }.orEmpty()
                for (f in files) {
                    if (sent >= MAX_PER_PASS) break
                    val sig = "${f.length()}:${f.lastModified()}"
                    val keyName = "$dir/${f.name}"
                    if (known[keyName] == sig) continue
                    val gz = withContext(Dispatchers.IO) { gzip(f) }
                    if (gz.size > MAX_FILE_BYTES) continue
                    val why = client.putFile(CloudClient.DATA_BRANCH, "phone/live/${f.name}.gz", gz, "${f.name} ${java.time.Instant.now()}")
                    if (why != null) { failed = why; break }
                    uploaded.update { it + (keyName to sig) }; sent++
                    delay(1_200L)
                }
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
        const val POLL_MS = 2_000L   // on screen (Tj, 2026-10-17: "real time or at most every couple seconds"); a 304 costs nothing against GitHub's limit
        const val INBOX_POLL_MS = 60_000L
        const val CHAT_OPEN_POLL_MS = 8_000L
        const val INBOX_BACKGROUND_MS = 5 * 60_000L
        /** How often the engine's usage file is asked for (a 304 costs nothing against GitHub's limit). */
        const val USAGE_POLL_MS = 30_000L
        const val TRADES_POLL_MS = 20_000L
        const val TRADES_POLL_BACKGROUND_MS = 5 * 60_000L
        /** Candidate books read per round, and the rounds' pace: the stream's books are free; the rest go through the key's paced reads. */
        const val BIDS_READS_PER_ROUND = 120
        const val BIDS_ROUND_MS = 3_000L
        const val BIDS_IDLE_ROUND_MS = 10_000L
        const val BIDS_PASS_GAP_MS = 20_000L
        const val POLL_BACKGROUND_MS = 20_000L
        /** Nothing to read (the switch off, paused, bids on, no token): look again this often; settings changes are also picked up by the loops that wait on them. */
        const val IDLE_MS = 10_000L
        /** While a requested scan has not finished the phone looks every 3 s (a 304 costs nothing). */
        const val POLL_PENDING_MS = 3_000L
        const val SAVE_EVERY_MS = 15 * 60_000L
        const val RETRY_PUSH_MS = 10 * 60_000L
        const val ENGINE_DEAD_S = 240L
        const val MAX_PER_PASS = 40
        const val MAX_FILE_BYTES = 20_000_000
    }
}
