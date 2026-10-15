package com.tjshea.vigilant.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tj, 2026-10-15: the Novig read-only key for the engine is made only by his tap and only with what it needs; without it nothing is made, nothing is sent. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CloudNovigKeyAppTest {
    private val app: VigilantApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `without a GitHub token nothing is made and nothing is sent, and the page says what is missing`() = runBlocking {
        val c = app.container
        val say = c.cloud.sendNovigKey()
        assertEquals("Save the GitHub token first.", say)
        assertEquals(say, c.cloud.ui.value.keysNote)
        assertEquals("Save the GitHub token first.", c.cloud.removeNovigKey())
        assertEquals("no key was made", null, c.cloudNovigKeys.load())
        assertTrue(c.cloud.ui.value.novigKeyMadeAtMs == 0L)
    }
}
