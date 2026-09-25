package com.tjshea.vigilant.data.keys

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class QuotaPeriod { MONTH_UTC, DAY_UTC }

/**
 * One provider's limits, straight from its own docs (RESEARCH.md §12). Keyed providers have a
 * per-key allowance that resets every [period]; keyless ones only get counted (requests today).
 */
data class QuotaPolicy(
    val id: String,
    val displayName: String,
    val unit: String,
    val keyed: Boolean,
    val period: QuotaPeriod = QuotaPeriod.DAY_UTC,
    /** Allowance per key per period, when the provider doesn't say (a free key). */
    val defaultLimit: Int? = null,
    val perMinute: Int? = null,
    val perHour: Int? = null,
    /** One line for the meter: what the provider allows. */
    val rule: String,
) {
    fun periodStart(now: Long): Long {
        val t = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
        val start = when (period) {
            QuotaPeriod.MONTH_UTC -> t.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS)
            QuotaPeriod.DAY_UTC -> t.truncatedTo(ChronoUnit.DAYS)
        }
        return start.toInstant().toEpochMilli()
    }

    fun nextReset(periodStart: Long): Long {
        val t = Instant.ofEpochMilli(periodStart).atZone(ZoneOffset.UTC)
        return when (period) {
            QuotaPeriod.MONTH_UTC -> t.plusMonths(1)
            QuotaPeriod.DAY_UTC -> t.plusDays(1)
        }.toInstant().toEpochMilli()
    }

    companion object {
        val ODDS_API = QuotaPolicy(
            "oddsapi", "The Odds API", "credits", keyed = true, period = QuotaPeriod.MONTH_UTC, defaultLimit = 500,
            rule = "500 credits a month per free key, reset on the 1st. A scan costs 1 credit per market type per league.",
        )
        val PINNAPI = QuotaPolicy(
            "pinnacle", "Pinnacle (pinnapi)", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 100,
            perMinute = 20, perHour = 100,
            rule = "100 requests a day per trial key (20 a minute), reset at midnight UTC. About 1 per sport per scan.",
        )
        val NOVIG = QuotaPolicy("novig", "Novig", "requests", keyed = false, rule = "No quota. Read at 4 a second to stay under Novig's per-network limit.")
        val POLYMARKET = QuotaPolicy("polymarket", "Polymarket", "requests", keyed = false, rule = "No quota or key. Allows 300 requests per 10 seconds.")
        val KALSHI = QuotaPolicy("kalshi", "Kalshi", "requests", keyed = false, rule = "No quota or key. Allows 20 requests a second.")

        val ALL = listOf(ODDS_API, PINNAPI, NOVIG, POLYMARKET, KALSHI)
    }
}

/** One key's usage in its current period. Persisted, so it survives restarts and updates. */
@Serializable
data class KeyUsage(
    val periodStart: Long = 0,
    /** Units used this period: the server's count when it sends one, our own otherwise. */
    val used: Int = 0,
    /** The server's own "remaining" figure from the last call, when it sends one. */
    val remaining: Int? = null,
    /** The key's allowance: used + remaining from the server, else the provider's default. */
    val limit: Int? = null,
    /** Spent (or refused as spent) until this time. */
    val depletedUntil: Long? = null,
    /** Briefly throttled until this time (a per-minute window or a burst 429). */
    val coolUntil: Long? = null,
    val calls: Int = 0,
    val lastCallMs: Long? = null,
    val lastCost: Int? = null,
    val lastNote: String? = null,
    /** Call times in the last hour, for per-minute and per-hour windows. */
    val recent: List<Long> = emptyList(),
) {
    fun left(policy: QuotaPolicy): Int? = remaining ?: (limit ?: policy.defaultLimit)?.let { (it - used).coerceAtLeast(0) }
    fun allowance(policy: QuotaPolicy): Int? = limit ?: policy.defaultLimit
}

