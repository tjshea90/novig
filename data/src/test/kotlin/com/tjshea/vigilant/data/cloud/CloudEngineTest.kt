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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
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

    private fun row(id: String, fetched: Long = System.currentTimeMillis()): Opportunity {
        val event = NovigEvent("e-$id", "BASKETBALL", "NBA", NovigEvent.STATUS_PREGAME, "Boston Celtics @ New York Knicks", start)
        val market = NovigMarket("m-$id", "e-$id", "SPREAD", "OPEN", "Spread", start, MarketFee.GAME, listOf(NovigOutcome("o-$id", "NYK -3.5", "TBD"), NovigOutcome("x-$id", "BOS +3.5", "TBD")), strike = -3.5)
        return Opportunity(
            league = Leagues.byNovigName("NBA")!!, event = event, market = market, outcome = market.outcomes.first(), marketLabel = "Spread", kind = LineKind.SPREAD,
            selection = "NYK -3.5", fair = null, fairProbability = 0.55, quote = EvQuote(0.55, 0.48, 0.0075), ladder = listOf(TakeLevel(0.48, 5_000)), depth = null,
            suggestedStake = null, novigWidth = null, bookFetchedAtMs = fetched, fairUpdatedMs = null, refEvent = null, lineKey = null, target = null,
        )
    }

    private fun result(vararg ids: String, at: Long = System.currentTimeMillis(), fetched: Long = System.currentTimeMillis()) =
        ScanResult(games = emptyList(), opportunities = ids.map { row(it, fetched) }, stats = ScanStats(ids.size, ids.size, ids.size, ids.size, ids.size), computedAtMs = at)

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

    private val on = CloudConfig(rev = 1, on = true, settings = ScanSettings(leagues = setOf("NBA"), includeLive = true, scanner = ScannerMode.BOTH, gates = com.tjshea.vigilant.data.scanner.ApiGates().copy(cloudHours = false)), passGapSeconds = 5)

    private fun engine(scanner: OddsScanner, cfg: () -> CloudConfig?, published: MutableList<Pair<CloudFeed?, CloudStatus>>, idleExitMs: Long = 600_000L, restartEveryMs: Long = 30_000L, fetchSettleMs: Long = 0, addressWatch: AddressWatch? = null, addressBlocked: () -> Boolean = { false }, clock: () -> Long = System::currentTimeMillis, walletProbe: (suspend (ScanSettings) -> Double?)? = null, walletEveryMs: Long = CloudEngine.WALLET_EVERY_MS, walletRetryMs: Long = CloudEngine.WALLET_RETRY_MS, trade: (suspend (List<com.tjshea.vigilant.data.scanner.Opportunity>, ScanSettings, Long) -> CloudEngine.TradeStatus?)? = null, governor: ApiGovernor = ApiGovernor(), overrideSink: suspend (CloudOverrides.Request) -> Unit = {}, messageSink: suspend (CloudMessages.Message) -> Unit = {}) = CloudEngine(
        scanner = scanner, sources = { emptyList() }, config = { cfg() },
        publish = { f, s -> published += f to s; null },
        pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, idleExitMs = idleExitMs, heartbeatEveryMs = 1, statusEveryMs = 1, refreshMinAgeMs = 20, fetchSettleMs = fetchSettleMs, restartEveryMs = restartEveryMs,
        addressWatch = addressWatch, addressBlocked = addressBlocked, clock = clock, walletProbe = walletProbe, walletEveryMs = walletEveryMs, walletRetryMs = walletRetryMs, trade = trade, governor = governor, overrideSink = overrideSink, messageSink = messageSink,
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
        // the long scan keeps the games about to start young (Tj, 2026-10-15), and the phone's own scans do not
        assertTrue(s.vigGuards.hotRefresh)
        assertFalse(on.settings.vigGuards.hotRefresh)
        assertTrue(e.passes >= 1)
    }

    @Test
    fun `a wallet under 50 cents puts the engine to sleep, no scan and no feed call, and it wakes when the balance is read again as funded`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val cash = java.util.concurrent.atomic.AtomicReference<Double?>(0.12)
        val reads = AtomicInteger()
        val e = engine(scanner, { on }, published, walletProbe = { reads.incrementAndGet(); cash.get() }, walletEveryMs = 60)
        val job = launch { e.run(untilMs = System.currentTimeMillis() + 5_000) }
        delay(40)
        assertEquals("asleep: nothing scanned", 0, scanner.calls.get())
        assertTrue("the phone is told why", published.any { it.second.walletSleep.contains("under \$0.50") && it.second.idle && it.first == null })
        val asleepReads = reads.get()
        cash.set(null)   // an unreadable balance changes nothing
        delay(120)
        assertEquals("still asleep on an unreadable balance", 0, scanner.calls.get())
        cash.set(25.0)
        delay(300)
        assertTrue("awake again once funded", scanner.calls.get() >= 1)
        assertTrue("the balance was read only now and then, not every pass", reads.get() - asleepReads < 12)
        assertEquals("", published.last().second.walletSleep)
        job.cancel()
    }

    @Test
    fun `before the trader judges, listed bets with an old Novig price are read again, and a bet whose edge is gone is not ordered`() = runBlocking {
        val askedIds = CopyOnWriteArrayList<Collection<String>>()
        val judged = CopyOnWriteArrayList<List<String>>()
        val scanner = object : OddsScanner {
            override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit) = report(result("a", "b", fetched = System.currentTimeMillis() - 600_000L))
            override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit) = RecheckReport(null, 0, 0, null)
            override suspend fun requoteListed(settings: ScanSettings, rows: List<com.tjshea.vigilant.data.scanner.Opportunity>): List<com.tjshea.vigilant.data.scanner.Opportunity> { askedIds += rows.map { it.market.marketId }; return rows.filter { it.event.eventId == "e-a" } }
            override suspend fun reprice(settings: ScanSettings): ScanResult? = null
            override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = emptySet()
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val paper = on.copy(settings = on.settings.copy(gates = on.settings.gates.copy(cloudTrading = com.tjshea.vigilant.data.scanner.CloudTradeMode.PAPER)))
        val e = engine(scanner, { paper }, published, trade = { rows, _, _ -> judged += rows.map { it.event.eventId }; null })
        val job = launch { e.run(untilMs = System.currentTimeMillis() + 2_000) }
        delay(400)
        job.cancel()
        assertTrue("the old prices were read again", askedIds.isNotEmpty())
        assertEquals("the trader saw only the bet that is still +EV after the re-read", listOf("e-a"), judged.first())
    }

    @Test
    fun `an api that answers nothing for three scans is rested, the next scans run without it, the phone's switch is untouched and Tj is told`() = runBlocking {
        val seen = CopyOnWriteArrayList<ScanSettings>()
        val scanner = object : OddsScanner {
            override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport {
                seen += settings
                val silent = listOf(com.tjshea.vigilant.data.scanner.SourceReport("parlay", "ParlayAPI", 0, 0, 0, null), com.tjshea.vigilant.data.scanner.SourceReport("sgo", "SGO", 8, 0, 5, null))
                return report(result("a")).copy(sources = silent)
            }
            override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit) = RecheckReport(null, 0, 0, null)
            override suspend fun reprice(settings: ScanSettings): ScanResult? = null
            override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = emptySet()
        }
        val messages = CopyOnWriteArrayList<CloudMessages.Message>()
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val withParlay = on.copy(settings = on.settings.copy(useParlay = true))
        val e = engine(scanner, { withParlay }, published, messageSink = { messages += it })
        val job = launch { e.run(untilMs = System.currentTimeMillis() + 3_000) }
        delay(900)
        job.cancel()
        assertTrue("the first scans ask parlay", seen.take(3).all { it.useParlay })
        assertTrue("then the engine scans without it", seen.drop(3).isNotEmpty() && seen.drop(3).all { !it.useParlay })
        assertTrue("Tj was told it is resting", messages.any { it.title.startsWith("Resting ParlayAPI") })
        assertTrue("the status says so", published.last().second.resting.any { it.startsWith("ParlayAPI") })
        assertTrue("the phone's own switch is still on in the config", withParlay.settings.useParlay)
    }

    @Test
    fun `a balance that gave nothing is asked for again soon, not after the 30 minutes (switching to LIVE reads the wallet at once)`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val cash = java.util.concurrent.atomic.AtomicReference<Double?>(null)   // trading was not live yet: nothing to read
        val e = engine(scanner, { on }, published, walletProbe = { cash.get() }, walletEveryMs = 10 * 60_000L, walletRetryMs = 40)
        val job = launch { e.run(untilMs = System.currentTimeMillis() + 5_000) }
        delay(100)
        assertTrue("nothing read yet: shown as not read (-1), never as an empty wallet", published.last().second.walletCash == -1.0 && published.last().second.walletReadAtMs == 0L)
        cash.set(42.5)   // the phone switched to LIVE and the key can read the balance
        delay(300)
        assertEquals("read within the short retry, not the long interval", 42.5, published.last().second.walletCash, 0.0)
        assertTrue(published.last().second.walletReadAtMs > 0L)
        job.cancel()
    }

    @Test
    fun `an engine with no wallet to read (not trading live) never sleeps`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { on }, published, walletProbe = { null })
        e.run(untilMs = System.currentTimeMillis() + 120)
        assertTrue(scanner.calls.get() >= 1)
        assertTrue(published.all { it.second.walletSleep.isEmpty() })
    }

    @Test
    fun `a reset asked for after the job began ends it as RESET, and the value read at its start never loops it`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val cfg = java.util.concurrent.atomic.AtomicReference(on.copy(resetAtMs = 5_000L))
        val e = engine(scanner, { cfg.get() }, published)
        val job = GlobalScope.async { e.run(untilMs = System.currentTimeMillis() + 10_000) }
        delay(150)
        assertTrue("a reset already in the config when the job starts is handled, not obeyed", !job.isCompleted)
        cfg.set(cfg.get().copy(resetAtMs = 6_000L))
        assertEquals(CloudEngine.End.RESET, withTimeout(5_000) { job.await() })
    }

    @Test
    fun `a PropLine refresh asked for on the phone reaches the engine from the config it already reads`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val cfg = java.util.concurrent.atomic.AtomicReference(on)
        val e = engine(scanner, { cfg.get() }, published)
        val job = GlobalScope.async { e.run(untilMs = System.currentTimeMillis() + 3_000) }
        delay(150)
        assertEquals(0L, e.propLineRefreshAtMs)
        cfg.set(cfg.get().copy(propLineRefreshAtMs = 7_000L))
        delay(1_500)
        assertEquals(7_000L, e.propLineRefreshAtMs)
        job.cancel()
    }

    @Test
    fun `when Novig refuses the runner's address no scan starts on any API and the job ends as ADDRESS`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val watch = AddressWatch(System.currentTimeMillis()) { false }
        val e = engine(scanner, { on }, published, addressWatch = watch, addressBlocked = { true })
        val end = e.run(untilMs = System.currentTimeMillis() + 500)
        assertEquals(CloudEngine.End.ADDRESS, end)
        assertEquals("not one scan (so not one request to any fair-odds API)", 0, scanner.calls.get())
        assertEquals("the streak is counted", 1, watch.count)
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
    fun `settings edited several times in a row cut the scan short once, after they settle`() = runBlocking {
        var cfg = on
        val scanner = FakeScanner { _, partial, n ->
            if (n == 1) { partial(result("a")); delay(5_000); report(result("a")) } else report(result("c"))
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { cfg }, published, fetchSettleMs = 200)
        launch(kotlinx.coroutines.Dispatchers.Default) {
            delay(60); cfg = on.copy(rev = 2, settings = on.settings.copy(leagues = setOf("NBA")))
            delay(100); cfg = on.copy(rev = 3, settings = on.settings.copy(leagues = setOf("NBA", "NHL")))
        }
        e.run(untilMs = System.currentTimeMillis() + 900)
        assertTrue("scanned again once settled", scanner.calls.get() >= 2)
        assertTrue("no scan ran on the half-edited settings", scanner.settingsSeen.drop(1).all { it.leagues == setOf("NBA", "NHL") })
    }

    @Test
    fun `a new settings revision during a scan starts it again under the new settings`() = runBlocking {
        var cfg = on
        val scanner = FakeScanner { _, partial, n ->
            if (n == 1) { partial(result("a")); delay(5_000); report(result("a")) } else report(result("c"))
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { cfg }, published)
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(60); cfg = on.copy(rev = 2, settings = on.settings.copy(leagues = setOf("NBA", "NHL"))) }
        e.run(untilMs = System.currentTimeMillis() + 400)
        assertTrue("scanned again", scanner.calls.get() >= 2)
        assertEquals(setOf("NBA", "NHL"), scanner.settingsSeen.last().leagues)
        assertTrue(published.any { it.second.configRev == 2L && it.first?.rows?.map { r -> r.event.eventId } == listOf("e-c") })
    }

    @Test
    fun `a filter-only or phone-only change never restarts a scan, it filters the list at once`() = runBlocking {
        var cfg = on
        val scanner = FakeScanner { _, partial, n -> partial(result("a")); delay(300); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { cfg }, published)
        // the phone raises the minimum edge to 50% (nothing passes) and moves an auto-bet setting: no scan is cut short
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(100); cfg = on.copy(rev = 2, settings = on.settings.copy(minEvPercent = 0.5, autoBetMinEv = 0.07)) }
        e.run(untilMs = System.currentTimeMillis() + 450)
        assertEquals("the first scan ran to its end (no restart); a second began only after it", 1, published.count { it.second.passes == 1 && !it.second.scanning }.coerceAtMost(1))
        val lastFeed = published.mapNotNull { it.first }.last()
        assertTrue("the final list was filtered under the new minimum edge", lastFeed.rows.isEmpty())
        assertEquals(2L, lastFeed.configRev)
    }

    @Test
    fun `a refresh asked for mid-scan starts a fresh scan at once and is acknowledged`() = runBlocking {
        var cfg = on
        val scanner = FakeScanner { _, _, n -> if (n == 1) { delay(5_000); report(result("a")) } else { delay(60); report(result("c")) } }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { cfg }, published)
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(100); cfg = on.copy(rev = 2, refreshAtMs = 777L) }
        val t0 = System.currentTimeMillis()
        e.run(untilMs = t0 + 600)
        assertTrue("the 5 s scan was cut short", scanner.calls.get() >= 2)
        val st = published.map { it.second }
        assertTrue("acknowledged", st.any { it.ackedRefreshAtMs == 777L && it.scanning })
        val done = st.last { it.lastScanForRefreshAtMs == 777L }
        assertTrue("a scan that began after the request finished and says which request it served", done.lastScanEndedAtMs >= done.lastScanStartedAtMs && done.lastScanStartedAtMs > 0L)
        assertTrue("the list published by it", published.any { it.first?.rows?.map { r -> r.event.eventId } == listOf("e-c") })
    }

    @Test
    fun `a refresh asked for in a scan's first moments is served by that scan, not a second one`() = runBlocking {
        var cfg = on
        val scanner = FakeScanner { _, _, _ -> delay(150); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { cfg }, published)
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(5); cfg = on.copy(rev = 2, refreshAtMs = 55L) }
        e.run(untilMs = System.currentTimeMillis() + 120)
        assertEquals("one scan served it", 1, scanner.calls.get())
        assertTrue(published.any { it.second.ackedRefreshAtMs == 55L })
    }

    @Test
    fun `a switch-off cancels a scan at once`() = runBlocking {
        var c2 = on
        val s2 = FakeScanner { _, _, _ -> delay(5_000); report(result("a")) }
        val e2 = engine(s2, { c2 }, CopyOnWriteArrayList(), idleExitMs = 30)
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(60); c2 = on.copy(rev = 3, on = false) }
        val t0 = System.currentTimeMillis()
        e2.run(untilMs = t0 + 3_000)
        assertTrue("stopped well before the 5 s scan would end", System.currentTimeMillis() - t0 < 2_500)
    }

    @Test
    fun `the heartbeat carries the scan's progress and the list's measured ages`() = runBlocking {
        val scanner = object : OddsScanner by FakeScanner({ _, _, _ -> report(null) }) {
            override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport {
                onProgress(ScanProgress("Reading Novig books", 3, 12)); onPartial(result("a")); delay(80)
                return report(result("a", "b"))
            }
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        engine(scanner, { on }, published).run(untilMs = System.currentTimeMillis() + 150)
        val mid = published.map { it.second }.first { it.scanning && it.progressStep == "Reading Novig books" }
        assertEquals(3, mid.progressDone); assertEquals(12, mid.progressTotal)
        val withList = published.map { it.second }.last { it.rows > 0 && it.novigAgeMedianS >= 0 }
        assertTrue("ages come from the rows' own stamps (the test rows were read just now)", withList.novigAgeMedianS in 0..5)
    }

    @Test
    fun `a long scan with nothing to list yet still sends heartbeats`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> delay(150); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = engine(scanner, { on }, published)
        e.run(untilMs = System.currentTimeMillis() + 120)
        assertTrue("heartbeats went out while the scan had nothing to publish", published.count { it.first == null && !it.second.idle } >= 2)
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

    // ---- the bid desk's candidates (Tj, 2026-10-15) ----------------------------------------------------------------------------

    private fun bidLine(id: String): com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.BidLine {
        val r = row(id)
        return com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.BidLine(
            com.tjshea.vigilant.data.novig.trading.maker.MakerLines.lineOf(r, System.currentTimeMillis(), null), r.event.startsTs,
        )
    }

    /** What the engine did about the bid desk: the settings each candidate pass saw, and each list published (null = the file taken away). */
    private class BidLog {
        val made = CopyOnWriteArrayList<ScanSettings>()
        val published = CopyOnWriteArrayList<CloudBids?>()
    }

    private fun bidEngine(scanner: OddsScanner, cfg: () -> CloudConfig?, published: MutableList<Pair<CloudFeed?, CloudStatus>>, log: BidLog, lines: Int = 2) = CloudEngine(
        scanner = scanner, sources = { emptyList() }, config = { cfg() },
        publish = { f, s -> published += f to s; null },
        pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, idleExitMs = 600_000L, heartbeatEveryMs = 1, statusEveryMs = 1, refreshMinAgeMs = 20, fetchSettleMs = 0, restartEveryMs = 30_000L,
        bidLines = { _, s, _ -> log.made += s; (1..lines).map { bidLine("b$it") } },
        publishBids = { log.published += it },
        bidsEveryMs = 0L,
    )

    private val bidsOn = on.copy(settings = on.settings.copy(maker = true))

    @Test
    fun `with Vigilant-priced bids on, the candidate lines are made from the list and sent with it, and the status says how many`() = runBlocking {
        val scanner = FakeScanner { _, partial, _ -> partial(result("a")); delay(40); report(result("a", "b")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val log = BidLog()
        bidEngine(scanner, { bidsOn }, published, log).run(untilMs = System.currentTimeMillis() + 150)
        assertTrue("candidate lines were made", log.made.isNotEmpty())
        assertTrue("under the engine's own settings (no live games)", log.made.all { !it.includeLive && it.bidsNeedLines })
        val sent = log.published.filterNotNull()
        assertTrue(sent.isNotEmpty())
        assertEquals(2, sent.last().lines.size)
        assertEquals(1L, sent.last().configRev)
        val st = published.map { it.second }.last { it.bidsAtMs > 0 }
        assertEquals(2, st.bidsCount)
        assertTrue("the final list's candidates went out at the scan's end", sent.any { !it.partial })
    }

    @Test
    fun `with no bids on, or bids priced from CrazyNinjaOdds, no candidate line is ever made`() = runBlocking {
        // (Recommend mode is a bid mode too, and the app's default: only switching it off as well means no bids.)
        val none = on.settings.copy(maker = false, makerRecommend = false)
        for (cfg in listOf(on.copy(settings = none), on.copy(settings = on.settings.copy(maker = true, makerSource = com.tjshea.vigilant.data.scanner.BidSource.CNO)))) {
            val scanner = FakeScanner { _, partial, _ -> partial(result("a")); delay(30); report(result("a")) }
            val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
            val log = BidLog()
            bidEngine(scanner, { cfg }, published, log).run(untilMs = System.currentTimeMillis() + 120)
            assertTrue("no candidate pass", log.made.isEmpty())
            assertTrue("no file published, nothing to take away either", log.published.isEmpty())
            assertTrue(published.all { it.second.bidsAtMs == 0L })
        }
    }

    @Test
    fun `a bid rule changed during a scan makes the candidates again from the held list with no new scan`() = runBlocking {
        var cfg = bidsOn
        val scanner = FakeScanner { _, partial, n -> if (n == 1) { partial(result("a")); delay(400); report(result("a")) } else report(result("z")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val log = BidLog()
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(120); cfg = bidsOn.copy(rev = 2, settings = bidsOn.settings.copy(makerMargin = 0.09)) }
        bidEngine(scanner, { cfg }, published, log).run(untilMs = System.currentTimeMillis() + 330)
        assertEquals("no restart for a bid rule", 1, scanner.calls.get())
        assertTrue("made under the old margin, then under the new one", log.made.any { it.makerMargin != 0.09 } && log.made.any { it.makerMargin == 0.09 })
        assertEquals(2L, log.published.filterNotNull().last().configRev)
    }

    @Test
    fun `bids switched off mid-scan take the candidate file away, once`() = runBlocking {
        var cfg = bidsOn
        val scanner = FakeScanner { _, partial, _ -> partial(result("a")); delay(400); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val log = BidLog()
        launch(kotlinx.coroutines.Dispatchers.Default) { delay(120); cfg = bidsOn.copy(rev = 2, settings = bidsOn.settings.copy(maker = false, makerRecommend = false)) }
        bidEngine(scanner, { cfg }, published, log).run(untilMs = System.currentTimeMillis() + 330)
        assertEquals("the list was sent while they were on, then taken away once", 1, log.published.count { it == null })
        assertTrue(log.published.last() == null)
        val st = published.map { it.second }.last()
        assertEquals(0L, st.bidsAtMs)
    }

    @Test
    fun `a failing candidate pass never costs the Feed its list`() = runBlocking {
        val scanner = FakeScanner { _, partial, _ -> partial(result("a")); delay(30); report(result("a", "b")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = CloudEngine(
            scanner = scanner, sources = { emptyList() }, config = { bidsOn },
            publish = { f, s -> published += f to s; null },
            pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, idleExitMs = 600_000L, heartbeatEveryMs = 1, statusEveryMs = 1, refreshMinAgeMs = 20, fetchSettleMs = 0,
            bidLines = { _, _, _ -> throw IllegalStateException("boom") }, publishBids = {}, bidsEveryMs = 0L,
        )
        e.run(untilMs = System.currentTimeMillis() + 150)
        assertTrue(published.any { it.first?.rows?.size == 2 })
        assertTrue(published.all { it.second.bidsAtMs == 0L })
    }

    // ---- the engine's Novig route and ranking, said in the status (TCL13) --------------------------------------------------------

    @Test
    fun `each scan prepares the engine's connections under the settings it reads, and the status carries how Novig is read and the API ranking`() = runBlocking {
        val prepared = CopyOnWriteArrayList<ScanSettings>()
        val scanner = object : OddsScanner {
            override suspend fun scan(settings: ScanSettings, sources: List<ReferenceSource>, pinned: Set<String>, onProgress: (ScanProgress) -> Unit, onPartial: (ScanResult) -> Unit): ScanReport = report(result("a"))
            override suspend fun recheck(settings: ScanSettings, marketIds: Collection<String>, onProgress: (Int, Int) -> Unit) = RecheckReport(null, 0, 0, null)
            override suspend fun reprice(settings: ScanSettings): ScanResult? = null
            override suspend fun unscannedLeagues(settings: ScanSettings): Set<String> = emptySet()
            override fun sourceRanking(): String? = "SGO (p90 20s) > ParlayAPI (p90 2m)"
        }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = CloudEngine(
            scanner = scanner, sources = { emptyList() }, config = { on }, publish = { f, s -> published += f to s; null },
            pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, idleExitMs = 600_000L, heartbeatEveryMs = 1, statusEveryMs = 1, refreshMinAgeMs = 20, fetchSettleMs = 0,
            prepare = { prepared += it }, novigRoute = { "Novig read-only key active: 14 reads a second, websocket on" },
        )
        e.run(untilMs = System.currentTimeMillis() + 120)
        assertTrue("prepared before a scan, under the engine's own settings", prepared.isNotEmpty() && prepared.all { !it.includeLive })
        val st = published.map { it.second }.last()
        assertEquals("Novig read-only key active: 14 reads a second, websocket on", st.novigRoute)
        assertEquals("SGO (p90 20s) > ParlayAPI (p90 2m)", st.ranking)
    }

    @Test
    fun `a connection that cannot be prepared never stops the scan`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a", "b")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = CloudEngine(
            scanner = scanner, sources = { emptyList() }, config = { on }, publish = { f, s -> published += f to s; null },
            pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, idleExitMs = 600_000L, heartbeatEveryMs = 1, statusEveryMs = 1, refreshMinAgeMs = 20, fetchSettleMs = 0,
            prepare = { throw IllegalStateException("socket") }, novigRoute = { throw IllegalStateException("route") },
        )
        e.run(untilMs = System.currentTimeMillis() + 120)
        assertTrue(published.any { it.first?.rows?.size == 2 })
        assertTrue(published.all { it.second.novigRoute == "" })
    }

    /** [h] may run past 23 into the next morning (24 = 00:xx tomorrow): the window now closes at 01:00. */
    private fun etMs(h: Int, m: Int = 0) = java.time.ZonedDateTime.of(2026, 10, 15, 0, m, 0, 0, EngineHours.ZONE).plusHours(h.toLong()).toInstant().toEpochMilli()

    @Test
    fun `outside 08-01 Eastern the engine scans nothing and ends with WINDOW, so the workflow starts no hand-over`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val hoursOn = on.copy(settings = on.settings.copy(gates = on.settings.gates.copy(cloudHours = true)))
        val night = etMs(3)
        val e = engine(scanner, { hoursOn }, published, clock = { night })
        assertEquals(CloudEngine.End.WINDOW, e.run(untilMs = night + 600_000L))
        assertEquals(0, scanner.calls.get())
        assertTrue(published.last().second.idle)
    }

    @Test
    fun `a manual run keeps the engine scanning at night, and a manual stop ends a day-time run with WINDOW`() = runBlocking {
        val hoursOn = on.copy(settings = on.settings.copy(gates = on.settings.gates.copy(cloudHours = true)))
        val night = etMs(3)
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val running = hoursOn.copy(manualRunUntilMs = night + 3_600_000L)
        val tick = java.util.concurrent.atomic.AtomicLong(night)
        val e = engine(scanner, { running }, CopyOnWriteArrayList(), clock = { tick.addAndGet(700L) })
        assertEquals(CloudEngine.End.TIME_UP, e.run(untilMs = night + 20_000L))
        assertTrue("it scanned although the hours are closed", scanner.calls.get() > 0)
        val noon = etMs(12)
        val stopped = hoursOn.copy(manualStopUntilMs = etMs(32))
        val scanner2 = FakeScanner { _, _, _ -> report(result("a")) }
        val e2 = engine(scanner2, { stopped }, CopyOnWriteArrayList(), clock = { noon })
        assertEquals(CloudEngine.End.WINDOW, e2.run(untilMs = noon + 600_000L))
        assertEquals(0, scanner2.calls.get())
    }

    @Test
    fun `a scan running when 01-00 Eastern arrives is cut and the job ends with WINDOW`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> delay(30_000); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val hoursOn = on.copy(settings = on.settings.copy(gates = on.settings.gates.copy(cloudHours = true)))
        val now = java.util.concurrent.atomic.AtomicLong(etMs(24, 59))
        val e = engine(scanner, { hoursOn }, published, clock = { now.get() })
        val flip = launch { delay(100); now.set(etMs(25, 0)) }
        val t0 = System.currentTimeMillis()
        assertEquals(CloudEngine.End.WINDOW, e.run(untilMs = now.get() + 3_600_000L))
        flip.cancel()
        assertTrue("it did not wait for the 30 s scan", System.currentTimeMillis() - t0 < 10_000L)
    }

    @Test
    fun `when Novig refuses the runner's address the job ends early so the workflow draws another runner, and says so`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> delay(30_000); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        // the job began 5 minutes ago and Novig has been refusing its address
        val watch = AddressWatch(startedAtMs = System.currentTimeMillis() - 5 * 60_000L) { true }
        val e = engine(scanner, { on }, published, addressWatch = watch)
        val t0 = System.currentTimeMillis()
        val end = e.run(untilMs = System.currentTimeMillis() + 60_000L, startedAtMs = System.currentTimeMillis() - 5 * 60_000L)
        assertEquals(CloudEngine.End.ADDRESS, end)
        assertTrue("it did not wait for the 30 s scan", System.currentTimeMillis() - t0 < 10_000L)
        assertEquals("the last status carries the streak for the next job", 1, published.last().second.addressHandovers)
        assertTrue(published.last().second.addressHandoverAtMs > 0L)
    }

    @Test
    fun `with no address trouble the engine never ends early`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> delay(20); report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val watch = AddressWatch(startedAtMs = System.currentTimeMillis() - 5 * 60_000L) { false }
        val end = engine(scanner, { on }, published, addressWatch = watch).run(untilMs = System.currentTimeMillis() + 120)
        assertEquals(CloudEngine.End.TIME_UP, end)
        assertEquals(0, published.last().second.addressHandovers)
    }

    @Test
    fun `the engine hands each published list to its trading with the phone's latest settings and says what it did in the heartbeat`() = runBlocking {
        val scanner = FakeScanner { _, partial, _ -> partial(result("a")); delay(40); report(result("a", "b")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val seen = CopyOnWriteArrayList<Pair<Int, com.tjshea.vigilant.data.scanner.CloudTradeMode>>()
        val cfg = on.copy(settings = on.settings.copy(gates = on.settings.gates.copy(cloudTrading = com.tjshea.vigilant.data.scanner.CloudTradeMode.PAPER)))
        val e = CloudEngine(
            scanner = scanner, sources = { emptyList() }, config = { cfg }, publish = { f, st -> published += f to st; null },
            pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, heartbeatEveryMs = 1, statusEveryMs = 1,
            trade = { rows, s, now -> seen += rows.size to s.gates.cloudTrading; CloudEngine.TradeStatus("paper", "paper: looked ${rows.size}", "", now) },
        )
        e.run(untilMs = System.currentTimeMillis() + 150)
        assertTrue("the partial list reached the trading", seen.any { it.first == 1 })
        assertTrue("the final list reached the trading", seen.any { it.first == 2 })
        assertTrue(seen.all { it.second == com.tjshea.vigilant.data.scanner.CloudTradeMode.PAPER })
        assertTrue(published.any { it.second.tradeMode == "paper" && it.second.tradeNote.startsWith("paper: looked") })
    }

    @Test
    fun `a trading pass that fails never stops the scan`() = runBlocking {
        val scanner = FakeScanner { _, _, _ -> report(result("a")) }
        val published = CopyOnWriteArrayList<Pair<CloudFeed?, CloudStatus>>()
        val e = CloudEngine(
            scanner = scanner, sources = { emptyList() }, config = { on }, publish = { f, st -> published += f to st; null },
            pause = { delay(3) }, partialEveryMs = 5, configEveryMs = 5, heartbeatEveryMs = 1, statusEveryMs = 1,
            trade = { _, _, _ -> error("boom") },
        )
        e.run(untilMs = System.currentTimeMillis() + 100)
        assertTrue(published.mapNotNull { it.first }.any { it.rows.size == 1 })
    }

    @Test fun `the engine reads BoltOdds by socket even when the phone's socket switch is off`() {
        val phone = ScanSettings(gates = ScanSettings().gates.copy(boltSocket = false))
        assertFalse(phone.gates.boltSocket)
        assertTrue(CloudEngine.forCloud(phone).gates.boltSocket)
    }

    @Test fun `the engine takes the bet slider's kinds from its level, totals included`() {
        val phone = ScanSettings(autoBetKinds = setOf(com.tjshea.vigilant.data.scanner.BetKind.PROP), vigGuards = ScanSettings().vigGuards.copy(betLevel = 61))
        val kinds = CloudEngine.forCloud(phone).autoBetKinds
        assertTrue(com.tjshea.vigilant.data.scanner.BetKind.TOTAL in kinds && com.tjshea.vigilant.data.scanner.BetKind.TEAM_TOTAL in kinds && com.tjshea.vigilant.data.scanner.BetKind.PROP in kinds)
        assertEquals(phone.autoBetKinds, CloudEngine.forCloud(phone.copy(vigGuards = phone.vigGuards.copy(betLevel = 0))).autoBetKinds)
    }
}
