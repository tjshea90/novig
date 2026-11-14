package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.awaitText
import com.tjshea.vigilant.data.keys.AllKeysExhaustedException
import com.tjshea.vigilant.data.keys.KeyAttemptResult
import com.tjshea.vigilant.data.keys.KeyPool
import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.reference.TheOddsApiClient
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.engine.Odds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Closing moneylines from ParlayAPI: Pinnacle's (Tj, 2026-09-30, RESEARCH.md §43) until 2026-11-09, DraftKings' and FanDuel's since (PA1, Tj 2026-11-14, from ParlayAPI's e-mail).
 *
 * **What changed.** ParlayAPI stopped serving Pinnacle on 2026-11-09 ("its public prices now arrive up to 15 minutes late"): a request that names `pinnacle` is refused, HTTP 400
 * `UNKNOWN_BOOKMAKER` (`{"detail":{"error":"UNKNOWN_BOOKMAKER","unknown":["pinnacle"],…}}`); its closes file (`/v1/historical/closing-lines.json?source=pinnacle`) answers 200 with no row for any day
 * since at least 2026-11-02 (GitHub probe, parlay.yml `closeprobe`, 2026-11-14). The refusal came back as "didn't answer", which the back-fill asks again every [CloseBackfill.RETRY_MS]: 117 refused calls
 * in one grading run (its 120-bet look), every run. Its e-mail named the fix: "closing lines are served for other books: leave the bookmakers parameter out, or name the books you want".
 *
 * **What this asks now.** `GET /v1/sports/{sport}/closing-lines?bookmakers=draftkings,fanduel&daysFrom=` (5 credits a league, kept [KEEP_MS]): flat rows, one per game and book, told apart by their
 * `bookmaker` (`home_odds`, `away_odds`, `draw_odds` null, `market_key` `h2h`, `commence_time`), each the book's last price BEFORE the start (ParlayAPI's own rule: a price quoted at or after the start is left out).
 * A two-way full-game moneyline only: that is all this endpoint sends (all 23 NBA, 19 NHL and 1 NFL rows of the probe were `h2h`). Each book's close is devigged across its two sides ([CloseDevig]) and, when both
 * books closed, the two are averaged. **They are soft books**: [sharp] is false, so a bet that has a read before the start keeps it ([ClosingLine.closeOf], [CloseBackfill]) and the sharp closes that remain (SGO,
 * OddsPapi Free) stay the sharp ones; the label says which books ("ParlayAPI · DraftKings + FanDuel close") and `BetHistory.closeBasis` files it as `history:softbooks`, not `history:pinnacle`.
 *
 * **What it no longer asks.** Spreads, totals and props: they came from the closes file, which has no Pinnacle now, and DraftKings' and FanDuel's rows in it are mostly prices from AFTER the start (probe: 298 of 300
 * FanDuel NBA game-line rows, all 42 of DraftKings' NFL ones; a day of DraftKings NBA is 23,183 rows, 24 credits, with the game lines spread over rows 0 to 17,757). ESPN (DraftKings' own close, free) and Novig's
 * trades answer those. The Pinnacle file parsers were removed with this change (`git log -S parseFileGameLine`).
 *
 * A book ParlayAPI answers "unknown" for is not named again by this instance ([refused]) and the bet gets a "never" from this source, not a "later". What a key's plan can reach back to is its own
 * (free 48 hours, $5 a week, $20 a month, Business 90 days: /v1/meta/limits), and nothing older is asked. Calls go through the same [KeyPool] as ParlayAPI's scans (metered, key 1 first, a spent key skipped), and
 * scans leave each key's last credits to these ([com.tjshea.vigilant.data.reference.OddsFeed.PARLAY]'s reserve).
 */
