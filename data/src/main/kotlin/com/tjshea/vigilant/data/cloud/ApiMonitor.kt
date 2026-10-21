package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.ApiProbe
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * One API's last live check by the engine, for the Engine > APIs cards (Tj, 2026-10-20, CH48: "real, fresh information that is updated frequently that shows accurate usage and API status ... green ... yellow ...
 * red"; and a ParlayAPI "will not last" warning that was wrong because the engine read each key's plan only once an hour). [on] false = the switch is off or there is no key: the API is never called, never checked.
 */
@Serializable
data class ApiCheckLine(
    val id: String,
    val name: String,
    val on: Boolean,
    val hasKey: Boolean = true,
    val atMs: Long = 0L,
    /** The last check found the API reachable and at least one key accepted. */
    val ok: Boolean = false,
    val keysOk: Int = 0,
    val keys: Int = 0,
    /** How long the last check took, ms. */
    val ms: Long = 0L,
    val message: String = "",
    /** Checks failed in a row (0 = the last one passed). */
    val failStreak: Int = 0,
    val lastOkMs: Long = 0L,
    /** Why the keys that failed failed (XO1): the commonest [ApiProbe.Check.Kind] among them, lower case ("refused", "unreachable", "error"); empty when no key failed. */
    val kind: String = "",
    /** Keys the provider refused, keys that did not answer, and keys that answered but are spent for now, in the last check. */
    val refused: Int = 0,
    val unreachable: Int = 0,
    val spent: Int = 0,
    /** The newest scan that read data from this API (its feeds fetched or reused something), by the engine's clock; 0 = none yet in this job. Filled by [ApiManager]; the check itself does not know. */
    val scanOkAtMs: Long = 0L,
    /** What the API manager is doing about a failure, in words ("restarted its connection", "asked for a fresh runner (1 of 3)", "switched off: not accepted on 3 new runners"); empty when nothing is wrong. */
    val fix: String = "",
)

/** What the [ApiManager] needs of a monitor (so its ladder can be tested with scripted lines): the lines, a turn of checking, and "check it now". */
interface ApiChecks {
    fun lines(): List<ApiCheckLine>
    suspend fun tick(s: ScanSettings, maxPerTick: Int = 6)
    fun recheckNow(id: String? = null)
}

/**
 * The engine's API monitor: every API that is switched on and has a key is checked through its own FREE account read ([ApiProbe.check]: the same call as the phone's Test now, which also lays the answer into the usage
 * ledger: credits left, a refused key marked, an accepted one cleared) every [okEveryMs]; a failing one is tried again at once and then after 30 s, 1 min, 2 min, 5 min, without touching any scan. The Pinnacle
 * boards' keys cost a real request per check (no free prompt, 100 a day), so they are checked only every [pinnEveryMs]. An API that is off, or on with no key, is listed and never called.
 *
 * Since 2026-10-21 (XO1, Tj: "some of the apis claim to be failing, but when I test the keys they are green and accepted"): the due APIs are checked AT THE SAME TIME, every key has a deadline of its own
 * ([ApiProbe.check]) so one silent key no longer sinks the whole check, ParlayAPI's ten keys are ten reads (they were a hundred, past the old 20 s allowance: "no answer within 20 s" while the scans used it fine), and
 * a line says WHY a check failed ([ApiCheckLine.kind]) so [ApiManager] can restart, replace the runner or switch the API off for the right reason. [recheckNow] makes every API due at once (a pull to refresh).
 */
