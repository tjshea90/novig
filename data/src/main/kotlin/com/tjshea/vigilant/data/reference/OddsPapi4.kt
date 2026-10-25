package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** `GET /v4/account` (docs: get-account): the plan's own figures. */
data class Op4Account(
    val limit: Int?,
    val count: Int?,
    val validUntilMs: Long?,
    val active: Boolean,
    /** Book slug -> (live odds, player props) the plan includes. */
    val books: Map<String, Pair<Boolean, Boolean>>,
    val sportIds: List<Int>,
    val websocket: Int?,
) {
    val left: Int? get() = if (limit != null && count != null) (limit - count).coerceAtLeast(0) else null

    fun summary(): String = buildString {
        append(if (active) "plan active" else "plan NOT active")
        append(" · ").append(count ?: "?").append('/').append(limit ?: "?").append(" requests used this period")
        if (books.isNotEmpty()) append(" · books ").append(books.keys.sorted().joinToString(", "))
        if (sportIds.isNotEmpty()) append(" · sports ").append(sportIds.joinToString(","))
    }
}

/** A fixture of `/v4/fixtures` (docs: get-fixtures). Participant 1 is not necessarily the home team. */
data class Op4Fixture(val id: String, val p1: String, val p2: String, val startMs: Long?, val trueStartMs: Long?, val statusId: Int, val tournamentId: Long?)

/** One price of a `/v4/historical-odds` timeline. */
data class Op4Tick(val atMs: Long, val decimal: Double, val active: Boolean)

