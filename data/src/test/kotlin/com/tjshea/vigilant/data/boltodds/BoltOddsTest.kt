package com.tjshea.vigilant.data.boltodds

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.RefBookMarket
import com.tjshea.vigilant.data.reference.Side
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.Instant

/**
 * BoltOdds (BOLTODDS_API.md): the parser, the market table, the board and the converter, against messages built in the shapes the docs print (the Mets / Angels `game_update` and `line_update`, the
 * clear messages with `{}` for "none"). No live key has been used: when Tj's Test-key sample arrives, a test is added from its real answers (TASKS.md TBO5).
 */
class BoltOddsTest {
    private val docsGameUpdate = """{
        "timestamp": "2025-07-23T19:46:10.023175+00:00", "action": "game_update",
        "data": {
            "sport": "MLB", "sportsbook": "ballybet", "game": "New York Mets vs Los Angeles Angels, 2025-07-23, 01",
            "home_team": "New York Mets", "away_team": "Los Angeles Angels",
            "info": {"id": 1022036664, "game": "New York Mets vs Los Angeles Angels, 2025-07-23, 01", "when": "2025-07-23, 01:09 PM", "link": "https://play.ballybet.ca/sports#event/1022036664"},
            "outcomes": {
                "New York Mets Moneyline": {"odds": "-3333", "link": "https://play.ballybet.ca/x", "outcome_name": "Moneyline", "outcome_line": null, "outcome_over_under": null, "outcome_target": "New York Mets"},
                "Los Angeles Angels Moneyline": {"odds": "+900", "link": "https://play.ballybet.ca/y", "outcome_name": "Moneyline", "outcome_line": null, "outcome_over_under": null, "outcome_target": "Los Angeles Angels"}
            }
        }
    }"""

    private val now = Instant.parse("2026-10-20T15:00:00Z").toEpochMilli()

    // ---- parser -----------------------------------------------------------------------------------------------------------------------------------------------------------------------

    @Test fun `the docs game_update example is read`() {
        val m = BoltParser.frame(docsGameUpdate, now).single() as BoltMessage.GameUpdate
        assertEquals(Instant.parse("2025-07-23T19:46:10.023175Z").toEpochMilli(), m.atMs)
        assertEquals("MLB", m.data.sport)
        assertEquals("ballybet", m.data.sportsbook)
        assertEquals("New York Mets", m.data.home)
        assertEquals("Los Angeles Angels", m.data.away)
        assertEquals("1022036664", m.data.infoId)
        // "2025-07-23, 01:09 PM" is Eastern time (EDT, UTC-4)
        assertEquals(Instant.parse("2025-07-23T17:09:00Z").toEpochMilli(), m.data.startMs)
        assertEquals(listOf(-3333, 900), m.data.outcomes.map { it.odds })
        assertEquals("New York Mets", m.data.outcomes[0].target)
        assertNull(m.data.outcomes[0].line)
    }

    @Test fun `the Eastern time reading matches the docs' own tennis match`() {
        // match_info says the match is at 20:00 UTC on 2026-02-26 and BoltOdds' id for it ends ", 03": 3 PM Eastern (EST, UTC-5).
        assertEquals(Instant.parse("2026-02-26T20:00:00Z").toEpochMilli(), BoltParser.whenMs("2026-02-26, 03:00 PM"))
        assertEquals(Instant.parse("2026-02-26T20:00:00Z").toEpochMilli(), BoltParser.whenMs("2026-02-26, 15:00"))
        assertNull(BoltParser.whenMs("tomorrow"))
        assertNull(BoltParser.whenMs(null))
    }

    @Test fun `a frame may be one object or an array of them`() {
        val two = "[" + docsGameUpdate + ",{\"action\":\"ping\"}]"
        val ms = BoltParser.frame(two, now)
        assertEquals(2, ms.size)
        assertTrue(ms[0] is BoltMessage.GameUpdate)
        assertTrue(ms[1] is BoltMessage.Ping)
        assertEquals(listOf(BoltMessage.Ping), BoltParser.frame("""{"action": "ping"}""", now))
        assertEquals(emptyList<BoltMessage>(), BoltParser.frame("not json", now))
        assertEquals(emptyList<BoltMessage>(), BoltParser.frame("42", now))
    }

