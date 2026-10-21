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
        fun serverIsA(n: Int): Boolean = if (((n + 1) / 2) % 2 == 0) aFirst else !aFirst
        fun pointA(n: Int): Double = if (serverIsA(n)) pA else 1 - pB
        // From 6-6 on the lead stays in -1..+1 until someone goes two clear: the distribution of the lead is stepped point by point (a few dozen points hold all but a vanishing share).
        fun tail(lead0: Int, played: Int): Double {
            var dist = mapOf(lead0 to 1.0)
            var win = 0.0
            var k = played
            repeat(120) {
                val next = HashMap<Int, Double>()
                for ((lead, pr) in dist) {
                    val pa = pointA(k)
                    if (lead + 1 >= 2) win += pr * pa else next[lead + 1] = (next[lead + 1] ?: 0.0) + pr * pa
                    if (lead - 1 > -2) next[lead - 1] = (next[lead - 1] ?: 0.0) + pr * (1 - pa)
                }
                dist = next
                k++
            }
            return win
        }
        val memo = HashMap<Int, Double>()
        fun rec(x: Int, y: Int): Double {
            if (x >= 7 && x - y >= 2) return 1.0
            if (y >= 7 && y - x >= 2) return 0.0
            if (x >= 6 && y >= 6) return tail(x - y, x + y)
            val key = x * 64 + y
            memo[key]?.let { return it }
            val pa = pointA(x + y)
            val v = pa * rec(x + 1, y) + (1 - pa) * rec(x, y + 1)
            memo[key] = v
            return v
        }
        return rec(a, b)
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
        val decider = st.setsA + st.setsB == cfg.bestOf - 1
        val tb = !decider || cfg.finalSetTiebreak
        val inTiebreak = tb && st.gamesA == 6 && st.gamesB == 6
        val curSet = when {
            inTiebreak -> tiebreakWin(pA, pB, st.aServes, st.pointsA, st.pointsB)
            st.pointsA == 0 && st.pointsB == 0 -> setWin(pA, pB, st.gamesA, st.gamesB, st.aServes, tb)
            else -> {
                // Part-way through a game: the server's chance to take it, then the set goes on with the other serving.
                val aWinsGame = if (st.aServes) gameFrom(pA, st.pointsA, st.pointsB) else 1 - gameFrom(pB, st.pointsB, st.pointsA)
                aWinsGame * setWin(pA, pB, st.gamesA + 1, st.gamesB, !st.aServes, tb) + (1 - aWinsGame) * setWin(pA, pB, st.gamesA, st.gamesB + 1, !st.aServes, tb)
            }
        }
        // Who serves the first game of a later set depends on how many games this set took, which is not known yet: the mean of both is used (a few tenths of a point at most).
        val cache = HashMap<Triple<Int, Int, Boolean>, Double>()
        fun later(sa: Int, sb: Int, aStarts: Boolean): Double {
            if (sa >= need) return 1.0
            if (sb >= need) return 0.0
            return cache.getOrPut(Triple(sa, sb, aStarts)) {
                val t = !(sa + sb == cfg.bestOf - 1) || cfg.finalSetTiebreak
                val w = setWin(pA, pB, 0, 0, aStarts, t)
                w * later(sa + 1, sb, !aStarts) + (1 - w) * later(sa, sb + 1, !aStarts)
            }
        }
        val ifWon = avg(later(st.setsA + 1, st.setsB, true), later(st.setsA + 1, st.setsB, false))
        val ifLost = avg(later(st.setsA, st.setsB + 1, true), later(st.setsA, st.setsB + 1, false))
        return curSet * ifWon + (1 - curSet) * ifLost
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
