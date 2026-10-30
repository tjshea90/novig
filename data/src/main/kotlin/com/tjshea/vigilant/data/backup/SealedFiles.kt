package com.tjshea.vigilant.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The app's files that hold a Keystore-sealed secret, and where in each the sealed text sits. A sealed value can only be opened on the phone that sealed it, so a backup stores it opened ([open]) inside
 * the encrypted archive and the phone that restores it seals it again with its own Keystore ([seal]). Everything else in the file (key ids, dates, the subaccount) is carried untouched.
 *
 * A new file that seals a secret goes in [FIELDS], or the full backup would carry a value no other phone can read ([SealedFilesTest] checks the table against the app's file names).
 */
object SealedFiles {
    /** File name in the app's files folder -> the JSON path of the sealed text. */
    val FIELDS: Map<String, List<String>> = mapOf(
        "novig_management_key.json" to listOf("key", "sealedPem"),
        "cloud_novig_key.json" to listOf("key", "sealedPem"),
        "cloud_trade_key.json" to listOf("key", "sealedPem"),
        "kalshi_key.json" to listOf("pem"),
        "cloud_token.json" to listOf("sealed"),
    )

    private val json = Json { prettyPrint = false }

    /** [text] with its sealed value replaced by `transform(value)`; null when the file has no value there (nothing saved), the value is blank, or [transform] cannot do it (an unreadable seal). */
    private fun map(name: String, text: String, transform: (String) -> String): String? {
        val path = FIELDS[name] ?: return null
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val value = at(root, path)?.takeIf { it.isNotBlank() } ?: return null
        val changed = runCatching { transform(value) }.getOrNull() ?: return null
        return json.encodeToString(JsonElement.serializer(), put(root, path, changed))
    }

    /** The file with its secret opened (plain text), or null when there is none or it cannot be opened here. */
    fun open(name: String, text: String, opener: (String) -> String): String? = map(name, text, opener)

    /** The file with a plain secret sealed again for this phone. */
    fun seal(name: String, text: String, sealer: (String) -> String): String? = map(name, text, sealer)

    /** True when [name] is a file that holds a saved secret ([text] has a value in the sealed place). */
    fun holdsSecret(name: String, text: String): Boolean = map(name, text) { it } != null

    private fun at(o: JsonObject, path: List<String>): String? {
        var cur: JsonElement = o
        for (p in path) cur = (cur as? JsonObject)?.get(p) ?: return null
        return (cur as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.content
    }

    private fun put(o: JsonObject, path: List<String>, value: String): JsonObject {
        val head = path.first()
        return if (path.size == 1) JsonObject(o + (head to JsonPrimitive(value)))
        else JsonObject(o + (head to put(o.getValue(head).jsonObject, path.drop(1), value)))
    }
}
