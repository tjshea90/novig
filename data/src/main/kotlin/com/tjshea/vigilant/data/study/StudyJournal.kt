package com.tjshea.vigilant.data.study

import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * The scan study's files: one journal per game day (Eastern time, [com.tjshea.vigilant.data.tracker.FreeScores.etDate] of the start), `study-2026-10-03.jsonl`,
 * one JSON line per event ([Line]). Only ever appended to: a scan adds a few lines, nothing is rewritten, so logging every scan costs the phone almost
 * nothing however many bets pile up (the Tracker's file is one document rewritten whole on every save, fine for hundreds of bets and not for tens of
 * thousands). A line cut short by a crash is skipped when read; the next append starts on a fresh line. Storage is no concern (Tj, 2026-09-30): nothing is
 * ever deleted.
 */
class StudyJournal(private val dir: File) {

    fun file(day: LocalDate) = File(dir, "$PREFIX$day$SUFFIX")

    /** The days that have a journal, oldest first. */
    fun days(): List<LocalDate> = dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
        ?.mapNotNull { f -> runCatching { LocalDate.parse(f.name.removePrefix(PREFIX).removeSuffix(SUFFIX)) }.getOrNull() }
        ?.sorted().orEmpty()

    /** Adds [lines] to [day]'s journal in one write, flushed to the disk. Throws when the disk refuses (the caller keeps them to try again). */
    @Synchronized
    fun append(day: LocalDate, lines: List<Line>) {
        if (lines.isEmpty()) return
        dir.mkdirs()
        val f = file(day)
        val text = StringBuilder()
        // A crash mid-line left no newline: start on a fresh one so the torn line stays a single skipped line.
        if (f.exists() && f.length() > 0 && !endsWithNewline(f)) text.append('\n')
        for (l in lines) text.append(StudyJson.encodeToString(Line.serializer(), l)).append('\n')
        FileOutputStream(f, true).use { out ->
            out.write(text.toString().toByteArray())
            out.fd.sync()
        }
    }

    /** [day]'s lines, in the order written; a line that can't be read is skipped. */
    fun read(day: LocalDate): Sequence<Line> = sequence {
        val f = file(day)
        if (!f.exists()) return@sequence
        f.bufferedReader().useLines { lines ->
            for (raw in lines) {
                if (raw.isBlank()) continue
                val line = runCatching { StudyJson.decodeFromString(Line.serializer(), raw) }.getOrNull() ?: continue
                yield(line)
            }
        }
    }

    /** [day]'s bets folded from their lines: the first line for a bet makes it, every later one adds to it. Bets by id, in the order first listed. */
    fun fold(day: LocalDate): Map<String, StudyBet> {
        val out = LinkedHashMap<String, StudyBet>()
        for (l in read(day)) {
            when (l.e) {
                Line.BET -> if (l.b != null && l.id !in out) out[l.id] = StudyBet(l.id, l.b, l.sc)
                Line.SIGHT -> if (l.s != null) out[l.id]?.addSight(l.t, l.s)
                Line.CHECK -> if (l.a != null) out[l.id]?.applyCheck(l.t, l.a)
                Line.VIG -> if (l.a != null) out[l.id]?.applyVig(l.a)
                Line.CNO_REC -> if (l.a != null) out[l.id]?.applyCnoRec(l.a)
                Line.RES -> if (l.r != null) out[l.id]?.applyResult(l.t, l.r)
                Line.IDS -> out[l.id]?.applyIds(l.m, l.o)
            }
        }
        return out
    }

    /** The bytes of every journal (Diagnostics' storage line). */
    fun bytes(): Long = dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }?.sumOf { it.length() } ?: 0L

    private fun endsWithNewline(f: File): Boolean = java.io.RandomAccessFile(f, "r").use { r ->
        r.seek(r.length() - 1)
        r.read() == '\n'.code
    }

    companion object {
        const val PREFIX = "study-"
        const val SUFFIX = ".jsonl"
    }
}
