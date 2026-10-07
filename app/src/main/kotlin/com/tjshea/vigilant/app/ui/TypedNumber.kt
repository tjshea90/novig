package com.tjshea.vigilant.app.ui

import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.tjshea.vigilant.engine.Odds
import java.util.Locale

/**
 * A number typed in beside a setting's chips (Tj, 2026-10-07: "anywhere there are settings for minimum/maximum EV, odds, times, or basically any number inputs that have
 * options, also put a box where I can manually type in a number to set. The more customizable the better."). What a typed number is: its words, range and how it reads. Pure,
 * so the tests check every setting's rule without a screen ([NumberSpecs] holds them all).
 */
data class NumberSpec(
    /** The box's label: "Or type your own (hours)". */
    val label: String,
    val min: Double,
    val max: Double,
    /** Decimal places typed (0 = whole numbers only). */
    val decimals: Int = 0,
    /** Whether a leading minus is typed (shortest odds: −200). */
    val negative: Boolean = false,
    /** Extra rule beyond the range (odds: not −99..+99). */
    val accept: (Double) -> Boolean = { true },
    /** Said under the box when what's typed isn't taken. */
    val error: String,
) {
    /** The number [text] is, or null when it isn't one this setting takes. A comma or a typographic minus reads as the plain one. */
    fun parse(text: String): Double? {
        val t = text.trim().replace(',', '.').replace('−', '-')
        if (t.isEmpty() || t == "-" || t == ".") return null
        val v = t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        if (v < 0 && !negative) return null
        if (decimals == 0 && v != Math.rint(v)) return null
        if (decimals > 0 && t.substringAfter('.', "").length > decimals) return null
        return v.takeIf { it in min..max && accept(it) }
    }

    /** [v] as typed back into the box: no trailing zeros ("2.5", "3", "-200"). */
    fun show(v: Double): String = if (v == Math.rint(v) && kotlin.math.abs(v) < 1e9) v.toLong().toString() else String.format(Locale.US, "%.${decimals.coerceAtLeast(1)}f", v).trimEnd('0').trimEnd('.')

    /** The characters the box lets through. */
    fun filter(text: String): String = text.filter { it.isDigit() || it == '.' || it == ',' || (negative && (it == '-' || it == '−')) }.take(10)
}

/** Every typed number's rule, in one place (the tests walk them). Percent specs are typed as percents (2.5), not fractions. */
object NumberSpecs {
    private fun trim(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

    fun percent(what: String, min: Double, max: Double, decimals: Int = 2) =
        NumberSpec("Or type your own ($what, %)", min, max, decimals, error = "A percent from ${trim(min)} to ${trim(max)}")

    fun count(what: String, min: Int, max: Int) = NumberSpec("Or type your own ($what)", min.toDouble(), max.toDouble(), error = "A whole number from $min to $max")

    fun time(unit: String, min: Int, max: Int) = NumberSpec("Or type your own ($unit)", min.toDouble(), max.toDouble(), error = "A whole number of $unit from $min to $max")

    fun dollars(what: String, min: Double = 0.01, max: Double = 100_000.0) =
        NumberSpec("Or type your own ($what, $)", min, max, decimals = 2, error = "An amount from $${trim(min)} to $${trim(max)}")

    /** American odds, longest: +100 (even money) or longer, as the auto-bet's limit. */
    val LONGEST_ODDS = NumberSpec(
        "Or type your own longest odds (+)", 100.0, 100_000.0, error = "+100 (even money) or more; pick No limit for none",
    )

    /** American odds, shortest: −100 or shorter (−250), or +100 or longer (underdogs only). */
    val SHORTEST_ODDS = NumberSpec(
        "Or type your own shortest odds (−250, or +110 for underdogs only)", -100_000.0, 100_000.0, negative = true,
        accept = { it <= -100.0 || it >= 100.0 }, error = "−100 or shorter (−250), or +100 or longer; pick No limit for none",
    )

    /** A Kelly fraction typed as a decimal: 0.25 is quarter Kelly. */
    val KELLY = NumberSpec("Or type your own (Kelly fraction, 0.25 = ¼ Kelly)", 0.01, 1.0, decimals = 3, error = "A fraction from 0.01 to 1")
}

/**
 * A box under a setting's chips: it always shows the saved value ([shown], blank when the chips hold a value with no number, like "No limit"), so a chip and the box never disagree;
 * a typed value saves as soon as it is one [spec] takes, anything else saves nothing and says why. [tag] is the box's test tag.
 */
@Composable
fun TypedNumberField(spec: NumberSpec, shown: String, tag: String, onSet: (Double) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(shown) { mutableStateOf(shown) }
    val bad = text.isNotEmpty() && spec.parse(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = spec.filter(t)
            spec.parse(text)?.let(onSet)
        },
        label = { Text(spec.label) },
        isError = bad,
        supportingText = { if (bad) Text(spec.error) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (spec.negative) KeyboardType.Number else if (spec.decimals > 0) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** A percent setting stored as a fraction (0.025 = 2.5%). */
@Composable
fun TypedPercentField(spec: NumberSpec, value: Double?, tag: String, onSet: (Double) -> Unit) =
    TypedNumberField(spec, value?.takeIf { it > 0.0 }?.let { spec.show(it * 100.0) }.orEmpty(), tag) { v -> onSet(Math.round(v * 1000.0) / 100_000.0) }

/** A whole-number setting where [none] (0 or NO_LIMIT) is a chip of its own and shows blank. */
@Composable
fun TypedIntField(spec: NumberSpec, value: Int, tag: String, none: (Int) -> Boolean = { it <= 0 }, onSet: (Int) -> Unit) =
    TypedNumberField(spec, if (none(value)) "" else value.toString(), tag) { v -> onSet(v.toInt()) }

/** A dollar setting. */
@Composable
fun TypedDollarField(spec: NumberSpec, value: Double, tag: String, onSet: (Double) -> Unit) =
    TypedNumberField(spec, if (value > 0.0) spec.show(value) else "", tag, onSet)

/** The American odds [limit] as the box shows it (blank = no limit). */
fun oddsShown(limit: Int): String = if (limit == 0) "" else limit.toString()

/** Whether American [odds] are a number the odds boxes take (not −99..+99, not 0): for the tests. */
fun isAmericanOdds(odds: Int): Boolean = odds <= -100 || odds >= 100

/** The price (cost of a $1 payout) of American [odds], for the notes under the boxes. */
fun priceOfOdds(odds: Int): Double = 1.0 / Odds.americanToDecimal(odds)
