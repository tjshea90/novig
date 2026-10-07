package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.scanner.TrapGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** The race's loops with every network piece faked: what is asked only while a game is live, what is written, and what it never does. */
class FeedRaceRunnerTest {

    private fun tennis(h1: Int, a1: Int) = """{"events":[{"id":17269437,"homeTeam":{"name":"Zhizhen Zhang"},"awayTeam":{"name":"Tomas Machac"},"homeScore":{"current":0,"period1":$h1},"awayScore":{"current":0,"period1":$a1},"tournament":{"name":"Shanghai, China"}}]}"""

    private class FakeSockets : SocketOpener {
        val opened = CopyOnWriteArrayList<String>()
        val sent = CopyOnWriteArrayList<String>()
        override fun open(url: String, onText: (String) -> Unit, onClosed: (String) -> Unit): FeedSocket {
            opened += url
            Thread {
                Thread.sleep(40)
                if (url.contains("sports-api")) {
                    onText("ping")
                    onText("""{"gameId": 6383932, "leagueAbbreviation": "atp", "homeTeam": "Zhizhen Zhang", "awayTeam": "Tomas Machac", "score": "0-0", "period": "S1", "live": true}""")
                    Thread.sleep(60)
                    onText("""{"gameId": 6383932, "leagueAbbreviation": "atp", "homeTeam": "Zhizhen Zhang", "awayTeam": "Tomas Machac", "score": "1-0", "period": "S1", "live": true}""")
                }
            }.start()
            return object : FeedSocket {
                override fun send(text: String): Boolean { sent += url.substringAfter("//").substringBefore("/") + " <- " + text; return true }
                override fun close() {}
            }
        }
    }

    private fun dir() = java.nio.file.Files.createTempDirectory("race").toFile().also { it.deleteOnExit() }

    private suspend fun until(what: String, check: () -> Boolean) = withTimeout(15_000) { while (!check()) delay(25) }.also { assertTrue(what, check()) }

