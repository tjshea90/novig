package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings

/**
 * Settings › Unabated › Test key (UNABATED_API.md §4): what this Free key really carries. The docs do not list the Free key's leagues, bet-type names or prop naming, and the parser was written from the
 * docs' examples, so this reads `/league`, `/bettype`, one league's sources, then ONE straight board and ONE props board (two odds requests, ~10 s: the Free interval), and says what came: leagues, books by
 * Vigilant's name, how many games and lines became reference events, how old the prices are, which prop bet types have no Vigilant stat. The raw answers are returned ([Result.sample]) so a sample can be
 * saved and sent to Claude to check the parser against. The key is never in the text.
 */
object UnabatedKeyTest {
    class Result(val ok: Boolean, val summary: String, val sample: String)

    suspend fun run(client: UnabatedClient, feed: UnabatedFeed, now: Long = System.currentTimeMillis()): Result {
        val lines = ArrayList<String>()
        val sample = StringBuilder()
        fun raw(title: String, body: String, max: Int = 40_000) { sample.append("=== ").append(title).append(" ===\n").append(body.take(max)).append(if (body.length > max) "\n…(${body.length - max} more characters)\n" else "\n").append('\n') }
        val leagues: List<UnLeague>
        try {
            val body = client.get("/league")
            raw("GET /league", body, 20_000)
            leagues = UnabatedParser.leagues(body)
        } catch (e: Exception) {
            val m = readableError(e)
            val hint = if (m.contains("401")) "\nThe key was refused. Free API access is tied to Unabated's content-partner terms and can be revoked: check https://tools.unabated.com/api-keys." else ""
            return Result(false, "Could not read the key's leagues: $m$hint", sample.toString())
        }
        val ours = Leagues.ALL.filter { it.novigName in setOf("NFL", "NCAAF", "NBA", "NCAAB", "WNBA", "MLB", "NHL") }
        val routes = ours.associateWith { UnabatedBooks.routeFor(it, leagues) }
        lines += "${leagues.size} leagues on this key: " + leagues.take(40).joinToString(", ") { it.name }
        lines += "Vigilant's leagues found: " + (routes.entries.filter { it.value != null }.joinToString(", ") { "${it.key.novigName}=${it.value}" }.ifEmpty { "none" }) +
            routes.entries.filter { it.value == null }.map { it.key.novigName }.takeIf { it.isNotEmpty() }?.let { "; not on this key: ${it.joinToString(", ")}" }.orEmpty()
        val first = routes.entries.firstOrNull { it.value != null } ?: return Result(false, lines.joinToString("\n") + "\nNone of Vigilant's leagues is on this key.", sample.toString())
        val league = first.key
        val route = first.value!!
        runCatching { client.betTypes() }.onSuccess { lines += "${it.size} bet types: " + it.take(30).joinToString(", ") { b -> "${b.id}=${b.name.ifBlank { "?" }}" } }
            .onFailure { lines += "Bet types: ${readableError(it)}" }
        val sources = runCatching { client.sources(route, "straight") }.getOrElse { lines += "Sources: ${readableError(it)}"; emptyList() }
        if (sources.isNotEmpty()) {
            val wanted = UnabatedBooks.sourceMap(sources, ScanSettings().referenceBooks.toSet())
            lines += "$route books (${sources.size}): " + sources.joinToString(", ") { "${it.name}#${it.id}${if (it.statusId == 3) " (offline)" else ""}" }
            lines += "Vigilant fair-line books found: " + (wanted.values.sorted().joinToString(", ").ifEmpty { "none" })
        }
        for (type in listOf("straight", "props")) {
            try {
                val body = client.oddsRaw(route, type)
                raw("GET /market/$route/$type/odds (${body.length} characters)", body, 60_000)
                val board = UnabatedParser.board(body, includeLive = false)
                val read = feed.read(league, type, ScanSettings(families = MarketFamily.entries.toSet()))
                lines += "$route $type: ${board.groups.size} groups; as reference events: ${read?.summary ?: "no events"}"
                board.lastUpdatedMs?.let { lines += "  board updated ${(now - it) / 1000L}s ago (Free data is delayed at least 15 s)" }
                if (type == "props") {
                    val types = runCatching { client.betTypes() }.getOrNull().orEmpty().associateBy { it.id }
                    val unmapped = board.groups.map { it.betTypeId }.distinct().mapNotNull { types[it]?.name }.filter { n ->
                        val k = n.lowercase().filter { c -> c.isLetterOrDigit() }
                        OpProps.stat(league.novigName, k.removePrefix("player")) == null && OpProps.stat(league.novigName, k) == null
                    }
                    if (unmapped.isNotEmpty()) lines += "  Prop bet types with no Vigilant stat (left unpriced): " + unmapped.take(40).joinToString(", ")
                }
            } catch (e: Exception) {
                lines += "$route $type: ${readableError(e)}"
            }
        }
        val ok = lines.none { it.startsWith("$route straight:") && it.contains("Unabated") && it.contains("403") }
        return Result(ok, lines.joinToString("\n"), sample.toString())
    }
}
