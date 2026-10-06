package com.tjshea.vigilant.data.novig.trading

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * What a reply looked like, with none of what it said: its keys and the kind of each value (`{accepted:[{clientId:string,orderId:string}x2]}`), so a reply this app can't read can be
 * reported and read by a person later without a price, an id or an amount ever leaving the phone (v0.70.4: Novig's batch-place answer was unreadable on the first batch of every run,
 * 3 of 3, and the file could not say why; v0.70.1 diagnostics, RESEARCH.md §97). A key that is an id (a UUID, or longer than 24 characters) is written `<id>`.
 */
object ReplyShape {

    private const val MAX_DEPTH = 4
    private const val MAX_LENGTH = 300
    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** [body]'s shape in at most [MAX_LENGTH] characters; an empty body, or one that isn't JSON, says so (and what kind of character it starts with, never the text). */
    fun of(body: String, json: Json = Json): String {
        if (body.isBlank()) return "an empty body (${body.length} characters)"
        val notJson = "not JSON (${body.length} characters, starting with ${startsWith(body.trimStart().first())})"
        val element = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            return notJson
        }
        // The parser reads a bare word ("<html>…", "oops!") as an unquoted literal: only a number, true, false or null is a JSON value on its own.
        if (element is JsonPrimitive && !element.isString && element !is JsonNull && element.booleanOrNull == null && element.doubleOrNull == null) return notJson
        return shape(element, 0).let { if (it.length > MAX_LENGTH) it.take(MAX_LENGTH) + "…" else it }
    }

    private fun startsWith(c: Char): String = when {
        c == '<' -> "'<' (HTML)"
        c.isLetter() -> "a letter"
        c.isDigit() -> "a digit"
        else -> "'$c'"
    }

    private fun key(k: String): String = if (UUID.matches(k) || k.length > 24) "<id>" else k

    private fun shape(e: JsonElement, depth: Int): String = when (e) {
        is JsonObject -> when {
            e.isEmpty() -> "{}"
            depth >= MAX_DEPTH -> "{…}"
            else -> e.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (k, v) -> "${key(k)}:${shape(v, depth + 1)}" }
        }
        is JsonArray -> when {
            e.isEmpty() -> "[]"
            depth >= MAX_DEPTH -> "[…]"
            else -> "[${shape(e.first(), depth + 1)}x${e.size}]"
        }
        is JsonNull -> "null"
        is JsonPrimitive -> if (e.isString) "string" else if (e.booleanOrNull != null) "bool" else "number"
    }
}
