package com.tjshea.vigilant.data.novig

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NovigAuthTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tokenProvider(clock: () -> Long) = NovigTokenProvider(
        httpClient = httpClient,
        credentials = NovigCredentials("client-id", "client-secret"),
        json = json,
        authUrl = server.url("/auth/emm-token").toString(),
        clock = clock,
    )

    @Test
    fun `fetches and returns a token on first use`() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token": "tok-1", "expires_in": 1800}"""))
        val provider = tokenProvider { 0L }

        val token = provider.getValidToken()

        assertEquals("tok-1", token)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `reuses a cached token instead of re-requesting while it is still fresh`() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token": "tok-1", "expires_in": 1800}"""))
        var now = 0L
        val provider = tokenProvider { now }

        provider.getValidToken()
        now += 5_000 // 5 seconds later, nowhere near the 30-minute expiry
        val second = provider.getValidToken()

        assertEquals("tok-1", second)
        assertEquals("should not have made a second HTTP call", 1, server.requestCount)
    }

    @Test
    fun `refreshes once the cached token is within the safety margin of expiring`() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token": "tok-1", "expires_in": 1800}"""))
        server.enqueue(MockResponse().setBody("""{"access_token": "tok-2", "expires_in": 1800}"""))
        var now = 0L
        val provider = tokenProvider { now }

        provider.getValidToken()
        now += 1800 * 1000 - 30_000 // inside the 60s refresh margin before the documented 30-min expiry
        val second = provider.getValidToken()

        assertEquals("tok-2", second)
        assertEquals(2, server.requestCount)
    }
}