class ApiMonitor(
    private val probe: ApiProbe,
    private val keysOf: (ApiProvider) -> List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val okEveryMs: Long = 5 * 60_000L,
    private val pinnEveryMs: Long = 2 * 3_600_000L,
    private val timeoutMs: Long = 75_000L,
    private val keyTimeoutMs: Long = ApiProbe.KEY_TIMEOUT_MS,
) : ApiChecks {
    /** A switch id, the provider whose keys it checks (null = keyless: kalshi, polymarket) and how often a good one is checked again. */
    private data class Target(val id: String, val provider: ApiProvider?, val every: Long)

    private val targets = listOf(
        Target("sgo", ApiProvider.SPORTSGAMEODDS, okEveryMs), Target("boltodds", ApiProvider.BOLTODDS, okEveryMs), Target("oddspapi", ApiProvider.ODDSPAPI, okEveryMs),
        Target("oddspapi_free", ApiProvider.ODDSPAPI_FREE, 30 * 60_000L), Target("unabated", ApiProvider.UNABATED, 30 * 60_000L), Target("parlay", ApiProvider.PARLAY, okEveryMs),
        Target("propline", ApiProvider.PROPLINE, okEveryMs), Target("odds_api", ApiProvider.THE_ODDS_API, okEveryMs), Target("pinnwire", ApiProvider.PINNWIRE, pinnEveryMs),
        Target("pinnapi", ApiProvider.PINNAPI, pinnEveryMs), Target("sxbet", ApiProvider.SXBET, okEveryMs), Target("sharpapi", ApiProvider.SHARPAPI, okEveryMs), Target("kalshi", null, okEveryMs), Target("polymarket", null, okEveryMs),
    )
    private val lines = java.util.concurrent.ConcurrentHashMap<String, ApiCheckLine>()
    private val dueAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** The lines as last known, in a stable order. */
    override fun lines(): List<ApiCheckLine> = targets.mapNotNull { lines[it.id] }

    /** One API's line, or null when it has none yet. */
    fun line(id: String): ApiCheckLine? = lines[id]

    /** The ids the monitor knows. */
    fun ids(): List<String> = targets.map { it.id }

    /**
     * Makes [id] (or every API) due at once: the next [tick] checks it whatever its schedule says. The Pinnacle boards, which cost a real request per key, are left on their own schedule unless they are
     * failing or have never been checked: a pull to refresh must not spend their day's allowance.
     */
    override fun recheckNow(id: String?) {
        for (t in targets) {
            if (id != null && t.id != id) continue
            val l = lines[t.id]
            if (id == null && t.every == pinnEveryMs && l != null && l.ok && l.atMs > 0L) continue
            dueAt[t.id] = 0L
        }
    }

    /**
     * Checks whatever is due under [s] (at most [maxPerTick] APIs at once, so a hung provider never holds the rest), and lists every API's switch state. Never throws. Each API's check has [timeoutMs] in all and
     * every key its own deadline, so a partial answer (some keys fine, one silent) is kept instead of being thrown away with the slow one.
     */
    override suspend fun tick(s: ScanSettings, maxPerTick: Int) {
        val now = clock()
        val due = ArrayList<Pair<Target, List<String>>>()
        for (t in targets) {
            val on = ApiToggles.isOn(s, t.id) == true
            val keys = t.provider?.let { keysOf(it) }.orEmpty()
            val hasKey = t.provider == null || keys.isNotEmpty()
            if (!on || !hasKey) {
                lines[t.id] = ApiCheckLine(t.id, ApiToggles.name(t.id), on = false, hasKey = hasKey)
                dueAt.remove(t.id)
                continue
            }
            val prev = lines[t.id]
            if (prev == null || !prev.on) lines[t.id] = ApiCheckLine(t.id, ApiToggles.name(t.id), on = true, hasKey = true)
            if (now < (dueAt[t.id] ?: 0L) || due.size >= maxPerTick) continue
            due += t to keys
        }
        if (due.isEmpty()) return
        val gate = Semaphore(4)
        coroutineScope { due.map { (t, keys) -> async { gate.withPermit { runCatching { checkOne(t, keys) } } } }.awaitAll() }
    }

    private suspend fun checkOne(t: Target, keys: List<String>) {
        val before = clock()
        val result = withTimeoutOrNull(timeoutMs) {
            runCatching { if (t.provider == null) listOf(probe.reach(t.id == "kalshi")) else probe.check(t.provider, keys, keyTimeoutMs) }.getOrNull()
        }
        val took = clock() - before
        val old = lines[t.id] ?: ApiCheckLine(t.id, ApiToggles.name(t.id), on = true)
        val checks = result.orEmpty()
        val okKeys = checks.count { it.ok }
        val ok = okKeys > 0
        val streak = if (ok) 0 else old.failStreak + 1
        val failing = checks.filter { !it.ok }
        val kind = when {
            result == null -> ApiProbe.Check.Kind.UNREACHABLE
            failing.isEmpty() -> null
            else -> failing.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }?.key
        }
        val msg = when {
            result == null -> "no answer within ${timeoutMs / 1000} s"
            ok -> checks.firstOrNull { it.ok }?.message.orEmpty()
            else -> checks.firstOrNull()?.message.orEmpty()
        }.take(160)
        lines[t.id] = ApiCheckLine(
            t.id, ApiToggles.name(t.id), on = true, hasKey = true, atMs = clock(), ok = ok, keysOk = okKeys, keys = if (t.provider == null) 1 else keys.size,
            ms = took, message = msg, failStreak = streak, lastOkMs = if (ok) clock() else old.lastOkMs,
            kind = kind?.name?.lowercase().orEmpty(),
            refused = checks.count { it.kind == ApiProbe.Check.Kind.REFUSED }, unreachable = if (result == null) keys.size else checks.count { it.kind == ApiProbe.Check.Kind.UNREACHABLE },
            spent = checks.count { it.kind == ApiProbe.Check.Kind.SPENT },
            scanOkAtMs = old.scanOkAtMs, fix = old.fix,
        )
        dueAt[t.id] = clock() + if (ok) t.every else RETRY_MS[(streak - 1).coerceIn(0, RETRY_MS.lastIndex)]
    }

    companion object {
        /** After a failed check: the next try comes after this long, by failures in a row. */
        val RETRY_MS = longArrayOf(0L, 30_000L, 60_000L, 120_000L, 300_000L)
    }
}
