package com.tjshea.vigilant.data.sxbet

import com.tjshea.vigilant.data.novig.lab.EspnGame
import com.tjshea.vigilant.data.match.TeamMatcher

/**
 * SX Bet's live scores (`fixtures:live_scores`, already on the socket) as a second score source behind ESPN's (Tj, 2026-10-20, CH31: "build the live score and odds feeds from the other sockets ... as backup or in addition").
 * SX's team order is not a rule (research/sxbet/MAPPING_2026-10-18.md), so a game is matched to Novig's by name in both orders and the scores follow the match. The period is read from the label ("5th Inning",
 * "3rd Quarter"). What `periodTime` counts (time left or time played) is not verified, so a clocked sport's score from here is for the model's paper records only; baseball has no clock and is trusted
 * (the lane's rule, [com.tjshea.vigilant.data.novig.lab.LiveLightRunner]).
 */
object SxScoreBoard {
    fun periodOf(label: String?): Int {
        val l = label?.trim().orEmpty()
        if (l.isEmpty()) return 0
        if (l.contains("overtime", true) || Regex("\\bOT\\b", RegexOption.IGNORE_CASE).containsMatchIn(l) || l.contains("extra", true) || l.contains("shootout", true)) return 99
        return Regex("(\\d+)").find(l)?.value?.toIntOrNull() ?: 0
    }

    /** "7:23" -> 443 s, "-1" (not available, or finished) -> 0, anything else unreadable -> 0. */
    fun clockOf(periodTime: String?): Double {
        val t = periodTime?.trim().orEmpty()
        if (t.isEmpty() || t == "-1") return 0.0
        return com.tjshea.vigilant.data.novig.lab.LabClock.clockSeconds(t)?.takeIf { it >= 0.0 } ?: 0.0
    }

    /** The score of the game [info] in Novig's orientation ([home], [away]): null when the names do not match in exactly one order. */
    fun forMatch(info: SxLiveBuild.EventInfo, score: SxScore, home: String, away: String): EspnGame? {
        val one = score.teamOne ?: return null
        val two = score.teamTwo ?: return null
        val same = TeamMatcher.similarity(info.teamTwo, home) >= 0.5 && TeamMatcher.similarity(info.teamOne, away) >= 0.5
        val swapped = TeamMatcher.similarity(info.teamOne, home) >= 0.5 && TeamMatcher.similarity(info.teamTwo, away) >= 0.5
        if (same == swapped) return null
        val homeScore = if (same) two else one
        val awayScore = if (same) one else two
        return EspnGame(
            home = home, homeAbbr = "", away = away, awayAbbr = "", homeScore = homeScore, awayScore = awayScore, live = !score.finished,
            period = periodOf(score.period), clockSec = clockOf(score.periodTime), done = score.finished,
        )
    }
}
