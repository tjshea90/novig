package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.keys.ApiProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads back the keys the "Export my keys (encrypted)" workflow wrote (Tj, 2026-10-28, KX1): one AES-256-GCM file under a key from PBKDF2-SHA256 of Tj's passphrase
 * (the same format `tools/keys_export.py` writes and `tools/keys_decrypt.html` opens). GitHub keeps secrets write-only, so this file is the only way a key comes back to a phone.
 * Nothing here is stored or logged; the caller puts each key where the app keeps it.
 */
object KeysRestore {
    private const val AAD = "vigilant-keys-v1"
    const val BRANCH = "keys-export"
    const val PATH = "keys.enc.json"

    /** The secret name -> value map in [blob], or null when [passphrase] is wrong or the file is damaged. */
    fun decrypt(blob: String, passphrase: String): Map<String, String>? = runCatching {
        val o: JsonObject = Json.parseToJsonElement(blob).jsonObject
        val dec = Base64.getDecoder()
        val salt = dec.decode(o.getValue("salt").jsonPrimitive.content)
        val iv = dec.decode(o.getValue("iv").jsonPrimitive.content)
        val ct = dec.decode(o.getValue("ct").jsonPrimitive.content)
        val iter = o.getValue("iter").jsonPrimitive.content.toInt()
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iter, 256)).encoded
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(AAD.toByteArray())
        Json.parseToJsonElement(c.doFinal(ct).decodeToString()).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrNull()

    /** The API keys in [secrets] by provider (comma, space or line separated, as GitHub holds them). Pinnodds' REST key was set by hand on GitHub, so it comes back too. */
    fun providerKeys(secrets: Map<String, String>): Map<ApiProvider, List<String>> {
        val names = CloudKeys.BY_PROVIDER + (ApiProvider.PINNODDS to CloudKeys.PINNODDS)
        return names.mapNotNull { (p, name) ->
            val keys = secrets[name].orEmpty().split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            if (keys.isEmpty()) null else p to keys
        }.toMap()
    }

    /** Kalshi's Key ID and PEM, when both are there. */
    fun kalshi(secrets: Map<String, String>): Pair<String, String>? {
        val id = secrets[CloudKeys.KALSHI_KEY_ID]?.trim().orEmpty()
        val pem = secrets[CloudKeys.KALSHI_KEY_PEM]?.trim().orEmpty()
        return if (id.isNotEmpty() && pem.isNotEmpty()) id to pem else null
    }
}
