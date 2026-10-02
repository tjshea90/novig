package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.cno.NovigBetFinder
import com.tjshea.vigilant.data.keys.CreditsHeldBackException
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.SharpConfirm
import com.tjshea.vigilant.data.tracker.BetGrader
import com.tjshea.vigilant.data.tracker.ParlayBooks
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The sharp books' prices for one exact bet (Tj, 2026-10-02: "can pinnapi or any other Pinnacle api be used in addition to cno to compare the odds?"),
 * for [SharpConfirm] to judge: Pinnacle's two-sided price for the same game, market, line and side, with its own time. RESEARCH.md §60.
 *
 * It asks the feeds Vigilant already has that carry Pinnacle, cheapest and freshest first: PinnWire / pinnapi (one request returns a whole sport's board,
 * stamped when it was made; PinnWire has props), PropLine (a whole league, every book, free daily allowance), then ParlayAPI and The Odds API (credits).
 * The first feed that has the bet's two sides at a sharp book answers; the rest aren't asked. A league's board is kept [keepMs] per feed (a failure too,
 * so a feed that refused isn't asked again at once), and the answer to the next bet in the same league costs nothing. Each feed spends its own allowance
 * as it does for a scan (pace, key rest, credits held back): a feed that can't answer is skipped and the verdict says so. Only called for a bet that
 * already passed every other criterion, so a cycle that finds nothing to bet asks nothing.
 *
 * The bet is matched the way every other comparison in the app is ([ParlayBooks.viewOf]): the game by both teams and its start, the line by its exact
 * number and side, a prop by the player's name and the stat.
 */
class SharpBooks(
    /** The feeds switched on with a key, as a scan would call them (background: the paced ones). */
    private val sources: suspend (ScanSettings) -> List<ReferenceSource>,
    private val settings: suspend () -> ScanSettings,
    private val clock: () -> Long = System::currentTimeMillis,
    private val keepMs: Long = KEEP_MS,
) {
    /** The bet to find, as CNO's row or alert names it. */
    data class Bet(val league: String, val event: String, val startsAtMs: Long?, val market: String, val bet: String)

    /** [quotes]: the sharp books' two-sided prices found (empty: none). [unavailable]: why no feed could be asked, when none answered. */
    data class Answer(val quotes: List<SharpConfirm.Quote>, val unavailable: String? = null)

    private class Kept(val atMs: Long, val snap: RefSnapshot?, val error: String?)

    private val kept = HashMap<String, Kept>()
    private val mutex = Mutex()

    /** Feed calls made (not answers re-used), and what they said, since the app opened: Diagnostics. */
    @Volatile
    var calls: Int = 0
        private set

    @Volatile
    var failures: Int = 0
        private set

    /** How many answers were found by which feed (its title). */
    val answeredBy: Map<String, Int> get() = synchronized(by) { HashMap(by) }
    private val by = HashMap<String, Int>()

    /** [bet]'s sharp quotes as the feeds have them now (cached [keepMs]), for the books in [rules]. */
    suspend fun quotes(bet: Bet, rules: SharpConfirm.Rules): Answer {
        val pick = BetGrader.pickOf(bet.market, bet.bet) ?: return Answer(emptyList())
        val league = Leagues.byNovigName(NovigBetFinder.novigLeague(bet.league) ?: return Answer(emptyList())) ?: return Answer(emptyList())
        val prop = pick is BetGrader.Pick.Prop
        // The scan's own family choices don't limit this: Tj's auto-bet may bet a prop even while the scan's feed leaves props out.
        val s = settings().let { it.copy(families = MarketFamily.entries.toSet()) }
        val usable = sources(s).filter { usable(it, league, prop) }.sortedBy { priority(it) }
        if (usable.isEmpty()) return Answer(emptyList(), "no Pinnacle feed is on with a key (Settings › Fair-odds sources)")
        var answered = false
        var firstError: String? = null
        for (source in usable) {
            val (snap, error) = snapshot(source, league, s)
            if (snap == null) {
                if (firstError == null) firstError = error
                continue
            }
            answered = true
            val view = ParlayBooks.viewOf(snap, bet.event, bet.startsAtMs, bet.bet, pick, clock()) ?: continue
            val quotes = view.prices.filter { it.code in rules.codes && it.twoSided }
                .map { SharpConfirm.Quote(it.code, it.odds!!, it.otherOdds!!, atMs = it.atMs ?: snap.fetchedAtMs.takeIf { t -> t > 0 }, via = source.displayName) }
            if (quotes.isNotEmpty()) {
                synchronized(by) { by.merge(source.displayName, 1, Int::plus) }
                return Answer(quotes)
            }
        }
        // Some feed answered but had no such bet: that is "no price", not "couldn't ask".
        return Answer(emptyList(), if (answered) null else firstError)
    }

    private suspend fun snapshot(source: ReferenceSource, league: com.tjshea.vigilant.data.scanner.League, s: ScanSettings): Pair<RefSnapshot?, String?> = mutex.withLock {
        val key = "${source.id}|${league.novigName}"
        val now = clock()
        kept[key]?.takeIf { now - it.atMs < keepMs }?.let { return@withLock it.snap to it.error }
        calls++
        try {
            val snap = source.odds(league, s)
            kept[key] = Kept(clock(), snap, null)
            snap to null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CreditsHeldBackException) {
            failures++
            val why = "${source.displayName}'s credits are held back for today"
            kept[key] = Kept(clock(), null, why)
            null to why
        } catch (e: Exception) {
            failures++
            val why = "${source.displayName} didn't answer (${(e.message ?: e.javaClass.simpleName).take(80)})"
            kept[key] = Kept(clock(), null, why)
            null to why
        }
    }

    companion object {
        /**
         * A league's board is re-used this long (the sharp quote must still be inside the freshness limit, [SharpConfirm.Rules.maxAgeMs], which the board's own
         * time is judged against, so a re-used board is never fresher than it was).
         */
        const val KEEP_MS = 60_000L

        /** Exchanges and prediction markets aren't sharp sportsbooks here; every other source carries Pinnacle among its books. */
        private val NOT_PINNACLE = setOf("polymarket", "kalshi")

        private fun usable(source: ReferenceSource, league: com.tjshea.vigilant.data.scanner.League, prop: Boolean): Boolean {
            if (source.id in NOT_PINNACLE || !source.supports(league)) return false
            // A prop: the feeds with props (a props source, or PinnWire's board); a game line: the feeds of game lines.
            return if (prop) source.propsOnly || source.id == PinnapiClient.ID else !source.propsOnly
        }

        /** Cheapest and freshest first: PinnWire / pinnapi, PropLine, ParlayAPI, The Odds API. */
        private fun priority(source: ReferenceSource): Int = when {
            source.id == PinnapiClient.ID -> 0
            source.id.startsWith("propline") -> 1
            source.id.startsWith("parlay") -> 2
            source.id.startsWith("oddsapi") -> 3
            else -> 4
        }
    }
}