@Serializable
data class ProviderUsage(
    val keys: Map<String, KeyUsage> = emptyMap(),
    /** Keyless providers (and all calls, for keyed ones): requests since midnight UTC. */
    val dayStart: Long = 0,
    val callsToday: Int = 0,
    val throttledToday: Int = 0,
    val lastThrottleMs: Long? = null,
)

@Serializable
data class UsageBook(val providers: Map<String, ProviderUsage> = emptyMap())

/**
 * The usage ledger behind the meters and key rotation. Every call a provider answers updates it,
 * and the UI observes [flow], so the meters move after every call. Stored in `usage.json`.
 */
class UsageMeter(
    private val store: JsonFileStore<UsageBook>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(UsageBook())
    private var loaded = false

    val flow: StateFlow<UsageBook> = state.asStateFlow()

    suspend fun load() = mutex.withLock { ensureLoaded() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        state.value = runCatching { store.read() }.getOrDefault(UsageBook())
        loaded = true
    }

    /** A key's usage as of now: a new period starts fresh, expired holds are lifted. */
    fun rolled(policy: QuotaPolicy, u: KeyUsage, now: Long): KeyUsage {
        var x = u
        if (x.periodStart == 0L) x = x.copy(periodStart = policy.periodStart(now))
        if (now >= policy.nextReset(x.periodStart)) {
            x = x.copy(periodStart = policy.periodStart(now), used = 0, calls = 0, remaining = null, lastNote = null)
        }
        // A hold that ran out means "try again": a remembered zero no longer applies.
        if (x.depletedUntil != null && now >= x.depletedUntil) x = x.copy(depletedUntil = null, remaining = x.remaining?.takeIf { it > 0 })
        if (x.coolUntil != null && now >= x.coolUntil) x = x.copy(coolUntil = null)
        if (x.recent.any { now - it >= HOUR }) x = x.copy(recent = x.recent.filter { now - it < HOUR })
        return x
    }

    private fun usable(policy: QuotaPolicy, u: KeyUsage, cost: Int, now: Long): Boolean {
        if (u.depletedUntil != null || u.coolUntil != null) return false
        val left = u.left(policy)
        if (left != null && left < cost.coerceAtLeast(1)) return false
        policy.perMinute?.let { cap -> if (u.recent.count { now - it < MINUTE } >= cap) return false }
        policy.perHour?.let { cap -> if (u.recent.size >= cap) return false }
        return true
    }

    /**
     * The first key, in Tj's order, that can afford a call costing [cost] right now. Because it
     * always starts from key 1, rotation falls back to key 1 as soon as its period resets.
     */
    suspend fun pick(policy: QuotaPolicy, keys: List<String>, cost: Int): String? = mutex.withLock {
        ensureLoaded()
        val now = clock()
        val p = provider(policy.id)
        val updated = p.keys.toMutableMap()
        var chosen: String? = null
        for (k in keys) {
            val u = rolled(policy, p.keys[k] ?: KeyUsage(), now)
            updated[k] = u
            if (chosen == null && usable(policy, u, cost, now)) chosen = k
        }
        put(policy.id, p.copy(keys = updated))
        chosen
    }

    /** A call the provider answered. [serverUsed]/[serverRemaining] are its own figures, if sent. */
    suspend fun recordCall(policy: QuotaPolicy, key: String, cost: Int, serverRemaining: Int? = null, serverUsed: Int? = null) = edit(policy, key) { u, now ->
        var x = u.copy(calls = u.calls + 1, lastCallMs = now, lastCost = cost, recent = u.recent + now, lastNote = null)
        if (serverUsed != null && serverRemaining != null) {
            // The server's count went DOWN without our period changing: this key runs on its own
            // cycle (a paid plan's billing date), so follow the server from here.
            if (serverUsed + cost < u.used) x = x.copy(periodStart = now)
            x = x.copy(used = serverUsed, remaining = serverRemaining, limit = serverUsed + serverRemaining)
        } else {
            x = x.copy(used = u.used + cost, remaining = null)
        }
        // Nothing left: rest the key until its reset instead of spending a refused call on it.
        if (x.left(policy) == 0) x = x.copy(depletedUntil = policy.nextReset(x.periodStart))
        x
    }

    /**
     * The provider refused the key as used up. With [retryAfterMs] (pinnapi says when) that's the
     * hold. Otherwise until the period's reset, unless the key was refused on its first call of a
     * fresh period: then its reset is later than the calendar says, so it's re-tried in 6 hours.
     */
    suspend fun recordDepleted(policy: QuotaPolicy, key: String, note: String, retryAfterMs: Long? = null) = edit(policy, key) { u, now ->
        val until = when {
            retryAfterMs != null -> now + retryAfterMs
            u.calls == 0 && now - u.periodStart < RESET_GRACE -> now + REPROBE
            else -> policy.nextReset(u.periodStart)
        }
        u.copy(depletedUntil = until, remaining = 0, lastNote = note, lastCallMs = now, recent = u.recent + now)
    }

    /** A short throttle (per-minute window, burst limit). The key is fine after [retryAfterMs]. */
    suspend fun recordThrottled(policy: QuotaPolicy, key: String, retryAfterMs: Long, note: String) = edit(policy, key) { u, now ->
        u.copy(coolUntil = now + retryAfterMs, lastNote = note, lastCallMs = now, recent = u.recent + now)
    }.also { countKeyless(policy, calls = 0, throttled = 1) }

    /** The key itself was refused (wrong, deleted, deactivated). Skipped until the next period. */
    suspend fun recordRejected(policy: QuotaPolicy, key: String, note: String) = edit(policy, key) { u, now ->
        u.copy(depletedUntil = policy.nextReset(u.periodStart), lastNote = note, lastCallMs = now)
    }

    /** Requests (and throttles) to any provider, for the "today" counters. In memory until [flush]. */
    suspend fun countKeyless(policy: QuotaPolicy, calls: Int = 1, throttled: Int = 0) = mutex.withLock {
        ensureLoaded()
        val now = clock()
        val today = QuotaPolicy.NOVIG.periodStart(now)
        val p = provider(policy.id).let { if (it.dayStart != today) it.copy(dayStart = today, callsToday = 0, throttledToday = 0) else it }
        put(
            policy.id,
            p.copy(
                callsToday = p.callsToday + calls,
                throttledToday = p.throttledToday + throttled,
                lastThrottleMs = if (throttled > 0) now else p.lastThrottleMs,
            ),
        )
    }

    /** Saves the ledger (keyed calls save as they happen; keyless counts save here, once a scan). */
    suspend fun flush() = mutex.withLock { persist() }

    /** Why nothing could be picked, for the error line. */
    suspend fun exhaustedMessage(policy: QuotaPolicy, keys: List<String>, lastProblem: String?): String = mutex.withLock {
        ensureLoaded()
        if (keys.isEmpty()) return@withLock "No ${policy.displayName} key. Add one in Settings."
        val now = clock()
        val p = provider(policy.id)
        val next = keys.mapNotNull { k -> p.keys[k]?.let { u -> listOfNotNull(u.depletedUntil, u.coolUntil).maxOrNull() } }.filter { it > now }.minOrNull()
        val count = if (keys.size == 1) "Your ${policy.displayName} key is" else "All ${keys.size} ${policy.displayName} keys are"
        count + " used up" + (next?.let { " " + untilText(it, now) } ?: "") + "." +
            (lastProblem?.let { " Last reply: $it." } ?: "")
    }

    private suspend fun edit(policy: QuotaPolicy, key: String, change: (KeyUsage, Long) -> KeyUsage) {
        mutex.withLock {
            ensureLoaded()
            val now = clock()
            val p = provider(policy.id)
            val u = rolled(policy, p.keys[key] ?: KeyUsage(), now)
            put(policy.id, p.copy(keys = p.keys + (key to change(u, now))))
            persist()
        }
        countKeyless(policy)
    }

    private fun provider(id: String) = state.value.providers[id] ?: ProviderUsage()

    private fun put(id: String, p: ProviderUsage) {
        state.value = state.value.copy(providers = state.value.providers + (id to p))
    }

    private suspend fun persist() {
        val snapshot = state.value
        runCatching { store.update { snapshot } }
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val REPROBE = 6 * HOUR
        const val RESET_GRACE = 36 * HOUR

        private val DAY_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.US)

        /** "for another 3h 12m" for soon, "until Oct 1" for later. */
        fun untilText(at: Long, now: Long): String =
            if (at - now < 24 * HOUR) "for another ${whenText(at, now).removePrefix("in ")}" else "until ${whenText(at, now)}"

        /** "in 3h 12m" for soon, "Oct 1" for later (UTC). */
        fun whenText(at: Long, now: Long): String {
            val ms = at - now
            return when {
                ms <= 0 -> "now"
                ms < HOUR -> "in ${(ms / MINUTE).coerceAtLeast(1)}m"
                ms < 24 * HOUR -> "in ${ms / HOUR}h ${(ms % HOUR) / MINUTE}m"
                else -> DAY_FMT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneOffset.UTC))
            }
        }
    }
}

