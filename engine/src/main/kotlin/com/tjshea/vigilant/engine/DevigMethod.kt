package com.tjshea.vigilant.engine

/**
 * Which margin-removal method to use when turning a book's quoted (vig-inflated) odds into
 * fair probabilities. See RESEARCH.md §5 for the formulas and citations, and its own caveat:
 * these are re-derived from a secondary source, not a primary academic paper — sanity-checked
 * by the unit tests in DevigTest (sums to 1, reduces correctly at zero margin, etc.) but worth
 * re-verifying against a primary source before leaning on them for real bet sizing.
 *
 * Deliberately not hard-coded to one method (RESEARCH.md §5's own recommendation): OddsJam and
 * Sharp Lines both let the user pick, since methods disagree meaningfully on favorite-heavy
 * lines, and hiding that choice is exactly the transparency gap RESEARCH.md §8.1 flagged in
 * Odds Assist Pro.
 */
enum class DevigMethod {
    MULTIPLICATIVE,
    ADDITIVE,
    POWER,
    SHIN,
}
