package com.tjshea.vigilant.data.reference

/**
 * Settings › SportsGameOdds Pro › Test key (SPORTSGAMEODDS_API.md §7): `/account/usage` for the key's real limits, then one small `/events` read (alternate lines and open/close odds on) to say
 * what the key actually carries: how many games and markets, which books, how old the prices are, whether alternates and closing lines came. The raw answer is returned too, so a sample can be
 * saved and sent to Claude: the parser was written from the docs, and this is how it is checked against the real thing.
 */
object SgoKeyTest {
    class Result(val ok: Boolean, val summary: String, val sample: String)

    private val LEAGUES = listOf("NFL", "NBA", "NHL", "MLB", "NCAAF", "NCAAB", "WNBA")

    suspend fun run(client: SportsGameOddsClient, now: Long = System.currentTimeMillis()): Result {
        val lines = ArrayList<String>()
        val usage = runCatching { client.usage() }
        usage.getOrNull()?.let { lines += "Limits: ${it.summary()}" } ?: usage.exceptionOrNull()?.let { lines += "Limits unreadable: ${readableError(it)}" }
        var raw = ""
        var page: SgoPage? = null
        var leagueUsed = ""
        for (league in LEAGUES) {
            val answer = runCatching { client.raw("/events", listOf("leagueID" to league, "oddsAvailable" to "true", "limit" to "2", "includeAltLines" to "true", "includeOpenCloseOdds" to "true")) }
            val body = answer.getOrElse { e -> return Result(false, (lines + "Could not read events: ${readableError(e)}").joinToString("\n"), "") }
            val p = SgoParser.page(body)
            if (p.events.isNotEmpty()) { raw = body; page = p; leagueUsed = league; break }
            if (raw.isEmpty()) raw = body
        }
        val p = page ?: return Result(true, (lines + "The key works, but no league has a game with odds right now.").joinToString("\n"), raw)
        val odds = p.events.flatMap { it.odds }
        val perBook = HashMap<String, MutableList<Long>>()
        var alts = 0
        var closes = 0
        for (o in odds) {
            for ((book, ls) in o.byBook) {
                ls.forEach { l -> l.updatedMs?.let { perBook.getOrPut(book) { ArrayList() }.add(it) } }
                alts += ls.count { !it.main }
            }
            closes += o.openClose.size
        }
        val ages = perBook.mapValues { (_, v) -> ((now - v.max()) / 1000L).coerceAtLeast(0) }
        lines += "$leagueUsed: ${p.events.size} games read, ${odds.size} markets, ${perBook.size} books priced, $alts alternate lines, $closes open/close values."
        if (ages.isNotEmpty()) {
            val freshest = ages.minBy { it.value }
            val sharp = listOf("pinnacle", "circa", "draftkings", "fanduel").mapNotNull { b -> ages[b]?.let { "$b ${it}s" } }
            lines += "Newest price ${freshest.value}s old ($sharp)".replace("($sharp)", if (sharp.isEmpty()) "" else "· " + sharp.joinToString(", "))
        }
        lines += "Books: " + perBook.keys.sorted().joinToString(", ")
        p.notice?.let { lines += "Plan notice: $it" }
        lines += variants(client, leagueUsed)
        return Result(true, lines.joinToString("\n"), raw)
    }

    /**
     * Which way of asking is fastest on THIS key (SGO's pages disagree on `bookmakerID` and `includeAltLines`): the same league, two games, with and without each, timed, plus whether the `oddID` filter was honored
     * (the docs name it `oddID` in the OpenAPI and `oddIDs` in some examples). The numbers go in the sample so the order Vigilant asks in can be tuned to what the key really does.
     */
    private suspend fun variants(client: SportsGameOddsClient, league: String): List<String> {
        val ids = SgoGamesSource.oddIds(setOf(com.tjshea.vigilant.data.scanner.MarketFamily.MONEYLINE, com.tjshea.vigilant.data.scanner.MarketFamily.SPREAD, com.tjshea.vigilant.data.scanner.MarketFamily.TOTAL), SgoConvert.Sport.of(league)).joinToString(",")
        val books = SgoBooks.filter(SgoBooks.wanted(com.tjshea.vigilant.data.scanner.ScanSettings().referenceBooks, true))
        val base = listOf("leagueID" to league, "oddsAvailable" to "true", "limit" to "2")
        val tries = listOf(
            "oddID only" to base + ("oddID" to ids),
            "oddID + bookmakerID" to base + ("oddID" to ids) + ("bookmakerID" to books),
            "oddID + bookmakerID + alternates" to base + ("oddID" to ids) + ("bookmakerID" to books) + ("includeAltLines" to "true"),
            "oddID + alternates (all books)" to base + ("oddID" to ids) + ("includeAltLines" to "true"),
            "no filters at all" to base,
        )
        val out = ArrayList<String>()
        out += "Timing, 2 games of $league:"
        for ((name, params) in tries) {
            val t0 = System.currentTimeMillis()
            val body = runCatching { client.raw("/events", params) }
            val ms = System.currentTimeMillis() - t0
            val b = body.getOrNull()
            if (b == null) { out += "  $name: failed in ${ms} ms (${readableError(body.exceptionOrNull()!!)})"; continue }
            val page = SgoParser.page(b)
            val odds = page.events.flatMap { it.odds }
            val books = odds.flatMap { it.byBook.keys }.toSet().size
            out += "  $name: ${ms} ms, ${b.length / 1024} KB, ${odds.size} markets, $books books" + if (name == "oddID only") " · oddID filter ${if (odds.size <= ids.split(',').size * page.events.size) "honored" else "NOT honored (switch to oddIDs)"}" else ""
        }
        return out
    }
}
