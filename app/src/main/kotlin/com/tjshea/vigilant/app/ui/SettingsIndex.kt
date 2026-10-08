package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import java.util.Locale

/**
 * Every setting by name, where it lives and what it does in a line (Tj, 2026-10-02 ~17:55Z: "make it so everything is clear and easy to find"): what
 * Settings' search box looks through. [Entry.page] null = the Auto-bet tab, or the Bids tab with [Entry.bids]. Titles are unique (each is a hit's test tag). [Entry.title] is the words the page shows for it (SettingsIndexTest checks
 * each is there), [Entry.help] a plain line, [Entry.words] other words someone might search by. [Entry.shown]: only while the page shows it.
 */
object SettingsIndex {

    data class Entry(
        val title: String,
        val page: SettingsPage?,
        val help: String,
        val words: String = "",
        val shown: (ScanSettings) -> Boolean = { true },
        /** On the Bids tab (make orders) rather than a Settings page or the Auto-bet tab; its rules open under "Rules". */
        val bids: Boolean = false,
    ) {
        /** Where it lives, as a search hit says it. */
        val where: String get() = if (bids) "Bids tab" else page?.title ?: "Auto-bet tab"
    }

    private val novig: (ScanSettings) -> Boolean = { AppBook.isNovig }
    private val autoBetTab: (ScanSettings) -> Boolean = { AppBook.isNovig && it.cnoOn }
    private val exchange: (ScanSettings) -> Boolean = { AppBook.exchange }