/** How a single attempt with one key went. */
sealed interface KeyAttemptResult<out T> {
    /** Answered. [cost] is what it charged when the server says; [remaining]/[used] likewise. */
    data class Success<T>(val value: T, val cost: Int? = null, val remaining: Int? = null, val used: Int? = null) : KeyAttemptResult<T>

    /** Slow down: fine again after [retryAfterMs]. */
    data class RateLimited(val retryAfterMs: Long, val reason: String? = null) : KeyAttemptResult<Nothing>

    /** This key's allowance is spent (monthly credits, daily requests). */
    data class Depleted(val reason: String, val retryAfterMs: Long? = null) : KeyAttemptResult<Nothing>

    /** The key itself was refused: wrong, deleted, deactivated. */
    data class Invalid(val reason: String? = null) : KeyAttemptResult<Nothing>
}

/** No key could make the call: every one is spent, cooling down, or refused. */
class AllKeysExhaustedException(message: String) : Exception(message)

/**
 * Tries Tj's keys for one provider in order (Tj's request, 2026-09-25): key 1 until it's spent,
 * then key 2, and so on. The [UsageMeter] decides before each call whether a key can afford it,
 * so a key is skipped before it's refused, and rotation starts from key 1 again once its
 * provider's period resets (monthly for The Odds API, daily for pinnapi).
 */
