package com.tjshea.vigilant.data

/**
 * Cuts a big JSON reply into the text of its array's elements WITHOUT building a tree (Tj, 2026-10-10: "stopped reading early because the app's memory was nearly full"). A kotlinx
 * `JsonElement` tree costs about eight times the JSON's size in heap (measured: 30 MB of SGO-shaped JSON became 252 MB), and PropLine's answers average 4 MB and SportsGameOdds' 1.7 MB a call,
 * so parsing a whole reply as one tree is how a phone's 512 MB heap fills. Here only the positions of the elements are found (two ints each); a caller parses ONE element at a time and lets it go,
 * so the peak is the reply's text plus one game. The scan is string- and escape-aware and single pass. Anything it cannot make sense of returns null, and the caller falls back to the whole-tree path
 * it had before, so no reply is read differently from how it was.
 */
object JsonSplit {

    /** [raw]'s array elements, by position ([element] cuts one out), and [rest]: the reply with the array's contents removed (`"data":[]`), small enough to parse for its other keys. */
    class Elements(private val raw: String, private val starts: IntArray, private val ends: IntArray, val rest: String) {
        val size: Int get() = starts.size
        fun element(i: Int): String = raw.substring(starts[i], ends[i])
    }

    /** The elements of the array that is the whole reply ([key] null) or the value of the reply's top-level [key]; null when there is none or the text is not well formed up to its end. */
    fun elements(raw: String, key: String? = null): Elements? {
        val n = raw.length
        var i = skipWs(raw, 0)
        if (i >= n) return null
        val rootArray = key == null
        if (rootArray) {
            if (raw[i] != '[') return null
            return splitArray(raw, i, rest = { _, _ -> "[]" })
        }
        if (raw[i] != '{') return null
        i++
        // Walk the root object's members; skip every value that is not the wanted array.
        while (true) {
            i = skipWs(raw, i)
            if (i >= n) return null
            if (raw[i] == '}') return null
            if (raw[i] == ',') { i++; continue }
            if (raw[i] != '"') return null
            val keyEnd = stringEnd(raw, i) ?: return null
            val name = raw.substring(i + 1, keyEnd)
            i = skipWs(raw, keyEnd + 1)
            if (i >= n || raw[i] != ':') return null
            i = skipWs(raw, i + 1)
            if (i >= n) return null
            if (name == key && raw[i] == '[') return splitArray(raw, i, rest = { arrayStart, arrayEnd -> raw.substring(0, arrayStart) + "[]" + raw.substring(arrayEnd) })
            i = valueEnd(raw, i) ?: return null
        }
    }

    private fun splitArray(raw: String, open: Int, rest: (Int, Int) -> String): Elements? {
        val n = raw.length
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        var i = skipWs(raw, open + 1)
        if (i < n && raw[i] == ']') return Elements(raw, IntArray(0), IntArray(0), rest(open, i + 1))
        while (true) {
            i = skipWs(raw, i)
            if (i >= n) return null
            val start = i
            val end = valueEnd(raw, i) ?: return null
            starts += start; ends += end
            i = skipWs(raw, end)
            if (i >= n) return null
            when (raw[i]) {
                ',' -> i++
                ']' -> return Elements(raw, starts.toIntArray(), ends.toIntArray(), rest(open, i + 1))
                else -> return null
            }
        }
    }

    private fun skipWs(s: String, from: Int): Int {
        var i = from
        while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        return i
    }

    /** The index of the closing quote of the string opening at [open]; null when unterminated. */
    private fun stringEnd(s: String, open: Int): Int? {
        var i = open + 1
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i++
                '"' -> return i
            }
            i++
        }
        return null
    }

    /** One past the end of the JSON value starting at [from] (a string, object, array or a bare scalar); null when it does not end. */
    private fun valueEnd(s: String, from: Int): Int? {
        val n = s.length
        when (s[from]) {
            '"' -> return stringEnd(s, from)?.plus(1)
            '{', '[' -> {
                var depth = 0
                var i = from
                while (i < n) {
                    when (s[i]) {
                        '"' -> i = stringEnd(s, i) ?: return null
                        '{', '[' -> depth++
                        '}', ']' -> { depth--; if (depth == 0) return i + 1 }
                    }
                    i++
                }
                return null
            }
            else -> {
                var i = from
                while (i < n && s[i] != ',' && s[i] != ']' && s[i] != '}' && s[i] != ' ' && s[i] != '\n' && s[i] != '\r' && s[i] != '\t') i++
                return if (i == from) null else i
            }
        }
    }
}
