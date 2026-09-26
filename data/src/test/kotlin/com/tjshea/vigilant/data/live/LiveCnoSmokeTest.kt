package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.cno.CnoBooks
import com.tjshea.vigilant.data.cno.CnoChecks
import com.tjshea.vigilant.data.cno.CnoClient
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Reads CrazyNinjaOdds' REAL pages the way the app does: the Novig list with the scanner's
 * default filters, a refresh (one postback), the top bet's books and its Novig link. Skipped
 * unless VIGILANT_LIVE=1. Run by hand:
 * `VIGILANT_LIVE=1 ./gradlew :data:test --tests '*LiveCnoSmokeTest'`.
 */
class LiveCnoSmokeTest {

    @Test
    fun `real CNO - the Novig view reads with the scanner's filters, refreshes, and its top bet's books check out`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain -> requests++; chain.proceed(chain.request()) }.build()
        val client = CnoClient(http)
        val filters = CnoFilters()
        val first = client.fetch(CnoView.DEFAULT, filters)
        println("LIVE CNO: ${first.rows.size} rows, CNO data ${first.cnoAgeSeconds}s old, EV column ${first.evLabel}, note ${first.note}")
        first.rows.take(5).forEach { println("  ${"%.2f".format(it.ev * 100)}% ${it.bet} | ${it.market} | ${it.event} | ${it.odds} (\$${it.available}) fair ${it.fairOdds} ${it.books} books | ${it.book}") }
        assertEquals(2, requests)
        assertEquals("C-WC", first.evLabel) // CNO honored the posted devig method
        assertTrue(first.rows.all { it.book == "Novig" && it.odds <= 150 && (it.books ?: 99) >= 5 && it.gameUrl != null })
        val screened = CnoChecks.screen(first, filters, System.currentTimeMillis())
        println("LIVE CNO screened: ${screened.picks.size} kept, hidden ${screened.hidden}")

        delay(4_000)
        val second = client.fetch(CnoView.DEFAULT, filters)
        println("LIVE CNO refresh: ${second.rows.size} rows, CNO data ${second.cnoAgeSeconds}s old")
        assertEquals(3, requests)

        val top = first.rows.firstOrNull() ?: return@runBlocking
        val books = client.books(top)
        println("LIVE CNO books for ${top.bet}: other side ${books?.otherBet}, ${books?.prices?.joinToString { "${it.code} ${it.odds}/${it.otherOdds}" }}")
        requireNotNull(books)
        assertTrue(books.prices.any { it.code == CnoBooks.NOVIG })
        val check = CnoBooks.check(books, top.odds)
        println("LIVE CNO check: ${check.verdict}, ${check.twoSided} two-sided, fair ${check.fairProbability}, EV ${check.ev}")
        val link = client.novigLink(top)
        println("LIVE CNO Novig link: $link")
        assertTrue(link == null || link.startsWith("novigapp://"))
        println("LIVE CNO check agreeing: ${check.agreeing} of ${check.twoSided}")

        // Player teams for the list's player bets, from ESPN's real rosters (RESEARCH.md §20).
        val teams = com.tjshea.vigilant.data.teams.PlayerTeams(OkHttpClient())
        val games = com.tjshea.vigilant.data.teams.PlayerTeams.gamesOf(first.rows)
        val reads = teams.fill(games)
        val players = first.rows.mapNotNull { r -> com.tjshea.vigilant.data.teams.PlayerTeams.playerOf(r)?.let { r to it } }
        val tagged = players.map { (r, p) -> "$p (${teams.state.value.teamOf(r.league, r.event, p) ?: "?"}) | ${r.event}" }
        println("LIVE ESPN: ${games.size} games, $reads reads, ${players.size} player bets")
        tagged.take(15).forEach { println("  $it") }
        if (players.isNotEmpty()) assertTrue("most players get a team", tagged.count { !it.contains("(?)") } * 2 >= players.size)
    }
}
