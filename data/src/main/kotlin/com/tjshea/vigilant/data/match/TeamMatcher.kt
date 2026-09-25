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
    )

    private val ALIASES = mapOf(
        "state" to "st",
        "saint" to "st",
        "utd" to "united",
        "u" to "university",
    )

    fun tokens(name: String): List<String> {
        var s = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        s = s.replace("&", " and ").replace(Regex("[^a-z0-9 ]"), " ")
        s = " " + s.replace(Regex("\\s+"), " ").trim() + " "
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
        val used = BooleanArray(b.size)
        var matched = 0
        for (t in a) {
            val i = b.indices.firstOrNull { !used[it] && tokenMatch(t, b[it]) } ?: continue
            used[i] = true
            matched++
        }
        return matched.toDouble() / min(a.size, b.size)
    }

    /**
     * How well an abbreviation like "DAL", "MSST" or "LAR" fits a full name. Only meaningful for
     * short single-token labels; 0 when the label isn't an abbreviation.
     */
    fun abbreviationScore(label: String, fullName: String): Int {
        val a = label.lowercase().filter(Char::isLetterOrDigit)
        if (a.length !in 2..5 || label.trim().contains(' ')) return 0
        val words = Normalizer.normalize(fullName, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
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

    private fun isSubsequence(needle: String, hay: String): Boolean {
        var i = 0
        for (c in hay) if (i < needle.length && needle[i] == c) i++
        return i == needle.length
    }
}
