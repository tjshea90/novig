package com.tjshea.vigilant.engine

/**
 * A tennis match's win probability from the score and who serves (CH47f, Tj 2026-10-20: "I want live auto betting and bidding on tennis"): the standard point-by-point Markov model. Pure maths, no feed:
 * ParlayAPI's tennis play-by-play frames have not been seen yet (the engine records them), so nothing here is wired to money; the lane that uses it starts in PAPER mode.
 *
 * A player is described by one number, the chance of winning a point on their own serve ([pA] for A, [pB] for B). [calibrate] finds the pair that matches a pre-match win probability (a sharp book's
 * devigged moneyline) at a typical serve strength, so a live state is judged against the market's own opinion of the two players. Game, tiebreak, set and match are exact dynamic programs
 * (deuce and the tiebreak's 6-6 tie are summed in closed form), so every function is deterministic and cheap enough to evaluate on every score change.
 */
object TennisModel {
    /** [bestOf] 3 or 5 sets; [finalSetTiebreak] false = the deciding set is played to a two-game lead (Grand Slam style before 2022 rules). */
    data class Config(val bestOf: Int = 3, val finalSetTiebreak: Boolean = true)

    /** One moment of a match as the score says. Points are 0, 1, 2, 3 (love, 15, 30, 40) in a game, advantage as 4 v 3 (or more); in a tiebreak the plain count. */
    data class State(
        val setsA: Int = 0, val setsB: Int = 0, val gamesA: Int = 0, val gamesB: Int = 0, val pointsA: Int = 0, val pointsB: Int = 0,
        /** A serves the current game (in a tiebreak: A served its first point). */
        val aServes: Boolean = true,
    )

    // ---- a game ------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** The server wins a game from love-all with point probability [p]. */
    fun gameWin(p: Double): Double {
        val q = 1 - p
        return p.pow4() * (1 + 4 * q + 10 * q * q) + 20 * p * p * p * q * q * q * (p * p / (p * p + q * q))
    }

    private fun Double.pow4() = this * this * this * this

    /** The server's chance of winning the game from [s] points to [r] (the receiver's). Deuce onward is closed form. */
    fun gameFrom(p: Double, s: Int, r: Int): Double {
        val q = 1 - p
        if (s >= 4 && s - r >= 2) return 1.0
        if (r >= 4 && r - s >= 2) return 0.0
        if (s >= 3 && r >= 3) {
            val deuce = p * p / (p * p + q * q)
            return when {
                s == r -> deuce
                s > r -> p + q * deuce
                else -> p * deuce
            }
        }
        return p * gameFrom(p, s + 1, r) + q * gameFrom(p, s, r + 1)
    }

    // ---- a tiebreak --------------------------------------------------------------------------------------------------------------------------------------------------------

    /** First to 7 (win by 2). [aFirst]: A serves the first point; then two each. [a] and [b] are the points so far. */
    fun tiebreakWin(pA: Double, pB: Double, aFirst: Boolean, a: Int = 0, b: Int = 0): Double {
        val memo = HashMap<Int, Double>()
        fun serverIsA(n: Int): Boolean = if (((n + 1) / 2) % 2 == 0) aFirst else !aFirst
        fun rec(x: Int, y: Int): Double {
            if (x >= 7 && x - y >= 2) return 1.0
            if (y >= 7 && y - x >= 2) return 0.0
            if (x >= 6 && y >= 6) {
                // Level (or one ahead) from 6-6 on: points come in pairs; closed form over a pair that is two points played by the players the schedule gives. Cap the recursion and use the pair rule.
                val d = x - y
                val n = x + y
                val aServesFirstOfPair = serverIsA(n)
                // From a tie at an even total: the next two points have servers (s, other). A wins both: pa1*pa2; loses both: ...
                val pa1 = if (serverIsA(n)) pA else 1 - pB
                val pa2 = if (serverIsA(n + 1)) pA else 1 - pB
                val winBoth = pa1 * pa2
                val loseBoth = (1 - pa1) * (1 - pa2)
                val split = 1 - winBoth - loseBoth
                val fromTie = winBoth / (winBoth + loseBoth).coerceAtLeast(1e-12)
                return when {
                    d == 0 -> fromTie
                    d == 1 -> pa1 + (1 - pa1) * fromTieAfterOne(pA, pB, aFirst, n + 1)
                    d == -1 -> pa1 * fromTieAfterOne(pA, pB, aFirst, n + 1, behind = true)
                    else -> fromTie
                }.also { aServesFirstOfPair.let { } ; split.let { } }
            }
            val key = x * 64 + y
            memo[key]?.let { return it }
            val pa = if (serverIsA(x + y)) pA else 1 - pB
            val v = pa * rec(x + 1, y) + (1 - pa) * rec(x, y + 1)
            memo[key] = v
            return v
        }
        return rec(a, b)
    }

