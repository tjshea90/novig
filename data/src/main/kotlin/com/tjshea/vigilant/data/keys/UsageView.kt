package com.tjshea.vigilant.data.keys

/** Where a key stands right now, for its meter row. */
enum class KeyState {
    /** The key the next call will use. */
    ACTIVE,

    /** Has allowance left, waiting behind an earlier key. */
    STANDBY,

    /** Allowance spent until [KeyView.until]. */
    SPENT,

    /** Briefly throttled (per-minute window or a burst limit) until [KeyView.until]. */
    COOLING,

    /** The provider refused the key itself. */
    REFUSED,
}

data class KeyView(
    val index: Int,
    val masked: String,
    val used: Int,
    val allowance: Int?,
    val left: Int?,
    val state: KeyState,
    val until: Long?,
    val note: String?,
    val lastCallMs: Long?,
    /** True when [left] is the provider's own figure rather than our count. */
    val serverReported: Boolean,
    /** When the provider's own figure was read (a call's answer, or its account page), on either machine ([UsageMerge]): how old the "left" is. */
    val figureAtMs: Long? = null,
) {
    /**
     * How much of the key is used, 0..1, for its bar: 1 when the key is spent or refused (a spent key with no published allowance used to show only a red dot and no bar, Tj 2026-10-18), else from the
     * API's own "left" when known, else from the count. Null = nothing to draw (no allowance known and still usable).
     */
    val fractionUsed: Float?
        get() {
            if (state == KeyState.SPENT || state == KeyState.REFUSED) return 1f
            val a = allowance?.takeIf { it > 0 } ?: return null
            return ((left?.let { a - it } ?: used).toFloat() / a).coerceIn(0f, 1f)
        }
}

/** One provider's meter: per-key rows plus totals. */
data class ProviderView(
    val policy: QuotaPolicy,
    val keys: List<KeyView>,
    val totalLeft: Int?,
    val totalAllowance: Int?,
    /** When the current period ends (the 1st for monthly, midnight UTC for daily; the provider's own time when it says). */
    val nextReset: Long?,
    val callsToday: Int,
    val throttledToday: Int,
    val lastThrottleMs: Long?,
    /** A paced provider ([CreditPace], ParlayAPI): credits its scans may still spend today; null when not paced or not known yet. */
    val scanShareToday: Int? = null,
    /** When the current period began, to match [nextReset]. */
    val periodStart: Long? = null,
) {
    val activeIndex: Int? get() = keys.firstOrNull { it.state == KeyState.ACTIVE }?.index
    /** Every key that is not refused is spent (and there is at least one): the bar is full and red whatever the last "left" figure said. */
    val spent: Boolean
        get() = policy.keyed && keys.any { it.state == KeyState.SPENT } && keys.all { it.state == KeyState.SPENT || it.state == KeyState.REFUSED }
    val fractionUsed: Float?
        get() = if (spent) 1f else if (totalAllowance != null && totalAllowance > 0 && totalLeft != null) (1f - totalLeft.toFloat() / totalAllowance).coerceIn(0f, 1f) else null
}

object UsageViews {

    fun mask(key: String): String = if (key.length <= 8) "••••" else key.take(4) + "…" + key.takeLast(4)

    /** Builds [policy]'s meter from the ledger as it stands at [now]. Pure: nothing is changed. */
    fun build(policy: QuotaPolicy, keys: List<String>, usage: ProviderUsage?, now: Long, pace: CreditPace? = null): ProviderView {
        val today = QuotaPolicy.NOVIG.periodStart(now)
        val fresh = usage?.takeIf { it.dayStart == today }
        val rolled = keys.map { policy.roll(usage?.keys?.get(it) ?: KeyUsage(), now) }
        // The key the next call goes to: the first that can pay and keep its day's share or closing-lines reserve ([CreditPace]); with every
        // key held back, the first that still has credits (closing lines and the like read it).
        val firstScan = rolled.indexOfFirst { !it.refused && policy.usable(it, 1 + (pace?.floor(it, now) ?: 0), now) }
        val activeIndex = if (firstScan >= 0) firstScan else rolled.indexOfFirst { !it.refused && policy.usable(it, 1, now) }
        val rows = keys.mapIndexed { i, key ->
            val u = rolled[i]
            val usable = policy.usable(u, 1, now)
            val minuteFull = policy.perMinute?.let { cap -> u.recent.count { now - it < UsageMeter.MINUTE } >= cap } ?: false
            val state = when {
                u.refused -> KeyState.REFUSED
                i == activeIndex -> KeyState.ACTIVE
                usable -> KeyState.STANDBY
                u.coolUntil != null || minuteFull -> KeyState.COOLING
                else -> KeyState.SPENT
            }
            val until = when (state) {
                KeyState.SPENT, KeyState.REFUSED -> u.depletedUntil ?: u.nextReset(policy)
                KeyState.COOLING -> u.coolUntil ?: u.recent.filter { now - it < UsageMeter.MINUTE }.minOrNull()?.plus(UsageMeter.MINUTE)
                else -> null
            }
            KeyView(
                index = i,
                masked = mask(key),
                used = u.used,
                allowance = u.allowance(policy),
                left = u.left(policy),
                state = state,
                until = until,
                note = u.lastNote,
                lastCallMs = u.lastCallMs,
                serverReported = u.remaining != null,
                figureAtMs = u.figureAtMs.takeIf { u.remaining != null },
            )
        }
        // Paced: what today's scans may still spend, over the keys a scan could use (known plans only): a paid key's day share, a free key down to its reserve.
        val live = rolled.filter { !it.refused }
        val share = if (pace == null || live.none { it.limit != null }) null
        else live.filter { it.limit != null }.sumOf { pace.spendableToday(it, now) }
        val serverResets = live.mapNotNull { it.resetAtMs }
        val serverStarts = live.filter { it.resetAtMs != null }.map { it.periodStart }
        val lefts = rows.filter { it.state != KeyState.REFUSED }.map { it.left }
        val allowances = rows.filter { it.state != KeyState.REFUSED }.map { it.allowance }
        return ProviderView(
            policy = policy,
            keys = rows,
            totalLeft = if (policy.keyed && lefts.isNotEmpty() && lefts.all { it != null }) lefts.sumOf { it!! } else null,
            totalAllowance = if (policy.keyed && allowances.isNotEmpty() && allowances.all { it != null }) allowances.sumOf { it!! } else null,
            // The provider's own reset time when it gave one (ParlayAPI's X-RateLimit-Reset), else the calendar's.
            nextReset = if (!policy.keyed) null else serverResets.minOrNull() ?: policy.nextReset(policy.periodStart(now)),
            periodStart = if (!policy.keyed) null else if (serverResets.isNotEmpty()) serverStarts.minOrNull() else policy.periodStart(now),
            callsToday = fresh?.callsToday ?: 0,
            throttledToday = fresh?.throttledToday ?: 0,
            lastThrottleMs = usage?.lastThrottleMs,
            scanShareToday = share,
        )
    }
}
