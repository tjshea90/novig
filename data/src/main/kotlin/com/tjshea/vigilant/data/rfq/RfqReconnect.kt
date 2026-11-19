package com.tjshea.vigilant.data.rfq

import kotlin.math.min

/**
 * When the maker may open its RFQ socket again after it closed or refused it (MK13, Tj 2026-11-19: "the system must use the api exactly how the docs describe"). Pure, so a test can read every rule:
 *  - docs.novig.com/api/rfq/socket, "Reconnect": "Reconnect with backoff, then subscribe again." Every upgrade spends 32 tokens of the subaccount's `stream` throttle (api-reference/rfq/streaming), shared with the
 *    engine's market feed, so a loop of failed upgrades starves it.
 *  - "Why the socket closes": 1008 `KEY_REVOKED` / `KEY_EXPIRED` need a valid key; 1008 with a `451` geolocation code needs the key holder to open the Novig app; 1009 `MESSAGE_TOO_BIG` is our bug.
 *  - docs/api/errors, `451`: "stop and fix the cause before you retry. A client that keeps retrying a 451 can get the account locked with a 423."
 *  - `429 TOO_MANY_SOCKETS`: the key already has 10 sockets open (a socket whose Pong went missing is closed by Novig within about 15-30 s); `429 RATE_LIMIT_EXCEEDED`: wait the `Retry-After`.
 *  - `404 MAKER_NOT_FOUND`: a new maker reaches every server within a minute: register again, retry after a few seconds.
 *  - `423` (locked account), `401`/`403` (key or scope or KYC): nothing a retry fixes.
 */
object RfqReconnect {
    sealed interface Cause {
        /** The upgrade was refused with an HTTP answer ([code] is the JSON `code`, [retryAfterS] the `Retry-After` header). */
        data class Upgrade(val status: Int, val code: String?, val retryAfterS: Long?) : Cause
        /** An open socket closed: [code] and [reason] as Novig sent them, after [openForMs] of being open. */
        data class Closed(val code: Int, val reason: String, val openForMs: Long) : Cause
        /** The connection failed before any HTTP answer (network, TLS, timeout). */
        data class Error(val message: String) : Cause
    }

    /** [delayMs] before the next attempt, whether the maker must be registered again, and the words for the status line. */
    data class Plan(val delayMs: Long, val reregister: Boolean, val why: String)

    private const val MIN = 60_000L
    /** A socket that stayed open this long was healthy: the failure count starts again. */
    const val STABLE_MS = 60_000L

    fun plan(cause: Cause, consecutive: Int): Plan {
        val exp = min(60_000L, 2_000L shl min(consecutive, 5))
        return when (cause) {
            is Cause.Upgrade -> when {
                cause.status == 404 -> Plan(maxOf(exp, 5_000L), true, "not a maker on every server yet (${cause.code ?: "404"}): registering again")
                cause.status == 429 && cause.code == "TOO_MANY_SOCKETS" -> Plan(min(5 * MIN, 30_000L shl min(consecutive, 3)), false, "the key has 10 sockets open (TOO_MANY_SOCKETS): waiting for stale ones to close")
                cause.status == 429 -> Plan(maxOf((cause.retryAfterS ?: 30L) * 1_000L, 5_000L), false, "rate limited (${cause.code ?: "429"}): waiting the Retry-After")
                cause.status == 451 -> Plan(if (consecutive == 0) 10 * MIN else 30 * MIN, false, "Novig refused the location (${cause.code ?: "451"}): not retrying for a while, fix the cause first")
                cause.status == 423 -> Plan(30 * MIN, false, "the account is locked (423)")
                cause.status == 401 || cause.status == 403 -> Plan(30 * MIN, false, "the key or the account is refused (${cause.status} ${cause.code ?: ""})".trim())
                else -> Plan(exp, false, "upgrade answered ${cause.status} ${cause.code ?: ""}".trim())
            }
            is Cause.Closed -> {
                val r = cause.reason.uppercase()
                when {
                    cause.code == 1008 && ("KEY_REVOKED" in r || "KEY_EXPIRED" in r) -> Plan(30 * MIN, false, "the key was revoked or expired (${cause.reason})")
                    cause.code == 1008 && ("GEOLOCATION" in r || "NETWORK" in r || "REGION" in r || "451" in r) -> Plan(30 * MIN, false, "Novig closed the socket on the location check (${cause.reason}): the key holder must open the Novig app")
                    cause.code == 1009 -> Plan(MIN, false, "a message of ours was too big (1009)")
                    cause.openForMs < STABLE_MS -> Plan(exp, false, "closed ${cause.code} ${cause.reason} after ${cause.openForMs / 1000}s")
                    else -> Plan(2_000L, false, "closed ${cause.code} ${cause.reason} after a stable connection")
                }
            }
            is Cause.Error -> Plan(exp, false, cause.message)
        }
    }
}