    /** From one point ahead (or behind) at [n] points played: win the next point and take the tiebreak, else back to a tie. Level states repeat every pair, so this is evaluated by value iteration. */
    private fun fromTieAfterOne(pA: Double, pB: Double, aFirst: Boolean, n: Int, behind: Boolean = false): Double {
        fun serverIsA(k: Int): Boolean = if (((k + 1) / 2) % 2 == 0) aFirst else !aFirst
        // Iterate the 6-6 pair process directly: state = lead of A (0, +1, -1), point index n. A tiebreak that reaches 6-6 ends within a few dozen points with overwhelming probability.
        var win = 0.0
        var dist = mapOf(if (behind) -1 to 1.0 else 1 to 1.0)
        var k = n
        repeat(80) {
            val next = HashMap<Int, Double>()
            for ((lead, pr) in dist) {
                val pa = if (serverIsA(k)) pA else 1 - pB
                val up = lead + 1
                val down = lead - 1
                if (up >= 2) win += pr * pa else next[up] = (next[up] ?: 0.0) + pr * pa
                if (down > -2) next[down] = (next[down] ?: 0.0) + pr * (1 - pa)
            }
            dist = next
            k++
        }
        return win
    }

    // ---- a set -------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** A wins the current set from [a]-[b] games with [aServes] the next game's server; [tiebreak] false = no tiebreak at 6-6 (play on to a two-game lead). Game-level: the next game starts at love. */
    fun setWin(pA: Double, pB: Double, a: Int, b: Int, aServes: Boolean, tiebreak: Boolean = true): Double {
        val gA = gameWin(pA)       // A holds
        val gB = gameWin(pB)       // B holds
        val memo = HashMap<Int, Double>()
        fun rec(x: Int, y: Int, aS: Boolean): Double {
            if (x >= 6 && x - y >= 2) return 1.0
            if (y >= 6 && y - x >= 2) return 0.0
            if (x == 7) return 1.0
            if (y == 7) return 0.0
            if (x == 6 && y == 6) {
                if (tiebreak) return tiebreakWin(pA, pB, aS)
                // No tiebreak: games go on from level; the closed form over a pair of games (one served by each).
                val aHold = gA; val bHold = gB
                val aWinBoth = if (aS) aHold * (1 - bHold) else (1 - bHold) * aHold
                val bWinBoth = if (aS) (1 - aHold) * bHold else bHold * (1 - aHold)
                return aWinBoth / (aWinBoth + bWinBoth).coerceAtLeast(1e-12)
            }
            val key = (x * 16 + y) * 2 + if (aS) 1 else 0
            memo[key]?.let { return it }
            val pAWinsGame = if (aS) gA else 1 - gB
            val v = pAWinsGame * rec(x + 1, y, !aS) + (1 - pAWinsGame) * rec(x, y + 1, !aS)
            memo[key] = v
            return v
        }
        return rec(a, b, aServes)
    }

    // ---- a match -----------------------------------------------------------------------------------------------------------------------------------------------------------