    @Test fun `control messages and an action Vigilant does not know`() {
        assertEquals(BoltMessage.SocketConnected, BoltParser.frame("""{"action": "socket_connected"}""", now).single())
        assertEquals("You have successfully updated your subscription filters.", (BoltParser.frame("""{"action": "subscription_updated", "message": "You have successfully updated your subscription filters."}""", now).single() as BoltMessage.SubscriptionUpdated).text)
        assertTrue((BoltParser.frame("""{"action": "error", "message": "Re-subscription rate limit hit. Please wait 5 seconds before re-subscribing."}""", now).single() as BoltMessage.Error).text.contains("rate limit"))
        assertEquals("brand_new", (BoltParser.frame("""{"action": "brand_new"}""", now).single() as BoltMessage.Unknown).action)
    }

    @Test fun `the clear messages carry empty objects for none`() {
        val sport = BoltParser.frame("""{"timestamp":"2025-07-23T19:58:43.086382+00:00","action":"sport_clear","data":{"sport":"NBA","sportsbook":"ballybet","game":{},"home_team":{},"away_team":{},"info":{},"outcomes":{}}}""", now).single()
        assertEquals("NBA", (sport as BoltMessage.SportClear).sport)
        assertEquals("ballybet", sport.sportsbook)
        val book = BoltParser.frame("""{"timestamp":"2025-07-23T19:58:43.086382+00:00","action":"book_clear","data":{"sport":{},"sportsbook":"ballybet","game":{},"home_team":{},"away_team":{},"info":{},"outcomes":{}}}""", now).single()
        assertEquals("ballybet", (book as BoltMessage.BookClear).sportsbook)
    }

    @Test fun `a price of none, empty or null means the line is gone`() {
        val msg = """{"timestamp":"2025-07-23T19:46:10+00:00","action":"line_update","data":{"sport":"MLB","sportsbook":"ballybet","game":"A vs B, 2025-07-23, 01","home_team":"A","away_team":"B","info":{},"outcomes":{
            "A Moneyline":{"odds":"-1250","outcome_name":"Moneyline","outcome_line":null,"outcome_over_under":null,"outcome_target":"A"},
            "B Moneyline":{"odds":"","outcome_name":"Moneyline","outcome_line":null,"outcome_over_under":null,"outcome_target":"B"},
            "A Spread":{"odds":null,"outcome_name":"Spread","outcome_line":"-1.5","outcome_over_under":null,"outcome_target":"A"},
            "B Spread":{"odds":"None","outcome_name":"Spread","outcome_line":"1.5","outcome_over_under":null,"outcome_target":"B"}}}}"""
        val m = BoltParser.frame(msg, now).single() as BoltMessage.LineUpdate
        assertEquals(listOf("A Moneyline"), m.data.outcomes.map { it.label })
        assertEquals(setOf("B Moneyline", "A Spread", "B Spread"), m.data.removed.toSet())
    }

    @Test fun `odds are American in any spelling and anything else is no price`() {
        fun o(json: String) = BoltParser.odds(kotlinx.serialization.json.Json.parseToJsonElement(json))
        assertEquals(-3333, o("\"-3333\""))
        assertEquals(900, o("\"+900\""))
        assertEquals(120, o("\"120\""))
        assertEquals(150, o("150"))
        assertEquals(-110, o("-110.0"))
        assertNull(o("\"\""))
        assertNull(o("\"None\""))
        assertNull(o("null"))
        assertNull(o("\"0\""))
        assertNull(o("\"50\""))
        assertNull(o("{}"))
    }

