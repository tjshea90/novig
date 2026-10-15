package com.tjshea.vigilant.data.keys

/**
 * One picture of an API key's use when two machines spend it (Tj, 2026-10-15: "Make sure I can still see accurate api usage meters in the app on my phone"). With the scan on GitHub the engine makes most of the
 * calls and keeps its own ledger ([UsageMeter]); this phone still makes some (taps, closing lines, grading). The meters, Diagnostics and the health checks show [merge]'s book, so what a key has spent is what
 * both did, and what it has left is the provider's own newest word. Pure.
 *
 * Per key, both ledgers rolled to [now] first (a new day or month starts fresh on both):
 *  - **A provider's own figure** (`remaining`, sent with an answer or read from its account page) is the truth at the moment it was read, and either machine may hold the newest ([KeyUsage.figureAtMs]): the newest
 *    one decides `remaining`, `limit`, `used` (= limit - remaining when the allowance is known), the provider's reset time and a depleted rest.
 *  - **Our own count** (a provider that sends no figure) is a sum: each machine counted only its own calls.
 *  - Calls, units charged and the last call add up; a throttle or a refusal on either machine counts (the later rest wins).
 * Keyless counters (`callsToday`, throttles) add up when both are from today.
 */
object UsageMerge {

    /** [local] with [cloud] laid over it; [cloud]'s keys must already be this phone's own key strings ([CloudUsage.rekey]). Providers and keys on one side only are kept as they are. */
    fun merge(local: UsageBook, cloud: UsageBook, now: Long): UsageBook {
        if (cloud.providers.isEmpty()) return local
        val ids = local.providers.keys + cloud.providers.keys
        return UsageBook(ids.associateWith { id ->
            val l = local.providers[id]
            val c = cloud.providers[id]
            when {
                c == null -> l!!
                l == null -> c
                else -> provider(QuotaPolicy.ALL.firstOrNull { it.id == id }, l, c, now)
            }
        })
    }

    fun provider(policy: QuotaPolicy?, l: ProviderUsage, c: ProviderUsage, now: Long): ProviderUsage {
        val today = QuotaPolicy.NOVIG.periodStart(now)
        val lToday = l.dayStart == today
        val cToday = c.dayStart == today
        val keys = (l.keys.keys + c.keys.keys).associateWith { k ->
            val a = l.keys[k]
            val b = c.keys[k]
            when {
                b == null -> a!!
                a == null -> b
                policy == null -> if ((a.lastCallMs ?: 0L) >= (b.lastCallMs ?: 0L)) a else b
                else -> key(policy, a, b, now)
            }
        }
        return ProviderUsage(
            keys = keys,
            dayStart = if (lToday || cToday) today else l.dayStart,
            callsToday = (if (lToday) l.callsToday else 0) + (if (cToday) c.callsToday else 0),
            throttledToday = (if (lToday) l.throttledToday else 0) + (if (cToday) c.throttledToday else 0),
            lastThrottleMs = listOfNotNull(l.lastThrottleMs, c.lastThrottleMs).maxOrNull(),
        )
    }

    fun key(policy: QuotaPolicy, local: KeyUsage, cloud: KeyUsage, now: Long): KeyUsage {
        val l = policy.roll(local, now)
        val c = policy.roll(cloud, now)
        val newerCall = if ((l.lastCallMs ?: 0L) >= (c.lastCallMs ?: 0L)) l else c
        // The provider's newest figure of its own, if either side has one.
        val figure = listOf(l, c).filter { it.remaining != null }.maxByOrNull { it.figureAtMs ?: it.lastCallMs ?: 0L }
        val base = (figure ?: newerCall)
        val samePeriod = l.periodStart == c.periodStart
        var out = base.copy(
            calls = l.calls + c.calls,
            charged = l.charged + c.charged,
            lastCallMs = listOfNotNull(l.lastCallMs, c.lastCallMs).maxOrNull(),
            lastCost = newerCall.lastCost,
            recent = l.recent + c.recent,
            firstSeenMs = listOfNotNull(l.firstSeenMs, c.firstSeenMs).minOrNull(),
            depletedUntil = listOfNotNull(rest(l, figure), rest(c, figure)).maxOrNull(),
            coolUntil = listOfNotNull(l.coolUntil, c.coolUntil).maxOrNull(),
            refused = newerCall.refused,
            refusals = newerCall.refusals,
            lastNote = newerCall.lastNote ?: base.lastNote,
            limit = base.limit ?: l.limit ?: c.limit,
        )
        out = if (figure != null) {
            // The provider's word: what is left is what it said; what was used follows from the allowance when it is known.
            val limit = out.limit
            out.copy(used = if (limit != null && figure.remaining != null) (limit - figure.remaining).coerceAtLeast(0) else figure.used)
        } else {
            // Our own counts: each machine counted its own calls, in the same period.
            out.copy(used = if (samePeriod) l.used + c.used else (if (l.periodStart >= c.periodStart) l else c).used, remaining = null)
        }
        return out
    }

    /**
     * A side's "spent until" rest, unless the provider's newest figure shows credits left and was read AFTER that rest was set (the side's last call is when it set it): a key with something left is not spent,
     * and a stale rest on one machine must not hold the other machine's fresher word hostage.
     */
    private fun rest(side: KeyUsage, figure: KeyUsage?): Long? {
        val until = side.depletedUntil ?: return null
        val lifted = figure != null && (figure.remaining ?: 0) > 0 && (figure.figureAtMs ?: 0L) > (side.lastCallMs ?: 0L)
        return until.takeUnless { lifted }
    }
}
