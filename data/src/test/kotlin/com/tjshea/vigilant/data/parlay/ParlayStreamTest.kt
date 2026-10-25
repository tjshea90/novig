package com.tjshea.vigilant.data.parlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** PS1 (2026-10-25): every scanned sport holds ParlayAPI's odds stream AND its hot stream (Pinnacle, FanDuel); a refused hot route leaves the odds streams alone. */
class ParlayStreamTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val paths = CopyOnWriteArrayList<String>()

    @After fun done() { scope.cancel(); server.shutdown() }

    private fun row(book: String, home: Int, away: Int) =
        """{"bookmaker":"$book","home_team":"Brooklyn Nets","away_team":"Indiana Pacers","last_update":1792913261000,"kind":"game","market_key":"h2h","home_ml":$home,"away_ml":$away}"""

    private fun sse(vararg rows: String) = MockResponse().setHeader("Content-Type", "text/event-stream")
        .setBody("data: {\"type\":\"connected\"}\n\ndata: {\"type\":\"initial_state\",\"data\":[${rows.joinToString(",")}]}\n\n")

    private fun start(hot: (RecordedRequest) -> MockResponse): ParlayStream {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                paths += p.substringBefore('?')
                return when {
                    p.startsWith("/v1/sse/odds/") -> sse(row("bovada", 220, -270))
                    p.startsWith("/v1/sse/hot/") -> hot(request)
                    else -> MockResponse().setBody("{}")
                }
            }
        }
        server.start()
        val live = ParlayLive()
        return ParlayStream(OkHttpClient(), Json, { "k" }, live, scope, baseUrl = server.url("/").toString().trimEnd('/'))
    }

    private fun waitFor(what: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!what() && System.currentTimeMillis() < until) Thread.sleep(50)
    }

    @Test
    fun `each sport holds the odds stream and the hot stream, both into one store`() {
        val s = start { sse(row("pinnacle", 224, -265)) }
        s.want(true, setOf("basketball_nba"))
        waitFor { s.live.size() >= 4 }
        assertTrue(paths.toString(), "/v1/sse/odds/basketball_nba" in paths && "/v1/sse/hot/basketball_nba" in paths)
        assertEquals(4, s.live.size())   // two books x two sides
        assertTrue(s.health(), s.health().contains("hot basketball_nba") && s.health().contains("odds basketball_nba"))
    }

    @Test
    fun `a refused hot route stops only the hot streams`() {
        val s = start { MockResponse().setResponseCode(402) }
        s.want(true, setOf("basketball_nba"))
        waitFor { s.health().contains("hot route refused") && s.live.size() >= 2 }
        assertTrue(s.health(), s.health().contains("hot route refused"))
        assertEquals(2, s.live.size())
        s.want(true, setOf("basketball_nba"))
        assertTrue(s.health(), !s.health().contains("hot basketball_nba"))
        assertTrue(s.health(), s.health().contains("odds basketball_nba"))
    }
}
