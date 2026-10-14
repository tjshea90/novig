package com.tjshea.vigilant.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudMainTest {
    @Test
    fun `git output never carries the token`() {
        val g = Git("secret-token-1234")
        // a command that fails and echoes its argument back in the error text
        val (code, text) = g.exec(java.io.File("."), listOf("remote", "show", "https://x-access-token:secret-token-1234@example.invalid/none.git"))
        assertEquals(false, text.contains("secret-token-1234"))
        assertEquals(true, code != 0)
    }
}
