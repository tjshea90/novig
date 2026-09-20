package com.tjshea.vigilant.data.keys

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRotatorTest {

    @Test
    fun `uses the only key when it succeeds`() = runTest {
        val rotator = KeyRotator(listOf("key-a"))
        val result = rotator.execute<String>("test") { key -> KeyAttemptResult.Success("ok:$key") }
        assertEquals("ok:key-a", result)
    }

    @Test
    fun `rejects an empty key list up front`() {
        assertThrows(IllegalArgumentException::class.java) { KeyRotator(emptyList()) }
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
    fun `throws once every key is exhausted, naming the provider`() = runTest {
        val rotator = KeyRotator(listOf("key-a", "key-b"))

        val exception = assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking {
                rotator.execute<String>("SharpAPI") { KeyAttemptResult.Invalid }
            }
        }
        assertTrue(exception.message!!.contains("SharpAPI"))
        assertTrue(exception.message!!.contains("2"))
    }

    @Test
    fun `a rate-limited key becomes available again after its cooldown passes`() = runTest {
        var now = 0L
        val rotator = KeyRotator(listOf("key-a"), clock = { now })

        // First call: rate limited for 60s.
        val firstAttempt = assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking {
                rotator.execute<String>("test") { KeyAttemptResult.RateLimited(retryAfterMs = 60_000) }
            }
        }
        assertTrue(firstAttempt.message!!.isNotEmpty())

        // Not enough time has passed — still exhausted.
        now += 30_000
        assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking {
                rotator.execute<String>("test") { KeyAttemptResult.Success("should not be reached") }
            }
        }

        // Cooldown has now passed — key is usable again.
        now += 31_000
        val result = rotator.execute<String>("test") { KeyAttemptResult.Success("recovered") }
        assertEquals("recovered", result)
    }

    @Test
    fun `an exhausted (invalid) key does not recover on its own`() = runTest {
        var now = 0L
        val rotator = KeyRotator(listOf("key-a"), clock = { now })

        assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking {
                rotator.execute<String>("test") { KeyAttemptResult.Invalid }
            }
        }

        now += 10_000_000 // a very long time later
        assertThrows(AllKeysExhaustedException::class.java) {
            kotlinx.coroutines.runBlocking {
                rotator.execute<String>("test") { KeyAttemptResult.Success("should never happen") }
            }
        }
    }

    @Test
    fun `a successful call resets a key that was previously cooling down`() = runTest {
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
