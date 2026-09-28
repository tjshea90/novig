package com.tjshea.vigilant.data.scanner

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Scratch timing probe (VIGILANT_PROBE=1): CPU the pump spends between Novig reads on a 1,200-price scan. */
class PumpCostProbe {
    @Test
    fun probe() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_PROBE") == "1")
        val t = BiggerScansTest()
        t.probeScan(1300, 1200)
        t.probeScan(1300, 1200)
        t.probeScan(1300, 400)
    }
}
