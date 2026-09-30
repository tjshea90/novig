package com.tjshea.vigilant.data.keys

import java.util.Locale

/** What a round of work (a scan, a Check odds now) asked of one provider: requests made, and the allowance units they used (credits for The Odds API). */
data class ProviderCost(val id: String, val calls: Int, val units: Int)

/** What one round of work cost each provider, and when it ran and how long it took. In memory only: a fact about the last one since the app opened. */
data class RoundCost(val startedAtMs: Long, val tookMs: Long, val costs: List<ProviderCost>, val note: String? = null)

/** The difference between two looks at the usage ledger: what happened in between. */
object UsageDelta {

    fun between(before: UsageBook, after: UsageBook): List<ProviderCost> = after.providers.mapNotNull { (id, now) ->
        val was = before.providers[id]
        // The counters restart at midnight UTC: a new day's whole count is what happened since.
        val calls = if (was != null && was.dayStart == now.dayStart) now.callsToday - was.callsToday else now.callsToday
        val units = now.keys.entries.sumOf { (key, u) ->
            val old = was?.keys?.get(key)
            // A key whose period rolled (or that is new) started from nothing.
            if (old == null || old.periodStart != u.periodStart || u.used < old.used) u.used else u.used - old.used
        }
        if (calls <= 0 && units <= 0) null else ProviderCost(id, calls.coerceAtLeast(0), if (now.keys.isEmpty()) calls.coerceAtLeast(0) else units.coerceAtLeast(0))
    }.sortedByDescending { it.calls }
}

enum class RunwayLevel { OK, WATCH, SHORT }

/** One keyed provider's answer to "will this last?". */
data class RunwayLine(val id: String, val name: String, val level: RunwayLevel, val text: String)

/**
 * Whether each keyed provider's allowance lasts to its next reset at the pace Tj is using it (Tj, 2026-09-29: "tell me which apis deplete too quickly
 * for daily use so I can add more keys"). The pace is what's been used since the period began, so it counts the hours he wasn't scanning too: a
 * projection, not a promise; it says so in the words. Pure: the meters go in, lines come out.
 */
object Runway {

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    /** Too early in a period for a pace to mean anything: a projection needs at least this much of it gone. */
    private fun minElapsed(policy: QuotaPolicy) = if (policy.period == QuotaPeriod.MONTH_UTC) 2 * DAY else 3 * HOUR

    fun lines(views: List<ProviderView>, now: Long): List<RunwayLine> {
        val lines = views.mapNotNull { line(it, now) }
        // Pinnacle is PinnWire's keys first, then pinnapi's (the same source, one after the other): PinnWire running short is only
        // short when pinnapi can't carry the rest of the day (Tj's diagnostics, 2026-09-30: "PinnWire … SHORT" beside "pinnapi … none used").
        val wire = views.firstOrNull { it.policy.id == QuotaPolicy.PINNWIRE.id }
        val api = views.firstOrNull { it.policy.id == QuotaPolicy.PINNAPI.id }
        return lines.map { l ->
            if (l.id != QuotaPolicy.PINNWIRE.id || l.level == RunwayLevel.OK || wire == null || api == null) l else backedUp(l, wire, api, now) ?: l
        }
    }

    /** [l] (PinnWire's line) judged with pinnapi's allowance behind it: both used at the pace so far, over both allowances. Null: no help. */
    private fun backedUp(l: RunwayLine, wire: ProviderView, api: ProviderView, now: Long): RunwayLine? {
        if (api.keys.none { it.state == KeyState.ACTIVE || it.state == KeyState.STANDBY }) return null
        val apiLeft = api.totalLeft ?: return null
        val allowance = (wire.totalAllowance ?: return null) + (api.totalAllowance ?: return null)
        val used = wire.keys.filter { it.state != KeyState.REFUSED }.sumOf { it.used } + api.keys.filter { it.state != KeyState.REFUSED }.sumOf { it.used }
        val start = wire.periodStart ?: wire.policy.periodStart(now)
        val reset = wire.nextReset ?: wire.policy.nextReset(start)
        val elapsed = (now - start).coerceAtLeast(1L)
        val projected = if (elapsed < minElapsed(wire.policy)) used.toLong() else Math.round(used.toDouble() * (reset - start) / elapsed)
        val level = when {
            projected > allowance -> RunwayLevel.SHORT
            projected * 5 > allowance * 4 -> RunwayLevel.WATCH
            else -> RunwayLevel.OK
        }
        if (level == RunwayLevel.SHORT) return RunwayLine(l.id, l.name, l.level, l.text + " · pinnapi's ${num(apiLeft)} behind it aren't enough either")
        val head = l.text.substringBefore(" · at this pace").substringBefore(" · SPENT")
        return RunwayLine(l.id, l.name, level, "$head · then pinnapi takes over (${num(apiLeft)} left): about ${num(projected.toInt())} of the two's ${num(allowance)} by the reset: ${level.name}")
    }

