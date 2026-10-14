package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.OddsScanner
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.RecheckReport
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.engine.EvQuote
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.TakeLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class CloudEngineTest {
    private val start = System.currentTimeMillis() + 6 * 3_600_000L

    private fun row(id: String): Opportunity {
        val event = NovigEvent("e-$id", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "Boston Celtics @ New York Knicks", start)
        val market = NovigMarket("m-$id", "e-$id", "SPREAD", "OPEN", "Spread", start, MarketFee.GAME, listOf(NovigOutcome("o-$id", "NYK -3.5", "TBD"), NovigOutcome("x-$id", "BOS +3.5", "TBD")), strike = -3.5)
        return Opportunity(
            league = Leagues.byNovigName("NBA")!!, event = event, market = market, outcome = market.outcomes.first(), marketLabel = "Spread", kind = LineKind.SPREAD,
            selection = "NYK -3.5", fair = null, fairProbability = 0.55, quote = EvQuote(0.55, 0.48, 0.0075), ladder = listOf(TakeLevel(0.48, 5_000)), depth = null,
            suggestedStake = null, novigWidth = null, bookFetchedAtMs = System.currentTimeMillis(), fairUpdatedMs = null, refEvent = null, lineKey = null, target = null,
        )
    }

    private fun result(vararg ids: String, at: Long = System.currentTimeMillis()) =
        ScanResult(games = emptyList(), opportunities = ids.map(::row), stats = ScanStats(ids.size, ids.size, ids.size, ids.size, ids.size), computedAtMs = at)

    private fun report(r: ScanResult?) = ScanReport(r, emptyList(), null, 1, 0, 0, 0, null, emptyList(), null)

    private class FakeScanner(val body: suspend (ScanSettings, (ScanResult) -> Unit, Int) -> ScanReport) : OddsScanner {
        val calls = AtomicInteger()
        val settingsSeen = CopyOnWriteArrayList<ScanSettings>()
        override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport {
            settingsSeen += settings
            return body(settings, onPartial, calls.incrementAndGet())
        }
        override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit) = RecheckReport(null, 0, 0, null)
        override suspend fun reprice(settings: ScanSettings): ScanResult? = null
        override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = emptySet()
    }

    private val on = CloudConfig(rev = 1, on = true, settings = ScanSettings(leagues = setOf("NBA"), includeLive = true, scanner = ScannerMode.BOTH), passGapSeconds = 5)

    private fun engine(scanner: OddsScanner, cfg: () -> CloudConfig?, published: MutableList<Pair<CloudFeed?, CloudStatus>>, idleExitMs: Long = 600_000L) = CloudEngine(
        scanner = scanner, sources = { emptyList() }, config = { cfg() },
        publish = { f, s -> published += f to s; null },
        pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, idleExitMs = idleExitMs, heartbeatEveryMs = 1,
    )

    @Test
    fun `a scan under the phone's settings publishes the feed, partials first, and the settings have live games and the CNO modes switched off`() = runBlocking {
        val scanner = FakeScanner { _, partial, _ ->
            partial(result("a")); delay(40); report(result("a", "b"))
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { on }, published)
        val end = e.run(untilMs = System.currentTimeMillis() + 150)
        assertEquals(CloudEngine.End.TIME_UP, end)
        val feeds = published.mapNotNull { it.first }
        assertTrue("a partial list went out before the final one", feeds.any { it.rows.size == 1 })
        assertTrue("the final list went out", feeds.any { it.rows.size == 2 })
        assertEquals(1L, published.last().second.configRev)
        val s = scanner.settingsSeen.first()
        assertFalse(s.includeLive)
        assertEquals(ScannerMode.VIGILANT, s.scanner)
        assertFalse(s.liveOnly)
        assertTrue(e.passes >= 1)
    }

    @Test
    fun `switched off, nothing is scanned and the job ends after the idle time`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { on.copy(on = false) }, published, idleExitMs = 30)
        val end = e.run(untilMs = System.currentTimeMillis() + 10_000)
        assertEquals(CloudEngine.End.IDLE, end)
        assertEquals(0, scanner.calls.get())
        assertTrue(published.all { it.first == null && it.second.idle })
    }

    @Test
    fun `a new settings revision during a scan starts it again under the new settings`() = runBlocking {
        var cfg = on
        val scanner = FakeScanner { _, partial, n ->
            if (n == 1) { partial(result("a")); delay(5_000); report(result("a")) } else report(result("c"))
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { cfg }, published)
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(60); cfg = on.copy(rev = 2, settings = on.settings.copy(minEvPercent = 0.03)) }
        e.run(untilMs = System.currentTimeMillis() + 400)
        assertTrue("scanned again", scanner.calls.get() >= 2)
        assertEquals(0.03, scanner.settingsSeen.last().minEvPercent, 0.0)
        assertTrue(published.any { it.second.configRev == 2L && it.first?.rows?.map { r -> r.event.eventId } == listOf("e-c") })
    }

    @Test
    fun `a failed scan is reported in the heartbeat and the engine carries on`() = runBlocking {
        val scanner = FakeScanner { _, _, n -> if (n == 1) throw IllegalStateException("boom") else report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { on }, published)
        e.run(untilMs = System.currentTimeMillis() + 200)
        assertTrue(published.any { it.second.problem?.contains("boom") == true })
        assertTrue(e.passes >= 1)
    }
}
