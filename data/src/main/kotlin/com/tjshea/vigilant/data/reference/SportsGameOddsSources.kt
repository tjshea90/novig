package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.League
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * SportsGameOdds' game lines for the scan (SPORTSGAMEODDS_API.md §6): one `/events` request per league (a page more when the league has more games than a page holds) returns every book's
 * moneyline, spread, total, team totals, 1st-half/first-5/first-inning lines AND their alternate lines, each with the time SGO last saw the price. Unlimited objects on Pro, so the only budget is
 * the 300 requests a minute, which [SportsGameOddsClient] spaces itself under. Books: the reference books picked in Settings, plus Circa, SuperBook and bet365 ([SgoBooks.EXTRA]) when
 * [ScanSettings.sgoExtraBooks]. A league SGO has no feed for (tennis) is not [supports]ed: the other feeds keep it.
 */
class SgoGamesSource(private val client: SportsGameOddsClient, private val clock: () -> Long = System::currentTimeMillis) : ReferenceSource {
    override val id = ID
    override val displayName = "SportsGameOdds"
    override val metered = true

    override fun supports(league: League) = SgoBooks.supports(league)

    /** SGO refreshes about every 30 s; scans closer together than this share one read. */
    override fun reuseMs(settings: ScanSettings): Long = REUSE_MS

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val leagueId = SgoBooks.leagueId(league) ?: return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val ids = oddIds(settings.families, SgoConvert.Sport.of(leagueId))
        if (ids.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val wanted = SgoBooks.wanted(settings.referenceBooks, settings.sgoExtraBooks)
        val base = listOf("leagueID" to leagueId, "oddsAvailable" to "true", "limit" to SportsGameOddsClient.PAGE.toString(), "oddID" to ids.joinToString(","))
        val pages = readLeague(base, wanted, settings.sgoAltLines)
        val events = pages.events
            .filter { settings.includeLive || (!it.started && !it.live) }
            .mapNotNull { SgoConvert.toRef(it, league.oddsApiSportKey, wanted, props = false) }
        client.lastReads["games ${league.novigName}"] = describe(events, clock())
        return RefSnapshot(league.oddsApiSportKey, events, clock(), provider = ID)
    }

    /**
     * One league's pages, lightest to heaviest SGO will bear: the books Vigilant uses only (`bookmakerID`: SGO's league pages advise it to keep replies small; 82 books are sent otherwise) with alternate lines,
     * then without the alternates, then without the book filter (SGO's errors page lists `includeAltLines` and `bookmakerID` among what makes a query time out: a 504 drops them in that order).
     */
    private suspend fun readLeague(base: List<Pair<String, String>>, wanted: Set<String>, alts: Boolean): SportsGameOddsClient.SgoPages {
        val books = SgoBooks.filter(wanted).let { if (it.isEmpty()) emptyList() else listOf("bookmakerID" to it) }
        val attempts = buildList {
            add(base + books + (if (alts) listOf("includeAltLines" to "true") else emptyList()))
            if (alts) add(base + books)
            if (books.isNotEmpty()) add(base)
        }.distinct()
        var last: SgoTooHeavyException? = null
        for (a in attempts) try {
            return client.eventsAll(a)
        } catch (e: SgoTooHeavyException) {
            last = e
        }
        throw last ?: ReferenceException("SportsGameOdds: no query to try")
    }

    companion object {
        const val ID = "sgo"
        const val REUSE_MS = 25_000L

        /** The oddIDs a scan asks for: only the markets [families] price, so the reply stays small and fast (SGO's own speed advice). */
        fun oddIds(families: Set<MarketFamily>, sport: SgoConvert.Sport): List<String> = buildList {
            fun both(period: String, ml: Boolean, sp: Boolean, total: Boolean, team: Boolean) {
                if (ml) { add("points-home-$period-ml-home"); add("points-away-$period-ml-away") }
                if (sp) { add("points-home-$period-sp-home"); add("points-away-$period-sp-away") }
                if (total) { add("points-all-$period-ou-over"); add("points-all-$period-ou-under") }
                if (team) { for (t in listOf("home", "away")) { add("points-$t-$period-ou-over"); add("points-$t-$period-ou-under") } }
            }
            both("game", MarketFamily.MONEYLINE in families, MarketFamily.SPREAD in families, MarketFamily.TOTAL in families, MarketFamily.TEAM_TOTAL in families)
            if (MarketFamily.FIRST_HALF in families) {
                both("1h", ml = false, sp = true, total = true, team = false)
                if (sport == SgoConvert.Sport.BASEBALL) both("1i", ml = false, sp = false, total = true, team = false)
            }
        }
    }
}

/**
 * SportsGameOdds' player props: one request per league (pages when it has many games) with the `PLAYER_ID` wildcard for every stat Vigilant prices in that sport ([SgoProps]), each book's
 * over/under per player with its own update time. Props have no alternate lines asked for yet (heavy; SPORTSGAMEODDS_API.md §7). A prop SGO sends as a Yes/No (anytime touchdown) is read as
 * Over/Under 0.5, the way Novig lists it.
 */
