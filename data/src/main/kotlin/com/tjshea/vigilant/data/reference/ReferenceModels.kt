package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.ScanSettings

/** Which side of a line a quote is for, independent of any provider's naming. */
enum class Side { HOME, AWAY, OVER, UNDER }

/**
 * What a line is about. Team totals and player props are over/under lines on one team or one
 * player ([RefBookMarket.subject]).
 */
enum class LineKind { MONEYLINE, SPREAD, TOTAL, TEAM_TOTAL, PLAYER_PROP }

/** One outcome price from one book. [point] is the handicap (spreads) or the line (over/unders). */
data class RefQuote(val side: Side, val decimalOdds: Double, val point: Double?)

/** One book's quote for one market of one event. */
data class RefBookMarket(
    val bookKey: String,
    val bookTitle: String,
    val kind: LineKind,
    val quotes: List<RefQuote>,
    val lastUpdateMs: Long?,
    /** 0 = full game, 1 = 1st half (the first 5 innings in baseball), [PERIOD_FIRST_INNING]. */
    val period: Int = 0,
    /** [LineKind.TEAM_TOTAL]: "HOME" or "AWAY". [LineKind.PLAYER_PROP]: the player's name. */
    val subject: String? = null,
    /** [LineKind.PLAYER_PROP]: the stat, as Novig names it (e.g. `PASSING_YARDS`). */
    val stat: String? = null,
) {
    /**
     * The line this quote is on: null for moneylines, the HOME side's handicap for spreads
     * (so "home -3.5 / away +3.5" is line -3.5), the over/under number for everything else.
     */
    val line: Double?
        get() = when (kind) {
            LineKind.MONEYLINE -> null
            LineKind.SPREAD -> quotes.firstOrNull { it.side == Side.HOME }?.point
            LineKind.TOTAL, LineKind.TEAM_TOTAL, LineKind.PLAYER_PROP -> quotes.firstOrNull { it.side == Side.OVER }?.point
        }

    /** What this quote covers, for a fallback source to skip: "PROP:<stat>" for props, else "<kind>:<period>". */
    val coverage: String get() = if (kind == LineKind.PLAYER_PROP) "PROP:$stat" else "$kind:$period"

    /** The same quote seen from the other team's side (home and away swapped). */
    fun flipped(): RefBookMarket = copy(
        quotes = quotes.map {
            when (it.side) {
                Side.HOME -> it.copy(side = Side.AWAY)
                Side.AWAY -> it.copy(side = Side.HOME)
                else -> it
            }
        },
        subject = if (kind == LineKind.TEAM_TOTAL) flipSide(subject) else subject,
    )

    companion object {
        const val HOME = "HOME"
        const val AWAY = "AWAY"

        /** Baseball's 1st inning (Novig's FIRST_INNING_TOTAL: "NRFI/YRFI", over/under 0.5 runs). */
        const val PERIOD_FIRST_INNING = 3

        fun flipSide(s: String?): String? = when (s) {
            HOME -> AWAY
            AWAY -> HOME
            else -> s
        }
    }
}

/** One game from a reference feed, with every book's quotes. */
data class RefEvent(
    val id: String,
    val sportKey: String,
    val commenceMs: Long,
    val home: String,
    val away: String,
    val markets: List<RefBookMarket>,
    /**
     * Set when the provider only knows the game's date, not its start time (Kalshi tickers carry
     * an Eastern-time date). Matching then compares dates instead of start times.
     */
    val etDate: String? = null,
) {
    fun flipped(): RefEvent = copy(home = away, away = home, markets = markets.map { it.flipped() })
}

/** One reference-feed call's result for one sport. */
data class RefSnapshot(
    val sportKey: String,
    val events: List<RefEvent>,
    val fetchedAtMs: Long,
    /** From The Odds API's `x-requests-remaining` header: credits left this billing period. */
    val creditsRemaining: Int? = null,
    val creditsUsed: Int? = null,
    /** [ReferenceSource.id] of the provider that produced it. */
    val provider: String = "",
    /**
     * Novig's own prices as this feed relays them (PropLine, ~20 s behind Novig), the same games with
     * only Novig's quotes. Never a fair line and never coverage: they only order which Novig books a
     * scan reads first (RESEARCH.md §23.6).
     */
    val novig: List<RefEvent> = emptyList(),
)

/** A fair-odds provider. Each call covers one league. */
interface ReferenceSource {
    /** Stable id: "pinnacle", "polymarket", "kalshi", "oddsapi". */
    val id: String
    val displayName: String

