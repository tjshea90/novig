package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * Settings › OddsPapi › Test key (ODDSPAPI_API.md §8): what this key really carries. The docs do not say a trial's books, sports, quota or prop market names, and the parser and the market table were
 * written from the OpenAPI examples, so this reads the key's own catalogue (`/bookmakers`, `/tournaments`, `/markets`), one tournament's main lines and one game in depth, and says what came: books by
 * Vigilant's name, the tournaments found per league, the prop `marketType`s the table has no stat for, how old the prices are, whether alternates, props and closing lines arrived. The raw answers
 * are returned too ([Result.sample]) so a sample can be saved and sent to Claude to check the parser against the real thing.
 */
object OpKeyTest {
    class Result(val ok: Boolean, val summary: String, val sample: String)

    suspend fun run(client: OddsPapiClient, now: Long = System.currentTimeMillis()): Result {
        val lines = ArrayList<String>()
        val sample = StringBuilder()
        fun raw(title: String, body: String, max: Int = 40_000) { sample.append("=== ").append(title).append(" ===\n").append(body.take(max)).append(if (body.length > max) "\n…(${body.length - max} more characters)\n" else "\n").append('\n') }
        val catalog: List<OpBookInfo>
        try {
            val body = client.get("/bookmakers")
            raw("GET /bookmakers", body, 12_000)
            catalog = OpParser.bookmakers(body)
        } catch (e: Exception) {
            val m = readableError(e)
            val hint = if (m.contains("401")) "\nVigilant speaks OddsPapi v5 (v5.oddspapi.io), which comes from a trial or contract arranged with contact@55-tech.com. A key from the self-serve plan builder (api.oddspapi.io/v4) is a different product and is refused here." else ""
            return Result(false, "Could not read the key's books: $m$hint", sample.toString())
        }
        val ours = OpBooks.wanted(ScanSettings().referenceBooks, true)
        val found = catalog.filter { OpBooks.appKey(it.slug, it.name) in ours }
        val missing = ours.filter { k -> found.none { OpBooks.appKey(it.slug, it.name) == k } }
        lines += "${catalog.size} books on this key; ${found.size} of Vigilant's ${ours.size} fair-line books found: " + found.joinToString(", ") { it.slug }
        if (missing.isNotEmpty()) lines += "Not on this key (or named differently): " + missing.joinToString(", ") + ". All slugs: " + catalog.take(120).joinToString(", ") { it.slug }
        catalog.firstOrNull { OpBooks.appKey(it.slug, it.name) == "pinnacle" }?.let { lines += "Pinnacle: ${it.slug}, documented delay pregame ≤ ${it.maxDelayPregameSec ?: "?"} s, live ≤ ${it.maxDelayLiveSec ?: "?"} s" }
        client.lastRemaining?.let { lines += "Rate limit left (last answer): $it" }

        val feed = OddsPapiFeed(client)
        var tried = false
        var sampleLeague: com.tjshea.vigilant.data.scanner.League? = null
        var sampleTournament: OpTournament? = null
        for (league in Leagues.ALL.filter { OpBooks.supports(it) }) {
            val t = runCatching { feed.tournamentFor(league) }.getOrNull()
            if (t == null) { lines += "${league.novigName}: no tournament found in the catalogue"; continue }
            val markets = runCatching { feed.marketsFor(league) }.getOrNull()
            lines += "${league.novigName}: tournament \"${t.name}\" (${t.category}, id ${t.id}); ${markets?.size ?: "?"} markets in the catalogue" +
                (markets?.takeIf { it.propTypes.isNotEmpty() }?.let { "; ${it.propTypes.size} prop types, ${it.unmappedProps.size} without a Vigilant stat" } ?: "")
            if (markets != null && markets.unmappedProps.isNotEmpty() && !tried) lines += "  Unmapped prop types (${league.novigName}): " + markets.unmappedProps.sorted().take(60).joinToString(", ")
            if (sampleLeague == null) { sampleLeague = league; sampleTournament = t }
            tried = true
        }
        val league = sampleLeague
        val tournament = sampleTournament
        if (league == null || tournament == null) return Result(true, (lines + "The key works, but no US league was found in its tournaments.").joinToString("\n"), sample.toString())

        val settings = ScanSettings(oddsPapi = true)
        val slugs = OpBooks.slugsFor(OpBooks.wanted(settings.referenceBooks, true), catalog)
        val bookParam = if (slugs.isEmpty()) emptyList() else listOf("bookmakers" to slugs.joinToString(","))
        val t0 = System.currentTimeMillis()
        val mainRaw = try { client.get("/fixtures/odds/main", listOf("tournamentId" to tournament.id.toString()) + bookParam) } catch (e: Exception) {
            return Result(true, (lines + "Main lines of ${league.novigName}: ${readableError(e)}").joinToString("\n"), sample.toString())
        }
        val mainMs = System.currentTimeMillis() - t0
        raw("GET /fixtures/odds/main?tournamentId=${tournament.id}", mainRaw, 120_000)
        val fixtures = OpParser.fixtures(mainRaw)
        lines += "${league.novigName} main lines: ${fixtures.size} games, ${mainRaw.length / 1024} KB in $mainMs ms, ${fixtures.sumOf { it.prices.size }} prices, books: " + fixtures.flatMap { f -> f.prices.map { it.book } }.toSet().sorted().joinToString(", ")
        val markets = feed.marketsFor(league)
        val ref = fixtures.mapNotNull { f -> OpConvert.toRef(f, markets, league.oddsApiSportKey, OpBooks.wanted(settings.referenceBooks, true), now, emptyMap(), props = false) }
        lines += "  read as Vigilant lines: " + describe(ref, now)
        val kinds = fixtures.flatMap { it.prices }.mapNotNull { p -> (p.marketId ?: markets.marketOf(p.outcomeId))?.let { markets.classify(it) } }.groupingBy { "${it.kind}/p${it.period}" }.eachCount()
        lines += "  line kinds (as read): " + kinds.entries.sortedBy { it.key }.joinToString(", ") { "${it.key} ${it.value}" }
        val stale = fixtures.flatMap { f -> f.books.entries.filter { it.value.stale }.map { "${f.p1}: ${it.key}" } }
        if (stale.isNotEmpty()) lines += "  books flagged stale: " + stale.take(8).joinToString("; ")
        val rotated = fixtures.flatMap { f -> f.books.entries.filter { it.value.rotated }.map { it.key } }.toSet()
        if (rotated.isNotEmpty()) lines += "  books with rotated participants (skipped): " + rotated.sorted().joinToString(", ")

        val game = fixtures.firstOrNull { it.startMs != null && !it.started } ?: fixtures.firstOrNull()
        if (game != null) {
            val t1 = System.currentTimeMillis()
            val deepRaw = try { client.get("/fixtures/odds", listOf("fixtureId" to game.id) + bookParam) } catch (e: Exception) { lines += "One game in depth: ${readableError(e)}"; null }
            if (deepRaw != null) {
                val ms = System.currentTimeMillis() - t1
                raw("GET /fixtures/odds?fixtureId=${game.id}", deepRaw, 150_000)
                val deep = OpParser.fixtures(deepRaw).firstOrNull()
                if (deep != null) {
                    val propPrices = deep.prices.filter { it.playerId != 0L }
                    lines += "${game.p1} vs ${game.p2} in depth: ${deepRaw.length / 1024} KB in $ms ms, ${deep.prices.size} prices, ${deep.prices.count { !it.mainLine }} not main lines, ${propPrices.size} player prices (${propPrices.map { it.playerId }.toSet().size} players)"
                    val types = deep.prices.mapNotNull { p -> (p.marketId ?: markets.marketOf(p.outcomeId)) }.toSet().size
                    lines += "  distinct markets priced: $types"
                    val ages = deep.prices.mapNotNull { it.changedMs }.map { ((now - it) / 1000L).coerceAtLeast(0) }.sorted()
                    if (ages.isNotEmpty()) lines += "  price age since last CHANGE: freshest ${ages.first()} s, median ${ages[ages.size / 2]} s, oldest ${ages.last()} s (a steady line is old; main lines of connected books are read as current)"
                    val names = deep.prices.map { it.playerId }.filter { it != 0L }.toSet().take(5)
                    if (names.isNotEmpty()) runCatching { client.players(names) }.getOrNull()?.let { raw("GET /players (5 of the game's players)", it.entries.joinToString("\n") { e -> "${e.key}: ${e.value}" }) }
                    val full = OpConvert.toRef(deep, markets, league.oddsApiSportKey, OpBooks.wanted(settings.referenceBooks, true), now, emptyMap(), props = false)
                    lines += "  read as Vigilant lines (no props): " + describe(listOfNotNull(full), now)
                    runCatching { client.get("/fixtures/odds/clv", listOf("fixtureId" to game.id, "bookmakers" to (slugs.firstOrNull { it.contains("pinnacle", true) } ?: slugs.firstOrNull() ?: "pinnacle"))) }
                        .onSuccess { raw("GET /fixtures/odds/clv", it, 20_000); val c = OpParser.clv(it); lines += "  closing-line endpoint: ${c.size} prices, ${c.count { it.closeDecimal != null }} with a close, ${c.count { it.openDecimal != null }} with an open" }
                        .onFailure { lines += "  closing-line endpoint: ${readableError(it)}" }
                }
            }
        }
        client.lastRemaining?.let { lines += "Rate limit left after the test: $it" }
        lines += "Props wanted by the scan: ${if (MarketFamily.PLAYER_PROPS in settings.families) "yes" else "no"}"
        return Result(true, lines.joinToString("\n"), sample.toString())
    }
}
