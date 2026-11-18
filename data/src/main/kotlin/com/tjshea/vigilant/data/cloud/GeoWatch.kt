package com.tjshea.vigilant.data.cloud

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Novig's device-location check, read BEFORE it bites (NVR, Tj 2026-11-18: "look for any changes or updates in novig api docs" ... "used to their full potential"). Novig's key holder must open the Novig app in a
 * permitted state within 3 days or placements answer `451 GEOLOCATION_EXPIRED`; the engine used to learn that only when an order was refused. Since 2026-10-27 `GET /v3/account/geolocation` says when the newest
 * device pass expires ([com.tjshea.vigilant.data.novig.trading.NovigTradingClient.geolocation]), so Tj can be told while there is still time. Reading it changes nothing and never works around the check: a
 * lapsed pass is for Tj's phone alone to renew (the class C stand-down rule stays).
 */
object GeoWatch {
    enum class Level { OK, SOON, BLOCKED, ADDRESS }

    /** Under this long to go, Tj is told. */
    const val SOON_MS = 12 * 3_600_000L
    const val SOON_REPEAT_MS = 6 * 3_600_000L
    const val BLOCKED_REPEAT_MS = 3 * 3_600_000L

    private val ET = ZoneId.of("America/New_York")
    private val AT = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ET)

    /** A refusal about the runner's ADDRESS (the engine swaps the runner for it, [AddressWatch]), not about the key holder's device. */
    private val NETWORK_CODES = setOf("ANONYMIZED_NETWORK", "RESTRICTED_NETWORK_REGION")

    fun level(expiresAtMs: Long?, code: String?, now: Long): Level = when {
        code != null && code in NETWORK_CODES -> Level.ADDRESS
        code != null -> Level.BLOCKED
        expiresAtMs == null || expiresAtMs <= now -> Level.BLOCKED
        expiresAtMs - now < SOON_MS -> Level.SOON
        else -> Level.OK
    }

    private fun span(ms: Long): String {
        val m = (ms / 60_000L).coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /** The state in words, for the status and the engine log. */
    fun note(expiresAtMs: Long?, code: String?, now: Long): String = when (level(expiresAtMs, code, now)) {
        Level.ADDRESS -> "Novig refuses this runner's address ($code): the engine is drawing another runner."
        Level.BLOCKED -> if (code != null) "Novig's device check refuses orders now ($code): open the Novig app on your phone in a permitted state."
        else "Novig has no valid device check on record: open the Novig app on your phone in a permitted state."
        Level.SOON -> "Novig's device check runs out in ${span(expiresAtMs!! - now)} (${AT.format(Instant.ofEpochMilli(expiresAtMs))} ET): open the Novig app on your phone before then or the engine stops ordering."
        Level.OK -> "Novig's device check is good for ${span(expiresAtMs!! - now)} more (until ${AT.format(Instant.ofEpochMilli(expiresAtMs))} ET)."
    }

    /** Whether to message Tj now: on entering SOON or BLOCKED, and again every 6 h (SOON) or 3 h (BLOCKED) while it holds; never for OK or a runner-address refusal. */
    fun shouldTell(level: Level, previous: Level?, lastToldMs: Long, now: Long): Boolean = when (level) {
        Level.OK, Level.ADDRESS -> false
        Level.SOON -> previous != Level.SOON || now - lastToldMs >= SOON_REPEAT_MS
        Level.BLOCKED -> previous != Level.BLOCKED || now - lastToldMs >= BLOCKED_REPEAT_MS
    }
}
