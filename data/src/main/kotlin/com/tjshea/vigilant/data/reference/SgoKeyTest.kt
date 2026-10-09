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
        return Result(true, lines.joinToString("\n"), raw)
    }
}
