package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.reference.ReferenceSource
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanProgress
import com.tjshea.vigilant.data.scanner.ScanReport
import com.tjshea.vigilant.data.scanner.ScanResult
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.Scanner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What a bets-only pricing pass reads (Tj, 2026-09-29: "update the EV for every single open bet, including bets added from vigilant scanner"):
 * the open, pregame bets that name a Novig market and outcome, and the scan settings that reach exactly them (not Tj's feed filters, which
 * hide leagues, later games and market families a bet may already be on).
 */
object BetsScope {

    /** Open bets a pass can ask about: pregame, with the Novig market and outcome that were bet. */
    fun priceable(bets: List<TrackedBet>, now: Long): List<TrackedBet> =
        bets.filter { it.status == BetStatus.PENDING && it.startsTs > now && it.marketId.isNotBlank() && it.outcomeId.isNotBlank() }

    /**
     * The market families [bets] are on, and so all a pass has to ask the fair-odds sources for (Tj's Diagnostics, 2026-09-29: a Check odds now
     * cost Kalshi 103 requests, Polymarket 44 and PropLine 24 for 56 bets, because every family was asked for in every league; every source
     * reads only the families it is given). A bet whose wording can't be read for certain ([BetGrader.pickOf]) asks for every family, so a
     * market the classification doesn't know is never left unpriced to save a request. A period market (1st half, F5, a set) also counts as its
     * full-game kind, since a source may file it there.
     */
    fun familiesFor(bets: List<TrackedBet>): Set<MarketFamily> {
        val all = MarketFamily.entries.toSet()
        val out = HashSet<MarketFamily>()
        for (bet in bets) {
            val pick = BetGrader.pickOf(bet) ?: return all
            out += when (pick) {
                is BetGrader.Pick.Moneyline -> setOf(MarketFamily.MONEYLINE)
                is BetGrader.Pick.Spread -> if (pick.period == BetGrader.Period.GAME) setOf(MarketFamily.SPREAD) else setOf(MarketFamily.SPREAD, MarketFamily.FIRST_HALF)
                is BetGrader.Pick.Total -> if (pick.period == BetGrader.Period.GAME) setOf(MarketFamily.TOTAL) else setOf(MarketFamily.TOTAL, MarketFamily.FIRST_HALF)
                is BetGrader.Pick.TeamTotal -> setOf(MarketFamily.TEAM_TOTAL, MarketFamily.TOTAL)
                is BetGrader.Pick.Prop -> setOf(MarketFamily.PLAYER_PROPS)
                is BetGrader.Pick.FirstSet -> setOf(MarketFamily.FIRST_HALF)
            }
        }
        return out.ifEmpty { all }
    }

    /**
     * [base] widened to cover [bets]: their leagues and the market families they are on ([familiesFor]), a window reaching past the last game (a
     * day of slack: Novig's start can differ from the one a bet was logged with), no per-game caps and no live games. Everything else (which
     * sources are on, the reference books, how fair odds are worked out, their credits) is Tj's, so the fair line is the one the feed uses.
     */
    fun settingsFor(base: ScanSettings, bets: List<TrackedBet>, now: Long): ScanSettings {
        val leagues = bets.mapNotNullTo(HashSet()) { Leagues.byNovigName(it.league)?.novigName }
        val lastStart = bets.maxOfOrNull { it.startsTs } ?: now
        val days = (((lastStart - now).coerceAtLeast(0L) / 86_400_000L) + 2L).toInt()
        return base.copy(
            leagues = leagues,
            families = familiesFor(bets),
            includeLive = false,
            daysAhead = days,
            startsWithinHours = 0,
            bookPropHours = ScanSettings.NO_LIMIT,
            propLineGamesPerScan = ScanSettings.NO_LIMIT,
            linesPerGame = ScanSettings.NO_LIMIT,
            propsPerGame = ScanSettings.NO_LIMIT,
            fillBudget = false,
            maxBooksPerScan = bets.size.coerceAtLeast(1),
        )
    }
}

/** Why a bet has no fair price (pure: the card shows this in place of a number). */
object BetPricingReasons {

