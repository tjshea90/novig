package com.tjshea.vigilant.data.cloud

import org.bouncycastle.math.ec.rfc7748.X25519
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/** Pinned to the NaCl test vectors (the box test of the NaCl distribution), so a seal GitHub opens is not a matter of hope. */
class SealedBoxTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private val aliceSk = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
    private val bobPk = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")

    @Test
    fun `the shared key is NaCl's first key`() {
        assertEquals("1b27556473e985d462cd51197a9a46c76009549eac6474f206c4ee0844f68389", SealedBox.beforenm(aliceSk, bobPk).hex())
    }

    @Test
    fun `the secretbox is NaCl's box test ciphertext`() {
        val nonce = hex("69696ee955b62b73cd62bda875fc73d68219e0036b7a0b37")
        val m = hex(
            "be075fc53c81f2d5cf141316ebeb0c7b5228c52a4c62cbd44b66849b64244ffce5ecbaaf33bd751a1ac728d45e6c61296cdc3c01233561f41db66cce314adb310e3be8250c46f06dceea3a7fa1348057e2f6556ad6b1318a024a838f21af1fde048977eb48f59ffd4924ca1c60902e52f0a089bc76897040e082f937763848645e0705",
        )
        val expected = "f3ffc7703f9400e52a7dfb4b3d3305d98e993b9f48681273c29650ba32fc76ce48332ea7164d96a4476fb8c531a1186ac0dfc17c98dce87b4da7f011ec48c97271d2c20f9b928fe2270d6fb863d51738b48eeee314a7cc8ab932164548e526ae90224368517acfeabd6bb3732bc0e9da99832b61ca01b6de56244a9e88d5f9b37973f622a43d14a6599b1f654cb45a74e355a5"
        assertEquals(expected, SealedBox.box(m, nonce, bobPk, aliceSk).hex())
    }

    @Test
    fun `a seal opens with the recipient's secret key and not with another`() {
        val rnd = SecureRandom()
        val sk = ByteArray(32).also { rnd.nextBytes(it) }
        val pk = ByteArray(32).also { X25519.scalarMultBase(sk, 0, it, 0) }
        val msg = "key-one,key-two".toByteArray()
        val sealed = SealedBox.seal(msg, pk, rnd)
        assertEquals(32 + 16 + msg.size, sealed.size)
        assertArrayEquals(msg, open(sealed, pk, sk))
        val other = ByteArray(32).also { rnd.nextBytes(it) }
        assertTrue(runCatching { open(sealed, pk, other) }.isFailure)
        // two seals of the same message differ (a fresh ephemeral key each time)
        assertTrue(!sealed.contentEquals(SealedBox.seal(msg, pk, rnd)))
    }

    /** crypto_box_seal_open, written independently of [SealedBox.seal]'s secretbox: decrypt with the same primitives run backwards, and check the tag. */
    private fun open(sealed: ByteArray, pk: ByteArray, sk: ByteArray): ByteArray {
        val epk = sealed.copyOfRange(0, 32)
        val box = sealed.copyOfRange(32, sealed.size)
        val key = SealedBox.beforenm(sk, epk)
        val nonce = SealedBox.sealNonce(epk, pk)
        val engine = org.bouncycastle.crypto.engines.XSalsa20Engine()
        engine.init(false, org.bouncycastle.crypto.params.ParametersWithIV(org.bouncycastle.crypto.params.KeyParameter(key), nonce))
        val polyKey = ByteArray(32).also { engine.processBytes(ByteArray(32), 0, 32, it, 0) }
        val ct = box.copyOfRange(16, box.size)
        org.bouncycastle.crypto.generators.Poly1305KeyGenerator.clamp(polyKey)
        val mac = org.bouncycastle.crypto.macs.Poly1305()
        mac.init(org.bouncycastle.crypto.params.KeyParameter(polyKey))
        mac.update(ct, 0, ct.size)
        val tag = ByteArray(16).also { mac.doFinal(it, 0) }
        require(tag.contentEquals(box.copyOfRange(0, 16))) { "tag mismatch" }
        return ByteArray(ct.size).also { engine.processBytes(ct, 0, ct.size, it, 0) }
    }
}
