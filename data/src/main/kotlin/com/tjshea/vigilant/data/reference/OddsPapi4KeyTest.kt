package com.tjshea.vigilant.data.reference

import com.tjshea.vigilant.data.await
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Settings › OddsPapi Free (v4) › Test key (ODDSPAPI_API.md addendum 2026-10-11): `GET /v4/account` is unmetered, so the test costs nothing. It says the plan's real request allowance and use, the books and
 * sports it includes, and what the app will do with it. A key that v4 refuses is tried against v5's `/bookmakers` (header key, no cost) so the message can say which page the key belongs on. The raw
 * account answer is the sample, with the key itself replaced.
 */
object OddsPapi4KeyTest {
    class Result(val ok: Boolean, val summary: String, val sample: String)

    suspend fun run(client: OddsPapi4Client, http: OkHttpClient? = null, v5Base: String = "https://v5.oddspapi.io/en"): Result {
        val sample = StringBuilder()
        val acc = try {
            client.account()
        } catch (e: Exception) {
            val m = readableError(e)
            val v5 = if (http != null && m.contains("refused", true)) isV5Key(client, http, v5Base) else false
            val hint = if (v5) "\nThis looks like a v5 key (a trial or contract from contact@55-tech.com). Paste it under Settings › OddsPapi (v5), where the app reads it for everything v5 does."
            else "\nA v4 key comes from your account page at oddspapi.io (the free plan)."
            return Result(false, "OddsPapi Free: $m$hint", sample.toString())
        }
        sample.append("=== GET /v4/account (key replaced) ===\n").append(client.lastAccountRaw.ifEmpty { "(see the summary)" }).append("\n")
        val lines = ArrayList<String>()
        lines += acc.summary()
        val left = acc.left
        lines += when {
            !acc.active -> "The plan is not active: nothing will be asked."
            left == null -> "The plan did not say how many requests are left."
            left <= Op4Budget.RESERVE -> "$left requests left: under the ${Op4Budget.RESERVE} the app always keeps, so it spends none this period (free historical odds still work)."
            else -> "$left requests left. The app spends them only on finding the game and the market list for a closing line (a few requests, cached for days), at most an even share a day; the closing prices themselves come from /v4/historical-odds, which is free."
        }
        if (acc.books.isNotEmpty() && "pinnacle" !in acc.books.keys) lines += "This plan has no Pinnacle: closing lines need it, so none will be read."
        if (acc.websocket != null && acc.websocket > 0) lines += "WebSocket access: ${acc.websocket} (not used by the app)."
        return Result(acc.active, lines.joinToString("\n"), sample.toString())
    }

    private var lastRaw: String = ""

    private fun redactedAccount(client: OddsPapi4Client): String = lastRaw.ifEmpty { "(see the summary)" }

    private suspend fun isV5Key(client: OddsPapi4Client, http: OkHttpClient, v5Base: String): Boolean = client.keyList().any { key ->
        runCatching {
            http.newCall(Request.Builder().url("$v5Base/bookmakers").header("X-API-Key", key).get().build()).await().use { it.code in 200..299 }
        }.getOrDefault(false)
    }
}
