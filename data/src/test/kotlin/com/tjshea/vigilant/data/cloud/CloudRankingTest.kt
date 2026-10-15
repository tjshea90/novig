package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.SourceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRankingTest {
    private val now = 1_000_000_000_000L
    private fun m(id: String, p90: Long?, pts: Double?, lines: Int, at: Long) = SourceScore.Measure(id, 500, p90, pts, lines, at)

    @Test
    fun `the measured ranking survives the handover whole, and the order it gives is the order it had`() {
        val measures = listOf(m("sgo", 20_000L, 0.4, 120, now - 60_000), m("propline", null, null, 0, now - 5 * 60_000L), m("bolt", 3_000L, 1.1, 60, now - 1_000)).associateBy { it.id }
        val back = CloudRanking.decode(CloudRanking.encode(measures), now)
        assertEquals(measures, back)
        val prior = listOf("propline", "sgo", "oddspapi")
        assertEquals(SourceScore.rank(prior, measures), SourceScore.rank(prior, back))
    }

    @Test
    fun `a measurement more than a day old, and anything unreadable, is left out - the shipped order stands`() {
        val old = m("parlay", 500_000L, 5.0, 100, now - CloudRanking.KEEP_MS - 1)
        val fresh = m("sgo", 10_000L, 0.3, 90, now - 1_000)
        val back = CloudRanking.decode(CloudRanking.encode(listOf(old, fresh).associateBy { it.id }), now)
        assertEquals(setOf("sgo"), back.keys)
        assertTrue(CloudRanking.decode("not json", now).isEmpty())
        assertTrue(CloudRanking.decode("{}", now).isEmpty())
        assertTrue(CloudRanking.decode("""{"measures":[{"id":"x"}]}""", now).isEmpty())
    }
}
