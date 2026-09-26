package com.tjshea.vigilant.data.cno

import com.tjshea.vigilant.engine.Devig
import com.tjshea.vigilant.engine.Fees
import com.tjshea.vigilant.engine.MarketFee
import com.tjshea.vigilant.engine.Odds

/**
 * Every book's price for a tapped CNO bet, and Vigilant's own check of it (Tj, 2026-09-26: "if
 * only one or two other sports books offer the odds then it may be just a small market with
 * inaccurate odds"; RESEARCH.md §19).
 *
 * CNO's game page has a row per side and a column per book. Only books that price **both** sides
 * can be devigged honestly, so the check counts them, devigs each worst-case (the lowest fair
 * probability of multiplicative, additive, power and Shin), takes the lower of their mean and
 * median, and prices the bet's book (Novig, nearly always) against that. The judged book's own
 * column is left out, and so are pick'em apps (PrizePicks: not two-sided odds).
 */
object CnoBooks {

    private val names = mapOf(
        "FD" to "FanDuel", "FDYW" to "FanDuel YourWay", "DK" to "DraftKings", "CZR" to "Caesars",
        "MGM" to "BetMGM", "MGM-ON" to "BetMGM (ON)", "BR" to "BetRivers", "BB" to "Bally Bet",
        "TSB" to "theScore Bet", "B365" to "Bet365", "PB" to "PointsBet", "FN" to "Fanatics",
        "HR-IN" to "Hard Rock", "HR-FL" to "Hard Rock (FL)", "HR-IL" to "Hard Rock (IL)", "HR-OH" to "Hard Rock (OH)",
        "FL" to "Fliff", "BV" to "Bovada", "BO" to "BetOnline", "CS" to "Circa", "PN" to "Pinnacle",
        "PX" to "ProphetX", "NV" to "Novig", "KI" to "Kalshi", "ST-NJ" to "Sporttrade (NJ)",
        "ST-CO" to "Sporttrade (CO)", "ST-IA" to "Sporttrade (IA)", "ST-AZ" to "Sporttrade (AZ)",
        "ST-VA" to "Sporttrade (VA)", "PPf" to "PrizePicks (flex)", "PPp" to "PrizePicks (power)",
    )

    /** Pinnacle and Circa (sharp books), then the exchanges: listed first. */
    private val sharpOrder = listOf("PN", "CS", "PX", "KI", "ST-NJ", "ST-CO", "ST-IA", "ST-AZ", "ST-VA", "NV")

    const val NOVIG = "NV"

    /** Fewest two-sided books (Novig aside) for Vigilant to call an edge confirmed. */
    const val MIN_TWO_SIDED = 3

    fun name(code: String): String = names[code] ?: code

    /** CNO's column code for a book as its +EV list names it ("Novig" → NV, "ProphetX" → PX). */
    fun codeFor(book: String): String? =
        names.entries.firstOrNull { it.value.equals(book, true) }?.key
            ?: names.entries.firstOrNull { book.startsWith(it.value, true) || it.value.startsWith(book, true) }?.key

    /** Whether a book's prices go into Vigilant's own fair value when [judged] is the book being judged. */
    fun usableForFair(code: String, judged: String = NOVIG): Boolean = code != judged && !code.startsWith("PP")

