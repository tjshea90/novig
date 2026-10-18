package com.tjshea.vigilant.data.cloud

/**
 * A runner whose internet address Novig refuses is replaced, not endured (Tj, 2026-10-15: "If novig refuses a runner's address, it should find a way to reconnect or try a different runner automatically").
 *
 * Why this is Novig's documented behaviour and not a way round its screen (NOVIG_API.md §4, docs.novig.com/api/errors): the IP check refuses restricted states and VPN, proxy or Tor addresses, and
 * says "a data-center address is fine"; a runner's address that lands in a restricted state or on a list (`RESTRICTED_NETWORK_REGION`, `ANONYMIZED_NETWORK`) is an unlucky draw of a data center, and the
 * next job is drawn again. The OTHER check, the key holder's DEVICE (the Novig app opened on Tj's phone within 3 days: `GEOLOCATION_*`) is not about the runner and is never touched here.
 *
 * Policy (pure, so the cases are tests; changed by Tj, 2026-10-18, TCL115: "I want the GitHub runner to immediately be restarted after every refused novig address, until there is a runner with an accepted address"):
 * as soon as the key route is refused for the ADDRESS ([MIN_AGE_MS] after the job started, so the first request has been made), the job ends and the workflow starts the next one, again and again with no cap and no
 * cooldown, until a runner Novig accepts. A job that gets through its first [WINDOW_MS] without a refusal ends the streak (the count is for the log and the app only).
 */
class AddressWatch(
    private val startedAtMs: Long,
    previousCount: Int = 0,
    previousAtMs: Long = 0L,
    /** True while the key route stands down for an address refusal ([CloudNovig.addressRefused]). */
    private val refused: (Long) -> Boolean,
) {
    /** Hand-overs in a row, and when the last one was: carried to the next job in the status file. */
    var count = previousCount
        private set
    var atMs = previousAtMs
        private set
    private var lastRefusedAt = 0L

    /** Whether this job should end now so that a new runner is drawn. Call about once a second. */
    fun shouldHandOver(now: Long): Boolean {
        if (refused(now)) lastRefusedAt = now
        val age = now - startedAtMs
        if (age > WINDOW_MS) {
            // a runner Novig accepted for its first minutes is a good runner: the streak is over
            if (lastRefusedAt == 0L) count = 0
            return false
        }
        if (age < MIN_AGE_MS) return false
        if (lastRefusedAt == 0L || now - lastRefusedAt > RECENT_MS) return false
        count += 1
        atMs = now
        return true
    }

    /** A hand-over decided outside [shouldHandOver] (the address was refused before the first scan): counted the same way. */
    fun noteHandOver(now: Long) { count += 1; atMs = now }

    /** One line for the engine log: why the watch did or did not hand over (the 2026-10-17 miss: refused for 5 minutes and never handed over). */
    fun describe(now: Long): String =
        "address watch: refused-now=${refused(now)} age=${(now - startedAtMs) / 1000}s lastRefusedAgo=${if (lastRefusedAt == 0L) "never" else "${(now - lastRefusedAt) / 1000}s"} streak=$count"

    companion object {
        /** Long enough for the key route to have been tried and a transient refusal to have passed. */
        const val MIN_AGE_MS = 10_000L
        /** A refusal later than this in a job's life is not the runner's address (it worked until then). */
        const val WINDOW_MS = 6 * 60 * 60_000L
        const val RECENT_MS = 60_000L

        /** Novig's codes for a verdict on the request's ADDRESS. The device check (`GEOLOCATION_*`) is deliberately not here. */
        val ADDRESS_CODES = listOf("ANONYMIZED_NETWORK", "RESTRICTED_NETWORK_REGION")

        fun isAddressRefusal(keyDownText: String?): Boolean = keyDownText != null && ADDRESS_CODES.any { keyDownText.contains(it) }
    }
}