class ParlayCloses(
    private val http: OkHttpClient,
    private val pool: KeyPool,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val base: String = "https://parlay-api.com/v1",
    private val clock: () -> Long = System::currentTimeMillis,
    /** How many days back the key's plan reaches ([ParlayAccount.historyDays]: free 2, Starter 7, Pro 30, Business 90); nothing older is asked. */
    private val historyDays: () -> Int = { DEFAULT_HISTORY_DAYS },
) : CloseSource {

    /** Requests made (tests, Diagnostics). */
    @Volatile
    var requests = 0
        private set

    private class Kept(val atMs: Long, val value: JsonElement)

    // Shared with the scan study's grading (which runs beside the Tracker's own): two looks at once mustn't corrupt it.
    private val kept = ConcurrentHashMap<String, Kept>()

    /** Books ParlayAPI answered "unknown" (400 UNKNOWN_BOOKMAKER) for: not named again by this instance. */
    private val refused: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val id: String get() = ID

    /** DraftKings and FanDuel are soft books: not the sharp close SGO and OddsPapi Free still give ([CloseSource.sharp]). */
    override val sharp: Boolean get() = false

    /** Settings' ParlayAPI switch: off, nothing is spent on closes either. */
    @Volatile
    var enabled: Boolean = true

    /** Asked only when ParlayAPI is on and Tj has a key. */
    override val active: Boolean get() = enabled && pool.keyCount() > 0

    override suspend fun closes(bets: List<TrackedBet>): Map<String, CloseLookup> {
        if (!active) return emptyMap()
        val out = HashMap<String, CloseLookup>()
        for (b in bets) {
            val sport = sportKeyOf(b)
            if (sport == null) {
                // A bet with no league on record is one of the ✓ marks imported before the Tracker kept bets (BetTracker.importPlaced).
                out[b.id] = CloseLookup.None(if (b.league.isBlank()) "No league on record (a ✓ mark imported before the Tracker)" else "ParlayAPI has no key for ${b.league}")
                continue
            }
            val pick = BetGrader.pickOf(b)
            // Past the plan's history (ParlayAPI's /v1/meta/limits: free 48 hours, Starter 7 days, Pro 30, Business 90): it answers HISTORICAL_LIMIT.
            val days = historyDays()
            if (clock() - b.startsTs > days * 86_400_000L) {
                out[b.id] = CloseLookup.None("Older than your ParlayAPI plan's $days-day history")
                continue
            }
            out[b.id] = if (pick is BetGrader.Pick.Moneyline) moneyline(b, pick, sport) else CloseLookup.None(ONLY_MONEYLINES)
        }
        return out
    }

    // ---- moneylines -------------------------------------------------------------------------------------------------

    private suspend fun moneyline(b: TrackedBet, pick: BetGrader.Pick.Moneyline, sport: String): CloseLookup {
        // daysFrom reaches back whole days, never past the plan's history (asked beyond it, the answer is 403 HISTORICAL_LIMIT).
        val back = (clock() - b.startsTs) / 86_400_000L + 1
        val most = historyDays().coerceAtMost(MAX_DAYS).toLong()
        if (back > most) return CloseLookup.None("Older than your ParlayAPI plan's $most-day history")
        // A book ParlayAPI answers "unknown" for is dropped and the look goes on with the others (at most once per book): FanDuel refused must not cost this bet DraftKings' close.
        var books = BOOKS.filter { it !in refused }
        var refusedNow: Set<String> = emptySet()
        for (attempt in 0..BOOKS.size) {
            if (books.isEmpty()) return CloseLookup.None(if (refusedNow.isEmpty()) NO_BOOKS else refusedText(refusedNow))
            val params = listOf("bookmakers" to books.joinToString(","), "daysFrom" to back.coerceIn(1, most).toString(), "oddsFormat" to "american")
            when (val got = get("/sports/$sport/closing-lines", params, cost = 5)) {
                is Fetched.Rows -> return combine(books.map { it to parseGameLine(got.root, b, pick, it) })
                is Fetched.Refused -> {
                    refusedNow = refusedNow + got.books
                    val left = BOOKS.filter { it !in refused }
                    // Nothing it named is one of ours (a body that names other keys): don't ask the same thing again.
                    if (left == books) return CloseLookup.None(refusedText(refusedNow))
                    books = left
                }
                Fetched.Silent -> return CloseLookup.Later("ParlayAPI didn't answer")
            }
        }
        return CloseLookup.None(refusedText(refusedNow))
    }

    private fun refusedText(books: Set<String>): String =
        "ParlayAPI no longer serves ${books.joinToString(" or ") { TheOddsApiClient.bookTitle(it) }} closes (HTTP 400 UNKNOWN_BOOKMAKER)"

    /** What one call came back with: its rows, a refusal of the books it named (ParlayAPI no longer knows them), or nothing (no answer: asked again later). */
    private sealed interface Fetched {
        class Rows(val root: JsonElement) : Fetched
        class Refused(val books: Set<String>) : Fetched
        object Silent : Fetched
    }

    /** [cost]: what the call is expected to cost (the server's own figure is recorded when it sends one). */
    private suspend fun get(path: String, params: List<Pair<String, String>>, cost: Int): Fetched {
        val cacheKey = path + params
        kept[cacheKey]?.takeIf { clock() - it.atMs < KEEP_MS }?.let { return Fetched.Rows(it.value) }
        var unknown: Set<String>? = null
        val value = try {
            pool.execute(cost) { key ->
                // ParlayAPI's best practices: the key in the X-API-Key header, not the URL; one retry a second later for a 502/503/504 or a
                // dropped connection, none for 4xx.
                val url = (base + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                val request = Request.Builder().url(url).get().header("X-API-Key", key).build()
                requests++
                var reply = try {
                    http.newCall(request).awaitText()
                } catch (e: IOException) {
                    kotlinx.coroutines.delay(RETRY_AFTER_MS)
                    requests++
                    http.newCall(request).awaitText()
                }
                if (reply.code in 502..504) {
                    kotlinx.coroutines.delay(RETRY_AFTER_MS)
                    requests++
                    reply = http.newCall(request).awaitText()
                }
                val credits = com.tjshea.vigilant.data.keys.CreditHeaders.read({ reply.headers[it] }, clock())
                val body = reply.body
                when {
                    reply.isSuccessful -> KeyAttemptResult.Success(json.parseToJsonElement(body), credits.cost ?: reply.headers["X-Export-Credits"]?.toDoubleOrNull()?.toInt(), credits.remaining, credits.used, credits.resetAtMs)
                    // Past the plan's history window, or no such sport: an answer (nothing to find), not a failure.
                    reply.code == 404 || (reply.code == 403 && body.contains("HISTORICAL_LIMIT", ignoreCase = true)) ->
                        KeyAttemptResult.Success(JsonArray(emptyList()), credits.cost ?: 0, credits.remaining, credits.used, credits.resetAtMs)
                    // A book it no longer serves (Pinnacle, from 2026-11-09): not a failure of the key or the network, and not worth asking again. Nothing is charged.
                    reply.code == 400 && body.contains("UNKNOWN_BOOKMAKER", ignoreCase = true) -> {
                        unknown = unknownBooks(body) ?: params.firstOrNull { it.first == "bookmakers" }?.second?.split(',')?.map { it.trim().lowercase() }?.toSet().orEmpty()
                        KeyAttemptResult.Success(JsonArray(emptyList()), 0, credits.remaining, credits.used, credits.resetAtMs)
                    }
                    reply.code == 429 -> KeyAttemptResult.RateLimited(reply.headers["Retry-After"]?.toLongOrNull()?.times(1000) ?: 2_000, "HTTP 429")
                    (reply.code == 401 || reply.code == 403) &&
                        (body.contains("credit_limit_exceeded") || body.contains("quota", ignoreCase = true) || body.contains("OUT_OF_USAGE_CREDITS")) ->
                        KeyAttemptResult.Depleted("monthly credits used up")
                    reply.code == 401 || reply.code == 403 -> KeyAttemptResult.Invalid("HTTP ${reply.code}" + credits.requestId?.let { " (request $it)" }.orEmpty())
                    else -> throw IOException("HTTP ${reply.code}" + credits.requestId?.let { " (request $it)" }.orEmpty())
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: AllKeysExhaustedException) {
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
        unknown?.let { refused += it; return Fetched.Refused(it) }
        if (value == null) return Fetched.Silent
        kept[cacheKey] = Kept(clock(), value)
        return Fetched.Rows(value)
    }

    /** The books a 400 UNKNOWN_BOOKMAKER body names (`{"detail":{"unknown":["pinnacle"],…}}`); null when it doesn't say. */
    private fun unknownBooks(body: String): Set<String>? = runCatching {
        val detail = (json.parseToJsonElement(body) as? JsonObject)?.get("detail") as? JsonObject
        (detail?.get("unknown") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.lowercase()?.takeIf { s -> s.isNotEmpty() } }?.toSet()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    companion object {
        const val ID = "parlay"

        /**
         * The books whose closes are asked, in the order they are named and shown: the two ParlayAPI's own e-mail names ("bookmakers=fanduel,draftkings") and its closing-lines endpoint returns without a
         * `bookmakers` parameter. Never `pinnacle` (withdrawn 2026-11-09; `ParlayClosesTest` and `ParlayNoPinnacleTest` pin it).
         */
        val BOOKS = listOf("draftkings", "fanduel")

        /** ParlayAPI's best practices: a 502 or a dropped connection is retried once, this much later. */
        const val RETRY_AFTER_MS = 1_000L

        /** A league-day's closes are kept this long: the answer is cached 6 hours on their side. */
        const val KEEP_MS = 6 * 60 * 60_000L

        /** A close's game must start within this of the bet's. */
        private const val START_GAP_MS = 3 * 60 * 60_000L

        /** The most days back `/sports/{sport}/closing-lines` is asked for (a plan's own history can be shorter: [historyDays]). */
        const val MAX_DAYS = 30

        /** Until a key's plan is known: Starter's 7 days (Tj's plan, 2026-09-30). */
        const val DEFAULT_HISTORY_DAYS = 7

        /** What a bet that isn't a full-game moneyline is told (the next source is asked). */
        const val ONLY_MONEYLINES = "ParlayAPI's closes are DraftKings' and FanDuel's moneylines since Pinnacle's were withdrawn (2026-11-09)"

        /** Every book this asks has been refused as unknown. */
        const val NO_BOOKS = "ParlayAPI no longer serves DraftKings' or FanDuel's closes"

        private fun JsonElement?.obj() = this as? JsonObject
        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content
        private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.toDoubleOrNull()

        /** The Odds API sport key of the bet's league (ParlayAPI uses the same keys). */
        fun sportKeyOf(b: TrackedBet): String? =
            Leagues.ALL.firstOrNull { it.novigName.equals(b.league, true) || it.displayName.equals(b.league, true) }?.oddsApiSportKey?.takeIf { it.isNotBlank() }

        private fun ms(s: String?): Long? = s?.let { runCatching { Instant.parse(if (it.endsWith("Z") || it.contains('+')) it else it + "Z").toEpochMilli() }.getOrNull() }

        /** A price to its vigged implied probability: American (-110, +120), or decimal (1.91) if a feed ever sends that. */
        private fun implied(v: Double?): Double? = when {
            v == null -> null
            abs(v) >= 100 -> 1.0 / Odds.americanToDecimal(Math.round(v).toInt())
            v > 1.0 && v < 100 -> 1.0 / v
            else -> null
        }

        private fun fair(mine: Double, other: Double) = CloseDevig.fair(mine, other)

        /** "ParlayAPI · DraftKings close": what the card and [BetHistory.closeBasis] read. A null [book] (an answer that names none) is "ParlayAPI close". */
        fun via(books: List<String>): String =
            if (books.isEmpty()) "ParlayAPI close" else "ParlayAPI · ${books.joinToString(" + ") { TheOddsApiClient.bookTitle(it) }} close"

        /**
         * One close from each book's look ([books] in their order): the books that closed are averaged (two soft books' last word is steadier than either alone), labelled by name; when none did, a "never"
         * naming why (each book's own reason once).
         */
        internal fun combine(looks: List<Pair<String, CloseLookup>>): CloseLookup {
            val found = looks.mapNotNull { (book, l) -> (l as? CloseLookup.Found)?.let { book to it } }
            if (found.isNotEmpty()) return CloseLookup.Found(found.map { it.second.fair }.average(), via(found.map { it.first }))
            val later = looks.firstOrNull { it.second is CloseLookup.Later }?.second
            if (later != null) return later
            return CloseLookup.None(looks.mapNotNull { (it.second as? CloseLookup.None)?.reason }.distinct().joinToString("; ").ifBlank { "Not in ParlayAPI's closing lines" })
        }

        /**
         * The game among [games] that is the bet's ([mHome] vs [mAway], starting about [startsTs]): the best [TeamMatcher.gameScore], the nearest start among
         * equal ones (a doubleheader's own game; the same pairing in two spellings). Null when none fits, or when two DIFFERENT pairings fit equally well:
         * a close from a guess could be another game's (Tj's scan-study file, 2026-10-03: Washington State -117 "closed" at +272). Order [games] so the
         * preferred one of equal starts comes first.
         */
        internal fun <G> bestGame(mHome: String, mAway: String, startsTs: Long, games: List<G>, home: (G) -> String, away: (G) -> String, start: (G) -> Long?): G? {
            val scored = games.mapNotNull { g -> TeamMatcher.gameScore(mHome, mAway, home(g), away(g)).takeIf { it > 0.0 }?.let { g to it } }
            val top = scored.maxOfOrNull { it.second } ?: return null
            val tied = scored.filter { it.second >= top - 1e-9 }.map { it.first }
            val nearest = tied.minByOrNull { g -> abs((start(g) ?: startsTs) - startsTs) } ?: return null
            // The others at that score must be this game under another name (or its other start), not a different pairing.
            val same = tied.all { g ->
                TeamMatcher.similarity(home(nearest), home(g)) >= SAME_NAME && TeamMatcher.similarity(away(nearest), away(g)) >= SAME_NAME
            }
            return nearest.takeIf { same }
        }

        /** Two spellings of one team: the shorter name's words all found in the longer ([TeamMatcher.similarity]). */
        private const val SAME_NAME = 0.8

        /**
         * [pick]'s close from `closing-lines`: flat rows, one per game and book, `home_odds`/`away_odds` at the start (Tj's key, 2026-11-14:
         * `{"home_team":…,"away_team":…,"bookmaker":"draftkings","market_key":"h2h","home_odds":361,"away_odds":-468,"draw_odds":null,"commence_time":…,"last_update":<the start>}`), of [book]'s rows only
         * (null: every row; a row that names no book is taken as [book]'s). A full-game moneyline only; a three-way one (a draw) is never Novig's two-way line.
         */
        fun parseGameLine(root: JsonElement, b: TrackedBet, pick: BetGrader.Pick, book: String? = null): CloseLookup {
            val m = NovigText.parseMatchup(b.eventName) ?: return CloseLookup.None("Couldn't read the teams")
            if (pick !is BetGrader.Pick.Moneyline) return CloseLookup.None(ONLY_MONEYLINES)
            val name = book?.let { TheOddsApiClient.bookTitle(it) } ?: "ParlayAPI"
            val rows = ((root.obj()?.get("data") ?: root.obj()?.get("rows") ?: root) as? JsonArray).orEmpty().mapNotNull { it.obj() }
                .filter { it.containsKey("home_odds") }
                .filter { r -> book == null || (r.str("bookmaker") ?: r.str("source") ?: r.str("archive_source"))?.equals(book, ignoreCase = true) != false }
            // The row of the game with the best fit (the latest update first among equal starts), not the latest row of every game that shares a word
            // with it: Tj's scan-study file, 2026-10-03, a Washington State -117 "closed" at +272, another State game's moneyline.
            val row = bestGame(
                m.home, m.away, b.startsTs,
                rows.filter { r ->
                    r.str("market_key").let { it == null || it == "h2h" } &&
                        (r.str("commence_time")?.let(::ms)?.let { abs(it - b.startsTs) <= START_GAP_MS } ?: false)
                }.sortedByDescending { it.str("last_update")?.let(::ms) ?: 0L },
                { it.str("home_team").orEmpty() }, { it.str("away_team").orEmpty() }, { it.str("commence_time")?.let(::ms) },
            ) ?: return CloseLookup.None("$name has no close for this game in ParlayAPI's closing lines")
            val home = implied(row.num("home_odds")) ?: return CloseLookup.None("$name's close has no price")
            val away = implied(row.num("away_odds")) ?: return CloseLookup.None("$name's close has no price")
            if (row.num("draw_odds") != null) return CloseLookup.None("A three-way close (with a draw) isn't Novig's two-way line")
            val homeSide = when (TeamMatcher.whichOf(pick.team, row.str("home_team").orEmpty(), row.str("away_team").orEmpty())) {
                1 -> true
                2 -> false
                else -> return CloseLookup.None("Couldn't tell which team ${pick.team} is in ParlayAPI's game")
            }
            return CloseLookup.Found(if (homeSide) fair(home, away) else fair(away, home), via(listOfNotNull(book)))
        }
    }
}
