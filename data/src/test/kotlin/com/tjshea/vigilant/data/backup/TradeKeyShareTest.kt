package com.tjshea.vigilant.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TradeKeyShareTest {
    private val key = TradeKeyShare.SharedTradeKey("trade-key-0001", "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n", "sub-1") // FAKE

    @Test fun `a key survives the hand over and a wrong passphrase gets nothing`() {
        val blob = TradeKeyShare.pack(key, "twelve chars+ passphrase")
        assertEquals(key, TradeKeyShare.unpack(blob, "twelve chars+ passphrase"))
        assertNull(TradeKeyShare.unpack(blob, "some other passphrase!!"))
        assertNull(TradeKeyShare.unpack("junk".toByteArray(), "twelve chars+ passphrase"))
    }

    @Test fun `a file without a usable key is refused and the key never prints`() {
        val empty = TradeKeyShare.pack(key.copy(pem = "not a key"), "twelve chars+ passphrase")
        assertNull(TradeKeyShare.unpack(empty, "twelve chars+ passphrase"))
        assertEquals("SharedTradeKey(••••0001)", key.toString())
    }
}