    @Test fun `the snapshot is read one game at a time from a string or a stream`() {
        val snap = "[" + listOf("ballybet", "draftkings").joinToString(",") { b -> docsGameUpdate.replace("game_update", "initial_state").replace("ballybet", b) } + ",{\"detail\":\"not a game\"}]"
        val games = BoltParser.snapshot(snap, now)
        assertEquals(listOf("ballybet", "draftkings"), games.map { it.data.sportsbook })
        assertTrue(games.all { it.initial })
        val streamed = ArrayList<String>()
        val r = BoltJsonArray.forEachElement(OneCharReader(snap)) { streamed += it }
        assertEquals(3, r.elements)
        assertNull(r.notAnArray)
        assertEquals(games.size, streamed.count { BoltParser.message(kotlinx.serialization.json.Json.parseToJsonElement(it) as kotlinx.serialization.json.JsonObject, now, true) is BoltMessage.GameUpdate })
    }

    @Test fun `the array reader survives braces and brackets inside strings, escapes and chunk edges`() {
        val tricky = """[ {"a":"} ] [ { \" \\ ","b":[1,{"c":"]"}]} , {"d":"x"} ]"""
        val got = ArrayList<String>()
        val r = BoltJsonArray.forEachElement(OneCharReader(tricky)) { got += it }
        assertEquals(2, r.elements)
        assertEquals("""{"a":"} ] [ { \" \\ ","b":[1,{"c":"]"}]}""", got[0])
        assertEquals("""{"d":"x"}""", got[1])
        assertEquals(0, BoltJsonArray.forEachElement(StringReader("[]")) { }.elements)
    }

    @Test fun `an error object instead of an array is reported with its text`() {
        val r = BoltJsonArray.forEachElement(StringReader("""{"detail":"Invalid API token"}""")) { throw AssertionError("no element") }
        assertEquals(0, r.elements)
        assertEquals("""{"detail":"Invalid API token"}""", r.notAnArray)
    }

    /** A reader that hands out one character at a time: every buffer edge falls inside something. */
    private class OneCharReader(text: String) : java.io.Reader() {
        private val r = StringReader(text)
        override fun read(buf: CharArray, off: Int, len: Int): Int = if (len == 0) 0 else r.read(buf, off, 1)
        override fun close() = r.close()
    }

    // ---- market names -----------------------------------------------------------------------------------------------------------------------------------------------------------------

    private fun out(name: String, line: Double? = null, ou: String? = null, target: String? = null, odds: Int = -110, at: Long = now) =
        BoltOutcome("$target $name", odds, name, line, ou, target, at)

    @Test fun `market names are read by their words and only when the shape agrees`() {
        fun k(league: String, o: BoltOutcome) = BoltMarkets.classify(league, o)
        assertEquals(BoltMarketRef(LineKind.MONEYLINE, 0), k("NBA", out("Moneyline", target = "A")))
        assertNull("a moneyline has no number", k("NBA", out("Moneyline", line = 3.5, target = "A")))
        assertEquals(BoltMarketRef(LineKind.SPREAD, 0), k("NBA", out("Spread", line = -3.5, target = "A")))
        assertEquals(BoltMarketRef(LineKind.SPREAD, 0), k("MLB", out("Run Line", line = -1.5, target = "A")))
        assertEquals(BoltMarketRef(LineKind.SPREAD, 0), k("NBA", out("Alternate Spread", line = -7.5, target = "A")))
        assertNull("a spread has no Over/Under", k("NBA", out("Spread", line = 3.5, ou = "Over", target = "A")))
        assertEquals(BoltMarketRef(LineKind.TOTAL, 0), k("NBA", out("Total", line = 220.5, ou = "Over")))
        assertEquals(BoltMarketRef(LineKind.TOTAL, 0), k("NHL", out("Total Goals", line = 5.5, ou = "Under")))
        assertEquals(BoltMarketRef(LineKind.TOTAL, 0), k("NBA", out("Alternate Total", line = 230.5, ou = "Over")))
        assertNull("a total needs its Over/Under", k("NBA", out("Total", line = 220.5)))
        assertEquals(BoltMarketRef(LineKind.TEAM_TOTAL, 0), k("NBA", out("Team Total", line = 112.5, ou = "Over", target = "A")))
        assertEquals(BoltMarketRef(LineKind.SPREAD, 1), k("NBA", out("1st Half Spread", line = -1.5, target = "A")))
        assertEquals(BoltMarketRef(LineKind.TOTAL, 1), k("NFL", out("First Half Total", line = 24.5, ou = "Over")))
        assertEquals(BoltMarketRef(LineKind.TOTAL, 1), k("MLB", out("First 5 Innings Total", line = 4.5, ou = "Over")))
        assertEquals(BoltMarketRef(LineKind.TOTAL, RefBookMarket.PERIOD_FIRST_INNING), k("MLB", out("1st Inning Total", line = 0.5, ou = "Over")))
        assertNull("a first-half moneyline is three-way at the books: not priced", k("NBA", out("1st Half Moneyline", target = "A")))
        assertNull("other parts of a game are not priced", k("NBA", out("2nd Quarter Spread", line = 1.5, target = "A")))
        assertNull(k("NBA", out("3rd Period Total", line = 1.5, ou = "Over")))
        assertNull(k("NHL", out("3-Way Moneyline", target = "A")))
    }

