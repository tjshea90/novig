package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.ApiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeysRestoreTest {
    // Sealed by tools/keys_export.py (the file the workflow writes) with the passphrase below: FAKE test data.
    private val blob = """{"v": 1, "kdf": "pbkdf2-sha256", "iter": 600000, "salt": "B9hACiXvFYlU2cQHYf/+wA==", "iv": "IlYourl7UIOCD0QX", "ct": "PMS5ywQDwkIpz7xDtr8sv3stq5cEAkqlqhVkO7BTBagrWy8h1ZVapU86bdRuyWyQxk2CF11yp2bS7GGo5mbZKNheuJvv3MxOlUYw/L40OFwpOmpyVUzt82QVo9E2ZnxeQ3IC9xa0deA2mJuRhptMnEcoitXcpmWJ+7QTWQ9GNhKau04/8nppUNs2l9QhfIfKwh+yLxdA1/sEQrKFRzKHL/GqMD+RktLsnOJjvfE="}"""

    @Test fun pythonSealedFileOpensAndMapsToProviders() {
        val s = KeysRestore.decrypt(blob, "correct horse battery")!!
        val keys = KeysRestore.providerKeys(s)
        assertEquals(listOf("k1", "k2"), keys[ApiProvider.PARLAY])
        assertEquals(listOf("sgokey"), keys[ApiProvider.SPORTSGAMEODDS])
        assertEquals("kid", KeysRestore.kalshi(s)!!.first)
    }

    @Test fun wrongPassphraseOrDamageGivesNothing() {
        assertNull(KeysRestore.decrypt(blob, "wrong wrong wrong wrong"))
        assertNull(KeysRestore.decrypt("{}", "correct horse battery"))
    }
}
