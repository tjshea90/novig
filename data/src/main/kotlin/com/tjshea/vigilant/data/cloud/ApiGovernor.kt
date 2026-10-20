package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * The engine turns APIs off to save usage when they are not helping (Tj, 2026-10-15: "Make the GitHub engine able to turn on and off apis as needed or to save usage when an API is not needed").
 * After each finished scan it looks at what every credit-metered API answered:
 *  - an API that was asked and answered NOTHING (no leagues, no error, not standing by, not paced) for [LIMIT] scans in a row is RESTED: the engine scans without it (its own copy of the settings has it
 *    switched off, so nothing calls it and nothing is spent) for [REST_MS] (1 h, then 3 h, then 9 h), then asks it once more;
 *  - one that answers again is forgiven (strikes cleared); one that is silent after the third rest is TURNED OFF for good: the engine asks the phone to switch it off ([CloudOverrides]) and says why
 *    ([CloudMessages]); Tj switches it back on after fixing the key or plan, and its strikes start again.
 * It never touches a keyless API (nothing to save), an API the phone has off, or the two Pinnacle boards (one shared feed), and it never makes a decision from a scan whose feeds are unknown.
 * The phone's own switch is never changed here: a rest lives in the engine's copy only, and the TURNED OFF step goes through the override file like any other request.
 */
class ApiGovernor(private val restMs: List<Long> = REST_MS) {
    data class Rest(val id: String, val sinceMs: Long, val untilMs: Long, val strikes: Int, val reason: String)

    sealed interface Event {
        val id: String
        data class Rested(override val id: String, val untilMs: Long, val strikes: Int) : Event
        data class Resumed(override val id: String) : Event
        data class TurnedOff(override val id: String) : Event
        /** An API that failed (an error and no answer) on an earlier scan answered again (Tj, 2026-10-20: "send me a status update telling me the API is now working"). */
        data class Recovered(override val id: String) : Event
    }

    private val streak = HashMap<String, Int>()
    private val strikes = HashMap<String, Int>()
    private val rests = HashMap<String, Rest>()
    private val offAsked = HashMap<String, Long>()
    private val failed = HashSet<String>()

    /** Switch ids resting now (with their reason and end), for the status and the dashboard. */
    fun resting(now: Long): List<Rest> = rests.values.filter { it.untilMs > now }.sortedBy { it.id }

    /** The settings the engine scans under: [s] with every resting API off. */
    fun apply(s: ScanSettings, now: Long): ScanSettings = ApiToggles.rest(s, resting(now).map { it.id })

    /** Try again (Tj, CH23): forgives [policyId]'s rests, strikes and silence so the next scan asks it; true when it was resting or struck. Nothing else changes. */
    fun retry(policyId: String): Boolean {
        val id = ApiToggles.idOfPolicy(policyId) ?: policyId
        val was = rests.remove(id) != null
        val hadStrikes = strikes.remove(id) != null
        streak.remove(id)
        return was || hadStrikes
    }

    /** Carries the APIs the engine asked the phone to turn off from the previous job (so a handover does not forget it asked). */
    fun seedOff(ids: Collection<String>, now: Long) { ids.forEach { offAsked.putIfAbsent(it, now) } }
    fun askedOff(): Set<String> = offAsked.keys.toSet()

    /** Call once per finished scan with the feeds' answers and the phone's settings; returns what happened. */
    fun observe(feeds: List<FeedLine>, settings: ScanSettings, now: Long): List<Event> {
        val events = ArrayList<Event>()
        // 1. housekeeping: a rest that ended is asked again; an API the phone now has off (Tj, or our own request applied) needs no governing
        for (r in rests.values.toList()) {
            if (ApiToggles.isOn(settings, r.id) == false) { rests.remove(r.id); streak.remove(r.id); continue }
            if (r.untilMs <= now) { rests.remove(r.id); events += Event.Resumed(r.id) }
        }
        for (id in offAsked.keys.toList()) if (ApiToggles.isOn(settings, id) == false) { offAsked.remove(id); strikes.remove(id) }
        // 2. what each credit-metered API answered on this scan
        val byApi = feeds.groupBy { ApiToggles.idOfPolicy(CloudDashboard.providerOf(it.id)) }
        for ((id, fs) in byApi) {
            if (id == null || ApiToggles.policy(id)?.keyed != true || ApiToggles.isOn(settings, id) != true) continue
            if (id in rests) continue
            val answered = fs.any { it.fetched + it.reused > 0 }
            if (answered && failed.remove(id)) events += Event.Recovered(id)
            else if (!answered && fs.any { it.error != null }) failed += id
            val silent = fs.all { it.fetched + it.reused == 0 && it.standingBy == 0 && it.skipped == 0 && it.heldBack == null && it.error == null }
            when {
                answered -> { streak.remove(id); strikes.remove(id) }
                silent -> {
                    val n = (streak[id] ?: 0) + 1
                    streak[id] = n
                    if (n >= LIMIT) {
                        streak.remove(id)
                        val strike = (strikes[id] ?: 0) + 1
                        strikes[id] = strike
                        if (strike > restMs.size) {
                            if (id !in offAsked) { offAsked[id] = now; events += Event.TurnedOff(id) }
                        } else {
                            val until = now + restMs[strike - 1]
                            rests[id] = Rest(id, now, until, strike, "answered nothing for $LIMIT scans in a row")
                            events += Event.Rested(id, until, strike)
                        }
                    }
                }
                else -> streak.remove(id)   // an error or a pacing hold is not silence
            }
        }
        return events
    }

    companion object {
        const val LIMIT = 3
        val REST_MS = listOf(3_600_000L, 3 * 3_600_000L, 9 * 3_600_000L)
    }
}
