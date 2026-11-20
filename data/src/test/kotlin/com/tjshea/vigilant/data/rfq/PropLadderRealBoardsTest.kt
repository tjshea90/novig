package com.tjshea.vigilant.data.rfq

import com.tjshea.vigilant.data.match.PlayerNames
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.Side
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The real boards (opt-in: `VIGILANT_PROPBOARDS=<dir of board-*.json.gz from the propline-samples Release, task propcov>`): the engine's own parser and [PropLadderFair] against Novig's two-sided rows of the same
 * boards. Prints coverage and error; asserts only the floor that made MK22 worth building.
 */
class PropLadderRealBoardsTest {
    @Test fun votesCoverMostOfNovigsStrikesOnTheRealBoards() {
        val dir = System.getenv("VIGILANT_PROPBOARDS")
        assumeTrue("set VIGILANT_PROPBOARDS to run", !dir.isNullOrBlank())
        val json = Json { ignoreUnknownKeys = true }
        var rungs = 0; var withVotes = 0; var withThree = 0; var engineLike = 0; var absErr = 0.0; var errN = 0; var bias = 0.0
        for (f in File(dir!!).listFiles { x -> x.name.startsWith("board-") && x.name.endsWith(".json.gz") }!!.sortedBy { it.name }) {
            val sport = f.name.removePrefix("board-").substringBefore("-player_")
            val raw = GZIPInputStream(f.inputStream()).bufferedReader().readText()
            val board = PropLineClient.parseBoard(raw, json, sport)
            val store = PropLadderFair.build(board.events)
            val novigById = board.novig.associateBy { it.id }
            var fr = 0; var fv = 0
            for (ev in board.events) {
                val nov = novigById[ev.id] ?: continue
                val twoSidedByStrike = HashMap<String, Int>()
                for (m in ev.markets) if (m.stat != null && m.subject != null && m.line != null) twoSidedByStrike.merge("${PlayerNames.key(m.subject!!)}|${m.stat}|${m.line}", 1, Int::plus)
                for (m in nov.markets) {
                    val stat = m.stat ?: continue; val subject = m.subject ?: continue; val strike = m.line ?: continue
                    val over = m.quotes.singleOrNull { it.side == Side.OVER } ?: continue; val under = m.quotes.singleOrNull { it.side == Side.UNDER } ?: continue
                    val truth = PropLadderFair.devig(over.decimalOdds, under.decimalOdds) ?: continue
                    if (truth !in 0.1..0.9) continue
                    rungs++; fr++
                    if ((twoSidedByStrike["${PlayerNames.key(subject)}|$stat|$strike"] ?: 0) >= 2) engineLike++
                    val s = store.table(ev.id, PlayerNames.key(subject), stat)?.let { PropLadderFair.atStrike(it, strike, true) } ?: continue
                    withVotes++; fv++
                    if (s.companies >= 3) { withThree++; absErr += Math.abs(s.overProb - truth); bias += s.overProb - truth; errN++ }
                }
            }
            println("PROPBOARD ${f.name}: novig rungs $fr, with votes $fv, margins ${store.holds.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=%.3f".format(it.value) }}")
        }
        println("PROPBOARD total: Novig rungs $rungs; the scan's rule (2+ two-sided books at the strike) $engineLike (${100 * engineLike / rungs}%); with votes $withVotes (${100 * withVotes / rungs}%); with 3+ companies $withThree (${100 * withThree / rungs}%); MAE of 3+ ${"%.4f".format(absErr / errN)} bias ${"%+.4f".format(bias / errN)}")
        assert(withVotes > 2 * engineLike) { "votes should cover at least twice what the scan's rule does" }
    }
}
