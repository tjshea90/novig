package com.tjshea.vigilant.data.tracker

import java.util.Locale
import kotlin.math.abs

/**
 * Whether a close a source found can be THIS bet's: a close far from the fair price the bet was made at, with the game as near as the bet was, is another
 * game's or another side's (Tj's scan-study file, 2026-10-03: a Washington State moneyline of -117, last seen at -115 two hours before the start, "closed"
 * at +272 and counted as -50% CLV; 41 other closes were within 3.3 points of the last fair price). The sources match teams by name, which can go wrong
 * in ways no one thought of; this checks the answer, whichever source gave it ([CloseBackfill] asks it of every one).
 *
 * The tolerance grows with how long before the start the bet's own fair price was taken, from Tj's 230 real closes (the move between the fair price when
 * bet and the close): within an hour the largest was 2.6 points, within 6 hours 6.8, within a day 14.7 (a player ruled out hours ahead), beyond a day 10.4.
 * Each bar here is 3 to 4 times that, so a real move, news included, is kept (dropping the biggest real moves would flatter CLV) and only a close that
 * can't be this bet's goes. The bet's [TrackedBet.fairAtBet] is the yardstick; with none (an imported ✓ mark) nothing is checked.
 */
object ClosePlausibility {
    /** The most the close may differ from the fair price when bet (probability points as a fraction), [gapMs] before the start. */
    fun maxMove(gapMs: Long): Double = when {
        gapMs <= HOUR_MS -> 0.10
        gapMs <= 6 * HOUR_MS -> 0.12
        gapMs <= 24 * HOUR_MS -> 0.20
        else -> 0.25
    }

    /** Why [found] can't be [b]'s close, in words for the bet's close note, or null when it can (or there's nothing to compare it with). */
    fun reason(b: TrackedBet, found: CloseLookup.Found): String? {
        val bet = b.fairAtBet ?: return null
        val gap = b.startsTs - b.createdAtMs
        val move = abs(found.fair - bet)
        if (move <= maxMove(gap)) return null
        fun pct(p: Double) = String.format(Locale.US, "%.1f%%", p * 100)
        val before = if (gap >= HOUR_MS) "${gap / HOUR_MS} h" else "${(gap / 60_000L).coerceAtLeast(0)} min"
        return "${found.via}'s close (${pct(found.fair)}) is ${String.format(Locale.US, "%.0f", move * 100)} points from the fair price when the bet was made (${pct(bet)}), " +
            "$before before the start: probably another game or side, not used"
    }

    private const val HOUR_MS = 3_600_000L
}