class SgoPropsSource(
    private val client: SportsGameOddsClient,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where each player's injury status goes, free with every answer (SGO's `players.*.status`); the paid injury read of ParlayAPI is not made while SGO answers. */
    private val injuries: InjuryIndex? = null,
) : ReferenceSource {
    override val id = ID
    override val displayName = "SportsGameOdds props"
    override val metered = true
    override val propsOnly = true
    override val extraPropTypes: Set<String> get() = ALL_TYPES

    override fun supports(league: League): Boolean = SgoBooks.leagueId(league)?.let { SgoProps.statIds(SgoConvert.Sport.of(it)).isNotEmpty() } == true

    override fun reuseMs(settings: ScanSettings): Long = REUSE_MS

    override suspend fun odds(league: League, settings: ScanSettings): RefSnapshot {
        val leagueId = SgoBooks.leagueId(league)
        if (leagueId == null || MarketFamily.PLAYER_PROPS !in settings.families) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val sport = SgoConvert.Sport.of(leagueId)
        val ids = oddIds(sport)
        if (ids.isEmpty()) return RefSnapshot(league.oddsApiSportKey, emptyList(), clock(), provider = ID)
        val wanted = SgoBooks.wanted(settings.referenceBooks, settings.sgoExtraBooks)
        val base = listOf("leagueID" to leagueId, "oddsAvailable" to "true", "limit" to SportsGameOddsClient.PAGE.toString(), "oddID" to ids.joinToString(","))
        // The book filter first (small replies), the full book list if SGO times out on it.
        val pages = try {
            client.eventsAll(SgoBooks.filter(wanted).let { f -> if (f.isEmpty()) base else base + ("bookmakerID" to f) })
        } catch (e: SgoTooHeavyException) {
            client.eventsAll(base)
        }
        val events = pages.events
            .filter { !it.started && !it.live }
            .mapNotNull { SgoConvert.toRef(it, league.oddsApiSportKey, wanted, props = true) }
        client.lastReads["props ${league.novigName}"] = describe(events, clock())
        injuries?.let { index -> record(index, league.oddsApiSportKey, pages.events.filter { !it.started && !it.live }) }
        return RefSnapshot(league.oddsApiSportKey, events, clock(), provider = ID)
    }

    companion object {
        /** SGO's player statuses as the injury index reads them ([Injury.levelOf]); "active" and "probable" are not reports. */
        fun injuryStatus(status: String?): String? = when (status?.trim()?.lowercase()) {
            "ir" -> "Injured Reserve"
            "out" -> "Out"
            "suspended" -> "Suspended"
            "doubtful" -> "Doubtful"
            "questionable" -> "Questionable"
            else -> null
        }

        /** Every player the answer names: the ones with a report are recorded, the rest are marked as having nothing to report (so nothing asks about them). */
        fun record(index: InjuryIndex, sportKey: String, events: List<SgoEvent>) {
            val all = events.flatMap { it.players.values }
            val hurt = all.mapNotNull { p -> injuryStatus(p.status)?.let { Injury(p.name, it, comment = p.statusDetails) } }
            index.record(sportKey, hurt, asked = all.map { it.name })
        }

        const val ID = "sgo-props"
        const val REUSE_MS = 45_000L

        val ALL_TYPES: Set<String> = SgoConvert.Sport.entries.flatMap { s -> SgoProps.statIds(s).mapNotNull { SgoProps.novigStat(s, it) } }.toSet()

        fun oddIds(sport: SgoConvert.Sport): List<String> = SgoProps.statIds(sport).flatMap { stat ->
            val base = listOf("$stat-PLAYER_ID-game-ou-over", "$stat-PLAYER_ID-game-ou-under")
            if (stat in SgoProps.YES_NO) base + listOf("$stat-PLAYER_ID-game-yn-yes", "$stat-PLAYER_ID-game-yn-no") else base
        }
    }
}

/** "14 games, 3210 markets, 9 books, prices 12s to 4m old (median 41s)": what a read brought, for Diagnostics. */
internal fun describe(events: List<RefEvent>, now: Long): String {
    val markets = events.flatMap { it.markets }
    val ages = markets.mapNotNull { m -> m.lastUpdateMs?.let { ((now - it) / 1000L).coerceAtLeast(0) } }.sorted()
    val books = markets.map { it.bookKey }.toSet().size
    val age = if (ages.isEmpty()) "no update times" else "prices ${fmtAge(ages.first())} to ${fmtAge(ages.last())} old (median ${fmtAge(ages[ages.size / 2])})"
    return "${events.size} games, ${markets.size} markets, $books books, $age"
}

private fun fmtAge(s: Long) = if (s < 120) "${s}s" else if (s < 7200) "${s / 60}m" else "${s / 3600}h"
