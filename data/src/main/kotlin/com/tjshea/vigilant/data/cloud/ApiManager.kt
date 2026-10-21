package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The engine's smart API manager (XO1, Tj, 2026-10-21): "If any keys are failing, make it a smart api manager that tries to diagnose or restart the API without breaking or interfering with anything else. If any
 * important apis are not responding, automatically try to get a fresh GitHub runner on the next scan. If the api is not accepted after a few new GitHub runners, send me a message and turn off the API toggle until I
 * look at it."
 *
 * It sits on the [ApiMonitor] (the free account read of every API that is on, every few minutes, a failing one again at once and then after 30 s, 1, 2 and 5 min) and climbs a ladder for an API whose check fails, one
 * rung at a time and only while nothing else explains it:
 *  1. DIAGNOSE: every line says why it failed ([ApiCheckLine.kind]: refused, unreachable, error). A check that fails while the SCANS are reading that API fine ([Hooks.answeredAt]) is the check's trouble, not the API's:
 *     it is noted and left alone, nothing is restarted or replaced (a runner swap costs a cold first scan and every bid up).
 *  2. RESTART after [RESTART_AT] failures in a row: the API's client in this process starts again clean ([Hooks.restart]: its "down" memory, a governor rest) and it is checked at once. Nothing else is touched.
 *  3. A FRESH RUNNER after [ESCALATE_STREAK] failures and [ESCALATE_MS] of failing, for an IMPORTANT API ([IMPORTANT]: the data the scan and the bids stand on): the engine ends this job after the scan it is on
 *     ([Hooks.freshRunner]) and the workflow draws another runner, which brings another address (a provider that blocks a data-center range, a half-dead connection). At most one swap every [MIN_SWAP_GAP_MS], and the
 *     count of swaps in a row is carried from job to job in the status ([CloudStatus.apiHandovers]).
 *  4. OFF after [MAX_SWAPS] fresh runners that did not help (or, for an API that is not important, after [OPTIONAL_OFF_MS] of failing): the API's switch is turned off for the engine and the phone ([Hooks.turnOff]) and
 *     Tj is told why, once. It stays off until he looks (a switch turned back on starts the ladder again).
 * When EVERY API that is on fails at the same time, the runner's own connection is the likely fault: that is one runner swap (up to [MAX_SWAPS] in a row) and no switch is touched.
 * A passing check clears everything for that API. A spent or rate-limited API is not failing (it is accepted: the allowance is another matter, [ApiGovernor] and [ApiAdvisor] own it).
 */
