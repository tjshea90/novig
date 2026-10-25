package com.tjshea.vigilant.data.scratch

import com.tjshea.vigilant.data.reference.LineKind
import com.tjshea.vigilant.data.reference.PropLineClient
import com.tjshea.vigilant.data.reference.Side
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** Scratch (PX): the engine's PropLine parser on a real board; skipped unless PX_BOARD points at one. */
class PxReplayTest {
    @Test
    fun replay() {
        val path = System.getenv("PX_BOARD") ?: ""
        assumeTrue(path.isNotEmpty() && File(path).exists())
        val raw = GZIPInputStream(File(path).inputStream()).readBytes().toString(Charsets.UTF_8)
        val board = PropLineClient.parseBoard(raw, Json { ignoreUnknownKeys = true }, "americanfootball_nfl")
        for (e in board.events) {
            if (!e.home.contains("Texans") && !e.home.contains("Jets")) continue
            val spreads = e.markets.filter { it.kind == LineKind.SPREAD && it.quotes.any { q -> q.side == Side.HOME && q.point == -4.5 } }
            val totals = e.markets.filter { it.kind == LineKind.TOTAL && it.quotes.any { q -> q.point == 41.5 } }
            println("PX ${e.away} @ ${e.home}: markets ${e.markets.size}; home -4.5 spreads ${spreads.map { it.bookKey }}; totals 41.5 ${totals.map { it.bookKey }}")
        }
    }
}
