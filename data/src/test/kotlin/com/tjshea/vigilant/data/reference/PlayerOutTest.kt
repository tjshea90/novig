package com.tjshea.vigilant.data.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bet or a bid on a player who isn't playing (Tj, 2026-10-07, a WNBA screenshot: "it says Allisha is out for the game ... yet the auto bid feature
 * offered bids on her"). Only a report that says he is certainly out blocks; doubtful, questionable, active and unknown never do.
 */
class PlayerOutTest {

    private val now = 1_790_000_000_000L
    private val wnba = "basketball_wnba"
    private val game = listOf("New York Liberty", "Atlanta Dream")

    private fun book(vararg injuries: Injury): InjuryBook = InjuryIndex { now }.apply { record(wnba, injuries.toList()) }.book.value

    private fun want(player: String, teams: List<String> = game, sport: String = wnba) = InjuryTags.Want("k", sport, player, teams)

    @Test
    fun `only a report that says he is certainly not playing blocks`() {
        for (status in listOf("Out", "Injured Reserve", "10-Day IL", "60-Day-IL", "Suspended", "Inactive")) {
            val reason = PlayerOut.reasonOf(Injury("Allisha Gray", status))
            assertNotNull(status, reason)
            assertTrue(reason!!, reason.startsWith("The player is out ("))
        }
        // Doubtful, questionable, day-to-day, active and no report at all are not blocks: an unknown is not a block.
        for (status in listOf("Doubtful", "Questionable", "Day-To-Day", "Active", "Probable")) assertNull(status, PlayerOut.reasonOf(Injury("Allisha Gray", status)))
        assertNull(PlayerOut.reasonOf(null))
        assertNull(PlayerOut.forTag(null))
        // The reason names the tag, so the reports count one reason a kind of report, not one a player.
        assertTrue(PlayerOut.reasonOf(Injury("A", "Out"))!!.contains("(OUT)"))
        assertTrue(PlayerOut.reasonOf(Injury("A", "Injured Reserve"))!!.contains("(IR)"))
    }

    @Test
    fun `the report is found by the player, his sport and the game's teams`() {
        val b = book(Injury("Allisha Gray", "Out", team = "Atlanta Dream", teamAbbr = "ATL"), Injury("Jonquel Jones", "Questionable", team = "New York Liberty"))
        assertTrue(PlayerOut.reason(b, want("Allisha Gray"), now)!!.contains("(OUT)"))
        // A looser spelling of the same name is the same player (PlayerNames.same).
        assertNotNull(PlayerOut.reason(b, want("Allisha  Gray"), now))
        // Questionable is never a block.
        assertNull(PlayerOut.reason(b, want("Jonquel Jones"), now))
        // A player no report names, and a sport the reports don't cover, are not blocks.
        assertNull(PlayerOut.reason(b, want("Breanna Stewart"), now))
        assertNull(PlayerOut.reason(b, want("Allisha Gray", sport = "basketball_nba"), now))
        assertNull(PlayerOut.reason(b, null, now))
    }

    @Test
    fun `a report naming another team is someone else of the same name, and an old report does not hold`() {
        val b = book(Injury("Allisha Gray", "Out", team = "Atlanta Dream"))
        // The same name in a game between two other teams: not him.
        assertNull(PlayerOut.reason(b, want("Allisha Gray", teams = listOf("Seattle Storm", "Las Vegas Aces")), now))
        // A report seen longer ago than InjuryIndex.KEEP_MS is gone (statuses change; every scan renews them).
        assertNull(PlayerOut.reason(b, want("Allisha Gray"), now + InjuryIndex.KEEP_MS + 1))
        assertEquals(true, PlayerOut.reason(b, want("Allisha Gray"), now + InjuryIndex.KEEP_MS - 1)?.contains("(OUT)"))
    }
}
