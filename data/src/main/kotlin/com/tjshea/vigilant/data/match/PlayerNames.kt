package com.tjshea.vigilant.data.match

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Matches one player's name across providers, which each write it their own way:
 * - "CJ Donaldson Jr." (Novig), "C.J. Donaldson" (Kalshi), "Donaldson, CJ" (some feeds);
 * - "Kiké Hernández" / "Kike Hernandez", "Amon-Ra St. Brown" / "Amon-Ra St.Brown";
 * - "Michael A. Taylor" / "Michael Taylor";
 * - "Gabe Davis" / "Gabriel Davis", "Hollywood Brown" / "Marquise Brown".
 *
 * Names are only ever compared within one game (a prop line belongs to one matched event), so a
 * same-surname match with a known short form of the first name is safe. A looser match (surname
 * alone, first initial alone) is not: a wrong match prices a bet against another player's line.
 */
object PlayerNames {
    private val SUFFIXES = setOf("jr", "sr", "ii", "iii", "iv", "v")

    /**
     * First names and the short forms or nicknames the books print instead, grouped under one
     * canonical spelling. Only needed where neither is a 3+ letter prefix of the other ("Cam" /
     * "Cameron" already matches).
     */
    private val FIRST_NAMES: Map<String, String> = buildMap {
        fun group(canon: String, vararg names: String) {
            put(canon, canon)
            names.forEach { put(it, canon) }
        }
        group("michael", "mike", "mikey")
        group("matthew", "matt")
        group("joshua", "josh")
        group("christopher", "chris")
        group("nicholas", "nick", "nico", "nicolas")
        group("jacob", "jake")
        group("joseph", "joe", "joey")
        group("anthony", "tony")
        group("zachary", "zach", "zack", "zac", "zak")
        group("alexander", "alex")
        group("benjamin", "ben")
        group("daniel", "dan", "danny")
        group("david", "dave")
        group("robert", "rob", "robbie", "bob", "bobby")
        group("william", "will", "bill", "billy", "willie")
        group("james", "jim", "jimmy")
        group("thomas", "tom", "tommy")
        group("steven", "stephen", "steve")
        group("samuel", "sam", "sammy")
        group("patrick", "pat")
        group("edward", "ed", "eddie")
        group("gabriel", "gabe")
        group("nathaniel", "nathan", "nate")
        group("andrew", "andy", "drew")
        group("gregory", "greg")
        group("jeffrey", "jeff")
        group("kenneth", "ken", "kenny")
        group("ronald", "ron", "ronnie")
        group("timothy", "tim")
        group("richard", "rich", "rick", "ricky")
        group("charles", "charlie", "chuck")
        group("maxwell", "max")
        group("vincent", "vince")
        group("dominic", "dominick", "dom")
        group("frederick", "fred", "freddie")
        group("lawrence", "larry")
        group("theodore", "ted", "teddy", "theo")
        group("jonathan", "jon", "jonny")
        // Players the books list by nickname.
        group("marquise", "hollywood") // Marquise "Hollywood" Brown
        group("demario", "pop") // Demario "Pop" Douglas
        group("enrique", "kike") // Enrique "Kiké" Hernández
        group("jasson", "jazz") // Jasson "Jazz" Chisholm Jr.
    }

    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Lowercase, no accents or punctuation, "Last, First" put back in order, initials joined
     * ("c j" -> "cj"), no suffix, no middle initial.
     */
    fun key(name: String): String {
        cache[name]?.let { return it }
        if (cache.size > CACHE_LIMIT) cache.clear()
        return compute(name).also { cache[name] = it }
    }

    private fun compute(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(".", "").replace("'", "").replace("’", "")
        fun tokens(s: String) = s.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in SUFFIXES }
        // "Brown, Marquise" -> "Marquise Brown". A comma before a suffix ("Walker, III") isn't one.
        val parts = plain.split(',')
        val ordered = if (parts.size == 2 && tokens(parts[1]).isNotEmpty() && tokens(parts[0]).isNotEmpty()) {
            tokens(parts[1]) + tokens(parts[0])
        } else {
            tokens(plain)
        }
        // Two single letters in a row are one set of initials: "c j donaldson" -> "cj donaldson".
        val merged = ArrayList<String>()
        for (t in ordered) {
            val last = merged.lastOrNull()
            if (t.length == 1 && last != null && last.all { it.isLetter() } && last.length <= 2 && merged.size == 1) merged[0] = last + t
            else merged += t
        }
        // A middle initial ("Michael A Taylor") is printed by some books and not others.
        return merged.filterIndexed { i, t -> i == 0 || i == merged.lastIndex || t.length > 1 }.joinToString(" ")
    }

    fun same(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ka = key(a)
        val kb = key(b)
        if (ka == kb) return true
        // "St. Brown" / "St.Brown", "Smith-Njigba" / "SmithNjigba": the same letters, spaced differently.
        if (ka.replace(" ", "") == kb.replace(" ", "")) return true
        val ta = ka.split(' ')
        val tb = kb.split(' ')
        if (ta.size < 2 || tb.size < 2 || ta.size != tb.size || ta.drop(1) != tb.drop(1)) return false
        // Same surname, and the first names are one name: a known short form or nickname...
        val fa = ta.first()
        val fb = tb.first()
        val ca = FIRST_NAMES[fa]
        if (ca != null && ca == FIRST_NAMES[fb]) return true
        // ...or one is a 3+ letter prefix of the other ("Cam" / "Cameron").
        return fa.length >= 3 && fb.length >= 3 && (fa.startsWith(fb) || fb.startsWith(fa))
    }

    private const val CACHE_LIMIT = 5_000
}
