package com.tjshea.vigilant.data.cno

import okhttp3.HttpUrl
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads CrazyNinjaOdds' ASP.NET WebForms page (RESEARCH.md §18.1). The +EV table isn't in the page
 * itself: the page's form is posted back (as its own AJAX timer or Refresh button would) and the
 * reply is an ASP.NET "delta" holding the table's HTML. Columns are found by their headers, not
 * their positions, so a reordered or added column doesn't break the reader.
 */
object CnoPage {

    /** What a postback needs from the page: its fields and the control names to post as. */
    data class Form(
        /** Every field a browser would submit, in page order (hidden state, filters, …). */
        val fields: List<Pair<String, String>>,
        val scriptManager: String,
        /** The update panel that holds the table. */
        val gridPanel: String,
        /** The one-shot timer that loads the table after the page (disabled once it has). */
        val timer: String?,
        /** The Refresh/Update button: every later refresh goes through it. */
        val button: Pair<String, String>?,
        /** Where the form posts, relative to the page. */
        val action: String?,
        /** Every checkbox's name, ticked or not (an unticked one isn't in [fields], but can be ticked). */
        val checkboxes: List<String> = emptyList(),
    )

    /** One `length|type|id|content|` record of a delta reply. */
    data class Record(val type: String, val id: String, val content: String)