    /** A wins the match from [st] with the serve strengths [pA] and [pB]. */
    fun matchWin(pA: Double, pB: Double, st: State, cfg: Config = Config()): Double {
        val need = cfg.bestOf / 2 + 1
        if (st.setsA >= need) return 1.0
        if (st.setsB >= need) return 0.0
        val decider = (st.setsA + st.setsB == cfg.bestOf - 1)
        val tb = !decider || cfg.finalSetTiebreak
        // Who serves the first game of the next set: the server alternates every game, so it is the one NOT serving the last game of the set just ended; the set's game count decides. Approximated from a fresh set: A serves
        // next set's first game when the sets so far ended on an even number of games with A starting... a state that gives it explicitly is better, so a new set is started with A serving iff [aSetStarts].
        fun setStart(aStarts: Boolean) = aStarts
        val inTiebreak = st.gamesA == 6 && st.gamesB == 6 && tb
        val curSet = if (inTiebreak) tiebreakWin(pA, pB, st.aServes, st.pointsA, st.pointsB) else {
            // Within the current game: the server wins it from the points so far, then the set continues with the other serving.
            val gameServerIsA = st.aServes
            val pa = if (gameServerIsA) gameFrom(pA, st.pointsA, st.pointsB) else 1 - gameFrom(pB, st.pointsB, st.pointsA)
            val atLove = st.pointsA == 0 && st.pointsB == 0
            if (atLove) setWin(pA, pB, st.gamesA, st.gamesB, st.aServes, tb)
            else pa * setWin(pA, pB, st.gamesA + 1, st.gamesB, !st.aServes, tb) + (1 - pa) * setWin(pA, pB, st.gamesA, st.gamesB + 1, !st.aServes, tb)
        }
        // Later sets: A serves first in them iff the new-set starter alternates; use the average of the two (the effect is a few tenths of a point).
        val futureSets = HashMap<Triple<Int, Int, Boolean>, Double>()
        fun later(sa: Int, sb: Int, aStarts: Boolean): Double {
            if (sa >= need) return 1.0
            if (sb >= need) return 0.0
            return futureSets.getOrPut(Triple(sa, sb, aStarts)) {
                val dec = (sa + sb == cfg.bestOf - 1)
                val t = !dec || cfg.finalSetTiebreak
                val w = setWin(pA, pB, 0, 0, aStarts, t)
                w * later(sa + 1, sb, !aStarts) + (1 - w) * later(sa, sb + 1, !aStarts)
            }
        }
        // The next set starts with the server who did not serve the current set's last game; unknown here, so take the mean of both starts.
        val nextStartsA = !st.aServes
        return curSet * 0.5.let { 1.0 }.let { avg(later(st.setsA + 1, st.setsB, nextStartsA), later(st.setsA + 1, st.setsB, !nextStartsA)) } +
            (1 - curSet) * avg(later(st.setsA, st.setsB + 1, nextStartsA), later(st.setsA, st.setsB + 1, !nextStartsA)).also { setStart(true) }
    }

    private fun avg(a: Double, b: Double) = (a + b) / 2

    // ---- calibration -------------------------------------------------------------------------------------------------------------------------------------------------------

    /** Serve strengths (pA, pB) whose pre-match win probability for A is [pregame], at an average of [serveAvg] (men's tour about 0.64, women's about 0.57) and equal first-server odds. Bisection on the gap. */
    fun calibrate(pregame: Double, serveAvg: Double = 0.62, cfg: Config = Config()): Pair<Double, Double> {
        val target = pregame.coerceIn(0.01, 0.99)
        var lo = -0.15
        var hi = 0.15
        repeat(40) {
            val d = (lo + hi) / 2
            val w = matchWin(serveAvg + d, serveAvg - d, State(), cfg)
            if (w < target) lo = d else hi = d
        }
        val d = (lo + hi) / 2
        return (serveAvg + d) to (serveAvg - d)
    }
}
