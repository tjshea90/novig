package com.tjshea.vigilant.data.live

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test

class TmpEspnDebugTest {
    @Test
    fun debug() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        val http = OkHttpClient()
        for (ua in listOf("Vigilant (Android)", "Vigilant", "okhttp/4.12.0", "Mozilla/5.0 (Linux; Android 16; moto g) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 Vigilant")) {
            val r = runCatching {
                http.newCall(Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/34/roster").header("User-Agent", ua).build())
                    .execute().use { "${it.code} ${it.body!!.string().replace("\n", " ").take(200)}" }
            }
            println("DBG ua '$ua' -> $r")
        }
    }
}
