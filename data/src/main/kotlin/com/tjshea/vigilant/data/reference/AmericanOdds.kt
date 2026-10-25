package com.tjshea.vigilant.data.reference

/** An American price as decimal odds: +150 -> 2.5, -200 -> 1.5 (BoltOdds' integer prices). */
internal fun americanToDecimal(price: Int): Double = if (price > 0) 1.0 + price / 100.0 else 1.0 + 100.0 / -price
