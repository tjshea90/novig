package com.tjshea.vigilant.data.reference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shape follows PropLine's docs (llms-full 774-782); no real answer has been saved yet (the probe's `trends` call will), so the reader is tolerant: the first object with a last_5/last_10 window. */
class PropLineTrendsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val raw = """{"player_id":"mlb:1","markets":[{"market":"pitcher_strikeouts","reference_bookmaker":"fanduel","recent_line":5.5,"avg_actual":6.04,
        "last_5":{"over":4,"under":1,"push":0,"over_pct":0.8},"last_10":{"over":7,"under":3,"push":0,"over_pct":0.7},"last_20":null,"current_streak":{"result":"over","length":2}}]}"""

    @Test
    fun overAndUnderReadTheSameWindows() {
        assertEquals("Over hit last 5: 4 of 5 · last 10: 7 of 10 · averages 6.0 (recent line 5.5)", PropLineTrends.summarize(raw, json, "Over"))
        assertEquals("Under hit last 5: 1 of 5 · last 10: 3 of 10 · averages 6.0 (recent line 5.5)", PropLineTrends.summarize(raw, json, "Under"))
    }

    @Test
    fun anAnswerWithoutWindowsOrJunkIsBlank() {
        assertEquals("", PropLineTrends.summarize("{}", json, "Over"))
        assertEquals("", PropLineTrends.summarize("not json", json, "Over"))
    }
}
