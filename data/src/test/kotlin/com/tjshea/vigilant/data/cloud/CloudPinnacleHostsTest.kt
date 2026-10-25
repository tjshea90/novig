package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.reference.PinnapiClient
import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PX10c (2026-10-25): the engine's Pinnacle source asks only the hosts whose own switch is on. Before, with Pinnodds on (Tj's key is now the free Trial: 100 REST requests a day), a failing Pinnodds
 * board fell through to PinnWire although PinnWire was switched off, and the engine reported "All 18 Pinnacle (PinnWire) keys are used up".
 */
class CloudPinnacleHostsTest {

    private val hosts = CopyOnWriteArrayList<String>()

    /** Every call is recorded and refused, as the free Pinnodds key's board is once its day is spent. */
    private val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        hosts += chain.request().url.host
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("rate_limited")
            .body("""{"error":"rate_limited"}""".toResponseBody("application/json".toMediaType())).build()
    }).build()

    private val keys = mapOf(CloudKeys.PINNODDS to listOf("po-key"), CloudKeys.PINNWIRE to listOf("pw-key"), CloudKeys.PINNAPI to listOf("pa-key"))

    private fun sources(scope: CoroutineScope) = CloudSources(
        http, Json { ignoreUnknownKeys = true },
        UsageMeter(JsonFileStore(java.io.File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() })),
        scope, kotlin.io.path.createTempDirectory("cloud").toFile(), { keys[it].orEmpty() },
    )

    private val nhl = Leagues.byNovigName("NHL")!!

    @Test
    fun `a switched-off PinnWire or pinnapi is never called when Pinnodds fails`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val s = ScanSettings(usePinnacle = true, gates = ApiGates(usePinnodds = true, usePinnwire = false, usePinnapi = false))
        val pinnacle = sources(scope).sources(s).single { it.id == PinnapiClient.ID }
        runCatching { pinnacle.odds(nhl, s) }
        scope.cancel()
        assertTrue("Pinnodds was asked: $hosts", hosts.any { it == "pinnodds.com" })
        assertFalse("PinnWire is off: $hosts", hosts.any { it == "pinnwire.com" })
        assertFalse("pinnapi is off: $hosts", hosts.any { it == "pinnapi.com" })
    }

    @Test
    fun `with every Pinnacle API off there is no Pinnacle source at all`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val s = ScanSettings(usePinnacle = true, gates = ApiGates(usePinnodds = false, usePinnwire = false, usePinnapi = false))
        assertEquals(emptyList<String>(), sources(scope).sources(s).map { it.id }.filter { it == PinnapiClient.ID })
        scope.cancel()
    }
}
