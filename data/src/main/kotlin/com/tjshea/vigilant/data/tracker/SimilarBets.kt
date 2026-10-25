package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.match.Picks

/**
 * Which bets are "the same bet" for the repeat guard's similar-bets cap (Tj, 2026-10-25: "a game over a total of 49.5 and the same game over a total of 50.5 ... these are very similar bets and I don't
 * want too much staked on identical or very similar bets"). Pure: [subject] reads a bet's market label and selection, in whichever form its source writes them, and names what it really rides on:
 *  - **the game's total**, either way, at any line and any period ("Over 49.5" under "Total", "1H Total", "Total Points" or Novig's own "SYR @ UNC t52.5"): `total|over`;
 *  - **a team's total** at any line and period ("Miami Heat 115.5 TEAM_TOTAL" + "Under 115.5", "Team Total" + "Miami Heat Under 115.5"): `tt:<away|home>|under`;
 *  - **a team's side**: its spread at any line or sign, its moneyline, any period ("TNTC -17.5", "Tennessee Tech -17.5", "ML BOS yes", "Calgary Flames"): `side:<away|home>|win`;
 *  - **a player's over (or under)**, any stat and line ("Joel Embiid 23.5 POINTS" + "Under 23.5", "Points" + "Joel Embiid Under 23.5"): `p:<name>|under`.
 * A team written as Novig's short code ("CIT", "OKST", "UNM") is matched to the game's away or home team ([role]); a name that matches neither is kept as written, so it still groups with itself.
 * A bet it cannot read falls back to its selection without the line, within its market label without numbers. Null: nothing to go by (no selection).
 */
object SimilarBets {

    private val OVER_UNDER = Regex("""\b(over|under)\b""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""[+\-−]?\d+(?:\.\d+)?""")
    /** Novig's own name of a player or team line: "Joel Embiid 23.5 POINTS", "Miami Heat 115.5 TEAM_TOTAL". */
    private val NOVIG_NAMED = Regex("""^(.+?)\s+\d+(?:\.\d+)?\s+([A-Z][A-Z_]*)(?:\s.*)?$""")
    /** Novig's own name of a game total: "SYR @ UNC t52.5", "HOU @ ATL t110.5 1H". */
    private val NOVIG_GAME_TOTAL = Regex("""@.*\bt\d""")
    private val MONEYLINE = Regex("""^ml\s+(.+?)\s+(yes|no)$""", RegexOption.IGNORE_CASE)
    private val FILLER = setOf("the", "of", "university", "univ", "at")

    fun subject(eventName: String, market: String, selection: String): String? {
        val sel = selection.trim()
        if (sel.isBlank()) return null
        val mkt = market.trim()
        val teams = Picks.sides(eventName)
        val ou = OVER_UNDER.find(sel) ?: if (NUMBER.find(sel) != null && sel.contains("total", true)) null else null
        if (ou != null) {
            val way = ou.value.lowercase()
            var name = clean(sel.replace(OVER_UNDER, " ").replace(NUMBER, " ").replace(Regex("""\btotal\b""", RegexOption.IGNORE_CASE), " "))
            val novigNamed = NOVIG_NAMED.matchEntire(mkt)
            if (name.isBlank() && novigNamed != null && !NOVIG_GAME_TOTAL.containsMatchIn(mkt)) name = clean(novigNamed.groupValues[1])
            val teamTotal = mkt.contains("team total", true) || mkt.contains("team_total", true)
            if (name.isBlank()) {
                // No one named: the game's total, unless the label is a stat with nobody in it (then only the same label is similar).
                return if (teamTotal || isTotal(mkt)) "total|$way" else "x:${plain(mkt)}|$way"
            }
            val r = role(name, teams)
            if (teamTotal) return "tt:${r ?: name}|$way"
            // A name that is plainly one of the game's teams (its full name) is that team's total, whatever the label says.
            if (r != null && strong(name, teams[r])) return "tt:$r|$way"
            return "p:$name|$way"
        }
        val ml = MONEYLINE.matchEntire(sel)
        if (ml != null) {
            val name = clean(ml.groupValues[1])
            val r = role(name, teams) ?: return "side:$name|${ml.groupValues[2].lowercase()}"
            return "side:${if (ml.groupValues[2].equals("no", true)) 1 - r else r}|win"
        }
        val (who, line) = Picks.split(sel)
        val name = clean(who.ifBlank { sel }.replace(NUMBER, " "))
        if (name == "draw" || name == "tie") return "draw"
        if (line != null && (line.equals("yes", true) || line.equals("no", true))) return "x:${plain(mkt)}|${clean(sel.replace(NUMBER, " "))}"
        if (name.isBlank()) return "x:${plain(mkt)}|${plain(sel)}"
        val r = role(name, teams)
        if (r != null) return "side:$r|win"
        return if (teams.size == 2) "side:$name|win" else "x:${plain(mkt)}|${Picks.family(sel)}"
    }

    /** "Total", "1H Total", "Total Points", "F5 Total", "SYR @ UNC t52.5": the game's total (a team's total says so and is read before this). */
    private fun isTotal(market: String): Boolean = market.isBlank() || market.contains("total", true) || NOVIG_GAME_TOTAL.containsMatchIn(market)

    /**
     * Which of the game's two teams ([teams]: away, home) [name] is: 0, 1, or null when it is neither or both. A full name or a part of one ("Memphis") wins outright; a short code counts two signs:
     * the team's initials appear in it in order ("UNC" holds N, C of North Carolina; "NDSU" holds N, D, S) and it is spelled from the team's own letters starting with the team's first letter
     * ("OKST" in OKlahoma STate, "ARIZ" in ARIZona).
     */
    fun role(name: String, teams: List<String>): Int? {
        if (teams.size != 2 || name.isBlank()) return null
        val n = clean(name)
        val scores = teams.map { score(n, clean(it)) }
        val best = scores.max()
        if (best <= 0 || scores[0] == scores[1]) return null
        return scores.indexOf(best)
    }

    private fun strong(name: String, team: String): Boolean = score(clean(name), clean(team)) >= FULL

    private fun score(n: String, t: String): Int {
        if (n.isBlank() || t.isBlank()) return 0
        if (n == t) return FULL + 2
        if (" $t ".contains(" $n ") || " $n ".contains(" $t ")) return FULL
        val code = n.replace(" ", "")
        if (n.contains(' ') || code.length > 8) return 0
        val words = t.split(' ').filter { it.isNotBlank() && it !in FILLER }
        if (words.isEmpty()) return 0
        val compact = words.joinToString("")
        var s = 0
        if (inOrder(words.joinToString("") { it.take(1) }, code)) s++
        if (code[0] == compact[0] && inOrder(code, compact)) s++
        return s
    }

    /** Every letter of [small] appears in [big] in the same order. */
    private fun inOrder(small: String, big: String): Boolean {
        var i = 0
        for (c in big) if (i < small.length && c == small[i]) i++
        return i == small.length
    }

    /** Lower case, letters and digits only, single spaces ("Ollie Gordon II" -> "ollie gordon ii", "St. Louis Blues" -> "st louis blues"). */
    fun clean(s: String): String = s.lowercase().replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun plain(s: String): String = clean(s.replace(NUMBER, " "))

    private const val FULL = 10
}
