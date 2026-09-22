package com.tjshea.vigilant.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMatcherTest {

    @Test
    fun `matches identical team name pairs`() {
        assertTrue(EventMatcher.matches("Alabama", "South Carolina", "Alabama", "South Carolina"))
    }

    @Test
    fun `is case and punctuation insensitive`() {
        assertTrue(EventMatcher.matches("St. Louis Cardinals", "chicago cubs", "St Louis Cardinals", "Chicago Cubs"))
    }

    @Test
    fun `still matches when home and away are swapped`() {
        // Order-independent on purpose (RESEARCH.md §4.4) — Novig's team names come from
        // moneyline outcomes in whatever order the API returns them, not a documented home/away
        // field, so two providers disagreeing on which side is "home" must not cause a miss.
        assertTrue(EventMatcher.matches("Alabama", "South Carolina", "South Carolina", "Alabama"))
    }

    @Test
    fun `does not match different teams`() {
        assertFalse(EventMatcher.matches("Alabama", "South Carolina", "Alabama", "Georgia"))
    }

    @Test
    fun `does not match a partial overlap with only one shared team`() {
        assertFalse(EventMatcher.matches("Alabama", "South Carolina", "South Carolina", "Georgia"))
    }
}
