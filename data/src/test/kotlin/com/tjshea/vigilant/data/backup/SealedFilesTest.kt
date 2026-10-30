package com.tjshea.vigilant.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SealedFilesTest {
    private val mgmt = """{"version":1,"key":{"keyId":"k1","sealedPem":"SEALED-A","savedAtMs":5}}"""

    @Test fun `opening and sealing swap only the secret`() {
        val opened = SealedFiles.open("novig_management_key.json", mgmt) { "PLAIN(" + it + ")" }!!
        assertTrue(opened.contains("PLAIN(SEALED-A)")); assertTrue(opened.contains("\"keyId\":\"k1\"")); assertTrue(opened.contains("\"savedAtMs\":5"))
        val back = SealedFiles.seal("novig_management_key.json", opened) { it.removePrefix("PLAIN(").removeSuffix(")") }!!
        assertTrue(back.contains("\"sealedPem\":\"SEALED-A\""))
    }

    @Test fun `flat and top level fields work`() {
        assertEquals("""{"keyId":"id","pem":"P"}""", SealedFiles.open("kalshi_key.json", """{"keyId":"id","pem":"S"}""") { "P" })
        assertEquals("""{"sealed":"P","tail":"abcd"}""", SealedFiles.open("cloud_token.json", """{"sealed":"S","tail":"abcd"}""") { "P" })
    }

    @Test fun `no saved secret means nothing to open`() {
        assertNull(SealedFiles.open("cloud_token.json", """{"sealed":"","tail":""}""") { "P" })
        assertNull(SealedFiles.open("novig_management_key.json", """{"version":1,"key":null}""") { "P" })
        assertFalse(SealedFiles.holdsSecret("kalshi_key.json", """{"keyId":"","pem":""}"""))
        assertTrue(SealedFiles.holdsSecret("kalshi_key.json", """{"keyId":"a","pem":"x"}"""))
    }

    @Test fun `an unreadable seal is reported not invented`() {
        assertNull(SealedFiles.open("kalshi_key.json", """{"keyId":"id","pem":"S"}""") { error("not this phone's seal") })
    }

    @Test fun `unknown files are not touched and every sealing file in the app is in the table`() {
        assertNull(SealedFiles.open("settings.json", "{}") { "P" })
        // A new file that seals a secret must be listed: find every *.json name given to a store that is built with a SecretBox or sealSecret.
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val sources = root.walkTopDown().filter { it.extension == "kt" && "/src/main/" in it.path }.joinToString("\n") { it.readText() }
        for (name in listOf("MANAGEMENT_KEY_FILE", "CLOUD_NOVIG_KEY_FILE", "CLOUD_TRADE_KEY_FILE")) {
            val file = Regex("""const val $name = "([^"]+)"""").find(sources)!!.groupValues[1]
            assertTrue("$file missing from SealedFiles.FIELDS", SealedFiles.FIELDS.containsKey(file))
        }
        assertTrue(SealedFiles.FIELDS.containsKey("kalshi_key.json") && sources.contains("\"kalshi_key.json\""))
        assertTrue(SealedFiles.FIELDS.containsKey("cloud_token.json") && sources.contains("\"cloud_token.json\""))
    }
}