    /** The page's form, or an exception naming what's missing (CNO changed its page). */
    fun form(html: String): Form {
        val start = Regex("""<form\b[^>]*>""", RegexOption.IGNORE_CASE).find(html) ?: throw changed("no form")
        val end = html.indexOf("</form>", start.range.last, ignoreCase = true).let { if (it < 0) html.length else it }
        val body = html.substring(start.range.last + 1, end)
        val fields = mutableListOf<Pair<String, String>>()
        val checkboxes = mutableListOf<String>()
        var button: Pair<String, String>? = null
        // Inputs, selects and textareas in document order, as a browser serializes them.
        val tag = Regex("""<(input|select|textarea)\b([^>]*)>""", RegexOption.IGNORE_CASE)
        var at = 0
        while (true) {
            val m = tag.find(body, at) ?: break
            val kind = m.groupValues[1].lowercase()
            val attrs = attributes(m.groupValues[2])
            at = m.range.last + 1
            val name = attrs["name"] ?: continue
            when (kind) {
                "input" -> {
                    when ((attrs["type"] ?: "text").lowercase()) {
                        "submit" -> if (name.endsWith("\$ButtonUpdate")) button = name to (attrs["value"] ?: "Update")
                        "button", "image", "reset", "file" -> Unit
                        "checkbox", "radio" -> {
                            if (attrs["type"].equals("checkbox", ignoreCase = true)) checkboxes += name
                            if ("checked" in attrs) fields += name to (attrs["value"] ?: "on")
                        }
                        else -> fields += name to (attrs["value"] ?: "")
                    }
                }
                "select" -> {
                    val close = body.indexOf("</select>", at, ignoreCase = true).let { if (it < 0) body.length else it }
                    val options = Regex("""<option\b([^>]*)>([^<]*)""", RegexOption.IGNORE_CASE).findAll(body.substring(at, close))
                        .map { attributes(it.groupValues[1]) to text(it.groupValues[2]) }.toList()
                    val chosen = options.firstOrNull { "selected" in it.first } ?: options.firstOrNull()
                    if (chosen != null) fields += name to (chosen.first["value"] ?: chosen.second)
                    at = close
                }
                "textarea" -> {
                    val close = body.indexOf("</textarea>", at, ignoreCase = true).let { if (it < 0) body.length else it }
                    // A browser drops one newline straight after the opening tag.
                    fields += name to unescape(body.substring(at, close).removePrefix("\r\n").removePrefix("\n"))
                    at = close
                }
            }
        }
        val scriptManager = Regex("""PageRequestManager\._initialize\('([^']+)'""").find(html)?.groupValues?.get(1)
            ?: throw changed("no AJAX script manager")
        val gridPanel = Regex("""'t([^']*UpdatePanelGridView)'""").find(html)?.groupValues?.get(1)
            ?: throw changed("no table panel")
        val timer = Regex("""Sys\.UI\._Timer,\s*\{[^}]*"uniqueID":"([^"]+)"""").find(html)?.groupValues?.get(1)
        val action = attributes(start.value.removePrefix("<form").removeSuffix(">"))["action"]
        if (timer == null && button == null) throw changed("no way to load the table")
        return Form(fields, scriptManager, gridPanel, timer, button, action, checkboxes)
    }

    /**
     * Splits a delta reply. Lengths count UTF-16 units including `\r\n`, which is exactly what a
     * Kotlin String counts, so the body must be read as-is (no newline translation).
     */
    fun delta(body: String): List<Record> {
        val out = mutableListOf<Record>()
        var i = 0
        while (i < body.length) {
            val a = body.indexOf('|', i)
            if (a < 0) break
            val length = body.substring(i, a).trim().toIntOrNull() ?: throw changed("unreadable reply")
            val b = body.indexOf('|', a + 1)
            val c = if (b < 0) -1 else body.indexOf('|', b + 1)
            if (c < 0 || c + 1 + length > body.length) throw changed("cut-off reply")
            out += Record(body.substring(a + 1, b), body.substring(b + 1, c), body.substring(c + 1, c + 1 + length))
            i = c + 1 + length + 1
        }
        return out
    }

    /** Where the rows are, and what they say. */
    class Table(val rows: List<CnoRow>, val evLabel: String?, val note: String?)

    /** The +EV table inside the grid panel's HTML. [base] resolves CNO's relative links. */
    fun table(gridHtml: String, base: HttpUrl): Table {
        val note = Regex("""LabelRedMessage"[^>]*>(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(gridHtml)?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotEmpty() }
        val tableStart = gridHtml.indexOf("GridView1")
        if (tableStart < 0) return Table(emptyList(), null, note)
        val headers = Regex("""<th\b[^>]*>(.*?)</th>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(gridHtml, tableStart).map { text(it.groupValues[1]) }.toList()
        fun col(test: (String) -> Boolean): Int = headers.indexOfFirst(test)
        val ev = col { it.endsWith("EV%", ignoreCase = true) }
        val date = col { it.equals("Date", true) }
        val sport = col { it.equals("Sport", true) }
        val league = col { it.equals("League", true) }
        val event = col { it.equals("Event", true) }
        val market = col { it.equals("Market", true) }
        val bet = col { it.equals("Bet Name", true) || it.equals("Bet", true) }
        val odds = col { it.equals("Odds", true) }
        val book = col { it.equals("Sportsbook", true) || it.equals("Book", true) }
        val fair = col { it.equals("Fair Odds", true) }
        val books = col { it.equals("Books", true) }
        val extra = col { it.equals("Extra", true) }
        if (listOf(ev, event, market, bet, odds, book).any { it < 0 }) {
            if (headers.isEmpty()) return Table(emptyList(), null, note)
            throw changed("the table's columns changed (${headers.joinToString()})")
        }
        val rows = Regex("""<tr\b([^>]*)>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(gridHtml, tableStart)
            .mapNotNull { tr ->
                val cells = Regex("""<td\b[^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .findAll(tr.groupValues[2]).map { it.groupValues[1] }.toList()
                if (cells.size < headers.size) return@mapNotNull null
                val price = parseOdds(text(cells[odds])) ?: return@mapNotNull null
                val evValue = text(cells[ev]).removeSuffix("%").replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                CnoRow(
                    ev = evValue / 100.0,
                    startsAtMs = cells.getOrNull(date)?.let(::utc),
                    sport = cells.getOrNull(sport)?.let(::text).orEmpty(),
                    league = cells.getOrNull(league)?.let(::text).orEmpty(),
                    event = text(cells[event]),
                    market = text(cells[market]),
                    bet = text(cells[bet]),
                    odds = price,
                    available = available(text(cells[odds])),
                    book = text(cells[book]),
                    fairOdds = cells.getOrNull(fair)?.let { parseOdds(text(it)) },
                    fairProbability = attributes(tr.groupValues[1])["data-fairpercentage"]?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 },
                    books = cells.getOrNull(books)?.let { text(it).toIntOrNull() },
                    gameUrl = link(cells[event], base),
                    betUrl = link(cells[book], base),
                    // CNO's legend: ⚠️ = devigged from 1-way lines using an estimated juice.
                    oneWay = WARNING in text(cells[ev] + " " + cells.getOrNull(extra).orEmpty() + " " + cells.getOrNull(fair).orEmpty()),
                )
            }
            .toList()
        return Table(rows, headers[ev].removeSuffix("EV%").trim().takeIf { it.isNotEmpty() }, note)
    }

    /**
     * "Last Updated: 27 seconds ago" → 27; "1 minute and 6 seconds ago" → 66 (CNO writes both).
     * Null while CNO still says "Loading...".
     */
    fun lastUpdatedSeconds(html: String): Int? {
        val phrase = Regex("""Last Updated:\s*([^<]*?)\s+ago""", RegexOption.IGNORE_CASE).find(text(html))?.groupValues?.get(1) ?: return null
        val parts = Regex("""(\d+|an?)\s+(day|hour|minute|second)s?""", RegexOption.IGNORE_CASE).findAll(phrase).toList()
        if (parts.isEmpty()) return null
        return parts.sumOf { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            n * when (m.groupValues[2].lowercase()) { "day" -> 86_400; "hour" -> 3600; "minute" -> 60; else -> 1 }
        }
    }

    /** "+335 ($4)" → 335; "EVEN" → 100. */
    fun parseOdds(cell: String): Int? {
        if (cell.startsWith("EVEN", ignoreCase = true)) return 100
        val m = Regex("""^([+\-−]?)(\d{3,6})""").find(cell.trim()) ?: return null
        val n = m.groupValues[2].toInt()
        return if (m.groupValues[1] == "-" || m.groupValues[1] == "−") -n else n
    }

    private fun available(cell: String): Double? =
        Regex("""\(\$([\d,]+(?:\.\d+)?)\)""").find(cell)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    private val utcFormat = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

    private fun utc(cell: String): Long? {
        val raw = Regex("""utc\s*=\s*['"]([^'"]+)['"]""").find(cell)?.groupValues?.get(1) ?: return null
        return runCatching { LocalDateTime.parse(raw.trim(), utcFormat).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun link(cell: String, base: HttpUrl): String? {
        val href = Regex("""href\s*=\s*(?:'([^']*)'|"([^"]*)")""", RegexOption.IGNORE_CASE).find(cell) ?: return null
        val raw = unescape(href.groupValues[1].ifEmpty { href.groupValues[2] })
        return base.resolve(raw)?.toString()
    }

    /** A tag's attributes; bare ones (`checked`, `selected`) map to "". Names are lower-cased. */
    fun attributes(inside: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("""([^\s=/>"']+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>"']+)))?""").findAll(inside).forEach { m ->
            val name = m.groupValues[1].lowercase()
            val value = when {
                m.groups[2] != null -> m.groupValues[2]
                m.groups[3] != null -> m.groupValues[3]
                m.groups[4] != null -> m.groupValues[4]
                else -> ""
            }
            out.putIfAbsent(name, unescape(value))
        }
        return out
    }

    /** Visible text: tags dropped, entities decoded, whitespace collapsed. */
    fun text(html: String): String =
        unescape(html.replace(Regex("""<[^>]*>"""), " ")).replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()

    fun unescape(s: String): String {
        if ('&' !in s) return s
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""").replace(s) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> when (e) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                    else -> m.value
                }
            }
        }
    }

    /** CNO's "⚠️" (warning sign, with or without the emoji variation selector). */
    const val WARNING = "\u26A0"

    private fun changed(what: String) = CnoException("CrazyNinjaOdds' page changed ($what), so its list can't be read. Open it in the browser instead.")
}
