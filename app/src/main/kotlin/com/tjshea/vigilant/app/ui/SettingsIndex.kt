package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.app.AppBook
import com.tjshea.vigilant.data.scanner.MarketFamily
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.ScannerMode
import java.util.Locale

/**
 * Every setting by name, where it lives and what it does in a line (Tj, 2026-10-02 ~17:55Z: "make it so everything is clear and easy to find"): what
 * Settings' search box looks through. [Entry.page] null = the Auto-bet tab. [Entry.title] is the words the page shows for it (SettingsIndexTest checks
 * each is there), [Entry.help] a plain line, [Entry.words] other words someone might search by. [Entry.shown]: only while the page shows it.
 */
object SettingsIndex {

    data class Entry(
        val title: String,
        val page: SettingsPage?,
        val help: String,
        val words: String = "",
        val shown: (ScanSettings) -> Boolean = { true },
    )

    private val novig: (ScanSettings) -> Boolean = { AppBook.isNovig }
    private val autoBetTab: (ScanSettings) -> Boolean = { AppBook.isNovig && it.cnoOn }
    private val exchange: (ScanSettings) -> Boolean = { AppBook.exchange }

    val entries: List<Entry> = listOf(
        // Scanning
        Entry("Pause all scanning", SettingsPage.SCANNING, "Stop every read until you switch it back on", "stop pause"),
        Entry("Which scanner", SettingsPage.SCANNING, "CrazyNinjaOdds' list, Vigilant's own scan, or both", "cno vigilant scanner mode both"),
        Entry("Games starting within", SettingsPage.SCANNING, "Only games starting within a few hours", "window hours time start soon", novig),
        Entry("Keep scanning in the background", SettingsPage.SCANNING, "Scan with Vigilant closed: what sends alerts and runs auto-bet", "auto-scan autoscan background interval every closed", novig),
        Entry("Keep awake (screen stays off)", SettingsPage.SCANNING, "Keeps background scans on time while the phone sleeps", "battery doze sleep", { AppBook.isNovig && BackgroundScan.on(it) }),
        // Alerts
        Entry("Smallest edge (EV) to alert on", SettingsPage.ALERTS, "How good a new bet must be to notify you", "alerts notification push ev minimum", novig),
        Entry("Sharp-book veto for alerts", SettingsPage.ALERTS, "Skip alerts the sharpest book disagrees with", "pinnacle kalshi confirm veto sharp", { AppBook.isNovig && it.cnoOn }),
        // CrazyNinjaOdds list
        Entry("How the true odds are worked out", SettingsPage.CNO, "Devig: taking the sportsbooks' profit out of their odds", "devig vig conservative fair true odds"),
        Entry("Longest odds", SettingsPage.CNO, "No long shots past this in CNO's list", "max odds longshot underdog cno"),
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
        Entry("Vigilant's scan again while the widget is open", SettingsPage.WIDGET, "Rescan Vigilant's own bets while the widget is up", "rescan widget", { it.scanner == ScannerMode.BOTH }),
        // +EV feed & scan size
        Entry("Smallest edge (EV) shown", SettingsPage.FEED, "The smallest edge Vigilant's +EV feed shows", "min ev minimum feed"),
        Entry("Longest odds shown", SettingsPage.FEED, "No long shots past this in Vigilant's feed", "max odds longshot"),
        Entry("Markets", SettingsPage.FEED, "Moneylines, spreads, totals, props…", "market types props spreads totals"),
        Entry("Alternate lines per game", SettingsPage.FEED, "How many extra lines a scan reads per game", "alt lines", exchange),
        Entry("Player props per game", SettingsPage.FEED, "How many props a scan reads per game", "props", { AppBook.exchange && MarketFamily.PLAYER_PROPS in it.families }),
        Entry("Most Novig prices per scan", SettingsPage.FEED, "The size of one scan", "scan size budget limit", exchange),
        Entry("Days ahead", SettingsPage.FEED, "How many days of games a scan reads", "days window"),
        Entry("Include live games", SettingsPage.FEED, "Also scan games already under way", "live in-game"),
        // Fair odds & sources
        Entry("Fair odds method", SettingsPage.FAIR, "Sharp books, every book's average, or a blend", "fair true odds blend sharp average"),
        Entry("Books that count as sharp", SettingsPage.FAIR, "Which books' odds count as the most accurate", "sharp pinnacle"),
        Entry("Fewest books for an average", SettingsPage.FAIR, "Skip a line with fewer books than this", "min books average"),
        Entry("Outlier guard", SettingsPage.FAIR, "Stop one stale book from faking an edge", "outlier median"),
        Entry("Devig method", SettingsPage.FAIR, "How the profit is taken out of a pair of odds", "devig vig power shin"),
        Entry("Where fair odds come from", SettingsPage.FAIR, "Turn feeds on or off and add their keys", "pinnacle pinnwire pinnapi polymarket kalshi propline parlayapi odds api keys sources feeds"),
        // Betting & Novig account
        Entry("Novig API key", SettingsPage.BETTING, "Connect Novig to bet from Vigilant", "key connect account api", novig),
        Entry("Bankroll", SettingsPage.BETTING, "The money you set aside for betting", "bankroll money"),
        Entry("Kelly fraction for suggested stakes", SettingsPage.BETTING, "How big suggested stakes are for a given edge", "kelly stake size"),
        Entry("Amount a bet starts at", SettingsPage.BETTING, "\$1, Kelly or your amount, in the bet slip and Bet sheet", "stake amount bet slip sheet dollar", novig),
        // Usage & keys
        Entry("API usage", SettingsPage.USAGE, "Credits left on each feed", "credits usage meter quota"),
        Entry("Keys backup", SettingsPage.USAGE, "Export or import your keys", "export import backup keys"),
        // Diagnostics & about
        Entry("Share with Claude", SettingsPage.HELP, "Make the diagnostics file for Claude", "diagnostics report bug claude share"),
        Entry("About", SettingsPage.HELP, "Version and where the data comes from", "version about"),
        // The Auto-bet tab
        Entry("Place bets automatically", null, "Auto-bet: turn it on or off", "auto-bet autobet automatic on off", autoBetTab),
        Entry("Presets", null, "Set every auto-bet, alert and CNO rule in one tap", "preset volume strict clv save", autoBetTab),
        Entry("Smallest edge (EV) at Novig's price now", null, "The smallest edge auto-bet takes", "auto-bet min ev minimum edge", autoBetTab),
        Entry("Books that each say +EV on their own", null, "How many books must agree before auto-bet bets", "auto-bet agree books", autoBetTab),
        Entry("Longest odds to bet", null, "No long shots past this for auto-bet", "auto-bet max odds longshot", autoBetTab),
        Entry("Shortest odds to bet", null, "No heavy favorites past this for auto-bet", "auto-bet min odds favorite", autoBetTab),
        Entry("Kinds of bet to place", null, "Props, moneylines, spreads, totals…", "auto-bet kinds markets props", autoBetTab),
        Entry("Sharp-book veto", null, "Skip a bet the sharpest book disagrees with", "auto-bet sharp veto confirm pinnacle kalshi", autoBetTab),
        Entry("Amount per bet", null, "Auto-bet's stake: Kelly, \$1 or your amount", "auto-bet stake kelly amount", autoBetTab),
        Entry("Check every", null, "How often auto-bet looks for bets", "auto-bet interval often", autoBetTab),
        Entry("Lock in profits automatically", null, "Buy the other side once a bet's odds moved your way, for a sure profit", "lock hedge arbitrage arb guarantee green", autoBetTab),
    )

    /** The entries matching every word of [query] (in the title, the line or the extra words), for these settings. */
    fun search(query: String, s: ScanSettings): List<Entry> {
        val words = query.lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e ->
            val hay = "${e.title} ${e.help} ${e.words} ${e.page?.title ?: "auto-bet"}".lowercase(Locale.US)
            e.shown(s) && (e.page?.shownIn(s) ?: true) && words.all { hay.contains(it) }
        }
    }
}
