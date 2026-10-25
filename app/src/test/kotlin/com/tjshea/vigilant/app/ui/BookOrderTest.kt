package com.tjshea.vigilant.app.ui

import com.tjshea.vigilant.engine.BookFair
import com.tjshea.vigilant.engine.BookPrices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PX11b: the bet sheet's books, best odds for the bet's side first, and the books whose own no-vig odds say the bet is not +EV. */
class BookOrderTest {
    private fun book(key: String, side0: Double, side1: Double, sharp: Boolean = false): BookFair {
        val p0 = 1 / side0; val p1 = 1 / side1
        return BookFair(BookPrices(key, key, listOf(side0, side1)), listOf(p0 / (p0 + p1), p1 / (p0 + p1)), p0 + p1 - 1, sharp)
    }

    private val books = listOf(book("dk", 1.90, 1.95), book("pin", 2.05, 1.83, sharp = true), book("fd", 1.95, 1.90), book("mgm", 2.10, 1.75))

    @Test
    fun `the best odds for the bet's side come first, the worst last`() {
        assertEquals(listOf("mgm", "pin", "fd", "dk"), BookOrder.sorted(books, 0).map { it.book.bookKey })
        assertEquals(listOf("dk", "fd", "pin", "mgm"), BookOrder.sorted(books, 1).map { it.book.bookKey })
    }

    @Test
    fun `a book whose no-vig chance is at or under the bet's cost disagrees`() {
        // cost 0.47 a $1 contract: mgm's no-vig chance for side 0 is 0.455, dk's 0.506
        val mgm = books.single { it.book.bookKey == "mgm" }
        val dk = books.single { it.book.bookKey == "dk" }
        assertTrue(BookOrder.disagrees(mgm, 0, 0.47))
        assertFalse(BookOrder.disagrees(dk, 0, 0.47))
        assertFalse("no side known", BookOrder.disagrees(mgm, null, 0.47))
        assertFalse("no price known", BookOrder.disagrees(mgm, 0, null))
    }

    @Test
    fun `without a side the sharp books lead as before`() {
        assertEquals("pin", BookOrder.sorted(books, null).first().book.bookKey)
    }
}
