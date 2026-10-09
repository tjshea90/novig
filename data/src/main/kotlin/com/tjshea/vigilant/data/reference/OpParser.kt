package com.tjshea.vigilant.data.reference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** One book's connection state for one fixture (`fixtures[].bookmakers.<slug>`): stale = its feed dropped, so its prices are not to be trusted. */
data class OpBookMeta(val hasOdds: Boolean = true, val stale: Boolean = false, val suspended: Boolean = false, val rotated: Boolean = false)

/** One price: `odds.<book>.<oddsId>` (ODDSPAPI_API.md §5). Times are epoch milliseconds. */
data class OpPrice(
    val book: String,
    val outcomeId: Long,
    val playerId: Long,
    val decimal: Double?,
    val active: Boolean,
    val marketActive: Boolean,
    val mainLine: Boolean,
    val marketId: Long?,
    val limit: Double?,
    val changedMs: Long?,
    val bookChangedMs: Long?,
)

/** A fixture as OddsPapi sends it (the `/fixtures` list, `/fixtures/odds` and `/fixtures/odds/main` share its fields); [prices] is empty on the list. */
data class OpFixture(
    val id: String,
    val sportId: Int?,
    val tournamentId: Long?,
    val tournament: String,
    val category: String,
    /** Epoch ms (OddsPapi sends schedule times in seconds). */
    val startMs: Long?,
    /** 0 pregame, 1 live, 2 finished, 3 cancelled. */
    val statusId: Int,
    val live: Boolean,
    val p1Id: Long?,
    val p1: String,
    val p2: String,
    val pinnacleId: String?,
    /** `scores.<period>`: participant 1 and 2's points; "result" is the headline score. */
    val scores: Map<String, Pair<Int?, Int?>>,
    val books: Map<String, OpBookMeta>,
    val prices: List<OpPrice>,
) {
    val started: Boolean get() = live || statusId >= 1
    val finished: Boolean get() = statusId == 2
    val cancelled: Boolean get() = statusId == 3
}

/** A market of `/markets`: [period] and [type] are descriptive strings, [marketId] is the key (ODDSPAPI_API.md §5). */
data class OpMarket(
    val marketId: Long,
    val type: String,
    val period: String,
    val handicap: Double?,
    val playerProp: Boolean,
    val name: String,
    val outcomes: List<Pair<Long, String>>,
)

/** One book's `/bookmakers` row. */
data class OpBookInfo(val slug: String, val name: String, val maxDelayPregameSec: Double?, val maxDelayLiveSec: Double?)

/** `/fixtures/odds/clv`: the first (olv) and last (clv) price of an odds id. */
data class OpClv(val book: String, val outcomeId: Long, val playerId: Long, val closeDecimal: Double?, val closeMs: Long?, val openDecimal: Double?)

/** A tournament of `/tournaments`. */
data class OpTournament(val id: Long, val name: String, val category: String, val slug: String)

/**
 * Reads OddsPapi's v5 answers (ODDSPAPI_API.md §5). Written from the OpenAPI examples, defensive about all of it: every field optional, an unreadable row is skipped (never the whole answer), a list may arrive as
 * a bare array or wrapped in an object. Nothing here has met a live key yet; Settings › OddsPapi › Test key saves a real sample to check it against.
 */
