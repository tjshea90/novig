package com.tjshea.vigilant.data.pinnodds

import com.tjshea.vigilant.data.awaitText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Settings › Pinnodds live › Test key (Tj, 2026-10-08). One request, `GET /panel/api/me`, with the key in the `x-api-key` header (it counts as one request against the plan, nothing more). It
 * does NOT open the WebSocket: the account allows one connection and a test must never evict the one the live feed is using; the add-on's state comes from the answer instead.
 * The account's email, which the same answer carries, is never read into the result.
 */
object PinnKeyTest {
    sealed interface Result {
        data class Ok(
            val planLabel: String,
            val expiresAtMs: Long?,
            val wsActive: Boolean,
            val wsUntilMs: Long?,
            val perSecond: Int?,
            val usedToday: Int?,
        ) : Result {
            /** True when the live feed can run: the add-on is on and has not lapsed. */
            fun canStream(nowMs: Long): Boolean = wsActive && (wsUntilMs == null || wsUntilMs > nowMs)

            fun summary(nowMs: Long): String = buildString {
                append("Pinnodds accepted the key: $planLabel")
                if (expiresAtMs != null) append(", ").append(if (expiresAtMs > nowMs) "ends " else "ended ").append(formatDay(expiresAtMs))
                append(". ")
                if (canStream(nowMs)) {
                    append("WebSocket add-on is ON")
                    if (wsUntilMs != null) append(" until ").append(formatDay(wsUntilMs))
                    append(".")
                } else {
                    append("The WebSocket add-on is OFF, so the live feed cannot connect (the 3-day demo has ended, or the plan needs the WebSocket add-on).")
                }
                if (perSecond != null) append(" REST: $perSecond requests a second.")
            }
        }

        data class Bad(val message: String) : Result
    }

    suspend fun run(http: OkHttpClient, key: String, baseUrl: String = "https://pinnodds.com"): Result {
        val k = key.trim()
        if (k.isEmpty()) return Result.Bad("Paste the Pinnodds key first.")
        return try {
            val text = http.newCall(Request.Builder().url("$baseUrl/panel/api/me").header("x-api-key", k).get().build()).awaitText()
            parse(text.code, text.body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Bad("Pinnodds could not be reached (${e.message ?: e.javaClass.simpleName}).")
        }
    }

    internal fun parse(code: Int, body: String): Result {
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject }.getOrNull()
        when {
            code == 401 -> return Result.Bad("Pinnodds does not know this key (401 ${obj.str("error").orEmpty()}). Check it for a missing or extra character.")
            code == 429 -> return Result.Bad("Pinnodds is rate limiting this key (429): wait a minute and test again.")
            code !in 200..299 || obj == null -> return Result.Bad("Pinnodds answered $code.")
        }
        val plan = obj!!["plan"] as? JsonObject
        val ws = obj["ws_addon"] as? JsonObject
        val limits = plan?.get("limits") as? JsonObject
        val usage = obj["usage"] as? JsonObject
        return Result.Ok(
            planLabel = plan.str("label") ?: plan.str("id") ?: "unknown plan",
            expiresAtMs = (obj["plan_expires_at"] as? JsonPrimitive)?.longOrNull,
            wsActive = (ws?.get("active") as? JsonPrimitive)?.booleanOrNull == true,
            wsUntilMs = (ws?.get("until") as? JsonPrimitive)?.longOrNull,
            perSecond = (limits?.get("perSec") as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() },
            usedToday = (usage?.get("day") as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun JsonObject?.str(name: String): String? = (this?.get(name) as? JsonPrimitive)?.contentOrNull

    private fun formatDay(ms: Long): String = SimpleDateFormat("MMM d, h:mm a", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(ms))
}