    val entries: List<Entry> = listOf(
        // Scanning
        Entry("Pinnodds key", SettingsPage.PINNODDS, "Your pinnodds.com key for Pinnacle's live WebSocket, with a button that tests it", "pinnodds pinnacle websocket key test live", novig),
        Entry("Pinnodds live feed", SettingsPage.PINNODDS, "Compare Pinnacle's live prices with Novig's live books; paper unless real bets are on", "pinnodds pinnacle live feed paper lag in-play", novig),
        Entry("Place real bets", SettingsPage.PINNODDS, "On live games, bet on Novig when its price lags Pinnacle's fair after the fee; stake, game, day and loss limits", "pinnodds live real bets stake limit lag in-play", novig),
        Entry("Pause all scanning", SettingsPage.SCANNING, "Stop every read until you switch it back on", "stop pause"),
        Entry("Which scanner", SettingsPage.SCANNING, "CrazyNinjaOdds' list, Vigilant's own scan, or both", "cno vigilant scanner mode both"),
        Entry("Pinnacle only", SettingsPage.SCANNING, "Compare Novig with Pinnacle's devigged price alone; read nothing else", "pinnacle only sharp devig compare auto-bet fresh age", novig),
        Entry("Games starting within", SettingsPage.SCANNING, "Only games starting within a few hours", "window hours time start soon", novig),
        Entry("Keep scanning in the background", SettingsPage.SCANNING, "Scan with Vigilant closed: what sends alerts and runs auto-bet", "auto-scan autoscan background interval every closed", novig),
        Entry("Keep awake (screen stays off)", SettingsPage.SCANNING, "Keeps background scans on time while the phone sleeps", "battery doze sleep", { AppBook.isNovig && BackgroundScan.on(it) }),
        // Alerts
        Entry("Smallest edge (EV) to alert on", SettingsPage.ALERTS, "How good a new bet must be to notify you", "alerts notification push ev minimum", novig),
        Entry("Sharp-book veto for alerts", SettingsPage.ALERTS, "Skip alerts the sharpest book disagrees with", "pinnacle kalshi confirm veto sharp", { AppBook.isNovig && it.cnoOn }),
        Entry("Trap guard", SettingsPage.ALERTS, "No alerts for games too far from the start (bets that early lost to the close)", "trap sharp early hours start gift steam type custom 12 hours", novig),
        // CrazyNinjaOdds list
        Entry("How the true odds are worked out", SettingsPage.CNO, "Devig: taking the sportsbooks' profit out of their odds", "devig vig conservative fair true odds"),
        Entry("Longest odds", SettingsPage.CNO, "No long shots past this in CNO's list", "max odds longshot underdog cno type custom number"),
        Entry("Shortest odds", SettingsPage.CNO, "No heavy favorites past this in CNO's list (or +100: underdogs only)", "min odds favorite shortest underdog cno type custom number"),
        Entry("Leagues listed", SettingsPage.CNO, "Which leagues CNO's list looks at (NFL, NHL, soccer…): nothing picked is every league", "league leagues sport nfl nhl nba mlb ncaaf wnba soccer football hockey basketball baseball filter cno"),
        Entry("Kinds of bet listed", SettingsPage.CNO, "Which kinds of bet CNO's list shows: props, spreads, totals…", "kind kinds bet type props spread total moneyline market filter cno"),
        Entry("Hide live games", SettingsPage.CNO, "Leave games already under way out of CNO's list", "live in-game under way started pregame hide cno"),
        Entry("Fewest dollars available", SettingsPage.CNO, "Leave out bets with less than this at Novig's price", "liquidity dollars available size thin minimum cno"),
        Entry("Show only bets with these words", SettingsPage.CNO, "Teams, players or markets: keep only bets that mention them", "words include text team player search contains cno"),
        Entry("Leave out bets with these words", SettingsPage.CNO, "Teams, players or markets: drop bets that mention them", "words exclude text team player hide not cno"),
        Entry("Props per game", SettingsPage.CNO, "At most this many player props from one game", "props per game cap limit player cno"),
        Entry("Fewest books behind the true odds", SettingsPage.CNO, "How many sportsbooks the true odds must come from", "min books"),
        Entry("Smallest edge (EV) listed", SettingsPage.CNO, "The smallest edge CNO's list shows", "min ev minimum cno"),
        Entry("Require a complete sportsbook", SettingsPage.CNO, "Only markets with every side priced", "complete"),
        Entry("Green ✓ when books agree", SettingsPage.CNO, "Check each top bet against every book's own odds", "check agree verify books only"),
        Entry("Novig's price now", SettingsPage.CNO, "Show Novig's current price, not CNO's minute-old one", "live price novig current", novig),
        Entry("Player teams", SettingsPage.CNO, "Show the player's team on player bets", "team roster espn"),
        Entry("Read the list every", SettingsPage.CNO, "How often CNO's list refreshes on screen", "refresh interval seconds"),
        Entry("Rows per read", SettingsPage.CNO, "How many of CNO's best bets each read takes", "rows"),
        Entry("Your view", SettingsPage.CNO, "Your own CrazyNinjaOdds filters (Shared View link)", "shared view link url filters"),
        // Widget
        Entry("Floating widget you can touch", SettingsPage.WIDGET, "A small window over other apps with your best bets", "widget floating overlay bubble pip"),
        Entry("Also open it when I leave Vigilant", SettingsPage.WIDGET, "Open the widget by itself when you switch apps", "mini window leave open"),
        Entry("Vigilant's scan again while the widget is open", SettingsPage.WIDGET, "Rescan Vigilant's own bets while the widget is up", "rescan widget", { it.scannerNow == ScannerMode.BOTH }),
        // +EV feed & scan size
        Entry("Smallest edge (EV) shown", SettingsPage.FEED, "The smallest edge Vigilant's +EV feed shows", "min ev minimum feed"),
        Entry("Longest odds shown", SettingsPage.FEED, "No long shots past this in Vigilant's feed", "max odds longshot type custom number"),
        Entry("Shortest odds shown", SettingsPage.FEED, "No heavy favorites past this in Vigilant's feed (or +100: underdogs only)", "min odds favorite shortest underdog type custom number"),
        Entry("Markets", SettingsPage.FEED, "Moneylines, spreads, totals, props…", "market types props spreads totals"),
        Entry("Alternate lines per game", SettingsPage.FEED, "How many extra lines a scan reads per game", "alt lines", exchange),
        Entry("Player props per game", SettingsPage.FEED, "How many props a scan reads per game", "props", { AppBook.exchange && MarketFamily.PLAYER_PROPS in it.families }),
        Entry("Most Novig prices per scan", SettingsPage.FEED, "The size of one scan", "scan size budget limit", exchange),
        Entry("Days ahead", SettingsPage.FEED, "How many days of games a scan reads", "days window"),
        Entry("Fill the scan with every quoted line", SettingsPage.FEED, "Spend what's left of the scan's prices on every other line the books quote", "fill budget all lines alternate", exchange),
        Entry("Include live games", SettingsPage.FEED, "Also scan games already under way", "live in-game"),
        // Fair odds & sources
        Entry("Fair odds method", SettingsPage.FAIR, "Sharp books, every book's average, or a blend", "fair true odds blend sharp average"),
        Entry("Books that count as sharp", SettingsPage.FAIR, "Which books' odds count as the most accurate", "sharp pinnacle"),
        Entry("Fewest books for an average", SettingsPage.FAIR, "Skip a line with fewer books than this", "min books average"),
        Entry("Outlier guard", SettingsPage.FAIR, "Stop one stale book from faking an edge", "outlier median"),
        Entry("Devig method", SettingsPage.FAIR, "How the profit is taken out of a pair of odds", "devig vig power shin"),
        Entry("Fall back to market average", SettingsPage.FAIR, "When no sharp book quotes a line, use every book instead of skipping it", "sharp fallback average", { it.fairSource == com.tjshea.vigilant.engine.FairSource.SHARP }),
        Entry("Sportsbook player props", SettingsPage.FAIR, "Your books' player props as fair odds (DraftKings, FanDuel, BetMGM…)", "props book props prop types credits games hours reuse", { it.useOddsApi || it.usePropLine || it.useParlay }),
        Entry("Sportsbooks for fair odds", SettingsPage.FAIR, "Which sportsbooks' odds count toward the fair odds", "reference books picker draftkings fanduel betmgm caesars", { it.useOddsApi || it.usePropLine || it.useParlay }),
        Entry("Where fair odds come from", SettingsPage.FAIR, "Turn feeds on or off and add their keys", "pinnacle pinnwire pinnapi polymarket kalshi propline parlayapi odds api keys sources feeds"),
        // Betting & Novig account
        Entry("Novig API key", SettingsPage.BETTING, "Connect Novig to bet from Vigilant", "key connect account api", novig),
        Entry("Bankroll", SettingsPage.BETTING, "The money you set aside for betting", "bankroll money"),
        Entry("Kelly fraction for suggested stakes", SettingsPage.BETTING, "How big suggested stakes are for a given edge", "kelly stake size"),
        Entry("Most for one bet you place", SettingsPage.BETTING, "The most one bet may stake (your own bets and auto-bets)", "limit max stake per bet", novig),
        Entry("Most in a day", SettingsPage.BETTING, "The most all bets may stake in a day", "limit max day daily", novig),
        Entry("Most at risk on one game", SettingsPage.BETTING, "All a game's lines, props and bids together", "limit max game exposure", novig),
        Entry("Amount a bet starts at", SettingsPage.BETTING, "\$1, Kelly or your amount, in the bet slip and Bet sheet", "stake amount bet slip sheet dollar", novig),
        // Usage & keys
        Entry("API usage", SettingsPage.USAGE, "Credits left on each feed", "credits usage meter quota"),
        Entry("Keys backup", SettingsPage.USAGE, "Export or import your keys", "export import backup keys"),
        // Diagnostics & about
        Entry("Share with Claude", SettingsPage.HELP, "Make the diagnostics file for Claude", "diagnostics report bug claude share"),
        Entry("Share scan study with Claude", SettingsPage.HELP, "Every bet a scan listed, graded, with its close, for Claude to find patterns", "scan study log patterns clv close profit claude share analyze"),
        Entry("Test live score and odds feeds", SettingsPage.HELP, "Which free feed shows a score or an odds move before Novig's price moves (reads only, no orders)", "live feed race test sofascore polymarket espn nhl mlb scores odds rapid websocket latency"),
        Entry("Share live feed test with Claude", SettingsPage.HELP, "The live feed test's verdict, table and tape as one file for Claude", "live feed race test share claude file scores odds"),
        Entry("Log every scan for the study", SettingsPage.HELP, "Switch the scan study's logging off or on", "scan study log switch"),
        Entry("Also log what your CNO filters hide", SettingsPage.HELP, "The scan study also logs the CNO rows your filters hide from the app (still hidden there)", "scan study hidden filtered cno wide log all finds"),
        Entry("About", SettingsPage.HELP, "Version and where the data comes from", "version about"),
        // The Auto-bet tab
        Entry("Place bets automatically", null, "Auto-bet: turn it on or off", "auto-bet autobet automatic on off", autoBetTab),
        Entry("Presets", null, "Set every auto-bet, alert and CNO rule in one tap", "preset volume strict clv save", autoBetTab),
        Entry("Smallest edge (EV) at Novig's price now", null, "The smallest edge auto-bet takes", "auto-bet min ev minimum edge", autoBetTab),
        Entry("Books that each say +EV on their own", null, "How many books must agree before auto-bet bets", "auto-bet agree books", autoBetTab),
        Entry("Books that must price both sides", null, "How many books must quote both sides before auto-bet can judge a bet", "auto-bet two sided books", autoBetTab),
        Entry("Every book scanned must agree", null, "5 of 5, not 3 of 5", "auto-bet all agree every book", autoBetTab),
        Entry("Longest odds to bet", null, "No long shots past this for auto-bet", "auto-bet max odds longshot", autoBetTab),
        Entry("Extra edge a favorite needs", null, "Favorites (shorter than even money) need more edge than the minimum for auto-bet", "auto-bet favorite favourites extra ev edge higher bar plus money", autoBetTab),
        Entry("Small-prop guard", null, "Auto-bet: the most one kind of player prop (NHL shots on goal) may take of the last 24 hours' auto-bets, and per game", "auto-bet small prop guard share cap concentration shots on goal nhl volatile obscure per game limit diversification", autoBetTab),
        Entry("Shortest odds to bet", null, "No heavy favorites past this for auto-bet", "auto-bet min odds favorite", autoBetTab),
        Entry("Kinds of bet to place", null, "Props, moneylines, spreads, totals…", "auto-bet kinds markets props", autoBetTab),
        Entry("Sharp-book veto", null, "Skip a bet the sharpest book disagrees with", "auto-bet sharp veto confirm pinnacle kalshi", autoBetTab),
        Entry(
            "Edge the sharpest book must give Novig's price", null, "The sharp veto's bar: skip a bet the sharpest book gives under this edge (1% by default)",
            "auto-bet sharp veto bar minimum edge pinnacle kalshi trap clv", { autoBetTab(it) && it.sharpAutoBet == com.tjshea.vigilant.data.scanner.SharpMode.VETO },
        ),
        Entry("Skip bets listed too early", null, "Trap guard: skip bets first listed more than the guard's hours before the start, even once the game is inside the window", "auto-bet alerts trap first listed early hours remember old listing", autoBetTab),
        Entry("Skip game lines Novig just moved", null, "Trap guard: skip games too far from the start, and game lines Novig just moved", "auto-bet trap sharp early hours gift steam moved", autoBetTab),
        Entry("Most to stake on one bet", null, "The most one auto-bet may stake", "auto-bet max stake limit", autoBetTab),
        Entry("Amount per bet", null, "Auto-bet's stake: Kelly, \$1 or your amount", "auto-bet stake kelly amount", autoBetTab),
        Entry("Check every", null, "How often auto-bet looks for bets", "auto-bet interval often", autoBetTab),
        Entry("Smallest profit to lock", null, "A lock waits for at least this share of the stake as guaranteed profit", "lock min percent profit", autoBetTab),
        Entry("Also during the game", null, "Lock in profits during the game too", "lock live in-game", autoBetTab),
        Entry("Hide locked bets in the Tracker", null, "Locked markets leave the Tracker's lists and stats", "lock hide tracker", autoBetTab),
        Entry("Lock in profits automatically", null, "Buy the other side once a bet's odds moved your way, for a sure profit", "lock hedge arbitrage arb guarantee green", autoBetTab),
        // The Bids tab (make orders)
        Entry("Fully automatic", null, "Bids: off, recommend each one, or post them by themselves", "bids make orders maker post auto-make recommend", novig, bids = true),
        Entry("Under the fair", null, "How far under the fair each bid is posted (its EV): 2% to 4%, or type your own", "bids maker margin edge ev type custom number 2% 2.5%", novig, bids = true),
        Entry("Bids priced from", null, "Vigilant's scan (as always) or CrazyNinjaOdds alone, with Vigilant's scan off; how old CNO's data may be for a bid to rest on it", "bids maker source cno crazyninja crazy ninja odds scanner vigilant scan off only age stale data seconds", novig, bids = true),
        Entry("Which bids go up", null, "All bids, Quick & likely to win (no strange props or small markets), or Low API usage", "bids maker focus quick likely low api usage kinds", novig, bids = true),
        Entry("Most one bid may cost", null, "The most any one bid may stake", "bids maker max stake", novig, bids = true),
        Entry("Size of each bid", null, "Kelly, \$1 or your amount per bid", "bids maker stake kelly amount", novig, bids = true),
        Entry("Low API usage", null, "Which bids go up: props only, games within your trap guard hours (6 h by default), 2-3 sharp prop books, a slow scan pace, 2.5% (or 1.5%, or your own) or more under the fair, nothing longer than +130 unless you pick another longest odds", "bids maker which bids go up low api usage credits sharp prop books kalshi prophetx fanduel caesars draftkings pace minutes interval margin 1.5 ev", novig, bids = true),
        Entry("Kinds of bet", null, "Which kinds of market get bids: props, team totals, game lines…", "bids maker kinds markets", { AppBook.isNovig && it.makerFocus != com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE }, bids = true),
        Entry("Both sides of a market", null, "Bid both sides, or only the better side", "bids maker both sides", novig, bids = true),
        Entry("Price under the sharp book's fair", null, "Take each bid's margin from the lower of the blend and the sharpest book's fair", "bids maker anchor sharp", { AppBook.isNovig && it.makerFocus != com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE }, bids = true),
        Entry("Stop bids when fills are picked off", null, "Bids stop themselves when most recent fills were filled above the fair", "bids maker guard picked off", novig, bids = true),
        Entry("Popular markets first", null, "Bids on the markets takers trade most go up first (All bids only)", "bids maker popular obscure order", { AppBook.isNovig && it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.ALL }, bids = true),
        Entry("Require a sharp book to agree", null, "No bid unless a sharp book prices the line both ways and agrees (All bids only)", "bids maker require sharp", { AppBook.isNovig && it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.ALL }, bids = true),
        Entry("Recommend bids when auto-make is off", null, "A notification for each new bid worth posting, with Approve and Deny", "bids maker recommend notify", novig, bids = true),
        Entry("Longest odds a bid may be posted at", null, "No bid at longer odds than this (+140: nothing at +141 or longer)", "bids maker longest odds longshot limit max plus", novig, bids = true),
        Entry("Shortest odds a bid may be posted at", null, "No bid at shorter odds than this (−200: no heavy favorites; +110: underdogs only)", "bids maker shortest odds favorite limit min underdog", novig, bids = true),
        Entry("Smallest market for quick bids", null, "Quick & likely: no bid on a line fewer books than this price (a small market) or on an unusual kind of prop", "bids maker quick likely small market books strange obscure props popular", { AppBook.isNovig && it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY }, bids = true),
        Entry("Fill leftover money with small markets", null, "Quick & likely and Low API usage: when popular bids don't use all your bid money, small markets get bids too, popular first, under strict safeguards", "bids maker quick likely low api usage obscure small market fill leftover idle money popular first strict safeguards", { AppBook.isNovig && (it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY || it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE) }, bids = true),
        Entry("Small-market bids: margin, sharp edge, agreement, books, stake", null, "How far under the fair a small-market bid sits, the edge every sharp book must give it, how close the sharp books must agree, the fewest books, and its share of the stake", "bids maker obscure small market margin under fair sharp edge agree points books stake share", { AppBook.isNovig && (it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.QUICK_LIKELY || it.makerFocus == com.tjshea.vigilant.data.scanner.BidFocus.LOW_USAGE) && it.makerObscureFill }, bids = true),
        Entry("Books that must each say the bid is +EV on their own", null, "How many books must agree a bid is +EV before it goes up", "bids maker books agree min", novig, bids = true),
        Entry("Most bids up at once", null, "How many bids rest at the same time", "bids maker cap limit", novig, bids = true),
        Entry("Most dollars up at once", null, "How much money all bids together may hold", "bids maker wallet dollars", novig, bids = true),
        Entry("Each bid expires after", null, "How long a bid rests before Novig takes it down", "bids maker ttl expiry", novig, bids = true),
        Entry("No bids this close to the start", null, "Bids come down this long before a game", "bids maker stop start", novig, bids = true),
        Entry("Trap guard: only games starting within", null, "No bids on games too far off (shared with auto-bet and alerts)", "bids maker trap early hours type custom 12 hours", novig, bids = true),
    )

    /** The entries matching every word of [query] (in the title, the line or the extra words), for these settings. */
    fun search(query: String, s: ScanSettings): List<Entry> {
        val words = query.lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e ->
            val hay = "${e.title} ${e.help} ${e.words} ${e.where}".lowercase(Locale.US)
            e.shown(s) && (e.page?.shownIn(s) ?: true) && words.all { hay.contains(it) }
        }
    }
}
