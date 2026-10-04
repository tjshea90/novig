package com.tjshea.vigilant.data.match

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Cross-provider name matching. Novig and The Odds API name teams differently:
 *
 *  - Novig events: "Baltimore Ravens @ Dallas Cowboys", "Alabama @ Mississippi State",
 *    "Newcastle United FC @ Coventry City FC", "Sedriques Dumas @ Luis Hernandez".
 *  - Novig outcomes: abbreviations ("DAL", "MSST", "NO") or initials ("L. Hernandez").
 *  - The Odds API: "Dallas Cowboys", "Alabama Crimson Tide", "Newcastle United".
 *
 * Everything here returns a score, not a yes/no, so callers can pick the best pairing and refuse
 * to guess when two candidates tie.
 */
object TeamMatcher {

    private val STOPWORDS = setOf(
        "fc", "afc", "cf", "sc", "ac", "cd", "ssc", "the", "club", "de", "round", "of", "final", "finals",
        "quarterfinal", "quarterfinals", "semifinal", "semifinals", "qualifying", "qualifier", "group",
    )

    /** Multi-word spellings that mean the same thing, applied before tokenizing. */
    private val PHRASES = listOf(
        "los angeles" to "la",
        "new york" to "ny",
        "saint " to "st ",
        "manchester" to "man",
        "a and m" to "am",
    )

    private val ALIASES = mapOf(
        "state" to "st",
        "saint" to "st",
        "utd" to "united",
        "u" to "university",
    )

    /**
     * Names repeat thousands of times in one plan (every Novig game against every feed's games,
     * four ways), and a plan is rebuilt each time a feed answers, so each name is tokenized once.
     * Measured 2026-09-27: 61 college games x 4 feeds went from 165 ms to about a tenth of that per
     * plan on a desktop JVM (a phone is several times slower).
     */
    private val tokenCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val wordCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private const val CACHE_LIMIT = 5_000

    fun tokens(name: String): List<String> {
        tokenCache[name]?.let { return it }
        if (tokenCache.size > CACHE_LIMIT) tokenCache.clear()
        return computeTokens(name).also { tokenCache[name] = it }
    }

    private fun computeTokens(name: String): List<String> {
        var s = Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
        s = s.replace("&", " and ").replace(NON_ALNUM_SPACE, " ")
        s = " " + s.replace(SPACES, " ").trim() + " "
        for ((from, to) in PHRASES) s = s.replace(" $from ", " $to ")
        return s.trim().split(' ')
            .filter { it.isNotBlank() && it !in STOPWORDS && !it.all(Char::isDigit) }
            .map { ALIASES[it] ?: it }
    }

    private fun tokenMatch(a: String, b: String): Boolean =
        a == b ||
            (a.length == 1 && b.startsWith(a)) ||
            (b.length == 1 && a.startsWith(b)) ||
            (min(a.length, b.length) >= 4 && (a.startsWith(b) || b.startsWith(a)))

    /**
     * Share of the shorter name's tokens found in the longer one, in [0, 1]. "Alabama" vs
     * "Alabama Crimson Tide" is 1.0; "L. Hernandez" vs "Luis Hernandez" is 1.0.
     */
    fun similarity(x: String, y: String): Double {
        val a = tokens(x)
        val b = tokens(y)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val usedB = BooleanArray(b.size)
        val usedA = BooleanArray(a.size)
        var matched = 0
        for ((ai, t) in a.withIndex()) {
            val i = b.indices.firstOrNull { !usedB[it] && tokenMatch(t, b[it]) } ?: continue
            usedB[i] = true
            usedA[ai] = true
            matched++
        }
        // A school qualifier on only one side ("Texas" vs "Texas Tech", "Kansas" vs "Kansas
        // State") names a different team, not a mascot. Found in the 2026-09-25 full test.
        val strayQualifiers = a.filterIndexed { i, t -> !usedA[i] && t in QUALIFIERS }.size +
            b.filterIndexed { i, t -> !usedB[i] && t in QUALIFIERS }.size
        return matched.toDouble() / min(a.size, b.size) - QUALIFIER_PENALTY * strayQualifiers
    }

    /** Each team of a game must score at least this against its counterpart ([gameScore], [whichOf]). */
    const val MIN_TEAM_SIMILARITY = 0.5

    /**
     * And together at least this: two names that share only a school word ("State" is the token `st`) score 0.5 each, so a pairing needs one team that
     * really matches. Two city-only matches (Yankees/Mets + Cubs/White Sox = 0.5 + 0.5) and "Washington State @ Fresno State" against "Oregon State @
     * Idaho State" (0.5 + 0.5) never pair a game with a different game (Tj's scan-study file, 2026-10-03: a Washington State -117 "closed" at +272, the
     * close of another State game). The scan's own planner uses the same two numbers ([com.tjshea.vigilant.data.scanner.Planner]).
     */
    const val MIN_GAME_SIMILARITY = 1.5

