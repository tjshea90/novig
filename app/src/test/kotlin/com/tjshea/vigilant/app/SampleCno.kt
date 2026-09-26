package com.tjshea.vigilant.app

import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoState
import com.tjshea.vigilant.data.cno.CnoView

/** A made-up CrazyNinjaOdds list in CNO's shape (RESEARCH.md §18.1), on [SampleScan]'s clock. */
object SampleCno {
    private const val DAY = 86_400_000L

    val rows = listOf(
        CnoRow(0.0718, SampleScan.NOW + DAY, "Baseball", "MLB", "Arizona Diamondbacks @ San Diego Padres", "Player Outs Recorded", "Walker Buehler Over 15.5", 160, 6.0, "Novig", 143, 0.4115, 4, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=1"),
        CnoRow(0.0584, SampleScan.NOW + DAY, "Football", "NFL", "Minnesota Vikings @ Tampa Bay Buccaneers", "Player Receiving Yards", "Justin Jefferson Under 69.5", 117, 88.0, "Novig", 105, 0.4878, 10, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=2"),
        CnoRow(0.0528, SampleScan.NOW + DAY / 3, "Football", "NCAAF", "Stonehill @ Ohio", "Point Spread", "Ohio -33.5", 117, 100.0, "Novig", 106, 0.4854, 6, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=3"),
        CnoRow(0.0405, SampleScan.NOW + DAY, "Football", "NFL", "Las Vegas Raiders @ New Orleans Saints", "Player Receptions", "Brock Bowers Under 4.5", 100, 109.0, "Novig", -108, 0.5202, 13, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=4"),
        CnoRow(0.0381, SampleScan.NOW + DAY / 2, "Baseball", "MLB", "Arizona Diamondbacks @ San Diego Padres", "Player Hits", "Geraldo Perdomo Under 0.5", 167, 203.0, "Novig", 157, 0.3887, 11, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=5"),
        CnoRow(0.0331, SampleScan.NOW + DAY, "Football", "NFL", "New York Jets @ Detroit Lions", "Player Touchdowns", "Amon-Ra St. Brown Under 0.5", 106, 226.0, "Novig", -101, 0.5015, 10, "https://crazyninjaodds.com/site/browse/game.aspx?side_id=6"),
    )

    /** Read [readAgoMs] before [SampleScan.NOW]; CNO said its odds were 29 s old then. */
    fun snapshot(readAgoMs: Long = 20_000, url: String = CnoView.DEFAULT, rows: List<CnoRow> = this.rows) =
        CnoSnapshot(url, rows, SampleScan.NOW - readAgoMs, cnoAgeSeconds = 29, evLabel = "LW-WC")

    fun state(base: UiState = SampleScan.state(), cno: CnoState = CnoState(snapshot = snapshot())): UiState = base.copy(cno = cno)
}
