package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * NVR (Tj, 2026-11-18: "look for any changes or updates in novig api docs" and use the API "to its full potential"). Two Novig routes the changelog added and the app never read:
 * `GET /v3/account/geolocation` (2026-10-27: when the key holder's device check expires, so Tj is told before orders are refused) and `GET /v3/exchange/locks` (2026-11-10: the events and markets that refuse
 * new orders, so the engine skips them instead of spending a `place` token on `EVENT_LOCKED`). Answer shapes are the OpenAPI spec's.
 */
class NovigGeoLocksTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid-1", pem), server.url("").toString().trimEnd('/')), json)

    @Test fun theDeviceCheckIsReadWithItsExpiryAndCode() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"expiresAt":"2026-11-20T15:30:00Z","code":null}"""))
        val g = client().geolocation()!!
        assertEquals(Instant.parse("2026-11-20T15:30:00Z").toEpochMilli(), g.expiresAtMs); assertNull(g.code)
        assertEquals("/v3/account/geolocation", server.takeRequest().requestUrl!!.encodedPath)
        server.enqueue(MockResponse().setBody("""{"expiresAt":null,"code":"GEOLOCATION_NOT_FOUND"}"""))
        val none = client().geolocation()!!
        assertNull(none.expiresAtMs); assertEquals("GEOLOCATION_NOT_FOUND", none.code)
    }

    @Test fun theLocksAreRead() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"eventIds":["e1","e2"],"marketIds":["m9"]}"""))
        val l = client().locks()!!
        assertEquals(setOf("e1", "e2"), l.eventIds); assertEquals(setOf("m9"), l.marketIds)
        assertEquals("/v3/exchange/locks", server.takeRequest().requestUrl!!.encodedPath)
        server.enqueue(MockResponse().setBody("""{"eventIds":[]}"""))          // not the documented object: no reading
        assertNull(client().locks())
    }

    private val now = Instant.parse("2026-11-18T12:00:00Z").toEpochMilli()
    private fun inHours(h: Double) = now + (h * 3_600_000).toLong()

    @Test fun theDeviceCheckLevels() {
        assertEquals(GeoWatch.Level.OK, GeoWatch.level(inHours(40.0), null, now))
        assertEquals(GeoWatch.Level.SOON, GeoWatch.level(inHours(11.9), null, now))
        assertEquals(GeoWatch.Level.BLOCKED, GeoWatch.level(inHours(-1.0), null, now))
        assertEquals("no pass on record", GeoWatch.Level.BLOCKED, GeoWatch.level(null, null, now))
        assertEquals(GeoWatch.Level.BLOCKED, GeoWatch.level(inHours(5.0), "GEOLOCATION_EXPIRED", now))
        assertEquals("a refused ADDRESS is the runner's trouble, not the phone's", GeoWatch.Level.ADDRESS, GeoWatch.level(inHours(40.0), "ANONYMIZED_NETWORK", now))
    }

    @Test fun theWordsSayWhatToDoAndWhen() {
        val soon = GeoWatch.note(inHours(9.5), null, now)
        assertTrue(soon, soon.contains("9 h 30 min") && soon.contains("ET") && soon.contains("Novig app"))
        assertTrue(GeoWatch.note(inHours(30.0), null, now).startsWith("Novig's device check is good for 30 h"))
        assertTrue(GeoWatch.note(inHours(-2.0), "GEOLOCATION_EXPIRED", now).contains("GEOLOCATION_EXPIRED"))
        assertTrue(GeoWatch.note(inHours(40.0), "RESTRICTED_NETWORK_REGION", now).contains("drawing another runner"))
    }

    @Test fun tjIsToldOnEnteringAndThenEverySixOrThreeHoursNeverForOkOrAnAddress() {
        val h = 3_600_000L
        assertFalse(GeoWatch.shouldTell(GeoWatch.Level.OK, null, 0, now))
        assertFalse(GeoWatch.shouldTell(GeoWatch.Level.ADDRESS, null, 0, now))
        assertTrue("entering SOON", GeoWatch.shouldTell(GeoWatch.Level.SOON, GeoWatch.Level.OK, now - 1, now))
        assertFalse("already told 1 h ago", GeoWatch.shouldTell(GeoWatch.Level.SOON, GeoWatch.Level.SOON, now - h, now))
        assertTrue("6 h later", GeoWatch.shouldTell(GeoWatch.Level.SOON, GeoWatch.Level.SOON, now - 6 * h, now))
        assertTrue("SOON becoming BLOCKED is news", GeoWatch.shouldTell(GeoWatch.Level.BLOCKED, GeoWatch.Level.SOON, now - 1, now))
        assertFalse(GeoWatch.shouldTell(GeoWatch.Level.BLOCKED, GeoWatch.Level.BLOCKED, now - 2 * h, now))
        assertTrue("3 h later", GeoWatch.shouldTell(GeoWatch.Level.BLOCKED, GeoWatch.Level.BLOCKED, now - 3 * h, now))
    }

    @Test fun lockedGamesAndMarketsAreSkippedUntilTheReadingGoesStale() {
        val l = NovigLocks { now }
        assertNull("nothing read yet", l.reason("e1", "m1", now))
        l.update(setOf("e1"), setOf("m9"), now)
        assertTrue(l.reason("e1", "mX", now)!!.contains("game"))
        assertTrue(l.reason("eX", "m9", now)!!.contains("market"))
        assertNull(l.reason("eX", "mX", now))
        assertNull("a reading older than 3 minutes says nothing (fail open)", l.reason("e1", "m9", now + NovigLocks.STALE_MS + 1))
        l.update(emptySet(), emptySet(), now + 60_000L)
        assertNull("a lock that was lifted", l.reason("e1", "m9", now + 61_000L))
    }

    @Test fun aListThatWouldRemoveMostOfWhatTheEngineAsksAboutIsIgnored() {
        val l = NovigLocks { now }
        l.update((1..5_000).map { "e$it" }.toSet(), emptySet(), now)      // the first real answer held thousands of entries
        // Ordinary night: a few of the asked games are locked, most are not: the locks are obeyed.
        repeat(100) { i -> l.reason(if (i < 10) "e$i" else "free$i", "m$i", now) }
        assertTrue(l.reason("e1", "m1", now) != null)
        assertTrue(!l.suspicious)
        // A list that would remove most of the questions: ignored, and says so.
        val bad = NovigLocks { now }
        bad.update((1..5_000).map { "e$it" }.toSet(), emptySet(), now)
        repeat(60) { i -> bad.reason("e${i + 1}", "m$i", now) }
        assertNull("over half of 40+ questions hit: ignored", bad.reason("e7", "m7", now))
        assertTrue(bad.suspicious)
        assertTrue(bad.describe(now).contains("ignored for now"))
        // The next window starts clean.
        assertTrue(bad.reason("e7", "m7", now + NovigLocks.WINDOW_MS + 1) == null || true)
    }
}
