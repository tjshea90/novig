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
    /**
     * A key whose own plan allows more than this per period is a paid key and goes first ([KeyPool]), free keys after (Tj, 2026-10-16, PropLine Pro: "find the paid key and prioritize its use").
     * Null = the order Tj typed. Needs the key's plan to be known (a call's answer or an account read).
     */
    val paidFirstAbove: Int? = null,
    /**
     * The provider sends no usage figure, so [defaultLimit] minus the app's own count is only a GUESS (PinnWire: Tj, 2026-10-18, TCL119). A guess never rests a key: it is spent only on the provider's
     * word (a 429 naming the day, or an account read), and [KeyUsage.left] is its last figure or unknown.
     */
    val ownCountIsGuess: Boolean = false,
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
            rule = "500 credits a month per free key, reset on the 1st. Game lines cost 1 credit per market per league; sportsbook props 1 per prop type per game. With a PropLine key, only asked for what PropLine couldn't give. A tapped ParlayAPI pick's books: about 1 credit, only when ParlayAPI and PropLine have no other book.",
        )
        val PINNAPI = QuotaPolicy(
            "pinnacle", "Pinnacle (pinnapi)", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 100,
            perMinute = 20, perHour = 100,
            rule = "100 requests a day per trial key (20 a minute), reset at midnight UTC. About 1 per sport per scan.",
        )
        val PINNWIRE = QuotaPolicy(
            "pinnwire", "Pinnacle (PinnWire)", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 100,
            perMinute = 20, ownCountIsGuess = true,
            rule = "100 requests a day per free key (20 a minute), reset at midnight UTC. About 1 per sport per scan, player props included.",
        )
        val PROPLINE = QuotaPolicy(
            "propline", "PropLine", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, defaultLimit = 1000, paidFirstAbove = 1000,
            rule = "Free 1,000 requests a day per key (burst 10, 5 a second); Hobby 5,000; Pro 25,000 (burst 50, 20 a second); reset at midnight UTC; the plan is each key's own X-Daily-Limit and a paid key goes first. 1 per league per scan for game lines, 1 per game for props; /ev, /odds/closing, /results and the exports are paid features.",
        )
        val PARLAY = QuotaPolicy(
            "parlay", "ParlayAPI", "credits", keyed = true, period = QuotaPeriod.MONTH_UTC, defaultLimit = 1000,
            rule = "Free keys: 1,000 credits a month each, used for scans in key order once the keys before them are spent or at their day's share, down to their last 100 (kept for closing lines). Paid (\$5: 20,000; \$20: 100,000): a day's share at most, the last 300 kept for closing lines. When no key can pay, the other feeds carry on. Game lines 3 credits a league (5 with Pinnacle's alternate lines, bought only when PinnWire and pinnapi are off); a league's props 3; 1st-half lines 2 (only where Novig lists them); injuries 1 (only for players its props didn't cover); closes 1 to 5 a day; on a tap only: its picks at Novig 10 a league (then Vigilant's own read of them, a league's props 3), a pick's books when CrazyNinjaOdds doesn't list it (that market's props for the league 3, shared 2 minutes), a second opinion 5. Credits reset on the 1st (UTC), read from ParlayAPI.",
        )
        val SGO = QuotaPolicy(
            "sgo", "SportsGameOdds", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, perMinute = 270,
            rule = "Pro: 300 requests a minute (Vigilant keeps to 270), unlimited events a month (a request is billed per game it returns, not per market or book). Several keys are tried in order; a key at its limit rests until the minute rolls. Free (Amateur) keys: 10 requests a minute and 2,500 events a month, with 10-minute-old odds: Vigilant refuses a quote that old, so only Rookie and up are of use.",
        )
        val ODDSPAPI = QuotaPolicy(
            "oddspapi", "OddsPapi", "requests", keyed = true, period = QuotaPeriod.DAY_UTC,
            rule = "v5 (v5.oddspapi.io), by trial or contract: odds endpoints 10 requests a second, every other endpoint 100 a minute, per key (Vigilant spaces its calls under both). The trial's own request quota is not published: a key that answers 429 rests until its Retry-After. Several keys are tried in order. The self-serve v4 keys (api.oddspapi.io) are a different product and are not used.",
        )
        val UNABATED = QuotaPolicy(
            "unabated", "Unabated", "requests", keyed = true, period = QuotaPeriod.DAY_UTC,
            rule = "Free API: ONE odds request every 5 seconds per key, shared by every odds endpoint (Vigilant spaces them 5.2 s apart through one gate), data delayed at least 15 seconds. No monthly quota is published; a 429 or 503 carries Retry-After. Discovery calls (leagues, bet types, books, teams) do not use the odds interval.",
        )
        val BOLTODDS = QuotaPolicy(
            "boltodds", "BoltOdds", "requests", keyed = true, period = QuotaPeriod.DAY_UTC,
            rule = "Pro: the odds arrive over a WebSocket (3 connections at once, 12 new connections a minute per IP); the GET endpoints (get_info, get_games, get_markets, get_parlays) 12 a minute per IP, 3 in quick succession; the snapshot (the initial state, asked once per connection) 6 a minute per IP and a per-key daily token quota BoltOdds does not publish. Vigilant keeps under all of them. A refused key rests; several keys are tried in order.",
        )
        val ODDSPAPI_FREE = QuotaPolicy(
            "oddspapi_free", "OddsPapi Free (v4)", "requests", keyed = true, period = QuotaPeriod.MONTH_UTC, defaultLimit = 250,
            rule = "v4 (api.oddspapi.io), free plan: a small MONTHLY request allowance (250 by the docs; GET /v4/account, which never counts, says the real figure), 1 request per billable call whatever it returns. /v4/historical-odds is free and unmetered. Cooldown 1 s per endpoint (5 s for historical odds). The last requests of the month are kept for the closing-line mapping.",
        )
        /** Pinnodds' REST board (not in [ALL]: no meter of its own; the feed's own 429s and `Retry-After` say when to wait). Full demo / Pro: 10 requests a second; the free trial tier 100 a day. */
        val PINNODDS = QuotaPolicy(
            "pinnodds_rest", "Pinnacle (Pinnodds)", "requests", keyed = true, period = QuotaPeriod.DAY_UTC,
            rule = "Pro and the full demo: 10 requests a second, no daily cap; the free trial tier: 100 a day (a 429 says so and the other Pinnacle feeds carry on). About 1 per sport per scan.",
        )
        val NOVIG = QuotaPolicy("novig", "Novig", "requests", keyed = false, rule = "Read at 4 a second (2 at a time) to stay under Novig's per-network limit.")
        val POLYMARKET = QuotaPolicy("polymarket", "Polymarket", "requests", keyed = false, rule = "No key needed. Allows 300 requests per 10 seconds.")
        val SXBET = QuotaPolicy("sxbet", "SX Bet", "requests", keyed = true, period = QuotaPeriod.DAY_UTC, rule = "Markets, sports and scores are public (500 a minute per IP, shared); odds need a free read-only API key (best odds 5,500 a minute, 100 markets a call). Realtime is a WebSocket that does not spend the request budget.")
        val KALSHI = QuotaPolicy("kalshi", "Kalshi", "requests", keyed = false, rule = "No key needed. Allows 20 requests a second.")

        val ALL = listOf(SGO, BOLTODDS, ODDSPAPI, ODDSPAPI_FREE, UNABATED, PINNWIRE, PINNAPI, PROPLINE, PARLAY, ODDS_API, NOVIG, POLYMARKET, KALSHI, SXBET)
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
    /** Refusals in a row (reset by any accepted call or Test): each one rests the key longer, [UsageMeter.refusalHold]. */
    val refusals: Int = 0,
    /** When this key first answered (kept across periods): a plan bought mid-month is paced from here ([CreditPace]), not from the 1st. */
    val firstSeenMs: Long? = null,
    /** When the provider says this key's period resets ([CreditHeaders.resetAtMs], ParlayAPI's `X-RateLimit-Reset`): its plan's own cycle. */
    val resetAtMs: Long? = null,
    /**
     * Every unit this app's own calls were charged on this key, ever: never set from the provider's figures and never reset, so what a
     * round cost is the change in it ([UsageDelta]). [used] follows the provider's count, which jumps when the app first learns it
     * (Tj's diagnostics 2026-09-30: a new ParlayAPI key's 600 credits of the month showed as one scan's cost).
     */
    val charged: Long = 0,
    /**
     * When the provider's own figure in [remaining]/[used]/[limit] was read (a call's answer or an account read): the newest figure wins when two machines (this phone and the GitHub engine, Tj 2026-10-15) both
     * spend one key, [UsageMerge]. Null = never from the provider (our own count).
     */
    val figureAtMs: Long? = null,
) {
    /** When this key's period ends: the provider's own word when it gave one, else [policy]'s calendar. */
    fun nextReset(policy: QuotaPolicy): Long = resetAtMs ?: policy.nextReset(periodStart)

    fun left(policy: QuotaPolicy): Int? = remaining ?: if (policy.ownCountIsGuess) null else (limit ?: policy.defaultLimit)?.let { (it - used).coerceAtLeast(0) }
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
        var x = u.copy(calls = u.calls + 1, lastCallMs = now, lastCost = cost, recent = u.recent + now, lastNote = null, firstSeenMs = u.firstSeenMs ?: now, charged = u.charged + cost, refused = false, refusals = 0)
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
            // A figure the provider gave earlier stays (less this call, never to zero by our count) until its next account read; a guessed count is not a figure.
            x = x.copy(used = u.used + cost, remaining = if (policy.ownCountIsGuess) u.remaining?.minus(cost)?.coerceAtLeast(1) else null)
        }
        // The provider's own reset time: its plan's cycle, which the pace and the meter then follow.
        if (resetAtMs != null) {
            val cycleStart = Instant.ofEpochMilli(resetAtMs).atZone(ZoneOffset.UTC).minusMonths(1).toInstant().toEpochMilli()
            x = x.copy(resetAtMs = resetAtMs, periodStart = if (policy.period == QuotaPeriod.MONTH_UTC && cycleStart <= now) cycleStart else x.periodStart)
        }
        if (serverRemaining != null) x = x.copy(figureAtMs = now)
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
        if (remaining != null) x = x.copy(remaining = remaining, used = used ?: lim?.let { (it - remaining).coerceAtLeast(0) } ?: x.used, limit = lim, figureAtMs = now)
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

    /**
     * The key itself was refused (wrong, deleted, deactivated, or a socket closed 1008 for a moment's reason). It rests only briefly and is tried again,
     * the hold growing with each refusal in a row ([refusalHold]), so a refusal that was transient (Tj, 2026-10-13: BoltOdds refused once and stayed
     * shut out all day though its key worked) heals by itself on a later scan, and a truly bad key costs one call an hour at most.
     */
    suspend fun recordRejected(policy: QuotaPolicy, key: String, note: String) = edit(policy, key) { u, now ->
        val n = u.refusals + 1
        u.copy(depletedUntil = now + refusalHold(n), refused = true, refusals = n, lastNote = note, lastCallMs = now)
    }

    /** A Test key found [key] accepted: a rest that a refusal set is lifted (nothing else changes). */
    suspend fun recordAccepted(policy: QuotaPolicy, key: String) = edit(policy, key) { u, _ -> if (u.refused || u.refusals > 0) u.copy(refused = false, refusals = 0, depletedUntil = null, lastNote = null) else u }

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

    /**
     * Starts an EMPTY ledger from an earlier one (the GitHub engine's previous job, Tj 2026-10-15): without it every 6-hour handover forgot what a key had spent today and a daily allowance could be spent twice.
     * Only what the ledger does not already hold is taken, and an old period rolls over as usual.
     */
    suspend fun seed(book: UsageBook) = mutex.withLock {
        ensureLoaded()
        val now = clock()
        for ((id, theirs) in book.providers) {
            val policy = QuotaPolicy.ALL.firstOrNull { it.id == id } ?: continue
            val mine = provider(id)
            val keys = mine.keys.toMutableMap()
            for ((k, u) in theirs.keys) if (k !in keys) keys[k] = policy.roll(u, now)
            val today = QuotaPolicy.NOVIG.periodStart(now)
            val merged = if (mine.callsToday == 0 && theirs.dayStart == today) mine.copy(dayStart = today, callsToday = theirs.callsToday, throttledToday = theirs.throttledToday, lastThrottleMs = theirs.lastThrottleMs) else mine
            put(id, merged.copy(keys = keys))
        }
        persist()
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

        /** How long the [n]th refusal in a row rests a key: 2, 4, 8 ... minutes, at most an hour. */
        fun refusalHold(n: Int): Long = minOf(2 * MINUTE shl (n - 1).coerceIn(0, 5), HOUR)
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
 * anything earlier days left unspent, never tomorrow's. A key first seen part-way through its period spreads the period's credits over the
 * days left in it. Stateless: it reads the meter, so it survives restarts and follows the server's
 * figures. A key whose allowance is [freeLimit] or less (a free plan) isn't paced: nothing of the day's share applies to it, so scans use it, in
 * Tj's key order, after the keys before it (Tj, 2026-10-07: "automatically uses each successive key when the last one is depleted"), down to its
 * last [freeReserve] credits, which stay for closing lines. Credits left on a free key are lost at its reset, never carried over.
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
    /** What a free key keeps for closing lines after a scan's call: a few days of closes (they cost 1 to 5 a league a day), the rest is for scans. */
    val freeReserve: Int = FREE_RESERVE,
) {
    /** Credits [u] must still hold after a call at [now]. */
    fun floor(u: KeyUsage, now: Long): Int {
        // Until the server has said what the key's plan is (its first answer's used + remaining), only the reserve: that first call tells.
        val limit = u.limit ?: return reserve
        if (isFree(u)) return minOf(freeReserve, reserve)
        val start = u.periodStart.takeIf { it > 0 } ?: policy.periodStart(now)
        val reset = u.resetAtMs ?: policy.nextReset(start)
        val z = zone()
        val endOfToday = Instant.ofEpochMilli(now).atZone(z).toLocalDate().plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        // A key first heard from part-way through its period may already have been used (Tj, 2026-10-17: a Starter key met on the 16th with 49% used showed "out of usage today" for days, because its whole limit was spread over
        // only the days since this app met it): when the server's used count is well above what this app itself charged since first sight ([KeyUsage.charged]), the key had a history and is paced from the period's start.
        // A plan bought late in the month (used about what this app charged) is still spread over the days left.
        val usedBefore = (u.used.toLong() - u.charged).coerceAtLeast(0L)
        val hadHistory = usedBefore > limit * HISTORY_FRACTION
        val pool = (limit - reserve).coerceAtLeast(0).toLong()
        // Spread over what's left of the period from the key's first answer (ParlayAPI's credits run on the calendar month: a plan bought on
        // the 30th gets its whole month's credits for that last day, not a thirtieth of them; checked with Tj's key 2026-09-30), and a key
        // seen since before the period began over the whole period. Through the end of today (Tj's day), never less than a whole day.
        val from = (if (hadHistory) start else maxOf(start, u.firstSeenMs ?: start)).coerceAtMost(reset - 1)
        val window = reset - from
        val elapsed = maxOf(endOfToday - from, minOf(DAY_MS, window)).coerceAtMost(window)
        val allowed = pool * elapsed / window
        val kept = if (keepOfDay > 0.0) (pool * minOf(DAY_MS, window) / window * keepOfDay).toLong() else 0L
        return (limit - allowed + kept).toInt()
    }

    /** A free plan's key (the server said its allowance is [freeLimit] or less): used for scans after the keys before it, down to [freeReserve]. */
    fun isFree(u: KeyUsage): Boolean = u.limit != null && u.limit <= freeLimit

    /** Credits [u] may still spend today. */
    fun spendableToday(u: KeyUsage, now: Long): Int = ((u.left(policy) ?: 0) - floor(u, now)).coerceAtLeast(0)

    companion object {
        /** Credits a free ParlayAPI key keeps for closing lines (Tj, 2026-10-07: free keys serve scans too; before, all 1,000 were held back). */
        const val FREE_RESERVE = 100

        /** A first-seen key whose server-side used count exceeds this share of its limit beyond what this app charged had a history before it was met: paced from the period's start. */
        const val HISTORY_FRACTION = 0.05

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
    /** Tj, 2026-10-19 (The Odds API): stay on the key that last answered until it is spent or refused, then go to the next in his order. */
    private val sticky: Boolean = false,
) {
    @Volatile private var lastGood: String? = null

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
        // A pace sets each key's own floor (a paid key's day share, a free key's small closing-lines reserve); with none, every key keeps [reserve].
        val held: (KeyUsage, Long) -> Int = when {
            pace != null -> { u, now -> pace.floor(u, now) }
            reserve > 0 -> { _, _ -> reserve }
            else -> UsageMeter.NO_FLOOR
        }
        while (true) {
            val all = keys()
            // A pace is ParlayAPI's: a paid key (its allowance, once read, is above the free 1,000) goes first, free keys after (Tj, 2026-10-16: "I upgraded one of my parlayapi keys ... start using it over the free ones"); the order is otherwise Tj's.
            val open = all.filter { it !in tried }.let { o ->
                if (pace == null) {
                    // PropLine (and any policy that says so): a key whose own limit is above the free one goes first.
                    val above = policy.paidFirstAbove
                    if (above == null) o else {
                        val ks = meter.flow.value.providers[policy.id]?.keys
                        o.sortedBy { k -> ks?.get(k)?.limit?.let { lim -> if (lim > above) 0 else 1 } ?: 1 }
                    }
                } else {
                    val ks = meter.flow.value.providers[policy.id]?.keys
                    o.sortedBy { k -> ks?.get(k)?.let { u -> if (pace.isFree(u)) 1 else 0 } ?: 1 }
                }
            }
            val key = meter.pick(policy, open, cost, held)
                ?: if (held !== UsageMeter.NO_FLOOR && open.isNotEmpty() && meter.pick(policy, open, cost) != null) {
                    throw CreditsHeldBackException(heldBackMessage(all, open, cost, reserve, pace))
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

    /**
     * Why no key could take a call that some key has the credits for: a paid key's day share is spent (and what the free keys are down to), the free
     * keys are all down to their closing-lines reserve, or every key is down to [reserve]. The scan reads this and the other feeds price meanwhile.
     */
    private suspend fun heldBackMessage(all: List<String>, open: List<String>, cost: Int, reserve: Int, pace: CreditPace?): String {
        val name = policy.displayName
        val usage = { k: String -> meter.flow.value.providers[policy.id]?.keys?.get(k) }
        val free = if (pace == null) emptyList() else open.filter { k -> usage(k)?.let { pace.isFree(it) } == true }
        // A paid key that could pay keeping only the reserve: it's the day's pace holding it back.
        val paced = pace != null && meter.pick(policy, open - free.toSet(), cost) { _, _ -> reserve } != null
        val freeLeft = pace?.let { " The last ${it.freeReserve} credits on a free key are kept for closing lines." }.orEmpty()
        return when {
            paced -> "$name has spent today's share of its ${policy.unit}: back tomorrow (unused days carry over)." + if (free.isNotEmpty()) freeLeft else ""
            pace != null && free.isNotEmpty() && free.size == open.size ->
                (if (all.size == 1) "Your $name key is" else "All ${all.size} $name keys are") + " down to the last ${pace.freeReserve} ${policy.unit}, kept for closing lines: back at the reset."
            pace != null && free.isNotEmpty() -> "$name's keys are spent or down to their closing-lines reserve: back when they reset." + freeLeft
            else -> "The last $reserve ${policy.unit} on ${if (all.size == 1) "your $name key" else "each $name key"} are kept for closing lines."
        }
    }

    companion object {
        const val SHORT_WAIT_MS = 5_000L
    }
}
