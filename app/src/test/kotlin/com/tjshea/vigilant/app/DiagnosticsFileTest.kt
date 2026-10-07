package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.diag.Event
import com.tjshea.vigilant.data.diag.HostStat
import com.tjshea.vigilant.data.diag.Level
import com.tjshea.vigilant.data.diag.LogcatTail
import com.tjshea.vigilant.data.diag.NetBook
import com.tjshea.vigilant.data.diag.PathStat
import com.tjshea.vigilant.data.diag.Problem
import com.tjshea.vigilant.data.diag.SampleSummary
import com.tjshea.vigilant.data.diag.Snap
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.TimeZone

/**
 * Tj, 2026-10-02: "output a file that I can send directly to Claude which Claude can understand and easily diagnose and improve the app". The file's order and contents, that
 * it carries a parseable block of numbers, that it stays a readable size, that every path it names exists, and that no key, token or URL query is ever in it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DiagnosticsFileTest {

    private val now = SampleScan.NOW
    private val zone = TimeZone.getTimeZone("UTC")

    /** A key-shaped string with the scanner's FAKE marker: it must never reach the file whole. */
    private val fakeKey = "pk-FAKE-SECRETSECRETSECRETSECRET1234"
    private val fakeToken = "sk-FAKE-TOKENTOKENTOKENTOKEN98765"

    private fun state() = SampleScan.state().copy(
        settings = ScanSettings(autoScan = AutoScanMode.CNO, autoScanSeconds = 5, autoBet = true),
        parlayKeys = listOf(fakeKey), oddsApiKeys = listOf(fakeToken),
    )

    private fun host(calls: Long, errors: Long) = HostStat(
        calls = calls, errors = errors, bytes = calls * 10_000, totalMs = calls * 400, status = mapOf("200" to calls - errors, "500" to errors), kinds = mapOf("timeout" to errors),
        recentMs = List(40) { 300 + it * 10 }, recentBps = List(10) { 300_000 }, paths = mapOf("/v1/sports/{id}/odds" to PathStat(calls, errors, calls * 400, 200, if (errors >= 30) mapOf("500" to errors - 10, "timeout" to 10L) else emptyMap())), byNet = mapOf("Wi-Fi" to calls),
        lastError = "SocketTimeoutException: timeout while sending $fakeKey", lastErrorAtMs = now - 60_000,
        hours = mapOf((now / 3_600_000L).toString() to com.tjshea.vigilant.data.diag.HourStat(calls.toInt(), errors.toInt(), calls * 400, calls * 10_000)),
        limits = 2, lastLimit = "Retry-After: 30", lastLimitAtMs = now - 120_000,
    )

    private fun extras(events: List<Event> = emptyList()) = Diagnostics.Extras(
        "0.43.0", 78, "Motorola moto g 2026 · Android 16 (API 36)",
        net = NetBook(mapOf("parlay-api.com" to host(120, 30), "crazyninjaodds.com" to host(400, 2)), now - 86_400_000L),
        events = events.ifEmpty {
            listOf(
                Event(now - 7_200_000L, "APP", Level.INFO, "process started: Vigilant 0.43.0"),
                Event(now - 600_000L, "NET", Level.WARN, "parlay-api.com/v1/sports/{id}/odds failed: timeout after 20000 ms"),
                Event(now - 300_000L, "CYCLE", Level.ERROR, "CNO read failed (IOException: boom with $fakeToken)", "AutoScanner.cycle(AutoScan.kt:231)"),
                Event(now - 60_000L, "AUTOBET", Level.INFO, "placed Justin Jefferson Under 69.5 \$1.00 at +117 (+3.3% EV)"),
            )
        },
        counters = mapOf("autobet.looked" to 300L, "autobet.placed" to 4L, "autobet.skip.no Novig price read in the last minute" to 200L, "sharp.autobet.CONFIRMED" to 4L, "cycle.runs" to 9000L),
        eventsSinceMs = now - 86_400_000L, perf = mapOf("cycle.ms" to SampleSummary(100, 900.0, 4_000.0, 9_000.0, 1_100.0)), coldStartMs = 1_400,
        logcat = listOf(LogcatTail.Line("10-02 01:00:00.000", 'E', "OkHttp", "failed with key …1234")),
        storage = listOf("events.json" to 120_000L, "bets.json" to 90_000L),
        problems = listOf(Problem("CrazyNinjaOdds", "Couldn't read CrazyNinjaOdds ($fakeKey)", now - 500_000L)),
        previous = Snap(now - 3 * 86_400_000L, "0.42.0", 77, mapOf("net.parlay-api.com.errorRate" to 5.0), mapOf("net:parlay-api.com:errors" to "FAILURE", "gone" to "BUG")),
        phone = Diagnostics.Phone(notifications = true, batteryPct = 64, charging = true, thermal = "none"),
    )

    private fun file(x: Diagnostics.Extras = extras()) = DiagnosticsFile.build(state(), x, now, zone)

    @Test
    fun `the sections come in the order a reader needs them`() {
        val text = file()
        val order = listOf(
            "VIGILANT DIAGNOSTICS FILE · version 0.43.0", "== READ ME FIRST (for Claude) ==", "== WHAT TO DO (ranked findings) ==", "== SINCE THE PREVIOUS REPORT ==",
            "VIGILANT DIAGNOSTICS ·", "== Health checks (worst first) ==", "== Settings ==", "== Phone ==", "== Recent problems",
            "== CONNECTIONS", "== API ISSUES", "== PERFORMANCE", "== COUNTERS", "== EVENT TIMELINE", "== APP LOG", "== STORAGE", "== EVERY BET", "== CODE MAP ==", "== MACHINE-READABLE", "== END OF FILE ==",
        )
        var at = -1
        for (h in order) {
            val i = text.indexOf(h)
            assertTrue("missing or out of order: $h", i > at)
            at = i
        }
    }

    @Test
    fun `every bet is in the file with its record as placed, and the close is split by that record`() {
        // Tj, 2026-10-02 17:01Z: "record all types of information on the bet as placed … Then include this information for all bets in the diagnosis feature."
        val at = com.tjshea.vigilant.data.tracker.AtBet(
            atMs = now - 7_200_000L, version = "0.45.0", how = com.tjshea.vigilant.data.tracker.AtBet.HOW_AUTO, scanner = "CNO", preset = "Volume + safe CLV",
            league = "NFL", sport = "FOOTBALL", kind = "PROP", minutesToStart = 95, american = 117, ev = 0.033, checkEv = 0.031, twoSided = 3, agreeing = 3,
            dissent = emptyList(), sharpVerdict = "PASSED", sharpBook = "Kalshi", books = listOf(com.tjshea.vigilant.data.tracker.AtBetBook("Kalshi", -103, -126, 0.49, 0.06)),
        )
        val base = state()
        val bets = base.bets.mapIndexed { i, b -> if (i == 0) b.copy(atBet = at) else b }
        val text = DiagnosticsFile.build(base.copy(bets = bets), extras(), now, zone)
        val section = text.substringAfter("== EVERY BET (JSON lines").substringBefore(">>>")
        val lines = section.lines().drop(2).filter { it.isNotBlank() }
        assertEquals(bets.size, lines.size)
        lines.forEach { kotlinx.serialization.json.Json.parseToJsonElement(it) }
        assertTrue(section, lines.any { it.contains("\"atBet\":{") && it.contains("\"sharpBook\":\"Kalshi\"") && it.contains("\"minutesToStart\":95") })
        // The splits by the record, with the bets from before it counted apart.
        val splits = text.substringAfter("== The bet as placed: close and results split by it (1 of ").substringBefore("== Recent problems")
        for (needed in listOf("Books agreeing every one (3 of 3)", "Sharp veto PASSED", "Sharpest book on the page Kalshi agreed", "Time to the start 30 min-2 h", "Kind of bet Player props", "Preset Volume + safe CLV", "Books agreeing not recorded")) {
            assertTrue(needed, splits.contains(needed))
        }
        // The read-me points Claude at them, and at the goal.
        assertTrue(text.contains("THE GOAL IS PROFIT: positive EV that the close confirms (CLV) and wins over time."))
    }

    @Test
    fun `the read-me says what the file is, how to work from it and the rules that bind any change`() {
        val readMe = DiagnosticsFile.readMe(extras(), now, zone).joinToString("\n")
        for (needed in listOf(
            "version 0.43.0 (code 78)", "github.com/tjshea90/novig", "WHAT TO DO", "BUG, FAILURE, OPTIMIZE, IMPROVE, WATCH", "SINCE THE PREVIOUS REPORT", "TASKS.md", "bash ship.sh",
            "Mobile data and phone storage are NOT constraints", "REAL money", "switched off by the app only after a phone restart", "never commit a key", "WHAT IS NEVER IN THIS FILE",
        )) assertTrue(needed, readMe.contains(needed, ignoreCase = true))
        assertTrue(DiagnosticsFile.PROMPT.contains("READ ME FIRST") && DiagnosticsFile.PROMPT.contains("WHAT TO DO") && DiagnosticsFile.PROMPT.contains("CLAUDE.md"))
    }

    @Test
    fun `the findings, the comparison with the last upload and the recorder's sections carry the data`() {
        val text = file()
        val needed = listOf(
            "[FAILURE] #", "net:parlay-api.com:errors", "[BUG] #", "bug:error:AutoScanner.cycle(AutoScan.kt:231)",
            // Since the last upload (3 days ago, v0.42.0): the app was updated, "gone" is resolved, the failure is still there.
            "Previous report: 3d ago, version 0.42.0 (code 77); this one is version 0.43.0 (code 78): THE APP WAS UPDATED SINCE", "RESOLVED since then (1): gone [was BUG]",
            // Connections by host with percentiles and endpoints.
            "parlay-api.com · 120 · 30 (25.0%) · 490/", "    /v1/sports/{id}/odds · 120 calls · 30 failed (HTTP 500×20, timeout×10) · 400 ms average to first byte · last HTTP 200",
            "last rate limit", "Retry-After: 30 (2 in all)",
            // API issues, performance, counters, timeline, log, storage.
            "Calls that failed before an answer, by kind: timeout 32", "cycle.ms: 100 samples · p50 900 · p95 4000 · max 9000 · mean 1100",
            "Screen appeared 1400 ms after the process started.", "Battery 64% (charging) · thermal none",
            "autobet.looked: 300", "no Novig price read in the last minute: 200", "AUTOBET  placed Justin Jefferson Under 69.5",
            "ERROR CYCLE    CNO read failed", "@ AutoScanner.cycle(AutoScan.kt:231)", "E/OkHttp: failed with key …1234", "Total 205 KB: events.json 117 KB, bets.json 87 KB",
        )
        val missing = needed.filter { !text.contains(it) }
        assertTrue("missing: $missing", missing.isEmpty())
    }

    @Test
    fun `no key, token or secret is ever in the file, whatever put it in a message`() {
        val text = file()
        for (secret in listOf(fakeKey, fakeToken, "SECRETSECRETSECRETSECRET", "TOKENTOKENTOKENTOKEN")) assertFalse("leaked $secret in: " + text.lines().filter { it.contains(secret) }.joinToString(" // ") { it.take(300) }, text.contains(secret))
        // A key is named only by its last four characters (the existing report's rule), and no URL with a query string is anywhere in the file.
        assertTrue(text.contains("…1234") || text.contains("1234"))
        assertFalse(Regex("https?://\\S*\\?\\S*=").containsMatchIn(text))
    }

    @Test
    fun `the JSON block parses and holds the version, the numbers and every finding with its key`() {
        val text = file()
        val block = text.substringAfter("<<<JSON\n").substringBefore("\n>>>")
        val obj = Json.parseToJsonElement(block).jsonObject
        assertEquals(1, obj["format"]!!.jsonPrimitive.content.toInt())
        assertEquals("0.43.0", obj["version"]!!.jsonPrimitive.content)
        assertEquals(78, obj["code"]!!.jsonPrimitive.content.toInt())
        assertTrue(obj["metrics"]!!.jsonObject.containsKey("net.parlay-api.com.errorRate"))
        val keys = obj["findings"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content }
        assertTrue(keys.toString(), "net:parlay-api.com:errors" in keys)
        assertEquals(keys.size, keys.toSet().size)
        val f = obj["findings"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("key", "kind", "title", "evidence", "code", "do"), f.keys)
    }

    @Test
    fun `the timeline shows every warning and error of the last day and the newest events, once each, oldest first`() {
        val events = (1..400).map { i ->
            Event(now - (400 - i) * 60_000L, "X", if (i % 50 == 0) Level.ERROR else Level.INFO, "event $i")
        }
        val shown = DiagnosticsFile.timelineEvents(events, now)
        assertEquals(shown.sortedBy { it.atMs }, shown)
        assertEquals(shown.size, shown.toSet().size)
        assertTrue(shown.takeLast(DiagnosticsFile.MAX_RECENT).map { it.msg } == (341..400).map { "event $it" })
        // Errors of the last day (the 50th, 100th … that fall within 24 h = 1,440 min: all 8 do).
        assertEquals(8, shown.count { it.level == Level.ERROR })
        // The newest sixty and the eight errors, two of which are among the sixty: 66 lines, not all 400.
        assertEquals(66, shown.size)
        // An error from a week ago is not "of the last day": left out when it is also not among the newest.
        val old = listOf(Event(now - 7 * 86_400_000L, "X", Level.ERROR, "ancient", lastMs = now - 7 * 86_400_000L))
        assertTrue(DiagnosticsFile.timelineEvents(old + events, now).none { it.msg == "ancient" })
        // A repeating warning counts by its last time, not its first: it began a week ago and happened a minute ago, so it is of the last day.
        val repeating = listOf(Event(now - 7 * 86_400_000L, "X", Level.WARN, "still going", n = 9000, lastMs = now - 60_000L))
        assertTrue(DiagnosticsFile.timelineEvents(repeating + events, now).any { it.msg == "still going" })
    }

    @Test
    fun `only the newest two hundred and fifty warnings of the day are listed when there are more, and the file says how many were left out`() {
        val warnings = (1..300).map { Event(now - (400 - it) * 60_000L, "W", Level.WARN, "warning $it") }
        val infos = (1..100).map { Event(now - (100 - it) * 1_000L, "I", Level.INFO, "info $it") }
        val shown = DiagnosticsFile.timelineEvents(warnings + infos, now)
        assertEquals(DiagnosticsFile.MAX_IMPORTANT + DiagnosticsFile.MAX_RECENT, shown.size)
        assertEquals("warning 51", shown.first().msg)
        assertEquals("info 100", shown.last().msg)
        // Not silent: 300 warnings of the day, 250 listed (the file used to cut at 120 while its header said "every warning").
        assertEquals(50, DiagnosticsFile.timelineLeftOut(warnings + infos, now).size)
    }

    @Test
    fun `a 451 is counted as a refusal, and every count in API ISSUES says since when`() {
        val novig = HostStat(calls = 500, errors = 0, status = mapOf("200" to 355L, "451" to 145L))
        val issues = file(extras().copy(net = NetBook(mapOf("api.novig.com" to novig), now - 86_400_000L))).substringAfter("== API ISSUES").substringBefore("== PERFORMANCE")
        assertTrue(issues, issues.contains("api.novig.com: refused 145 calls with HTTP 451"))
        assertTrue(issues, issues.contains("every count is since "))
        assertTrue(issues, issues.contains("No host has asked the app to slow down"))
    }

    @Test
    fun `more findings than fit are cut to the top twenty-five and the file says how many were left out`() {
        val hosts = (1..30).associate { "bad$it.example.com" to host(200, 60) }
        val text = file(extras().copy(net = NetBook(hosts, now - 86_400_000L)))
        val listed = text.lines().count { Regex("^\\[[A-Z]+] #\\d+ ").containsMatchIn(it) }
        assertEquals(DiagnosticsFile.MAX_FINDINGS, listed)
        val total = Regex("\\((\\d+) in all; the top 25 are listed\\)").find(text)!!.groupValues[1].toInt()
        assertTrue(total > 25)
        assertTrue(text, text.contains("… and ${total - 25} more"))
        // The JSON block has every one of them, with the evidence cut to 300 characters.
        val obj = Json.parseToJsonElement(text.substringAfter("<<<JSON\n").substringBefore("\n>>>")).jsonObject
        assertEquals(total, obj["findings"]!!.jsonArray.size)
    }

    @Test
    fun `the evidence in the JSON block is cut at three hundred characters`() {
        val longMsg = "x".repeat(10).let { "a failure with a long explanation of what went wrong ".repeat(4) }
        val frames = (1..3).joinToString(" < ") { "com.tjshea.vigilant.app.SomeVeryLongClassNameNumber$it.someVeryLongFunctionName$it(SomeVeryLongFile$it.kt:$it)" }
        val x = extras(listOf(Event(now - 60_000L, "CYCLE", Level.ERROR, longMsg, frames)))
        val full = Advisor.findings(state(), x, now).first { it.key.startsWith("bug:error:") }
        assertTrue(full.evidence.length > 300)
        val obj = Json.parseToJsonElement(file(x).substringAfter("<<<JSON\n").substringBefore("\n>>>")).jsonObject
        val shown = obj["findings"]!!.jsonArray.map { it.jsonObject }.first { it["key"]!!.jsonPrimitive.content.startsWith("bug:error:") }["evidence"]!!.jsonPrimitive.content
        assertEquals(300, shown.length)
    }

    @Test
    fun `connections list the busiest host first, five endpoints each, six hours, and the files cut to fourteen`() {
        val paths = (1..8).associate { "/p$it" to PathStat(100L - it, 0, 4_000, 200) }
        val hours = (1..10).associate { ((now / 3_600_000L) - it).toString() to com.tjshea.vigilant.data.diag.HourStat(5, 0, 100, 100) }
        val busy = host(500, 0).copy(paths = paths, hours = hours)
        val x = extras().copy(
            net = NetBook(mapOf("quiet.example.com" to host(20, 0).copy(limits = 0, lastLimit = null, lastLimitAtMs = null), "busy.example.com" to busy), now - 86_400_000L),
            storage = (1..20).map { "f$it.json" to (21_000L - it * 1_000L) },
        )
        val text = file(x)
        val conn = text.substringAfter("== CONNECTIONS").substringBefore("== API ISSUES")
        assertTrue(conn.indexOf("busy.example.com") < conn.indexOf("quiet.example.com"))
        val busyBlock = conn.substringAfter("busy.example.com ·").substringBefore("quiet.example.com ·")
        assertEquals(busyBlock, 5, busyBlock.lines().count { it.trim().startsWith("/p") })
        assertTrue(busyBlock, busyBlock.contains("/p1 ·") && !busyBlock.contains("/p6 ·"))
        assertEquals(busyBlock, 6, Regex("\\d\\d-\\d\\d \\d\\dh 5/0").findAll(busyBlock.lines().first { it.contains("last hours") }).count())
        // A host that was never rate limited has no line in the API issues.
        val issues = text.substringAfter("== API ISSUES").substringBefore("== PERFORMANCE")
        assertFalse(issues, issues.contains("quiet.example.com"))
        assertTrue(issues, issues.contains("busy.example.com: 2 rate-limit answers"))
        // Storage: fourteen files, the biggest first.
        val storage = text.substringAfter("== STORAGE").substringBefore("== CODE MAP")
        assertTrue(storage, storage.contains("f1.json") && storage.contains("f14.json") && !storage.contains("f15.json"))
        assertTrue(storage, storage.contains("Total 210 KB") || storage.contains("Total 2"))
    }

    @Test
    fun `failures are counted by the hour of the day they happened, in UTC`() {
        val hour = HostStat(calls = 10, errors = 5, hours = mapOf("37" to com.tjshea.vigilant.data.diag.HourStat(10, 5, 100, 100)))
        val issues = file(extras().copy(net = NetBook(mapOf("h.example.com" to hour), now - 86_400_000L))).substringAfter("== API ISSUES").substringBefore("== PERFORMANCE")
        assertTrue(issues, issues.contains("Failures by hour of day (UTC): 13h:5"))
    }

    @Test
    fun `the counters list shows the fifteen most common skip reasons`() {
        val skips = (1..20).associate { "autobet.skip.reason $it" to (100L - it) }
        val text = file(extras().copy(counters = skips + mapOf("autobet.looked" to 500L)))
        val block = text.substringAfter("Why auto-bet skipped bets (reason: count):").substringBefore("== EVENT TIMELINE")
        assertEquals(15, Regex("reason \\d+: ").findAll(block).count())
        assertTrue(block, block.contains("reason 1: 99") && block.contains("reason 15: 85") && !block.contains("reason 16: "))
    }

    @Test
    fun `a key in a rate-limit answer, a log line or a stack frame is masked in the file`() {
        val limited = host(50, 5).copy(lastLimit = "Retry-After: 30 $fakeToken")
        val x = extras(listOf(Event(now - 60_000L, "CYCLE", Level.ERROR, "boom", "com.tjshea.vigilant.app.A.b($fakeToken.kt:1)")))
            .copy(net = NetBook(mapOf("h.example.com" to limited), now - 86_400_000L), logcat = listOf(LogcatTail.Line("10-02 01:00:00.000", 'E', "Tag", "bad key $fakeToken")))
        val text = file(x)
        assertFalse(text.lines().filter { it.contains("TOKENTOKENTOKEN") }.joinToString(" // "), text.contains("TOKENTOKENTOKEN"))
    }

    @Test
    fun `a full recorder still makes a file of a size a chat can take`() {
        val events = (1..1_000).map { Event(now - (1_000 - it) * 1_000L, "CAT${it % 7}", if (it % 3 == 0) Level.WARN else Level.INFO, "something happened number $it with some words", n = 1) }
        val hosts = (1..20).associate { "host$it.example.com" to host(1_000L * it, 20L * it) }
        val x = extras(events).copy(net = NetBook(hosts, now - 86_400_000L), logcat = List(120) { LogcatTail.Line("10-02 01:00:00.000", 'W', "Tag$it", "a warning line with some words in it number $it") })
        val bytes = file(x).toByteArray().size
        assertTrue("$bytes bytes", bytes < 220_000)
        assertTrue(file(x).lines().count { it.contains("] #") || it.startsWith("[") } <= DiagnosticsFile.MAX_FINDINGS + 5)
    }

    @Test
    fun `every path the code map names exists in the repo`() {
        val root = File("..")
        for ((area, where) in DiagnosticsFile.CODE_MAP) {
            // "a/b/C.kt, D.kt (note); x/y/" : the first path is full; later bare names live in the same directory.
            val plain = where.replace(Regex("\\([^)]*\\)"), "")
            val parts = plain.substringBefore(";").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var dir = ""
            fun check(list: List<String>) {
                for (p in list) {
                    val full = listOf("app/", "data/", "engine/").any { p.startsWith(it) }
                    val path = if (full) p else "$dir$p"
                    if (full) dir = p.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                    assertTrue("$area: $path", File(root, path).exists())
                }
            }
            check(parts)
            check(plain.substringAfter(";", "").split(",").map { it.trim() }.filter { it.contains('/') })
        }
        assertTrue(DiagnosticsFile.CODE_MAP.size >= 12)
    }

    @Test
    fun `the file is named for the version and the minute, and is plain text`() {
        assertEquals("vigilant-diagnostics-v0.43.0-2026-09-26-0520.txt", DiagnosticsFile.fileName("0.43.0", now, zone))
    }

    @Test
    fun `a first report, or nothing found, says so plainly`() {
        val healthy = DiagnosticsFile.build(SampleScan.state().copy(settings = ScanSettings()), Diagnostics.Extras("0.43.0", 78, "dev"), now, zone)
        assertTrue(healthy, healthy.contains("This is the first report saved on this phone: nothing earlier to compare with."))
        assertTrue(healthy, healthy.contains("No events yet.") && healthy.contains("No calls recorded yet."))
    }
}
