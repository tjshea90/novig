package com.tjshea.vigilant.data.novig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovigLeaguesTest {

    @Test
    fun `maps a known sport key to its Novig league string`() {
        assertEquals("NFL", NovigLeagues.forSportKey("americanfootball_nfl"))
        assertEquals("WNBA", NovigLeagues.forSportKey("basketball_wnba"))
    }

    @Test
    fun `returns null for an unmapped sport rather than guessing`() {
        assertNull(NovigLeagues.forSportKey("soccer_epl"))
        assertNull(NovigLeagues.forSportKey("mma_mixed_martial_arts"))
        assertNull(NovigLeagues.forSportKey("not_a_real_sport"))
    }
}
