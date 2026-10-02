package com.tjshea.vigilant.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.alerts.EvAlert
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tj, 2026-10-02 21:51Z: "always include my vigilant wallet current balance in all vigilant notifications whether push or silent, so I can always quickly
 * see how much is in the wallet".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WalletNotificationsTest {

    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()
    private val now = 1_800_000_000_000L

    @Test
    fun `the wallet line - the balance, how old once it's a while, and why there's none`() {
        assertEquals("Wallet \$25.40", WalletNote.line(WalletBalance.Reading(25.4, now - 20_000), setUp = true, now = now))
        assertEquals("Wallet \$25.40 (5m ago)", WalletNote.line(WalletBalance.Reading(25.4, now - 5 * 60_000), setUp = true, now = now))
        assertEquals("Wallet: not read yet", WalletNote.line(null, setUp = true, now = now))
        assertEquals("Wallet: betting not set up", WalletNote.line(null, setUp = false, now = now))
    }

    @Test
    fun `one reading for the app - a young one is reused, an old one is read again, every read elsewhere counts, and Novig not answering keeps the last`() = runBlocking {
        var t = now
        var reads = 0
        var answer: Double? = 12.0
        val w = WalletBalance(read = { reads++; answer ?: throw java.io.IOException("down") }, setUp = { true }, prefs = null, clock = { t })
        assertEquals(12.0, w.fresh()!!.dollars, 0.0)
        t += 10_000
        assertEquals(12.0, w.fresh()!!.dollars, 0.0)
        assertEquals(1, reads)
        // The auto-bet's own read before a pass (or the Bet sheet's) updates it: no extra request.
        w.record(9.5)
        assertEquals(9.5, w.fresh()!!.dollars, 0.0)
        assertEquals(1, reads)
        t += WalletBalance.FRESH_MS
        answer = 8.0
        assertEquals(8.0, w.fresh()!!.dollars, 0.0)
        assertEquals(2, reads)
        t += WalletBalance.FRESH_MS
        answer = null
        assertEquals(8.0, w.fresh()!!.dollars, 0.0)
        // An older answer arriving late never replaces a newer one.
        w.record(1.0, at = t - 3_600_000)
        assertEquals(8.0, w.last!!.dollars, 0.0)
    }

    @Test
    fun `the reading is kept across restarts, so a worker in a new process still shows it`() {
        val prefs = app.getSharedPreferences("wallet-test", android.content.Context.MODE_PRIVATE)
        WalletBalance(read = { null }, setUp = { true }, prefs = prefs, clock = { now }).record(31.25)
        val again = WalletBalance(read = { null }, setUp = { true }, prefs = prefs, clock = { now })
        assertEquals(WalletBalance.Reading(31.25, now), again.last)
    }

    @Test
    fun `a push alert and a silent one both show the wallet in their header`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.container.wallet.record(42.17)
        val alert = EvAlert(
            key = "k", bet = "Team A", event = "B @ A", market = "Moneyline", league = "NFL", american = 120, ev = 0.04, startsAtMs = System.currentTimeMillis() + 3_600_000,
        )
        assertEquals(1, EvAlerts.post(app, listOf(alert)))
        AutoBetNotes.stopped(app, "Auto-bet stopped", "Novig refused an order")
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        assertTrue(posted.size >= 2)
        for (n in posted) assertEquals("Wallet \$42.17", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
    }

    @Test
    fun `no notification in the app is built without the wallet line`() {
        // Every NotificationCompat.Builder in the app's sources sets it before build(): a new notification can't forget it.
        val dir = File("src/main/kotlin/com/tjshea/vigilant/app").takeIf { it.isDirectory } ?: File("app/src/main/kotlin/com/tjshea/vigilant/app")
        val builders = dir.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            Regex("""NotificationCompat\.Builder\(""").findAll(text).map { m ->
                val end = text.indexOf(".build()", m.range.first)
                f.name to (end > 0 && text.substring(m.range.first, end).contains(".withWallet("))
            }
        }.toList()
        assertTrue("found ${builders.size} builders", builders.size >= 11)
        assertEquals(emptyList<String>(), builders.filterNot { it.second }.map { it.first })
    }
}
