package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class FairProvenanceTest {
    @Test fun `feed ids read as the API names people know`() {
        assertEquals("SGO", FairProvenance.apiName("sgo-props"))
        assertEquals("PropLine", FairProvenance.apiName("propline_props"))
        assertEquals("ParlayAPI", FairProvenance.apiName("parlay_1h"))
        assertEquals("BoltOdds", FairProvenance.apiName("bolt"))
        assertEquals("feed", FairProvenance.apiName(null))
    }
}