    @Test fun `player props use the stat table and need an Over or Under and a number`() {
        assertEquals(BoltMarketRef(LineKind.PLAYER_PROP, 0, "PLAYER_GOALS"), BoltMarkets.classify("NHL", out("Goals", line = 0.5, ou = "Over", target = "Auston Matthews")))
        assertEquals(BoltMarketRef(LineKind.PLAYER_PROP, 0, "HOME_RUNS"), BoltMarkets.classify("MLB", out("Home Runs", line = 0.5, ou = "Over", target = "Vladimir Guerrero Jr.")))
        assertEquals(BoltMarketRef(LineKind.PLAYER_PROP, 0, "POINTS"), BoltMarkets.classify("NBA", out("Player Points", line = 27.5, ou = "Over", target = "Jayson Tatum")))
        assertEquals(BoltMarketRef(LineKind.PLAYER_PROP, 0, "POINTS_REBOUNDS_ASSISTS"), BoltMarkets.classify("NBA", out("Points + Rebounds + Assists", line = 40.5, ou = "Over", target = "Jayson Tatum")))
        assertNull("a prop for a stat Novig has no market for", BoltMarkets.classify("NBA", out("Fight To Not Go Distance", line = 0.5, ou = "Over", target = "X")))
        assertNull("no number", BoltMarkets.classify("NBA", out("Points", ou = "Over", target = "Jayson Tatum")))
        assertNull("a prop for the first half is not priced", BoltMarkets.classify("NBA", out("1st Half Points", line = 12.5, ou = "Over", target = "Jayson Tatum")))
        // which names the market filter asks for
        assertTrue(BoltMarkets.usableName("NBA", "Moneyline", props = false))
        assertTrue(BoltMarkets.usableName("NBA", "Alternate Spread", props = false))
        assertFalse(BoltMarkets.usableName("NBA", "Player Points", props = false))
        assertTrue(BoltMarkets.usableName("NBA", "Player Points", props = true))
        assertFalse(BoltMarkets.usableName("NBA", "Moneyline", props = true))
        assertFalse(BoltMarkets.usableName("NBA", "Fight To Not Go Distance", props = true))
    }

