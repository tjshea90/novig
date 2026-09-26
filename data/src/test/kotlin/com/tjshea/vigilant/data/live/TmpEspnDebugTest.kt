package com.tjshea.vigilant.data.live

import com.tjshea.vigilant.data.teams.PlayerTeams
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test

class TmpEspnDebugTest {
    @Test
    fun debug() = runBlocking {
        assumeTrue(System.getenv("VIGILANT_LIVE") == "1")
        for (proto in listOf(listOf(okhttp3.Protocol.HTTP_1_1), listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))) {
            val c = OkHttpClient.Builder().protocols(proto).build()
            val t0 = System.currentTimeMillis()
            val r = runCatching { c.newCall(Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams?limit=1000").build()).execute().use { "${it.code} ${it.protocol} ${it.body!!.string().length}" } }
            println("DBG proto $proto -> $r in ${System.currentTimeMillis() - t0} ms")
        }
        val http = OkHttpClient.Builder().protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build()
        val body = http.newCall(Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams?limit=1000").header("User-Agent", PlayerTeams.USER_AGENT).build()).execute().use { it.body!!.string() }
        println("DBG body ${body.length} ${body.replace("\n"," ").take(440)}")
        for (ua in listOf("Vigilant (Android)", "Vigilant", "Mozilla/5.0 (Linux; Android 16; moto g) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36 Vigilant")) {
            val r = runCatching { http.newCall(Request.Builder().url("https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/34/roster").header("User-Agent", ua).build()).execute().use { "${it.code} ${it.body!!.string().take(30)}" } }
            println("DBG ua '$ua' -> $r")
        }
        return@runBlocking
        val teams = PlayerTeams.parseTeams(el)
        println("DBG teams ${teams.size} ${teams.take(3)}")
        println("DBG find ${PlayerTeams.findTeam("Houston Texans", teams)}")
        val t = PlayerTeams(http)
        val n = t.fill(listOf(PlayerTeams.Game("NFL", "Houston Texans @ Indianapolis Colts")))
        println("DBG fill $n leagues ${t.state.value.leagues.keys} rosters ${t.state.value.rosters.keys} req ${t.requests}")
    }
}