    @Test
    fun `with a live game it reads the feeds, writes what changed, answers the socket's ping, and never places anything`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val t0 = System.currentTimeMillis()
        val sofaCalls = AtomicInteger()
        val urls = CopyOnWriteArrayList<String>()
        val sockets = FakeSockets()
        val journal = FeedRaceJournal(dir())
        val tradeCalls = AtomicInteger()
        val runner = FeedRaceRunner(
            scope = scope,
            fetch = { url ->
                urls += url
                when {
                    url.contains("sofascore") -> Fetched(if (sofaCalls.getAndIncrement() < 2) tennis(2, 1) else tennis(3, 1), 120)
                    url.contains("gamma-api") -> Fetched("[]", 50)
                    else -> null
                }
            },
            sockets = sockets,
            liveGames = { listOf(LiveGame("e1", "Tomas Machac @ Zhizhen Zhang Round of 16", "ATP", "TENNIS", "m1")) },
            novigTrades = { listOf(TrapGuard.Trade("o1", 0.55, 100, t0 - 1_000), TrapGuard.Trade("o2", 0.45, 50, t0 - 900)).also { tradeCalls.incrementAndGet() } },   // the same trades every read, as Novig lists them
            journal = journal, pollMs = 60, discoverMs = 120, reportMs = 300,
        )
        runner.start()
        assertTrue(runner.running)
        until("readings and trades written") { runner.status.value.readings >= 3 && runner.status.value.novigTrades >= 2 }
        // Sofascore was asked for tennis (and nothing else: no NHL, no MLB, no ESPN path for a tennis game).
        assertTrue(urls.toString(), urls.any { it == "https://api.sofascore.com/api/v1/sport/tennis/events/live" })
        assertFalse(urls.toString(), urls.any { it.contains("espn") || it.contains("nhle") || it.contains("statsapi.mlb") })
        // Polymarket's score socket was opened, its ping answered with pong, and its frames became readings.
        until("pong sent") { sockets.sent.any { it.endsWith("<- pong") } }
        assertTrue(sockets.opened.toString(), "wss://sports-api.polymarket.com/ws" in sockets.opened)
        assertEquals(setOf("tennis"), runner.status.value.sports)
        assertEquals(1, runner.status.value.liveGames)
        // The journal holds: a baseline and a change per feed, the trades once each (repeats of the same trade are not written again).
        until("journal") { journal.read(0).trades.size >= 2 && journal.read(0).scores.count { it.src == "sofa" } >= 2 && journal.read(0).scores.count { it.src == "poly" } >= 2 }
        val tape = journal.read(0)
        assertEquals(2, tape.trades.size)
        val sofa = tape.scores.filter { it.src == "sofa" }
        assertTrue(sofa.first().init); assertEquals(2 to 1, sofa.first().h to sofa.first().a); assertEquals(3 to 1, sofa.last().h to sofa.last().a)
        assertTrue(tradeCalls.get() >= 2)
        // The report is made and says something honest about so little.
        val report = runner.makeReport()
        assertNotNull(runner.lastReport)
        assertTrue(report.verdict(), report.verdict().startsWith("Live feed test:"))
        // Only GET reads and sockets: the only thing it ever sent on a socket is the pong and (for odds) a market subscription: no order route exists in its constructor at all.
        assertTrue(sockets.sent.toString(), sockets.sent.all { it.endsWith("<- pong") || it.contains("assets_ids") })
        runner.stop()
        assertFalse(runner.running)
        scope.cancel()
    }

    @Test
    fun `with nothing live on Novig it asks only for the catalog, opens no socket and makes no feed request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val urls = CopyOnWriteArrayList<String>()
        val sockets = FakeSockets()
        val catalog = AtomicInteger()
        val runner = FeedRaceRunner(
            scope = scope, fetch = { urls += it; null }, sockets = sockets, liveGames = { catalog.incrementAndGet(); emptyList() }, novigTrades = { emptyList() },
            journal = FeedRaceJournal(dir()), pollMs = 40, discoverMs = 60, reportMs = 10_000,
        )
        runner.start()
        until("catalog asked more than once") { catalog.get() >= 3 }
        delay(300)
        assertTrue(urls.toString(), urls.isEmpty())
        assertTrue(sockets.opened.toString(), sockets.opened.isEmpty())
        assertEquals(0, runner.status.value.liveGames)
        runner.stop()
        scope.cancel()
    }

    @Test
    fun `a failing catalog or feed is a problem line, not the end of the others`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        val runner = FeedRaceRunner(
            scope = scope, fetch = { if (it.contains("sofascore")) throw java.io.IOException("HTTP 403") else null }, sockets = FakeSockets(),
            liveGames = { if (calls.getAndIncrement() == 0) error("Novig 429") else listOf(LiveGame("e1", "A @ B", "ATP", "TENNIS", null)) }, novigTrades = { emptyList() },
            journal = FeedRaceJournal(dir()), pollMs = 40, discoverMs = 60, reportMs = 10_000,
        )
        runner.start()
        until("the catalog recovered") { runner.status.value.liveGames == 1 }
        assertTrue(runner.running)
        runner.stop()
        scope.cancel()
    }

    @Test
    fun `Novig's sports map to Sofascore's and its leagues to ESPN's, the rest are not read`() {
        assertEquals("tennis", FeedRaceRunner.sofaSport("TENNIS"))
        assertEquals("american-football", FeedRaceRunner.sofaSport("football"))
        assertEquals("football", FeedRaceRunner.sofaSport("SOCCER"))
        assertEquals("ice-hockey", FeedRaceRunner.sofaSport("HOCKEY"))
        assertEquals(null, FeedRaceRunner.sofaSport("MMA"))
        assertEquals("hockey/nhl", FeedRaceRunner.ESPN_PATHS["NHL"])
        assertEquals("Zhizhen Zhang Round of 16" to "Tomas Machac", FeedRaceRunner.novigNames("Tomas Machac @ Zhizhen Zhang Round of 16"))
        assertTrue(FeedRace.sameGame("Zhizhen Zhang" to "Tomas Machac", FeedRaceRunner.novigNames("Tomas Machac @ Zhizhen Zhang Round of 16")))
        // Two different matches in the same round are not one game just because both say "Round of 16".
        assertFalse(FeedRace.sameGame("Marco Trungelliti" to "Rei Sakamoto", FeedRaceRunner.novigNames("Tomas Machac @ Zhizhen Zhang Round of 16")))
    }
}
