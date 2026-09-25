package com.tjshea.vigilant.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSigningKey
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * A Novig key whose private half lives in the Android Keystore (the phone's secure hardware
 * where available). It can sign but never be read out, copied off the phone, or backed up.
 * P-256 because Android's `SHA256withECDSA` emits exactly the DER signature Novig wants.
 */
class KeystoreSigningKey(private val alias: String, override val keyId: String) : NovigSigningKey {
    override val algorithm = NovigKeyAlgorithm.P256

    override fun sign(message: ByteArray): ByteArray {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = ks.getKey(alias, null) as? PrivateKey ?: error("Novig key $alias is missing from the Keystore; run setup again")
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(message)
            sign()
        }
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Makes a fresh P-256 keypair under [alias] and returns its public half as SPKI PEM. */
        fun generate(alias: String): String {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            gen.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            val pub = gen.generateKeyPair().public.encoded
            val b64 = Base64.encodeToString(pub, Base64.NO_WRAP).chunked(64).joinToString("\n")
            return "-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----\n"
        }

        fun delete(alias: String) {
            runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias) }
        }
    }
}
