package com.tjshea.vigilant.data.scanner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedViewFilterTest {
    private fun c(two: Int, agree: Int) = Agreement.Count(two, agree)

    @Test fun offPassesEverything() {
        assertFalse(FeedViewFilter().active)
        assertTrue(FeedViewFilter().passes(c(0, 0)))
    }

    @Test fun minimumCountsAgreeingBooks() {
        val f = FeedViewFilter(minAgreeing = 3)
        assertFalse(f.passes(c(5, 2)))
        assertTrue(f.passes(c(5, 3)))
        assertTrue(f.passes(c(3, 3)))
    }

    @Test fun allMustAgreeNeedsEveryTwoSidedBookAndAtLeastOne() {
        val f = FeedViewFilter(allMustAgree = true)
        assertTrue(f.passes(c(4, 4)))
        assertFalse(f.passes(c(4, 3)))
        assertFalse(f.passes(c(0, 0)))
    }

    @Test fun bothTogether() {
        val f = FeedViewFilter(minAgreeing = 3, allMustAgree = true)
        assertFalse(f.passes(c(2, 2)))
        assertTrue(f.passes(c(3, 3)))
        assertEquals(true, f.active)
    }
}
