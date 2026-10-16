package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * PropLine's cross-book +EV scanner at Novig (Tj, 2026-10-16, TCL56: "cross book +ev scanner (make a section in the android app for this because I want to see what +ev bets it finds)").
 * Pro's `/events/{id}/ev?bookmakers=novig` gives, per line, a no-vig fair probability from the sharpest book that prices it (Pinnacle first) and Novig's EV% against it. The GitHub engine sweeps the
 * pregame games of the picked leagues ([PropLineEvSweep]) and publishes the +EV lines as `propline-ev.json.gz`; the phone lists them (the +EV tab's PropLine screen). A line is information, not an order: the
 * Bet sheet re-prices at Novig's live book before anything is placed, and a Novig price PropLine last read minutes ago (props: 15 to 45 minutes) says so in its age.
 */
@Serializable
data class PlEvRow(
    val sport: String,
    val league: String,
    val eventId: String,
    val home: String,
    val away: String,
    val startsAtMs: Long,
    val market: String,
    /** The player for a prop; blank for a game line. */
    val player: String,
    val point: Double? = null,
    val outcome: String,
    /** Novig's American price as PropLine read it. */
    val price: Int,
    val evPct: Double,
    /** The fair probability of this outcome (0-1). */
    val fairProb: Double,
    val fairSource: String,
    /** When PropLine last read Novig's price for this line. */
    val updatedAtMs: Long,
    /** When the sweep that found it ran (the journal's clock; a line is journaled each sweep it is found in). */
    val seenAtMs: Long = 0L,
)

@Serializable
data class PlEvFeed(
    val atMs: Long,
    val rows: List<PlEvRow>,
    /** Games looked at, requests spent, the plan, and why a sweep was partial. */
    val games: Int = 0,
    val requests: Int = 0,
    val tier: String = "",
    val note: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(f: PlEvFeed): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(json.encodeToString(serializer(), f).toByteArray()) } }.toByteArray()

        fun decode(bytes: ByteArray): PlEvFeed? = runCatching {
            json.decodeFromString(serializer(), GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) })
        }.getOrNull()
    }
}

object PropLineEv {
    /** Games starting within this many hours are swept. */
    const val WINDOW_HOURS = 24

    /** Under this EV% a line is not worth a row; over [MAX_EV] it is nearly always a stale or mismatched line (PropLine's own advice: about 10 is a sensible ceiling for alerts; the list keeps more and says so). */
    const val MIN_EV = 0.5
    const val MAX_EV = 30.0

    /** One `/ev` answer into rows. [redacted] = a free key (every number nulled): no rows, the caller says why. */
    class Parsed(val rows: List<PlEvRow>, val redacted: Boolean)

    fun parse(raw: String, json: Json, sport: String, league: String, eventId: String, home: String, away: String, startsAtMs: Long, now: Long): Parsed {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return Parsed(emptyList(), false)
        if ((root["redacted"] as? JsonPrimitive)?.booleanOrNull == true) return Parsed(emptyList(), true)
        val out = ArrayList<PlEvRow>()
        for (line in (root["lines"] as? JsonArray).orEmpty()) {
            val l = line as? JsonObject ?: continue
            val market = (l["market_key"] as? JsonPrimitive)?.contentOrNull ?: continue
            val player = (l["description"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val point = (l["point"] as? JsonPrimitive)?.doubleOrNull
            val source = (l["fair_source"] as? JsonPrimitive)?.contentOrNull ?: ""
            val probs = l["fair_probs"] as? JsonObject ?: continue
            for (o in (l["outcomes"] as? JsonArray).orEmpty()) {
                val oc = o as? JsonObject ?: continue
                if ((oc["book"] as? JsonPrimitive)?.contentOrNull != PropLineClient.NOVIG) continue
                if ((oc["is_plus_ev"] as? JsonPrimitive)?.booleanOrNull != true) continue
                val ev = (oc["ev_pct"] as? JsonPrimitive)?.doubleOrNull ?: continue
                if (ev < MIN_EV || ev > MAX_EV) continue
                val name = (oc["name"] as? JsonPrimitive)?.contentOrNull ?: continue
                val price = (oc["price"] as? JsonPrimitive)?.intOrNull ?: continue
                val fair = (probs[name] as? JsonPrimitive)?.doubleOrNull ?: continue
                val upd = (oc["last_update"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
                out += PlEvRow(sport, league, eventId, home, away, startsAtMs, market, player, (oc["point"] as? JsonPrimitive)?.doubleOrNull ?: point, name, price, ev, fair, source, upd, now)
            }
        }
        return Parsed(out, false)
    }
}

/** The engine's sweep: the picked leagues' pregame games within [PropLineEv.WINDOW_HOURS], one `/ev` request each, six at a time (PropLine allows 20 in flight; its client paces the starts). */
class PropLineEvSweep(private val client: PropLineClient, private val json: Json = Json { ignoreUnknownKeys = true }) {
    suspend fun sweep(settings: ScanSettings, now: Long): PlEvFeed {
        val plan = client.plan() ?: return PlEvFeed(now, emptyList(), tier = "free", note = "needs a paid PropLine key (Hobby or Pro): the free key's +EV answers are redacted")
        var requests = 0
        var games = 0
        var note = ""
        val rows = ArrayList<PlEvRow>()
        val horizon = now + PropLineEv.WINDOW_HOURS * 3_600_000L
        val leagues = settings.leagues.mapNotNull { Leagues.byNovigName(it) }.filter { it.oddsApiSportKey in PropLineClient.SPORTS }.distinctBy { it.oddsApiSportKey }
        val gate = Semaphore(6)
        for (league in leagues) {
            val sport = league.oddsApiSportKey
            val events = try {
                requests++
                client.events(sport)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                note = "${league.displayName}: ${e.message?.take(80) ?: e.javaClass.simpleName}"
                continue
            }
            val soon = events.filter { it.commenceMs in (now + 1)..horizon }
            games += soon.size
            val results = coroutineScope {
                soon.map { ev ->
                    async {
                        gate.withPermit {
                            try {
                                requests++
                                val raw = client.evRaw(sport, ev.id.removePrefix(PropLinePropsSource.PREFIX)) ?: return@withPermit null
                                PropLineEv.parse(raw, json, sport, league.displayName, ev.id.removePrefix(PropLinePropsSource.PREFIX), ev.home, ev.away, ev.commenceMs, now)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                note = "${league.displayName}: ${e.message?.take(80) ?: e.javaClass.simpleName}"
                                null
                            }
                        }
                    }
                }.map { it.await() }
            }
            for (p in results) if (p != null) rows += p.rows
        }
        return PlEvFeed(now, rows.sortedByDescending { it.evPct }, games, requests, plan.tier, note)
    }
}
