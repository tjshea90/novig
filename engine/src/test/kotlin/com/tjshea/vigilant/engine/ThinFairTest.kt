package com.tjshea.vigilant.engine

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThinFairTest {
    private fun line(vararg books: Pair<String, Double>, sharp: Set<String> = emptySet()): FairLine {
        val prices = books.map { (k, p) -> BookPrices(k, k, listOf(1 / p, 1 / (1 - p) )) }
        return FairValue.compute(prices, FairSettings(source = FairSource.MARKET_AVERAGE, sharpBooks = sharp, method = DevigMethod.MULTIPLICATIVE))!!
    }

    @Test fun twoSmallBooksAreBlocked() = assertNotNull(ThinFair.reason(line("bet105" to 0.55, "lowvigx" to 0.55)))
    @Test fun smallIdenticalPlusOneLargeIsBlocked() = assertNotNull(ThinFair.reason(line("draftkings" to 0.55, "betx" to 0.55)))
    @Test fun twoLargeBrandsPass() = assertNull(ThinFair.reason(line("draftkings" to 0.55, "fanduel" to 0.56)))
    @Test fun twoLargeIdenticalStillPass() = assertNull(ThinFair.reason(line("draftkings" to 0.55, "fanduel" to 0.55)))
    @Test fun sharpBookPasses() = assertNull(ThinFair.reason(line("pinnacle" to 0.55, "smallx" to 0.55, sharp = setOf("pinnacle"))))
    @Test fun manySmallCompaniesPass() = assertNull(ThinFair.reason(line("a1" to 0.55, "a2" to 0.56, "a3" to 0.55, "a4" to 0.54)))
    @Test fun noFairIsNoOpinion() = assertNull(ThinFair.reason(null))
}