/** Reads OddsPapi v4's answers (ODDSPAPI_API.md addendum 2026-10-11). Defensive about every field: an unreadable row is skipped. */
object Op4Parser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.lng(k: String) = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

    fun parse(raw: String): JsonElement = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: throw ReferenceException("OddsPapi sent a reply Vigilant couldn't read")

    /** The error `code` of a failure body (`REQUEST_LIMIT_EXCEEDED`…). */
    fun errorCode(body: String): String? = Regex("\"code\"\\s*:\\s*\"([A-Za-z_]{1,60})\"").find(body)?.groupValues?.get(1)

    fun iso(s: String?): Long? = s?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    fun account(raw: String): Op4Account {
        val root = parse(raw).obj() ?: throw ReferenceException("OddsPapi: no account in the reply")
        val subs = (root["subscriptions"] as? JsonArray).orEmpty().mapNotNull { it.obj() }
        val current = root.str("current_subscription_id")
        val sub = subs.firstOrNull { it.str("subscription_id") == current } ?: subs.firstOrNull { it.bool("is_active") == true } ?: subs.firstOrNull()
            ?: return Op4Account(null, null, null, false, emptyMap(), emptyList(), null)
        val books = (sub["bookmakers"].obj()).orEmpty().mapNotNull { (slug, v) -> v.obj()?.let { slug to ((it.bool("has_live_odds") ?: false) to (it.bool("has_player_props") ?: false)) } }.toMap()
        return Op4Account(
            sub.lng("request_limit")?.toInt(), sub.lng("request_count")?.toInt(), iso(sub.str("valid_until")), sub.bool("is_active") ?: true, books,
            (sub["sport_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }, sub.lng("websocket_access")?.toInt(),
        )
    }

    fun fixtures(raw: String): List<Op4Fixture> = ((parse(raw) as? JsonArray) ?: (parse(raw).obj()?.get("data") as? JsonArray)).orEmpty().mapNotNull { e ->
        val o = e.obj() ?: return@mapNotNull null
        Op4Fixture(
            o.str("fixtureId") ?: return@mapNotNull null, o.str("participant1Name").orEmpty(), o.str("participant2Name").orEmpty(), iso(o.str("startTime")), iso(o.str("trueStartTime")),
            o.lng("statusId")?.toInt() ?: 0, o.lng("tournamentId"),
        )
    }

    /** The timeline of [outcomeId] for [slug] at player 0 (newest first, as the service sends it). */
    fun ticks(raw: String, slug: String, outcomeId: Long): List<Op4Tick> {
        val markets = parse(raw).obj()?.get("bookmakers").obj()?.get(slug).obj()?.get("markets").obj() ?: return emptyList()
        val out = ArrayList<Op4Tick>()
        for ((_, market) in markets) {
            val rows = market.obj()?.get("outcomes").obj()?.get(outcomeId.toString()).obj()?.get("players").obj()?.get("0") as? JsonArray ?: continue
            for (r in rows) {
                val o = r.obj() ?: continue
                val at = iso(o.str("createdAt")) ?: continue
                val price = o.dbl("price")?.takeIf { it > 1.0 } ?: continue
                out += Op4Tick(at, price, o.bool("active") ?: true)
            }
        }
        return out
    }
}

/**
 * OddsPapi v4, the FREE plan (ODDSPAPI_API.md addendum 2026-10-11): `https://api.oddspapi.io`, key as the `apiKey` query parameter (the only way v4 takes it; never logged). Two kinds of call:
 *  - [billable]: counted against the plan's small monthly allowance, so it goes through the key pool with a reserve, and after each answer the unmetered `/v4/account` is read for the plan's REAL figures
 *    (the meter then uses the server's count, not a guess). A `429 REQUEST_LIMIT_EXCEEDED` marks the key spent until the period ends.
 *  - [free]: `/v4/historical-odds` and `/v4/account` never count and are never blocked, so they bypass the quota and try each key.
 * Each endpoint keeps its documented cooldown (1 s; 2 s settlements; 5 s historical odds).
 */
class OddsPapi4Client(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val keys: () -> List<String>,
    private val baseUrl: String = "https://api.oddspapi.io",
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val gates = HashMap<String, Long>()
    private val gate = Mutex()

    @Volatile var lastAccount: Op4Account? = null
        private set
    @Volatile var lastError: String? = null
        private set

    /** The last `/v4/account` answer with the key (and the `api_key` field) replaced, for a saved sample. */
    @Volatile var lastAccountRaw: String = ""
        private set
    @Volatile var requests = 0
        private set
    @Volatile private var spentDay = 0L
    @Volatile private var spentToday = 0

    /** Billable requests this phone made today (UTC), for the daily share ([Op4Budget]). */
    fun spentToday(now: Long = clock()): Int = if (spentDay == now / DAY_MS) spentToday else 0

    fun hasKey() = keys().isNotEmpty()

    /** The saved keys, for the key test's v4/v5 check (never logged). */
    fun keyList(): List<String> = keys()

    fun health(): String = lastAccount?.let { "answering · ${it.summary()}" } ?: (lastError ?: "no answer yet")

    private fun cooldown(path: String) = when {
        path.contains("historical-odds") -> 5_000L
        path.contains("settlements") -> 2_000L
        else -> 1_000L
    }

    private suspend fun pace(path: String) = gate.withLock {
        val wait = (gates[path] ?: 0L) + cooldown(path) - clock()
        if (wait > 0) sleep(wait)
        gates[path] = clock()
    }

    private suspend fun fetch(key: String, path: String, params: List<Pair<String, String>>, headers: List<Pair<String, String>> = emptyList()): Triple<Int, String, Map<String, String>> {
        pace(path)
        requests++
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) }; addQueryParameter("apiKey", key) }.build()
        val req = Request.Builder().url(url).header("Accept", "application/json").apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()
        return http.newCall(req).await().use { r -> Triple(r.code, r.body?.string().orEmpty(), mapOf("etag" to r.header("ETag").orEmpty())) }
    }

    /** The plan's figures; unmetered, so it works at the limit. */
    suspend fun account(key: String? = null): Op4Account {
        var failure: Exception? = null
        for (k in listOfNotNull(key).ifEmpty { keys() }) {
            try {
                val (code, body, _) = fetch(k, "/v4/account", emptyList())
                if (code == 401 || code == 403) { failure = ReferenceException("OddsPapi Free: key refused (HTTP $code)"); continue }
                if (code !in 200..299) { failure = ReferenceException("OddsPapi Free HTTP $code"); continue }
                lastAccountRaw = body.replace(k, "<key>").replace(Regex("\"api_key\"\\s*:\\s*\"[^\"]*\""), "\"api_key\":\"<key>\"")
                return Op4Parser.account(body).also { lastAccount = it; lastError = null }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failure = e }
        }
        lastError = failure?.message?.take(160) ?: "no key"
        throw failure ?: ReferenceException("OddsPapi Free: no key")
    }

    /** A call that costs one request of the monthly allowance. [reserve] requests are kept back for the callers that matter more. */
    suspend fun billable(path: String, params: List<Pair<String, String>>, reserve: Int = 0): String {
        val body = pool.execute(cost = 1, reserve = reserve) { key ->
            val (code, text, _) = fetch(key, path, params)
            val code4 = Op4Parser.errorCode(text).orEmpty()
            when {
                code == 401 -> KeyAttemptResult.Invalid("HTTP 401, key refused")
                code == 403 -> throw ReferenceException("OddsPapi Free HTTP 403: your plan does not include this")
                // Only the documented quota code means the month is spent; any other 429 is the per-endpoint cooldown (1 s) and the key is merely asked to wait.
                code == 429 && code4.equals("REQUEST_LIMIT_EXCEEDED", true) -> KeyAttemptResult.Depleted("monthly request limit used up", null)
                code == 429 -> KeyAttemptResult.RateLimited(1_500L, "slow down (HTTP 429)")
                code in 500..599 -> throw ReferenceException("OddsPapi Free HTTP $code")
                code !in 200..299 -> { countSpend(); throw ReferenceException("OddsPapi Free HTTP $code: ${code4.ifEmpty { "refused the request" }}") }
                else -> {
                    countSpend()
                    // The unmetered account tells the plan's real count, so the meter and the budget never guess.
                    val acc = runCatching { account(key) }.getOrNull()
                    KeyAttemptResult.Success(text, cost = 1, remaining = acc?.left, used = acc?.count, resetAtMs = acc?.validUntilMs)
                }
            }
        }
        return body
    }

    private fun countSpend() {
        val day = clock() / DAY_MS
        if (spentDay != day) { spentDay = day; spentToday = 0 }
        spentToday++
    }

    /** A call that never counts (historical odds): tried with each key; null body when the answer is 304. */
    suspend fun free(path: String, params: List<Pair<String, String>>): String {
        var failure: Exception? = null
        for (k in keys()) {
            try {
                val (code, text, _) = fetch(k, path, params)
                when {
                    code == 401 -> failure = ReferenceException("OddsPapi Free: key refused")
                    code == 429 -> failure = ReferenceException("OddsPapi Free: slow down (HTTP 429)")
                    code !in 200..299 -> failure = ReferenceException("OddsPapi Free HTTP $code")
                    else -> return text
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failure = e }
        }
        throw failure ?: ReferenceException("OddsPapi Free: no key")
    }

    /** The raw body of a billable or free path for Test key's sample. */
    companion object { const val DAY_MS = 86_400_000L }
}

