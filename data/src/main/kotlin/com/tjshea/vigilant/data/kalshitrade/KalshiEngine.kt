package com.tjshea.vigilant.data.kalshitrade

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the phone tells the GitHub engine about Kalshi (`kalshi.json` on `cloud-config`, KV9): the engine's mode and the Kalshi config the phone shows. OFF = the engine does nothing for Kalshi (and takes
 * its bids down); PAPER = it scans and records what it would bet, orders nothing; LIVE = it places the orders the config allows (the auto-bet when [KalshiConfig.autoMode] is not OFF, bids when
 * `scan.maker`) with the Kalshi key sent as `KALSHI_KEY_ID` / `KALSHI_KEY_PEM`. Written ONLY by the phone's Kalshi tab (or a session through `tools/engine_set.py`); default OFF.
 */
@Serializable
data class KalshiEngineConfig(
    val rev: Long = 0L,
    val mode: KalshiAutoMode = KalshiAutoMode.OFF,
    val config: KalshiConfig = KalshiConfig(),
    val writtenAtMs: Long = 0L,
) {
    companion object {
        const val BRANCH = "cloud-config"
        const val FILE = "kalshi.json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): KalshiEngineConfig? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(c: KalshiEngineConfig): String = json.encodeToString(serializer(), c)
    }
}

/** One bet the engine's Kalshi scan lists, for the phone's "GitHub engine" card. */
@Serializable
data class KalshiEngineRow(
    val selection: String,
    val event: String,
    val league: String,
    val market: String,
    val startsTs: Long,
    val ev: Double,
    val price: Double,
    val fair: Double,
    val stars: Double? = null,
)

/** One sure profit the engine found between the Novig scan it just ran and Kalshi (listed, never ordered by the engine). */
@Serializable
data class KalshiEngineArb(
    val selectionNovig: String,
    val selectionKalshi: String,
    val event: String,
    val league: String,
    val profit: Double,
    val payout: Double,
    val safe: Boolean,
)

/** What the engine says about its Kalshi lane (`kalshi-status.json` on `cloud-feed`). */
@Serializable
data class KalshiEngineStatus(
    val atMs: Long = 0L,
    val mode: KalshiAutoMode = KalshiAutoMode.OFF,
    val rev: Long = 0L,
    val note: String = "",
    val scanMs: Long = 0L,
    val scannedAtMs: Long = 0L,
    val listed: Int = 0,
    val priced: Int = 0,
    val rows: List<KalshiEngineRow> = emptyList(),
    val arbs: List<KalshiEngineArb> = emptyList(),
    val cash: Double? = null,
    val autoNote: String = "",
    val bidNote: String = "",
    val stopped: String = "",
    val betsPlaced: Int = 0,
    val betsPaper: Int = 0,
    /** The Kalshi key reached the engine (the two secrets): without it LIVE is paper. */
    val keyOnGitHub: Boolean = false,
) {
    companion object {
        const val FILE = "kalshi-status.json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        fun parse(text: String): KalshiEngineStatus? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
        fun text(s: KalshiEngineStatus): String = json.encodeToString(serializer(), s)
    }
}
