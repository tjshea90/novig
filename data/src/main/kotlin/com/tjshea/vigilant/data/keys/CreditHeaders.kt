package com.tjshea.vigilant.data.keys

/**
 * A metered provider's own figures from a reply's headers (Tj, 2026-09-30: "It allows the API key to tell the app how many credits I have
 * left … so the meter is accurate"). The Odds API's `x-requests-remaining/used/last`, ParlayAPI's `x-credits-remaining/cost`, and ParlayAPI's
 * best-practices `X-RateLimit-Remaining` / `X-RateLimit-Reset` (credits left in the period, and the epoch second it resets: the plan's own
 * cycle, not the calendar's). On a free ParlayAPI key `X-RateLimit-Limit` is the per-second cap and its Remaining is per second, so the
 * rate-limit pair counts only when the limit reads "unlimited" (a paid plan) or is a monthly-sized number. `X-Request-ID` goes into errors.
 */
data class CreditHeaders(
    val remaining: Int? = null,
    val used: Int? = null,
    val cost: Int? = null,
    val resetAtMs: Long? = null,
    val requestId: String? = null,
) {
    companion object {
        /** A "reset" sooner than this is a per-second or per-minute window, not the plan's period. */
        private const val MIN_PERIOD_MS = 60 * 60_000L

        /** A reset further away than this isn't a monthly plan's (a garbled header). */
        private const val MAX_PERIOD_MS = 40L * 24 * 60 * 60_000L

        /** [header] reads one header by name (case-insensitive, as OkHttp's are); [now] judges the reset time. */
        fun read(header: (String) -> String?, now: Long): CreditHeaders {
            fun int(name: String) = header(name)?.trim()?.toDoubleOrNull()?.toInt()
            val limit = header("x-ratelimit-limit")?.trim()
            val monthly = limit != null && (limit.equals("unlimited", true) || (limit.toDoubleOrNull() ?: 0.0) >= 1_000)
            val reset = if (!monthly) null else header("x-ratelimit-reset")?.trim()?.toDoubleOrNull()?.toLong()?.let { v ->
                if (v > 100_000_000_000L) v else v * 1000
            }?.takeIf { it - now in MIN_PERIOD_MS..MAX_PERIOD_MS }
            return CreditHeaders(
                remaining = int("x-requests-remaining") ?: int("x-credits-remaining") ?: if (monthly) int("x-ratelimit-remaining") else null,
                used = int("x-requests-used"),
                cost = int("x-requests-last") ?: int("x-credits-cost"),
                resetAtMs = reset,
                requestId = header("x-request-id")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