    fun line(v: ProviderView, now: Long): RunwayLine? {
        val policy = v.policy
        val allowance = v.totalAllowance ?: return null
        if (!policy.keyed || v.keys.isEmpty()) return null
        val live = v.keys.filter { it.state != KeyState.REFUSED }
        val used = live.sumOf { it.used }
        val left = v.totalLeft ?: (allowance - used).coerceAtLeast(0)
        val reset = v.nextReset ?: policy.nextReset(policy.periodStart(now))
        val start = v.periodStart ?: policy.periodStart(now)
        val elapsed = now - start
        val span = (reset - start).coerceAtLeast(1L)
        val period = if (policy.period == QuotaPeriod.MONTH_UTC) "this month" else "today"
        val keyCount = "${v.keys.size} key${if (v.keys.size == 1) "" else "s"}"
        val head = "${policy.displayName}: ${num(used)} of ${num(allowance)} ${policy.unit} used $period ($keyCount), ${num(left)} left · resets ${UsageMeter.whenText(reset, now)}"
        val refused = v.keys.count { it.state == KeyState.REFUSED }
        val refusedNote = if (refused > 0) " · $refused key${if (refused == 1) "" else "s"} refused by the provider (wrong or deleted)" else ""

        val usable = live.any { it.state == KeyState.ACTIVE || it.state == KeyState.STANDBY || it.state == KeyState.COOLING }
        if (!usable || left <= 0) {
            val until = live.mapNotNull { it.until }.minOrNull() ?: reset
            return RunwayLine(policy.id, policy.displayName, RunwayLevel.SHORT, "$head · SPENT ${UsageMeter.untilText(until, now)}: add a key$refusedNote")
        }
        if (elapsed < minElapsed(policy) || used <= 0) {
            val level = fractionLevel(used, allowance)
            val note = if (used <= 0) "none used yet" else "too early in the ${if (policy.period == QuotaPeriod.MONTH_UTC) "month" else "day"} to project a pace"
            return RunwayLine(policy.id, policy.displayName, level, "$head · $note: ${level.name}$refusedNote")
        }
        val projected = Math.round(used.toDouble() * span / elapsed)
        val perMs = used.toDouble() / elapsed
        val runsOutIn = (left / perMs).toLong()
        val level = when {
            projected > allowance -> RunwayLevel.SHORT
            projected * 5 > allowance * 4 -> RunwayLevel.WATCH
            else -> RunwayLevel.OK
        }
        val pace = if (level == RunwayLevel.SHORT) {
            "at this pace the last of it goes ${whenAt(now + runsOutIn, now)}, before the reset: SHORT (add keys, or scan less)"
        } else {
            "at this pace about ${num(projected)} by the reset: ${level.name}"
        }
        return RunwayLine(policy.id, policy.displayName, level, "$head · $pace$refusedNote")
    }

    /** "in 3h 12m" for soon, "on Oct 1" for later. */
    private fun whenAt(at: Long, now: Long): String = UsageMeter.whenText(at, now).let { if (it.startsWith("in ") || it == "now") it else "on $it" }

    private fun fractionLevel(used: Int, allowance: Int): RunwayLevel = when {
        allowance <= 0 -> RunwayLevel.OK
        used * 10 >= allowance * 9 -> RunwayLevel.SHORT
        used * 10 >= allowance * 6 -> RunwayLevel.WATCH
        else -> RunwayLevel.OK
    }

    /**
     * What a round that cost [cost] of [v]'s allowance means for Tj: how many like it a period of allowance buys ("a scan costs about 6, so 100 a
     * day is 16 scans, 33 with the 2 keys"). Null when the provider isn't keyed, has no allowance, or the round didn't touch it.
     */
    fun roundsNote(v: ProviderView, cost: ProviderCost, what: String): String? {
        val allowance = v.totalAllowance ?: return null
        if (!v.policy.keyed || v.keys.isEmpty() || cost.units <= 0) return null
        val per = if (v.policy.period == QuotaPeriod.MONTH_UTC) "a month" else "a day"
        val perKey = v.keys.firstNotNullOfOrNull { it.allowance } ?: return null
        val rounds = allowance / cost.units
        val one = perKey / cost.units
        return if (v.keys.size == 1) "$what costs ${cost.units} → $rounds $per" else "$what costs ${cost.units} → $one $per per key, $rounds with ${v.keys.size} keys"
    }

    private fun num(n: Int): String = String.format(Locale.US, "%,d", n)
    private fun num(n: Long): String = String.format(Locale.US, "%,d", n)
}
