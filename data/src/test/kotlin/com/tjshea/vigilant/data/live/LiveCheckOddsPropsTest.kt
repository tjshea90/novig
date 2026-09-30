package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.keys.QuotaPolicy
import com.tjshea.vigilant.data.keys.UsageBook
import com.tjshea.vigilant.data.keys.UsageMeter
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.reference.KalshiClient
import com.tjshea.vigilant.data.reference.OddsFeed
import com.tjshea.vigilant.data.reference.ParlayPropsSource
import com.tjshea.vigilant.data.reference.PolymarketClient
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.Scanner
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.store.JsonFileStore
import com.tjshea.vigilant.data.tracker.BetStatus
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.OpenBetPricer
import com.tjshea.vigilant.data.tracker.TrackedBet
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Check odds now's Vigilant half for player-prop bets (Tj, 2026-09-30: "all the vigilant results show stale odds and aren't refreshed"), against
 * the real Novig board and ParlayAPI's props: real open MLB prop markets become $1 bets with an old read on record, the pass runs `alongside`
 * as Check odds now runs it, and [BetTracker.mergeReads] decides what each bet shows. Prints which were refreshed and why the rest weren't.
 * Skipped unless VIGILANT_LIVE=1 and PARLAY_KEY_FILE names a file holding a ParlayAPI key (never a repo file):
 * `VIGILANT_LIVE=1 PARLAY_KEY_FILE=… bash tools/test.sh :data:test --tests '*LiveCheckOddsPropsTest'` (lines start "LIVE CHECK").
 */
class LiveCheckOddsPropsTest {

    @Test
    fun `real prop bets are refreshed by Check odds now's Vigilant read, or say why not`() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val keyFile = System.getenv("PARLAY_KEY_FILE")?.let(::File)
        assumeTrue(keyFile?.canRead() == true)
        val key = keyFile!!.readText().trim()
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient()
        val novig = NovigPublicClient(http, json)
        val now = System.currentTimeMillis()
        val types = MarketFamily.PLAYER_PROPS.novigTypes
        val events = novig.events(listOf("MLB"), listOf(NovigEvent.STATUS_PREGAME), null).filter { it.startsTs > now + 30 * 60_000L }
        assumeTrue(events.isNotEmpty())
        val markets = novig.markets(listOf("MLB"), types, listOf(NovigEvent.STATUS_PREGAME), null).filter { m -> events.any { it.eventId == m.eventId } }
        println("LIVE CHECK ${markets.size} MLB prop markets on Novig: ${markets.groupingBy { it.marketType }.eachCount()}")
        val chosen = markets.groupBy { it.marketType }.values.flatMap { it.take(3) }.take(15)
        val old = now - 3 * 3_600_000L
        val bets = chosen.map { m ->
            val e = events.first { it.eventId == m.eventId }
            val o = m.outcomes.first { it.name.startsWith("Over", true) || it.name.equals("Yes", true) }
            // Labelled as the Tracker labels a Vigilant bet ("Player Hits", "Isaac Paredes Over 1.5"), from Novig's "Isaac Paredes 1.5 HITS".
            val parts = Regex("^(.+?) ([0-9]+(?:\\.[0-9]+)?) ([A-Z_]+)$").matchEntire(m.description)?.groupValues
            val label = parts?.let { "Player " + com.tjshea.vigilant.data.scanner.PropStats.displayName(it[3]) } ?: m.description
            val selection = parts?.let { "${it[1]} ${o.name.substringBefore(' ')} ${it[2]}" } ?: o.name
            TrackedBet(
                id = "${m.marketId}/${o.outcomeId}", createdAtMs = old, league = "MLB", eventName = e.description, startsTs = m.startsTs,
                marketLabel = label, selection = selection, marketId = m.marketId, outcomeId = o.outcomeId, price = 0.5, cost = 0.5,
                fairAtBet = 0.5, evPercentAtBet = 0.0, stake = 1.0, status = BetStatus.PENDING,
                nowFair = 0.5, nowEv = 0.0, nowAtMs = old, nowVia = BetTracker.VIA_VIGILANT,
            )
        }
        val file = File.createTempFile("live-check", ".json").also { it.deleteOnExit() }
        file.writeText(Json.encodeToString(ListSerializer(TrackedBet.serializer()), bets))
        val tracker = BetTracker(file)
        val meter = UsageMeter(JsonFileStore(File.createTempFile("usage", ".json").also { it.delete() }, UsageBook.serializer(), { UsageBook() }))
        val parlay = TheOddsApiClient(http, KeyPool(QuotaPolicy.PARLAY, { listOf(key) }, meter), json, baseUrl = OddsFeed.PARLAY.base, feed = OddsFeed.PARLAY)
        val sources = listOf(ParlayPropsSource(parlay), PolymarketClient(http, json), KalshiClient(http, json))
        val settings = ScanSettings(leagues = setOf("MLB"), useBookProps = true)
        val pricer = OpenBetPricer(tracker, Scanner(novig, betsOnly = true), { sources })
        // Check odds now's other read for bets with no CNO page: every book's price from ParlayAPI, judged with CNO's check.
        val recheck = com.tjshea.vigilant.data.tracker.BetRecheck(
            tracker, books = { null }, backup = { bet -> com.tjshea.vigilant.data.tracker.ParlayBooks(parlay, active = { true }).view(bet) },
        )
        val began = System.currentTimeMillis()
        val report = pricer.run(settings, bets.map { it.id }, alongside = true)
        val booksRead = recheck.readWithoutPage(bets.map { it.id })
        println("LIVE CHECK books read (ParlayAPI's, CNO's check): ${booksRead.size} of ${bets.size}")
        val merged = tracker.mergeReads(bets.map { it.id }, since = began, cnoSince = began, reasons = report.reasons)
        println("LIVE CHECK ${bets.size} bets in ${(System.currentTimeMillis() - began) / 1000.0} s: Vigilant priced ${report.priced}; both ${merged.both.size}, books only ${merged.cnoOnly.size}, Vigilant only ${merged.vigOnly.size}, neither ${merged.neither.size}")
        for (b in tracker.all()) {
            val refreshed = (b.nowAtMs ?: 0L) >= began
            println("LIVE CHECK ${if (refreshed) "REFRESHED" else "STALE    "} ${b.marketLabel} ${b.selection}: " +
                (if (refreshed) "fair %.3f via ${b.nowVia}, ${b.nowBooks} books".format(b.nowFair) else "note: ${b.nowNote}"))
        }
    }
}
