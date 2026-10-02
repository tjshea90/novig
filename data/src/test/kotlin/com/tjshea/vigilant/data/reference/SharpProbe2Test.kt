package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.tracker.BetGrader
import org.junit.Test

class SharpProbe2Test {
    @Test
    fun probe() {
        for (m in listOf("Player Receiving Yards", "Player Passing Yards", "Player Rushing Yards", "Player Receptions", "Player Passing Touchdowns", "Player Anytime Touchdown",
            "Player Rushing + Receiving Yards", "Player Pass Completions", "Player Pass Attempts", "Player Rush Attempts", "Player Interceptions", "Player Longest Reception",
            "Pitcher Strikeouts", "Player Strikeouts", "Batter Total Bases", "Player Total Bases", "Player Home Runs", "Batter Hits", "Player Hits + Runs + RBIs", "Pitcher Outs",
            "Player Points", "Player Rebounds", "Player Assists", "Player Threes", "Player 3-Pointers Made", "Player Points + Rebounds + Assists",
            "Player Shots On Goal", "Player Goals", "Player Saves", "Goalie Saves", "Player Anytime Goal")) {
            println("STATPROBE $m -> ${BetGrader.statOf(m)}")
        }
    }
}
