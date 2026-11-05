package com.tjshea.vigilant.data.ncaab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * College basketball against-the-spread system (NC1, Tj 2026-11-05): the files `tools/ncaab_ats.py` keeps on the `ncaab-data` branch of the repository.
 * `ratings.json` holds every team's averages as of the last run (its points for and against per game, and the average of its green-plus / red-minus tiers against the
 * closing spread, outliers left out); `slate.json` is today's and tomorrow's schedule; the games themselves (`games/YYYY-MM.jsonl`) stay on GitHub for Claude and the backtest.
 * Nothing here is computed on the phone: it reads what the GitHub job wrote.
 */
@Serializable
data class NcaabTeam(
    val n: String = "",
    val ab: String = "",
    /** Games behind the averages (the ones that were not left out). */
    val g: Int = 0,
    /** All logged games this season, and how many were left out (a mismatch of 20+ points, or a scoring outlier). */
    val all: Int = 0,
    val ex: Int = 0,
    val ppg: Double = 0.0,
    val papg: Double = 0.0,
    /** The mean tier: -2 (double red minus) to +2 (double green plus). */
    val ats: Double = 0.0,
    /** The mean cover margin in points (each game's clipped at 25). */
    val cm: Double = 0.0,
    val sym: String = "=",
    /** The last results, oldest first: -2, -1, 0, 1, 2. */
    val last: List<Int> = emptyList(),
)

/** What `backtest` fitted: points of expected cover margin per point of scoring / per tier of difference, and the total's slope. Zeroes = no fit yet (the app then shows the numbers and no pick). */
@Serializable
data class NcaabModel(
    val kO: Double = 0.0,
    val kD: Double = 0.0,
    val kR: Double = 0.0,
    val kT: Double = 0.0,
    /** The spread of cover margins around the line, in points (a game's result against its spread): the normal curve's width. */
    val sigma: Double = 11.0,
    val source: String = "none",
    val note: String = "",
) {
    val fitted: Boolean get() = source == "backtest"
}

@Serializable
data class NcaabRatings(
    val asOf: Long = 0L,
    val season: Int = 0,
    val teams: Map<String, NcaabTeam> = emptyMap(),
    val model: NcaabModel = NcaabModel(),
    val games: Int = 0,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        fun decode(text: String): NcaabRatings? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

@Serializable
data class SlateTeam(
    val id: String,
    val n: String = "",
    val dn: String = "",
    val ab: String = "",
    /** AP rank when ranked. */
    val rk: Int? = null,
)

@Serializable
data class SlateGame(
    val id: String,
    val t: Long,
    val d: String = "",
    /** pre, in or post. */
    val state: String = "pre",
    val neu: Int = 0,
    val detail: String = "",
    val h: SlateTeam,
    val a: SlateTeam,
    val hs: String? = null,
    @SerialName("as") val awayScore: String? = null,
    /** ESPN BET's current HOME spread (negative = home favoured) and total: only a fallback when Novig lists no line. */
    val sp: Double? = null,
    val tt: Double? = null,
)

@Serializable
data class NcaabSlate(
    val asOf: Long = 0L,
    val games: List<SlateGame> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        fun decode(text: String): NcaabSlate? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/** The tier symbols as Tj wrote them: green plus / double, red minus / double, and the tie. */
object NcaabSymbols {
    fun pretty(sym: String): String = when (sym) {
        "++" -> "++"
        "+" -> "+"
        "-" -> "−"
        "--" -> "−−"
        else -> "="
    }

    /** A tier of one game (-2..2) as a symbol. */
    fun ofTier(t: Int): String = when {
        t >= 2 -> "++"
        t == 1 -> "+"
        t == 0 -> "="
        t == -1 -> "−"
        else -> "−−"
    }
}
