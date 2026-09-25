package com.tjshea.vigilant.data.novig.signing

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.crypto.util.PrivateKeyFactory
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/** The two algorithms Novig accepts. [apiName] is the `algorithm` field value in key-creation bodies. */
enum class NovigKeyAlgorithm(val apiName: String) { ED25519("Ed25519"), P256("P-256") }

/**
 * Anything that can sign a NOVIG-V3 string: a PEM key held in memory (the management key during
 * setup), or an Android Keystore key whose private half never leaves the phone's secure hardware.
 */
interface NovigSigningKey {
    val keyId: String
    val algorithm: NovigKeyAlgorithm
    fun sign(message: ByteArray): ByteArray
}

/**
 * Novig's request-signing scheme (NOVIG_API.md §3), verified against Novig's 30 published
 * vectors in `NovigV3Test`.
 */
object NovigV3 {
    const val SCHEME = "NOVIG-V3"
    const val HEADER_KEY_ID = "Novig-Key-Id"
    const val HEADER_TIMESTAMP = "Novig-Timestamp"
    const val HEADER_SIGNATURE = "Novig-Signature"

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Line 5. Split on '&', split each pair at the FIRST '=', percent-decode only `%XX` (a bare
     * '+' stays '+'), re-encode everything but `A-Z a-z 0-9 - . _ ~` as uppercase `%XX`, sort
     * bytewise by name then value keeping repeats, join with '&'.
     */
    fun canonicalQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val pairs = rawQuery.split('&').filter { it.isNotEmpty() }.map { token ->
            val i = token.indexOf('=')
            val (k, v) = if (i < 0) token to "" else token.substring(0, i) to token.substring(i + 1)
            encode(decode(k)) to encode(decode(v))
        }
        // Encoded strings are pure ASCII, so String comparison is bytewise.
        return pairs.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { "${it.first}=${it.second}" }
    }

    fun stringToSign(timestampMs: Long, method: String, path: String, rawQuery: String?, body: ByteArray): String =
        listOf(SCHEME, timestampMs.toString(), method.uppercase(), path, canonicalQuery(rawQuery), sha256Hex(body)).joinToString("\n")

    /** The three header values for one request. */
    fun headers(key: NovigSigningKey, timestampMs: Long, method: String, path: String, rawQuery: String?, body: ByteArray): Map<String, String> {
        val text = stringToSign(timestampMs, method, path, rawQuery, body)
        return mapOf(
            HEADER_KEY_ID to key.keyId,
            HEADER_TIMESTAMP to timestampMs.toString(),
            HEADER_SIGNATURE to Base64.getEncoder().encodeToString(key.sign(text.toByteArray(Charsets.UTF_8))),
        )
    }

    private fun decode(part: String): ByteArray {
        val raw = part.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(raw.size)
        var i = 0
        while (i < raw.size) {
            val b = raw[i]
            if (b == '%'.code.toByte() && i + 2 < raw.size) {
                val hi = Character.digit(raw[i + 1].toInt().toChar(), 16)
                val lo = Character.digit(raw[i + 2].toInt().toChar(), 16)
                if (hi >= 0 && lo >= 0) {
                    out.write(hi * 16 + lo)
                    i += 3
                    continue
                }
            }
            out.write(b.toInt())
            i++
        }
        return out.toByteArray()
    }

    private fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 3)
        for (byte in bytes) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
            }
        }
        return sb.toString()
    }
}

/**
 * A key from a PKCS#8 PEM, e.g. the `novig-api-key-<nickname>.pem` Novig's profile page
 * downloads. Pure BouncyCastle, so it works the same on every Android version and in tests.
 * P-256 uses deterministic ECDSA (RFC 6979) with DER output, as Novig requires.
 */
class PemSigningKey(override val keyId: String, pkcs8Pem: String) : NovigSigningKey {

    private val params = PrivateKeyFactory.createKey(pemToDer(pkcs8Pem))

    override val algorithm: NovigKeyAlgorithm = when (params) {
        is Ed25519PrivateKeyParameters -> NovigKeyAlgorithm.ED25519
        is ECPrivateKeyParameters -> {
            require(params.parameters.curve.fieldSize == 256) { "Only P-256 EC keys are supported" }
            NovigKeyAlgorithm.P256
        }
        else -> throw IllegalArgumentException("Novig keys are Ed25519 or P-256; this PEM is neither")
    }

    override fun sign(message: ByteArray): ByteArray = when (val p = params) {
        is Ed25519PrivateKeyParameters -> Ed25519Signer().run {
            init(true, p)
            update(message, 0, message.size)
            generateSignature()
        }
        is ECPrivateKeyParameters -> {
            val digest = MessageDigest.getInstance("SHA-256").digest(message)
            val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
            signer.init(true, p)
            val (r, s) = signer.generateSignature(digest)
            DERSequence(arrayOf(ASN1Integer(r), ASN1Integer(s))).encoded
        }
        else -> error("unreachable")
    }

    companion object {
        fun pemToDer(pem: String): ByteArray {
            val body = pem.lines()
                .filterNot { it.startsWith("-----") }
                .joinToString("") { it.trim() }
            require(body.isNotEmpty()) { "That doesn't look like a PEM key file" }
            return Base64.getMimeDecoder().decode(body)
        }
    }
}
