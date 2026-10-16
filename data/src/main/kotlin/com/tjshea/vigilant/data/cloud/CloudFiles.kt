package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.Opportunity
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScanStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * What the phone tells the GitHub engine (Tj, 2026-10-14: "I just control the settings from the phone"). Written by the app to `settings.json` on the `cloud-config` branch whenever a setting changes
 * or the switch moves; read by the engine before every pass. [rev] only ever grows (the phone's clock in ms at the write), so the engine can say which settings a feed was priced under.
 * [on] false = the engine finishes its job and starts nothing (the phone is asleep and so is the cloud: no scan, no API call, no minute spent).
 */
@Serializable
data class CloudConfig(
    val rev: Long = 0L,
    val on: Boolean = false,
    val settings: ScanSettings = ScanSettings(),
    /** Which phone wrote it (a label for the logs). */
    val device: String = "",
    /** Seconds between one scan's end and the next one's start (the scan itself takes as long as it takes). */
    val passGapSeconds: Int = 20,
    /**
     * Pull to refresh / the Scan button on the phone (Tj, 2026-10-14: "I want it to tell GitHub to scan every time I pull to refresh"): the phone's clock when it asked. A value newer than the one the engine last
     * served starts a fresh scan at once ([CloudStatus.ackedRefreshAtMs] is the engine's acknowledgement); 0 = never asked.
     */
    val refreshAtMs: Long = 0L,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): CloudConfig? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(c: CloudConfig): String = json.encodeToString(serializer(), c)
    }
}

/**
 * The engine's heartbeat (`status.json` on the `cloud-feed` branch, small so the phone can poll it every few seconds for free with an ETag). [computedAtMs] is when the newest feed's scan finished;
 * the phone fetches `feed.json.gz` only when it changes. [heartbeatMs] moves every pass even when nothing changed, so a dead engine is told from a quiet one.
 */
@Serializable
data class CloudStatus(
    val version: Int = 1,
    val engine: String = "",
    val runId: String = "",
    val computedAtMs: Long = 0L,
    val startedAtMs: Long = 0L,
    val heartbeatMs: Long = 0L,
    val scanMs: Long = 0L,
    val rows: Int = 0,
    val feedBytes: Int = 0,
    val configRev: Long = 0L,
    val sources: List<String> = emptyList(),
    val passes: Int = 0,
    val problem: String? = null,
    /** The engine is idle on purpose (the phone switched it off). */
    val idle: Boolean = false,
    /** When this job will hand over to the next (ms): the phone shows it, nothing depends on it. */
    val handoverAtMs: Long = 0L,
    /** A scan is running now, since [scanStartedAtMs], and what it is doing ([progressStep], [progressDone] of [progressTotal]). */
    val scanning: Boolean = false,
    val scanStartedAtMs: Long = 0L,
    val progressStep: String = "",
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    /** The newest [CloudConfig.refreshAtMs] the engine has seen and begun serving (its acknowledgement), and the one the running or last scan was started for. */
    val ackedRefreshAtMs: Long = 0L,
    val scanRefreshAtMs: Long = 0L,
    /** The last scan that ran to its end: when it began and ended, and which refresh request it served. */
    val lastScanStartedAtMs: Long = 0L,
    val lastScanEndedAtMs: Long = 0L,
    val lastScanForRefreshAtMs: Long = 0L,
    /**
     * How old the listed bets' prices were at [computedAtMs] (seconds, worked out from each row's own timestamps: [com.tjshea.vigilant.data.scanner.Opportunity.bookFetchedAtMs] is when its Novig book was read,
     * [com.tjshea.vigilant.data.scanner.Opportunity.fairAsOfMs] when the oldest book price behind its fair line was last seen by its feed); -1 = no list or no stamps.
     */
    val novigAgeMedianS: Long = -1L,
    val novigAgeMaxS: Long = -1L,
    val fairAgeMedianS: Long = -1L,
    val fairAgeMaxS: Long = -1L,
    /** The settings revision the running (or last) scan was priced under, and whether the phone changed something since that only the next scan applies ([CloudScanKey]). */
    val pricedConfigRev: Long = 0L,
    val settingsPending: Boolean = false,
    /** The last finished scan's account of itself: Novig reads, throttling, each feed's answer and errors. */
    val scanNote: String = "",
    /** The bid desk's candidate list (`bids.json.gz`; only while the phone has Vigilant-priced bids on): when it was made, how many lines it holds and how big the file is; 0 = none. */
    val bidsAtMs: Long = 0L,
    val bidsCount: Int = 0,
    /** Jobs in a row that ended early because Novig refused the runner's address, and when the last did ([AddressWatch]); carried from job to job. */
    val addressHandovers: Int = 0,
    val addressHandoverAtMs: Long = 0L,
    /** When the engine's API usage snapshot (`usage.json`, [CloudUsage]) was made: the phone fetches it when this changes. 0 = none yet (an engine older than this app). */
    val usageAtMs: Long = 0L,
    /** How the engine reads Novig now ([CloudNovig.route]): its own read-only key (with the key's rate) or the public routes, and why (a refusal of the runner's address). Empty = not said. */
    val novigRoute: String = "",
    /** The order the engine asks its APIs in, best first, with what each was measured to deliver (p90 age of its quotes by the books' own stamps, distance from Pinnacle): [com.tjshea.vigilant.data.scanner.SourceScore]. Empty before the first measurement. */
    val ranking: String = "",
    val bidsBytes: Int = 0,
    /** PropLine's +EV-at-Novig list (`propline-ev.json.gz`, [com.tjshea.vigilant.data.reference.PlEvFeed], TCL56): when the engine's last sweep finished and how many lines it holds; 0 = none (no paid PropLine key, or not swept yet). */
    val propLineEvAtMs: Long = 0L,
    val propLineEvRows: Int = 0,
    /** The engine's own trading (TCL20d): its mode (off / paper / live), what its last pass did in words, why ordering has stopped if it has, and when `trades.json` last changed. */
    val tradeMode: String = "",
    val tradeNote: String = "",
    val tradeStopped: String = "",
    val tradesAtMs: Long = 0L,
    /** The wallet is under [CloudEngine.WALLET_MIN] and the engine trades live: it scans nothing and calls no feed, and only reads the balance every [CloudEngine.WALLET_EVERY_MS] (Tj, 2026-10-15). Empty = not asleep. */
    val walletSleep: String = "",
    val walletCheckedAtMs: Long = 0L,
    /** The trading wallet as last read, in dollars, and when; -1 = never read. Cash is spendable, equity adds what is at risk in open orders. */
    val walletCash: Double = -1.0,
    val walletEquity: Double = -1.0,
    val walletReadAtMs: Long = 0L,
    /** What each fair-odds feed did on the last finished scan (TCL22d): the dashboard's per-API table. Keys never appear here. */
    val feedReport: List<FeedLine> = emptyList(),
    /** When that scan ended, so the dashboard says how old the per-feed answers are. */
    val feedReportAtMs: Long = 0L,
    /** What to refill, buy, fix or switch off ([ApiAdvisor]), most urgent first; empty = nothing to do. */
    val advice: List<ApiAdvisor.Advice> = emptyList(),
    /** APIs the engine is resting to save usage, in words ("ParlayAPI until 22:30 UTC: answered nothing for 3 scans in a row"), and the switch ids it has asked the phone to turn off ([ApiGovernor]). */
    val resting: List<String> = emptyList(),
    val governorOff: List<String> = emptyList(),
    /** Feeds that answered nothing for [FeedWatch.LIMIT] scans in a row (TCL21f), "id (n scans in a row)"; empty = none. */
    val feedAlerts: List<String> = emptyList(),
    /**
     * The engine's bid desk (TCL31): true while the engine itself is posting and watching bids (the phone's own desk then stays down), the last pass in words, the bids resting and the dollars they hold, when it last
     * ran, and why it is stopped (the picked-off guard, a refusal) if it is. [deskBytes]: the size of `desk.json`, the bids and their fills for the phone's screens.
     */
    val deskActive: Boolean = false,
    val deskNote: String = "",
    val deskResting: Int = 0,
    val deskRestingDollars: Double = 0.0,
    val deskAtMs: Long = 0L,
    val deskStopped: String = "",
    val deskBytes: Int = 0,
) {
    /** The engine's desk is the one posting bids now: it says so and its heartbeat is recent. A silent engine hands the bids back to the phone. */
    fun deskLive(now: Long): Boolean = deskActive && heartbeatMs > 0 && now - heartbeatMs < DESK_STALE_MS

    companion object {
        const val DESK_STALE_MS = 3 * 60_000L
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): CloudStatus? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(s: CloudStatus): String = json.encodeToString(serializer(), s)
    }
}

