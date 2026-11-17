package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * PLS1 (Tj, 2026-11-17): he resubscribed to Streaming Lite mid-day; the engine, which read each PropLine plan hourly, kept the key as `free`, spent until midnight, so PropLine and its stream stayed silent.
 * A spent Free-plan key is read again every five minutes and the first bigger `X-Daily-Limit` frees it and makes it the Streaming key; keys with allowance left or a paid plan are left to the hourly read.
 */
class PropLineUpgradeWatchTest {
    private var now = 1_000_000_000_000L
    private val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }), clock = { now })
    private val calls = ArrayList<String>()
    /** What PropLine says for each key right now: (limit, used). */
    private val plan = HashMap<String, Pair<Int, Int>>()

    private val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val key = chain.request().header("X-API-Key").orEmpty()
        calls += key
        val (limit, used) = plan.getValue(key)
        val remaining = (limit - used).coerceAtLeast(0)
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (remaining == 0) 429 else 200).message("x")
            .header("X-Daily-Limit", "$limit").header("X-Daily-Used", "$used").header("X-Daily-Remaining", "$remaining").header("X-Daily-Reset", "${(now / 1000) + 3_600}")
            .body("[]".toResponseBody(null)).build()
    }).build()

    private val account = PropLineAccount(http, { listOf("paid", "free2") }, meter, clock = { now })

    @Test
    fun aResubscribedKeyIsFoundWithinFiveMinutesAndTheStreamingKeyAppears() = runBlocking<Unit> {
        plan["paid"] = 1_000 to 1_000; plan["free2"] = 1_000 to 400
        account.refresh()                                      // the hourly read: both on the Free plan, "paid" spent
        assertNull(account.streamingKey())
        calls.clear()

        now += 60_000L; assertEquals("not due yet: read a minute ago", 0, account.watchUpgrade()); assertEquals(emptyList<String>(), calls)

        plan["paid"] = 250_000 to 1_000                        // Tj resubscribes
        now += 5 * 60_000L
        assertEquals(1, account.watchUpgrade())
        assertEquals("only the spent key was read; the free key with allowance left keeps the hourly read", listOf("paid"), calls)
        assertEquals("paid", account.streamingKey())
        assertEquals("streaming-lite", account.last.getValue("paid").tier)

        // The ledger no longer holds it as spent: the pool uses it first again.
        val pool = KeyPool(QuotaPolicy.PROPLINE, { listOf("free2", "paid") }, meter)
        var used = ""
        pool.execute(cost = 1) { k -> used = k; KeyAttemptResult.Success(Unit) }
        assertEquals("paid", used)
    }

    @Test
    fun aSpentKeyThatIsStillFreeIsLookedAtEveryFiveMinutesNotMore() = runBlocking<Unit> {
        plan["paid"] = 1_000 to 1_000; plan["free2"] = 1_000 to 1_000
        account.refresh(); calls.clear()
        repeat(3) { now += 5 * 60_000L + 1; account.watchUpgrade() }
        assertEquals(6, calls.size)                            // 3 rounds x 2 spent keys, none while a read was under 5 minutes old
        now += 60_000L; account.watchUpgrade(); assertEquals(6, calls.size)
        assertNull(account.streamingKey())
    }

    @Test
    fun aPaidKeyIsNeverWatchedOnlyReadHourly() = runBlocking<Unit> {
        plan["paid"] = 250_000 to 10; plan["free2"] = 1_000 to 400
        account.refresh(); calls.clear()
        now += 6 * 60_000L
        assertEquals(0, account.watchUpgrade()); assertEquals(emptyList<String>(), calls)
        assertNotNull(account.streamingKey())
    }
}
