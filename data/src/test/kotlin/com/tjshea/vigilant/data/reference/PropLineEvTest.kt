package com.tjshea.vigilant.data.reference

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PropLine's `/ev` answer (the real shape, saved 2026-10-16 in research/propline/samples_2026-10-16/ev_novig.json) read into the +EV-at-Novig rows. */
class PropLineEvTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val start = 1_792_191_600_000L

    private fun answer(vararg novig: String) = """
        {"id":"369360","sport_key":"basketball_nba","home_team":"Philadelphia 76ers","away_team":"Boston Celtics","commence_time":"2026-10-16T23:00:00Z",
         "devig_method":"multiplicative","redacted":false,"upgrade_url":null,
         "lines":[
          {"market_key":"h2h","description":"","point":null,"fair_source":"pinnacle","fair_probs":{"Boston Celtics":0.3836,"Philadelphia 76ers":0.6164},"fair_books":null,
           "outcomes":[{"book":"novig","book_title":"Novig","name":"Philadelphia 76ers","price":-160,"ev_pct":0.16,"is_plus_ev":true,"point":null,"last_update":"2026-10-16T19:23:51.287352Z"},
                       {"book":"novig","book_title":"Novig","name":"Boston Celtics","price":156,"ev_pct":-1.79,"is_plus_ev":false,"point":null,"last_update":"2026-10-16T19:23:51.287352Z"}]},
          {"market_key":"player_points","description":"Tyrese Maxey","point":24.5,"fair_source":"pinnacle","fair_probs":{"Over":0.51,"Under":0.49},"fair_books":null,
           "outcomes":[${novig.joinToString(",")}]}
         ]}
    """.trimIndent()

    private fun parse(raw: String) = PropLineEv.parse(raw, json, "basketball_nba", "NBA", "369360", "Philadelphia 76ers", "Boston Celtics", start, start - 3_600_000L)

    @Test
    fun onlyPlusEvLinesAtNovigAboveTheFloorBecomeRows() {
        val p = parse(answer(
            """{"book":"novig","name":"Over","price":-105,"ev_pct":3.2,"is_plus_ev":true,"point":24.5,"last_update":"2026-10-16T19:20:00Z"}""",
            """{"book":"novig","name":"Under","price":-120,"ev_pct":-4.0,"is_plus_ev":false,"point":24.5,"last_update":"2026-10-16T19:20:00Z"}""",
            """{"book":"draftkings","name":"Over","price":110,"ev_pct":9.0,"is_plus_ev":true,"point":24.5,"last_update":"2026-10-16T19:20:00Z"}""",
        ))
        assertEquals(false, p.redacted)
        assertEquals(1, p.rows.size)
        val r = p.rows.single()
        assertEquals("player_points", r.market); assertEquals("Tyrese Maxey", r.player); assertEquals("Over", r.outcome)
        assertEquals(-105, r.price); assertEquals(24.5, r.point!!, 0.0); assertEquals(3.2, r.evPct, 0.0); assertEquals(0.51, r.fairProb, 0.0); assertEquals("pinnacle", r.fairSource)
        assertEquals("NBA", r.league); assertEquals(start, r.startsAtMs)
    }

    @Test
    fun aHugeEdgeIsTreatedAsAStaleLineAndLeftOut() {
        assertTrue(parse(answer("""{"book":"novig","name":"Over","price":300,"ev_pct":55.0,"is_plus_ev":true,"point":24.5,"last_update":"2026-10-16T19:20:00Z"}""")).rows.isEmpty())
    }

    @Test
    fun aFreeKeysRedactedAnswerIsSaidNotRead() {
        val p = parse("""{"id":"1","lines":[],"redacted":true,"upgrade_url":"https://prop-line.com/pricing"}""")
        assertTrue(p.redacted); assertTrue(p.rows.isEmpty())
    }

    @Test
    fun theFeedSurvivesItsOwnFile() {
        val feed = PlEvFeed(123L, parse(answer("""{"book":"novig","name":"Over","price":-105,"ev_pct":3.2,"is_plus_ev":true,"point":24.5,"last_update":"2026-10-16T19:20:00Z"}""")).rows, games = 4, requests = 5, tier = "pro", note = "x")
        val back = PlEvFeed.decode(PlEvFeed.encode(feed))
        assertNotNull(back); assertEquals(feed, back)
        assertEquals(null, PlEvFeed.decode(ByteArray(10)))
    }
}
