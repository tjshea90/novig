package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.AddressWatch
import com.tjshea.vigilant.data.cloud.CloudBids
import com.tjshea.vigilant.data.cloud.CloudConfig
import com.tjshea.vigilant.data.cloud.CloudEngine
import com.tjshea.vigilant.data.cloud.CloudFeed
import com.tjshea.vigilant.data.cloud.CloudKeys
import com.tjshea.vigilant.data.cloud.CloudNovig
import com.tjshea.vigilant.data.cloud.CloudRanking
import com.tjshea.vigilant.data.cloud.CloudSources
import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.cloud.CloudUsage
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.lab.gh.EdgeLog
import com.tjshea.vigilant.data.novig.lab.gh.EdgeRow
import com.tjshea.vigilant.data.novig.trading.maker.BidCandidates
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.scanner.ApiChain
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.vigilantHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * The GitHub engine (`.github/workflows/cloud-scan.yml`): Vigilant's scanner, run on a GitHub runner for the phone.
 *
 *   --minutes N     how long this job runs before it hands over to the next (default 345; a job is capped at 6 h)
 *   --out DIR       journals and logs (default `out`)
 *   --pub DIR       the publishing work tree (default `pub`)
 *   --cfg DIR       a git work tree of the repository, to read the `cloud-config` branch with (default `.`)
 *
 * Environment: the API keys as repository secrets ([CloudKeys.ALL], optional, each only used when its API is switched on in the phone's settings), `GITHUB_TOKEN`, `GITHUB_REPOSITORY`, `GITHUB_RUN_ID`.
 * It **places no order**: Novig's public client only.
 */
fun main(args: Array<String>) = runBlocking {
    fun v(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val minutes = v("--minutes")?.toIntOrNull()?.coerceIn(1, 350) ?: 345
    val out = File(v("--out") ?: "out").apply { mkdirs() }
    val pubDir = File(v("--pub") ?: "pub")
    val cfgDir = File(v("--cfg") ?: ".")
    val repo = System.getenv("GITHUB_REPOSITORY").orEmpty().ifBlank { "tjshea90/novig" }
    val token = System.getenv("GITHUB_TOKEN").orEmpty()
    val runId = System.getenv("GITHUB_RUN_ID").orEmpty()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val http = vigilantHttpClient()
    val usage = UsageMeter(JsonFileStore(File(out, "usage.json"), UsageBook.serializer(), { UsageBook() }, json))
    val novig = NovigPublicClient(http, json, usage = usage)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sources = CloudSources(http, json, usage, scope, File(out, "state").apply { mkdirs() }, CloudKeys::fromEnv)
    val scanner = Scanner(novig)
    val edge = EdgeLog(DayJournal(File(out, "cloud"), "edge", EdgeRow.serializer()) { it.atMs })
    val git = Git(token)
    val log = Log(File(out, "cloud-engine.log"))
    // The engine's own Novig key (Tj, 2026-10-15): a read-only key sent as two secrets. Without them the engine reads the public routes as before; with them it reads through the signed route and keeps the key's websocket.
    val novigKeyId = CloudKeys.rawEnv(CloudKeys.NOVIG_KEY_ID)
    val novigKeyPem = CloudKeys.rawEnv(CloudKeys.NOVIG_KEY_PEM)
    val novigKeyed = if (novigKeyId.isNotEmpty() && novigKeyPem.isNotEmpty()) CloudNovig(novig, http, json, scope, novigKeyId, novigKeyPem, log = log::invoke) else null
    val publisher = Publisher(pubDir, repo, token, git, log::tail)
    val config = ConfigReader(git, cfgDir)
    val started = System.currentTimeMillis()
    val until = started + minutes * 60_000L
    log("cloud engine starting: $minutes min, run $runId, keys present: " + CloudKeys.ALL.filter { CloudKeys.fromEnv(it).isNotEmpty() }.joinToString(",").ifBlank { "none (public feeds only)" } + " · Novig read-only key: " + (if (novigKeyed != null) "present" else "none (public routes)"))

    novigKeyed?.problem?.let { log("Novig key: $it") }
    // The APIs' measured ranking survives the handover: the previous job's file is read back before the first scan ([CloudRanking]), and this job writes its own with every push.
    runCatching { PreviousFeed.read(git, cfgDir, "ranking.json") }.getOrNull()?.let { text ->
        val seeded = CloudRanking.decode(text, System.currentTimeMillis())
        if (seeded.isNotEmpty()) { ApiChain.measures = seeded; log("ranking: ${seeded.size} measurements carried over from the previous job") }
    }

    // What each key spent in the previous job (Tj, 2026-10-15): read back so a handover does not forget a daily allowance already spent, and so the phone's meters keep their totals.
    val allKeys = (CloudKeys.ALL.flatMap { CloudKeys.fromEnv(it) }).distinct()
    runCatching { PreviousFeed.read(git, cfgDir, "usage.json") }.getOrNull()?.let { text ->
        CloudUsage.decode(text)?.let { f ->
            runBlocking { usage.seed(CloudUsage.rekey(f.book, allKeys)) }
            log("usage: carried over from the previous job (${f.book.providers.size} providers)")
        }
    }
    val flusher = scope.launch {
        var usageAt = 0L
        while (isActive) {
            val now = System.currentTimeMillis()
            // The engine's API usage goes out with the list, at most every 30 s (keys by id, never in clear: [CloudUsage]).
            if (now - usageAt >= 30_000L) { usageAt = now; runCatching { publisher.stageUsage(CloudUsage.encode(usage.flow.value, runId, now, allKeys), now) } }
            runCatching { publisher.flush() }
            delay(4_000L)
        }
    }
    // Novig refusing THIS runner's address ends the job early so the workflow draws another runner (AddressWatch, Tj 2026-10-15); the streak is carried from job to job in the status file.
    val prevStatus = runCatching { PreviousFeed.read(git, cfgDir, "status.json") }.getOrNull()?.let { CloudStatus.parse(it) }
    val addressWatch = novigKeyed?.let { nk -> AddressWatch(started, prevStatus?.addressHandovers ?: 0, prevStatus?.addressHandoverAtMs ?: 0L) { now -> nk.addressRefused(now) } }
    val engine = CloudEngine(
        scanner = scanner, sources = sources::sources, config = { config.read() },
        publish = { feed, status -> publisher.stage(feed, status) },
        // The bid desk's candidate lines (Tj, 2026-10-15), only while the phone has Vigilant-priced bids on: the fair side of every bid decision is made here, the phone adds Novig's books and posts.
        prepare = { s -> novigKeyed?.configure(s) },
        novigRoute = { novigKeyed?.route(System.currentTimeMillis()) ?: CloudNovig.PUBLIC_ONLY },
        addressWatch = addressWatch,
        bidLines = { result, s, now -> sources.askInjuries(result, s, now); BidCandidates.from(result, s, now) { o -> sources.unavailable(o, now) } },
        publishBids = { publisher.stageBids(it) },
        onFeed = { rows, now -> runCatching { edge.observe(rows, now) } },
        engine = "github-actions", runId = runId, log = log::invoke, sourceNames = sources::names,
    )
    val end = engine.run(until, started)
    log("cloud engine ending: $end after ${(System.currentTimeMillis() - started) / 60_000L} min, ${engine.passes} passes")
    runCatching { publisher.flush(force = true) }
    novigKeyed?.close()
    flusher.cancel()
    scope.cancel()
    File(out, "end").writeText(end.name)
}

/**
 * Reads `settings.json` from the `cloud-config` branch with git (the checkout already has the token). The engine looks every 3 s, so it asks first with the light `ls-remote` which commit the branch is at and
 * fetches only when that changed: an unchanged answer is a few hundred bytes, not a fetch.
 */
private class ConfigReader(private val git: Git, private val dir: File) {
    private var sha: String? = null
    private var cached: CloudConfig? = null

    @Synchronized
    fun read(): CloudConfig? {
        val head = git.out(dir, "ls-remote", "origin", "refs/heads/cloud-config")?.trim()?.substringBefore('\t')?.takeIf { it.length >= 40 } ?: return cached
        if (head == sha && cached != null) return cached
        if (git.run(dir, "fetch", "-q", "--depth=1", "origin", "cloud-config") != 0) return cached
        val text = git.out(dir, "show", "FETCH_HEAD:settings.json") ?: return cached
        return CloudConfig.parse(text)?.also { cached = it; sha = head } ?: cached
    }
}

/** The previous job's `ranking.json` or `usage.json` from `cloud-feed`, read with git like [ConfigReader] reads the settings (the checkout already has the token). Null when there is none. */
private object PreviousFeed {
    fun read(git: Git, dir: File, file: String): String? {
        if (git.run(dir, "fetch", "-q", "--depth=1", "origin", "cloud-feed") != 0) return null
        return git.out(dir, "show", "FETCH_HEAD:$file")
    }
}

private class Log(private val file: File) {
    private val recent = ArrayDeque<String>()

    operator fun invoke(s: String) {
        val line = "${Instant.now()} $s"
        println(line)
        runCatching { file.appendText(line + "\n") }
        synchronized(recent) { recent.addLast(line); while (recent.size > TAIL_LINES) recent.removeFirst() }
    }

    /** The newest lines, published with the list (`engine-log.txt` on `cloud-feed`) so the engine can be read from the repository without opening a job's logs. */
    fun tail(): String = synchronized(recent) { recent.joinToString("\n") }

    companion object { const val TAIL_LINES = 200 }
}

/** git through the command line; the token is never printed (it is part of the remote URL only, and anything git says is scrubbed). */
internal class Git(private val token: String) {
    fun run(dir: File, vararg a: String): Int = exec(dir, a.toList()).first
    fun out(dir: File, vararg a: String): String? = exec(dir, a.toList()).let { if (it.first == 0) it.second else null }
    fun exec(dir: File, a: List<String>): Pair<Int, String> = runCatching {
        val p = ProcessBuilder(listOf("git") + a).directory(dir).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        code to (if (token.isNotEmpty()) text.replace(token, "***") else text)
    }.getOrElse { -1 to it.javaClass.simpleName }
}

/**
 * Publishes to the `cloud-feed` branch as ONE commit that is amended and force-pushed each time, so the branch never grows (the same trick as `lab-data`). [stage] only stores the newest list and
 * heartbeat; [flush] (every few seconds, from one coroutine) pushes the newest, never faster than [MIN_GAP_MS] (GitHub's secondary limits dislike a push a second), a heartbeat at least
 * every [HEARTBEAT_MS] when nothing else changed.
 */
internal class Publisher(private val dir: File, private val repo: String, private val token: String, private val git: Git, private val logTail: () -> String = { "" }) {
    private var feedBytes: ByteArray? = null
    private var bidsBytes: ByteArray? = null
    private var usageText: String? = null
    private var usageAt = 0L
    private var status: CloudStatus? = null
    private var dirty = false
    private var lastPushAt = 0L
    private var committed = false
    @Volatile private var lastProblem: String? = null

    @Synchronized
    fun stage(feed: CloudFeed?, st: CloudStatus): String? {
        if (feed != null) feedBytes = CloudFeed.encode(feed)
        status = st.copy(feedBytes = feedBytes?.size ?: 0, bidsBytes = bidsBytes?.size ?: 0, usageAtMs = usageAt)
        dirty = true
        return lastProblem
    }

    /** The engine's API usage snapshot ([CloudUsage]; keys by id), written beside the list and announced in the status. */
    @Synchronized
    fun stageUsage(text: String, at: Long) {
        usageText = text; usageAt = at
        status = status?.copy(usageAtMs = at)
        dirty = true
    }

    /** The bid desk's candidate lines (`bids.json.gz`); null (bids switched off on the phone) takes the file away so nothing stale lingers. Goes out with the next push. */
    @Synchronized
    fun stageBids(b: CloudBids?) {
        bidsBytes = b?.let { CloudBids.encode(it) }
        status = status?.copy(bidsBytes = bidsBytes?.size ?: 0)
        dirty = true
    }

    @Synchronized
    fun flush(force: Boolean = false) {
        val st = status ?: return
        val now = System.currentTimeMillis()
        if (!dirty || (!force && now - lastPushAt < MIN_GAP_MS)) return
        dir.mkdirs()
        if (!File(dir, ".git").exists()) {
            git.run(dir, "init", "-q")
            git.run(dir, "config", "user.name", "vigilant-cloud")
            git.run(dir, "config", "user.email", "actions@users.noreply.github.com")
            git.run(dir, "remote", "add", "origin", "https://x-access-token:$token@github.com/$repo.git")
            git.run(dir, "checkout", "-q", "--orphan", "cloud-feed")
        }
        feedBytes?.let { File(dir, "feed.json.gz").writeBytes(it) }
        usageText?.let { File(dir, "usage.json").writeText(it) }
        ApiChain.measures.takeIf { it.isNotEmpty() }?.let { File(dir, "ranking.json").writeText(CloudRanking.encode(it)) }
        File(dir, "bids.json.gz").let { f -> bidsBytes?.let { f.writeBytes(it) } ?: f.delete() }
        File(dir, "status.json").writeText(CloudStatus.text(st.copy(heartbeatMs = now)))
        logTail().takeIf { it.isNotBlank() }?.let { File(dir, "engine-log.txt").writeText(it + "\n") }
        git.run(dir, "add", "-A")
        val msg = "cloud feed ${java.time.Instant.ofEpochMilli(now)}"
        val c = if (committed) git.exec(dir, listOf("commit", "-q", "--amend", "--allow-empty", "-m", msg)) else git.exec(dir, listOf("commit", "-q", "--allow-empty", "-m", msg))
        if (c.first != 0) { lastProblem = "commit failed: ${c.second.take(120)}"; return }
        committed = true
        val p = git.exec(dir, listOf("push", "-q", "-f", "origin", "cloud-feed"))
        lastPushAt = now
        if (p.first == 0) { lastProblem = null; dirty = false } else lastProblem = "push failed: ${p.second.take(160)}"
    }

    companion object {
        const val MIN_GAP_MS = 8_000L
    }
}
