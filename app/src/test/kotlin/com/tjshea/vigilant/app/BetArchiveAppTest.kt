package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjshea.vigilant.data.tracker.BetHistory
import com.tjshea.vigilant.data.tracker.BetHistoryExport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tj, 2026-10-11: each day's bets and bids are saved on their own, and every bet and bid can be shared with Claude as files that can't grow too big.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BetArchiveAppTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the app saves a file for each day of bets and says so`() = runBlocking {
        val c = app.container
        c.tracker.all().forEach { c.tracker.delete(it.id) }
        c.tracker.addAll(SampleScan.state().bets)
        val note = c.syncBetArchive(full = true)
        assertNotNull(note)
        val days = SampleScan.state().bets.map { BetHistory.day(it.createdAtMs) }.toSet()
        days.forEach { assertTrue("no file for $it", c.betArchive.betFile(it).exists()) }
        assertTrue(note!!, note.contains("day files"))
        assertEquals(note, c.betArchiveNote)
        // Run again: nothing changed, nothing rewritten.
        val before = days.associateWith { c.betArchive.betFile(it).lastModified() }
        c.syncBetArchive(full = true)
        days.forEach { assertEquals(before[it], c.betArchive.betFile(it).lastModified()) }
    }

    @Test
    fun `the share intent carries every file with read permission for just those files`() {
        val dir = BetShare.dir(app).apply { deleteRecursively(); mkdirs() }
        val bets = SampleScan.state().bets
        val r = BetHistoryExport.write(dir, "vigilant-bets-test", bets, emptyList(), emptySequence(), System.currentTimeMillis())
        val chooser = BetShare.intent(app, r.files, "0.0.0")
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals(android.content.Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals(r.files.size, send.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!.size)
        assertTrue(send.getStringExtra(android.content.Intent.EXTRA_TEXT)!!.contains("README"))
    }
}