class KeyPool(
    val policy: QuotaPolicy,
    private val keys: () -> List<String>,
    private val meter: UsageMeter,
) {
    suspend fun <T> execute(cost: Int, action: suspend (key: String) -> KeyAttemptResult<T>): T {
        val tried = HashSet<String>()
        var lastProblem: String? = null
        var shortWaits = 0
        while (true) {
            val all = keys()
            val key = meter.pick(policy, all.filter { it !in tried }, cost)
                ?: throw AllKeysExhaustedException(meter.exhaustedMessage(policy, all, lastProblem))
            when (val r = action(key)) {
                is KeyAttemptResult.Success -> {
                    meter.recordCall(policy, key, r.cost ?: cost, r.remaining, r.used)
                    return r.value
                }
                is KeyAttemptResult.RateLimited -> {
                    lastProblem = r.reason
                    // A burst limit (a second or two) is worth waiting out on the same key once.
                    if (r.retryAfterMs <= SHORT_WAIT_MS && shortWaits++ < 1) {
                        meter.countKeyless(policy, calls = 1, throttled = 1)
                        delay(r.retryAfterMs)
                        continue
                    }
                    meter.recordThrottled(policy, key, r.retryAfterMs, r.reason ?: "slow down")
                    tried += key
                }
                is KeyAttemptResult.Depleted -> {
                    meter.recordDepleted(policy, key, r.reason, r.retryAfterMs)
                    lastProblem = r.reason
                    tried += key
                }
                is KeyAttemptResult.Invalid -> {
                    meter.recordRejected(policy, key, r.reason ?: "key refused")
                    lastProblem = r.reason
                    tried += key
                }
            }
        }
    }

    companion object {
        const val SHORT_WAIT_MS = 5_000L
    }
}
