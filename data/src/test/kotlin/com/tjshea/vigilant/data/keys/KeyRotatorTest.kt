package com.tjshea.vigilant.data.keys

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KeyRotatorTest {

    private suspend fun assertAllExhausted(rotator: KeyRotator, providerName: String, action: suspend (String) -> KeyAttemptResult<String>): AllKeysExhaustedException {
        return try {
            rotator.execute(providerName, action)
            fail("expected AllKeysExhaustedException, but the call succeeded")
            throw IllegalStateException("unreachable")
        } catch (e: AllKeysExhaustedException) {
            e
        }
    }

    @Test
    fun `uses the only key when it succeeds`() = runTest {
        val rotator = KeyRotator(listOf("key-a"))
        val result = rotator.execute<String>("test") { key -> KeyAttemptResult.Success("ok:$key") }
        assertEquals("ok:key-a", result)
    }

    @Test
    fun `rejects an empty key list up front`() {
        try {
            KeyRotator(emptyList())
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `rotates to the next key when the first is rate limited`() = runTest {
        val rotator = KeyRotator(listOf("key-a", "key-b"))
        val attempted = mutableListOf<String>()

        val result = rotator.execute<String>("test") { key ->
            attempted += key
            if (key == "key-a") KeyAttemptResult.RateLimited(retryAfterMs = 60_000) else KeyAttemptResult.Success("ok")
        }

        assertEquals("ok", result)
        assertEquals(listOf("key-a", "key-b"), attempted)
    }

    @Test
    fun `rotates to the next key when the first is invalid`() = runTest {
        val rotator = KeyRotator(listOf("key-a", "key-b"))
        val attempted = mutableListOf<String>()

        val result = rotator.execute<String>("test") { key ->
            attempted += key
            if (key == "key-a") KeyAttemptResult.Invalid else KeyAttemptResult.Success("ok")
        }

        assertEquals("ok", result)
        assertEquals(listOf("key-a", "key-b"), attempted)
    }

    @Test
    fun `throws once every key is exhausted, naming the provider and count`() = runTest {
        val rotator = KeyRotator(listOf("key-a", "key-b"))
        val exception = assertAllExhausted(rotator, "SharpAPI") { KeyAttemptResult.Invalid }
        assertTrue(exception.message!!.contains("SharpAPI"))
        assertTrue(exception.message!!.contains("2"))
    }

    @Test
    fun `a rate-limited key becomes available again after its cooldown passes`() = runTest {
        var now = 0L
        val rotator = KeyRotator(listOf("key-a"), clock = { now })

        assertAllExhausted(rotator, "test") { KeyAttemptResult.RateLimited(retryAfterMs = 60_000) }

        // Not enough time has passed — still exhausted.
        now += 30_000
        assertAllExhausted(rotator, "test") { KeyAttemptResult.Success("should not be reached") }

        // Cooldown has now passed — key is usable again.
        now += 31_000
        val result = rotator.execute<String>("test") { KeyAttemptResult.Success("recovered") }
        assertEquals("recovered", result)
    }

    @Test
    fun `an exhausted (invalid) key does not recover on its own`() = runTest {
        var now = 0L
        val rotator = KeyRotator(listOf("key-a"), clock = { now })

        assertAllExhausted(rotator, "test") { KeyAttemptResult.Invalid }

        now += 10_000_000 // a very long time later
        assertAllExhausted(rotator, "test") { KeyAttemptResult.Success("should never happen") }
    }

    @Test
    fun `a rate-limited key's cooldown clears on its own once the clock passes it`() = runTest {
        var now = 0L
        val rotator = KeyRotator(listOf("key-a", "key-b"), clock = { now })

        // key-a gets rate limited, key-b succeeds.
        rotator.execute<String>("test") { key -> if (key == "key-a") KeyAttemptResult.RateLimited(5_000) else KeyAttemptResult.Success("b") }

        // Time passes enough for key-a's cooldown to clear.
        now += 6_000
        val snapshot = rotator.snapshot()
        val keyAStatus = snapshot.first { it.key == "key-a" }.status
        assertTrue(keyAStatus is KeyStatus.Available)
    }

    @Test
    fun `snapshot reports current status of every key`() = runTest {
        val rotator = KeyRotator(listOf("key-a", "key-b"))
        rotator.execute<String>("test") { key -> if (key == "key-a") KeyAttemptResult.Invalid else KeyAttemptResult.Success("ok") }

        val snapshot = rotator.snapshot()
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.first { it.key == "key-a" }.status is KeyStatus.Exhausted)
        assertTrue(snapshot.first { it.key == "key-b" }.status is KeyStatus.Available)
    }
}
