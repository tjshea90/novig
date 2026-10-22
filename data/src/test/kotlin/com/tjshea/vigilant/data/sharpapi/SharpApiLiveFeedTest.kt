
class SharpApiLiveLeaguesTest {
    @org.junit.Test
    fun tennisIsReadWheneverLiveTennisIsWantedEvenWhenTheLeagueListLacksIt() {
        val s = com.tjshea.vigilant.data.scanner.ScanSettings(leagues = setOf("MLB"))
        val on = SharpApiLiveFeed.leaguesFor(s.copy(liveBidQuality = s.liveBidQuality.copy(tennis = true)))
        val off = SharpApiLiveFeed.leaguesFor(s.copy(liveBidQuality = s.liveBidQuality.copy(tennis = false)))
        org.junit.Assert.assertEquals(setOf("MLB", "ATP", "WTA"), on)
        org.junit.Assert.assertEquals(setOf("MLB"), off)
    }
}
