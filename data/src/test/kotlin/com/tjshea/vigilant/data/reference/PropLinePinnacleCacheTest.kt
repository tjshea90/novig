package com.tjshea.vigilant.data.reference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * PLS2 (Tj, 2026-11-17, "double check that the app and github engine are using the correct commands"): PropLine's docs of 2026-11-16 say Pinnacle caches its public feed for up to ~15 minutes, so a price PropLine
 * read a second ago can be 15 minutes old; `book_updated_at` is when that cached copy was made and `last_seen_at` only says when PropLine read it. A Pinnacle quote's age is the older of the two, and neither a
 * push nor a heartbeat may make it look fresher than that; every other book keeps `last_seen_at`.
 */
class PropLinePinnacleCacheTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()
    private val now = 1_790_500_000_000L

    private fun outcome(seen: Long, copied: Long?) =
        json.decodeFromString(PlOutcome.serializer(), """{"name":"Over","price":-110,"point":47.5,"last_seen_at":"${iso(seen)}","book_updated_at":${copied?.let { "\"${iso(it)}\"" } ?: "null"}}""")

    @Test fun aPinnacleQuoteIsAsOldAsItsCachedCopyNotAsItsRead() {
        val o = outcome(seen = now - 2_000L, copied = now - 11 * 60_000L)
        assertEquals(now - 11 * 60_000L, o.seenAt("pinnacle"))
        assertEquals("every other book keeps PropLine's read", now - 2_000L, o.seenAt("draftkings"))
    }

    @Test fun withoutACopyTimeOrWithANewerOneThePinnacleReadStands() {
        assertEquals(now - 2_000L, outcome(now - 2_000L, null).seenAt("pinnacle"))
        assertEquals("a copy newer than the read cannot make the price younger than the read", now - 5_000L, outcome(now - 5_000L, now - 1_000L).seenAt("pinnacle"))
    }

    private val readAt = now - 60_000L
    private val cachedCopy = readAt - 9 * 60_000L
    private fun total(book: String) = RefBookMarket(book, book, LineKind.TOTAL, listOf(RefQuote(Side.OVER, 1.91, 47.5, feedOutcomeId = "11"), RefQuote(Side.UNDER, 1.91, 47.5, feedOutcomeId = "12")), cachedCopy)
    private fun game(book: String) = RefEvent("pl:555", "americanfootball_nfl", now + 3_600_000L, "Buffalo Bills", "Miami Dolphins", listOf(total(book)))
    private fun live() = PropLineLive { now }.also { it.onReady(continuous = false, now = readAt - 1_000L); it.heard(now) }
    private fun move(book: String, id: String, price: Int) = json.parseToJsonElement(
        """{"sport_key":"football_nfl","event":{"id":555},"bookmaker_key":"$book","outcome_id":$id,"previous":{"price_american":-110,"point":47.5},"current":{"price_american":$price,"point":47.5}}""",
    ).jsonObject
    private val heartbeat: JsonArray = json.parseToJsonElement(
        """[{"bookmaker_key":"pinnacle","sport_key":"football_nfl","pregame_read_at":"${iso(now - 5_000L)}","read_age_seconds":5.0,"reread_seconds":10.0,"events":12},
            {"bookmaker_key":"draftkings","sport_key":"football_nfl","pregame_read_at":"${iso(now - 5_000L)}","read_age_seconds":5.0,"reread_seconds":10.0,"events":12}]""",
    ).jsonArray

    @Test fun aPushOrAHeartbeatNeverMakesACachedPinnaclePriceLookFresh() {
        val lv = live(); lv.onHeartbeat(heartbeat, now)
        lv.onLine("line_movement", move("pinnacle", "11", -120), now); lv.onLine("line_movement", move("pinnacle", "12", 100), now)
        val m = lv.overlay(listOf(game("pinnacle")), readAt, now).single().markets.single()
        assertEquals("the new price is laid over", 1.0 + 100.0 / 120.0, m.quotes[0].decimalOdds, 1e-9)
        assertEquals("but its age stays the cached copy's", cachedCopy, m.lastUpdateMs)
    }

    @Test fun aPushAndAHeartbeatStillFreshenAnyOtherBook() {
        val lv = live(); lv.onHeartbeat(heartbeat, now)
        lv.onLine("line_movement", move("draftkings", "11", -120), now); lv.onLine("line_movement", move("draftkings", "12", 100), now)
        val m = lv.overlay(listOf(game("draftkings")), readAt, now).single().markets.single()
        assertEquals(now, m.lastUpdateMs)
    }
}
