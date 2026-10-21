package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.ApiProbe
import com.tjshea.vigilant.data.keys.ApiProvider
import com.tjshea.vigilant.data.scanner.ScanSettings
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
)

/**
 * The engine's API monitor: every API that is switched on and has a key is checked through its own FREE account read ([ApiProbe.check]: the same call as the phone's Test now, which also lays the answer into the usage
 * ledger: credits left, a refused key marked, an accepted one cleared) every [okEveryMs]; a failing one is tried again at once and then after 30 s, 1 min, 2 min, 5 min, without touching any scan. The Pinnacle
 * boards' keys cost a real request per check (no free prompt, 100 a day), so they are checked only every [pinnEveryMs]. An API that is off, or on with no key, is listed and never called.
 */
class ApiMonitor(
    private val probe: ApiProbe,
    private val keysOf: (ApiProvider) -> List<String>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val okEveryMs: Long = 5 * 60_000L,
    private val pinnEveryMs: Long = 2 * 3_600_000L,
    private val timeoutMs: Long = 20_000L,
) {
    /** A switch id, the provider whose keys it checks (null = keyless: kalshi, polymarket) and how often a good one is checked again. */
    private data class Target(val id: String, val provider: ApiProvider?, val every: Long)

    private val targets = listOf(
        Target("sgo", ApiProvider.SPORTSGAMEODDS, okEveryMs), Target("boltodds", ApiProvider.BOLTODDS, okEveryMs), Target("oddspapi", ApiProvider.ODDSPAPI, okEveryMs),
        Target("oddspapi_free", ApiProvider.ODDSPAPI_FREE, 30 * 60_000L), Target("unabated", ApiProvider.UNABATED, 30 * 60_000L), Target("parlay", ApiProvider.PARLAY, okEveryMs),
        Target("propline", ApiProvider.PROPLINE, okEveryMs), Target("odds_api", ApiProvider.THE_ODDS_API, okEveryMs), Target("pinnwire", ApiProvider.PINNWIRE, pinnEveryMs),
        Target("pinnapi", ApiProvider.PINNAPI, pinnEveryMs), Target("sxbet", ApiProvider.SXBET, okEveryMs), Target("kalshi", null, okEveryMs), Target("polymarket", null, okEveryMs),
    )
    private val lines = java.util.concurrent.ConcurrentHashMap<String, ApiCheckLine>()
    private val dueAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** The lines as last known, in a stable order. */
    fun lines(): List<ApiCheckLine> = targets.mapNotNull { lines[it.id] }

    /** Checks whatever is due under [s] (at most [maxPerTick] APIs, so one slow answer never holds the rest), and lists every API's switch state. Never throws. */
    suspend fun tick(s: ScanSettings, maxPerTick: Int = 3) {
        val now = clock()
        var ran = 0
        for (t in targets) {
            val on = ApiToggles.isOn(s, t.id) == true
            val keys = t.provider?.let { keysOf(it) }.orEmpty()
            val hasKey = t.provider == null || keys.isNotEmpty()
            if (!on || !hasKey) {
                lines[t.id] = ApiCheckLine(t.id, ApiToggles.name(t.id), on = false, hasKey = hasKey)
                continue
            }
            val prev = lines[t.id]
            if (prev == null || !prev.on) lines[t.id] = ApiCheckLine(t.id, ApiToggles.name(t.id), on = true, hasKey = true)
            if (now < (dueAt[t.id] ?: 0L) || ran >= maxPerTick) continue
            ran++
            val before = clock()
            val result = withTimeoutOrNull(timeoutMs) {
                runCatching { if (t.provider == null) listOf(probe.reach(t.id == "kalshi")) else probe.check(t.provider, keys) }.getOrNull()
            }
            val took = clock() - before
            val old = lines[t.id] ?: ApiCheckLine(t.id, ApiToggles.name(t.id), on = true)
            val okKeys = result?.count { it.ok } ?: 0
            val ok = okKeys > 0
            val streak = if (ok) 0 else old.failStreak + 1
            val msg = when {
                result == null -> "no answer within ${timeoutMs / 1000} s"
                ok -> result.firstOrNull { it.ok }?.message.orEmpty()
                else -> result.firstOrNull()?.message.orEmpty()
            }.take(160)
            lines[t.id] = ApiCheckLine(t.id, ApiToggles.name(t.id), on = true, hasKey = true, atMs = clock(), ok = ok, keysOk = okKeys, keys = if (t.provider == null) 1 else keys.size,
                ms = took, message = msg, failStreak = streak, lastOkMs = if (ok) clock() else old.lastOkMs)
            dueAt[t.id] = clock() + if (ok) t.every else RETRY_MS[(streak - 1).coerceIn(0, RETRY_MS.lastIndex)]
        }
    }

    companion object {
        /** After a failed check: the next try comes after this long, by failures in a row. */
        val RETRY_MS = longArrayOf(0L, 30_000L, 60_000L, 120_000L, 300_000L)
    }
}