    /** True when each call spends a limited quota (credits), so callers should re-use results. */
    val metered: Boolean get() = false

    /**
     * Player-prop stats this source can price that the free exchanges (Kalshi) don't: Novig lists
     * hundreds of those markets a day, so the scan only loads them when some source prices them.
     */
    val extraPropTypes: Set<String> get() = emptySet()

    /** Whether this provider lists [league] at all. A scan never calls [odds] for one it doesn't. */
    fun supports(league: League): Boolean = true

    /** How long a fetched snapshot may be re-used instead of calling again. 0 = fetch on every scan. */
    fun reuseMs(settings: ScanSettings): Long = 0L

    suspend fun odds(league: League, settings: ScanSettings): RefSnapshot

    /**
     * True for a source that needs Novig's board first ([ScanContext]), e.g. to spend credits only
     * on games Novig actually lists props for. The scanner waits for the catalog before calling it.
     */
    val needsCatalog: Boolean get() = false

    /** [odds] with Novig's board for the league, for sources that [needsCatalog]. */
    suspend fun odds(league: League, settings: ScanSettings, context: ScanContext): RefSnapshot = odds(league, settings)

    /**
     * The [id] of the source that goes first for the same books (RESEARCH.md §23; Tj, 2026-09-27: "If
     * apis overlap odds from the same sports books, use the best/fastest API first and the others as
     * automatic fallbacks"). A fallback runs once that source has answered, with what it gave in
     * [ScanContext.covered], and only when [needed] says it could add something. Null = first choice.
     */
    val fallbackFor: String? get() = null

    /**
     * For a fallback ([fallbackFor]): whether calling for [league] could add anything the first source
     * didn't give this scan. False = it stands by (nothing spent). Called only when no re-usable
     * snapshot of its own would be used instead.
     */
    suspend fun needed(league: League, settings: ScanSettings, context: ScanContext): Boolean = true
}

/** What a scan already knows when a [ReferenceSource.needsCatalog] source (or a fallback) runs. */
data class ScanContext(
    val novigEvents: List<NovigEvent> = emptyList(),
    val novigMarkets: List<NovigMarket> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    /**
     * For a fallback source: per Novig event id, what its first source priced for that game this
     * scan ([RefBookMarket.coverage]: "MONEYLINE:0", "PROP:RECEPTIONS", …).
     */
    val covered: Map<String, Set<String>> = emptyMap(),
    /** For a fallback source: the Novig leagues its first source answered this scan. */
    val firstAnswered: Set<String> = emptySet(),
)

/**
 * An error as one short line for the scan's banner: a reply that couldn't be read says so plainly
 * instead of dumping the JSON it choked on (Tj's phone, 2026-09-27: "Unexpected JSON token at offset
 * 537 … JSON input: ….."), and anything else is cut to [max] characters.
 */
fun readableError(e: Throwable, max: Int = 160): String {
    if (e is kotlinx.serialization.SerializationException) return "sent a reply Vigilant couldn't read"
    val m = (e.message ?: e.javaClass.simpleName).replace(Regex("\\s+"), " ").trim()
    return if (m.length <= max) m else m.take(max - 1).trimEnd() + "…"
}

/** A provider refused or failed in a way the user should read as-is. */
open class ReferenceException(message: String) : Exception(message)

/**
 * A provider answered part of a request, then failed (e.g. the credits ran out halfway through a
 * slate's props). [partial] is still worth pricing; the message is still worth showing.
 */
class PartialReferenceException(val partial: RefSnapshot, message: String) : ReferenceException(message)

/**
 * Turns an exchange's two-sided quote into book-style decimal odds. Buying side A costs its ask;
 * buying side B costs `1 - bid(A)`. The gap between them plays the role of a book's vig, so the
 * normal devig math lands on the mid. Returns null for thin or lopsided markets that would only
 * add noise to a fair line.
 */
object ExchangeQuote {
    fun toDecimal(bid: Double?, ask: Double?, maxSpread: Double): Pair<Double, Double>? {
        if (bid == null || ask == null) return null
        if (bid <= 0.0 || ask >= 1.0 || ask <= bid) return null
        if (ask - bid > maxSpread + 1e-9) return null
        val buyA = ask
        val buyB = 1.0 - bid
        if (buyA !in MIN_PRICE..MAX_PRICE || buyB !in MIN_PRICE..MAX_PRICE) return null
        return (1.0 / buyA) to (1.0 / buyB)
    }

    const val MIN_PRICE = 0.02
    const val MAX_PRICE = 0.98
}
