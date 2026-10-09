package com.tjshea.vigilant.data.novig.lab.gh

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigText
import com.tjshea.vigilant.data.novig.lab.AltQuote
import com.tjshea.vigilant.data.novig.lab.SgoAltQuotes
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.reference.SgoBooks
import com.tjshea.vigilant.data.reference.SgoGamesSource
import com.tjshea.vigilant.data.scanner.Leagues
import com.tjshea.vigilant.data.scanner.Planner
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * The paper lab's outside alternate lines from SportsGameOdds for a Novig game, one board read per league serving every game of it for [BOARD_MS] (SGO refreshes about every 30 s). The same
 * matching the phone's lab does (`AppContainer.labAltQuotes`), for the GitHub lab, where there is no AppContainer.
 */
class SgoLabBoards(private val games: SgoGamesSource, private val settings: () -> ScanSettings, private val clock: () -> Long = System::currentTimeMillis) {
    private val boards = ConcurrentHashMap<String, Pair<Long, RefSnapshot>>()

    suspend fun quotes(ev: NovigEvent): List<AltQuote> {
        val league = Leagues.byNovigName(ev.league) ?: return emptyList()
        if (!SgoBooks.supports(league)) return emptyList()
        val now = clock()
        val snap = boards[league.novigName]?.takeIf { now - it.first < BOARD_MS }?.second
            ?: games.odds(league, settings().copy(includeLive = true)).also { boards[league.novigName] = now to it }
        val match = Planner.matchEvents(listOf(ev), listOf(snap)).firstOrNull()?.refEvent ?: return emptyList()
        val novigHome = NovigText.parseMatchup(ev.description)?.home.orEmpty()
        return SgoAltQuotes.quotes(match, TeamMatcher.whichOf(novigHome, match.home, match.away) == 2)
    }

    companion object { const val BOARD_MS = 25_000L }
}
