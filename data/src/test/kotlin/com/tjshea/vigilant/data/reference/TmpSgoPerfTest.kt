package com.tjshea.vigilant.data.reference

import org.junit.Test

class TmpSgoPerfTest {
    @Test fun perf() {
        val dir = "/tmp/claude-0/-home-user-novig/f36ef45c-e072-599a-a9c9-b91ee94fe110/scratchpad/sgo/"
        for (f in listOf("props_prod.json", "games_prod.json")) {
            val txt = java.io.File(dir + f).readText()
            val wanted = SgoBooks.wanted(com.tjshea.vigilant.data.scanner.ScanSettings().referenceBooks, true)
            System.gc()
            val before = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
            val t0 = System.nanoTime()
            val page = SgoParser.page(txt)
            val t1 = System.nanoTime()
            val used = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } - before
            val refs = page.events.mapNotNull { SgoConvert.toRef(it, "americanfootball_nfl", wanted) }
            val t2 = System.nanoTime()
            println("PERF $f: ${txt.length / 1024} KB; parse ${(t1 - t0) / 1_000_000} ms, retained ~${used / 1_000_000} MB; convert ${(t2 - t1) / 1_000_000} ms; events ${page.events.size}, markets ${refs.sumOf { it.markets.size }}, lines ${page.events.sumOf { e -> e.odds.sumOf { o -> o.byBook.values.sumOf { it.size } } }}")
        }
    }
}