/** One feed's answer on the last scan ([com.tjshea.vigilant.data.scanner.SourceReport] without the long words): leagues answered fresh/reused, games matched, leagues it stood by or skipped, and its error. */
@Serializable
data class FeedLine(
    val id: String,
    val name: String = "",
    val fetched: Int = 0,
    val reused: Int = 0,
    val matched: Int = 0,
    val standingBy: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    val heldBack: String? = null,
)

/**
 * One list as the engine publishes it (`feed.json.gz`). [computedAtMs] is the moment it was PUBLISHED, so every list (each partial one of a long scan, then the final one) has a time of its own
 * and the phone, which fetches a list only when this changes, never misses one.
 */
class CloudFeed(val computedAtMs: Long, val configRev: Long, val stats: ScanStats, val rows: List<Opportunity>, val unreadable: Int = 0) {
    companion object {
        const val VERSION = 1

        fun encode(feed: CloudFeed): ByteArray {
            val j = buildJsonObject {
                put("v", VERSION); put("at", feed.computedAtMs); put("rev", feed.configRev)
                put("stats", buildJsonObject {
                    put("ne", feed.stats.novigEvents); put("me", feed.stats.matchedEvents); put("mp", feed.stats.marketsPriced)
                    put("ow", feed.stats.outcomesWithFair); put("pe", feed.stats.positiveEv); put("lg", feed.stats.laterGames)
                })
                put("rows", JsonArray(feed.rows.map(CloudCodec::encode)))
            }
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(Json.encodeToString(JsonObject.serializer(), j).toByteArray(Charsets.UTF_8)) }
            return out.toByteArray()
        }

        /** The feed in [gz], or null when the bytes are not a feed this version understands. */
        fun decode(gz: ByteArray): CloudFeed? = runCatching {
            val text = GZIPInputStream(gz.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val j = Json.parseToJsonElement(text).jsonObject
            val s = j["stats"]!!.jsonObject
            val (rows, bad) = CloudCodec.decodeRows(j["rows"]!!.jsonArray)
            CloudFeed(
                j["at"]!!.jsonPrimitive.long, j["rev"]?.jsonPrimitive?.long ?: 0L,
                ScanStats(s["ne"]!!.jsonPrimitive.int, s["me"]!!.jsonPrimitive.int, s["mp"]!!.jsonPrimitive.int, s["ow"]!!.jsonPrimitive.int, s["pe"]!!.jsonPrimitive.int, s["lg"]?.jsonPrimitive?.int ?: 0),
                rows, bad,
            )
        }.getOrNull()
    }
}
