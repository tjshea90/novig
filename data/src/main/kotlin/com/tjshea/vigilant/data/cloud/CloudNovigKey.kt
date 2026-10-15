package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.novig.signing.ManagementKey
import com.tjshea.vigilant.data.novig.signing.NovigKeyAlgorithm
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.signing.SecretBox
import com.tjshea.vigilant.data.store.JsonFileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The Novig key the GitHub engine reads with (Tj, 2026-10-15: "allow me to send my novig key and boltodds key to GitHub for rapid scanning"; TASKS.md TCL13, the word TCL11 waited for).
 *
 * It is a **`trading::read`** key made for the engine alone: it can read the catalog, the books and the websocket, and it **cannot place an order or move money** (NOVIG_API.md §2). It is a key of its own rather
 * than the phone's read key for two reasons: the phone's key is kept in the Android Keystore and cannot be taken out of it, and two machines sharing one key would share its rate bucket and its single websocket (a
 * second connection starves the first). Novig allows any number of `trading::read` keys per subaccount.
 *
 * The private half exists in plain text only for the moment it takes to mint the key and seal it for GitHub: the phone keeps it sealed by the Keystore ([CloudNovigKeyStore]) so it can be sent again, and the secret on
 * GitHub is write-only. Never the trading key, never the management key.
 */
class EngineNovigKey(val keyId: String, val pem: String) {
    override fun toString() = "EngineNovigKey(••••${keyId.takeLast(4)})"
}

@Serializable
data class SavedEngineKey(val keyId: String, val sealedPem: String, val subaccountKeyId: String, val mintedAtMs: Long)

@Serializable
data class EngineKeyFile(val key: SavedEngineKey? = null)

/** The engine's key, sealed for disk ([SecretBox]: the Android Keystore on the phone). */
class CloudNovigKeyStore(file: File, private val box: SecretBox, json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }, private val clock: () -> Long = System::currentTimeMillis) {
    private val store = JsonFileStore(file, EngineKeyFile.serializer(), { EngineKeyFile() }, json)

    /** The saved key, or null when there is none or this phone can no longer open it. */
    suspend fun load(): EngineNovigKey? {
        val saved = store.read().key ?: return null
        val pem = runCatching { box.open(saved.sealedPem) }.getOrNull()?.takeIf { it.contains("PRIVATE KEY") } ?: return null
        return EngineNovigKey(saved.keyId, pem)
    }

    /** When it was made and for which subaccount, without opening it (the Cloud page says "a read-only key made 2 days ago"). */
    suspend fun saved(): SavedEngineKey? = store.read().key?.copy(sealedPem = "")

    suspend fun save(key: EngineNovigKey, subaccountKeyId: String) {
        store.update { it.copy(key = SavedEngineKey(key.keyId, box.seal(key.pem), subaccountKeyId, clock())) }
    }

    suspend fun clear() { store.update { EngineKeyFile() } }
}

object CloudNovigKey {
    const val NAME = "Vigilant cloud engine"

    /** A fresh P-256 keypair (software, so the private half can be sent): its PKCS#8 PEM and the public half's SPKI PEM with the line breaks Novig asks for. */
    fun generate(): Pair<String, String> {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return pem("PRIVATE KEY", kp.private.encoded) to pem("PUBLIC KEY", kp.public.encoded)
    }

    private fun pem(label: String, der: ByteArray): String =
        "-----BEGIN $label-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der) + "\n-----END $label-----\n"

    /**
     * Mints the engine's read-only key on the subaccount [subaccountKeyId] with the management key ([management], held by the phone), then proves it with `POST /v3/echo` (costs nothing) before it is returned.
     * Throws with Novig's own reason on a refusal (the caller words it; no key text is ever in a message).
     */
    suspend fun mint(
        http: OkHttpClient, json: Json, management: ManagementKey, subaccountKeyId: String,
        baseUrl: String = "https://api.novig.com", clock: () -> Long = System::currentTimeMillis,
    ): EngineNovigKey {
        require(subaccountKeyId.isNotBlank()) { "no Vigilant subaccount is connected" }
        val (privatePem, publicPem) = generate()
        val admin = NovigSignedClient(http, json, PemSigningKey(management.keyId, management.pem), baseUrl, clock)
        val keyId = admin.createSubaccountKey(subaccountKeyId, NAME, publicPem, NovigKeyAlgorithm.P256, "trading::read").keyId
        NovigSignedClient(http, json, PemSigningKey(keyId, privatePem), baseUrl, clock).echo()
        return EngineNovigKey(keyId, privatePem)
    }
}