    /**
     * The bet's row on CNO's game page (by [sideId], else by name) and its other side, with every
     * book's prices. Null when the bet isn't on the page.
     */
    fun parse(gridHtml: String, sideId: String?, bet: String, fetchedAtMs: Long): CnoBooksView? {
        val tableStart = gridHtml.indexOf("<table", ignoreCase = true)
        if (tableStart < 0) return null
        val table = gridHtml.substring(tableStart, gridHtml.indexOf("</table>", tableStart, ignoreCase = true).let { if (it < 0) gridHtml.length else it })
        val headers = Regex("""<th\b[^>]*>(.*?)</th>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(table).map { CnoPage.text(it.groupValues[1]) }.toList()
        val nameCol = headers.indexOfFirst { it.equals("Bet Name", true) }
        val fairCol = headers.indexOfFirst { it.equals("Fair Odds", true) }
        if (nameCol < 0) throw CnoException("CrazyNinjaOdds' game page changed (no Bet Name column), so its books can't be read.")
        val bookCols = headers.indices.filter { it != nameCol && it != fairCol && !headers[it].equals("Best", true) && headers[it].isNotBlank() }

        class Line(val id: String?, val name: String, val cells: List<String>)
        val lines = Regex("""<tr\b([^>]*)>(.*?)</tr>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(table)
            .mapNotNull { tr ->
                val cells = Regex("""<td\b[^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .findAll(tr.groupValues[2]).map { CnoPage.text(it.groupValues[1]) }.toList()
                if (cells.size < headers.size) null else Line(CnoPage.attributes(tr.groupValues[1])["id"], cells[nameCol], cells)
            }
            .toList()
        val at = lines.indexOfFirst { sideId != null && it.id == sideId }.takeIf { it >= 0 }
            ?: lines.indexOfFirst { it.name.equals(bet, ignoreCase = true) }.takeIf { it >= 0 }
            ?: return null
        val line = lines[at]
        // The other side sits next to it; look nearby first, then anywhere on the page.
        val near = listOf(at - 1, at + 1, at - 2, at + 2).filter { it in lines.indices }
        val other = (near + lines.indices.filter { it !in near && it != at })
            .map { lines[it] }
            .firstOrNull { complement(line.name, it.name) }
            ?: lines.takeIf { it.size == 2 }?.get(1 - at)?.takeIf { !hasNumber(line.name) && !hasNumber(it.name) }

        val prices = bookCols.mapNotNull { c ->
            val mine = line.cells[c]
            val theirs = other?.cells?.getOrNull(c).orEmpty()
            val odds = CnoPage.parseOdds(mine)
            val otherOdds = CnoPage.parseOdds(theirs)
            if (odds == null && otherOdds == null) null
            else CnoBookPrice(headers[c], odds, available(mine), otherOdds, available(theirs))
        }.sortedWith(compareBy<CnoBookPrice> { sharpOrder.indexOf(it.code).let { i -> if (i < 0) sharpOrder.size else i } }.thenBy { it.odds == null })
        val fairCell = line.cells.getOrNull(fairCol).orEmpty()
        return CnoBooksView(
            bet = line.name,
            otherBet = other?.name,
            cnoFair = CnoPage.parseOdds(fairCell),
            cnoFairOneWay = CnoPage.WARNING in fairCell,
            prices = prices,
            fetchedAtMs = fetchedAtMs,
        )
    }

    /**
     * Whether two bet names are the two sides of one line: Over/Under the same number, Yes/No of
     * the same thing, or a spread's two teams at the same number with opposite signs.
     */
    fun complement(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return false
        val ou = Regex("""^(.*?)\b(Over|Under)\s+([\d.]+)$""", RegexOption.IGNORE_CASE)
        val x = ou.find(a.trim())
        val y = ou.find(b.trim())
        if (x != null && y != null) {
            return x.groupValues[1].trim().equals(y.groupValues[1].trim(), true) &&
                !x.groupValues[2].equals(y.groupValues[2], true) &&
                x.groupValues[3].toDoubleOrNull() == y.groupValues[3].toDoubleOrNull()
        }
        val yn = Regex("""^(.*?)\s+(Yes|No)$""", RegexOption.IGNORE_CASE)
        val p = yn.find(a.trim())
        val q = yn.find(b.trim())
        if (p != null && q != null) {
            return p.groupValues[1].equals(q.groupValues[1], true) && !p.groupValues[2].equals(q.groupValues[2], true)
        }
        val spread = Regex("""^(.*?)\s+([+\-−])([\d.]+)$""")
        val s = spread.find(a.trim())
        val t = spread.find(b.trim())
        if (s != null && t != null) {
            val signA = if (s.groupValues[2] == "+") 1 else -1
            val signB = if (t.groupValues[2] == "+") 1 else -1
            return signA != signB && s.groupValues[3].toDoubleOrNull() == t.groupValues[3].toDoubleOrNull() &&
                !s.groupValues[1].equals(t.groupValues[1], true)
        }
        return false
    }

    private fun hasNumber(s: String) = Regex("""\d""").containsMatchIn(s)

    private fun available(cell: String): Double? =
        Regex("""\(\$([\d,]+(?:\.\d+)?)\)""").find(cell)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

    /** Vigilant's verdict on a CNO bet, from the books that price both sides. */
    data class Check(
        /** Books (Novig and pick'em apps aside) pricing both sides. */
        val twoSided: Int,
        /** Books pricing only one of the two sides (can't be devigged honestly). */
        val oneSided: Int,
        /** Worst case of mean and median of each two-sided book's worst-case devig. */
        val fairProbability: Double?,
        /** The price judged (Novig's, nearly always): the game page's, else the list's. */
        val novigOdds: Int,
        val ev: Double?,
        val verdict: Verdict,
    )

    enum class Verdict { CONFIRMED, THIN, NOT_CONFIRMED, NO_DATA }

    /** Checks [row] (its book's price, and Novig's taker fee if the game is [live]) against [view]. */
    fun check(view: CnoBooksView, row: CnoRow, live: Boolean = false): Check =
        check(view, row.odds, live, codeFor(row.book) ?: NOVIG)

    /**
     * [listOdds] is the price in CNO's +EV list at book [judged]; [live] adds Novig's taker fee
     * (pregame is free; other books' fees aren't modeled).
     */
    fun check(view: CnoBooksView, listOdds: Int, live: Boolean = false, judged: String = NOVIG): Check {
        val usable = view.prices.filter { usableForFair(it.code, judged) }
        val pairs = usable.filter { it.twoSided }
        val fairs = pairs.mapNotNull { fairFor(it.odds!!, it.otherOdds!!) }
        val fair = if (fairs.isEmpty()) null else minOf(fairs.average(), median(fairs))
        val novig = view.prices.firstOrNull { it.code == judged }?.odds ?: listOdds
        val ev = fair?.let { evAt(it, novig, live && judged == NOVIG) }
        val verdict = when {
            fairs.isEmpty() -> Verdict.NO_DATA
            fairs.size < MIN_TWO_SIDED -> Verdict.THIN
            ev != null && ev > 0 -> Verdict.CONFIRMED
            else -> Verdict.NOT_CONFIRMED
        }
        return Check(fairs.size, usable.count { !it.twoSided }, fair, novig, ev, verdict)
    }

    /** One book's fair probability for the first side: worst-case devig, or plain normalizing when there's no vig to remove. */
    fun fairFor(odds: Int, otherOdds: Int): Double? {
        if (odds == 0 || otherOdds == 0) return null
        val raw = listOf(1.0 / Odds.americanToDecimal(odds), 1.0 / Odds.americanToDecimal(otherOdds))
        val sum = raw.sum()
        return if (sum <= 1.0) raw[0] / sum else Devig.worstCase(raw)[0]
    }

    /** EV of a Novig price against a fair probability, net of the taker fee when [live]. */
    fun evAt(fairProbability: Double, odds: Int, live: Boolean): Double {
        val price = 1.0 / Odds.americanToDecimal(odds)
        val fee = if (live) Fees.takerFee(price, MarketFee.GAME, eventLive = true) else 0.0
        return fairProbability / (price + fee) - 1.0
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
