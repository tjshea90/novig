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

    /** A key's usage as of [now]: a new period starts fresh, expired holds are lifted. */
    fun roll(u: KeyUsage, now: Long): KeyUsage {
        var x = u
        if (x.periodStart == 0L) x = x.copy(periodStart = periodStart(now))
        val reset = x.resetAtMs
        if (reset != null && now >= reset) {
            // The provider's own reset time passed: a new period from then, its figures unknown until its next answer.
            x = x.copy(periodStart = reset, resetAtMs = null, used = 0, calls = 0, remaining = null, lastNote = null, refused = false)
        } else if (reset == null && now >= nextReset(x.periodStart)) {
            x = x.copy(periodStart = periodStart(now), used = 0, calls = 0, remaining = null, lastNote = null, refused = false)
        }
        // A hold that ran out means "try again": a remembered zero no longer applies.
        if (x.depletedUntil != null && now >= x.depletedUntil) {
            x = x.copy(depletedUntil = null, remaining = x.remaining?.takeIf { it > 0 }, refused = false)
        }
        if (x.coolUntil != null && now >= x.coolUntil) x = x.copy(coolUntil = null)
        if (x.recent.any { now - it >= UsageMeter.HOUR }) x = x.copy(recent = x.recent.filter { now - it < UsageMeter.HOUR })
        return x
    }

    /** Whether an already-[roll]ed key can make a call costing [cost] now. */
    fun usable(u: KeyUsage, cost: Int, now: Long): Boolean {
        if (u.depletedUntil != null || u.coolUntil != null) return false
        val left = u.left(this)
        if (left != null && left < cost.coerceAtLeast(1)) return false
        perMinute?.let { cap -> if (u.recent.count { now - it < UsageMeter.MINUTE } >= cap) return false }
        perHour?.let { cap -> if (u.recent.size >= cap) return false }
        return true
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
            rule = "500 credits a month per free key, reset on the 1st. Game lines cost 1 credit per market per league; sportsbook props 1 per prop type per game. With a PropLine key, only asked for what PropLine couldn't give.",
        )
        val PINNAPI = QuotaPolicy(
            "pinnacle", "Pinnacle (pinnapi)", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 100,
            perMinute = 20, perHour = 100,
            rule = "100 requests a day per trial key (20 a minute), reset at midnight UTC. About 1 per sport per scan.",
        )
        val PINNWIRE = QuotaPolicy(
            "pinnwire", "Pinnacle (PinnWire)", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 100,
            perMinute = 20,
            rule = "100 requests a day per free key (20 a minute), reset at midnight UTC. About 1 per sport per scan, player props included.",
        )
        val PROPLINE = QuotaPolicy(
            "propline", "PropLine", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 1000,
            rule = "1,000 requests a day per free key, reset at midnight UTC. 1 per league per scan for game lines, 1 per game for props.",
        )
        val PARLAY = QuotaPolicy(
            "parlay", "ParlayAPI", "credits", keyed = true, period = QuotaPeriod.MONTH_UTC, defaultLimit = 1000,
            rule = "Free: 1,000 credits a month, kept for Pinnacle's closing lines. Paid (\$5: 20,000; \$20: 100,000): scans too, a day's share at most, the last 300 kept for closing lines. Game lines 5 credits a league (alternate lines included); a league's props 3; closes 1 to 5 a day. Resets with the plan's month.",
        )
        val NOVIG = QuotaPolicy("novig", "Novig", "requests", keyed = false, rule = "Read at 4 a second (2 at a time) to stay under Novig's per-network limit.")
        val POLYMARKET = QuotaPolicy("polymarket", "Polymarket", "requests", keyed = false, rule = "No key needed. Allows 300 requests per 10 seconds.")
        val KALSHI = QuotaPolicy("kalshi", "Kalshi", "requests", keyed = false, rule = "No key needed. Allows 20 requests a second.")

        val ALL = listOf(PINNWIRE, PINNAPI, PROPLINE, PARLAY, ODDS_API, NOVIG, POLYMARKET, KALSHI)
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
    /** The provider refused the key itself (wrong or deleted), not just its allowance. */
    val refused: Boolean = false,
    /** When this key first answered (kept across periods): a plan bought mid-month is paced from here ([CreditPace]), not from the 1st. */
    val firstSeenMs: Long? = null,
    /** When the provider says this key's period resets ([CreditHeaders.resetAtMs], ParlayAPI's `X-RateLimit-Reset`): its plan's own cycle. */
    val resetAtMs: Long? = null,
) {
    /** When this key's period ends: the provider's own word when it gave one, else [policy]'s calendar. */
    fun nextReset(policy: QuotaPolicy): Long = resetAtMs ?: policy.nextReset(periodStart)

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

    /**
     * The first key, in Tj's order, that can afford a call costing [cost] right now. Because it
     * always starts from key 1, rotation falls back to key 1 as soon as its period resets.
     */
    suspend fun pick(policy: QuotaPolicy, keys: List<String>, cost: Int, floor: (KeyUsage, Long) -> Int = NO_FLOOR): String? = mutex.withLock {
        ensureLoaded()
        val now = clock()
        val p = provider(policy.id)
        val updated = p.keys.toMutableMap()
        var chosen: String? = null
        for (k in keys) {
            val u = policy.roll(p.keys[k] ?: KeyUsage(), now)
            updated[k] = u
            // [floor]: credits the key must still have after this call (a reserve, a day's pace).
            if (chosen == null && policy.usable(u, cost + floor(u, now), now)) chosen = k
        }
        put(policy.id, p.copy(keys = updated))
        chosen
    }

    /** A call the provider answered. [serverUsed]/[serverRemaining] are its own figures, if sent. */
    suspend fun recordCall(
        policy: QuotaPolicy, key: String, cost: Int, serverRemaining: Int? = null, serverUsed: Int? = null, resetAtMs: Long? = null,
    ) = edit(policy, key) { u, now ->
        var x = u.copy(calls = u.calls + 1, lastCallMs = now, lastCost = cost, recent = u.recent + now, lastNote = null, firstSeenMs = u.firstSeenMs ?: now)
        if (serverUsed != null && serverRemaining != null) {
            val drop = u.used - serverUsed
            val sinceLast = u.lastCallMs?.let { now - it } ?: Long.MAX_VALUE
            if (u.remaining != null && drop > 0 && drop <= STALE_SLACK && sinceLast < STALE_WINDOW_MS) {
                // An answer to a call made before one already recorded (calls in parallel on one key: a big props reply landing after
                // two small game-line ones, Tj's diagnostics 2026-09-30): its figures are older than the ones kept, so they're left out.
            } else {
                // The server's count went DOWN without our period changing: this key runs on its own
                // cycle (a paid plan's billing date), so follow the server from here.
                if (serverUsed + cost < u.used) x = x.copy(periodStart = now)
                x = x.copy(used = serverUsed, remaining = serverRemaining, limit = serverUsed + serverRemaining)
            }
        } else if (serverRemaining != null) {
            // Only what's left (ParlayAPI's X-RateLimit-Remaining): the server's word for it; the count used is ours, the allowance as known.
            x = x.copy(used = u.used + cost, remaining = serverRemaining)
        } else {
            x = x.copy(used = u.used + cost, remaining = null)
        }
        // The provider's own reset time: its plan's cycle, which the pace and the meter then follow.
        if (resetAtMs != null) {
            val cycleStart = Instant.ofEpochMilli(resetAtMs).atZone(ZoneOffset.UTC).minusMonths(1).toInstant().toEpochMilli()
            x = x.copy(resetAtMs = resetAtMs, periodStart = if (policy.period == QuotaPeriod.MONTH_UTC && cycleStart <= now) cycleStart else x.periodStart)
        }
        // Nothing left: rest the key until its reset instead of spending a refused call on it.
        if (x.left(policy) == 0) x = x.copy(depletedUntil = x.nextReset(policy))
        x
    }

    /**
     * The provider's own account figures, read without a call to count (ParlayAPI's free `/v1/meta/api-key-check`, Tj 2026-09-30: "so
     * the meter is accurate"): credits left, and when known the allowance, the count used and the reset time. [exhausted]: the key is
     * spent until its reset; [inactive]: the provider refused the key.
     */
    suspend fun recordBalance(
        policy: QuotaPolicy, key: String, remaining: Int?, limit: Int? = null, used: Int? = null, resetAtMs: Long? = null,
        exhausted: Boolean = false, inactive: Boolean = false, note: String? = null, periodStartMs: Long? = null,
    ) = edit(policy, key) { u, now ->
        var x = u.copy(firstSeenMs = u.firstSeenMs ?: now)
        if (resetAtMs != null && resetAtMs > now) {
            // The period's own start when the answer gave it (/v1/usage's period_start), else a month before its reset.
            val cycleStart = periodStartMs?.takeIf { it in 1..now && it < resetAtMs }
                ?: Instant.ofEpochMilli(resetAtMs).atZone(ZoneOffset.UTC).minusMonths(1).toInstant().toEpochMilli()
            x = x.copy(resetAtMs = resetAtMs, periodStart = if (policy.period == QuotaPeriod.MONTH_UTC && cycleStart <= now) cycleStart else x.periodStart)
        }
        // An account read sent before a call whose answer is already kept (both in flight at once): the call's figures are newer.
        val sinceLast = u.lastCallMs?.let { now - it } ?: Long.MAX_VALUE
        if (!exhausted && !inactive && used != null && u.remaining != null && used < u.used && u.used - used <= STALE_SLACK && sinceLast < STALE_WINDOW_MS) return@edit x
        val lim = limit ?: if (remaining != null && used != null) remaining + used else x.limit
        if (remaining != null) x = x.copy(remaining = remaining, used = used ?: lim?.let { (it - remaining).coerceAtLeast(0) } ?: x.used, limit = lim)
        else if (lim != null) x = x.copy(limit = lim)
        if (note != null) x = x.copy(lastNote = note)
        when {
            inactive -> x = x.copy(refused = true)
            exhausted || x.left(policy) == 0 -> x = x.copy(remaining = 0, depletedUntil = x.nextReset(policy))
            else -> if (x.depletedUntil != null && (x.left(policy) ?: 0) > 0) x = x.copy(depletedUntil = null, refused = false)
        }
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
            else -> u.nextReset(policy)
        }
        u.copy(depletedUntil = until, remaining = 0, lastNote = note, lastCallMs = now, recent = u.recent + now)
    }

    /** A short throttle (per-minute window, burst limit). The key is fine after [retryAfterMs]. */
    suspend fun recordThrottled(policy: QuotaPolicy, key: String, retryAfterMs: Long, note: String) = edit(policy, key) { u, now ->
        u.copy(coolUntil = now + retryAfterMs, lastNote = note, lastCallMs = now, recent = u.recent + now)
    }.also { countKeyless(policy, calls = 0, throttled = 1) }

    /** The key itself was refused (wrong, deleted, deactivated). Skipped until the next period. */
    suspend fun recordRejected(policy: QuotaPolicy, key: String, note: String) = edit(policy, key) { u, now ->
        u.copy(depletedUntil = u.nextReset(policy), refused = true, lastNote = note, lastCallMs = now)
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
            val u = policy.roll(p.keys[key] ?: KeyUsage(), now)
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
        /** [pick]'s floor when nothing is held back. */
        val NO_FLOOR: (KeyUsage, Long) -> Int = { _, _ -> 0 }

        /** A lower count from the server this soon after the last answer, and by no more than [STALE_SLACK], is a late reply, not a new cycle. */
        const val STALE_WINDOW_MS = 2 * 60_000L
        const val STALE_SLACK = 100

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
    data class Success<T>(val value: T, val cost: Int? = null, val remaining: Int? = null, val used: Int? = null, val resetAtMs: Long? = null) : KeyAttemptResult<T>

    /** Slow down: fine again after [retryAfterMs]. */
    data class RateLimited(val retryAfterMs: Long, val reason: String? = null) : KeyAttemptResult<Nothing>

    /** This key's allowance is spent (monthly credits, daily requests). */
    data class Depleted(val reason: String, val retryAfterMs: Long? = null) : KeyAttemptResult<Nothing>

    /** The key itself was refused: wrong, deleted, deactivated. */
    data class Invalid(val reason: String? = null) : KeyAttemptResult<Nothing>
}

