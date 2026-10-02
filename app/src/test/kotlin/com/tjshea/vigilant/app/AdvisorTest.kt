package com.tjshea.vigilant.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.Advisor.Finding
import com.tjshea.vigilant.data.diag.Event
import com.tjshea.vigilant.data.diag.HostStat
import com.tjshea.vigilant.data.diag.Level
import com.tjshea.vigilant.data.diag.LogcatTail
import com.tjshea.vigilant.data.diag.NetBook
import com.tjshea.vigilant.data.diag.PathStat
import com.tjshea.vigilant.data.diag.Problem
import com.tjshea.vigilant.data.diag.SampleSummary
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-02: "signal to Claude what to optimize, what bugs or failures there are to fix, how to make features smarter or faster or better coded". Each rule on data that
 * should trigger it (and data that shouldn't), the ranking, the keys the next report compares by, and that the code each finding points at exists.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdvisorTest {

    private val now = SampleScan.NOW
    private val base = Diagnostics.Extras("0.43.0", 78, "Motorola moto g 2026 · Android 16 (API 36)")
    private fun state(f: (ScanSettings) -> ScanSettings = { it }) = SampleScan.state().copy(settings = f(ScanSettings(autoScan = AutoScanMode.CNO, autoScanSeconds = 5)))
    private fun findings(x: Diagnostics.Extras = base, s: UiState = state()) = Advisor.findings(s, x, now)
    private fun byKey(x: Diagnostics.Extras, s: UiState = state()) = findings(x, s).associateBy { it.key }

    private fun host(
        calls: Long = 100, errors: Long = 0, kinds: Map<String, Long> = emptyMap(), status: Map<String, Long> = mapOf("200" to calls - errors), recentMs: List<Int> = List(40) { 300 },
        recentBps: List<Int> = emptyList(), paths: Map<String, PathStat> = emptyMap(), limits: Long = 0, byNet: Map<String, Long> = emptyMap(), lastError: String? = null,
    ) = HostStat(
        calls = calls, errors = errors, kinds = kinds, status = status, recentMs = recentMs, recentBps = recentBps, paths = paths, limits = limits, byNet = byNet,
        lastError = lastError, lastErrorAtMs = if (errors > 0) now - 60_000 else null, lastLimit = if (limits > 0) "Retry-After: 30" else null, lastLimitAtMs = if (limits > 0) now - 120_000 else null,
    )

    private fun net(vararg hosts: Pair<String, HostStat>) = NetBook(hosts.toMap(), now - 86_400_000L)

    private fun event(level: Level, msg: String, where: String? = null, n: Int = 1, cat: String = "CYCLE", at: Long = now - 600_000) = Event(at, cat, level, msg, where, null, n, at)

    // ---- the app threw ---------------------------------------------------------------------------------------------------

    @Test
    fun `a saved crash becomes a BUG at the first frame of the app's own code, grouped, with its count`() {
        val stack = "at=1 thread=main heap=100/256MB\njava.lang.IllegalStateException: boom in cycle\n\tat com.tjshea.vigilant.app.AutoScanner.cycle(AutoScan.kt:231)\n\tat kotlinx.coroutines.X.run(X.kt:1)"
        val x = base.copy(problems = listOf(Problem("App crash", stack, now - 3_600_000L, now - 600_000L, count = 3), Problem("CrazyNinjaOdds", "CNO read failed", now - 100, now - 100)))
        val f = byKey(x).getValue("bug:crash:AutoScanner.cycle(AutoScan.kt:231)")
        assertEquals("BUG", f.kind)
        assertTrue(f.title, f.title.startsWith("The app crashed 3 times at AutoScanner.cycle(AutoScan.kt:231)"))
        assertTrue(f.evidence, f.evidence.contains("java.lang.IllegalStateException: boom in cycle"))
        assertTrue(f.action, f.action.contains("test"))
        // A crash with no app frame is grouped by its first line.
        val other = base.copy(problems = listOf(Problem("App crash", "at=1 thread=main\njava.lang.OutOfMemoryError: Failed to allocate", now - 10, now - 10)))
        assertTrue(byKey(other).keys.any { it.startsWith("bug:crash:java.lang.OutOfMemoryError") })
    }

    @Test
    fun `Android ending the app is a BUG unless a saved crash already says it, and an old exit isn't`() {
        val freeze = AppExits.Exit(now - 3_600_000L, "not responding", "executing service", foreground = false, pssMb = 200, trace = listOf("at com.tjshea.vigilant.app.MainViewModel.build(MainViewModel.kt:1500)"))
        val f = byKey(base.copy(exits = listOf(freeze))).getValue("bug:exit:not responding")
        assertEquals("BUG", f.kind)
        assertTrue(f.evidence, f.evidence.contains("main thread at at com.tjshea.vigilant.app.MainViewModel.build"))
        assertTrue(f.action, f.action.contains("main thread"))
        // The same minute as a saved crash: the crash finding already says it.
        val crash = Problem("App crash", "at=1\njava.lang.RuntimeException: x\n\tat com.tjshea.vigilant.app.Foo.bar(Foo.kt:9)", now - 3_600_000L, now - 3_600_000L)
        assertFalse(byKey(base.copy(exits = listOf(freeze.copy(reason = "crash")), problems = listOf(crash))).containsKey("bug:exit:crash"))
        // A week old: not this report's business.
        assertTrue(findings(base.copy(exits = listOf(freeze.copy(atMs = now - 8 * 86_400_000L)))).none { it.key.startsWith("bug:exit") })
    }

    @Test
    fun `an error the code reported with its place is a BUG at that place, and a repeating warning is a FAILURE`() {
        val where = "AutoBettor.sharpReason(AutoBettor.kt:212) < AutoBettor.run(AutoBettor.kt:150)"
        val x = base.copy(
            events = listOf(
                event(Level.ERROR, "sharp check failed (IllegalStateException: x)", where), event(Level.ERROR, "sharp check failed (IllegalStateException: x)", where, n = 4, at = now - 300_000),
                event(Level.WARN, "CrazyNinjaOdds asked the app to wait 600 s", cat = "CNO", n = 41, at = now - 7_200_000),
                event(Level.WARN, "one-off", n = 2), event(Level.ERROR, "old error", "Old.thing(Old.kt:1)", at = now - 5 * 86_400_000L),
            ),
        )
        val all = byKey(x)
        val bug = all.getValue("bug:error:AutoBettor.sharpReason(AutoBettor.kt:212)")
        assertEquals("BUG", bug.kind)
        assertTrue(bug.title, bug.title.startsWith("5 caught errors at AutoBettor.sharpReason(AutoBettor.kt:212)"))
        assertTrue(bug.evidence, bug.evidence.contains("(AutoBettor.sharpReason(AutoBettor.kt:212) < AutoBettor.run(AutoBettor.kt:150))"))
        val repeat = all.values.single { it.key.startsWith("repeat:CNO") }
        assertEquals("FAILURE", repeat.kind)
        assertTrue(repeat.title, repeat.title.contains("×41"))
        assertFalse(all.keys.any { it.contains("one-off") || it.contains("Old.thing") })
    }

    // ---- the health checks -----------------------------------------------------------------------------------------------

    @Test
    fun `a failing health check is a FAILURE and a warning a WATCH, each with the check's own evidence and where to look`() {
        val x = base.copy(phone = Diagnostics.Phone(notifications = false, online = false))
        val all = findings(x)
        val fail = all.first { it.title.contains("no internet connection") }
        assertEquals("FAILURE", fail.kind)
        val watch = all.first { it.title.contains("notifications are off") || it.kind == "WATCH" }
        assertTrue(all.any { it.kind == "WATCH" })
        assertTrue(fail.key.startsWith("health:Phone:"))
        assertTrue(all.none { it.kind == "FAILURE" && it.title.startsWith("OK") })
        assertTrue(watch.title.isNotBlank())
    }

    // ---- connections -----------------------------------------------------------------------------------------------------

    @Test
    fun `a host that fails a tenth of its calls is a FAILURE that says how and where, with advice for the kind of failure`() {
        val h = host(calls = 200, errors = 40, kinds = mapOf("timeout" to 30L), status = mapOf("200" to 160L, "500" to 10L), paths = mapOf("/site/browse/game.aspx" to PathStat(50, 35, 900_000, null), "/ok" to PathStat(100, 0, 1, 200)), lastError = "SocketTimeoutException: timeout", byNet = mapOf("mobile" to 150L, "Wi-Fi" to 50L))
        val f = byKey(base.copy(net = net("crazyninjaodds.com" to h))).getValue("net:crazyninjaodds.com:errors")
        assertEquals("FAILURE", f.kind)
        assertTrue(f.title, f.title == "crazyninjaodds.com: 20% of calls failed (40 of 200)")
        assertTrue(f.evidence, f.evidence.contains("30 timeout") && f.evidence.contains("10× HTTP 500") && f.evidence.contains("worst endpoint /site/browse/game.aspx (35/50)"))
        assertTrue(f.code, f.code.contains("CnoClient.kt"))
        assertTrue(f.action, f.action.startsWith("Mostly timeouts"))
        // Few calls, or a low rate: no finding.
        assertTrue(findings(base.copy(net = net("h" to host(calls = 10, errors = 9)))).none { it.key.startsWith("net:h:") })
        assertTrue(findings(base.copy(net = net("h" to host(calls = 200, errors = 10)))).none { it.key == "net:h:errors" })
        // Refusals get the pacing advice.
        val limited = byKey(base.copy(net = net("parlay-api.com" to host(calls = 50, errors = 10, status = mapOf("200" to 40L, "429" to 10L), limits = 10)))).getValue("net:parlay-api.com:errors")
        assertTrue(limited.action, limited.action.startsWith("Refused with 429/403"))
    }

    @Test
    fun `a slow host is an OPTIMIZE naming its slowest endpoint, rate limits and slow bodies are too, and none of it suggests saving data`() {
        val slow = host(recentMs = List(30) { if (it < 20) 800 else 6_000 }, paths = mapOf("/v1/sports/{id}/odds" to PathStat(30, 0, 120_000, 200), "/fast" to PathStat(30, 0, 3_000, 200)), byNet = mapOf("mobile" to 60L), limits = 5, recentBps = List(10) { 20_000 })
        val all = byKey(base.copy(net = net("parlay-api.com" to slow)))
        val s = all.getValue("net:parlay-api.com:slow")
        assertEquals("OPTIMIZE", s.kind)
        assertTrue(s.title, s.title.contains("half of calls take 800 ms") && s.title.contains("1 in 20 over 6000 ms"))
        assertTrue(s.evidence, s.evidence.contains("slowest endpoint /v1/sports/{id}/odds (4000 ms average)") && s.evidence.contains("mobile 60"))
        assertEquals("OPTIMIZE", all.getValue("net:parlay-api.com:limits").kind)
        assertTrue(all.getValue("net:parlay-api.com:limits").evidence.contains("Retry-After: 30"))
        assertEquals("OPTIMIZE", all.getValue("net:parlay-api.com:throughput").kind)
        // Standing rule (CLAUDE.md): never advise saving mobile data or storage.
        for (f in all.values) assertFalse(f.text(1), Regex("(?i)save (mobile )?data|reduce (data|storage)|smaller download").containsMatchIn(f.text(1)))
        // Too few samples: no verdict.
        assertTrue(findings(base.copy(net = net("h" to host(recentMs = List(5) { 9_000 })))).none { it.key == "net:h:slow" })
    }

    // ---- timings ---------------------------------------------------------------------------------------------------------

    @Test
    fun `cycles that overrun their interval, a slow scan, a slow start and a full heap are OPTIMIZE, and a quiet app has none`() {
        val perf = mapOf("cycle.ms" to SampleSummary(50, 4_000.0, 25_000.0, 40_000.0, 6_000.0), "scan.ms" to SampleSummary(3, 90_000.0, 250_000.0, 250_000.0, 120_000.0))
        val x = base.copy(perf = perf, coldStartMs = 3_100, memory = Diagnostics.Memory(210, 256, listOf("Scan result: 900 priced sides")))
        val all = byKey(x)
        assertEquals("OPTIMIZE", all.getValue("perf:cycle").kind)
        assertTrue(all.getValue("perf:cycle").title, all.getValue("perf:cycle").title.contains("typical 4 s, 1 in 20 over 25 s"))
        assertTrue(all.getValue("perf:cycle").code.contains("AutoScan.kt"))
        // Which step to look at: the one with the worst 1-in-20, with its typical time; none yet when the steps are too few.
        assertTrue(all.getValue("perf:cycle").evidence, all.getValue("perf:cycle").evidence.contains("per-step timings: none yet"))
        val steps = perf + mapOf("cycle.step.cno" to SampleSummary(50, 800.0, 2_000.0, 3_000.0, 900.0), "cycle.step.autobet" to SampleSummary(50, 900.0, 21_000.0, 30_000.0, 4_000.0), "cycle.step.alerts" to SampleSummary(3, 1.0, 99_000.0, 99_000.0, 1.0))
        val ev = byKey(x.copy(perf = steps)).getValue("perf:cycle").evidence
        assertTrue(ev, ev.contains("slowest step: autobet (1 in 20 over 21000 ms; typical 900 ms)"))
        assertTrue(all.getValue("perf:scan").title.contains("250 s"))
        assertTrue(all.getValue("perf:coldstart").title.contains("3100 ms"))
        assertTrue(all.getValue("perf:heap").title.contains("82%"))
        // Cycles fine for a slow interval, auto-scan off, or too few of them: no cycle finding.
        val quiet = base.copy(perf = mapOf("cycle.ms" to SampleSummary(50, 4_000.0, 25_000.0, 40_000.0, 6_000.0)))
        assertTrue(findings(quiet, state { it.copy(autoScanSeconds = 1200) }).none { it.key == "perf:cycle" })
        assertTrue(findings(quiet, state { it.copy(autoScan = AutoScanMode.OFF) }).none { it.key == "perf:cycle" })
        assertTrue(findings(base.copy(perf = mapOf("cycle.ms" to SampleSummary(3, 4_000.0, 90_000.0, 90_000.0, 6_000.0)))).none { it.key == "perf:cycle" })
        assertTrue(findings(base).none { it.key.startsWith("perf:") })
    }

    // ---- what the app decided --------------------------------------------------------------------------------------------

    @Test
    fun `when most skips share one reason it says so and points at the code that owns the reason, and the sharp funnel names its failures`() {
        val c = mapOf(
            "autobet.looked" to 300L, "autobet.passed" to 10L, "autobet.placed" to 4L,
            "autobet.skip.no Novig price read in the last minute" to 160L, "autobet.skip.tried a moment ago" to 40L, "autobet.skip.not pregame" to 10L,
            "sharp.autobet.UNAVAILABLE" to 6L, "sharp.autobet.CONFIRMED" to 4L, "sharp.alert.STALE" to 5L,
        )
        val all = byKey(base.copy(counters = c, eventsSinceMs = now - 86_400_000L))
        val top = all.getValue("funnel:autobet:top-skip")
        assertEquals("IMPROVE", top.kind)
        assertTrue(top.title, top.title.contains("76% of the bets it skipped") && top.title.contains("no Novig price read in the last minute"))
        assertTrue(top.code, top.code.contains("NovigLive.kt"))
        assertTrue(top.action, top.action.contains("Never loosen a safety limit"))
        assertEquals("FAILURE", all.getValue("funnel:sharp:unavailable").kind)
        assertEquals("OPTIMIZE", all.getValue("funnel:sharp:stale").kind)
        // No dominant reason: no finding; too few skips: none.
        assertTrue(findings(base.copy(counters = mapOf("autobet.skip.a" to 30L, "autobet.skip.b" to 30L, "autobet.skip.c" to 30L))).none { it.key.startsWith("funnel:autobet") })
        assertTrue(findings(base.copy(counters = mapOf("autobet.skip.a" to 19L))).none { it.key.startsWith("funnel:autobet") })
    }

    @Test
    fun `dropped frames and errors in the app's own log, and a file that has grown too big, are found`() {
        fun line(level: Char, tag: String, text: String) = LogcatTail.Line("10-02 01:00:00.000", level, tag, text)
        val x = base.copy(
            logcat = listOf(line('W', "Choreographer", "Skipped 47 frames!  The application may be doing too much work"), line('W', "Choreographer", "Skipped 31 frames!"), line('E', "OkHttp", "failed"), line('E', "OkHttp", "failed again")),
            storage = listOf("events.json" to 25L * 1_048_576, "bets.json" to 90_000L),
        )
        val all = byKey(x)
        assertEquals("OPTIMIZE", all.getValue("logcat:jank").kind)
        assertTrue(all.getValue("logcat:jank").action.contains("compose-performance"))
        assertEquals("WATCH", all.getValue("logcat:error:OkHttp").kind)
        assertTrue(all.getValue("logcat:error:OkHttp").title.contains("2 error lines from 'OkHttp'"))
        assertEquals("WATCH", all.getValue("storage:events.json").kind)
        assertFalse(all.containsKey("storage:bets.json"))
    }

    // ---- order, keys, numbers ---------------------------------------------------------------------------------------------

    @Test
    fun `findings are ranked BUG then FAILURE then OPTIMIZE then IMPROVE then WATCH, the bigger first within a kind, and keys never repeat`() {
        val x = base.copy(
            problems = listOf(Problem("App crash", "at=1\nboom\n\tat com.tjshea.vigilant.app.Foo.bar(Foo.kt:9)", now, now)),
            net = net("a.com" to host(calls = 100, errors = 50, kinds = mapOf("timeout" to 50L)), "b.com" to host(calls = 100, errors = 20, kinds = mapOf("dns" to 20L)), "c.com" to host(recentMs = List(30) { 5_000 })),
            phone = Diagnostics.Phone(notifications = false),
        )
        val all = findings(x)
        val kinds = all.map { it.kind }
        assertEquals(kinds.sortedBy { com.tjshea.vigilant.data.diag.Trend.KINDS.indexOf(it) }, kinds)
        assertEquals("BUG", kinds.first())
        val failures = all.filter { it.key.endsWith(":errors") }
        assertEquals(listOf("net:a.com:errors", "net:b.com:errors"), failures.map { it.key })
        assertEquals(all.size, all.map { it.key }.toSet().size)
        // The text carries everything a reader needs.
        val text = all.first().text(1)
        assertTrue(text, text.startsWith("[BUG] #1 ") && text.contains("\n    key: bug:crash:") && text.contains("\n    evidence: ") && text.contains("\n    do: "))
    }

    @Test
    fun `the numbers for the next report's comparison, and a snapshot with the finding keys`() {
        val x = base.copy(
            net = net("h.com" to host(calls = 200, errors = 20, recentMs = List(30) { 1_200 }), "few.com" to host(calls = 2)),
            perf = mapOf("cycle.ms" to SampleSummary(20, 1.0, 9_000.0, 12_000.0, 2.0)), counters = mapOf("autobet.placed" to 3L, "autobet.looked" to 40L),
            events = listOf(event(Level.ERROR, "x", "A.b(A.kt:1)", n = 2)), coldStartMs = 1_800,
        )
        val m = Advisor.metrics(state(), x, now)
        assertEquals(10.0, m.getValue("net.h.com.errorRate"), 0.0)
        assertEquals(1_200.0, m.getValue("net.h.com.p95ms"), 0.0)
        assertFalse(m.containsKey("net.few.com.errorRate"))
        assertEquals(9_000.0, m.getValue("perf.cycle.p95ms"), 0.0)
        assertEquals(3.0, m.getValue("autobet.placed"), 0.0)
        assertEquals(2.0, m.getValue("events.errors24h"), 0.0)
        assertEquals(1_800.0, m.getValue("perf.coldStartMs"), 0.0)
        assertTrue(m.containsKey("health.fail") && m.containsKey("heap.pct"))
        val f = findings(x)
        val snap = Advisor.snap(state(), x, now, f)
        assertEquals(f.associate { it.key to it.kind }, snap.findings)
        assertEquals("0.43.0" to 78, snap.version to snap.code)
    }

    // ---- the edges of every rule: just under says nothing, at the limit says it -----------------------------------------------

    private fun keys(x: Diagnostics.Extras, s: UiState = state()) = findings(x, s).map { it.key }.toSet()

    @Test
    fun `rate limits, name lookups and the sharp funnel speak up exactly at their limits`() {
        assertFalse("net:h:limits" in keys(base.copy(net = net("h" to host(limits = 2)))))
        assertTrue("net:h:limits" in keys(base.copy(net = net("h" to host(limits = 3)))))
        assertFalse("net:dns" in keys(base.copy(net = net("h" to host(kinds = mapOf("dns" to 2L))))))
        assertTrue("net:dns" in keys(base.copy(net = net("h" to host(kinds = mapOf("dns" to 2L)), "i" to host(kinds = mapOf("dns" to 1L))))))
        // Ten checks: nine say nothing; at ten, a third unavailable, a third stale or half without a quote is a finding.
        fun sharp(unavailable: Long = 0, stale: Long = 0, noQuote: Long = 0, confirmed: Long) =
            base.copy(counters = mapOf("sharp.autobet.UNAVAILABLE" to unavailable, "sharp.autobet.STALE" to stale, "sharp.autobet.NO_QUOTE" to noQuote, "sharp.autobet.CONFIRMED" to confirmed))
        assertTrue(keys(sharp(unavailable = 9, confirmed = 0)).none { it.startsWith("funnel:sharp") })
        assertTrue("funnel:sharp:unavailable" in keys(sharp(unavailable = 10, confirmed = 0)))
        assertFalse("funnel:sharp:unavailable" in keys(sharp(unavailable = 2, confirmed = 8)))
        assertTrue("funnel:sharp:unavailable" in keys(sharp(unavailable = 3, confirmed = 7)))
        assertFalse("funnel:sharp:stale" in keys(sharp(stale = 2, confirmed = 8)))
        assertTrue("funnel:sharp:stale" in keys(sharp(stale = 3, confirmed = 7)))
        assertFalse("funnel:sharp:noquote" in keys(sharp(noQuote = 4, confirmed = 6)))
        assertTrue("funnel:sharp:noquote" in keys(sharp(noQuote = 5, confirmed = 5)))
    }

    @Test
    fun `the app's log: one dropped-frame line is not a finding, a warning is not an error, and a crash's own tag is left to the crash`() {
        fun line(level: Char, tag: String, text: String) = LogcatTail.Line("10-02 01:00:00.000", level, tag, text)
        assertFalse("logcat:jank" in keys(base.copy(logcat = listOf(line('W', "Choreographer", "Skipped 47 frames!")))))
        assertTrue("logcat:jank" in keys(base.copy(logcat = listOf(line('W', "Choreographer", "Skipped 47 frames!"), line('W', "OpenGLRenderer", "Davey! duration=900ms")))))
        assertTrue(keys(base.copy(logcat = listOf(line('W', "SomeTag", "just a warning"), line('W', "SomeTag", "another")))).none { it.startsWith("logcat:error") })
        assertTrue(keys(base.copy(logcat = listOf(line('E', "AndroidRuntime", "FATAL EXCEPTION: main")))).none { it.startsWith("logcat:error") })
        assertTrue("logcat:error:SomeTag" in keys(base.copy(logcat = listOf(line('E', "SomeTag", "bad")))))
    }

    @Test
    fun `a slower kind of finding outranks a bigger one of a lighter kind, and a retry loop whose messages begin alike is one finding each`() {
        // A host 30 s slow (weight 300) is still below a failing one (a FAILURE) even though its weight is bigger.
        val x = base.copy(net = net("a.com" to host(calls = 100, errors = 12, kinds = mapOf("timeout" to 12L)), "slow.com" to host(recentMs = List(30) { 30_000 })))
        val order = findings(x).map { it.key }
        assertTrue(order.toString(), order.indexOf("net:a.com:errors") < order.indexOf("net:slow.com:slow"))
        // Two repeating warnings that share their first 48 characters have their own keys (the rest of the message differs): both show, none twice.
        val common = "the same long beginning of a message, then differ: "
        val r = findings(base.copy(events = listOf(event(Level.WARN, common + "one", n = 30), event(Level.WARN, common + "two", n = 25)))).filter { it.key.startsWith("repeat:") }
        assertEquals(r.toString(), 1, r.size)
        assertTrue(r.first().title, r.first().title.contains("×30") || r.first().title.contains("×25"))
        // A call that keeps failing is in the connection findings, not also a repeating event.
        assertTrue(keys(base.copy(events = listOf(event(Level.WARN, "host/x failed: timeout", cat = "NET", n = 40)))).none { it.startsWith("repeat:") })
    }

    @Test
    fun `health findings are the checks that are not fine, and only those`() {
        val x = base.copy(phone = Diagnostics.Phone(notifications = false))
        val notFine = HealthChecks.of(state(), x, now).count { it.level != HealthChecks.Level.OK }
        assertTrue(notFine > 0)
        assertEquals(notFine, findings(x).count { it.key.startsWith("health:") })
    }

    @Test
    fun `cycles, scans and starts speak up past their limits and not under them`() {
        fun cyc(p95: Double, count: Int = 50) = base.copy(perf = mapOf("cycle.ms" to SampleSummary(count, 1_000.0, p95, p95, 2_000.0)))
        // A 5 s schedule: 20 s is the floor, so 18 s is fine and 25 s is not.
        assertFalse("perf:cycle" in keys(cyc(18_000.0)))
        assertTrue("perf:cycle" in keys(cyc(25_000.0)))
        // A 20 minute schedule: 5 minutes is well inside the interval (nothing to say), 40 minutes is longer than it.
        val slowSchedule = state { it.copy(autoScanSeconds = 1200) }
        assertFalse("perf:cycle" in keys(cyc(300_000.0), slowSchedule))
        assertTrue("perf:cycle" in keys(cyc(1_900_000.0), slowSchedule))
        assertFalse("perf:cycle" in keys(cyc(25_000.0, count = 7)))
        assertTrue("perf:cycle" in keys(cyc(25_000.0, count = 8)))
        // A scan: 3 minutes is the limit.
        assertFalse("perf:scan" in keys(base.copy(perf = mapOf("scan.ms" to SampleSummary(3, 90_000.0, 170_000.0, 170_000.0, 100_000.0)))))
        assertTrue("perf:scan" in keys(base.copy(perf = mapOf("scan.ms" to SampleSummary(3, 90_000.0, 180_000.0, 180_000.0, 100_000.0)))))
        // The first screen: 2.5 s.
        assertFalse("perf:coldstart" in keys(base.copy(coldStartMs = 2_400)))
        assertTrue("perf:coldstart" in keys(base.copy(coldStartMs = 2_600)))
    }

    @Test
    fun `a league's games unmatched to fair odds are a finding below seven in ten, and not with few games`() {
        val sample = SampleScan.state().result!!.games.first { it.refEvent != null || it.outcomes.any { o -> o.fairProbability != null } }
        fun game(i: Int, hit: Boolean) = sample.copy(event = sample.event.copy(description = "Team $i @ Other $i"), refEvent = if (hit) sample.refEvent else null, outcomes = if (hit) sample.outcomes else emptyList())
        fun withGames(hits: Int, misses: Int): UiState {
            val st = state()
            return st.copy(result = st.result!!.copy(games = (1..hits).map { game(it, true) } + (1..misses).map { game(100 + it, false) }))
        }
        val league = sample.league.displayName
        assertTrue(keys(base, withGames(7, 3)).none { it.startsWith("match:") })
        assertTrue("match:$league" in keys(base, withGames(6, 4)))
        assertTrue(keys(base, withGames(0, 4)).none { it.startsWith("match:") })
        assertTrue("match:$league" in keys(base, withGames(0, 5)))
    }

    // ---- what the findings say about the code ------------------------------------------------------------------------------

    @Test
    fun `every source file the advisor names exists in the repo`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/Advisor.kt").readText()
        val names = Regex("""[A-Z][A-Za-z]+\.kt""").findAll(src).map { it.value }.toSet() - setOf("Foo.kt")
        assertTrue(names.size >= 15)
        val root = File("..")
        val have = root.walkTopDown().onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }.filter { it.isFile && it.extension == "kt" }.map { it.name }.toSet()
        val missing = names.filter { it !in have }
        assertTrue("named in Advisor but missing: $missing", missing.isEmpty())
    }
}
