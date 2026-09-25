package com.tjshea.vigilant.data.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One player, written the way each book writes him (Tj's request, 2026-09-25 ~16:15Z). */
class PlayerNamesTest {

    @Test
    fun `the same player across the books' naming styles`() {
        assertTrue(PlayerNames.same("Lamar Jackson", "Jackson, Lamar"))
        assertTrue(PlayerNames.same("Kenneth Walker III", "Walker III, Kenneth"))
        assertTrue(PlayerNames.same("Kenneth Walker, III", "Kenneth Walker"))
        assertTrue(PlayerNames.same("CeeDee Lamb", "Ceedee Lamb"))
        assertTrue(PlayerNames.same("Kiké Hernández", "Kike Hernandez"))
        assertTrue(PlayerNames.same("Michael A. Taylor", "Michael Taylor"))
        assertTrue(PlayerNames.same("Amon-Ra St. Brown", "Amon-Ra St.Brown"))
        assertTrue(PlayerNames.same("Jaxon Smith-Njigba", "Jaxon Smith Njigba"))
        assertTrue(PlayerNames.same("D.J. Moore", "DJ Moore"))
        assertTrue(PlayerNames.same("A.J. Brown", "AJ Brown"))
    }

    @Test
    fun `short forms and nicknames of the same first name`() {
        assertTrue(PlayerNames.same("Gabe Davis", "Gabriel Davis"))
        assertTrue(PlayerNames.same("Mike Evans", "Michael Evans"))
        assertTrue(PlayerNames.same("Matt Stafford", "Matthew Stafford"))
        assertTrue(PlayerNames.same("Hollywood Brown", "Marquise Brown"))
        assertTrue(PlayerNames.same("Zach Neto", "Zachary Neto"))
        assertTrue(PlayerNames.same("Jazz Chisholm Jr.", "Jasson Chisholm"))
        assertTrue(PlayerNames.same("Cam Ward", "Cameron Ward"))
    }

    @Test
    fun `different players are never merged`() {
        assertFalse(PlayerNames.same("Josh Allen", "Kyle Allen"))
        assertFalse(PlayerNames.same("Marquise Brown", "Antonio Brown"))
        assertFalse(PlayerNames.same("A.J. Brown", "Amon-Ra St. Brown"))
        assertFalse(PlayerNames.same("Mike Williams", "Tyler Williams"))
        assertFalse(PlayerNames.same("Nick Bosa", "Joey Bosa"))
        // A surname alone, or a first initial alone, is not enough.
        assertFalse(PlayerNames.same("J. Allen", "Josh Allen"))
        assertFalse(PlayerNames.same("Allen", "Josh Allen"))
        assertFalse(PlayerNames.same(null, "Josh Allen"))
    }

    @Test
    fun `keys are normalized once and reused`() {
        assertEquals("lamar jackson", PlayerNames.key("Jackson, Lamar"))
        assertEquals("cj donaldson", PlayerNames.key("C.J. Donaldson Jr."))
        assertEquals(PlayerNames.key("Jackson, Lamar"), PlayerNames.key("Jackson, Lamar"))
    }
}
