package com.tjshea.vigilant.data.ncaab

import com.tjshea.vigilant.data.cloud.CloudCodec
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.scanner.EventMatch
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.Planner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Every Novig college basketball game line (moneyline, every spread number, every total number) as the engine publishes it (`ncaab.json.gz`, NC1): one [Opportunity] per side, the SCAN's own fully priced
 * row (market fair odds, EV, the other books) where the scan priced that number, otherwise a Novig-only row with just Novig's take price. The scan prices only the few numbers the books quote (`linesPerGame`);
 * the app's alternate spread and total drop-downs need all the numbers Novig lists, and this is how they get them without a book request per number.
 */
class NcaabBoardFeed(val atMs: Long, val rows: List<Opportunity>, val games: Int, val unreadable: Int = 0) {
    /** Rows with a Novig price to take: without the engine's read-only Novig key the catalog carries no prices and only the scan's priced rows have one. */
    val priced: Int get() = rows.count { it.ladder.isNotEmpty() }

    companion object {
        const val VERSION = 1

        fun encode(f: NcaabBoardFeed): ByteArray {
            val j = buildJsonObject {
                put("v", VERSION); put("at", f.atMs); put("games", f.games)
                put("rows", JsonArray(f.rows.map(CloudCodec::encode)))
            }
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(Json.encodeToString(JsonObject.serializer(), j).toByteArray(Charsets.UTF_8)) }
            return out.toByteArray()
        }

        fun decode(gz: ByteArray): NcaabBoardFeed? = runCatching {
            val text = GZIPInputStream(gz.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val j = Json.parseToJsonElement(text).jsonObject
            val (rows, bad) = CloudCodec.decodeRows(j["rows"]!!.jsonArray)
            NcaabBoardFeed(j["at"]!!.jsonPrimitive.long, rows, j["games"]?.jsonPrimitive?.int ?: 0, bad)
        }.getOrNull()
    }
}

object NcaabBoard {
    const val LEAGUE = "NCAAB"
    /** Games starting within this many hours are on the board (Novig lists about a day ahead). */
    const val WINDOW_HOURS = 36L
    /** Spread numbers further than this from the main one are left out (a 30-point alternate is a longshot nobody drops down to). */
    const val SPREAD_REACH = 12.0
    const val TOTAL_REACH = 16.0
    val TYPES = listOf("MONEY", "SPREAD", "TOTAL")

    /**
     * The board from what the engine holds. [books] are Novig books by market id (best-bid-only is enough: a price to take is one minus the opposing best bid), [scanned] the scan's priced rows by
     * `marketId/outcomeId`. Pure: the same inputs give the same board.
     */
    fun build(events: List<NovigEvent>, markets: List<NovigMarket>, books: Map<String, NovigBook>, scanned: Map<String, Opportunity>, now: Long): List<Opportunity> {
        val league = Leagues.byNovigName(LEAGUE) ?: return emptyList()
        val horizon = now + WINDOW_HOURS * 3_600_000L
        val byEvent = markets.groupBy { it.eventId }
        val out = ArrayList<Opportunity>()
        for (event in events.sortedBy { it.startsTs }) {
            if (event.status != NovigEvent.STATUS_PREGAME || event.matchup == null || event.startsTs > horizon || event.startsTs < now - 10 * 60_000L) continue
            val match = EventMatch(league, event, null, false)
            val planned = byEvent[event.eventId].orEmpty()
                .filter { it.isOpen && it.fee != null && it.marketType in TYPES }
                .mapNotNull { Planner.planOne(match, it) }
            val main = mainStrikes(planned.map { it.market to it.kind }, books)
            for (pm in planned) {
                val strike = pm.market.strike
                if (strike != null) {
                    val reach = when (pm.kind) { LineKind.SPREAD -> SPREAD_REACH; LineKind.TOTAL -> TOTAL_REACH; else -> Double.MAX_VALUE }
                    val centre = main[pm.kind]
                    if (centre != null && kotlin.math.abs(strike - centre) > reach) continue
                }
                val book = books[pm.market.marketId]
                val takes = pm.outcomes.associate { it.outcome.outcomeId to book?.takeLadder(pm.market, it.outcome.outcomeId).orEmpty() }
                val width = takes.values.mapNotNull { it.firstOrNull()?.price }.takeIf { it.size == 2 }?.let { it.sum() - 1.0 }
                for (po in pm.outcomes) {
                    val priced = scanned["${pm.market.marketId}/${po.outcome.outcomeId}"]?.takeIf { it.fairProbability != null }
                    out += priced ?: Opportunity(
                        league = league, event = event, market = pm.market, outcome = po.outcome, marketLabel = pm.label, kind = pm.kind, selection = po.selection,
                        fair = null, fairProbability = null, quote = null, ladder = takes[po.outcome.outcomeId].orEmpty().take(1), depth = null, suggestedStake = null,
                        novigWidth = width, bestBid = book?.bestBid(po.outcome.outcomeId)?.price, bookFetchedAtMs = book?.fetchedAtMs, fairUpdatedMs = null,
                        refEvent = null, lineKey = null, target = po.target,
                    )
                }
            }
        }
        return out
    }