    /**
     * How well a feed's game ([home2] vs [away2]) is the game [home1] vs [away1], straight order only: the two teams' scores added when each clears
     * [MIN_TEAM_SIMILARITY] and together they reach [MIN_GAME_SIMILARITY], else 0.0 (not the same game).
     */
    fun gameScore(home1: String, away1: String, home2: String, away2: String): Double {
        val h = similarity(home1, home2)
        val a = similarity(away1, away2)
        return if (h >= MIN_TEAM_SIMILARITY && a >= MIN_TEAM_SIMILARITY && h + a >= MIN_GAME_SIMILARITY) h + a else 0.0
    }

    /**
     * Which of a game's two team names [team] is: 1 = [first], 2 = [second], 0 = can't tell (neither clears [MIN_TEAM_SIMILARITY], or they fit
     * equally: two "X State" teams both share a word with "Y State"). The better fit wins; never the first that merely shares a word.
     */
    fun whichOf(team: String, first: String, second: String): Int {
        val a = similarity(team, first)
        val b = similarity(team, second)
        return when {
            a >= MIN_TEAM_SIMILARITY && a > b + 1e-9 -> 1
            b >= MIN_TEAM_SIMILARITY && b > a + 1e-9 -> 2
            else -> 0
        }
    }

    /**
     * Tie-breaker between candidates [similarity] rates equally: the share of ALL tokens that
     * matched, so "Texas" prefers "Texas Longhorns" (1 of 2) over "Texas Tech Red Raiders"
     * (1 of 4) and "Miami Florida" prefers "Miami Hurricanes" over "Miami (OH) RedHawks".
     */
    fun closeness(x: String, y: String): Double {
        val a = tokens(x)
        val b = tokens(y)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val used = BooleanArray(b.size)
        var matched = 0
        for (t in a) {
            val i = b.indices.firstOrNull { !used[it] && tokenMatch(t, b[it]) } ?: continue
            used[i] = true
            matched++
        }
        return matched.toDouble() / maxOf(a.size, b.size)
    }

    /** Tokens that change which school a name means. */
    private val QUALIFIERS = setOf(
        "st", "tech", "am", "southern", "northern", "eastern", "western", "central", "international",
        "christian", "atlantic", "poly", "southeastern", "northwestern", "southwestern", "northeastern", "intl",
    )
    private const val QUALIFIER_PENALTY = 0.6

    /**
     * How well an abbreviation like "DAL", "MSST" or "LAR" fits a full name. Only meaningful for
     * short single-token labels; 0 when the label isn't an abbreviation.
     */
    fun abbreviationScore(label: String, fullName: String): Int {
        val a = label.lowercase().filter(Char::isLetterOrDigit)
        if (a.length !in 2..5 || label.trim().contains(' ')) return 0
        val words = words(fullName)
        if (words.isEmpty()) return 0
        val concat = words.joinToString("")
        val initials = words.joinToString("") { it.take(1) }
        var s = 0
        if (initials == a) s += 10
        if (words.first().startsWith(a)) s += 8
        if (concat.startsWith(a)) s += 4
        if (isSubsequence(a, concat)) s += 3
        if (a.first() == concat.first()) s += 2
        if (initials != a && (initials.startsWith(a) || a.startsWith(initials))) s += 3
        // Schools: "UNM" = U + New Mexico, "NMSU" = New Mexico State + U, "UK" = U + Kentucky.
        if (a.length >= 2 && a.first() == 'u' && a.drop(1) == initials) s += 9
        if (a.length >= 3 && a.last() == 'u' && a.dropLast(1) == initials) s += 9
        return s
    }

    /** One score for "does this outcome label name that team", abbreviations and full names alike. */
    fun labelScore(label: String, fullName: String): Double =
        max(similarity(label, fullName) * 20.0, abbreviationScore(label, fullName).toDouble())

    /**
     * Decides which of two labels is the away team and which is home. Returns true when
     * `first` is away (and `second` home), false for the reverse, null when it can't tell.
     */
    fun firstLabelIsAway(first: String, second: String, away: String, home: String): Boolean? {
        val straight = labelScore(first, away) + labelScore(second, home)
        val swapped = labelScore(first, home) + labelScore(second, away)
        return when {
            max(straight, swapped) < 4.0 -> null
            straight - swapped >= 2.0 -> true
            swapped - straight >= 2.0 -> false
            else -> null
        }
    }

    /** Which of the two teams a single label names: true = away, false = home, null = unsure. */
    fun labelIsAway(label: String, away: String, home: String): Boolean? {
        val a = labelScore(label, away)
        val h = labelScore(label, home)
        return when {
            max(a, h) < 4.0 -> null
            a - h >= 2.0 -> true
            h - a >= 2.0 -> false
            else -> null
        }
    }

    /** A name's lowercase words, accents and punctuation gone (cached like [tokens]). */
    private fun words(name: String): List<String> {
        wordCache[name]?.let { return it }
        if (wordCache.size > CACHE_LIMIT) wordCache.clear()
        return Normalizer.normalize(name, Normalizer.Form.NFD).replace(MARKS, "")
            .lowercase().split(NON_ALNUM).filter { it.isNotBlank() }
            .also { wordCache[name] = it }
    }

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM_SPACE = Regex("[^a-z0-9 ]")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")

    private fun isSubsequence(needle: String, hay: String): Boolean {
        var i = 0
        for (c in hay) if (i < needle.length && needle[i] == c) i++
        return i == needle.length
    }
}