class ApiManager(
    private val monitor: ApiChecks,
    private val hooks: Hooks,
    private val clock: () -> Long = System::currentTimeMillis,
    previousSwaps: Map<String, Int> = emptyMap(),
) {
    /** What the manager asks of the rest of the engine. Everything here is a small, isolated action; none of them touches a scan, a bid or an order directly. */
    interface Hooks {
        /** Starts the API's client in this process again from clean (never throws; touches only that API). */
        suspend fun restart(id: String)
        /** Asks for a new runner after the scan in progress; false when one is already on its way. */
        fun freshRunner(why: String): Boolean
        /** Switches the API off for the engine and (through the config the phone adopts) the phone, and records why. */
        suspend fun turnOff(id: String, why: String)
        /** Says something to Tj in the Messages (words only). */
        suspend fun say(key: String, title: String, body: String, priority: Int)
        /** When a scan last read data from this API (the engine's clock), 0 = not in this job. */
        fun answeredAt(id: String): Long
        fun log(line: String)
    }

    private class St(var swaps: Int = 0) {
        var failingSince = 0L
        var restarted = false
        var swapAt = 0L
        var off = false
        var note = ""
        var noteAt = 0L
    }

    private val state = HashMap<String, St>()
    private val runner = St()
    private var runnerSaid = false
    private var offsToday = 0
    private var offDay = ""
    init { previousSwaps.forEach { (id, n) -> if (n > 0) { if (id == RUNNER) runner.swaps = n else state[id] = St(n) } } }

    /** The lines for the status: the monitor's, with when the scans last read each API and what the manager is doing about it. */
    fun lines(): List<ApiCheckLine> = monitor.lines().map { l -> l.copy(scanOkAtMs = hooks.answeredAt(l.id), fix = state[l.id]?.note.orEmpty()) }

    /** Fresh runners asked for per API in a row (the runner as a whole under [RUNNER]); only the non-zero ones. Carried to the next job. */
    fun swaps(): Map<String, Int> = (state.filterValues { it.swaps > 0 }.mapValues { it.value.swaps } + (if (runner.swaps > 0) mapOf(RUNNER to runner.swaps) else emptyMap()))

    /** When the newest swap was asked for (0 = none in this job): a carried count older than [CARRY_MS] is dropped at the next job's start. */
    fun swapAtMs(): Long = (state.values.map { it.swapAt } + runner.swapAt).maxOrNull() ?: 0L

    /** What the manager is doing, for the phone's API manager section: one entry per API it has something to say about. */
    fun fixes(): List<ApiFix> = state.entries.filter { it.value.note.isNotBlank() }.sortedBy { it.key }.map { (id, st) ->
        ApiFix(id, ApiToggles.name(id), if (st.off) "off" else if (st.swaps > 0) "fresh-runner" else if (st.restarted) "restarted" else "watching", st.note, st.swaps, st.noteAt)
    }

    /** Every API is checked again at once (a pull to refresh, Tj's "Test"). */
    fun recheckAll() = monitor.recheckNow()

    /** One turn: the checks that are due, then the decisions. Never throws; the monitor's loop calls it about every 10 s. */
    suspend fun step(s: ScanSettings) {
        runCatching { monitor.tick(s) }
        runCatching { decide(clock()) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; hooks.log("API manager: ${it.javaClass.simpleName}: ${it.message?.take(120)}") }
    }

    private suspend fun decide(now: Long) {
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        if (day != offDay) { offDay = day; offsToday = 0 }
        val all = monitor.lines()
        // An API that was switched off by the manager and is on again (Tj looked at it) starts over.
        for (l in all) if (l.on) state[l.id]?.takeIf { it.off }?.let { it.off = false; it.swaps = 0; it.note = ""; it.failingSince = 0L; it.restarted = false }
        val checked = all.filter { it.on && it.hasKey && it.atMs > 0L }
        val failing = checked.filter { !it.ok }
        val healthy = checked.count { it.ok }
        // Every API on failing together: the runner's connection, not the APIs.
        val runnerWide = failing.size >= RUNNER_WIDE_MIN && healthy == 0 && failing.all { it.failStreak >= RESTART_AT }
        if (healthy > 0 && runner.swaps > 0) { runner.swaps = 0; runnerSaid = false }
        if (runnerWide) {
            if (runner.swaps < MAX_SWAPS && now - runner.swapAt >= MIN_SWAP_GAP_MS) {
                if (hooks.freshRunner("none of the ${failing.size} APIs that are on answers: this runner's connection")) {
                    runner.swaps++; runner.swapAt = now
                    hooks.log("API manager: ${failing.size} APIs fail at once: asking for a fresh runner (${runner.swaps} of $MAX_SWAPS)")
                    hooks.say("runner-wide-$now", "Replacing the GitHub runner: no API answers", "Every API that is on failed its check at once (${failing.joinToString(", ") { it.name }}), which points at this runner's connection and not at the APIs. The engine ends this job after the scan it is on so a new runner starts (${runner.swaps} of $MAX_SWAPS). No API was switched off.", 0)
                }
            } else if (runner.swaps >= MAX_SWAPS && !runnerSaid) {
                runnerSaid = true
                hooks.say("runner-wide-stuck-$day", "No API answers, on any runner", "Every API that is on failed its check on $MAX_SWAPS new runners in a row. No switch was touched (it is probably GitHub's network or a wide outage). The engine keeps checking and recovers by itself when they answer.", 1)
            }
        }
        for (l in checked) {
            val st = state.getOrPut(l.id) { St() }
            if (l.ok) {
                if (st.swaps > 0 || st.restarted) {
                    hooks.log("API manager: ${l.name} answers again" + if (st.swaps > 0) " after ${st.swaps} fresh runner(s)" else " after a restart")
                    if (st.swaps > 0) hooks.say("api-back-${l.id}-$now", "${l.name} is working again", "It failed its check, the engine restarted it and asked for ${st.swaps} fresh runner(s); it answers now. Nothing to do.", 1)
                }
                st.failingSince = 0L; st.restarted = false; st.swaps = 0; st.note = ""; st.noteAt = 0L
                continue
            }
            if (st.failingSince == 0L) st.failingSince = minOf(l.atMs, now)
            val failingFor = now - st.failingSince
            val scanAt = hooks.answeredAt(l.id)
            if (scanAt > 0L && now - scanAt <= SCAN_FINE_MS) {
                setNote(st, "its check fails (${l.kind.ifBlank { "error" }}: ${l.message.take(60)}) but the scans are reading it fine: left alone", now)
                continue
            }
            if (l.failStreak >= RESTART_AT && !st.restarted) {
                st.restarted = true
                hooks.log("API manager: restarting ${l.name} (${l.failStreak} failed checks: ${l.kind.ifBlank { "error" }}: ${l.message.take(80)})")
                runCatching { hooks.restart(l.id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                monitor.recheckNow(l.id)
                setNote(st, "restarted its connection after ${l.failStreak} failed checks (${l.kind.ifBlank { "error" }})", now)
            } else if (st.note.isBlank() || !st.restarted) setNote(st, "check failing (${l.kind.ifBlank { "error" }}): trying again in a moment", now)
            if (runnerWide || l.failStreak < ESCALATE_STREAK || failingFor < ESCALATE_MS) continue
            if (l.id in IMPORTANT) {
                if (st.swaps >= MAX_SWAPS) off(l, st, "it was not accepted on ${st.swaps} fresh runners in a row (last: ${l.message.take(80)})", now)
                else if (now - st.swapAt >= MIN_SWAP_GAP_MS && hooks.freshRunner("${l.name} is not answering (${l.kind.ifBlank { "error" }}: ${l.message.take(80)})")) {
                    st.swaps++; st.swapAt = now
                    hooks.log("API manager: asking for a fresh runner for ${l.name} (${st.swaps} of $MAX_SWAPS)")
                    setNote(st, "asked for a fresh runner (${st.swaps} of $MAX_SWAPS): ${l.message.take(70)}", now)
                    hooks.say("api-swap-${l.id}-$now", "Replacing the GitHub runner: ${l.name} is not answering", "${l.name} failed ${l.failStreak} checks in a row (${l.kind.ifBlank { "error" }}: ${l.message.take(100)}) and the scans are not getting data from it. The engine restarted its connection and now ends this job after the scan it is on so a new runner (a new address) starts: ${st.swaps} of $MAX_SWAPS. After $MAX_SWAPS it will switch ${l.name} off and tell you.", 0)
                }
            } else if (failingFor >= OPTIONAL_OFF_MS) off(l, st, "it kept failing for ${failingFor / 60_000L} min (last: ${l.message.take(80)})", now)
        }
    }

    private fun setNote(st: St, note: String, now: Long) { if (st.note != note) { st.note = note; st.noteAt = now } }

    private suspend fun off(l: ApiCheckLine, st: St, why: String, now: Long) {
        if (st.off) return
        if (offsToday >= MAX_OFFS_PER_DAY) { setNote(st, "still failing; not switched off (the day's limit of $MAX_OFFS_PER_DAY switch-offs is used)", now); return }
        st.off = true; offsToday++
        hooks.log("API manager: switching ${l.name} off: $why")
        runCatching { hooks.turnOff(l.id, why) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; hooks.log("API manager: could not switch ${l.name} off: ${it.javaClass.simpleName}") }
        setNote(st, "switched off: $why. Fix its key or plan, then switch it on in Settings › APIs.", now)
        hooks.say("api-off-${l.id}-$now", "Switched ${l.name} off", "${l.name} was not accepted: $why. The engine restarted it and tried ${st.swaps} fresh GitHub runner(s). It is OFF now and stays off until you look at it: check its key and plan, then switch it back on in Settings › APIs (the ladder starts again).", 1)
    }

    companion object {
        /** The key under which "the runner as a whole" is counted in [swaps]. */
        const val RUNNER = "runner"
        /** Failed checks in a row before the API's client is restarted, and before a fresh runner may be asked for, and how long it has to have been failing. */
        const val RESTART_AT = 3
        const val ESCALATE_STREAK = 5
        const val ESCALATE_MS = 4 * 60_000L
        /** Fresh runners asked for in a row before the API is switched off; the least time between two asks. */
        const val MAX_SWAPS = 3
        const val MIN_SWAP_GAP_MS = 10 * 60_000L
        /** An API that is not important is switched off after this much failing (no runner swap for it). */
        const val OPTIONAL_OFF_MS = 30 * 60_000L
        /** A scan that read data from the API this recently says the API itself is fine whatever its check says. */
        const val SCAN_FINE_MS = 10 * 60_000L
        /** This many APIs failing at once with none healthy is the runner's connection. */
        const val RUNNER_WIDE_MIN = 3
        /** Switch-offs in a day, at most: a wide outage must never leave the engine with nothing on. */
        const val MAX_OFFS_PER_DAY = 3
        /** A swap count carried from the previous job is only believed if the swap was this recent. */
        const val CARRY_MS = 3 * 3_600_000L

        /** The data the scan, the fair lines and the bids stand on: a failing one earns a fresh runner. The others (The Odds API, Unabated, OddsPapi Free, Kalshi, Polymarket) are retried and, if they never recover, switched off. */
        val IMPORTANT = setOf("sgo", "boltodds", "parlay", "propline", "pinnwire", "pinnapi", "oddspapi", "sxbet")

        /** The switch ids a feed's answer counts for ("pinnacle" is one feed for both Pinnacle hosts). */
        fun switchIdsOfFeed(feedId: String): List<String> {
            val provider = CloudDashboard.providerOf(feedId)
            return when (provider) {
                "pinnacle" -> listOf("pinnwire", "pinnapi")
                "kalshi", "polymarket" -> listOf(provider)
                else -> listOfNotNull(ApiToggles.idOfPolicy(provider))
            }
        }
    }
}

/** One line of the API manager's word for the phone (status `apiManager`): what is wrong with the API and what the engine did about it. */
@kotlinx.serialization.Serializable
data class ApiFix(
    val id: String,
    val name: String,
    /** "watching" (a check failing), "restarted", "fresh-runner" or "off". */
    val state: String,
    val note: String,
    val swaps: Int = 0,
    val atMs: Long = 0L,
)
