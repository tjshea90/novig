package com.tjshea.vigilant.engine

/**
 * Which margin-removal method turns a book's quoted (vig-inflated) odds into fair
 * probabilities. See RESEARCH.md §5 for the formulas.
 *
 * Never hard-code one method (BRIEF.md, locked decision). OddsJam lets the user pick, and the
 * methods disagree most on favorite/longshot lines, which is exactly where edges get overstated
 * (RESEARCH.md §8.1).
 */
enum class DevigMethod(val displayName: String, val blurb: String) {
    MULTIPLICATIVE("Multiplicative", "Spreads the vig in proportion to each side's price. Simple, but overrates longshots."),
    ADDITIVE("Additive", "Takes the same amount of vig off every side."),
    POWER("Power", "Raises every side to one power so they sum to 100%. Handles the favorite-longshot bias well."),
    SHIN("Shin", "Models the vig as protection against informed bettors. Strong on lopsided lines."),
    WORST_CASE("Worst case", "Uses the least favorable fair price of all four methods. The most conservative option."),
}