/**
 * How much of the plan's allowance may be spent, from the plan's own figures ([Op4Account], refreshed after every billable call and by Test key). Billable calls (the game list, the market catalogue,
 * tournaments) are for the closing-line mapping only: they stop at [RESERVE] requests left, and each day may spend at most an even share of what is left until the period ends. Unknown figures (before the first
 * `/v4/account`) allow one call, whose answer fills them in.
 */
class Op4Budget(private val client: OddsPapi4Client, private val clock: () -> Long = System::currentTimeMillis) {
    fun mayBuy(): Boolean {
        val acc = client.lastAccount ?: return true
        val left = acc.left ?: return true
        if (!acc.active || left <= RESERVE) return false
        val now = clock()
        val dailyShare = ((left - RESERVE) / daysLeft(acc, now)).coerceAtLeast(1)
        return client.spentToday(now) < dailyShare
    }

    /** Days until the plan's period ends, counting today (at least 1): the month's end unless the account says when it is valid until. */
    private fun daysLeft(acc: Op4Account, now: Long): Int {
        val end = acc.validUntilMs?.takeIf { it > now } ?: java.time.ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC).let { it.withDayOfMonth(1).plusMonths(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toInstant().toEpochMilli() }
        return (((end - now) / OddsPapi4Client.DAY_MS) + 1).toInt().coerceAtLeast(1)
    }

    /** A cached lookup (tournament id, game list, market catalogue) is allowed while more than [RESERVE] requests are left. */
    fun mayBuyLookup(): Boolean {
        val acc = client.lastAccount ?: return true
        val left = acc.left ?: return true
        return acc.active && left > RESERVE
    }

    /**
     * Whether the fair-odds feed may buy one `/v4/odds` read now ([perDay]: the daily figure Tj chose, 0 = never; [spentToday]: its reads so far today). It never spends the last [RESERVE] + [CLOSE_KEEP] requests of the
     * period: those stay for the closing lines, which matter more.
     */
    fun mayBuyFair(perDay: Int, spentToday: Int): Boolean {
        if (perDay <= 0 || spentToday >= perDay) return false
        val acc = client.lastAccount ?: return true
        val left = acc.left ?: return true
        if (!acc.active || left <= RESERVE + CLOSE_KEEP) return false
        // The figure Tj chose cannot outrun the plan: at most an even share of what is left above the two reserves for each day to the period's end (Tj's file 2026-10-11 23:18: "250 requests a day" on a plan of
        // 250 a MONTH spent 220 in a day and left nothing for closing lines).
        val share = ((left - RESERVE - CLOSE_KEEP) / daysLeft(acc, clock())).coerceAtLeast(1)
        return spentToday < share
    }

