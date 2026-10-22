package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.live.KalshiLiveData
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.sharpapi.SharpApiSplits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The API audit's research journals (AF6/AF7/AF9): each reader parses the provider's documented shape. */
class AuditFeedsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `kalshi milestones name the sports games and live data keeps each game's details whole`() {
        val ms = json.parseToJsonElement("""{"milestones":[
            {"id":"m1","category":"Sports","type":"basketball_game","title":"Celtics at Knicks","start_date":"2026-10-22T23:30:00Z","end_date":null,"details":{"competition":"Pro Basketball (M)"},"related_event_tickers":[],"primary_event_tickers":[],"notification_message":"","last_updated_ts":1},
            {"id":"m2","category":"Elections","type":"political_race","title":"x","start_date":"2026-10-22T00:00:00Z","details":{},"related_event_tickers":[],"primary_event_tickers":[],"notification_message":"","last_updated_ts":1}],"cursor":""}""").jsonObject
        val games = KalshiLiveData.parseMilestones(ms)
        assertEquals(listOf("m1"), games.map { it.id })
        assertEquals("Pro Basketball (M)", games.single().competition)
        val live = json.parseToJsonElement("""{"live_datas":[{"type":"basketball_game","milestone_id":"m1","details":{"status":"live","home_points":54,"away_points":49,"period":3,"live_data_delayed":false}}]}""").jsonObject
        val row = KalshiLiveData.parseLive(live, games.associateBy { it.id }, 7L).single()
        assertEquals("live", row.status)
        assertEquals("Celtics at Knicks", row.title)
        assertTrue(row.details.contains("\"home_points\":54"))
        assertEquals(false, row.delayed)
    }

    @Test
    fun `sharpapi splits keep each source's row whole`() {
        val body = json.parseToJsonElement("""{"data":[{"event_id":"nba_celtics_knicks_2026-10-22","league":"nba","sportsbook":"circa","home_team":"New York Knicks","away_team":"Boston Celtics",
            "moneyline":{"away_odds":-120,"home_odds":100,"handle_pct":{"away":0.7,"home":0.3},"bets_pct":{"away":0.4,"home":0.6}},"fetched_at":"2026-10-22T22:00:00Z"}],
            "pagination":{"limit":200,"offset":0,"count":1,"total":1,"has_more":false,"next_offset":null}}""").jsonObject
        val r = SharpApiSplits.parse(body, 9L).single()
        assertEquals("circa", r.book)
        assertEquals("nba", r.league)
        assertTrue(r.raw.contains("handle_pct"))
    }

    @Test
    fun `novig's close is the last trade before the start, from the other side's series when this side has none`() {
        val body = """{"marketId":"mk","interval":"1m","series":[
            {"outcomeId":"A","candles":[{"t":1000,"o":"0.50","h":"0.52","l":"0.49","c":"0.51","volume":10},{"t":2000,"o":"0.51","h":"0.56","l":"0.51","c":"0.55","volume":30},{"t":3000,"o":"0.60","h":"0.60","l":"0.60","c":"0.60","volume":5}]},
            {"outcomeId":"B","candles":[]}]}"""
        val series = NovigPublicClient.parseCandles(body, json)!!
        val (a, flippedA) = NovigCloses.close(series, "A", startsTs = 2500)
        assertEquals(0.55, a!!.c, 1e-9)
        assertEquals(false, flippedA)
        val (b, flippedB) = NovigCloses.close(series, "B", startsTs = 2500)
        assertEquals(0.45, b!!.c, 1e-9)
        assertTrue(flippedB)
        assertNull(NovigCloses.close(series, "A", startsTs = 500).first)
        assertNull(NovigPublicClient.parseCandles("not json", json))
    }
}