/** No key could make the call: every one is spent, cooling down, or refused. */
class AllKeysExhaustedException(message: String) : Exception(message)

/** A key could pay for the call but its credits are held back ([KeyPool.execute]'s reserve or pace): the call waits, nothing failed. */
class CreditsHeldBackException(message: String) : Exception(message)

/**
 * A month's credits spread over its days (Tj's ParlayAPI Starter plan, 2026-09-30: 20,000 credits a month). After a call a key must still
 * hold [reserve] plus the share of every day after today (Tj's own day, [zone]), so a busy evening can spend the whole of today's share and
 * anything earlier days left unspent, never tomorrow's. Stateless: it reads the meter, so it survives restarts and follows the server's
 * figures. A key whose allowance is [freeLimit] or less (a free plan) isn't paced but held back entirely: its few credits are for [reserve]'s
 * purpose (closing lines).
 */
class CreditPace(
    private val policy: QuotaPolicy,
    private val reserve: Int,
    private val freeLimit: Int,
    private val zone: () -> java.time.ZoneId = java.time.ZoneId::systemDefault,
    /**
     * The part of a day's share this caller leaves for others: background auto-scans keep half of it for the scans Tj starts himself, so a
     * morning of 15-minute cycles can't spend the evening's credits (earlier days' leftovers stay open to both).
     */
    private val keepOfDay: Double = 0.0,
) {
    /** Credits [u] must still hold after a call at [now]. */
    fun floor(u: KeyUsage, now: Long): Int {
        // Until the server has said what the key's plan is (its first answer's used + remaining), only the reserve: that first call tells.
        val limit = u.limit ?: return reserve
        if (isFree(u)) return FREE_ONLY
        val start = u.periodStart.takeIf { it > 0 } ?: policy.periodStart(now)
        val reset = u.resetAtMs ?: policy.nextReset(start)
        val z = zone()
        val endOfToday = Instant.ofEpochMilli(now).atZone(z).toLocalDate().plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        val span = (reset - start).coerceAtLeast(DAY_MS)
        val pool = (limit - reserve).coerceAtLeast(0).toLong()
        // The days this key has had through the end of today: from its first answer when that came after the period began (a plan bought
        // on the 29th has had one day, not 29 "left unspent"), and never less than a whole day (bought at 11 pm, it still gets today's share).
        val from = maxOf(start, u.firstSeenMs ?: start)
        val elapsed = maxOf(endOfToday - from, DAY_MS).coerceAtMost(span)
        val allowed = pool * elapsed / span
        val kept = if (keepOfDay > 0.0) (pool * DAY_MS / span * keepOfDay).toLong() else 0L
        return (limit - allowed + kept).toInt()
    }

    /** A free plan's key (the server said its allowance is [freeLimit] or less): kept for closing lines. */
    fun isFree(u: KeyUsage): Boolean = u.limit != null && u.limit <= freeLimit

    /** Credits [u] may still spend today. */
    fun spendableToday(u: KeyUsage, now: Long): Int = ((u.left(policy) ?: 0) - floor(u, now)).coerceAtLeast(0)

    companion object {
        /** A floor no key reaches: held back from these calls altogether. */
        const val FREE_ONLY = Int.MAX_VALUE / 4

        private const val DAY_MS = 86_400_000L
    }
}

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
    /** How many keys Tj has for this provider right now. */
    fun keyCount(): Int = keys().size

    /**
     * [reserve]: credits each key keeps back for other calls (ParlayAPI's scans leave the last few hundred to the closing lines, which
     * matter more); [pace]: a month's credits spread over its days ([CreditPace]). A key is used only while it can afford [cost] and
     * still keep both. A key that could pay but is held back throws [CreditsHeldBackException] (not a failure: the call waits).
     */
    suspend fun <T> execute(cost: Int, reserve: Int = 0, pace: CreditPace? = null, action: suspend (key: String) -> KeyAttemptResult<T>): T {
        val tried = HashSet<String>()
        var lastProblem: String? = null
        var shortWaits = 0
        val held: (KeyUsage, Long) -> Int = if (reserve <= 0 && pace == null) UsageMeter.NO_FLOOR else { u, now -> maxOf(reserve, pace?.floor(u, now) ?: 0) }
        while (true) {
            val all = keys()
            val open = all.filter { it !in tried }
            val key = meter.pick(policy, open, cost, held)
                ?: if (held !== UsageMeter.NO_FLOOR && open.isNotEmpty() && meter.pick(policy, open, cost) != null) {
                    // Past the reserve only when a key could pay and keep it: then it's the day's pace holding back.
                    val paced = pace != null && meter.pick(policy, open, cost) { _, _ -> reserve } != null
                    val usages = open.mapNotNull { meter.flow.value.providers[policy.id]?.keys?.get(it) }
                    val free = pace != null && usages.size == open.size && usages.all { pace.isFree(it) }
                    throw CreditsHeldBackException(
                        if (free) "${policy.displayName} is on its free plan: its ${policy.unit} are kept for closing lines (scans use a paid plan's)."
                        else if (paced) "${policy.displayName} has spent today's share of its ${policy.unit}: back tomorrow (unused days carry over)."
                        else "The last $reserve ${policy.unit} on ${if (all.size == 1) "your ${policy.displayName} key" else "each ${policy.displayName} key"} are kept for closing lines.",
                    )
                } else {
                    throw AllKeysExhaustedException(meter.exhaustedMessage(policy, all, lastProblem))
                }
            when (val r = action(key)) {
                is KeyAttemptResult.Success -> {
                    meter.recordCall(policy, key, r.cost ?: cost, r.remaining, r.used, r.resetAtMs)
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
