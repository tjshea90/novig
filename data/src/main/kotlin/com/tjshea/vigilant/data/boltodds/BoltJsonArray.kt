package com.tjshea.vigilant.data.boltodds

import java.io.Reader

/**
 * Reads a JSON array of objects from a stream ONE ELEMENT AT A TIME, so a snapshot of a whole sport's lines and props (tens of megabytes: BOLTODDS_API.md §4, `/api/snapshot`) is never in memory: only the
 * text of the element being read is. It follows the same rule as [com.tjshea.vigilant.data.JsonSplit] (string- and escape-aware, brace-counting) but over a [Reader] instead of a String. A reply that does not
 * start with `[` is an error object (`{"detail":"Invalid API token"}`): its text is returned as [Result.notAnArray] so the caller can say why.
 */
object BoltJsonArray {
    class Result(val elements: Int, val notAnArray: String?)

    fun forEachElement(reader: Reader, onElement: (String) -> Unit): Result {
        val buf = CharArray(16 * 1024)
        var started = false
        var depth = 0
        var inString = false
        var escape = false
        var count = 0
        val el = StringBuilder()
        val head = StringBuilder()
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            var i = 0
            while (i < n) {
                val c = buf[i++]
                if (!started) {
                    if (c == '[') { started = true; continue }
                    if (!c.isWhitespace()) {
                        // Not an array: keep its start (up to 400 characters) to say what it was.
                        head.append(c)
                        while (i < n && head.length < 400) head.append(buf[i++])
                        while (head.length < 400) {
                            val m = reader.read(buf)
                            if (m < 0) break
                            head.append(buf, 0, minOf(m, 400 - head.length))
                        }
                        return Result(0, head.toString().trim())
                    }
                    continue
                }
                if (depth == 0) {
                    when {
                        c == '{' -> { depth = 1; el.setLength(0); el.append(c) }
                        c == ']' -> return Result(count, null)
                        else -> Unit // commas, whitespace between elements
                    }
                    continue
                }
                el.append(c)
                if (inString) {
                    if (escape) escape = false else if (c == '\\') escape = true else if (c == '"') inString = false
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> {
                        depth--
                        if (depth == 0) {
                            onElement(el.toString())
                            count++
                            el.setLength(0)
                        }
                    }
                }
            }
        }
        return Result(count, if (started) null else head.toString().trim().ifEmpty { "an empty reply" })
    }
}
