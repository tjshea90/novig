package com.tjshea.vigilant.data.match

/** A bet's name read apart: who it's on, and which side and line ("Under 69.5", "-33.5", "Yes"). */
object Picks {

    private val PICK_LINE = Regex("""^(.*?)\s*((?:Over|Under)\s+[\d.]+|[+\-−][\d.]+|Yes|No)$""", RegexOption.IGNORE_CASE)

    /**
     * "Justin Jefferson Under 69.5" → "Justin Jefferson" + "Under 69.5"; "Ohio -33.5" → "Ohio" +
     * "-33.5"; "Under 8.5" → "" + "Under 8.5". Picks without a line ("Dallas Cowboys") come back
     * whole, with no line.
     */
    fun split(title: String): Pair<String, String?> {
        val m = PICK_LINE.find(title.trim()) ?: return title to null
        return m.groupValues[1].trim() to m.groupValues[2]
    }

    /**
     * The same bet at any line: who, and which way ("over", "under", "+", "-", "yes", "no"),
     * lowercased. "Dalton Schultz Over 5.5" and "Dalton Schultz Over 4.5" share it; the Under
     * doesn't. A pick without a line is its own family.
     */
    fun family(title: String): String {
        val (who, line) = split(title)
        val way = line?.trim()?.let { l ->
            when {
                l.startsWith("over", true) -> "over"
                l.startsWith("under", true) -> "under"
                l.startsWith("+") -> "+"
                l.startsWith("-") || l.startsWith("−") -> "-"
                else -> l.lowercase()
            }
        }
        return listOfNotNull(who.trim().lowercase(), way).joinToString(" ")
    }

    /** The side and line in short form ("O5.5", "U69.5", "-3.5", "Yes"), for a small tag. */
    fun shortLine(title: String): String {
        val line = split(title).second ?: return title
        return line.replace(Regex("^over\\s+", RegexOption.IGNORE_CASE), "O").replace(Regex("^under\\s+", RegexOption.IGNORE_CASE), "U")
    }
}
