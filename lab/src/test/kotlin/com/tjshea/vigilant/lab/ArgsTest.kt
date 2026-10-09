package com.tjshea.vigilant.lab

import org.junit.Assert.assertEquals
import org.junit.Test

class ArgsTest {
    @Test fun defaultsAndLimits() {
        val d = Args.parse(emptyArray())
        assertEquals(60, d.minutes); assertEquals("out", d.out); assertEquals(Args.DEFAULT_LEAGUES, d.leagues); assertEquals(60, d.scanSeconds); assertEquals(30, d.tapeSeconds)
        assertEquals("a run is never longer than a job can be", 330, Args.parse(arrayOf("--minutes", "9999")).minutes)
        assertEquals("never faster than the public rate allows", 20, Args.parse(arrayOf("--scan-seconds", "1")).scanSeconds)
    }

    @Test fun leaguesAreKnownOnesOnly() {
        assertEquals(listOf("NBA", "NHL"), Args.parse(arrayOf("--leagues", "nba, NHL ,CRICKET")).leagues)
        assertEquals(Args.DEFAULT_LEAGUES, Args.parse(arrayOf("--leagues", "CRICKET")).leagues)
    }
}
