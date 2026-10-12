package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.readableError
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Settings › BoltOdds › Test key (BOLTODDS_API.md §8): what the key actually carries, said in words, and a SAMPLE of the real answers for Claude to check the parser against (the parser was written from the docs;
 * nothing in it has met a live key). It reads `get_info` (the sports and sportsbooks, and which of them Vigilant prices with), `get_games`, `get_markets` for Vigilant's leagues with what [BoltMarkets] made of every
 * market name, one small snapshot (one sport, one book), the optional `get_parlays` and the two scores `.../user` endpoints, then LISTENS on a socket of its own for [listenMs] and records every kind of message it
 * heard, the first of each, the keep-alive interval and whether frames came as arrays. The GETs go through the client's gates (12 a minute), so the whole test takes about half a minute.
 */
object BoltKeyTest {
    class Result(val ok: Boolean, val summary: String, val sample: String)

    suspend fun run(feed: BoltOddsFeed, settings: ScanSettings, now: Long = System.currentTimeMillis(), listenMs: Long = 15_000L): Result {
        val client = feed.client
        val sum = ArrayList<String>()
        val out = StringBuilder("BoltOdds sample · ${Instant.ofEpochMilli(now)}\nThe parser was written from the docs; this is the real answer it must be checked against (BOLTODDS_API.md §8).\n")
        fun section(title: String, body: String) { out.append("\n===== ").append(title).append(" =====\n").append(client.redact(body)).append('\n') }

        val info = try { client.info() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            return Result(false, "BoltOdds: ${client.redact(readableError(e))}", "get_info failed: ${client.redact(readableError(e))}\n")
        }
        val known = info.sportsbooks.associateWith { BoltBooks.appKey(it) }
        val wanted = BoltBooks.wanted(settings)
        val mine = info.sportsbooks.filter { s -> known[s]?.let { wanted == null || it in wanted } == true }
        val sharp = listOf("pinnacle", "circa", "bet365", "bookmaker", "betonline", "superbook").associateWith { s -> info.sportsbooks.any { BoltBooks.appKey(it) == s } }
        section(
            "get_info",
            "sports (${info.sports.size}): ${info.sports.joinToString(", ")}\n" +
                "sportsbooks (${info.sportsbooks.size}): " + info.sportsbooks.joinToString(", ") { s -> if (known[s] == null) "$s [never the fair line]" else if (known[s] == s) s else "$s → ${known[s]}" } + "\n" +
                "books Vigilant prices with that BoltOdds carries (${mine.size}): ${mine.joinToString(", ")}\n" +
                "sharp books present: " + sharp.entries.joinToString(", ") { (k, v) -> "$k ${if (v) "yes" else "NO"}" },
        )
        val leagues = settings.selectedLeagues.filter { BoltBooks.supports(it) }.ifEmpty { listOf("NBA", "NFL", "MLB", "NHL").mapNotNull { Leagues.byNovigName(it) } }
        val sportsOf = leagues.associateWith { BoltBooks.sportsFor(it, info.sports) }
        val sports = sportsOf.values.flatten().distinct()
        sum += "BoltOdds key accepted: ${info.sports.size} sports, ${info.sportsbooks.size} sportsbooks, ${mine.size} of them books Vigilant prices with."
        sum += "Sharp books: " + sharp.entries.filter { it.value }.joinToString(", ") { it.key }.ifEmpty { "none of Pinnacle, Circa, bet365, Bookmaker, BetOnline, SuperBook" }
        if (sports.isEmpty()) sum += "None of the leagues picked in Settings is a BoltOdds sport name (sports: ${info.sports.take(12).joinToString(", ")}…)."

        try {
            val games = client.games()
            val bySport = games.values.groupingBy { it.sport }.eachCount().entries.sortedByDescending { it.value }
            section("get_games", "${games.size} games\n" + bySport.joinToString("\n") { "${it.key}: ${it.value}" } + "\nfirst entries:\n" + games.values.take(3).joinToString("\n") { "${it.game} · when ${it.whenText} · universal ${it.universalId} · sport ${it.sport}" })
            sum += "${games.size} games listed" + (bySport.take(4).joinToString { "${it.key} ${it.value}" }.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "") + "."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("get_games", "failed: ${readableError(e)}") }

        var snapshotSport: String? = null
        try {
            val books = mine.take(6).ifEmpty { info.sportsbooks.take(3) }
            val markets = client.markets(sports.take(6), books)
            val text = StringBuilder()
            for ((book, bySport) in markets) for ((sport, names) in bySport) {
                val league = BoltBooks.leagueOfSport(sport)
                text.append("$book · $sport (${names.size}): ")
                text.append(names.joinToString("; ") { n -> if (league == null) n else "$n ${verdict(league, n)}" }).append('\n')
            }
            section("get_markets (Vigilant's leagues, first books)", text.toString().ifBlank { "(none)" })
            val lineNames = markets.values.flatMap { it.entries }.flatMap { (sport, names) -> BoltBooks.leagueOfSport(sport)?.let { l -> names.filter { BoltMarkets.usableName(l, it, false) } } ?: emptyList() }.distinct()
            val propNames = markets.values.flatMap { it.entries }.flatMap { (sport, names) -> BoltBooks.leagueOfSport(sport)?.let { l -> names.filter { BoltMarkets.usableName(l, it, true) } } ?: emptyList() }.distinct()
            sum += "Market names Vigilant can use: ${lineNames.size} line markets, ${propNames.size} player-prop markets."
            snapshotSport = markets.values.flatMap { it.keys }.firstOrNull { BoltBooks.leagueOfSport(it) != null }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("get_markets", "failed: ${readableError(e)}") }

        val book = mine.firstOrNull() ?: info.sportsbooks.firstOrNull()
        val sport = snapshotSport ?: sports.firstOrNull() ?: info.sports.firstOrNull()
        if (book != null && sport != null) {
            val kept = ArrayList<String>()
            val t0 = System.currentTimeMillis()
            try {
                val n = client.snapshotStream(BoltFilters(sports = listOf(sport), sportsbooks = listOf(book))) { if (kept.size < 3) kept += it.take(2_500) }
                val board = BoltBoard()
                var applied = 0
                kept.forEach { t -> (BoltParser.message(kotlinx.serialization.json.Json.parseToJsonElement(t) as? kotlinx.serialization.json.JsonObject ?: return@forEach, now, true))?.let { if (board.apply(it)) applied++ } }
                section("snapshot ($sport · $book, no market filter)", "$n games in ${System.currentTimeMillis() - t0} ms\nthe first ${kept.size} (cut at 2,500 characters):\n" + kept.joinToString("\n\n") + "\nparsed into ${board.summary().outcomes} usable outcomes across ${applied} games; unmapped names: " + board.unmapped.entries.sortedByDescending { it.value }.take(25).joinToString { "${it.key} ×${it.value}" })
                sum += "Snapshot of $sport at $book: $n games in ${System.currentTimeMillis() - t0} ms."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("snapshot ($sport · $book)", "failed: ${readableError(e)}") }
            try {
                val parlays = client.parlaysRaw(book)
                section("get_parlays ($book)", "${parlays.length} characters\n" + parlays.take(800))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("get_parlays ($book)", "failed: ${readableError(e)}") }
        }
        for (path in listOf("/playbyplay/user", "/livescores/user")) {
            try {
                val (code, body) = client.probe(path)
                section("GET $path (is the scores feed part of this key?)", "HTTP $code: ${body.take(400)}")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("GET $path", "failed: ${readableError(e)}") }
        }

        // The listening probe: a socket of its own (Pro allows three; the app's two are lines and props).
        if (book != null && sport != null) {
            val probe = feed.probeStream()
            try {
                probe.want(BoltFilters(sports = listOf(sport), sportsbooks = listOf(book)))
                probe.awaitReady(20_000L)
                delay(listenMs)
                val sb = StringBuilder("state ${probe.state}, connects ${probe.connects}, frames ${probe.frames} (${probe.arrayFrames} arrays), biggest frame ${probe.biggestFrame} characters, snapshot ${probe.snapshotGames} games\n")
                sb.append("messages by action: ").append(probe.board.counts.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" }).append('\n')
                if (probe.pings.size >= 2) sb.append("ping interval: ").append(probe.pings.zipWithNext { a, b -> (b - a) / 1000.0 }.joinToString(", ") { String.format(java.util.Locale.US, "%.1fs", it) }).append('\n')
                else sb.append("pings seen: ${probe.pings.size}\n")
                probe.lastError?.let { sb.append("last error: $it\n") }
                probe.snapshotError?.let { sb.append("snapshot error: $it\n") }
                sb.append("first message of each kind (cut at 200 characters):\n").append(probe.examples.entries.sortedBy { it.key }.joinToString("\n") { "  ${it.key}: ${it.value}" })
                section("websocket probe ($sport · $book, ${listenMs / 1000} s)", sb.toString())
                sum += "Socket: ${probe.state.name.lowercase()}, ${probe.frames} frames in ${listenMs / 1000} s" + (probe.board.counts["line_update"]?.let { ", $it line updates" } ?: "") + "."
            } finally {
                probe.close()
            }
        }
        return Result(true, sum.joinToString("\n"), out.toString())
    }

    private fun verdict(league: String, name: String): String {
        val l = BoltMarkets.usableName(league, name, false)
        val p = BoltMarkets.usableName(league, name, true)
        return when {
            l -> "[line]"
            p -> "[prop ${BoltMarkets.stat(league, name.lowercase().filter { it.isLetterOrDigit() }) ?: ""}]"
            else -> "[—]"
        }
    }
}