    @Test fun `sports and books are matched by their letters`() {
        assertEquals("NBA", BoltBooks.leagueOfSport("NBA"))
        assertEquals("NBA", BoltBooks.leagueOfSport("NBA Preseason"))
        assertNull(BoltBooks.leagueOfSport("NBA Summer"))
        assertEquals("NCAAF", BoltBooks.leagueOfSport("NCAAF"))
        assertNull(BoltBooks.leagueOfSport("Wimbledon (M)"))
        assertEquals(listOf("NBA", "NBA Preseason"), BoltBooks.sportsFor(Leagues.byNovigName("NBA")!!, listOf("MLB", "NBA", "NBA Summer", "NBA Preseason")))
        assertEquals(listOf("NFL"), BoltBooks.sportsFor(Leagues.byNovigName("NFL")!!, null))
        assertEquals("williamhill_us", BoltBooks.appKey("caesars"))
        assertEquals("hardrockbet", BoltBooks.appKey("hardrock"))
        assertNull(BoltBooks.appKey("novig"))
        assertNull(BoltBooks.appKey("prizepicks"))
        assertEquals(listOf("draftkings", "hardrock"), BoltBooks.slugsFor(setOf("draftkings", "hardrockbet"), listOf("neobet", "hardrock", "novig", "draftkings", "draftkings")))
        assertEquals(listOf("draftkings", "neobet"), BoltBooks.slugsFor(null, listOf("neobet", "novig", "draftkings")))
    }

    // ---- the board -------------------------------------------------------------------------------------------------------------------------------------------------------------------

    private fun update(action: String, book: String, game: String, outcomes: String, home: String = "Boston Celtics", away: String = "New York Knicks", at: String = "2026-10-20T14:00:00+00:00", sport: String = "NBA", whenText: String = "2026-10-20, 07:30 PM") =
        """{"timestamp":"$at","action":"$action","data":{"sport":"$sport","sportsbook":"$book","game":"$game","home_team":"$home","away_team":"$away","info":{"id":1,"game":"$game","when":"$whenText","link":"x"},"outcomes":{$outcomes}}}"""

