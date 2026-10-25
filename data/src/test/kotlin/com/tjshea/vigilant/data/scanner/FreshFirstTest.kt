package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/** PB20 (Tj, 2026-10-25): "If the odds are very stale, do not use them at all for the fair odds calculation"; one slow book must not date (or bend) a line fresh books price. */
class FreshFirstTest {
    private val now = 1_800_000_000_000L
    private data class B(val key: String, val ageS: Long?, val sharp: Boolean = false)
    private fun pick(vararg b: B) = Freshness.freshFirst(b.toList(), now, { x -> x.ageS?.let { now - it * 1000 } }, { it.sharp }).map { it.key }

    @Test fun `a stale book is left out when two fresh ones price the line`() {
        assertEquals(listOf("fd", "dk", "nostamp"), pick(B("fd", 60), B("dk", 90), B("hardrock", 426), B("nostamp", null)))
    }

    @Test fun `with fewer than two fresh books nothing is dropped`() {
        assertEquals(listOf("fd", "hardrock"), pick(B("fd", 60), B("hardrock", 426)))
    }

    @Test fun `a stale sharp book stays while no other sharp book is fresh, and goes when one is`() {
        assertEquals(listOf("fd", "dk", "pinnacle"), pick(B("fd", 60), B("dk", 90), B("pinnacle", 300, sharp = true)))
        assertEquals(listOf("fd", "dk", "circa"), pick(B("fd", 60), B("dk", 90), B("pinnacle", 300, sharp = true), B("circa", 30, sharp = true)))
    }

    /** PX (2026-10-25): on the engine (the list re-priced every 3 s) a book is left out beside fresh ones only past the plain 5-minute limit, and a scan keeps no 2-minute headroom. */
    @Test fun `on the engine a 4-minute-old book stays beside fresh ones`() {
        try {
            Freshness.continuous = true
            assertEquals(listOf("fd", "dk", "hardrock"), pick(B("fd", 60), B("dk", 90), B("hardrock", 240)))
            assertEquals(listOf("fd", "dk"), pick(B("fd", 60), B("dk", 90), B("bovada", 330)))
            assertEquals(0L, Freshness.MIN_SHOWN_MS)
        } finally {
            Freshness.continuous = false
        }
    }
}