object OpParser {
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(raw: String): JsonElement? = runCatching { lenient.parseToJsonElement(raw) }.getOrNull()

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    private fun JsonObject.lng(k: String): Long? = (this[k] as? JsonPrimitive)?.contentOrNull?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.let { s -> s == "1" || s.equals("true", true) } }

    /** An epoch in seconds or milliseconds (anything above 1e11 is already milliseconds) as milliseconds. */
    fun ms(v: Double?): Long? = v?.let { if (it > 1e11) it.toLong() else (it * 1000.0).toLong() }

    /** The list an answer holds: a bare array, or the first array under `data`/`fixtures`/`markets`/..., or the object itself as a one-row list. */
    private fun rows(root: JsonElement?): List<JsonObject> = when (root) {
        is JsonArray -> root.mapNotNull { it as? JsonObject }
        is JsonObject -> {
            val inner = listOf("data", "fixtures", "markets", "bookmakers", "tournaments", "results", "items").firstNotNullOfOrNull { k -> (root[k] as? JsonArray) }
            if (inner != null) inner.mapNotNull { it as? JsonObject } else listOf(root)
        }
        else -> emptyList()
    }

    /** `/fixtures`, `/fixtures/live`, `/fixtures/odds`, `/fixtures/odds/main`, `/fixtures/settlement`: fixtures, with prices when the answer has them. */
    fun fixtures(raw: String): List<OpFixture> = rows(parse(raw)).mapNotNull { fixture(it) }

    fun fixture(o: JsonObject): OpFixture? {
        val id = o.str("fixtureId") ?: return null
        val status = o["status"].obj()
        val participants = o["participants"].obj() ?: o
        val tournament = o["tournament"].obj()
        val scores = HashMap<String, Pair<Int?, Int?>>()
        (o["scores"].obj())?.forEach { (period, v) ->
            val s = v.obj() ?: return@forEach
            scores[period] = (s.dbl("participant1Score")?.toInt()) to (s.dbl("participant2Score")?.toInt())
        }
        val books = HashMap<String, OpBookMeta>()
        (o["bookmakers"].obj())?.forEach { (slug, v) ->
            val b = v.obj() ?: return@forEach
            books[slug] = OpBookMeta(b.bool("hasOdds") ?: true, b.bool("staleOdds") ?: false, b.bool("suspended") ?: false, b.bool("participantsRotated") ?: false)
        }
        val prices = ArrayList<OpPrice>()
        (o["odds"].obj())?.forEach { (book, byId) ->
            (byId.obj())?.forEach { (oddsId, v) ->
                price(book, oddsId, v.obj() ?: return@forEach)?.let { prices += it }
            }
        }
        return OpFixture(
            id = id,
            sportId = o.lng("sportId")?.toInt() ?: o["sport"].obj()?.lng("sportId")?.toInt(),
            tournamentId = o.lng("tournamentId") ?: tournament?.lng("tournamentId"),
            tournament = tournament?.str("tournamentName") ?: o.str("tournamentName").orEmpty(),
            category = tournament?.str("categoryName") ?: o.str("categoryName").orEmpty(),
            startMs = ms(o.dbl("startTime")),
            statusId = status?.lng("statusId")?.toInt() ?: o.lng("statusId")?.toInt() ?: 0,
            live = status?.bool("live") ?: o.bool("live") ?: false,
            p1Id = participants.lng("participant1Id"),
            p1 = participants.str("participant1Name").orEmpty(),
            p2 = participants.str("participant2Name").orEmpty(),
            pinnacleId = o["externalProviders"].obj()?.str("pinnacleId"),
            scores = scores,
            books = books,
            prices = prices,
        )
    }

    /** The ids an oddsId holds when its price row does not say them: `{fixtureId}:{bookmaker}:{outcomeId}:{playerId}` read from the end. */
    private fun idsOf(oddsId: String): Pair<Long?, Long?> {
        val parts = oddsId.split(':')
        return if (parts.size >= 4) parts[parts.size - 2].toLongOrNull() to parts.last().toLongOrNull() else null to null
    }

    private fun price(book: String, oddsId: String, v: JsonObject): OpPrice? {
        val (outFromId, playerFromId) = idsOf(oddsId)
        val outcome = v.lng("outcomeId") ?: outFromId ?: return null
        return OpPrice(
            book = v.str("bookmaker") ?: book,
            outcomeId = outcome,
            playerId = v.lng("playerId") ?: playerFromId ?: 0L,
            decimal = v.dbl("price")?.takeIf { it > 1.0 } ?: v.dbl("priceAmerican")?.let { decimalFromAmerican(it) },
            active = v.bool("active") ?: true,
            marketActive = v.bool("marketActive") ?: true,
            mainLine = v.bool("mainLine") ?: false,
            marketId = v.lng("marketId"),
            limit = v.dbl("limit"),
            changedMs = v.dbl("changedAt")?.let { ms(it) },
            bookChangedMs = v.dbl("bookmakerChangedAt")?.let { ms(it) },
        )
    }

    fun decimalFromAmerican(a: Double): Double? = when {
        a >= 100.0 -> 1.0 + a / 100.0
        a <= -100.0 -> 1.0 + 100.0 / -a
        else -> null
    }

    /** `/markets`. */
    fun markets(raw: String): List<OpMarket> = rows(parse(raw)).mapNotNull { m ->
        val id = m.lng("marketId") ?: return@mapNotNull null
        OpMarket(
            id, m.str("marketType").orEmpty(), m.str("period").orEmpty(), m.dbl("handicap"), m.bool("playerProp") ?: false, m.str("marketName").orEmpty(),
            (m["outcomes"] as? JsonArray).orEmpty().mapNotNull { x -> x.obj()?.let { o -> o.lng("outcomeId")?.let { it to o.str("outcomeName").orEmpty() } } },
        )
    }

    /** `/bookmakers`. */
    fun bookmakers(raw: String): List<OpBookInfo> {
        val root = parse(raw)
        // Either a list of rows or a map slug -> row.
        val list = rows(root)
        val fromRows = list.mapNotNull { b ->
            val slug = b.str("slug") ?: b.str("bookmaker") ?: b.str("bookmakerSlug") ?: return@mapNotNull null
            OpBookInfo(slug, b.str("bookmakerName") ?: b.str("name") ?: slug, b.dbl("maxDelayPregameInSec"), b.dbl("maxDelayLiveInSec"))
        }
        if (fromRows.isNotEmpty()) return fromRows
        return (root.obj() ?: return emptyList()).mapNotNull { (slug, v) ->
            val b = v.obj() ?: return@mapNotNull null
            OpBookInfo(slug, b.str("bookmakerName") ?: b.str("name") ?: slug, b.dbl("maxDelayPregameInSec"), b.dbl("maxDelayLiveInSec"))
        }
    }

    /** `/tournaments`. */
    fun tournaments(raw: String): List<OpTournament> = rows(parse(raw)).mapNotNull { t ->
        val id = t.lng("tournamentId") ?: return@mapNotNull null
        OpTournament(id, t.str("tournamentName").orEmpty(), t.str("categoryName").orEmpty(), t.str("tournamentSlug").orEmpty())
    }

    /** `/fixtures/odds/clv`: `odds.<book>.<oddsId>.{clv, olv}`. */
    fun clv(raw: String): List<OpClv> {
        val out = ArrayList<OpClv>()
        for (fx in rows(parse(raw))) {
            (fx["odds"].obj())?.forEach { (book, byId) ->
                (byId.obj())?.forEach { (oddsId, v) ->
                    val o = v.obj() ?: return@forEach
                    val (outcome, player) = idsOf(oddsId)
                    val oc = o.lng("outcomeId") ?: outcome ?: return@forEach
                    val close = o["clv"].obj()
                    val open = o["olv"].obj()
                    fun dec(p: JsonObject?) = p?.dbl("price")?.takeIf { it > 1.0 } ?: p?.dbl("priceAmerican")?.let { decimalFromAmerican(it) }
                    out += OpClv(book, oc, o.lng("playerId") ?: player ?: 0L, dec(close), close?.dbl("changedAt")?.let { ms(it) }, dec(open))
                }
            }
        }
        return out
    }

    /** One entry of a price's timeline (`/fixtures/odds/historical`). */
    data class OpTick(val book: String, val outcomeId: Long, val playerId: Long, val changedMs: Long, val decimal: Double?, val active: Boolean)

    /** `/fixtures/odds/historical`: `odds.<book>.<oddsId>.{"<changedAt>": {price, active, changedAt, marketActive}}`. */
    fun historical(raw: String): List<OpTick> {
        val out = ArrayList<OpTick>()
        for (fx in rows(parse(raw))) {
            (fx["odds"].obj())?.forEach { (book, byId) ->
                (byId.obj())?.forEach { (oddsId, timeline) ->
                    val (outcome, player) = idsOf(oddsId)
                    val oc = outcome ?: return@forEach
                    (timeline.obj())?.forEach { (at, v) ->
                        val row = v.obj() ?: return@forEach
                        val ms = (row.dbl("changedAt") ?: at.toDoubleOrNull())?.let { ms(it) } ?: return@forEach
                        val dec = row.dbl("price")?.takeIf { it > 1.0 } ?: row.dbl("priceAmerican")?.let { decimalFromAmerican(it) }
                        out += OpTick(book, oc, player ?: 0L, ms, dec, (row.bool("active") ?: true) && (row.bool("marketActive") ?: true))
                    }
                }
            }
        }
        return out
    }

    /** The error text an OddsPapi failure body carries (`{"error":429,"code":"rate_limited",...}`), or null. */
    fun errorCode(raw: String): String? = parse(raw).obj()?.let { it.str("code") ?: it.str("message") ?: it.str("error") }

    /** `retryAfterSec` of a 429 body, in ms. */
    fun retryAfterMs(raw: String): Long? = parse(raw).obj()?.dbl("retryAfterSec")?.let { (it * 1000.0).toLong() }
}
