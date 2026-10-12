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

    /** The exchanges and prediction markets among BoltOdds' sportsbooks (TASKS.md TBP6). */
    private val EXCHANGES = listOf("prophetx", "kalshi", "novig", "polymarket", "dk predictions", "fd predicts")

    suspend fun run(feed: BoltOddsFeed, settings: ScanSettings, now: Long = System.currentTimeMillis(), listenMs: Long = 15_000L, scoresListenMs: Long = 8_000L): Result {
        val client = feed.client
        // The app's own two sockets would take two of Pro's three connections and the scores probes need a free one: they close for the test and reopen with the next scan.
        feed.close()
        var gameNames: List<String> = emptyList()
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
            gameNames = games.values.map { it.game }
            val bySport = games.values.groupingBy { it.sport }.eachCount().entries.sortedByDescending { it.value }
            section("get_games", "${games.size} games\n" + bySport.joinToString("\n") { "${it.key}: ${it.value}" } + "\nfirst entries:\n" + games.values.take(3).joinToString("\n") { "${it.game} · when ${it.whenText} · universal ${it.universalId} · sport ${it.sport}" })
            sum += "${games.size} games listed" + (bySport.take(4).joinToString { "${it.key} ${it.value}" }.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "") + "."
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("get_games", "failed: ${readableError(e)}") }

        var snapshotSport: String? = null
        // The exchanges and prediction markets BoltOdds carries beside the sportsbooks: their prices say what the crowd thinks, and the sample shows what their games look like (TBP6).
        val exchanges = EXCHANGES.filter { it in info.sportsbooks }
        val probeBooks = (mine.take(6) + exchanges).distinct().ifEmpty { info.sportsbooks.take(3) }
        try {
            val markets = client.markets(sports.take(6), probeBooks)
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
            // The snapshot goes to a sport that HAS markets (the first sample's NBA had none: it was the preseason, "NBA Pre").
            val perSport = HashMap<String, Int>()
            for (bySport in markets.values) for ((sport, names) in bySport) if (BoltBooks.leagueOfSport(sport) != null) perSport.merge(sport, names.size, Int::plus)
            snapshotSport = perSport.entries.filter { it.value > 0 }.maxByOrNull { it.value }?.key
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("get_markets", "failed: ${readableError(e)}") }

        val book = mine.firstOrNull() ?: info.sportsbooks.firstOrNull()
        val sport = snapshotSport ?: sports.firstOrNull() ?: info.sports.firstOrNull()
        if (book != null && sport != null) {
            val perBook = LinkedHashMap<String, ArrayList<String>>()
            val gamesPerBook = HashMap<String, Int>()
            val board = BoltBoard()
            val t0 = System.currentTimeMillis()
            try {
                val n = client.snapshotStream(BoltFilters(sports = listOf(sport), sportsbooks = probeBooks)) { t ->
                    val msg = (kotlinx.serialization.json.Json.parseToJsonElement(t) as? kotlinx.serialization.json.JsonObject)?.let { BoltParser.message(it, now, true) }
                    val gameBook = (msg as? BoltMessage.GameUpdate)?.data?.sportsbook
                    if (gameBook != null) {
                        gamesPerBook.merge(gameBook, 1, Int::plus)
                        val kept = perBook.getOrPut(gameBook) { ArrayList() }
                        if (kept.size < 2) kept += t.take(2_500)
                        board.apply(msg)
                    }
                }
                section(
                    "snapshot ($sport · ${probeBooks.size} books: ${probeBooks.joinToString(", ")}; no market filter)",
                    "$n games in ${System.currentTimeMillis() - t0} ms\ngames per book: " + gamesPerBook.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" } +
                        "\nthe first two games of each book (cut at 2,500 characters):\n" + perBook.entries.joinToString("\n\n") { (b, list) -> "--- $b\n" + list.joinToString("\n") } +
                        "\nparsed into ${board.summary().outcomes} usable outcomes across ${board.summary().games} games; outcomes per book: " + board.summary().byBook.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" } +
                        "\nunmapped names: " + board.unmapped.entries.sortedByDescending { it.value }.take(25).joinToString { "${it.key} ×${it.value}" },
                )
                sum += "Snapshot of $sport at ${gamesPerBook.size} books: $n games in ${System.currentTimeMillis() - t0} ms (${gamesPerBook.entries.sortedByDescending { it.value }.take(5).joinToString { "${it.key} ${it.value}" }})."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("snapshot ($sport · ${probeBooks.joinToString(", ")})", "failed: ${readableError(e)}") }
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

        // The listening probe: a socket of its own (the app's two were closed above, so Pro's three connections are free).
        if (book != null && sport != null) {
            val probe = feed.probeStream()
            try {
                probe.want(BoltFilters(sports = listOf(sport), sportsbooks = probeBooks))
                probe.awaitReady(20_000L)
                delay(listenMs)
                val sb = StringBuilder("state ${probe.state}, connects ${probe.connects}, frames ${probe.frames} (${probe.arrayFrames} arrays), biggest frame ${probe.biggestFrame} characters, snapshot ${probe.snapshotGames} games\n")
                probe.connectionNotes()?.let { sb.append(it).append('\n') }
                sb.append("messages by action: ").append(probe.board.counts.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" }).append('\n')
                if (probe.pings.size >= 2) sb.append("ping interval: ").append(probe.pings.zipWithNext { a, b -> (b - a) / 1000.0 }.joinToString(", ") { String.format(java.util.Locale.US, "%.1fs", it) }).append('\n')
                else sb.append("pings seen: ${probe.pings.size}\n")
                probe.lastClose?.let { sb.append("last close: $it\n") }
                probe.lastError?.let { sb.append("last error: $it\n") }
                probe.snapshotError?.let { sb.append("snapshot error: $it\n") }
                if (probe.rawFrames.isNotEmpty()) sb.append("the first raw frames:\n").append(probe.rawFrames.joinToString("\n") { "  $it" }).append('\n')
                sb.append("first message of each kind (cut at 200 characters):\n").append(probe.examples.entries.sortedBy { it.key }.joinToString("\n") { "  ${it.key}: ${it.value}" })
                section("websocket probe ($sport · ${probeBooks.size} books, ${listenMs / 1000} s)", sb.toString())
                sum += "Odds socket: ${probe.state.name.lowercase()}, ${probe.frames} frames (${probe.textFrames} text, ${probe.binaryFrames} binary) in ${listenMs / 1000} s" + (probe.board.counts["line_update"]?.let { ", $it line updates" } ?: "") + "."
            } finally {
                probe.close()
            }
        }

        // The scores sockets (the docs: Trial / Custom). A subscription with two games: only live games send updates, but a refusal or an acceptance is the answer.
        val gamesFilter = gameNames.take(2).takeIf { it.isNotEmpty() }?.let { names ->
            kotlinx.serialization.json.buildJsonObject {
                put("action", kotlinx.serialization.json.JsonPrimitive("subscribe"))
                put("filters", kotlinx.serialization.json.buildJsonObject { put("games", kotlinx.serialization.json.buildJsonArray { names.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }) })
            }.toString()
        }
        for (path in listOf("/livescores", "/playbyplay")) {
            try {
                val result = feed.probeRaw(path, gamesFilter, scoresListenMs)
                section("websocket $path (is it part of this key?)", result)
                sum += "Scores socket $path: " + result.lines().firstOrNull { it.contains("closed") || it.contains("failed") || it.contains("frame") }?.substringAfter(' ')?.take(120).orEmpty().ifEmpty { "opened, silent" } + "."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { section("websocket $path", "failed: ${readableError(e)}") }
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
