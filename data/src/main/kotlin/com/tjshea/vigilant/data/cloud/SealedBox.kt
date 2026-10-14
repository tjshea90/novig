package com.tjshea.vigilant.data.cloud

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.generators.Poly1305KeyGenerator
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.SecureRandom

/**
 * libsodium's `crypto_box_seal`, which is what GitHub requires to store an Actions secret through its API (docs: "Encrypting secrets for the REST API": the value is sealed to the repository's
 * public key; nothing but GitHub's private key can open it, so the phone can write a secret and never read one back). Written with BouncyCastle's primitives (already a dependency) so no native
 * library is added to the app.
 *
 * Construction: an ephemeral X25519 pair; the shared key is HSalsa20 of the X25519 product (`crypto_box_beforenm`); the nonce is BLAKE2b-192 of `ephemeralPublic || recipientPublic`; the message
 * is sealed with XSalsa20-Poly1305 (`crypto_secretbox`); the result is `ephemeralPublic || tag(16) || ciphertext`. [SealedBoxTest] pins the key derivation and the secretbox to the NaCl test vectors
 * and opens a seal with an independent decryptor.
 */
object SealedBox {
    const val KEY_BYTES = 32

    /** [message] sealed to [recipientPublic] (32 bytes). */
    fun seal(message: ByteArray, recipientPublic: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(recipientPublic.size == KEY_BYTES) { "a public key is 32 bytes" }
        val esk = ByteArray(KEY_BYTES).also { random.nextBytes(it) }
        val epk = ByteArray(KEY_BYTES).also { X25519.scalarMultBase(esk, 0, it, 0) }
        val nonce = sealNonce(epk, recipientPublic)
        val box = box(message, nonce, recipientPublic, esk)
        return epk + box
    }

    internal fun sealNonce(epk: ByteArray, recipientPublic: ByteArray): ByteArray {
        val d = Blake2bDigest(24 * 8)
        d.update(epk, 0, epk.size)
        d.update(recipientPublic, 0, recipientPublic.size)
        return ByteArray(24).also { d.doFinal(it, 0) }
    }

    /** `crypto_box`: the tag (16 bytes) then the ciphertext, for a message from the holder of [secret] to [publicKey]. */
    internal fun box(message: ByteArray, nonce: ByteArray, publicKey: ByteArray, secret: ByteArray): ByteArray = secretbox(message, nonce, beforenm(secret, publicKey))

    /** `crypto_box_beforenm`: HSalsa20 over the X25519 shared point with a zero input. */
    internal fun beforenm(secret: ByteArray, publicKey: ByteArray): ByteArray {
        val shared = ByteArray(32)
        X25519.scalarMult(secret, 0, publicKey, 0, shared, 0)
        return hsalsa20(shared, ByteArray(16))
    }

    /** `crypto_secretbox_xsalsa20poly1305`: tag(16) || ciphertext. The first 32 bytes of the keystream are the Poly1305 key; the message is encrypted with the stream after them. */
    internal fun secretbox(message: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val polyKey = ByteArray(32).also { engine.processBytes(ByteArray(32), 0, 32, it, 0) }
        val ct = ByteArray(message.size).also { engine.processBytes(message, 0, message.size, it, 0) }
        Poly1305KeyGenerator.clamp(polyKey)
        val mac = Poly1305()
        mac.init(KeyParameter(polyKey))
        mac.update(ct, 0, ct.size)
        val tag = ByteArray(16).also { mac.doFinal(it, 0) }
        return tag + ct
    }

    /** HSalsa20 (Bernstein's "Extending the Salsa20 nonce"): 20 rounds of the Salsa20 core over key and a 16-byte input, no final addition; the output is words 0, 5, 10, 15, 6, 7, 8, 9. */
    internal fun hsalsa20(key: ByteArray, input: ByteArray): ByteArray {
        fun le(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        val x = IntArray(16)
        x[0] = 0x61707865; x[5] = 0x3320646e; x[10] = 0x79622d32; x[15] = 0x6b206574
        for (i in 0 until 4) { x[1 + i] = le(key, 4 * i); x[11 + i] = le(key, 16 + 4 * i); x[6 + i] = le(input, 4 * i) }
        fun r(v: Int, n: Int) = (v shl n) or (v ushr (32 - n))
        repeat(10) {
            x[4] = x[4] xor r(x[0] + x[12], 7); x[8] = x[8] xor r(x[4] + x[0], 9); x[12] = x[12] xor r(x[8] + x[4], 13); x[0] = x[0] xor r(x[12] + x[8], 18)
            x[9] = x[9] xor r(x[5] + x[1], 7); x[13] = x[13] xor r(x[9] + x[5], 9); x[1] = x[1] xor r(x[13] + x[9], 13); x[5] = x[5] xor r(x[1] + x[13], 18)
            x[14] = x[14] xor r(x[10] + x[6], 7); x[2] = x[2] xor r(x[14] + x[10], 9); x[6] = x[6] xor r(x[2] + x[14], 13); x[10] = x[10] xor r(x[6] + x[2], 18)
            x[3] = x[3] xor r(x[15] + x[11], 7); x[7] = x[7] xor r(x[3] + x[15], 9); x[11] = x[11] xor r(x[7] + x[3], 13); x[15] = x[15] xor r(x[11] + x[7], 18)
            x[1] = x[1] xor r(x[0] + x[3], 7); x[2] = x[2] xor r(x[1] + x[0], 9); x[3] = x[3] xor r(x[2] + x[1], 13); x[0] = x[0] xor r(x[3] + x[2], 18)
            x[6] = x[6] xor r(x[5] + x[4], 7); x[7] = x[7] xor r(x[6] + x[5], 9); x[4] = x[4] xor r(x[7] + x[6], 13); x[5] = x[5] xor r(x[4] + x[7], 18)
            x[11] = x[11] xor r(x[10] + x[9], 7); x[8] = x[8] xor r(x[11] + x[10], 9); x[9] = x[9] xor r(x[8] + x[11], 13); x[10] = x[10] xor r(x[9] + x[8], 18)
            x[12] = x[12] xor r(x[15] + x[14], 7); x[13] = x[13] xor r(x[12] + x[15], 9); x[14] = x[14] xor r(x[13] + x[12], 13); x[15] = x[15] xor r(x[14] + x[13], 18)
        }
        val out = ByteArray(32)
        val words = intArrayOf(x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9])
        for (i in 0 until 8) for (j in 0 until 4) out[4 * i + j] = (words[i] ushr (8 * j)).toByte()
        return out
    }
}
