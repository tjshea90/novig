package com.tjshea.vigilant.data.alerts

import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tj, 2026-09-28: an alert for each new +EV bet: never the same bet twice, from either scanner. */
class AlertLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000_000L

    private fun log(file: File = File(tmp.root, "alerts.json")) =
        AlertLog(JsonFileStore(file, AlertBook.serializer(), { AlertBook() }), clock = { now })

    private fun alert(key: String, ev: Double = 0.04, outcome: String? = null, scanner: String = "CNO", starts: Long? = now + 3_600_000) =
        EvAlert(scanner, key, outcome, "Bet $key", "Moneyline", "A @ B", 110, ev, 5, 4, starts, link = null, exact = false)

    @Test
    fun `a bet alerts once, through a restart`() = runTest {
        val file = File(tmp.root, "alerts.json")
        val first = log(file)
        val a = alert("k1")
        assertEquals(listOf(a), first.unseen(listOf(a)))
        first.record(listOf(a))
        assertTrue(first.unseen(listOf(a)).isEmpty())
        // A new process reads the same file.
        assertTrue(log(file).unseen(listOf(a)).isEmpty())
        // Another bet still alerts.
        assertEquals(listOf("k2"), log(file).unseen(listOf(a, alert("k2"))).map { it.key })
    }

    @Test
    fun `the same Novig outcome from both scanners is one bet, the better EV kept`() = runTest {
        val l = log()
        val cno = alert("cno:row", ev = 0.035, outcome = "o1", scanner = "CNO")
        val vig = alert("m1/o1", ev = 0.041, outcome = "o1", scanner = "Vigilant")
        assertEquals(listOf(vig), l.unseen(listOf(cno, vig)))
        l.record(listOf(vig))
        assertTrue(l.unseen(listOf(cno)).isEmpty())
        // Without an outcome id, each scanner's own key.
        assertEquals("CNO:cno:row", alert("cno:row").dedupeKey)
    }

    @Test
    fun `entries drop off twelve hours after the game starts`() = runTest {
        val l = log()
        val a = alert("k1", starts = now + 60_000)
        l.record(listOf(a))
        now += 60_000 + AlertLog.KEEP_AFTER_START_MS + 1
        // Recording anything prunes; the old bet is forgotten (its game is long over).
        l.record(listOf(alert("k2")))
        assertEquals(listOf("k1"), l.unseen(listOf(a)).map { it.key })
        assertTrue(AlertLog.expired(AlertedBet("x", atMs = 0, startsAtMs = null), AlertLog.KEEP_WITHOUT_START_MS + 1))
    }
}
