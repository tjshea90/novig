package com.tjshea.vigilant.data.bench

import com.tjshea.vigilant.data.match.TeamMatcher
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.reference.RefEvent
import com.tjshea.vigilant.data.reference.RefSnapshot
import com.tjshea.vigilant.data.scanner.Planner
import org.junit.Test

class MatchBench {
    private val schools = listOf("Alabama","Auburn","Georgia","Florida","Florida State","Miami","Texas","Texas Tech","Texas A&M","Oklahoma","Oklahoma State","Kansas","Kansas State","Iowa","Iowa State","Ohio State","Michigan","Michigan State","Penn State","Wisconsin","Minnesota","Nebraska","Purdue","Indiana","Illinois","Northwestern","Oregon","Oregon State","Washington","Washington State","USC","UCLA","Utah","Arizona","Arizona State","Colorado","Stanford","California","Clemson","Louisville","Duke","North Carolina","NC State","Virginia","Virginia Tech","Pittsburgh","Syracuse","Boston College","Wake Forest","Georgia Tech","LSU","Ole Miss","Mississippi State","Arkansas","Missouri","Tennessee","Kentucky","South Carolina","Vanderbilt","Baylor","TCU","SMU","Houston","Cincinnati","UCF","BYU","West Virginia","Memphis","Tulane","Navy","Army","Air Force","Boise State","San Diego State","Fresno State","Utah State","Colorado State","Wyoming","New Mexico","New Mexico State","UNLV","Nevada","Hawaii","Toledo","Ohio","Miami (OH)","Buffalo","Akron","Kent State","Bowling Green","Ball State","Western Michigan","Central Michigan","Eastern Michigan","Northern Illinois","Marshall","Appalachian State","Coastal Carolina","Georgia Southern","Georgia State","Troy","South Alabama","Louisiana","Louisiana Tech","Liberty","Jacksonville State","Sam Houston","Western Kentucky","Middle Tennessee","FIU","FAU","UTSA","North Texas","Rice","Tulsa","UAB","Temple","East Carolina","Charlotte","Old Dominion","James Madison","Texas State")
    @Test fun bench() {
        val now = 1_790_500_000_000L
        val n = schools.size / 2 * 2
        val novig = (0 until n step 2).map { i -> NovigEvent("e$i", "FOOTBALL", "NCAAF", NovigEvent.STATUS_PREGAME, "${schools[i]} @ ${schools[i+1]}", now + i * 60_000L) }
        val refs = (0 until n step 2).map { i -> RefEvent("r$i", "americanfootball_ncaaf", now + i * 60_000L, home = schools[i+1] + " Mascots", away = schools[i] + " Tigers", markets = emptyList()) }
        val snaps = (1..4).map { RefSnapshot("americanfootball_ncaaf", refs, now, provider = "p$it") }
        repeat(2) { Planner.matchEvents(novig, snaps) }
        val t0 = System.nanoTime()
        val runs = 5
        repeat(runs) { Planner.matchEvents(novig, snaps) }
        val ms = (System.nanoTime() - t0) / 1e6 / runs
        println("BENCH matchEvents ${novig.size} games x ${snaps.size} feeds: ${"%.1f".format(ms)} ms per plan")
        val t1 = System.nanoTime()
        repeat(100_000) { TeamMatcher.similarity("Mississippi State", "Mississippi State Bulldogs") }
        println("BENCH similarity: ${"%.2f".format((System.nanoTime() - t1) / 1e3 / 100_000)} us per call")
    }
}