    private fun oc(name: String, target: String?, odds: String, line: String? = null, ou: String? = null, label: String = "$target $name ${line ?: ""} ${ou ?: ""}".trim()) =
        "\"$label\":{\"odds\":${if (odds == "null") "null" else "\"$odds\""},\"link\":\"l\",\"outcome_name\":\"$name\",\"outcome_line\":${line?.let { "\"$it\"" } ?: "null"},\"outcome_over_under\":${ou?.let { "\"$it\"" } ?: "null"},\"outcome_target\":${target?.let { "\"$it\"" } ?: "null"}}"

    private val game = "Boston Celtics vs New York Knicks, 2026-10-20, 07"

    private fun apply(board: BoltBoard, text: String) { BoltParser.frame(text, now).forEach { board.apply(it) } }

    private fun moneyline(odds1: String, odds2: String) = oc("Moneyline", "Boston Celtics", odds1) + "," + oc("Moneyline", "New York Knicks", odds2)

    @Test fun `game_update replaces, line_update edits, and a price of none removes an outcome`() {
        val b = BoltBoard(clock = { now })
        apply(b, update("game_update", "draftkings", game, moneyline("-150", "+130") + "," + oc("Spread", "Boston Celtics", "-110", "-3.5") + "," + oc("Spread", "New York Knicks", "-110", "3.5")))
        assertEquals(4, b.gamesOf(listOf("NBA")).single().outcomes.size)
        // one price moves
        apply(b, update("line_update", "draftkings", game, oc("Moneyline", "Boston Celtics", "-160")))
        assertEquals(-160, b.gamesOf(listOf("NBA")).single().outcomes.values.first { it.target == "Boston Celtics" && it.name == "Moneyline" }.odds)
        assertEquals(4, b.gamesOf(listOf("NBA")).single().outcomes.size)
        // the spread is suspended
        apply(b, update("line_update", "draftkings", game, oc("Spread", "Boston Celtics", "null", "-3.5")))
        assertEquals(3, b.gamesOf(listOf("NBA")).single().outcomes.size)
        // a whole new set replaces
        apply(b, update("game_update", "draftkings", game, moneyline("-170", "+145")))
        assertEquals(2, b.gamesOf(listOf("NBA")).single().outcomes.size)
        // the game leaves
        apply(b, update("game_removed", "draftkings", game, ""))
        assertTrue(b.gamesOf(listOf("NBA")).isEmpty())
    }

    @Test fun `sport_clear and book_clear drop what BoltOdds' backend lost`() {
        val b = BoltBoard(clock = { now })
        apply(b, update("game_update", "draftkings", game, moneyline("-150", "+130")))
        apply(b, update("game_update", "fanduel", game, moneyline("-155", "+135")))
        apply(b, update("game_update", "fanduel", "A vs B, 2026-10-20, 07", oc("Moneyline", "A", "-110") + "," + oc("Moneyline", "B", "-110"), home = "A", away = "B", sport = "NHL"))
        apply(b, """{"timestamp":"2026-10-20T14:05:00+00:00","action":"sport_clear","data":{"sport":"NBA","sportsbook":"fanduel","game":{},"home_team":{},"away_team":{},"info":{},"outcomes":{}}}""")
        assertEquals(setOf("draftkings|NBA", "fanduel|NHL"), b.gamesOf(listOf("NBA", "NHL")).map { "${it.sportsbook}|${it.sport}" }.toSet())
        apply(b, """{"timestamp":"2026-10-20T14:06:00+00:00","action":"book_clear","data":{"sport":{},"sportsbook":"fanduel","game":{},"home_team":{},"away_team":{},"info":{},"outcomes":{}}}""")
        assertEquals(listOf("draftkings"), b.gamesOf(listOf("NBA", "NHL")).map { it.sportsbook })
    }

    @Test fun `a snapshot never overwrites a newer live update, and fills what the live updates have not`() {
        val b = BoltBoard(clock = { now })
        apply(b, update("game_update", "draftkings", game, moneyline("-160", "+140"), at = "2026-10-20T14:10:00+00:00"))
        // the snapshot (older timestamps) arrives afterwards
        val snap = update("initial_state", "draftkings", game, moneyline("-150", "+130"), at = "2026-10-20T14:00:00+00:00")
        val other = update("initial_state", "fanduel", game, moneyline("-155", "+135"), at = "2026-10-20T14:00:00+00:00")
        assertEquals(1, b.applyInitial(BoltParser.snapshot("[$snap,$other]", now)))
        val games = b.gamesOf(listOf("NBA")).associateBy { it.sportsbook }
        assertEquals(-160, games.getValue("draftkings").outcomes.values.first { it.target == "Boston Celtics" }.odds)
        assertEquals(-155, games.getValue("fanduel").outcomes.values.first { it.target == "Boston Celtics" }.odds)
    }

    @Test fun `outcomes the table cannot classify are counted, not kept`() {
        val b = BoltBoard(clock = { now })
        apply(b, update("game_update", "draftkings", game, moneyline("-150", "+130") + "," + oc("Race To 10 Points", "Boston Celtics", "+200") + "," + oc("Race To 10 Points", "New York Knicks", "+250")))
        assertEquals(2, b.gamesOf(listOf("NBA")).single().outcomes.size)
        assertEquals(2, b.unmapped["NBA | Race To 10 Points"])
        // the props board keeps only props, the lines board anything but
        val props = BoltBoard({ now }) { it == LineKind.PLAYER_PROP }
        apply(props, update("game_update", "draftkings", game, moneyline("-150", "+130") + "," + oc("Player Points", "Jayson Tatum", "-115", "27.5", "Over") + "," + oc("Player Points", "Jayson Tatum", "-105", "27.5", "Under")))
        assertEquals(2, props.gamesOf(listOf("NBA")).single().outcomes.size)
    }

    // ---- conversion -----------------------------------------------------------------------------------------------------------------------------------------------------------------

    private val nba = Leagues.byNovigName("NBA")!!
    private val settings = ScanSettings(referenceBooks = listOf("draftkings", "fanduel"))

    private fun ctx(aliveAt: (String, String) -> Long? = { _, _ -> null }, wanted: Set<String>? = setOf("draftkings", "fanduel"), families: Set<MarketFamily> = MarketFamily.entries.toSet(), live: Boolean = false, at: Long = now) =
        BoltContext(nba, wanted, families, live, at, aliveAt)

    private fun fullBook(b: BoltBoard) {
        apply(
            b,
            update(
                "game_update", "draftkings", game,
                moneyline("-150", "+130") + "," +
                    oc("Spread", "Boston Celtics", "-110", "-3.5") + "," + oc("Spread", "New York Knicks", "-110", "3.5") + "," +
                    oc("Spread", "Boston Celtics", "+120", "-4.5") + "," + oc("Spread", "New York Knicks", "-140", "4.5") + "," +
                    oc("Total", null, "-108", "220.5", "Over", label = "Over 220.5") + "," + oc("Total", null, "-112", "220.5", "Under", label = "Under 220.5") + "," +
                    oc("Team Total", "Boston Celtics", "-105", "112.5", "Over") + "," + oc("Team Total", "Boston Celtics", "-115", "112.5", "Under") + "," +
                    oc("1st Half Spread", "Boston Celtics", "-110", "-1.5") + "," + oc("1st Half Spread", "New York Knicks", "-110", "1.5"),
            ),
        )
    }

    @Test fun `a book's outcomes become oriented reference markets`() {
        val b = BoltBoard(clock = { now })
        fullBook(b)
        val ev = BoltConvert.events(b.gamesOf(listOf("NBA")), ctx()).single()
        assertEquals("Boston Celtics", ev.home)
        assertEquals("New York Knicks", ev.away)
        assertEquals(Instant.parse("2026-10-20T23:30:00Z").toEpochMilli(), ev.commenceMs)
        assertEquals("basketball_nba", ev.sportKey)
        val ml = ev.markets.single { it.kind == LineKind.MONEYLINE }
        assertEquals(1.0 + 100.0 / 150.0, ml.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
        assertEquals(2.30, ml.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-9)
        assertEquals("draftkings", ml.bookKey)
        val spreads = ev.markets.filter { it.kind == LineKind.SPREAD && it.period == 0 }
        assertEquals(setOf(-3.5, -4.5), spreads.map { it.line!! }.toSet())
        assertEquals(3.5, spreads.first { it.line == -3.5 }.quotes.single { it.side == Side.AWAY }.point!!, 1e-9)
        val total = ev.markets.single { it.kind == LineKind.TOTAL }
        assertEquals(220.5, total.line!!, 1e-9)
        assertEquals(1.0 + 100.0 / 112.0, total.quotes.single { it.side == Side.UNDER }.decimalOdds, 1e-9)
        val tt = ev.markets.single { it.kind == LineKind.TEAM_TOTAL }
        assertEquals(RefBookMarket.HOME, tt.subject)
        val half = ev.markets.single { it.kind == LineKind.SPREAD && it.period == 1 }
        assertEquals(-1.5, half.line!!, 1e-9)
    }

    @Test fun `a pair that does not close is dropped, never repaired`() {
        val b = BoltBoard(clock = { now })
        apply(
            b,
            update(
                "game_update", "draftkings", game,
                // spread: both teams carry the SAME sign (a feed that repeats the home number): nothing is priced
                oc("Spread", "Boston Celtics", "-110", "-3.5") + "," + oc("Spread", "New York Knicks", "-110", "-3.5") + "," +
                    // total: Over and Under on different numbers
                    oc("Total", null, "-110", "220.5", "Over", label = "o") + "," + oc("Total", null, "-110", "221.5", "Under", label = "u") + "," +
                    // moneyline with one side missing
                    oc("Moneyline", "Boston Celtics", "-150") + "," +
                    // a team the game does not have
                    oc("Moneyline", "Brooklyn Nets", "+500"),
            ),
        )
        assertTrue(BoltConvert.events(b.gamesOf(listOf("NBA")), ctx()).isEmpty())
    }

    @Test fun `a book that names home and away the other way round is put the right way round`() {
        val b = BoltBoard(clock = { now })
        fullBook(b)
        apply(b, update("game_update", "fanduel", game, moneyline("+125", "-145"), home = "New York Knicks", away = "Boston Celtics"))
        // In FanDuel's own message the Knicks are home and Boston is away; its outcomes name the TEAM, so Boston's price is +125 whichever side the book calls home.
        val ev = BoltConvert.events(b.gamesOf(listOf("NBA")), ctx()).single()
        val fd = ev.markets.single { it.bookKey == "fanduel" && it.kind == LineKind.MONEYLINE }
        assertEquals("Boston Celtics stays home in the event's orientation", 2.25, fd.quotes.single { it.side == Side.HOME }.decimalOdds, 1e-9)
        assertEquals(1.0 + 100.0 / 145.0, fd.quotes.single { it.side == Side.AWAY }.decimalOdds, 1e-9)
    }

    @Test fun `books, families and live games are filtered`() {
        val b = BoltBoard(clock = { now })
        fullBook(b)
        apply(b, update("game_update", "bet365", game, moneyline("-148", "+128")))
        val all = b.gamesOf(listOf("NBA"))
        assertEquals(setOf("draftkings"), BoltConvert.events(all, ctx()).single().markets.map { it.bookKey }.toSet())
        assertEquals(setOf("draftkings", "bet365"), BoltConvert.events(all, ctx(wanted = null)).single().markets.map { it.bookKey }.toSet())
        assertEquals(setOf(LineKind.MONEYLINE), BoltConvert.events(all, ctx(families = setOf(MarketFamily.MONEYLINE))).single().markets.map { it.kind }.toSet())
        assertEquals(setOf(LineKind.MONEYLINE, LineKind.SPREAD), BoltConvert.events(all, ctx(families = setOf(MarketFamily.MONEYLINE, MarketFamily.SPREAD))).single().markets.map { it.kind }.toSet())
        // after the start: a live game, only when live games are on
        val late = Instant.parse("2026-10-21T00:30:00Z").toEpochMilli()
        assertTrue(BoltConvert.events(all, ctx(at = late)).isEmpty())
        assertEquals(1, BoltConvert.events(all, ctx(at = late, live = true)).size)
    }

    @Test fun `player props pair an Over with its Under on one number`() {
        val b = BoltBoard({ now }) { it == LineKind.PLAYER_PROP }
        apply(
            b,
            update(
                "game_update", "draftkings", game,
                oc("Player Points", "Jayson Tatum", "-115", "27.5", "Over") + "," + oc("Player Points", "Jayson Tatum", "-105", "27.5", "Under") + "," +
                    oc("Player Points", "Jayson Tatum", "+120", "30.5", "Over") + "," + // no Under at 30.5: nothing
                    oc("Rebounds", "Jaylen Brown", "-120", "5.5", "Over") + "," + oc("Rebounds", "Jaylen Brown", "-100", "5.5", "Under"),
            ),
        )
        val ev = BoltConvert.events(b.gamesOf(listOf("NBA")), ctx()).single()
        val props = ev.markets.filter { it.kind == LineKind.PLAYER_PROP }
        assertEquals(setOf("POINTS" to "Jayson Tatum", "REBOUNDS" to "Jaylen Brown"), props.map { it.stat!! to it.subject!! }.toSet())
        assertEquals(27.5, props.first { it.stat == "POINTS" }.line!!, 1e-9)
        assertTrue(BoltConvert.events(b.gamesOf(listOf("NBA")), ctx(families = setOf(MarketFamily.MONEYLINE))).isEmpty())
    }

    @Test fun `a quote is as fresh as its book's last word while the socket is alive, else as old as its own message`() {
        val b = BoltBoard(clock = { now })
        fullBook(b) // every outcome set at 14:00Z
        val own = Instant.parse("2026-10-20T14:00:00Z").toEpochMilli()
        val dead = BoltConvert.events(b.gamesOf(listOf("NBA")), ctx()).single().markets.first { it.kind == LineKind.MONEYLINE }
        assertEquals(own, dead.lastUpdateMs)
        val alive = BoltConvert.events(b.gamesOf(listOf("NBA")), ctx(aliveAt = { book, sport -> if (book == "draftkings" && sport == "NBA") now else null })).single().markets.first { it.kind == LineKind.MONEYLINE }
        assertEquals(now, alive.lastUpdateMs)
        assertNotNull(alive.lastUpdateMs)
    }
}
