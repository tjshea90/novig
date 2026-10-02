package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.tracker.PlacedIndex
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Test

class ScratchNotifyTiming {
    @Test
    fun timing() {
        val s = SampleScan.settings.copy(minEvPercent = 0.01)
        val base = SampleScan.result(s)
        val n = base.opportunities.size
        val big = base.copy(opportunities = List(8660) { i ->
            val o = base.opportunities[i % n]
            o.copy(outcome = o.outcome.copy(outcomeId = o.outcome.outcomeId + i), event = o.event.copy(startsTs = o.event.startsTs + i * 1000L))
        })
        val b0 = SampleScan.bets.first()
        val bets = List(430) { i -> b0.copy(id = "b$i", eventName = "Team $i @ Other $i", selection = "Sel $i", marketId = "m$i", outcomeId = "o$i", startsTs = SampleScan.NOW + i * 60_000L) }
        fun once(): Long {
            val t = System.nanoTime()
            val feed = big.feed(s)
            val idx = PlacedIndex.of(emptyList(), bets, SampleScan.NOW)
            val shown = idx.visible(feed).filter { s.startsInWindow(it.event.startsTs, SampleScan.NOW) }
            check(shown.size >= 0)
            return System.nanoTime() - t
        }
        repeat(30) { once() }
        val times = List(30) { once() / 1_000_000.0 }.sorted()
        println("NOTIFY-TIMING feed of ${big.opportunities.size}: median ${times[15]} ms, p90 ${times[27]} ms, min ${times[0]}")
        val t2 = List(30) { val t = System.nanoTime(); big.feed(s); (System.nanoTime() - t) / 1_000_000.0 }.sorted()
        println("NOTIFY-TIMING feed alone median ${t2[15]} ms; feed size ${big.feed(s).size}")
    }
}