    /**
     * The main number of each kind in one game: the spread or total whose two sides cost closest to even money to take. Without any book read the middle strike stands in, so a game whose books were not
     * read yet still keeps a sensible window.
     */
    private fun mainStrikes(ms: List<Pair<NovigMarket, LineKind>>, books: Map<String, NovigBook>): Map<LineKind, Double> {
        val out = HashMap<LineKind, Double>()
        for (kind in listOf(LineKind.SPREAD, LineKind.TOTAL)) {
            val of = ms.filter { it.second == kind && it.first.strike != null }.map { it.first }
            if (of.isEmpty()) continue
            val even = of.mapNotNull { m ->
                val b = books[m.marketId] ?: return@mapNotNull null
                val a = b.takeLadder(m, m.outcomes[0].outcomeId).firstOrNull()?.price ?: return@mapNotNull null
                m to kotlin.math.abs(a - 0.5)
            }.minByOrNull { it.second }?.first
            out[kind] = (even ?: of.sortedBy { it.strike }[of.size / 2]).strike!!
        }
        return out
    }

    /** The engine is alive (heartbeat under 3 minutes, not idle) and has published a board: the phone then takes its board. Otherwise (the engine is off, asleep on an empty wallet, or no GitHub token) the phone reads Novig itself. */
    fun engineServes(status: com.tjshea.vigilant.data.cloud.CloudStatus?, now: Long): Boolean =
        status != null && !status.idle && status.ncaabAtMs > 0L && now - status.heartbeatMs <= ALIVE_MS

    private const val ALIVE_MS = 3 * 60_000L

    /** One read of the board from Novig: the open NCAAB game lines and their best prices, joined with the scan's priced rows. */
    suspend fun read(novig: NovigSource, scanned: Collection<Opportunity>, now: Long): NcaabBoardFeed {
        val before = now + WINDOW_HOURS * 3_600_000L
        val events = novig.events(listOf(LEAGUE), listOf(NovigEvent.STATUS_PREGAME), before).filter { it.league == LEAGUE }
        if (events.isEmpty()) return NcaabBoardFeed(now, emptyList(), 0)
        val ids = events.mapTo(HashSet()) { it.eventId }
        val markets = novig.markets(listOf(LEAGUE), TYPES, listOf(NovigEvent.STATUS_PREGAME), before).filter { it.eventId in ids }
        val marketIds = markets.map { it.marketId }
        novig.refreshTops(marketIds)
        val books = novig.pushedTop(marketIds)
        val rows = build(events, markets, books, scanned.filter { it.league.novigName == LEAGUE }.associateBy { it.key }, now)
        return NcaabBoardFeed(now, rows, rows.map { it.event.eventId }.distinct().size)
    }
}