    companion object {
        /** Requests of the allowance never spent by the app: left for Tj's own use of the key. */
        const val RESERVE = 10

        /** Requests the fair-odds feed leaves for closing lines. */
        const val CLOSE_KEEP = 25
    }
}

/** A tiny persistent cache (a JSON file in the app's folder): the catalogue and tournament ids cost billable requests, so they survive restarts. `file == null` keeps it in memory (tests). */
class Op4Store(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
    @kotlinx.serialization.Serializable
    private class Entry(val at: Long, val v: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    init {
        runCatching { file?.takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Map<String, Entry>>(it) } }.getOrNull()?.let { entries.putAll(it) }
        // Tj's 2026-10-13 file: 17 MB, rewritten whole on every change and held in memory whole. Entries past what any reader would still use are dropped at once, not only when a new one is saved.
        if (prune()) persist()
    }

    /** How long an entry is worth keeping: its reader's own limit ([OddsPapi4Client]'s window, whole-league and month caches) plus a margin. Unknown keys keep the month. */
    private fun keepFor(key: String): Long = when {
        key.startsWith("fxall|") -> 6 * 3_600_000L
        key.startsWith("fx|") -> 24 * 3_600_000L
        key.startsWith("fair|") -> 2 * OddsPapi4Client.DAY_MS
        else -> 31 * OddsPapi4Client.DAY_MS
    }

    /** Drops the entries past their keep time; true when something went. */
    private fun prune(): Boolean = entries.entries.removeIf { clock() - it.value.at > keepFor(it.key) }

    private fun persist() {
        runCatching { file?.let { f -> val tmp = File(f.path + ".tmp"); tmp.writeText(json.encodeToString(entries.toMap())); tmp.renameTo(f) } }
    }

    fun get(key: String, maxAgeMs: Long): String? = entries[key]?.takeIf { clock() - it.at < maxAgeMs }?.v

    fun put(key: String, value: String) {
        entries[key] = Entry(clock(), value)
        prune()
        persist()
    }

    /** Entries held, for tests and Diagnostics. */
    val size: Int get() = entries.size
}

/**
 * The lookups every OddsPapi v4 use needs and that cost BILLABLE requests, each cached on disk ([Op4Store]) and gated by [Op4Budget]: a league's tournament id and the market catalogue (30 days), and a league's game list
 * (12 hours per window). Shared by the closing-line source ([com.tjshea.vigilant.data.tracker.Op4Closes]) and the fair-odds source ([Op4Feed]).
 */
