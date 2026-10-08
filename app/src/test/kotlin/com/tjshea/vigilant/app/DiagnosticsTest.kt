package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.keys.KeyUsage
import com.tjshea.vigilant.data.keys.ProviderCost
import com.tjshea.vigilant.data.keys.ProviderUsage
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.RoundCost
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScannerMode
import com.tjshea.vigilant.data.tracker.BetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The Diagnostics page (Tj, 2026-09-29: "tell me what you need me to do or show you to optimize the app and make sure everything is working as
 * designed"): one copy-able page of the settings, the last scan, API usage, the background scan and the Tracker. Never a key.
 */
class DiagnosticsTest {

    private val now = SampleScan.NOW
    private val extras = Diagnostics.Extras("0.21.3", 49, "Motorola moto g 2026 · Android 16 (API 36)")
    private fun report(s: UiState = SampleScan.state(), x: Diagnostics.Extras = extras) = Diagnostics.report(s, x, now, TimeZone.getTimeZone("UTC"))

    @Test
    fun `the page has every section, the build and what the phone is`() {
        val text = report()
        listOf(
            "VIGILANT DIAGNOSTICS", "Version 0.21.3 (code 49) · Motorola moto g 2026 · Android 16 (API 36)", "== Settings ==", "== Last Vigilant scan ==",
            "== API usage (each provider's own allowance) ==", "== Runway (will each API's allowance last?) ==", "== Last rounds (what they cost each API) ==",
            "== CrazyNinjaOdds ==", "== Background auto-scan ==", "== Tracker ==",
        ).forEach { assertTrue("$it in:\n$text", text.contains(it)) }
        assertTrue(text, text.contains("Bets: 6 (open 3: 3 upcoming, 0 started; settled 3)"))
        assertTrue(text, text.contains("Current EV: 2 of 3 upcoming bets have one read inside the fair odds' age limit"))
    }

    /** Tj, 2026-10-07: the small-prop guard's limits and what the last day and week of auto-bets look like by kind of prop, so the next file shows whether one market carries the day. */
    @Test
    fun `it says what the small-prop guard is set to and which kinds of prop the last day's auto-bets were`() {
        val text = report()
        assertTrue(text, text.contains("Small-prop guard (RESEARCH.md §109): no kind of player prop over 25% of the last 24 h's auto-bets (once there are 8; never under an even split of the kinds being bet) · at most 3 auto-bets on one kind of prop in one game · Last 24 hours:"))
        assertTrue(text, text.contains("Last 7 days:"))
        val off = report(SampleScan.state().let { it.copy(settings = it.settings.copy(propGuardShare = 0.0, propGuardPerGame = 0)) })
        assertTrue(off, off.contains("Small-prop guard (RESEARCH.md §109): off: no cap on how much one kind of player prop may take"))
        val two = report(SampleScan.state().let { it.copy(settings = it.settings.copy(propGuardShare = 0.35, propGuardMinSample = 12, propGuardPerGame = 0)) })
        assertTrue(two, two.contains("no kind of player prop over 35% of the last 24 h's auto-bets (once there are 12; never under an even split of the kinds being bet) · Last 24 hours:"))
    }

    /** DI5 (Tj, 2026-10-07): the next file says what the CNO list was read and screened with, the games he picked included, and what was asked of CNO for them. */
    @Test
    fun `it says what CNO's list is read with, and what the games picked asked of CNO`() {
        val plain = report()
        assertTrue(plain, plain.contains("CNO list (RESEARCH.md §85): conservative devig · odds any to +150 · 4+ books · edge ≥ 1.0% · 50 rows · complete book on · games: every league, every kind"))
        assertFalse(plain, plain.contains("asked of CNO as"))
        fun scoped(scope: com.tjshea.vigilant.data.cno.CnoScope) = report(SampleScan.state().let { it.copy(settings = it.settings.copy(cnoFilters = it.settings.cnoFilters.copy(scope = scope))) })
        val one = scoped(com.tjshea.vigilant.data.cno.CnoScope(leagues = setOf("NHL"), hideLive = true))
        assertTrue(one, one.contains("games: NHL · pregame only · asked of CNO as league 4"))
        val several = scoped(com.tjshea.vigilant.data.cno.CnoScope(leagues = setOf("NHL", "NFL"), propsPerGame = 4))
        assertTrue(several, several.contains("(300 when the app has to filter)") && several.contains("asked of CNO as all leagues (the app filters)"))
    }

    @Test
    fun `it says what the trap guard is set to, and splits every bet by its time to the start, recorded as placed or not (RESEARCH 71)`() {
        // The sample keeps it off (its games are 8 h+ off); the default is 6 h with the game-line check on.
        assertTrue(report().contains("Trap guard (RESEARCH.md §71): auto-bet, alerts and bids only on games starting within any time (off)"))
        val on = report(SampleScan.state().let { it.copy(settings = it.settings.copy(trapEarlyHours = 6, trapNovigMove = true)) })
        assertTrue(on, on.contains("only on games starting within 6 h · bets first listed more than that many hours before the start skipped (auto-bet and alerts) · game lines Novig just moved skipped (the auto-bet and game-line bids read Novig's trades first) · favorites need 1.0% more edge (auto-bet)"))
        val off = report(SampleScan.state().let { it.copy(settings = it.settings.copy(trapEarlyHours = 6, trapFirstListed = false, autoBetFavouriteExtraEv = 0.0)) })
        assertTrue(off, off.contains("not skipped · game lines") && off.contains("favorites need no extra edge"))
        // The sample's bets have no record as placed: their time to the start is split all the same.
        assertTrue(on, on.lines().any { it.startsWith("Time to the start ") && !it.startsWith("Time to the start not recorded") })
    }

    @Test
    fun `it says how the Kalshi 3-requests-a-second test has gone, in the API usage section (Tj, 2026-10-07)`() {
        val note = "Kalshi pace test: PASSED, 312 requests at 3 a second and none refused: Kalshi stays at 3 a second this session"
        val text = report(x = extras.copy(kalshiPace = note))
        assertTrue(text, text.substringAfter("== API usage").contains(note))
        assertFalse(report().contains("Kalshi pace test"))
    }

    @Test
    fun `the live feed test has its own section when it recorded something, and none when it never ran`() {
        val text = report(x = extras.copy(feedRace = listOf("Running for 12 min: 3 live games on Novig (tennis).", "54 score readings from 2 feeds (poly, sofa); 210 Novig trades; 9 games")))
        assertTrue(text, text.contains("== LIVE FEED TEST (which free feed shows a score or odds move before Novig's price; no orders; RESEARCH.md §106) =="))
        assertTrue(text.contains("54 score readings from 2 feeds (poly, sofa)"))
        assertFalse(report().contains("LIVE FEED TEST"))
    }

    @Test
    fun `it says what the background scan really runs at each scanner choice`() {
        fun runs(scanner: ScannerMode, auto: AutoScanMode) = Diagnostics.runsText(SampleScan.settings.copy(scanner = scanner, autoScan = auto))
        assertEquals("CNO + Vigilant", runs(ScannerMode.BOTH, AutoScanMode.BOTH))
        assertEquals("CNO only (Vigilant skipped: the scanner is on CNO only)", runs(ScannerMode.CNO, AutoScanMode.BOTH))
        assertEquals("CNO only", runs(ScannerMode.CNO, AutoScanMode.CNO))
        assertEquals("Vigilant only (CNO skipped: the scanner is on Vigilant only)", runs(ScannerMode.VIGILANT, AutoScanMode.BOTH))
        assertEquals("nothing (the scanner choice leaves nothing to read)", runs(ScannerMode.VIGILANT, AutoScanMode.CNO))
        assertEquals("nothing", runs(ScannerMode.BOTH, AutoScanMode.OFF))
        val cnoOnly = report(SampleScan.state().let { it.copy(settings = it.settings.copy(scanner = ScannerMode.CNO, autoScan = AutoScanMode.BOTH)) })
        assertTrue(cnoOnly, cnoOnly.contains("actually runs: CNO only (Vigilant skipped: the scanner is on CNO only)"))
    }

    @Test
    fun `no key ever appears, only how many are saved and the last four characters in the usage meters`() {
        val secret = "sk_live_SUPERSECRETKEYVALUE1234"
        val base = SampleScan.state()
        val s = base.copy(
            oddsApiKeys = listOf(secret),
            usage = UsageBook(mapOf("oddsapi" to ProviderUsage(keys = mapOf(secret to KeyUsage(used = 40, remaining = 460, limit = 500)), callsToday = 40))),
        )
        val text = report(s)
        assertFalse(text, text.contains(secret))
        assertFalse(text, text.contains("SUPERSECRET"))
        assertTrue(text, text.contains("The Odds API 1"))
        assertTrue(text, text.contains("key …1234: used 40, 460 left"))
        assertTrue(text, text.contains("oddsapi: 40 calls today"))
    }

    @Test
    fun `it carries the Tracker's Check odds now counter once a check has run`() {
        val base = SampleScan.state()
        assertFalse(report(base.copy(checkStartedAtMs = null)).contains("Check odds now counter"))
        val bets = base.bets.map { if (it.id == "b3") it.copy(nowEv = 0.02, nowAtMs = now) else if (it.id == "b4") it.copy(nowEv = -0.07, nowAtMs = now) else it }
        val text = report(base.copy(bets = bets, checkStartedAtMs = now - 60_000))
        assertTrue(text, text.contains("1 +EV · 1 −EV · 50% +EV · Avg +2.0% EV (1 over ±5% left out)"))
    }

    @Test
    fun `it says what the look for closes after the start found, and what it's still waiting for`() {
        val base = SampleScan.state()
        val bets = base.bets.map { if (it.id == "b1") it.copy(closeNote = "Novig publishes this day's trades the next morning", closeLookedAtMs = now - 3_600_000L) else it }
        val x = extras.copy(backfill = com.tjshea.vigilant.data.tracker.CloseBackfill.Report(5, 3, mapOf("ESPN" to 2, "Novig's last trades" to 1)), novigTradeBytes = 2_048_000)
        val text = Diagnostics.report(base.copy(bets = bets), x, now, TimeZone.getTimeZone("UTC"))
        assertTrue(text, text.contains("Closes found after the start: last look 5 bets, found 3 (ESPN 2, Novig's last trades 1) · Novig trade data read 2000 KB"))
        assertTrue(text, text.contains("×1: Novig publishes this day's trades the next morning"))
        assertFalse(text, text.contains("ParlayAPI close calls"))
        val parlay = Diagnostics.report(base.copy(bets = bets), x.copy(parlayCloseRequests = 4), now, TimeZone.getTimeZone("UTC"))
        assertTrue(parlay, parlay.contains("Novig trade data read 2000 KB · ParlayAPI close calls 4"))
        assertTrue(report(base).contains("Closes found after the start: none looked for since the app opened"))
    }

    @Test
    fun `it says whether the management key is saved, by its last four only`() {
        val base = SampleScan.state()
        assertTrue(report(base).contains("management key not saved"))
        val saved = base.copy(novig = base.novig.copy(managementKey = com.tjshea.vigilant.data.novig.signing.ManagementKeyHint("5678", 0L)))
        assertTrue(report(saved).contains("management key saved on this phone (••••5678)"))
        val locked = base.copy(novig = base.novig.copy(managementKey = com.tjshea.vigilant.data.novig.signing.ManagementKeyHint("5678", 0L, unreadable = true)))
        assertTrue(report(locked).contains("management key saved but can't be unlocked"))
    }

    @Test
    fun `the Tracker part names what is priced, what is not and why, and what has been waiting since its game`() {
        val base = SampleScan.state()
        val noted = base.bets.map { if (it.id == "b3") it.copy(nowNote = "No fair-odds source has current prices for this game", nowNoteAtMs = now) else it }
        val stuck = base.bets.first { it.id == "b3" }.copy(
            id = "stuck", startsTs = now - 9 * 3_600_000L, status = BetStatus.PENDING, gradeNote = "Cam Talbot isn't in the box score", gradeManual = true,
            nowNote = null, nowEv = null,
        )
        val text = report(base.copy(bets = noted + stuck))
        assertTrue(text, text.contains("not priced ×1: No fair-odds source has current prices for this game"))
        assertTrue(text, text.contains("Started and still open: 1 (1 for over 6 hours, 1 need a tap)"))
        assertTrue(text, text.contains("waiting ×1: Cam Talbot isn't in the box score"))
        assertTrue(text, text.contains("Results: "))
    }

    /** Tj, 2026-09-29: "tell me which apis deplete too quickly for daily use so I can add more keys". */
    @Test
    fun `the runway names the API running short and what a scan and a Check odds now each cost it`() {
        val noon = java.time.Instant.parse("2026-09-29T12:00:00Z").toEpochMilli()
        val day = QuotaPolicy.PINNWIRE.periodStart(noon)
        val s = SampleScan.state().copy(
            pinnwireKeys = listOf("wire-key-0001"),
            // No pinnapi key behind PinnWire here (with one, pinnapi's allowance counts too: RunwayTest).
            pinnapiKeys = emptyList(),
            proplineKeys = listOf("prop-key-0002"),
            usage = UsageBook(
                mapOf(
                    "pinnwire" to ProviderUsage(keys = mapOf("wire-key-0001" to KeyUsage(periodStart = day, used = 90)), dayStart = day, callsToday = 90),
                    "propline" to ProviderUsage(keys = mapOf("prop-key-0002" to KeyUsage(periodStart = day, used = 100, remaining = 900, limit = 1000)), dayStart = day, callsToday = 60),
                ),
            ),
        )
        val x = extras.copy(
            lastScan = RoundCost(noon - 120_000, 30_000, listOf(ProviderCost("kalshi", 57, 57), ProviderCost("pinnwire", 6, 6))),
            lastCheck = RoundCost(
                noon - 30_000, 74_000, listOf(ProviderCost("kalshi", 103, 103), ProviderCost("propline", 14, 24)),
                note = "covered 106 of 109 open bets: CNO read 50, 56 priced from Vigilant's own fair odds",
            ),
        )
        val text = Diagnostics.report(s, x, noon, TimeZone.getTimeZone("UTC"))
        // 90 by noon runs out at 1:20 PM, well before midnight; PropLine's 100 is on course for 200 of 1,000.
        assertTrue(text, text.contains("Pinnacle (PinnWire): 90 of 100 requests used today (1 key), 10 left"))
        assertTrue(text, text.contains("the last of it goes in 1h 20m, before the reset: SHORT (add keys, or scan less)"))
        assertTrue(text, text.contains("PropLine: 100 of 1,000 requests used today (1 key), 900 left") && text.contains("at this pace about 200 by the reset: OK"))
        assertTrue(text, text.contains("    a scan costs 6 → 16 a day"))
        assertTrue(text, text.contains("    a Check odds now costs 24 → 41 a day"))
        assertTrue(text, text.contains("Scan: 2m ago · took 30 s"))
        assertTrue(text, text.contains("    cost: Kalshi 57, Pinnacle (PinnWire) 6"))
        assertTrue(text, text.contains("Check odds now: 30s ago · took 74 s · covered 106 of 109 open bets: CNO read 50, 56 priced from Vigilant's own fair odds"))
        assertTrue(text, text.contains("    cost: Kalshi 103, PropLine 14 (24 of its allowance)"))
        // No key is ever shown.
        assertFalse(text, text.contains("wire-key-0001"))
        assertFalse(text, text.contains("prop-key-0002"))
    }

    @Test
    fun `before any round ran it says none since the app opened`() {
        val text = report()
        assertTrue(text, text.contains("Scan: none since the app opened."))
        assertTrue(text, text.contains("Check odds now: none since the app opened."))
    }

    @Test
    fun `before any scan it says so instead of showing blanks`() {
        val text = report(UiState())
        assertTrue(text, text.contains("No scan since the app opened."))
        assertTrue(text, text.contains("Nothing counted yet."))
        assertTrue(text, text.contains("Bets: 0"))
    }

    // ---- V3 (Tj, 2026-09-30: "make the diagnostics section in settings as smart as possible so that when I output it to Claude, Claude can
    // run deep analysis on the app and know what is working or broken and how to improve the app") --------------------------------------

    @Test
    fun `health checks come first, worst first, each with its evidence and the code that owns it`() {
        val base = SampleScan.state()
        val s = base.copy(
            settings = base.settings.copy(autoScan = AutoScanMode.BOTH),
            status = base.status.copy(sources = base.status.sources + com.tjshea.vigilant.data.scanner.SourceReport("kalshi", "Kalshi", 0, 0, 0, "HTTP 401 key refused")),
        )
        val x = extras.copy(autoScanServiceRunning = false, phone = Diagnostics.Phone(notifications = false, exactAlarms = false, batteryUnrestricted = false, online = true))
        val text = report(s, x)
        assertTrue(text, text.indexOf("== Health checks (worst first) ==") in 0 until text.indexOf("== Settings =="))
        assertTrue(text, text.contains("For Claude: code at github.com/tjshea90/novig"))
        val checks = HealthChecks.of(s, x, now)
        assertEquals(checks.sortedBy { it.level.ordinal }, checks)
        fun find(area: String, level: HealthChecks.Level) = checks.firstOrNull { it.area == area && it.level == level }
        // The background scan is on but its service isn't running; alerts can't reach a phone with notifications off.
        assertTrue(text, find("Background auto-scan", HealthChecks.Level.FAIL)!!.look!!.contains("AutoScanService"))
        assertTrue(text, find("Phone", HealthChecks.Level.FAIL)!!.finding.contains("notifications are off"))
        assertTrue(text, checks.any { it.area == "Phone" && it.finding.contains("exact alarms") })
        assertTrue(text, checks.any { it.area == "Phone" && it.finding.contains("battery optimization") })
        // A source that failed names the error and where to look.
        val kalshi = find("Source Kalshi", HealthChecks.Level.FAIL)!!
        assertEquals("HTTP 401 key refused", kalshi.evidence)
        assertTrue(text, text.contains("FAIL Source Kalshi: failed in the last scan [HTTP 401 key refused] → data/reference/"))
        // The counts line says how many of each.
        assertTrue(text, Regex("\\d+ FAIL · \\d+ WARN · \\d+ OK").containsMatchIn(text))
    }

    @Test
    fun `before a scan or a check it says what to tap so the report has numbers`() {
        val base = SampleScan.fresh()
        val checks = HealthChecks.of(base, extras, now)
        assertTrue(checks.joinToString("\n") { it.text() }, checks.any { it.area == "Vigilant scan" && it.look!!.contains("tap Scan") })
    }

    @Test
    fun `edges that lose to the close are called out as not real, with the numbers`() {
        val base = SampleScan.state()
        val start = now - 2 * 3_600_000L
        // 20 bets at +3% EV when bet, each closing 2% worse than its price: CLV −2%.
        val bets = (1..20).map { i ->
            val cost = 0.50
            com.tjshea.vigilant.data.tracker.TrackedBet(
                "c$i", start - 3_600_000L, "NFL", "A @ B", start, "Moneyline", "A", "m$i", "o$i", cost, cost, 0.515, 0.03, 1.0,
                status = BetStatus.PENDING, closingFair = cost * 0.98, closingSeenAtMs = start - 5 * 60_000L,
            )
        }
        val checks = HealthChecks.of(base.copy(bets = bets), extras, now)
        val clv = checks.first { it.area == "Edge accuracy (CLV)" }
        assertEquals(HealthChecks.Level.FAIL, clv.level)
        assertTrue(clv.evidence!!, clv.evidence!!.contains("average CLV -2.0%") && clv.evidence!!.contains("EV when bet +3.0%") && clv.evidence!!.contains("20 bets"))
        // Beating the close by less than the EV claimed: overstated, a warning.
        val over = bets.map { it.copy(closingFair = it.cost * 1.005) }
        assertEquals(HealthChecks.Level.WARN, HealthChecks.of(base.copy(bets = over), extras, now).first { it.area == "Edge accuracy (CLV)" }.level)
        val real = bets.map { it.copy(closingFair = it.cost * 1.03) }
        assertEquals(HealthChecks.Level.OK, HealthChecks.of(base.copy(bets = real), extras, now).first { it.area == "Edge accuracy (CLV)" }.level)
    }

    @Test
    fun `the report splits accuracy by scanner and market, compares open bets' edge now with when bet, and lists the phone`() {
        val base = SampleScan.state()
        val bets = base.bets.map { if (it.status == BetStatus.PENDING && it.nowEv != null) it.copy(nowFair = (it.fairAtBet ?: 0.5) + 0.01, nowAtMs = now) else it }
        val text = report(base.copy(bets = bets), extras.copy(phone = Diagnostics.Phone(true, true, true, false, false, true, "Wi-Fi")))
        listOf("== Accuracy by bet or bid, scanner and market (outliers aside; CLV on n = bets with a true close) ==", "== Each scanner by market ==", "== Bets by what made their fair odds (recorded from v0.36.0) ==", "== Vigilant's own bets against the close (newest 30) ==", "== Open bets: edge now vs when bet (pregame, current reads only) ==", "== Phone ==", "== Recent problems (saved across restarts, newest first) ==")
            .forEach { assertTrue("$it in:\n$text", text.contains(it)) }
        assertTrue(text, text.contains("Scanner Vigilant:") || text.contains("Scanner CNO:"))
        assertTrue(text, text.contains("Notifications yes · exact alarms yes · battery unrestricted yes · draw over apps NO · Data Saver off · online yes (Wi-Fi)"))
        assertTrue(text, text.contains("None recorded."))
    }

    /** Tj, 2026-10-07: "so I can see stats and ev filtered my bids as well as bets, and also for the diagnostics and studies sections." */
    @Test
    fun `the report counts bets and bids apart, splits accuracy by bet or bid, and has a block for each kind`() {
        val base = SampleScan.state()
        // b1 (settled, a win) and b4 (open) are bids; b2, b3, b5 and b6 are taker bets.
        val bets = base.bets.map { if (it.id == "b1" || it.id == "b4") it.copy(maker = true, orderId = "bid-${it.id}") else it }
        val text = report(base.copy(bets = bets), extras)
        assertTrue(text, text.contains("By kind: bets 4 (open 2) · bids filled 2 (open 1)"))
        assertTrue(text, text.contains("== Bets and bids apart"))
        val apart = text.substringAfter("== Bets and bids apart").substringBefore("\n== ")
        assertTrue(apart, apart.contains("Bets (taker orders): 4 bets"))
        assertTrue(apart, apart.contains("Bids (make orders that filled): 2 bids"))
        assertTrue(apart, apart.contains("avg EV at post"))
        assertTrue(apart, apart.contains("avg EV at bet"))
        // The accuracy split has both kinds, and the as-placed split's first key is the same one.
        assertTrue(text, text.contains("Bet or bid Bids (make orders that filled): 2 bids"))
        assertTrue(text, text.contains("Bet or bid Bets (taker orders): 4 bets"))
        // With no bid filled the block says so, in plain words.
        val none = report(base, extras)
        assertTrue(none, none.contains("Bids (make orders that filled): none"))
        assertTrue(none, none.contains("By kind: bets 6 (open 3) · bids filled 0 (open 0)"))
    }

    @Test
    fun `recent problems are listed newest first with their counts, and only failures shown on screen go in`() {
        val p = listOf(
            com.tjshea.vigilant.data.diag.Problem("CrazyNinjaOdds", "Couldn't reach CrazyNinjaOdds (timeout)", now - 3_600_000L, now - 60_000L, 4),
            com.tjshea.vigilant.data.diag.Problem("Vigilant scan", "Kalshi: HTTP 503", now - 7_200_000L),
        )
        val text = report(x = extras.copy(problems = p))
        assertTrue(text, text.contains("CrazyNinjaOdds: Couldn't reach CrazyNinjaOdds (timeout) (×4 since"))
        assertTrue(text, text.indexOf("CrazyNinjaOdds: Couldn't reach") < text.indexOf("Vigilant scan: Kalshi: HTTP 503"))
        assertTrue(Diagnostics.isProblem("Couldn't save"))
        assertTrue(Diagnostics.isProblem("Check odds failed: timeout"))
        assertTrue(Diagnostics.isProblem("ParlayAPI answered HTTP 503"))
        assertFalse(Diagnostics.isProblem("Tracked: $1 on Team A"))
        assertFalse(Diagnostics.isProblem("Bet placed: $5.00 on Team A"))
    }

    @Test
    fun `open bets' edge now compares with when bet, overall and by scanner`() {
        val start = now + 3_600_000L
        fun bet(id: String, source: String, fairAtBet: Double, fairNow: Double) = com.tjshea.vigilant.data.tracker.TrackedBet(
            id, now - 3_600_000L, "NFL", "A @ B", start, "Moneyline", "A", "m$id", "o$id", 0.5, 0.5, fairAtBet, fairAtBet / 0.5 - 1, 1.0,
            status = BetStatus.PENDING, source = source, nowFair = fairNow, nowEv = fairNow / 0.5 - 1, nowAtMs = now,
        )
        val lines = Diagnostics.edgeNowLines(
            listOf(bet("a", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO, 0.52, 0.53), bet("b", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT, 0.52, 0.49)),
            now,
        )
        assertEquals("All: 2 bets · EV when bet +4.0% → now +2.0% · fair moved toward the bet on 1, away on 1 · still +EV 1", lines[0])
        assertTrue(lines.toString(), lines.any { it.startsWith("CNO: 1 bet · EV when bet +4.0% → now +6.0%") })
        assertTrue(lines.toString(), lines.any { it.startsWith("Vigilant: 1 bet ·") })
        assertEquals(listOf("No open pregame bet has a current EV (tap Check odds now, then copy Diagnostics again)."), Diagnostics.edgeNowLines(emptyList(), now))
    }

    // ---- W2/W3 (Tj's v0.35.0 report, 2026-09-30) --------------------------------------------------------------------------------------

    @Test
    fun `a busy source that answered its other leagues is a passing miss, and a backup that matched nothing is normal`() {
        val base = SampleScan.state()
        val busy = "ParlayAPI props WNBA: ParlayAPI failed for basketball_wnba props: HTTP 503 (request 5845e57c3c6286e0) {\"error\":\"props_temporarily_busy\",\"detail\":\"The props board is being rebuilt\"}"
        val s = base.copy(
            status = base.status.copy(
                errors = listOf(busy),
                sources = base.status.sources + com.tjshea.vigilant.data.scanner.SourceReport("parlay_props", "ParlayAPI props", 2, 0, 3, busy) +
                    com.tjshea.vigilant.data.scanner.SourceReport("oddsapi_props", "Sportsbook props", 3, 0, 0, null),
            ),
        )
        val checks = HealthChecks.of(s, extras, now)
        val props = checks.first { it.area == "Source ParlayAPI props" }
        assertEquals(HealthChecks.Level.WARN, props.level)
        assertEquals("ParlayAPI props WNBA: ParlayAPI failed for basketball_wnba props: HTTP 503 (props_temporarily_busy)", props.evidence)
        assertEquals(HealthChecks.Level.OK, checks.first { it.area == "Vigilant scan" }.level)
        assertEquals(HealthChecks.Level.OK, checks.first { it.area == "Source Sportsbook props" }.level)
        assertTrue(checks.none { it.level == HealthChecks.Level.FAIL })
    }

    @Test
    fun `a spent key is fine while another carries on, and a few refusals in thousands of calls are the pacing working`() {
        val base = SampleScan.state()
        val month = com.tjshea.vigilant.data.keys.QuotaPolicy.ODDS_API.periodStart(now)
        val usage = UsageBook(
            mapOf(
                "oddsapi" to ProviderUsage(
                    keys = mapOf(
                        "key-16a7" to KeyUsage(periodStart = month, used = 500, remaining = 0, depletedUntil = now + (2 * 60 + 25) * 60_000L),
                        "key-71c4" to KeyUsage(periodStart = month, used = 195, remaining = 305),
                    ),
                    callsToday = 81,
                ),
                "novig" to ProviderUsage(callsToday = 8255, throttledToday = 9, lastThrottleMs = now - 7 * 3_600_000L),
            ),
        )
        val checks = HealthChecks.of(base.copy(usage = usage), extras, now)
        val key = checks.first { it.area == "API The Odds API" }
        assertEquals(HealthChecks.Level.OK, key.level)
        assertEquals("key …16a7 is spent until its reset in 2h 25m; the other key carries on", key.finding)
        assertEquals(HealthChecks.Level.OK, checks.first { it.area == "API Novig" }.level)
        // The last key spent is a warning; so is a refusal in the last hour.
        val alone = UsageBook(mapOf("oddsapi" to ProviderUsage(keys = mapOf("key-16a7" to KeyUsage(periodStart = month, used = 500, remaining = 0, depletedUntil = now + 3_600_000L))), "novig" to ProviderUsage(callsToday = 8255, throttledToday = 9, lastThrottleMs = now - 60_000L)))
        val two = HealthChecks.of(base.copy(usage = alone), extras, now)
        assertEquals(HealthChecks.Level.WARN, two.first { it.area == "API The Odds API" }.level)
        assertEquals(HealthChecks.Level.WARN, two.first { it.area == "API Novig" }.level)
    }

    /** A bet placed [hoursBefore] h before a start 2 h ago, at 50¢, whose close was [closeRatio] times its price: CLV = closeRatio − 1; EV when bet +3%. */
    private fun leadBet(id: String, hoursBefore: Long, closeRatio: Double, source: String = com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO): com.tjshea.vigilant.data.tracker.TrackedBet {
        val start = now - 2 * 3_600_000L
        return com.tjshea.vigilant.data.tracker.TrackedBet(
            id, start - hoursBefore * 3_600_000L, "NFL", "A @ B", start, "Moneyline", "A", "m$id", "o$id", 0.5, 0.5, 0.515, 0.03, 1.0,
            status = BetStatus.PENDING, source = source, closingFair = 0.5 * closeRatio, closingSeenAtMs = start - 5 * 60_000L,
        )
    }

    @Test
    fun `edge accuracy is judged on the bets placed inside the trap guard's window, and the earlier ones are called out on their own (RESEARCH 82)`() {
        val base = SampleScan.state()
        // 20 bets placed 1 h before the start that closed 3% better than their price, 20 placed 30 h before that closed 4% worse: pooled −0.5%, a FAIL before.
        val near = (1..20).map { leadBet("n$it", 1, 1.03) }
        val early = (1..20).map { leadBet("e$it", 30, 0.96) }
        val checks = HealthChecks.of(base.copy(bets = near + early), extras, now)
        val acc = checks.first { it.area == "Edge accuracy (CLV)" }
        assertEquals(acc.toString(), HealthChecks.Level.OK, acc.level)
        assertTrue(acc.finding, acc.finding.startsWith("bets placed within 6 h of the start beat the close"))
        assertTrue(acc.evidence!!, acc.evidence!!.contains("average CLV +3.0%") && acc.evidence!!.contains("20 bets placed within 6 h of the start") && acc.evidence!!.contains("placed earlier: CLV -4.0% on 20 bets"))
        // The early ones are not hidden: a warning of their own, with the numbers, while some were placed in the last 3 days.
        val e = checks.first { it.area == "Early bets (CLV)" }
        assertEquals(HealthChecks.Level.WARN, e.level)
        assertTrue(e.finding, e.finding.contains("more than 6 h before the start lose to the close, and 20 were placed that early in the last 3 days"))
        assertTrue(e.evidence!!, e.evidence!!.contains("CLV -4.0% on 20 bets") && e.evidence!!.contains("within 6 h: CLV +3.0% on 20"))
        assertTrue(e.look!!, e.look!!.contains("Starts within: 6h"))
        // The window is the guard's own setting.
        val twelve = HealthChecks.of(base.copy(bets = near + early, settings = base.settings.copy(trapEarlyHours = 12)), extras, now)
        assertTrue(twelve.first { it.area == "Edge accuracy (CLV)" }.evidence!!.contains("placed within 12 h of the start"))
        // Early bets from long ago aren't a warning today: they are said, not flagged.
        val old = (1..20).map { leadBet("o$it", 300, 0.96) }
        val calm = HealthChecks.of(base.copy(bets = near + old), extras, now).first { it.area == "Early bets (CLV)" }
        assertEquals(HealthChecks.Level.OK, calm.level)
        assertTrue(calm.finding, calm.finding.contains("none were placed that early in the last 3 days"))
        // Early bets that beat the close are no problem either.
        val fine = HealthChecks.of(base.copy(bets = near + (1..20).map { leadBet("f$it", 30, 1.01) }), extras, now).first { it.area == "Early bets (CLV)" }
        assertEquals(HealthChecks.Level.OK, fine.level)
    }

    @Test
    fun `with too few bets inside the window the edge check judges them all together, and says nothing about a window`() {
        val checks = HealthChecks.of(SampleScan.state().let { it.copy(bets = (1..10).map { i -> leadBet("n$i", 1, 1.03) } + (1..20).map { i -> leadBet("e$i", 30, 0.96) }) }, extras, now)
        val acc = checks.first { it.area == "Edge accuracy (CLV)" }
        assertEquals(HealthChecks.Level.FAIL, acc.level) // −1.7% pooled
        assertTrue(acc.evidence!!, acc.evidence!!.contains("30 bets") && !acc.evidence!!.contains("placed within"))
        assertTrue(checks.none { it.area == "Early bets (CLV)" })
    }

    @Test
    fun `a scanner that is switched off is a warning for its old bets, not a failure, and a scanner is judged on its bets inside the window`() {
        val base = SampleScan.state()
        val vig = (1..15).map { leadBet("v$it", 1, 0.985, com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT) }
        val on = HealthChecks.of(base.copy(bets = vig, settings = base.settings.copy(scanner = ScannerMode.BOTH)), extras, now).first { it.area == "Vigilant's edges (CLV)" }
        assertEquals(HealthChecks.Level.FAIL, on.level)
        val off = HealthChecks.of(base.copy(bets = vig, settings = base.settings.copy(scanner = ScannerMode.CNO)), extras, now).first { it.area == "Vigilant's edges (CLV)" }
        assertEquals(HealthChecks.Level.WARN, off.level)
        assertTrue(off.finding, off.finding.contains("asleep") && off.finding.contains("nothing running to fix"))
        // CNO: 15 bets inside the window that beat the close, 15 earlier that lost to it: judged on the first, the others counted out loud.
        val cno = (1..15).map { leadBet("c$it", 1, 1.02) } + (1..15).map { leadBet("d$it", 30, 0.95) }
        val c = HealthChecks.of(base.copy(bets = cno), extras, now).first { it.area == "CNO's edges (CLV)" }
        assertEquals(HealthChecks.Level.OK, c.level)
        assertEquals("CLV +2.0% on 15 bets, beat the close 100%, EV when bet +3.0% (placed within 6 h; 15 earlier left out)", c.evidence)
    }

    @Test
    fun `each scanner is judged on its own closes, and imported marks that can never close aren't counted against the capture`() {
        val base = SampleScan.state()
        val start = now - 2 * 3_600_000L
        fun bet(id: String, source: String, closeRatio: Double, league: String = "NFL", outcome: String = "o$id") = com.tjshea.vigilant.data.tracker.TrackedBet(
            id, start - 3_600_000L, league, "A @ B", start, "Total", "Over 8.5", "m$id", outcome, 0.5, 0.5, 0.515, 0.03, 1.0,
            status = BetStatus.PENDING, source = source, closingFair = 0.5 * closeRatio, closingSeenAtMs = start - 5 * 60_000L,
        )
        val cno = (1..15).map { bet("c$it", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO, 1.02) }
        val vig = (1..15).map { bet("v$it", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT, 0.985) }
        val imported = (1..10).map { bet("i$it", com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_CNO, 1.0, league = "", outcome = "").copy(closingFair = null, closingSeenAtMs = null) }
        val checks = HealthChecks.of(base.copy(bets = cno + vig + imported), extras, now)
        val v = checks.first { it.area == "Vigilant's edges (CLV)" }
        assertEquals(HealthChecks.Level.FAIL, v.level)
        assertEquals("CLV -1.5% on 15 bets, beat the close 0%, EV when bet +3.0%", v.evidence)
        assertEquals(HealthChecks.Level.OK, checks.first { it.area == "CNO's edges (CLV)" }.level)
        val closes = checks.first { it.area == "Closing lines" }
        assertEquals(HealthChecks.Level.OK, closes.level)
        assertTrue(closes.evidence!!, closes.evidence!!.startsWith("30 of 30") && closes.evidence!!.contains("10 imported ✓ marks left out"))
    }

    @Test
    fun `the report lists Vigilant's bets against the close, and splits CLV by what made the fair odds`() {
        val start = now - 2 * 3_600_000L
        fun bet(id: String, basis: com.tjshea.vigilant.data.tracker.FairBasis?, close: Double) = com.tjshea.vigilant.data.tracker.TrackedBet(
            id, start - 3_600_000L, "MLB", "A @ B", start, "Total", "Over 8.5", "m$id", "o$id", 0.5, 0.5, 0.515, 0.03, 1.0,
            status = BetStatus.PENDING, source = com.tjshea.vigilant.data.tracker.BetTracker.SOURCE_VIGILANT, american = 100,
            closingFair = close, closingSeenAtMs = start - 5 * 60_000L, fairBasis = basis,
        )
        val bets = listOf(
            bet("a", com.tjshea.vigilant.data.tracker.FairBasis("BLEND", listOf("pinnacle", "kalshi"), 7), 0.51),
            bet("b", com.tjshea.vigilant.data.tracker.FairBasis("SHARP", listOf("kalshi"), 1), 0.48),
            bet("c", null, 0.49),
        )
        val rows = Diagnostics.closeRows(bets, now, TimeZone.getTimeZone("UTC"))
        assertEquals(3, rows.size)
        assertTrue(rows[0], rows.any { it.contains("MLB · Total: Over 8.5 · +100 · EV +3.0% · fair") && it.contains("CLV -4.0% (read before the start) · exchange sharp only (Kalshi), one book") })
        val basis = Diagnostics.basisLines(bets, now)
        assertTrue(basis.toString(), basis.any { it.startsWith("Pinnacle in the fair: 1 bets · EV when bet +3.0% · CLV +2.0% on 1, beat close 100%") })
        assertTrue(basis.toString(), basis.any { it.startsWith("exchange sharp only (Kalshi), one book: 1 bets") })
        assertEquals("(1 older bets: not recorded)", basis.last())
    }

    // ---- X2 (Tj, 2026-09-30: "The app just crashed a couple times") ----------------------------------------------------------------------

    @Test
    fun `a crash or freeze in the last day is a FAIL, and the report shows where the main thread was stuck`() {
        val freeze = AppExits.Exit(
            now - 20 * 60_000L, "not responding", "Input dispatching timed out", true, 412,
            listOf("\"main\" prio=5 tid=1 Runnable", "at com.tjshea.vigilant.app.UiState.feedAt(MainViewModel.kt:250)"),
        )
        val old = AppExits.Exit(now - 3 * 86_400_000L, "crash", "java.lang.OutOfMemoryError", true, 500)
        val quit = AppExits.Exit(now - 60 * 60_000L, "closed by you", null, false, null)
        val x = extras.copy(exits = listOf(freeze, quit, old))
        val check = HealthChecks.of(SampleScan.state(), x, now).first { it.area == "App stability" }
        assertEquals(HealthChecks.Level.FAIL, check.level)
        assertEquals("the app ended badly 1 time in the last day (1 not responding)", check.finding)
        assertTrue(check.evidence!!, check.evidence!!.contains("on screen: Input dispatching timed out"))
        val text = report(x = x)
        assertTrue(text, text.contains("== How the app last ended (Android's own record, newest first) =="))
        assertTrue(text, text.contains("not responding · on screen · 412 MB · Input dispatching timed out"))
        assertTrue(text, text.contains("    at com.tjshea.vigilant.app.UiState.feedAt(MainViewModel.kt:250)"))
        // Only ordinary exits lately: OK.
        assertEquals(HealthChecks.Level.OK, HealthChecks.of(SampleScan.state(), extras.copy(exits = listOf(quit, old)), now).first { it.area == "App stability" }.level)
    }

    /** Tj's 2026-10-02 file: "FAIL App stability: the app ended badly 3 times in the last day (3 low memory) [the last 1h ago, in the background]". */
    @Test
    fun `Android freeing a cached Vigilant is said but isn't a FAIL, and what ended before this version installed is a WARN`() {
        val cached = AppExits.Exit(now - 60 * 60_000L, "low memory", null, false, 190, importance = AppExits.CACHED)
        val x = extras.copy(exits = listOf(cached, cached.copy(atMs = now - 2 * 3_600_000L), cached.copy(atMs = now - 3 * 3_600_000L)))
        val check = HealthChecks.of(SampleScan.state(), x, now).first { it.area == "App stability" }
        assertEquals(HealthChecks.Level.OK, check.level)
        assertTrue(check.finding, check.finding.contains("Android also freed Vigilant's memory 3 times while it sat cached in the background"))
        val text = report(x = x)
        assertTrue(text, text.contains("low memory · cached in the background (nothing running) · 190 MB"))
        // A freeze, but on the version before this one: a WARN that says so, not a FAIL.
        val freeze = AppExits.Exit(now - 5 * 3_600_000L, "not responding", null, true, 300, importance = AppExits.FOREGROUND)
        val older = extras.copy(installedAtMs = now - 3_600_000L, exits = listOf(freeze))
        val warn = HealthChecks.of(SampleScan.state(), older, now).first { it.area == "App stability" }
        assertEquals(HealthChecks.Level.WARN, warn.level)
        assertTrue(warn.finding, warn.finding.endsWith(", 1 before this version was installed"))
        assertTrue(report(x = older).contains("not responding · on screen · 300 MB · before this version was installed"))
        // On this version: FAIL.
        assertEquals(HealthChecks.Level.FAIL, HealthChecks.of(SampleScan.state(), older.copy(installedAtMs = now - 6 * 3_600_000L), now).first { it.area == "App stability" }.level)
    }

    /** Tj's v0.38.0 report, 2026-10-01: an OutOfMemoryError at the heap limit mid-scan. The next report says what filled it. */
    @Test
    fun `the report has a memory block and warns when the heap is nearly full`() {
        val calm = extras.copy(memory = Diagnostics.Memory(140, 512, listOf("Scan result: 6321 priced sides in 120 games", "Kept between scans: 40 fair-odds boards (900 games, 41233 book lines) · 5100 Novig books + 3000 in the books cache")))
        val text = report(x = calm)
        assertTrue(text, text.contains("== Memory (the app's heap is fixed"))
        assertTrue(text, text.contains("Heap 140 of 512 MB (27%)"))
        assertTrue(text, text.contains("Scan result: 6321 priced sides in 120 games"))
        assertTrue(text, text.contains("41233 book lines"))
        assertEquals(HealthChecks.Level.OK, HealthChecks.of(SampleScan.state(), calm, now).single { it.area == "Memory" }.level)
        // Over the guard's line (75%): a WARN that says where to cut a scan down.
        val tight = extras.copy(memory = Diagnostics.Memory(400, 512, listOf("Scan result: 25000 priced sides in 300 games")))
        val warn = HealthChecks.of(SampleScan.state(), tight, now).single { it.area == "Memory" }
        assertEquals(HealthChecks.Level.WARN, warn.level)
        assertTrue(warn.text(), warn.text().contains("heap is nearly full: heap 400 of 512 MB (78%)"))
        assertTrue(warn.text(), warn.text().contains("Novig prices per scan"))
        // No numbers read (a test, a very old caller): no line at all.
        assertTrue(HealthChecks.of(SampleScan.state(), extras, now).none { it.area == "Memory" })
    }

    // ---- Tj, 2026-10-04: "novig scanning is going extremely slow. Maybe 1 per 2 seconds" (RESEARCH.md §81.1) ----

    private fun connected() = SampleScan.state().let { it.copy(novig = it.novig.copy(connection = com.tjshea.vigilant.data.novig.signing.NovigConnection("read-1", "a", "sub-1", false, tradingKeyId = "t"))) }

    @Test
    fun `it says why a scan was on the public routes - each stand-down of the key route with when, how long and why`() {
        val none = report(connected())
        assertTrue(none, none.contains("Novig key route: usable now · stand-downs since the app opened: 0"))
        val x = extras.copy(
            keyStanddowns = listOf(
                com.tjshea.vigilant.data.novig.KeyStanddown(now - 3_600_000L, 120_000L, "HTTP 451 ANONYMIZED_NETWORK"),
                com.tjshea.vigilant.data.novig.KeyStanddown(now - 60_000L, 30_000L, "HTTP 502 BAD_GATEWAY"),
            ),
            keyDownNow = "Novig lists the internet address of the network this phone is on as a VPN or proxy",
        )
        val text = report(connected(), x)
        assertTrue(text, text.contains("Novig key route: STANDING DOWN now (reads go to the public routes): Novig lists the internet address"))
        assertTrue(text, text.contains("stand-downs since the app opened: 2"))
        assertTrue(text, text.contains("public routes for 120 s · HTTP 451 ANONYMIZED_NETWORK"))
        assertTrue(text, text.contains("public routes for 30 s · HTTP 502 BAD_GATEWAY"))
        // Without a connected key there is no key route to talk about.
        assertTrue(report(SampleScan.state(), x).contains("Novig key route").not())
    }

    @Test
    fun `a key route that stood down is a health warning with its reasons, and one that is down now says so`() {
        fun checks(x: Diagnostics.Extras, s: UiState = connected()) = HealthChecks.of(s, x, now).filter { it.area == "Novig key route" }
        assertTrue(checks(extras).isEmpty())
        val recent = extras.copy(
            keyStanddowns = listOf(
                com.tjshea.vigilant.data.novig.KeyStanddown(now - 600_000L, 30_000L, "HTTP 404 MARKET_NOT_FOUND"),
                com.tjshea.vigilant.data.novig.KeyStanddown(now - 300_000L, 30_000L, "HTTP 404 MARKET_NOT_FOUND"),
                com.tjshea.vigilant.data.novig.KeyStanddown(now - 100_000L, 120_000L, "HTTP 451 ANONYMIZED_NETWORK"),
            ),
        )
        val c = checks(recent).single()
        assertEquals(HealthChecks.Level.WARN, c.level)
        assertEquals("stood down 3 times in the last 6 h", c.finding)
        assertEquals("HTTP 404 MARKET_NOT_FOUND ×2, HTTP 451 ANONYMIZED_NETWORK ×1", c.evidence)
        // Older than six hours: not news. No connected key: nothing to judge.
        assertTrue(checks(extras.copy(keyStanddowns = listOf(com.tjshea.vigilant.data.novig.KeyStanddown(now - 7 * 3_600_000L, 30_000L, "x")))).isEmpty())
        assertTrue(checks(recent, SampleScan.state()).isEmpty())
        val down = checks(extras.copy(keyDownNow = "VPN")).single()
        assertTrue(down.finding, down.finding.startsWith("standing down now"))
        assertEquals("VPN", down.evidence)
    }

    // ---- low API usage bids (RESEARCH.md §92) ------------------------------------------------------------------------------

    @Test
    fun `Diagnostics says what low API usage bids read, at what pace, through which feeds, and what cannot be read`() {
        val off = com.tjshea.vigilant.data.scanner.ScanSettings(maker = true)
        assertTrue("nothing when that isn't the bids' choice", Diagnostics.lowUsageLines(off, null, 0L).isEmpty())
        val low = com.tjshea.vigilant.data.scanner.ScanSettings(maker = true, makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE, lowUsagePace = 15, lowUsageMargin = 0.03)
        val plan = com.tjshea.vigilant.data.scanner.LowUsageBids.feedsFor(com.tjshea.vigilant.data.scanner.LowUsageBids.books(low), setOf("kalshi", "propline_props"))
        val lines = Diagnostics.lowUsageLines(low, plan, 0L)
        val text = lines.joinToString("\n")
        assertTrue(text, text.contains("ON: Vigilant's scan reads player props only, the next 6 h (the trap guard's hours; Off = as far as Starts within and Days ahead say), from the picked books alone"))
        // The trap guard Tj sets is the window the file says (Tj, 2026-10-07).
        assertTrue(Diagnostics.lowUsageLines(low.copy(trapEarlyHours = 8), plan, 0L).joinToString().contains("the next 8 h (the trap guard's hours"))
        assertTrue(Diagnostics.lowUsageLines(low.copy(trapEarlyHours = 0, daysAhead = 2), plan, 0L).joinToString().contains("the next 48 h (the trap guard's hours"))
        assertTrue(text, text.contains("books Kalshi, ProphetX, FanDuel"))
        assertTrue(text, text.contains("scan pace at most every 15 min (the usual gap is 4 min)"))
        assertTrue(text, text.contains("at least +3.0% under the fair") || text.contains("at least 3.0% under the fair"))
        assertTrue(text, text.contains("no bid longer than +130"))
        assertTrue(text, text.contains("feeds asked: Kalshi (free), PropLine props"))
        assertTrue("ProphetX has no feed with a key", text.contains("CANNOT BE READ: ProphetX"))
        // Chosen but bids off: said plainly, and the scan is the usual one.
        assertTrue(Diagnostics.lowUsageLines(low.copy(maker = false, makerRecommend = false), plan, 0L).first().contains("chosen but bids are off"))
        // Any limit of Tj's is the one shown, tighter or longer (it wins since 2026-10-07).
        assertTrue(Diagnostics.lowUsageLines(low.copy(makerMaxOdds = 115), plan, 0L).joinToString().contains("no bid longer than +115"))
        assertTrue(Diagnostics.lowUsageLines(low.copy(makerMaxOdds = 200), plan, 0L).joinToString().contains("no bid longer than +200"))
    }

    // ---- bids priced from CrazyNinjaOdds (Tj, 2026-10-07; RESEARCH.md §114) ------------------------------------------------------

    private val cnoLane = com.tjshea.vigilant.data.novig.trading.maker.CnoBidLane.Status(
        lastStepMs = now - 5_000, rows = 120, candidates = 40, pages = 12, pagesRead = 3, listAgeSec = 14, oldestPageSec = 41, wideRows = 300,
        skipped = mapOf("No book on CNO's page prices both sides" to 7),
    )

    @Test
    fun `Diagnostics has a line for bids priced from CrazyNinjaOdds - the ages, the pages, the stop and why rows got no line - and none for Vigilant's scan`() {
        val base = SampleScan.state()
        val cnoSet = base.settings.copy(makerSource = com.tjshea.vigilant.data.scanner.BidSource.CNO, maker = true, scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO, autoScan = AutoScanMode.CNO)
        val on = report(base.copy(settings = cnoSet), extras.copy(cnoBids = cnoLane.copy(stop = "CrazyNinjaOdds asked for a pause (busy): bids priced from it come down")))
        assertTrue(on, on.contains("Bids priced from CrazyNinjaOdds (RESEARCH.md §113-§114; Vigilant's scan is not used for them): CrazyNinjaOdds: its list 14 s old · 12 game pages held, the oldest line's data 41 s old"))
        assertTrue(on, on.contains("limit 120 s"))
        assertTrue(on, on.contains("wide list 300 rows"))
        assertTrue(on, on.contains("STOPPED: CrazyNinjaOdds asked for a pause"))
        assertTrue(on, on.contains("no line for: 7 × No book on CNO's page prices both sides"))
        val vig = report(base, extras)
        assertFalse(vig, vig.contains("Bids priced from CrazyNinjaOdds"))
    }

    @Test
    fun `health checks say when bids from CrazyNinjaOdds cannot be priced or are stopped, and say nothing for Vigilant's bids or when bids are off`() {
        val base = SampleScan.state()
        fun checks(set: com.tjshea.vigilant.data.scanner.ScanSettings, lane: com.tjshea.vigilant.data.novig.trading.maker.CnoBidLane.Status? = cnoLane) =
            HealthChecks.of(base.copy(settings = set), extras.copy(cnoBids = lane), now).filter { it.area == "Bids from CrazyNinjaOdds" }
        val cno = base.settings.copy(makerSource = com.tjshea.vigilant.data.scanner.BidSource.CNO, maker = true, scanner = com.tjshea.vigilant.data.scanner.ScannerMode.CNO, autoScan = AutoScanMode.CNO, pausedByHand = false)
        assertTrue(checks(cno).isEmpty())
        assertTrue(checks(cno.copy(autoScan = AutoScanMode.OFF)).single().finding.contains("background scan doesn't read CrazyNinjaOdds"))
        assertEquals("every bid priced from it is down", checks(cno, cnoLane.copy(stop = "CrazyNinjaOdds hasn't been read yet")).single().finding)
        assertTrue(checks(cno, cnoLane.copy(failed = 2, pagesRead = 0, lastError = "CrazyNinjaOdds is busy (HTTP 429)")).single().evidence!!.contains("HTTP 429"))
        // Vigilant-priced bids, or bids off: nothing about CrazyNinjaOdds.
        assertTrue(checks(cno.copy(makerSource = com.tjshea.vigilant.data.scanner.BidSource.VIGILANT)).isEmpty())
        assertTrue(checks(cno.copy(maker = false, makerRecommend = false)).isEmpty())
    }
}
