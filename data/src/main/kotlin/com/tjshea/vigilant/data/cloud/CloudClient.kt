package com.tjshea.vigilant.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * The phone's (and Claude's tools') way into the GitHub engine, over GitHub's REST API with one fine-grained token for this repository (Contents read and write, Actions write for the Start button).
 * Nothing here talks to any other server. The token is never logged and never part of an error text.
 *
 *  - [pollStatus] / [fetchFeed]: the engine's heartbeat (a few hundred bytes, read with an ETag, so an unchanged answer is a 304 that GitHub does not count against the hourly limit) and its list;
 *  - [writeConfig]: the settings the engine reads before every pass;
 *  - [putFile]: permanent saving of a file to a branch that is never rewritten (`phone-data`): the day files of bets, bids and history;
 *  - [startEngine]: dispatches `cloud-scan.yml`.
 */
class CloudClient(
    private val http: OkHttpClient,
    private val token: () -> String?,
    val repo: String = DEFAULT_REPO,
    private val api: String = "https://api.github.com",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Poll<out T> {
        data class Fresh<T>(val value: T, val etag: String?) : Poll<T>
        data object NotModified : Poll<Nothing>
        data class Failed(val why: String, val retryAfterMs: Long? = null) : Poll<Nothing>
    }

    /** Set from the last answer: how many API requests GitHub says are left this hour, for Diagnostics. */
    @Volatile var rateRemaining: Int? = null
        private set

    private fun req(path: String) = Request.Builder().url("$api/repos/$repo/$path")
        .header("Authorization", "Bearer ${token().orEmpty()}")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "vigilant-app")

    private fun hasToken() = !token().isNullOrBlank()

    private fun why(code: Int, body: String?): String = when (code) {
        401 -> "GitHub refused the token (expired or wrong)"
        403, 429 -> if (body.orEmpty().contains("rate limit", true)) "GitHub's rate limit" else "GitHub refused (the token lacks permission, or a limit)"
        404 -> "not found (the engine has not published yet, or the token cannot see the repository)"
        else -> "GitHub answered $code"
    }

    private fun retryAfter(r: okhttp3.Response): Long? =
        r.header("Retry-After")?.toLongOrNull()?.times(1000L)
            ?: r.header("X-RateLimit-Reset")?.toLongOrNull()?.let { (it * 1000L - clock()).coerceAtLeast(1_000L) }.takeIf { r.header("X-RateLimit-Remaining") == "0" }

    private fun note(r: okhttp3.Response) { r.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { rateRemaining = it } }

    /** The engine's heartbeat, or [Poll.NotModified] when [etag] is still current. */
    suspend fun pollStatus(etag: String?): Poll<CloudStatus> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        val b = req("contents/status.json?ref=$FEED_BRANCH").header("Accept", RAW)
        if (etag != null) b.header("If-None-Match", etag)
        runCatching {
            http.newCall(b.build()).execute().use { r ->
                note(r)
                when {
                    r.code == 304 -> Poll.NotModified
                    r.isSuccessful -> CloudStatus.parse(r.body?.string().orEmpty())?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the engine's status could not be read")
                    else -> Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                }
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** The newest list, as the engine published it. */
    suspend fun fetchFeed(): Poll<CloudFeed> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        runCatching {
            http.newCall(req("contents/feed.json.gz?ref=$FEED_BRANCH").header("Accept", RAW).build()).execute().use { r ->
                note(r)
                if (!r.isSuccessful) return@use Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                val bytes = r.body?.bytes() ?: return@use Poll.Failed("empty answer")
                CloudFeed.decode(bytes)?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the list could not be read (an engine newer than this app?)")
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** The bid desk's candidate lines, as the engine published them. */
    suspend fun fetchBids(): Poll<CloudBids> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        runCatching {
            http.newCall(req("contents/bids.json.gz?ref=$FEED_BRANCH").header("Accept", RAW).build()).execute().use { r ->
                note(r)
                if (!r.isSuccessful) return@use Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                val bytes = r.body?.bytes() ?: return@use Poll.Failed("empty answer")
                CloudBids.decode(bytes)?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the candidate list could not be read (an engine newer than this app?)")
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** PropLine's +EV-at-Novig list as the engine published it (`propline-ev.json.gz`, TCL56). */
    suspend fun fetchPropLineEv(): Poll<com.tjshea.vigilant.data.reference.PlEvFeed> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        runCatching {
            http.newCall(req("contents/propline-ev.json.gz?ref=$FEED_BRANCH").header("Accept", RAW).build()).execute().use { r ->
                note(r)
                if (!r.isSuccessful) return@use Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                val bytes = r.body?.bytes() ?: return@use Poll.Failed("empty answer")
                com.tjshea.vigilant.data.reference.PlEvFeed.decode(bytes)?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the PropLine +EV list could not be read (an engine newer than this app?)")
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** The engine's API usage snapshot (`usage.json`, [CloudUsage]), or [Poll.NotModified] when [etag] is still current. Keys are ids: the caller maps them to its own ([CloudUsage.rekey]). */
    suspend fun fetchUsage(etag: String?): Poll<CloudUsage.File> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        val b = req("contents/usage.json?ref=$FEED_BRANCH").header("Accept", RAW)
        if (etag != null) b.header("If-None-Match", etag)
        runCatching {
            http.newCall(b.build()).execute().use { r ->
                note(r)
                when {
                    r.code == 304 -> Poll.NotModified
                    r.isSuccessful -> CloudUsage.decode(r.body?.string().orEmpty())?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the engine's usage could not be read")
                    else -> Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                }
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** The engine's bid desk (`desk.json`, [DeskFile]): its bids and their fills; [Poll.NotModified] when [etag] is still current; an empty file when the engine has never bid. */
    suspend fun fetchDesk(etag: String?): Poll<DeskFile> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        val b = req("contents/desk.json?ref=$FEED_BRANCH").header("Accept", RAW)
        if (etag != null) b.header("If-None-Match", etag)
        runCatching {
            http.newCall(b.build()).execute().use { r ->
                note(r)
                when {
                    r.code == 304 -> Poll.NotModified
                    r.isSuccessful -> DeskFile.parse(r.body?.string().orEmpty())?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the engine's bids could not be read")
                    r.code == 404 -> Poll.Fresh(DeskFile(), null)   // the engine has not bid yet
                    else -> Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                }
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** The engine's order book (`trades.json`, [TradeLedger]), or [Poll.NotModified] when [etag] is still current. */
    suspend fun fetchTrades(etag: String?): Poll<TradeLedger> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        val b = req("contents/trades.json?ref=$FEED_BRANCH").header("Accept", RAW)
        if (etag != null) b.header("If-None-Match", etag)
        runCatching {
            http.newCall(b.build()).execute().use { r ->
                note(r)
                when {
                    r.code == 304 -> Poll.NotModified
                    r.isSuccessful -> TradeLedger.parse(r.body?.string().orEmpty())?.let { Poll.Fresh(it, r.header("ETag")) } ?: Poll.Failed("the engine's orders could not be read")
                    r.code == 404 -> Poll.Fresh(TradeLedger(), null)   // the engine has not traded yet
                    else -> Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                }
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /**
     * The file names in [dir] on [branch] (one request; [Poll.NotModified] when [etag] is current, which costs nothing against the rate limit). A folder that does not exist yet is an empty list.
     * Used for the Messages and the settings requests ([CloudMessages], [CloudOverrides]): one small file each, so two writers never overwrite each other.
     */
    suspend fun listDir(branch: String, dir: String, etag: String?): Poll<List<String>> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        val b = req("contents/${encodePath(dir)}?ref=$branch")
        if (etag != null) b.header("If-None-Match", etag)
        runCatching {
            http.newCall(b.build()).execute().use { r ->
                note(r)
                when {
                    r.code == 304 -> Poll.NotModified
                    r.code == 404 -> Poll.Fresh(emptyList(), null)
                    r.isSuccessful -> {
                        val names = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonArray.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
                        Poll.Fresh(names, r.header("ETag"))
                    }
                    else -> Poll.Failed(why(r.code, r.body?.string()), retryAfter(r))
                }
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "")) }
    }

    /** One file's text from [branch], or null when it cannot be read. */
    suspend fun readFile(branch: String, path: String): String? = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext null
        runCatching {
            http.newCall(req("contents/${encodePath(path)}?ref=$branch").header("Accept", RAW).build()).execute().use { r ->
                note(r)
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
    }

    /** Writes the settings the engine reads (creating the branch the first time). Null = done; otherwise why not. */
    suspend fun writeConfig(config: CloudConfig): String? =
        putFile(CONFIG_BRANCH, "settings.json", CloudConfig.text(config).toByteArray(Charsets.UTF_8), "settings rev ${config.rev}")

    /**
     * Saves [bytes] to [path] on [branch] as one commit, replacing what is there (the branch keeps every version: history is never rewritten). The branch is made from the default branch's head the first
     * time. Null = saved; otherwise why not. One retry on a stale sha (another writer got there first).
     */
    suspend fun putFile(branch: String, path: String, bytes: ByteArray, message: String): String? = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext "no GitHub token saved"
        runCatching {
            repeat(2) { attempt ->
                val existing = existingSha(branch, path)
                if (existing is Existing.Error) return@withContext existing.why
                val sha = (existing as? Existing.Found)?.sha
                val body = buildJsonObject {
                    put("message", message); put("branch", branch)
                    put("content", Base64.getEncoder().encodeToString(bytes))
                    if (sha != null) put("sha", sha)
                }
                val r = http.newCall(req("contents/${encodePath(path)}").put(body.toString().toRequestBody(JSON)).build()).execute()
                r.use {
                    note(it)
                    when {
                        it.isSuccessful -> return@withContext null
                        it.code == 409 || it.code == 422 -> if (attempt == 1) return@withContext why(it.code, it.body?.string()) // stale sha: look again once
                        it.code == 404 && existing is Existing.NoBranch -> { ensureBranch(branch)?.let { e -> return@withContext e } }
                        else -> return@withContext why(it.code, it.body?.string())
                    }
                }
            }
            "could not save $path"
        }.getOrElse { it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "") }
    }

    /**
     * Stores [value] as the repository secret [name], sealed to the repository's public key ([SealedBox]); GitHub keeps it write-only. Needs a token with Secrets: read and write.
     * Null = stored; otherwise why not (never carrying the value or the token).
     */
    suspend fun putSecret(name: String, value: String, key: SecretsKey): String? = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext "no GitHub token saved"
        runCatching {
            val sealed = SealedBox.seal(value.toByteArray(Charsets.UTF_8), key.publicKey)
            val body = buildJsonObject { put("encrypted_value", Base64.getEncoder().encodeToString(sealed)); put("key_id", key.keyId) }.toString().toRequestBody(JSON)
            http.newCall(req("actions/secrets/$name").put(body).build()).execute().use { r ->
                note(r)
                when {
                    r.isSuccessful -> null
                    r.code == 403 || r.code == 404 -> "the token cannot write secrets (give it Secrets: read and write on the repository)"
                    else -> why(r.code, r.body?.string())
                }
            }
        }.getOrElse { it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "") }
    }

    /** Removes the repository secret [name]. Null = removed (or it was not there); otherwise why not. */
    suspend fun deleteSecret(name: String): String? = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext "no GitHub token saved"
        runCatching {
            http.newCall(req("actions/secrets/$name").delete().build()).execute().use { r ->
                note(r)
                when {
                    r.isSuccessful || r.code == 404 -> null
                    r.code == 403 -> "the token cannot remove secrets (give it Secrets: read and write on the repository)"
                    else -> why(r.code, r.body?.string())
                }
            }
        }.getOrElse { it.javaClass.simpleName + (it.message?.let { m -> ": " + m.take(80) } ?: "") }
    }

    /** The key a secret is sealed to. */
    class SecretsKey(val keyId: String, val publicKey: ByteArray)

    suspend fun secretsKey(): Poll<SecretsKey> = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext Poll.Failed("no GitHub token saved")
        runCatching {
            http.newCall(req("actions/secrets/public-key").header("Accept", "application/vnd.github+json").build()).execute().use { r ->
                note(r)
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@use Poll.Failed(if (r.code == 403 || r.code == 404) "the token cannot write secrets (give it Secrets: read and write on the repository)" else why(r.code, text))
                val j = Json.parseToJsonElement(text).jsonObject
                val id = j["key_id"]?.jsonPrimitive?.contentOrNull
                val k = j["key"]?.jsonPrimitive?.contentOrNull?.let { Base64.getDecoder().decode(it) }
                if (id == null || k == null || k.size != SealedBox.KEY_BYTES) Poll.Failed("GitHub's secrets key could not be read") else Poll.Fresh(SecretsKey(id, k), null)
            }
        }.getOrElse { Poll.Failed(it.javaClass.simpleName) }
    }

    /** Stops a running workflow run (so a new engine starts with the secrets just stored). Null = asked. */
    suspend fun cancelRun(runId: String): String? = withContext(Dispatchers.IO) {
        if (!hasToken() || runId.isBlank()) return@withContext "nothing to cancel"
        runCatching {
            http.newCall(req("actions/runs/$runId/cancel").post("".toRequestBody(JSON)).build()).execute().use { r -> note(r); if (r.isSuccessful) null else why(r.code, r.body?.string()) }
        }.getOrElse { it.javaClass.simpleName }
    }

    /** Starts the engine (`cloud-scan.yml` on the default branch). Null = asked; otherwise why not. */
    suspend fun startEngine(workflow: String = WORKFLOW): String? = withContext(Dispatchers.IO) {
        if (!hasToken()) return@withContext "no GitHub token saved"
        runCatching {
            val body = buildJsonObject { put("ref", "main") }.toString().toRequestBody(JSON)
            http.newCall(req("actions/workflows/$workflow/dispatches").post(body).build()).execute().use { r ->
                note(r)
                if (r.isSuccessful) null else if (r.code == 403 || r.code == 404) "the token cannot start workflows (give it Actions: read and write)" else why(r.code, r.body?.string())
            }
        }.getOrElse { it.javaClass.simpleName }
    }

    private sealed interface Existing {
        data class Found(val sha: String) : Existing
        data object Missing : Existing
        data object NoBranch : Existing
        data class Error(val why: String) : Existing
    }

    private fun existingSha(branch: String, path: String): Existing =
        http.newCall(req("contents/${encodePath(path)}?ref=$branch").header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            note(r)
            val text = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> runCatching { Json.parseToJsonElement(text).jsonObject["sha"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.let { Existing.Found(it) } ?: Existing.Missing
                r.code == 404 -> if (branchExists(branch)) Existing.Missing else Existing.NoBranch
                else -> Existing.Error(why(r.code, text))
            }
        }

    private fun branchExists(branch: String): Boolean =
        http.newCall(req("branches/$branch").build()).execute().use { it.isSuccessful }

    /** Makes [branch] from the repository's default branch head. Null = it exists now. */
    private fun ensureBranch(branch: String): String? {
        if (branchExists(branch)) return null
        val head = http.newCall(req("git/ref/heads/main").build()).execute().use { r ->
            if (!r.isSuccessful) return why(r.code, r.body?.string())
            Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.contentOrNull
        } ?: return "could not find the default branch"
        val body = buildJsonObject { put("ref", "refs/heads/$branch"); put("sha", head) }.toString().toRequestBody(JSON)
        return http.newCall(req("git/refs").post(body).build()).execute().use { r -> if (r.isSuccessful || r.code == 422) null else why(r.code, r.body?.string()) }
    }

    private fun encodePath(p: String) = p.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    companion object {
        const val DEFAULT_REPO = "tjshea90/novig"
        const val FEED_BRANCH = "cloud-feed"
        const val CONFIG_BRANCH = "cloud-config"
        const val DATA_BRANCH = "phone-data"
        const val WORKFLOW = "cloud-scan.yml"
        private const val RAW = "application/vnd.github.raw+json"
        private val JSON = "application/json".toMediaType()
    }
}
