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
    fun `does not match when home and away are swapped`() {
        assertFalse(EventMatcher.matches("Alabama", "South Carolina", "South Carolina", "Alabama"))
    }

    @Test
    fun `does not match different teams`() {
        assertFalse(EventMatcher.matches("Alabama", "South Carolina", "Alabama", "Georgia"))
    }
}
