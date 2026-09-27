package com.tjshea.vigilant.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a tap on a BetMGM bet goes (BetMGM's documented deep links; its sites are per state). */
class BetMgmLinksTest {

    @Test
    fun `fixture, market and option open the bet slip in the picked state`() {
        val link = BetMgmLinks.link(BookRef("17345678", "888-1002", null), "PA")
        assertEquals("https://sports.pa.betmgm.com/en/sports?options=17345678-888-1002&type=Single", link.url)
        assertTrue(link.exact)
        // A feed that sends the whole triple, or a prefixed fixture id, says the same thing.
        assertEquals(link.url, BetMgmLinks.link(BookRef("2:17345678", "17345678-888-1002", null), "pa").url)
        assertEquals(link.url, BetMgmLinks.link(BookRef("17345678", "888:1002", null), "pa").url)
    }

    @Test
    fun `an option id alone can't make a bet slip, so the game opens`() {
        val link = BetMgmLinks.link(BookRef("17345678", "1002", null), "nj")
        assertEquals("https://sports.nj.betmgm.com/en/sports/events/17345678", link.url)
        assertFalse(link.exact)
        // A triple for another game is never trusted.
        assertFalse(BetMgmLinks.link(BookRef("17345678", "99999999-888-1002", null), "nj").exact)
    }

    @Test
    fun `the feed's game page is used, its state placeholder filled in`() {
        val page = "https://sports.{state}.betmgm.com/en/sports/events/ravens-at-cowboys-17345678"
        assertEquals(
            "https://sports.mi.betmgm.com/en/sports/events/ravens-at-cowboys-17345678",
            BetMgmLinks.link(BookRef(null, null, page), "mi").url,
        )
        // No state picked yet: a page that needs one can't open, BetMGM's home can.
        assertEquals(BetMgmLinks.HOME, BetMgmLinks.link(BookRef(null, null, page), "").url)
        // Only BetMGM's own https pages.
        assertEquals("https://sports.mi.betmgm.com/en/sports", BetMgmLinks.link(BookRef(null, null, "https://evil.example.com/betmgm.com"), "mi").url)
    }

    @Test
    fun `without a state the bet slip can't be built`() {
        val link = BetMgmLinks.link(BookRef("17345678", "888-1002", null), "")
        assertEquals(BetMgmLinks.HOME, link.url)
        assertFalse(link.exact)
        assertEquals(BetMgmLinks.HOME, BetMgmLinks.link(null, "not a state").url)
    }
}
