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

    @Test
    fun `auto-scan is off by default, every 5 to 40 minutes, alerts at 2, 3 or 4 percent (3 by default)`() {
        val s = ScanSettings()
        assertEquals(AutoScanMode.OFF, s.autoScan)
        assertEquals(listOf(5, 10, 20, 30, 40), ScanSettings.AUTO_SCAN_MINUTES_CHOICES)
        assertTrue(s.autoScanMinutes in ScanSettings.AUTO_SCAN_MINUTES_CHOICES)
        assertEquals(listOf(0.0, 0.02, 0.03, 0.04), ScanSettings.ALERT_MIN_EV_CHOICES)
        assertEquals(0.03, s.alertMinEv, 0.0)
        assertEquals(listOf("Off", "CNO", "CNO + Vigilant"), AutoScanMode.entries.map { it.displayName })
        assertTrue(AutoScanMode.CNO.cno && !AutoScanMode.CNO.vigilant)
        assertTrue(AutoScanMode.BOTH.cno && AutoScanMode.BOTH.vigilant)
        assertFalse(AutoScanMode.OFF.cno || AutoScanMode.OFF.vigilant)
        assertEquals(listOf("Off", "2%+", "3%+", "4%+"), ScanSettings.ALERT_MIN_EV_CHOICES.map { alertLabel(it) })
    }

    @Test
    fun `the next scan is due its interval after the last one started, never sooner than 30 s`() {
        assertEquals(now, AutoScanClock.nextAtMs(null, 10, now))
        assertEquals(now + 10 * 60_000L, AutoScanClock.nextAtMs(now, 10, now + 1_000))
        // A scan that ran past its interval: the next one waits a moment rather than running back to back.
        assertEquals(now + 6 * 60_000L + AutoScanClock.MIN_GAP_MS, AutoScanClock.nextAtMs(now, 5, now + 6 * 60_000L))
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
        assertTrue(EvAlerts.detail(alert(exact = false)).contains("opens the game"))
    }

    private fun installNovig() {
        val main = ComponentName(MiniWindow.NOVIG_PACKAGE, "${MiniWindow.NOVIG_PACKAGE}.MainActivity")
        val pm = shadowOf(context.packageManager)
        pm.addActivityIfNotPresent(main)
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test
    fun `tapping it opens the bet in Novig's app, or Novig's site when the app isn't installed`() {
        // Not installed: Novig's site (a novigapp:// link would open nothing).
        val web = EvAlerts.intent(context, alert())
        assertEquals(Uri.parse("https://novig.com"), web.data)
        installNovig()
        val app = EvAlerts.intent(context, alert())
        assertEquals(Intent.ACTION_VIEW, app.action)
        assertEquals(Uri.parse("novigapp://events/o1"), app.data)
        assertEquals(MiniWindow.NOVIG_PACKAGE, app.`package`)
        // No link at all: Novig itself.
        assertEquals(MiniWindow.NOVIG_PACKAGE, EvAlerts.intent(context, alert(link = null)).`package`)
    }

    @Test
    fun `alerts post as high-importance notifications that open the bet, once notifications are allowed`() {
        installNovig()
        val nm = context.getSystemService(NotificationManager::class.java)
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(0, EvAlerts.post(context, listOf(alert()), now))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(1, EvAlerts.post(context, listOf(alert()), now))
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(EvAlerts.CHANNEL, n.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel(EvAlerts.CHANNEL).importance)
        val tap = shadowOf(n.contentIntent).savedIntent
        assertEquals(Uri.parse("novigapp://events/o1"), tap.data)
        assertEquals(MiniWindow.NOVIG_PACKAGE, tap.`package`)
        assertTrue(shadowOf(n.contentIntent).isActivityIntent)
    }

    // ---- the service's notification and alarm ------------------------------------------------------

    @Test
    fun `the ongoing notification says what runs, when next, and what the last scan found`() {
        val s = ScanSettings(autoScan = AutoScanMode.BOTH, autoScanMinutes = 10)
        val zone = TimeZone.getTimeZone("UTC")
        assertEquals("Auto-scan: CNO + Vigilant every 10 min", AutoScanText.title(s))
        val idle = AutoScanner.Status(lastStartMs = now - 60_000, lastEndMs = now - 30_000, lastFound = 2, lastAlerts = 1)
        // SampleScan.NOW is 05:20 UTC.
        assertEquals("Next at 5:29 AM · last found 2 (1 new) · alerts at 3%+", AutoScanText.status(idle, s, now + 9 * 60_000L, now, zone = zone))
        assertEquals("Vigilant scan 40/300…", AutoScanText.status(AutoScanner.Status(running = true, step = "Vigilant scan"), s, null, now, ScanProgress("Novig prices", 40, 300), zone))
        assertEquals("Next scan soon · last found nothing to alert · alerts off", AutoScanText.status(idle.copy(lastFound = 0), s.copy(alertMinEv = 0.0), null, now, zone = zone))
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
}