class Op4Catalog(
    private val client: OddsPapi4Client,
    private val budget: Op4Budget,
    private val store: Op4Store,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The fair-odds feed has its own daily figure, so its lookups (a handful of cached requests) are held only by the reserve, not by the closing lines' daily share. */
    private val lenient: Boolean = false,
) {
    private fun may(): Boolean = if (lenient) budget.mayBuyLookup() else budget.mayBuy()

    private val marketsHeld = java.util.concurrent.ConcurrentHashMap<String, OpMarkets>()

    private fun held() = com.tjshea.vigilant.data.keys.CreditsHeldBackException("OddsPapi Free's daily share is spent")

    suspend fun tournamentId(league: com.tjshea.vigilant.data.scanner.League, sport: Int): Long? {
        val raw = store.get("tournaments|$sport", MONTH_MS) ?: run {
            if (!may()) throw held()
            client.billable("/v4/tournaments", listOf("sportId" to sport.toString()), reserve = Op4Budget.RESERVE).also { store.put("tournaments|$sport", it) }
        }
        return OpBooks.tournamentFor(league, OpParser.tournaments(raw))?.id
    }

    /** The league's games within 3 hours of [startMs]: one request for the 12 hours from the 6-hour mark before it, cached 12 hours. */
    suspend fun fixturesAround(tournament: Long, startMs: Long): List<Op4Fixture> = chunk(tournament, (startMs - GAP_MS) / WINDOW_MS)

    /** The league's games between [fromMs] and [toMs]: 12-hour chunks, one request each, cached 12 hours. */
    suspend fun fixturesBetween(tournament: Long, fromMs: Long, toMs: Long): List<Op4Fixture> {
        // The documented one-request form (ODDSPAPI_API.md: `/fixtures` with `tournamentId` alone lists every fixture of the league): one request for the whole window instead of one per 12 hours
        // (Tj's Diagnostics 2026-10-11: 123 of 250 monthly requests gone in a day, 75 of 91 /v4/fixtures calls refused). Any trouble other than "no fixtures" falls back to the windows.
        val whole = try { wholeLeague(tournament) } catch (e: com.tjshea.vigilant.data.keys.AllKeysExhaustedException) { throw e } catch (e: com.tjshea.vigilant.data.keys.CreditsHeldBackException) { throw e } catch (e: ReferenceException) { null }
        if (whole != null) return whole.filter { f -> (f.trueStartMs ?: f.startMs)?.let { it in fromMs..toMs } == true }
        val out = ArrayList<Op4Fixture>()
        var w = fromMs / WINDOW_MS
        while (w * WINDOW_MS <= toMs) {
            out += chunk(tournament, w)
            w += 2
        }
        return out.distinctBy { it.id }
    }

    /** Every fixture of [tournament] in one request, cached [WHOLE_MS]; an empty league (404 FIXTURE_NOT_FOUND) is an empty list kept like any answer. */
    private suspend fun wholeLeague(tournament: Long): List<Op4Fixture> {
        val key = "fxall|$tournament"
        val raw = store.get(key, WHOLE_MS) ?: run {
            if (!may()) throw held()
            try {
                client.billable("/v4/fixtures", listOf("tournamentId" to tournament.toString()), reserve = Op4Budget.RESERVE)
            } catch (e: ReferenceException) {
                if (e.message?.contains("FIXTURE_NOT_FOUND") == true) "[]" else throw e
            }.also { store.put(key, it) }
        }
        return Op4Parser.fixtures(raw)
    }

    private suspend fun chunk(tournament: Long, w: Long): List<Op4Fixture> {
        val key = "fx|$tournament|$w"
        val raw = store.get(key, FIXTURES_MS) ?: run {
            if (!may()) throw held()
            // No game in the window comes back as 404 FIXTURE_NOT_FOUND (Tj's Diagnostics, 2026-10-11): an empty window, kept like any other answer, never a failed scan.
            try {
                client.billable(
                    "/v4/fixtures",
                    listOf("tournamentId" to tournament.toString(), "from" to Instant.ofEpochMilli(w * WINDOW_MS).toString(), "to" to Instant.ofEpochMilli((w + 2) * WINDOW_MS).toString()),
                    reserve = Op4Budget.RESERVE,
                )
            } catch (e: ReferenceException) {
                if (e.message?.contains("FIXTURE_NOT_FOUND") == true) "[]" else throw e
            }.also { store.put(key, it) }
        }
        return Op4Parser.fixtures(raw)
    }

    suspend fun markets(league: com.tjshea.vigilant.data.scanner.League, sport: Int): OpMarkets {
        marketsHeld[league.novigName]?.let { return it }
        val raw = store.get("markets", MONTH_MS) ?: run {
            if (!may()) throw held()
            // The whole catalogue (every sport) is megabytes: cut it element by element and keep only the sports Vigilant bets.
            val all = client.billable("/v4/markets", emptyList(), reserve = Op4Budget.RESERVE)
            val parts = com.tjshea.vigilant.data.JsonSplit.elements(all)
            val kept = if (parts == null) all else (0 until parts.size).map { parts.element(it) }.filter { e -> SPORT_FIELD.find(e)?.groupValues?.get(1)?.toIntOrNull() in SPORTS }.joinToString(",", "[", "]")
            kept.also { store.put("markets", it) }
        }
        val rows = Regex("\"sportId\"\\s*:\\s*$sport\\b")
        val parts = com.tjshea.vigilant.data.JsonSplit.elements(raw)
        val mine = if (parts == null) raw else (0 until parts.size).map { parts.element(it) }.filter { rows.containsMatchIn(it) }.joinToString(",", "[", "]")
        return OpMarkets(OpParser.markets(mine), league.novigName).also { marketsHeld[league.novigName] = it }
    }

    companion object {
        const val GAP_MS = 3 * 3_600_000L
        const val WINDOW_MS = 6 * 3_600_000L
        const val FIXTURES_MS = 12 * 3_600_000L
        /** A league's whole fixture list is read again after this (a game's status changes, new games are listed). */
        const val WHOLE_MS = 3 * 3_600_000L
        const val MONTH_MS = 30 * 86_400_000L

        /** Basketball 11, baseball 13, American football 14, ice hockey 15 (docs: get-account's sport ids). */
        val SPORTS = setOf(11, 13, 14, 15)
        private val SPORT_FIELD = Regex("\"sportId\"\\s*:\\s*(\\d+)")
    }
}
