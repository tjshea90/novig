package com.tjshea.vigilant.data.novig.signing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * NOVIG-V3 against Novig's own published vectors (docs.novig.com signing-vectors.json, 30 cases
 * covering every divergence point in NOVIG_API.md §3).
 */
class NovigV3Test {

    private val vectors: JsonObject = Json.parseToJsonElement(
        javaClass.getResource("/novig-signing-vectors.json")!!.readText(),
    ).jsonObject

    private val cases get() = vectors["vectors"]!!.let { it as kotlinx.serialization.json.JsonArray }.map { it.jsonObject }

    private fun s(o: JsonObject, k: String) = o[k]!!.jsonPrimitive.content

    @Test
    fun `all 30 published vectors produce Novig's exact string to sign`() {
        assertEquals(30, cases.size)
        for (v in cases) {
            val input = v["input"]!!.jsonObject
            val actual = NovigV3.stringToSign(
                timestampMs = input["timestamp"]!!.jsonPrimitive.long,
                method = s(input, "method"),
                path = s(input, "path"),
                rawQuery = s(input, "query"),
                body = s(input, "body").toByteArray(Charsets.UTF_8),
            )
            assertEquals("vector ${s(v, "id")}", s(v, "string_to_sign"), actual)
        }
    }

    @Test
    fun `Novig's published signatures verify over our strings with the published public keys`() {
        val keypairs = vectors["keypairs"]!!.jsonObject
        for (v in cases) {
            val input = v["input"]!!.jsonObject
            val text = NovigV3.stringToSign(input["timestamp"]!!.jsonPrimitive.long, s(input, "method"), s(input, "path"), s(input, "query"),
                s(input, "body").toByteArray()).toByteArray()
            val sig = Base64.getDecoder().decode(s(v, "signature"))
            val pubPem = s(keypairs[s(v, "keypair_id")]!!.jsonObject, "public_key_spki_pem")
            val ok = when (s(v, "algorithm")) {
                "ed25519" -> Ed25519Signer().run {
                    init(false, PublicKeyFactory.createKey(PemSigningKey.pemToDer(pubPem)) as Ed25519PublicKeyParameters)
                    update(text, 0, text.size)
                    verifySignature(sig)
                }
                else -> Signature.getInstance("SHA256withECDSA").run {
                    initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(PemSigningKey.pemToDer(pubPem))))
                    update(text)
                    verify(sig)
                }
            }
            assertTrue("vector ${s(v, "id")}", ok)
        }
    }

    private fun pem(der: ByteArray) =
        "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END PRIVATE KEY-----\n" // FAKE: runtime-generated test key

    @Test
    fun `an Ed25519 PKCS8 PEM like Novig's download signs verifiable, deterministic signatures`() {
        val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
        val pair = gen.generateKeyPair()
        val key = PemSigningKey("key-1", pem(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded))
        assertEquals(NovigKeyAlgorithm.ED25519, key.algorithm)
        val msg = "NOVIG-V3\n1\nGET\n/v3/echo\n\n${NovigV3.sha256Hex(ByteArray(0))}".toByteArray()
        val sig = key.sign(msg)
        assertEquals(64, sig.size)
        assertArrayEquals(sig, key.sign(msg))
        assertTrue(Ed25519Signer().run { init(false, pair.public); update(msg, 0, msg.size); verifySignature(sig) })
    }

    @Test
    fun `a P-256 key signs DER signatures that standard ECDSA verification accepts`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val key = PemSigningKey("key-2", pem(pair.private.encoded))
        assertEquals(NovigKeyAlgorithm.P256, key.algorithm)
        val msg = "hello".toByteArray()
        val sig = key.sign(msg)
        assertEquals(0x30, sig[0].toInt()) // DER SEQUENCE, not raw r||s
        assertTrue(sig.size in 68..72)
        assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(pair.public); update(msg); verify(sig) })
    }

    @Test
    fun `headers carry key id, unpadded millisecond timestamp and padded base64`() {
        val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
        val key = PemSigningKey("3f2504e0-4f89-11d3-9a0c-0305e82c3301", pem(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded))
        val h = NovigV3.headers(key, 1755000000000, "POST", "/v3/echo", null, """{"a":1}""".toByteArray())
        assertEquals("3f2504e0-4f89-11d3-9a0c-0305e82c3301", h["Novig-Key-Id"])
        assertEquals("1755000000000", h["Novig-Timestamp"])
        assertTrue(h["Novig-Signature"]!!.endsWith("=="))
    }

    @Test
    fun `an RSA key is refused with a clear message`() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val e = assertThrows(IllegalArgumentException::class.java) { PemSigningKey("k", pem(rsa.private.encoded)) }
        assertTrue(e.message!!.contains("Ed25519 or P-256"))
    }
}
