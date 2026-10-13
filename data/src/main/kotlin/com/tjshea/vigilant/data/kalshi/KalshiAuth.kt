package com.tjshea.vigilant.data.kalshi

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.RSAEngine
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.signers.PSSSigner
import org.bouncycastle.crypto.util.PrivateKeyFactory
import java.util.Base64

/**
 * Tj's Kalshi API key (KALSHI_API.md, docs.kalshi.com "API Keys"): the Key ID and the private key's PEM. Kept in the app's own file (`kalshi_key.json`), never in the repo.
 */
@Serializable
data class KalshiCredentials(val keyId: String = "", val pem: String = "") {
    val complete: Boolean get() = keyId.isNotBlank() && pem.isNotBlank()
}

/**
 * Kalshi's request signing, exactly as the docs give it: the message is `timestamp + METHOD + path` (path without the query string, with the `/trade-api/v2` prefix), signed with the key and
 * sent base64 in `KALSHI-ACCESS-SIGNATURE`, beside `KALSHI-ACCESS-KEY` (the Key ID) and `KALSHI-ACCESS-TIMESTAMP` (milliseconds). The socket's handshake signs `GET /trade-api/ws/v2` the same way.
 *
 * Ed25519 (the recommended type, a PKCS#8 PEM) signs the message bytes directly. RSA keys (a PKCS#8 or a PKCS#1 PEM) use RSA-PSS with SHA-256, MGF1 SHA-256 and a 32-byte salt. The PEM's header
 * does not say which type it is (PKCS#8 RSA looks the same), so the type is read from the parsed key. Pure BouncyCastle, as Novig's signer, so it behaves the same on every Android version.
 */
class KalshiSigner(val keyId: String, pem: String) {

    private val params: AsymmetricKeyParameter = parse(pem)

    /** "Ed25519" or "RSA", for Test key and Diagnostics. */
    val algorithm: String = when (params) {
        is Ed25519PrivateKeyParameters -> "Ed25519"
        is RSAPrivateCrtKeyParameters -> "RSA"
        else -> throw IllegalArgumentException("Kalshi keys are Ed25519 or RSA; this PEM is neither")
    }

    /** The base64 signature of [message]. */
    fun sign(message: String): String {
        val bytes = message.toByteArray(Charsets.UTF_8)
        val raw = when (val p = params) {
            is Ed25519PrivateKeyParameters -> Ed25519Signer().run {
                init(true, p)
                update(bytes, 0, bytes.size)
                generateSignature()
            }
            is RSAPrivateCrtKeyParameters -> PSSSigner(RSAEngine(), SHA256Digest(), SHA256Digest(), SALT_LENGTH).run {
                init(true, p)
                update(bytes, 0, bytes.size)
                generateSignature()
            }
            else -> error("unreachable")
        }
        return Base64.getEncoder().encodeToString(raw)
    }

    /** The three headers for a request of [method] to [path] (the query string, if any, is cut off before signing) made at [nowMs]. */
    fun headers(method: String, path: String, nowMs: Long): Map<String, String> {
        val ts = nowMs.toString()
        return mapOf(
            HEADER_KEY to keyId,
            HEADER_TIMESTAMP to ts,
            HEADER_SIGNATURE to sign(ts + method.uppercase() + path.substringBefore('?')),
        )
    }

    /** The headers for a request to [url]: its own path is what is signed. */
    fun headers(method: String, url: HttpUrl, nowMs: Long): Map<String, String> = headers(method, url.encodedPath, nowMs)

    companion object {
        const val HEADER_KEY = "KALSHI-ACCESS-KEY"
        const val HEADER_TIMESTAMP = "KALSHI-ACCESS-TIMESTAMP"
        const val HEADER_SIGNATURE = "KALSHI-ACCESS-SIGNATURE"
        const val SALT_LENGTH = 32

        /** The path the socket's handshake signs. */
        const val WS_PATH = "/trade-api/ws/v2"

        /** A signer for [c], or null when it is incomplete or its PEM cannot be read (the message says why, for the settings page). */
        fun of(c: KalshiCredentials): Result<KalshiSigner> = runCatching {
            require(c.complete) { "Add the Key ID and the private key" }
            KalshiSigner(c.keyId.trim(), c.pem)
        }

        private fun parse(raw: String): AsymmetricKeyParameter {
            // A key copied out of JSON or a chat often carries `\n` as two characters, or one long line: both are put back into lines.
            val pem = raw.replace("\\n", "\n").replace("\r", "").trim()
            require(pem.isNotEmpty()) { "The private key is empty" }
            val der = pemToDer(pem)
            return if (pem.contains("BEGIN RSA PRIVATE KEY")) {
                val k = RSAPrivateKey.getInstance(der)
                RSAPrivateCrtKeyParameters(k.modulus, k.publicExponent, k.privateExponent, k.prime1, k.prime2, k.exponent1, k.exponent2, k.coefficient)
            } else {
                PrivateKeyFactory.createKey(der)
            }
        }

        private fun pemToDer(pem: String): ByteArray {
            val lines = pem.lines().map { it.trim() }
            val body = if (lines.any { it.startsWith("-----") }) lines.filterNot { it.startsWith("-----") }.joinToString("") else lines.joinToString("")
            require(body.isNotEmpty()) { "That doesn't look like a PEM key" }
            return Base64.getMimeDecoder().decode(body)
        }
    }
}
