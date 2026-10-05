package com.tjshea.vigilant.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.app.ui.alertLabel
import com.tjshea.vigilant.data.alerts.EvAlert
import com.tjshea.vigilant.data.cno.CnoFeed
import com.tjshea.vigilant.data.scanner.Agreement
import com.tjshea.vigilant.data.scanner.AutoScanMode
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.tracker.PlacedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.async
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.TimeZone

/**
 * Tj, 2026-09-28: "run the app in the background and it will continue scanning … auto scan either cno
 * or both cno and vigilant every 5 10 20 30 or 40 minutes … if it is scanning in the background and at
 * any time it finds positive EV bets of 3% or higher and multiple books agree on the price that it
 * sends me an android push notification and I can click on the notification and it will open the exact
 * bet in novig immediately … select automatic notifications for a minimum of 2%, 3%, or 4%."
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoScanTest {

    private val now = SampleScan.NOW
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    // ---- settings ----------------------------------------------------------------------------------

    /**
     * Tj's v0.52.0 file: "a background cycle took 456 s (its interval is 30 sec)": the cycle waited Vigilant's whole scan out, and CNO went unread and
     * auto-bet idle for those minutes. Now the cycle starts the scan and ends; the scan's alerts go out when it ends.
     */
    @Test
    fun `a background cycle starts Vigilant's scan and ends without waiting for it`() {
        val app = context as VigilantApp
        kotlinx.coroutines.runBlocking {
            app.container.settingsStore.update {
                ScanSettings(autoScan = AutoScanMode.BOTH, scanner = com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT, autoScanSeconds = 30, leagues = setOf("NFL"), alertMinEv = 0.03)
            }
        }
        var started = 0
        // A scan that's started and never ends (the runner isn't even asked): the old cycle would wait for it forever.
        val scanner = AutoScanner(context, app.container, clock = { now }, phone = { false to false }, startVigilant = { _, _ -> started++; true })
        val ran = kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(20_000) { scanner.cycle(forceVigilant = true) } }
        assertTrue(ran)
        assertEquals(1, started)
        assertFalse(scanner.status.value.running)
    }

    @Test
    fun `auto-scan is off by default, every 5 seconds to 40 minutes, alerts at 2, 3 or 4 percent (3 by default)`() {
        val s = ScanSettings()
        assertEquals(AutoScanMode.OFF, s.autoScan)
        // Tj, 2026-10-01: 3 minutes, 1 minute, 30 seconds and 15 seconds beside the old 5-40 minutes, then "every 5 seconds for the auto bet function".
        assertEquals(listOf(5, 15, 30, 60, 180, 300, 600, 1200, 1800, 2400), ScanSettings.AUTO_SCAN_SECONDS_CHOICES)
        assertEquals(listOf("5 sec", "15 sec", "30 sec", "1 min", "3 min", "5 min", "10 min", "20 min", "30 min", "40 min"), ScanSettings.AUTO_SCAN_SECONDS_CHOICES.map(ScanSettings::intervalLabel))
        assertEquals(600, s.autoScanSeconds)
        assertTrue(s.autoScanSeconds in ScanSettings.AUTO_SCAN_SECONDS_CHOICES)
        assertEquals(listOf(0.0, 0.02, 0.03, 0.04), ScanSettings.ALERT_MIN_EV_CHOICES)
        assertEquals(0.03, s.alertMinEv, 0.0)
        assertEquals(listOf("Off", "CNO", "CNO + Vigilant"), AutoScanMode.entries.map { it.displayName })
        assertTrue(AutoScanMode.CNO.cno && !AutoScanMode.CNO.vigilant)
        assertTrue(AutoScanMode.BOTH.cno && AutoScanMode.BOTH.vigilant)
        assertFalse(AutoScanMode.OFF.cno || AutoScanMode.OFF.vigilant)
        assertEquals(listOf("Off", "2%+", "3%+", "4%+"), ScanSettings.ALERT_MIN_EV_CHOICES.map { alertLabel(it) })
    }

    @Test
    fun `the next scan is due its interval after the last one started, never sooner than 5 s`() {
        assertEquals(now, AutoScanClock.nextAtMs(null, 600, now))
        assertEquals(now + 10 * 60_000L, AutoScanClock.nextAtMs(now, 600, now + 1_000))
        // A scan that ran past its interval: the next one waits a moment rather than running back to back.
        assertEquals(now + 6 * 60_000L + AutoScanClock.MIN_GAP_MS, AutoScanClock.nextAtMs(now, 300, now + 6 * 60_000L))
    }

    /** Tj, 2026-10-01: "scan every 3 minutes, 1 minute, 30 seconds, and 15 seconds". */
    @Test
    fun `the fast intervals are due their seconds after the last start, and a slow cycle waits only a moment`() {
        assertEquals(now + 15_000L, AutoScanClock.nextAtMs(now, 15, now + 2_000))
        assertEquals(now + 30_000L, AutoScanClock.nextAtMs(now, 30, now + 2_000))
        assertEquals(now + 60_000L, AutoScanClock.nextAtMs(now, 60, now + 2_000))
        assertEquals(now + 180_000L, AutoScanClock.nextAtMs(now, 180, now + 2_000))
        // A 15 s cycle that took 22 s (a slow page): the next one is 5 s after it ended, not already overdue and not a minute away.
        assertEquals(now + 22_000L + AutoScanClock.MIN_GAP_MS, AutoScanClock.nextAtMs(now, 15, now + 22_000L))
        assertTrue(AutoScanClock.MIN_GAP_MS < 15_000L)
    }

    /** Tj, 2026-10-01: "Add an option to scan cno every 5 seconds for the auto bet function." */
    @Test
    fun `a 5 second interval checks CNO every 5 seconds, a long cycle waits 5 seconds after it, and Vigilant's own scan still waits 4 minutes`() {
        assertEquals("5 sec", ScanSettings.intervalLabel(5))
        assertEquals(now + 5_000L, AutoScanClock.nextAtMs(now, 5, now + 2_000L))
        // A 2 s cycle: the next is 5 s after the START (an every-5-seconds cadence, not a cycle plus 5 s).
        assertEquals(now + 5_000L, AutoScanClock.nextAtMs(now, 5, now + 2_000L))
        // A cycle that took 9 s (a CNO page and a few books): the next starts 1 s after it ended, never already overdue; slower intervals keep their 5 s.
        assertEquals(now + 9_000L + 1_000L, AutoScanClock.nextAtMs(now, 5, now + 9_000L))
        assertEquals(1_000L, AutoScanClock.minGapMs(5))
        assertEquals(AutoScanClock.MIN_GAP_MS, AutoScanClock.minGapMs(15))
        // CNO's own floor between two reads is lower than the interval, so a 5 s cycle never asks for a read CNO's pace would refuse.
        assertTrue(com.tjshea.vigilant.data.cno.CnoFeed.MIN_GAP_MS < 5_000L)
        // Vigilant's scan spends API credits: not at 5 s, not at 3 min; due at 4 min.
        assertFalse(AutoScanClock.vigilantDue(now - 5_000L, 5, now))
        assertFalse(AutoScanClock.vigilantDue(now - 180_000L, 5, now))
        assertTrue(AutoScanClock.vigilantDue(now - 240_000L, 5, now))
        assertEquals(240, ScanSettings.vigilantEverySeconds(5))
        assertEquals(60_000L, AutoScanClock.closingFreshMs(5))
    }

    @Test
    fun `Vigilant's own scan, which spends API credits, runs at most every 4 minutes however fast the cycles are`() {
        val fourMin = 4 * 60_000L
        // None yet this run: it runs now, at any interval.
        for (seconds in ScanSettings.AUTO_SCAN_SECONDS_CHOICES) assertTrue(AutoScanClock.vigilantDue(null, seconds, now))
        // 5 minutes and slower: every cycle, as before.
        for (seconds in listOf(300, 600, 1200, 1800, 2400)) assertTrue(AutoScanClock.vigilantDue(now - seconds * 1_000L + 400, seconds, now))
        // 15 s: not 15 s, 3 min, or a cycle early; due at 4 min (the alarm may be a second or two off).
        assertFalse(AutoScanClock.vigilantDue(now - 15_000L, 15, now))
        assertFalse(AutoScanClock.vigilantDue(now - 180_000L, 15, now))
        assertFalse(AutoScanClock.vigilantDue(now - (fourMin - 15_000L), 15, now))
        assertTrue(AutoScanClock.vigilantDue(now - fourMin, 15, now))
        assertTrue(AutoScanClock.vigilantDue(now - fourMin + 1_500L, 15, now))
        // 3 min: the cycle after the one that ran it (3 min on) is too soon, the one after that (6 min on) runs.
        assertFalse(AutoScanClock.vigilantDue(now - 180_000L, 180, now))
        assertTrue(AutoScanClock.vigilantDue(now - 360_000L, 180, now))
        // What the Settings hint promises is what runs: every 4 min at 15 s, 30 s and 1 min, every 6 min at 3 min, else the interval.
        assertEquals(listOf(240, 240, 240, 240, 360, 300, 600, 1200, 1800, 2400), ScanSettings.AUTO_SCAN_SECONDS_CHOICES.map(ScanSettings::vigilantEverySeconds))
    }

    /** Low API usage bids (RESEARCH.md §92) run Vigilant's scan at the pace Tj picked, however fast the cycles are, and the cycle really asks for it. */
    @Test
    fun `in low API usage bids Vigilant's scan waits the picked pace - 10 minutes by default - and the usual four otherwise`() {
        val low = ScanSettings(makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE, maker = true)
        val gap = low.vigilantGapSeconds
        assertEquals(600, gap)
        // A 60-second cycle (what bids turn on): not due 4 or 9 minutes after the last one, due at 10; the usual gap would have run at 4.
        assertFalse(AutoScanClock.vigilantDue(now - 4 * 60_000L, 60, now, gap))
        assertFalse(AutoScanClock.vigilantDue(now - 9 * 60_000L, 60, now, gap))
        assertTrue(AutoScanClock.vigilantDue(now - 10 * 60_000L, 60, now, gap))
        assertTrue(AutoScanClock.vigilantDue(now - 4 * 60_000L, 60, now))
        assertTrue("none yet this run: now", AutoScanClock.vigilantDue(null, 60, now, gap))
        // A cycle as slow as the pace runs every time; a slower pace than the cycle still holds the scan back.
        assertTrue(AutoScanClock.vigilantDue(now - 600_000L + 400, 600, now, gap))
        assertFalse(AutoScanClock.vigilantDue(now - 600_000L, 600, now, 1_800))
        // The cycle passes the settings' own gap.
        val src = java.io.File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        assertTrue(src.contains("AutoScanClock.vigilantDue(lastVigilantStartMs, settings.autoScanSeconds, clock(), settings.vigilantGapSeconds)"))
    }

    /** Tj's screenshot, 2026-10-05: at 15 s the page says Vigilant's own scan starts at most every 4 min; with low API usage bids on it is their pace, and the page says so. */
    @Test
    fun `the Scanning page says how often Vigilant's own scan really runs at a 15 second interval - 4 minutes, or the low API usage pace`() {
        val base = ScanSettings(autoScan = AutoScanMode.BOTH, autoScanSeconds = 15, scanner = com.tjshea.vigilant.data.scanner.ScannerMode.BOTH)
        val usual = com.tjshea.vigilant.app.ui.autoScanHint(base)
        assertTrue(usual, usual.contains("it starts at most every 4 min, however fast CNO is read"))
        assertTrue(usual, usual.contains("360 scans a day"))
        assertFalse(usual.contains("Low API usage"))
        val low = com.tjshea.vigilant.app.ui.autoScanHint(base.copy(maker = true, makerFocus = com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE))
        assertTrue(low, low.contains("it starts at most every 10 min, however fast CNO is read"))
        assertTrue(low, low.contains("144 scans a day"))
        assertTrue(low, low.contains("Low API usage bids are on: this scan reads player props only"))
        // The gap, not the cycle, sets it; a gap shorter than the cycle changes nothing.
        assertEquals(600, ScanSettings.vigilantEverySeconds(15, 600))
        assertEquals(600, ScanSettings.vigilantEverySeconds(600, 600))
        assertEquals(1800, ScanSettings.vigilantEverySeconds(1800, 600))
        assertEquals(240, ScanSettings.vigilantEverySeconds(15))
    }

    /** Faster cycles than the usual 5 minutes also re-read the bets in their last 15 minutes at the cycle's pace: a closer last read before the start is the close. */
    @Test
    fun `cycles faster than 5 minutes re-read bets about to start at their own pace, never faster than once a minute`() {
        // 5 s, 15 s and 30 s cycles: once a minute each at most; 1 min: every minute; 3 min: every 3 minutes; 5 minutes and slower: the usual 5 minute rule alone.
        assertEquals(listOf(60_000L, 60_000L, 60_000L, 60_000L, 180_000L, null, null, null, null, null), ScanSettings.AUTO_SCAN_SECONDS_CHOICES.map(AutoScanClock::closingFreshMs))
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        val closing = src.substringAfter("c.recheck.captureClosing()").substringBefore("}.onFailure")
        assertTrue(closing, closing.contains("AutoScanClock.closingFreshMs(settings.autoScanSeconds)?.let { c.recheck.captureClosing(withinMs = ClosingLine.TRUE_CLOSE_MS, freshMs = it) }"))
        // Both reads sit in the CNO branch, before Vigilant's scan: nothing reads CNO's pages with the scanner on Vigilant only.
        assertTrue(src.indexOf("closingFreshMs(settings") < src.indexOf("if (settings.autoScansVigilant"))
        assertTrue(src.indexOf("if (settings.autoScansCno) {") < src.indexOf("closingFreshMs(settings"))
    }

    /** The schedule's own survival: an alarm that goes off during a cycle is dropped, so the cycle's end must arm the next one. */
    @Test
    fun `a cycle that outlasts its interval arms the next alarm when it ends, unless the service is stopping`() {
        val service = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt").readText()
        val finallyBlock = service.substringAfter("container.autoScan.cycle(forceVigilant) }").substringBefore("updateOngoing(container.autoScan.status.value, force = true)")
        assertTrue(finallyBlock, finallyBlock.contains("if (!stopping) {"))
        assertTrue(finallyBlock, finallyBlock.contains("armAlarm(KeepAwake.active(now), now.autoScanSeconds, AutoScanClock.nextAtMs(container.autoScan.status.value.lastStartMs, now.autoScanSeconds, System.currentTimeMillis()))"))
        // Stop and destroy set the flag first, so a cycle cancelled by them can't arm an alarm after the schedule was cancelled.
        assertTrue(service.contains("private fun stopNow() {\n        stopping = true\n        AutoScanAlarm.cancel(this)"))
        assertTrue(service.contains("override fun onDestroy() {\n        stopping = true"))
        // The first alarm is still armed when the cycle starts, so a cycle killed part-way can't end the schedule.
        assertTrue(service.indexOf("armAlarm(KeepAwake.active(s), s.autoScanSeconds, System.currentTimeMillis() + s.autoScanSeconds") < service.indexOf("container.autoScan.cycle(forceVigilant) }"))
        // Scan now (the notification's button) runs Vigilant's scan whatever the credit gate says; the alarm's cycle obeys it.
        assertTrue(service.contains("ACTION_SCAN_NOW -> runCycle(forceVigilant = true)"))
        assertTrue(File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText().contains("(forceVigilant || AutoScanClock.vigilantDue("))
    }

    @Test
    fun `a saved file's minutes become seconds once, and a later pick sticks`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        // v0.37.0's file: minutes, no seconds, schema 11.
        val old = json.decodeFromString(ScanSettings.serializer(), """{"autoScan":"BOTH","autoScanMinutes":20,"schema":11}""")
        val moved = old.migrate()
        assertEquals(1200, moved.autoScanSeconds)
        assertEquals(12, moved.schema)
        assertEquals(AutoScanMode.BOTH, moved.autoScan)
        // Saved at schema 12 with 15 s picked: loading and migrating again changes nothing.
        val picked = moved.copy(autoScanSeconds = 15)
        val again = json.decodeFromString(ScanSettings.serializer(), json.encodeToString(ScanSettings.serializer(), picked)).migrate()
        assertEquals(15, again.autoScanSeconds)
        // A fresh install: the 10 minute default is 600 seconds.
        assertEquals(600, ScanSettings().migrate().autoScanSeconds)
        // Every minute choice of the old list lands on a choice of the new one.
        for (minutes in listOf(5, 10, 20, 30, 40)) assertTrue(ScanSettings(autoScanMinutes = minutes).migrate().autoScanSeconds in ScanSettings.AUTO_SCAN_SECONDS_CHOICES)
    }

    // ---- which CNO bets alert ------------------------------------------------------------------------

    private val jefferson = SampleCno.rows[1]

    @Test
    fun `a CNO bet at or over the minimum that the books agree on alerts`() {
        val s = SampleCno.withBooks().indexed(now)
        val alerts = AlertPicks.cno(s, 0.03, now)
        assertEquals(listOf("Justin Jefferson Under 69.5"), alerts.map { it.bet })
        val a = alerts.single()
        assertEquals(AlertPicks.SCANNER_CNO, a.scanner)
        assertEquals(117, a.american)
        assertEquals(jefferson.ev, a.ev, 1e-9)
        assertEquals(3, a.books)
        assertEquals(3, a.agreeing)
        // No Novig link known yet: the cycle looks one up before posting.
        assertNull(a.link)
        // Over the minimum, nothing.
        assertTrue(AlertPicks.cno(s, 0.06, now).isEmpty())
        // Alerts off: nothing.
        assertTrue(AlertPicks.cno(s, 0.0, now).isEmpty())
    }

    @Test
    fun `a CNO bet whose books haven't been read, or placed, or outside the window, doesn't alert`() {
        // The other bets are +EV too, but nobody read their books: not "several books agree".
        assertTrue(AlertPicks.cnoCandidates(SampleCno.withBooks().indexed(now), 0.03, now).size > 1)
        // Placed with ✓: never alerts.
        val placed = SampleCno.withBooks().copy(placed = listOf(PlacedBet(MiniWindow.cnoKey(jefferson), jefferson.bet, placedAtMs = now - 60_000, startsAtMs = jefferson.startsAtMs))).indexed(now)
        assertTrue(AlertPicks.cno(placed, 0.03, now).isEmpty())
        // Starts in 24 h: outside a 12 h window.
        val twelve = SampleCno.withBooks().let { it.copy(settings = it.settings.copy(startsWithinHours = 12)) }.indexed(now)
        assertTrue(AlertPicks.cno(twelve, 0.03, now).isEmpty())
        // The green check switched off in Settings doesn't stop alerts: they read the books themselves.
        val noCheck = SampleCno.withBooks().let { it.copy(settings = it.settings.copy(cnoCheckBooks = false)) }.indexed(now)
        assertEquals(1, AlertPicks.cno(noCheck, 0.03, now).size)
    }

    @Test
    fun `the trap guard's early rule (RESEARCH 71) - no alert for a game further off than its hours, CNO's or Vigilant's, and its books aren't read for one`() {
        fun guarded(hours: Int, base: UiState) = base.let { it.copy(settings = it.settings.copy(trapEarlyHours = hours)) }.indexed(now)
        // Jefferson starts a day off.
        assertTrue(AlertPicks.cno(guarded(12, SampleCno.withBooks()), 0.03, now).isEmpty())
        assertEquals(1, AlertPicks.cno(guarded(24, SampleCno.withBooks()), 0.03, now).size)
        assertEquals(1, AlertPicks.cno(guarded(0, SampleCno.withBooks()), 0.03, now).size)
        // The candidates whose books a cycle reads leave them out too, and the auto-bet's count says how many.
        val twelve = guarded(12, SampleCno.withBooks())
        assertTrue(AlertPicks.cnoCandidates(twelve, 0.03, now).all { it.row.startsAtMs!! - now <= 12 * 3_600_000L })
        assertTrue(AlertPicks.tooEarly(twelve, 0.03, now) >= 1)
        assertEquals(0, AlertPicks.tooEarly(guarded(0, SampleCno.withBooks()), 0.03, now))
        // Vigilant's own alerts: every sample game is 8 h or more off.
        assertTrue(AlertPicks.vigilant(SampleScan.state().indexed(now), 0.02, now).isNotEmpty())
        assertTrue(AlertPicks.vigilant(guarded(6, SampleScan.state()), 0.02, now).isEmpty())
    }

    @Test
    fun `a CNO bet whose Novig link is known opens that exact bet slip`() {
        val link = "novigapp://events/out-123/cno"
        val s = SampleCno.withBooks().copy(cnoLinks = mapOf(CnoFeed.linkKey(jefferson) to link)).indexed(now)
        val a = AlertPicks.cno(s, 0.03, now).single()
        assertEquals(link, a.link)
        assertTrue(a.exact)
        assertEquals("out-123", a.outcomeId)
        assertEquals("outcome:out-123", a.dedupeKey)
    }

    // ---- which Vigilant bets alert ------------------------------------------------------------------

    @Test
    fun `Vigilant's bets alert when the books agree, with the exact bet slip`() {
        val s = SampleScan.state().indexed(now)
        val alerts = AlertPicks.vigilant(s, 0.02, now)
        assertTrue(alerts.isNotEmpty())
        val feed = s.feedAt(now).associateBy { it.key }
        for (a in alerts) {
            val o = feed.getValue(a.key)
            assertTrue(a.ev >= 0.02)
            assertTrue(Agreement.of(o).agrees)
            assertEquals("novigapp://events/${o.outcome.outcomeId}", a.link)
            assertTrue(a.exact)
            assertEquals(o.outcome.outcomeId, a.outcomeId)
        }
        // Every one the feed has at 2%+ that the books back is there.
        val expected = s.feedAt(now).filter { (it.evPercent ?: 0.0) >= 0.02 && Agreement.of(it).agrees }.map { it.key }
        assertEquals(expected, alerts.map { it.key })
        assertTrue(AlertPicks.vigilant(s, 0.0, now).isEmpty())
        // A higher minimum alerts on fewer.
        assertTrue(AlertPicks.vigilant(s, 0.04, now).size <= alerts.size)
    }

    @Test
    fun `a Vigilant bet already placed never alerts`() {
        val s = SampleScan.state().indexed(now)
        val first = AlertPicks.vigilant(s, 0.02, now).first()
        val placed = s.copy(placed = listOf(PlacedBet(first.key, first.bet, placedAtMs = now - 60_000, startsAtMs = first.startsAtMs, outcomeId = first.outcomeId))).indexed(now)
        assertTrue(AlertPicks.vigilant(placed, 0.02, now).none { it.key == first.key })
    }

    // ---- the notification --------------------------------------------------------------------------

    private fun alert(link: String? = "novigapp://events/o1", exact: Boolean = true) = EvAlert(
        "CNO", "cno:k", "o1", "Justin Jefferson Under 69.5", "Player Receiving Yards", "Minnesota Vikings @ Tampa Bay Buccaneers",
        117, 0.0584, 3, 3, now + 86_400_000L, link, exact,
    )

    @Test
    fun `the alert says the EV, the bet, the price, the game and who agrees`() {
        val a = alert()
        assertEquals("+5.8% EV · Justin Jefferson Under 69.5", EvAlerts.title(a))
        assertEquals(
            "+117 on Novig · Player Receiving Yards · Minnesota Vikings @ Tampa Bay Buccaneers, Sun 1:20 AM",
            EvAlerts.text(a, TimeZone.getTimeZone("America/New_York")),
        )
        assertEquals("3 of 3 books agree · found by CNO", EvAlerts.detail(a))
        // A tap opens Vigilant now, so no alert says it opens only the game.
        assertEquals("3 of 3 books agree · found by CNO", EvAlerts.detail(alert(exact = false)))
    }

    private fun installNovig() {
        val main = ComponentName(MiniWindow.NOVIG_PACKAGE, "${MiniWindow.NOVIG_PACKAGE}.MainActivity")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(main)
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test
    fun `alerts post as high-importance notifications that open Vigilant full screen, once notifications are allowed`() {
        installNovig()
        val nm = context.getSystemService(NotificationManager::class.java)
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(0, EvAlerts.post(context, listOf(alert()), now))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(1, EvAlerts.post(context, listOf(alert()), now))
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(EvAlerts.CHANNEL, n.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel(EvAlerts.CHANNEL).importance)
        // Tj, 2026-09-30: "when I click on anything in the push notifications for vigilant, instead of opening the bet, it opens the vigilant
        // app in full screen": Vigilant's own screen, never Novig's bet slip, even with Novig installed.
        val tap = shadowOf(n.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component!!.className)
        assertEquals(context.packageName, tap.component!!.packageName)
        assertNull(tap.data)
        assertTrue(tap.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0 && tap.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(shadowOf(n.contentIntent).isActivityIntent)
    }

    // ---- "✓ Placed" on the alert (Tj, 2026-09-29) ---------------------------------------------------------

    // The placed-bet store prunes by the real clock, so the game starts a real day from now.
    private fun fullAlert(stake: Double? = 5.0) = alert().copy(
        startsAtMs = System.currentTimeMillis() + 86_400_000L, stake = stake, league = "NFL", gameUrl = "https://crazyninjaodds.com/game?side_id=9", betUrl = "https://crazyninjaodds.com/d?l=9", live = false,
    )

    @Test
    fun `a tap removes the alert, and its Placed button says the amount it tracks`() {
        installNovig()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = context.getSystemService(NotificationManager::class.java)
        val a = fullAlert()
        assertEquals(1, EvAlerts.post(context, listOf(a), now))
        val n = shadowOf(nm).allNotifications.single()
        // Tapping opens Novig full screen and takes the alert down (Tj, 2026-09-30: "that notification should be removed").
        assertEquals(android.app.Notification.FLAG_AUTO_CANCEL, n.flags and android.app.Notification.FLAG_AUTO_CANCEL)
        assertTrue(n.contentIntent != null)
        val action = n.actions.single()
        assertEquals("✓ Placed $5", action.title.toString())
        val sent = shadowOf(action.actionIntent).savedIntent
        assertEquals(EvAlerts.ACTION_PLACED, sent.action)
        assertEquals(AlertActionReceiver::class.java.name, sent.component!!.className)
        assertTrue(shadowOf(action.actionIntent).isBroadcastIntent)
        // The button carries the whole alert: the receiver needs no scan, screen or list to log the bet.
        assertEquals(a, EvAlerts.alertOf(sent))
        assertEquals("✓ Placed", EvAlerts.placedLabel(fullAlert(stake = null)))
        assertNull(EvAlerts.alertOf(Intent("nothing")))
    }

    @Test
    fun `Placed tracks the bet, hides it everywhere and turns the alert into a quiet Tracked confirmation with Undo`() = kotlinx.coroutines.runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val app = context as VigilantApp
        val nm = context.getSystemService(NotificationManager::class.java)
        val a = fullAlert()
        EvAlerts.post(context, listOf(a), now)
        EvAlerts.handle(context, app.container, EvAlerts.ACTION_PLACED, a)
        val bet = app.container.tracker.all().single { it.placedKey == a.key }
        assertEquals(5.0, bet.stake, 0.0)
        assertEquals(117, bet.american)
        assertEquals("https://crazyninjaodds.com/game?side_id=9", bet.gameUrl)
        assertEquals(a.key, app.container.placed.load().bets.single { it.key == a.key }.key)
        // The alert was replaced, in place, by a low-importance confirmation with Undo.
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(EvAlerts.DONE_CHANNEL, n.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(EvAlerts.DONE_CHANNEL).importance)
        assertTrue(n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().startsWith("Tracked ✓ Justin Jefferson"))
        // Tapping the confirmation opens Vigilant too (it used to do nothing).
        assertEquals(MainActivity::class.java.name, shadowOf(n.contentIntent).savedIntent.component!!.className)
        assertEquals("Undo", n.actions.single().title.toString())
        assertEquals(EvAlerts.ACTION_UNDO, shadowOf(n.actions.single().actionIntent).savedIntent.action)
        // Undo: the bet, the mark and the confirmation all go.
        EvAlerts.handle(context, app.container, EvAlerts.ACTION_UNDO, a)
        assertTrue(app.container.tracker.all().none { it.placedKey == a.key })
        assertTrue(app.container.placed.load().bets.none { it.key == a.key })
        assertTrue(shadowOf(nm).allNotifications.isEmpty())
    }

    @Test
    fun `the receiver reads the alert out of the button and does the same`() = kotlinx.coroutines.runBlocking {
        val app = context as VigilantApp
        val a = fullAlert().copy(key = "cno:receiver-test", outcomeId = "o-recv")
        AlertActionReceiver().onReceive(context, EvAlerts.actionIntent(context, a, EvAlerts.ACTION_PLACED))
        // It runs in the app's own scope; wait for it (a few ms of disk).
        val deadline = System.currentTimeMillis() + 5_000
        while (app.container.tracker.all().none { it.placedKey == a.key } && System.currentTimeMillis() < deadline) kotlinx.coroutines.delay(20)
        assertTrue(app.container.tracker.all().any { it.placedKey == a.key })
        // A broadcast with no alert in it does nothing.
        AlertActionReceiver().onReceive(context, Intent(EvAlerts.ACTION_PLACED))
    }

    @Test
    fun `each background cycle also records the closing line of the open bets about to start`() {
        // The cycle needs CNO and a clock to run for real; this pins that the Tracker's CLV read is in it (Tj, 2026-09-29).
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        assertTrue(src.contains("c.recheck.captureClosing()"))
        // Inside the CNO branch: with CNO off, nothing reads CNO's game pages.
        val cno = src.indexOf("if (settings.autoScansCno) {")
        assertTrue(cno in 0 until src.indexOf("c.recheck.captureClosing()"))
        assertTrue(src.indexOf("c.recheck.captureClosing()") < src.indexOf("if (settings.autoScansVigilant"))
    }

    /** Tj, 2026-09-29: "if I have cno only turned on in the settings that it doesn't scan vigilant in the background and waste api usage". */
    @Test
    fun `a background cycle runs each scanner only when the scanner choice has it on`() {
        val src = File("src/main/kotlin/com/tjshea/vigilant/app/AutoScan.kt").readText()
        // Both parts are gated on the scanner choice, not on the auto-scan mode alone (which is what let "Both" scan Vigilant on CNO only).
        assertTrue(src.contains("if (settings.autoScansVigilant && settings.leagues.isNotEmpty() &&"))
        assertTrue(!src.contains("settings.autoScan.vigilant"))
        assertTrue(!src.contains("settings.autoScan.cno"))
        // The scan itself is refused for CNO only at the runner, whoever starts it (CnoOnlyAsleepTest).
        assertTrue(File("../data/src/main/kotlin/com/tjshea/vigilant/data/scanner/ScanRunner.kt").readText().contains("if (!settings.vigilantOn) return false"))
    }

    @Test
    fun `the notification and the Settings hint say what really runs at each scanner choice`() {
        fun s(scanner: com.tjshea.vigilant.data.scanner.ScannerMode) = ScanSettings(scanner = scanner, autoScan = AutoScanMode.BOTH, autoScanSeconds = 600)
        assertEquals("Auto-scan: CNO + Vigilant every 10 min", AutoScanText.title(s(com.tjshea.vigilant.data.scanner.ScannerMode.BOTH)))
        assertEquals("Auto-scan: CNO every 10 min", AutoScanText.title(s(com.tjshea.vigilant.data.scanner.ScannerMode.CNO)))
        assertEquals("Auto-scan: Vigilant every 10 min", AutoScanText.title(s(com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT)))
        val cnoOnly = com.tjshea.vigilant.app.ui.autoScanHint(s(com.tjshea.vigilant.data.scanner.ScannerMode.CNO))
        assertTrue(cnoOnly, cnoOnly.contains("Vigilant's scan is skipped, because the scanner above is on CNO only: no API credits are spent in the background"))
        assertTrue(cnoOnly, !cnoOnly.contains("scans a day"))
        val both = com.tjshea.vigilant.app.ui.autoScanHint(s(com.tjshea.vigilant.data.scanner.ScannerMode.BOTH))
        assertTrue(both, both.contains("Each scan spends API credits like a tap on Scan"))
        val vigilantOnly = com.tjshea.vigilant.app.ui.autoScanHint(s(com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT))
        assertTrue(vigilantOnly, vigilantOnly.contains("CrazyNinjaOdds isn't read, because the scanner above is on Vigilant only"))
        val nothing = com.tjshea.vigilant.app.ui.autoScanHint(s(com.tjshea.vigilant.data.scanner.ScannerMode.VIGILANT).copy(autoScan = AutoScanMode.CNO))
        assertTrue(nothing, nothing.startsWith("Nothing runs in the background"))
    }

    /** Tj, 2026-10-01: the Settings hint and the notification say a fast interval in seconds, and what it costs. */
    @Test
    fun `the Settings hint and the notification name a fast interval in seconds and say what runs each cycle`() {
        fun s(seconds: Int, scanner: com.tjshea.vigilant.data.scanner.ScannerMode = com.tjshea.vigilant.data.scanner.ScannerMode.CNO) =
            ScanSettings(scanner = scanner, autoScan = AutoScanMode.CNO, autoScanSeconds = seconds)
        assertEquals("Auto-scan: CNO every 15 sec", AutoScanText.title(s(15)))
        assertEquals("Auto-scan: CNO every 30 sec", AutoScanText.title(s(30)))
        assertEquals("Auto-scan: CNO every 1 min", AutoScanText.title(s(60)))
        assertEquals("Auto-scan: CNO every 3 min", AutoScanText.title(s(180)))
        val fifteen = com.tjshea.vigilant.app.ui.autoScanHint(s(15))
        assertTrue(fifteen, fifteen.startsWith("Every 15 sec, with Vigilant open or closed:"))
        assertTrue(fifteen, fifteen.contains("About 5,760 reads of CNO a day"))
        assertTrue(fifteen, fifteen.contains("in their last 15 minutes, once a minute each at most"))
        assertTrue(fifteen, fifteen.contains("Under a minute apart is constant background work"))
        val three = com.tjshea.vigilant.app.ui.autoScanHint(s(180))
        assertTrue(three, three.contains("About 480 reads of CNO a day") && !three.contains("Under a minute apart"))
        val slow = com.tjshea.vigilant.app.ui.autoScanHint(s(600))
        assertTrue(slow, slow.contains("About 144 reads of CNO a day") && !slow.contains("once a minute each at most"))
        // With Vigilant's scan too: it says it starts at most every 4 minutes however fast CNO is read, and its credits follow that.
        val both = com.tjshea.vigilant.app.ui.autoScanHint(s(15, com.tjshea.vigilant.data.scanner.ScannerMode.BOTH).copy(autoScan = AutoScanMode.BOTH))
        assertTrue(both, both.contains("360 scans a day at this setting (it starts at most every 4 min, however fast CNO is read)"))
        val bothThree = com.tjshea.vigilant.app.ui.autoScanHint(s(180, com.tjshea.vigilant.data.scanner.ScannerMode.BOTH).copy(autoScan = AutoScanMode.BOTH))
        assertTrue(bothThree, bothThree.contains("240 scans a day at this setting (it starts at most every 6 min"))
        val bothSlow = com.tjshea.vigilant.app.ui.autoScanHint(s(600, com.tjshea.vigilant.data.scanner.ScannerMode.BOTH).copy(autoScan = AutoScanMode.BOTH))
        assertTrue(bothSlow, bothSlow.contains("144 scans a day at this setting.") && !bothSlow.contains("at most every"))
    }

    // ---- the service's notification and alarm ------------------------------------------------------

    @Test
    fun `the ongoing notification says what runs, when next, and what the last scan found`() {
        val s = ScanSettings(autoScan = AutoScanMode.BOTH, autoScanSeconds = 600)
        val zone = TimeZone.getTimeZone("UTC")
        assertEquals("Auto-scan: CNO + Vigilant every 10 min", AutoScanText.title(s))
        val idle = AutoScanner.Status(lastStartMs = now - 60_000, lastEndMs = now - 30_000, lastFound = 2, lastAlerts = 1)
        // SampleScan.NOW is 05:20 UTC.
        assertEquals("Next at 5:29 AM · last found 2 (1 new) · alerts at 3%+", AutoScanText.status(idle, s, now + 9 * 60_000L, now, zone = zone))
        assertEquals("Vigilant scan 40/300…", AutoScanText.status(AutoScanner.Status(running = true, step = "Vigilant scan"), s, null, now, ScanProgress("Novig prices", 40, 300), zone))
        assertEquals("Next scan soon · last found nothing to alert · alerts off", AutoScanText.status(idle.copy(lastFound = 0), s.copy(alertMinEv = 0.0), null, now, zone = zone))
        // Scans under a minute apart show the seconds of the next one.
        // Faster than 9 minutes the service keeps the CPU awake, and the note says so; with the switch off, or at 9 minutes and slower, it doesn't.
        assertEquals("Next at 5:20:15 AM · last found 2 (1 new) · alerts at 3%+ · stays awake", AutoScanText.status(idle, s.copy(autoScanSeconds = 15), now + 15_000L, now, zone = zone))
        assertEquals("Next at 5:21 AM · last found 2 (1 new) · alerts at 3%+ · stays awake", AutoScanText.status(idle, s.copy(autoScanSeconds = 60), now + 60_000L, now, zone = zone))
        assertEquals("Next at 5:21 AM · last found 2 (1 new) · alerts at 3%+", AutoScanText.status(idle, s.copy(autoScanSeconds = 60, autoScanKeepAwake = false), now + 60_000L, now, zone = zone))
        assertEquals("Next at 5:29 AM · last found 2 (1 new) · alerts at 3%+", AutoScanText.status(idle, s.copy(autoScanSeconds = 540), now + 9 * 60_000L, now, zone = zone))
    }

    @Test
    fun `each scan's alarm is set for its time, and switching off cancels it`() {
        val am = context.getSystemService(AlarmManager::class.java)
        AutoScanAlarm.set(context, now + 10 * 60_000L)
        val alarm = shadowOf(am).nextScheduledAlarm!!
        assertEquals(now + 10 * 60_000L, alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(now + 10 * 60_000L, AutoScanAlarm.nextAtMs)
        AutoScanAlarm.cancel(context)
        assertNull(shadowOf(am).nextScheduledAlarm)
        assertNull(AutoScanAlarm.nextAtMs)
    }

    @Test
    fun `the manifest has the service, its alarm and restart receiver, and their permissions`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:name=".AutoScanService""""))
        assertTrue(manifest.contains("""android:foregroundServiceType="specialUse""""))
        assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertTrue(manifest.contains("""android:name=".AutoScanReceiver""""))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        for (p in listOf("FOREGROUND_SERVICE_SPECIAL_USE", "USE_EXACT_ALARM", "SCHEDULE_EXACT_ALARM", "RECEIVE_BOOT_COMPLETED", "POST_NOTIFICATIONS")) {
            assertTrue(p, manifest.contains("android.permission.$p"))
        }
    }

    // ---- sending: each bet once, even from two scans ending together --------------------------------

    @Test
    fun `two scans ending at once alert each bet once`() = kotlinx.coroutines.runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val app = context as VigilantApp
        val scanner = AutoScanner(app, app.container, clock = { now })
        val result = SampleScan.state().result
        val settings = SampleScan.settings.copy(alertMinEv = 0.02)
        // This test's app has no tracked or placed bets (SampleScan's state has some, which hide one).
        val bare = SampleScan.state().copy(bets = emptyList(), placed = emptyList(), settings = settings).indexed(now)
        val expected = AlertPicks.vigilant(bare, 0.02, now).take(AutoScanner.MAX_ALERTS).size
        assertTrue(expected > 0)
        // A background cycle that waited on Tj's own scan, and that scan's own end, send at the same moment.
        val sent = (1..2).map { async(kotlinx.coroutines.Dispatchers.Default) { scanner.afterScan(result, settings) } }.map { it.await() }
        assertEquals(expected, sent.sum())
        // And nothing again later.
        assertEquals(0, scanner.afterScan(result, settings))
    }

    @Test
    fun `turning auto-scan on asks for notifications once, not every time Vigilant opens`() {
        val source = File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        assertTrue(source.contains("!prefs.getBoolean(ASKED_NOTIFICATIONS_AUTO, false)"))
        assertTrue(source.contains("prefs.edit().putBoolean(ASKED_NOTIFICATIONS_AUTO, false).apply()"))
    }

    /**
     * v0.19.0's Novig live feed (RESEARCH.md §27) never outlives its use off screen: a background auto-scan
     * closes it when it ends, and leaving Vigilant with no scan running closes it at once (source pins:
     * lifecycle callbacks have no unit-test harness here).
     */
    @Test
    fun `Novig's live feed is closed off screen, not left pushing for two minutes`() {
        val activity = File("src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt").readText()
        assertTrue(activity.contains("if (!container.runner.running) container.novig.stream?.close()"))
        val app = File("src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt").readText()
        assertTrue(app.contains("if (!onScreen) novig.stream?.close()"))
        // Built only with the key, replaced (the old one closed) when the key changes.
        assertTrue(app.contains("novig.stream?.close()\n        novig.keyed = signer\n        novig.stream = signer?.let { NovigStream(http, it, appScope) }"))
    }
}
