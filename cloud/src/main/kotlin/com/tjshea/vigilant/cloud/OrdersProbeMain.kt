package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.CloudKeys
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient

/**
 * PX11a (Tj 2026-10-25: "I'm getting a lot of "no one was selling at that price" errors again. Investigate."): Novig's own record of the Vigilant subaccount's orders that ended with nothing filled, read
 * with the engine's `trading::read` key (it can read every order of its subaccount, never place one). Prints every field Novig returns for each, so the reason a taker order was rejected is in Novig's
 * words, not inferred; account, subaccount and user ids are left out (a public repository's logs). Read-only: GET requests only.
 *   ./gradlew :cloud:ordersprobe --args="[status ...]"   (default REJECTED CANCELED), run by .github/workflows/novig-probe.yml
 */
fun main(args: Array<String>) = runBlocking {
    val id = CloudKeys.rawEnv(CloudKeys.NOVIG_KEY_ID).trim()
    val pem = CloudKeys.rawEnv(CloudKeys.NOVIG_KEY_PEM)
    if (id.isEmpty() || pem.isEmpty()) { println("no Novig read key in this job's secrets"); return@runBlocking }
    val json = Json { ignoreUnknownKeys = true }
    val novig = NovigSignedClient(OkHttpClient(), json, PemSigningKey(id, pem))
    for (status in args.toList().ifEmpty { listOf("REJECTED", "CANCELED") }) {
        val raw = runCatching { novig.call("GET", "/v3/orders", rawQuery = "status=$status&limit=100") }.getOrElse { e -> println("$status: ${e.javaClass.simpleName} ${e.message?.take(160)}"); continue }
        val root = json.parseToJsonElement(raw)
        val orders = (root as? JsonArray) ?: ((root as? JsonObject)?.values?.firstOrNull { it is JsonArray } as? JsonArray) ?: JsonArray(emptyList())
        println("== $status: ${orders.size} orders (newest Novig returns first); top-level keys: ${(root as? JsonObject)?.keys ?: "array"}")
        for (o in orders) println("  " + shown(o))
    }
}

/** One order's fields, any id naming an account, subaccount, user or member left out. */
private fun shown(e: JsonElement): String = when (e) {
    is JsonObject -> e.entries.filterNot { (k, _) -> PRIVATE.any { k.contains(it, ignoreCase = true) } }.joinToString(", ", "{", "}") { (k, v) -> "$k=${shown(v)}" }
    is JsonArray -> e.joinToString(", ", "[", "]") { shown(it) }
    is JsonPrimitive -> e.content
}

private val PRIVATE = listOf("account", "user", "member", "owner")
