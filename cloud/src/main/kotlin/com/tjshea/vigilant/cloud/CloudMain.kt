package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.CloudConfig
import com.tjshea.vigilant.data.cloud.CloudEngine
import com.tjshea.vigilant.data.cloud.CloudFeed
import com.tjshea.vigilant.data.cloud.CloudKeys
import com.tjshea.vigilant.data.cloud.CloudSources
import com.tjshea.vigilant.data.cloud.CloudStatus
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.novig.lab.gh.EdgeLog
import com.tjshea.vigilant.data.novig.lab.gh.EdgeRow
import com.tjshea.vigilant.data.pinnodds.DayJournal
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
    val publisher = Publisher(pubDir, repo, token, git)
    val log = Log(File(out, "cloud-engine.log"))
    val started = System.currentTimeMillis()
    val until = started + minutes * 60_000L
    log("cloud engine starting: $minutes min, run $runId, keys present: " + CloudKeys.ALL.filter { CloudKeys.fromEnv(it).isNotEmpty() }.joinToString(",").ifBlank { "none (public feeds only)" })

    val flusher = scope.launch { while (isActive) { runCatching { publisher.flush() }; delay(4_000L) } }
    val engine = CloudEngine(
        scanner = scanner, sources = sources::sources, config = { readConfig(git, cfgDir) },
        publish = { feed, status -> publisher.stage(feed, status) },
        onFeed = { rows, now -> runCatching { edge.observe(rows, now) } },
        engine = "github-actions", runId = runId, log = log::invoke, sourceNames = sources::names,
    )
    val end = engine.run(until, started)
    log("cloud engine ending: $end after ${(System.currentTimeMillis() - started) / 60_000L} min, ${engine.passes} passes")
    runCatching { publisher.flush(force = true) }
    flusher.cancel()
    scope.cancel()
    File(out, "end").writeText(end.name)
}

/** Reads `settings.json` from the `cloud-config` branch with git (the checkout already has the token). */
private fun readConfig(git: Git, dir: File): CloudConfig? {
    if (git.run(dir, "fetch", "-q", "--depth=1", "origin", "cloud-config") != 0) return null
    val text = git.out(dir, "show", "FETCH_HEAD:settings.json") ?: return null
    return CloudConfig.parse(text)
}

private class Log(private val file: File) {
    operator fun invoke(s: String) {
        val line = "${Instant.now()} $s"
        println(line)
        runCatching { file.appendText(line + "\n") }
    }
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
internal class Publisher(private val dir: File, private val repo: String, private val token: String, private val git: Git) {
    private var feedBytes: ByteArray? = null
    private var status: CloudStatus? = null
    private var dirty = false
    private var lastPushAt = 0L
    private var committed = false
    @Volatile private var lastProblem: String? = null

    @Synchronized
    fun stage(feed: CloudFeed?, st: CloudStatus): String? {
        if (feed != null) feedBytes = CloudFeed.encode(feed)
        status = st.copy(feedBytes = feedBytes?.size ?: 0)
        dirty = true
        return lastProblem
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
        File(dir, "status.json").writeText(CloudStatus.text(st.copy(heartbeatMs = now)))
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
        const val MIN_GAP_MS = 10_000L
    }
}
