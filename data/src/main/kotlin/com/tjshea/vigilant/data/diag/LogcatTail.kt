package com.tjshea.vigilant.data.diag

/**
 * The app's own warnings and errors from Android's log (Tj, 2026-10-02: "log all types of events"): what the system and libraries said about this process
 * that the app didn't write down itself (a GC pause, a strict-mode violation, a dropped frame warning, an OkHttp or Compose complaint, a native crash line).
 * Only W and E lines, this process only, masked like every other line, the same line in a row once.
 */
object LogcatTail {

    /** One log line: when, the level (W or E), the tag, the message. */
    data class Line(val time: String, val level: Char, val tag: String, val text: String) {
        override fun toString() = "$time $level/$tag: $text"
    }

    private val THREADTIME = Regex("""^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3})\s+\d+\s+\d+\s+([VDIWEFA])\s+(.+?)\s*:\s(.*)$""")

    /** Tags that only say the app is alive (nothing to learn from them). */
    private val NOISE = setOf("chatty", "ziparchive", "InputMethodManager", "ProfileInstaller")

    /** [raw] (`logcat -v threadtime` output) as at most [max] lines of W and E, newest last. Pure. */
    fun parse(raw: String, max: Int = 120): List<Line> {
        val out = ArrayList<Line>()
        for (l in raw.lineSequence()) {
            val m = THREADTIME.find(l) ?: continue
            val level = m.groupValues[2][0]
            if (level != 'W' && level != 'E' && level != 'F' && level != 'A') continue
            val tag = m.groupValues[3].trim()
            if (tag in NOISE) continue
            val line = Line(m.groupValues[1], if (level == 'W') 'W' else 'E', tag.take(40), ProblemLog.clean(m.groupValues[4]).take(200))
            if (out.lastOrNull()?.let { it.tag == line.tag && it.text == line.text } == true) continue
            out += line
        }
        return out.takeLast(max)
    }

    /**
     * Reads this process's own log through the `logcat` command (an app may read its own lines, no permission needed), at most [timeoutMs]. Empty when it
     * can't (a phone that refuses, the tests).
     */
    fun read(pid: Int, max: Int = 120, timeoutMs: Long = 3_000): List<Line> = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", "800", "*:W").redirectErrorStream(true).start()
        val text = StringBuilder()
        val reader = Thread { runCatching { p.inputStream.bufferedReader().useLines { it.forEach { l -> if (text.length < 400_000) text.appendLine(l) } } } }.apply { isDaemon = true; start() }
        reader.join(timeoutMs)
        if (reader.isAlive) p.destroy()
        parse(text.toString(), max)
    }.getOrDefault(emptyList())
}
