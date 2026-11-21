package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.rfq.RfqReconnect.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MK13: when the maker may open its RFQ socket again, by the rules of docs.novig.com/api/rfq/socket ("Reconnect with backoff"), api/errors (451, 429) and the close codes. */
class RfqReconnectTest {
    private fun plan(c: Cause, n: Int = 0) = RfqReconnect.plan(c, n)
    private val minute = 60_000L

    @Test fun everyFailureBacksOffAndDoublesUpToAMinute() {
        val delays = (0..7).map { plan(Cause.Error("boom"), it).delayMs }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L), delays)
        assertEquals("a server error is no different", 2_000L, plan(Cause.Upgrade(503, null, null)).delayMs)
        assertTrue("no failure is ever retried at once (the old loop retried every second, and every upgrade costs 32 stream tokens)", delays.all { it >= 2_000L })
    }

    @Test fun aRefusedLocationIsNotRetriedForAWhile() {
        // "On any 451, stop and fix the cause before you retry. A client that keeps retrying a 451 can get the account locked with a 423."
        assertEquals(10 * minute, plan(Cause.Upgrade(451, "ANONYMIZED_NETWORK", null), 0).delayMs)
        assertEquals(30 * minute, plan(Cause.Upgrade(451, "GEOLOCATION_EXPIRED", null), 1).delayMs)
        assertEquals(30 * minute, plan(Cause.Closed(1008, "GEOLOCATION_OUT_OF_BOUNDARY", 20 * minute), 0).delayMs)
        assertEquals(30 * minute, plan(Cause.Upgrade(423, "ACCOUNT_LOCKED", null)).delayMs)
        assertEquals("the whole exchange paused (maintenance, 2026-11-21) is looked at again in 90 s, not 30 minutes", 90_000L, plan(Cause.Upgrade(423, "SYSTEM_LOCKED", null)).delayMs)
        assertEquals(30 * minute, plan(Cause.Upgrade(403, "KYC_REQUIRED", null)).delayMs)
        assertEquals(30 * minute, plan(Cause.Upgrade(401, "SIGNATURE_REJECTED", null)).delayMs)
    }

    @Test fun aRevokedOrExpiredKeyWaitsForANewOne() {
        assertEquals(30 * minute, plan(Cause.Closed(1008, "KEY_REVOKED", 3 * minute)).delayMs)
        assertEquals(30 * minute, plan(Cause.Closed(1008, "KEY_EXPIRED", 3 * minute)).delayMs)
        assertEquals("our own message was too big: a bug, not a storm", minute, plan(Cause.Closed(1009, "MESSAGE_TOO_BIG", 1_000L)).delayMs)
    }

    @Test fun tooManySocketsWaitsForTheStaleOnesToCloseAndARateLimitWaitsItsRetryAfter() {
        val a = plan(Cause.Upgrade(429, "TOO_MANY_SOCKETS", null), 0).delayMs; val b = plan(Cause.Upgrade(429, "TOO_MANY_SOCKETS", null), 1).delayMs
        assertTrue("at least 30 s: Novig closes a socket whose Pong went missing within about 15 s", a >= 30_000L && b > a)
        assertTrue(plan(Cause.Upgrade(429, "TOO_MANY_SOCKETS", null), 9).delayMs <= 5 * minute)
        assertEquals(7_000L, plan(Cause.Upgrade(429, "RATE_LIMIT_EXCEEDED", 7L)).delayMs)
        assertEquals("a Retry-After of 1 s still waits 5 s", 5_000L, plan(Cause.Upgrade(429, "RATE_LIMIT_EXCEEDED", 1L)).delayMs)
        assertEquals(30_000L, plan(Cause.Upgrade(429, null, null)).delayMs)
    }

    @Test fun aMakerNotYetKnownEverywhereIsRegisteredAgain() {
        val p = plan(Cause.Upgrade(404, "MAKER_NOT_FOUND", null))
        assertTrue(p.reregister); assertTrue(p.delayMs >= 5_000L)
        assertFalse(plan(Cause.Upgrade(429, "TOO_MANY_SOCKETS", null)).reregister)
    }

    @Test fun aSocketThatWasHealthyReconnectsSoonAndAFlappingOneDoesNot() {
        assertEquals("a close after a minute of being open (the 15-minute re-check, a Novig deploy): back in 2 s", 2_000L, plan(Cause.Closed(1001, "going away", 20 * minute), 0).delayMs)
        assertEquals("closing again within seconds: back-off", 8_000L, plan(Cause.Closed(1006, "", 3_000L), 2).delayMs)
    }
}