    /**
     * [bet] found no fair price in [result], the outcome of a bets-only pass (null when Novig's board couldn't be read): the first reason that
     * applies, in words for the card. [listed]: the markets Novig's board still lists open, [errors]: what the pass's sources said went wrong.
     */
    fun explain(bet: TrackedBet, result: ScanResult?, listed: Set<String>, errors: List<String>): String {
        if (Leagues.byNovigName(bet.league) == null) return "Vigilant doesn't price ${bet.league.ifBlank { "this league" }}"
        if (result == null) {
            return "Novig's board couldn't be read" + errors.firstOrNull()?.let { ": $it" }.orEmpty()
        }
        val quote = result.opportunities.firstOrNull { it.market.marketId == bet.marketId && it.outcome.outcomeId == bet.outcomeId }
        val trouble = errors.firstOrNull()?.let { " (${it.take(MAX_ERROR)})" }.orEmpty()
        return when {
            quote == null && bet.marketId !in listed -> "Novig no longer lists this market (closed, or the game moved)"
            quote == null -> "No fair-odds source has a line for this bet$trouble"
            quote.refEvent == null -> "No fair-odds source has current prices for this game$trouble"
            else -> "Too few current book prices for this exact line (prices older than ${Freshness.LIMIT_TEXT} are left out)$trouble"
        }
    }

    private const val MAX_ERROR = 90
}

/**
 * Prices Vigilant's own open bets, and any others [run] is given, from Vigilant's own fair odds (the feed's sources, freshness rules, devig and
 * blend) in one pass of a bets-only [Scanner]: only those bets' games and markets are asked for, and only their Novig books read. A bet that
 * comes out with a fair price gets its EV now against the price it was bet at; one that doesn't gets the reason ([BetPricingReasons]), so an open
 * bet always shows either a current EV or why not. Runs one pass at a time.
 */
class OpenBetPricer(
    private val tracker: BetTracker,
    /** A `betsOnly` scanner: its own catalog and fair-odds snapshots, never the feed's. */
    private val scanner: Scanner,
    private val sources: (ScanSettings) -> List<ReferenceSource>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Of [asked] open bets: [priced] have a current EV now, the rest got their reason ([reasons], by bet id: written on the bet unless the pass
     * ran `alongside` CNO's, when [BetTracker.mergeReads] writes them). [error]: the pass couldn't run at all.
     */
    data class Report(val asked: Int, val priced: Int, val unpriced: Int, val error: String? = null, val reasons: Map<String, String> = emptyMap())

    private val mutex = Mutex()

    /** Open bets among [ids] that Novig's ids and start let a pass ask about, and how many others there are (their reason is recorded). */
    suspend fun run(
        settings: ScanSettings,
        ids: Collection<String>,
        onProgress: (ScanProgress) -> Unit = {},
        /** Beside CNO's reads of the same bets ([BetTracker.applyPricing] `alongside`): only Vigilant's own read is written. */
        alongside: Boolean = false,
    ): Report = mutex.withLock {
        // CNO only: Vigilant's APIs are asleep, so nothing is asked of them, whoever calls (Tj, 2026-09-29).
        if (!settings.vigilantOn) return@withLock Report(0, 0, 0)
        val now = clock()
        val wanted = ids.toHashSet()
        val open = tracker.all().filter { it.id in wanted && it.status == BetStatus.PENDING && it.startsTs > now }
        if (open.isEmpty()) return@withLock Report(0, 0, 0)
        val askable = BetsScope.priceable(open, now).filter { Leagues.byNovigName(it.league) != null }
        val reasons = HashMap<String, String>()
        // A bet with no Novig market on record (an old ✓ mark) or in a league Vigilant doesn't price can't be asked about.
        for (b in open - askable.toSet()) {
            reasons[b.id] = when {
                Leagues.byNovigName(b.league) == null -> "Vigilant doesn't price ${b.league.ifBlank { "this league" }}"
                else -> "This bet has no Novig market on record (it was logged from an old ✓), so Vigilant can't price it: tap Replace to find it"
            }
        }
        var report: ScanReport? = null
        var error: String? = null
        if (askable.isNotEmpty()) {
            val scoped = BetsScope.settingsFor(settings, askable, now)
            try {
                report = scanner.scan(scoped, sources(scoped), askable.mapTo(HashSet()) { it.marketId }, onProgress)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
        val result = report?.result
        val errors = report?.errors.orEmpty() + listOfNotNull(error)
        val listed = scanner.listed
        val pricedKeys = result?.opportunities?.filter { it.fairProbability != null }?.mapTo(HashSet()) { it.market.marketId to it.outcome.outcomeId }.orEmpty()
        for (b in askable) {
            if ((b.marketId to b.outcomeId) !in pricedKeys) reasons[b.id] = BetPricingReasons.explain(b, result, listed, errors)
        }
        // What was read is saved even if the screen that asked has gone.
        val applied = withContext(NonCancellable) { tracker.applyPricing(result, open.map { it.id }, reasons, alongside) }
        Report(open.size, applied.priced, applied.unpriced, error = error ?: report?.errors?.takeIf { result == null }?.firstOrNull(), reasons = reasons)
    }
}
