package com.tjshea.vigilant.data.match

import java.text.Normalizer

/**
 * Matches one player's name across providers: "CJ Donaldson Jr." (Novig) and "C.J. Donaldson"
 * (Kalshi) are the same person; "Marquise Brown" and "Hollywood Brown" are not treated as one,
 * because a wrong match prices a bet against another player's line.
 */
object PlayerNames {
    private val SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv", "v")

    /** Lowercase, no accents or punctuation, initials joined ("c j" -> "cj"), no suffix. */
    fun key(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(".", "").replace("'", "").replace("’", "")
        val tokens = plain.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in SUFFIXES }
        // Two single letters in a row are one set of initials: "c j donaldson" -> "cj donaldson".
        val merged = ArrayList<String>()
        for (t in tokens) {
            val last = merged.lastOrNull()
            if (t.length == 1 && last != null && last.all { it.isLetter() } && last.length <= 2 && merged.size == 1) merged[0] = last + t
            else merged += t
        }
        return merged.joinToString(" ")
    }

    fun same(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ka = key(a)
        val kb = key(b)
        if (ka == kb) return true
        val ta = ka.split(' ')
        val tb = kb.split(' ')
        if (ta.size < 2 || tb.size < 2 || ta.last() != tb.last() || ta.size != tb.size) return false
        // Same surname, and one first name is a short form of the other ("Cam" / "Cameron").
        val fa = ta.first()
        val fb = tb.first()
        return fa.length >= 3 && fb.length >= 3 && (fa.startsWith(fb) || fb.startsWith(fa))
    }
}
