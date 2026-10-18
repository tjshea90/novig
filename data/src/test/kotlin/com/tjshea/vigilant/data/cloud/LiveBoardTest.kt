package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TailLiveSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tj, 2026-10-18 (TCL122-4): the Live dashboard with a GitHub / Phone switch, real-time numbers and graphs, every socket. */
class LiveBoardTest {
    private val now = 1_792_400_000_000L

    @Test
    fun `rates come from two readings, a restarted lane starts the history over, old readings go`() {
        var t = emptyList<LiveTrend.Sample>()
        t = LiveTrend.add(t, LiveTrend.Sample(now, 100, 10, 3, 0))
        t = LiveTrend.add(t, LiveTrend.Sample(now + 30_000, 160, 16, 3, 1))
        t = LiveTrend.add(t, LiveTrend.Sample(now + 30_000, 999, 99, 3, 1))   // not newer: ignored
        assertEquals(2, t.size)
        assertEquals(listOf(120f), LiveTrend.perMinute(t) { it.pushes })
        assertEquals(120.0, LiveTrend.recentPerMinute(t, { it.pushes })!!, 1e-9)
        t = LiveTrend.add(t, LiveTrend.Sample(now + 40_000, 5, 1, 2, 0))       // the counters went back: the lane started again
        assertEquals(1, t.size)
        assertNull(LiveTrend.recentPerMinute(t, { it.pushes }))
        val long = LiveTrend.add(listOf(LiveTrend.Sample(now, 1, 1, 1, 0)), LiveTrend.Sample(now + LiveTrend.KEEP_MS + 1, 2, 2, 1, 0))
        assertEquals(1, long.size)
    }

    @Test
    fun `the switch says where live runs, and each side's predicates follow it`() {
        val gh = ScanSettings().let { it.copy(gates = it.gates.copy(cloudScan = true)) }
        assertTrue(gh.liveOnGitHub)
        val phone = gh.copy(tailLive = TailLiveSettings(engine = false))
        assertFalse(phone.liveOnGitHub)
        // On GitHub with the engine scanning, the phone holds no live sockets and runs no live lane; on the phone it does.
        assertTrue(gh.cloudDrives); assertFalse(gh.phoneSockets); assertFalse(gh.phoneLive)
        assertTrue(phone.phoneSockets); assertTrue(phone.phoneLive)
        // Phone mode closes the runner's BoltOdds, Kalshi and SX Bet sockets.
        val runner = CloudEngine.forCloud(phone.copy(gates = phone.gates.copy(kalshiSocket = true, sxSocket = true, boltSocket = true)))
        assertFalse(runner.gates.boltSocket); assertFalse(runner.gates.kalshiSocket); assertFalse(runner.gates.sxSocket)
        val onGh = CloudEngine.forCloud(gh.copy(gates = gh.gates.copy(kalshiSocket = true, sxSocket = true, boltSocket = false)))
        assertTrue(onGh.gates.boltSocket); assertTrue(onGh.gates.kalshiSocket); assertTrue(onGh.gates.sxSocket)
    }

    @Test
    fun `the GitHub board reads the engine's live lane, its sockets and today's live bets`() {
        val s = ScanSettings(tailLive = TailLiveSettings(on = true, light = true, engine = true), gates = ScanSettings().gates.copy(cloudScan = true, cloudTrading = CloudTradeMode.LIVE))
        val stats = LiveStats(real = true, games = 4, withScore = 3, markets = 22, pushes = 900, judgments = 120, offers = 2, bidsUp = 1, lastPushMs = now - 2_000, judgeTookMs = 35, gameLines = listOf("A @ B 3-1 Q3"))
        val st = CloudStatus(runId = "1", heartbeatMs = now - 2_000, liveActive = true, liveNote = "REAL · 4 live games", liveStats = stats,
            socketStates = listOf(SocketState("Kalshi", wanted = true, live = true, lastMs = now - 1_000), SocketState("SX Bet", wanted = true, live = false)))
        val ledger = TradeLedger(entries = listOf(
            TradeEntry("live-a", now - 60_000, "LIVE", "m", "o", "", "A @ B", "NBA", "ML", "Moneyline", "A", now - 3_600_000, 5, 0.5, 2.5, 0.0, 0.55, 0.06, null, null, 0, "live tail bet", status = "WON", pnl = 2.5),
            TradeEntry("x", now - 60_000, "LIVE", "m2", "o2", "", "C @ D", "NBA", "ML", "Moneyline", "C", now + 3_600_000, 5, 0.5, 2.5, 0.0, 0.55, 0.06, null, null, 0, "auto-bet"),
        ))
        val today = LiveBoard.githubToday(ledger, now)
        assertEquals(1, today.bets)
        val trend = listOf(LiveTrend.Sample(now - 62_000, 780, 100, 4, 1), LiveTrend.Sample(now - 2_000, 900, 120, 4, 1))
        val b = LiveBoard.github(s, st, trend, today, now)
        assertEquals(LiveBoard.Where.GITHUB, b.where)
        assertTrue(b.title, b.title.startsWith("Live on GitHub: running") && b.title.contains("REAL"))
        assertEquals(DashboardModel.Tone.GOOD, b.tone)
        assertEquals("4", b.tiles.first { it.label == "Live games" }.value)
        assertEquals("120/min", b.tiles.first { it.label == "Novig pushes" }.value)
        assertEquals("1", b.tiles.first { it.label == "Live bets today" }.value)
        assertEquals(DashboardModel.Tone.GOOD, LiveBoard.socketTone(b.sockets[0]))
        assertEquals(DashboardModel.Tone.BAD, LiveBoard.socketTone(b.sockets[1]))
        assertEquals(listOf("A @ B 3-1 Q3"), b.gameLines)
        // A quiet engine is said plainly, and live does not move to the phone by itself.
        val quiet = LiveBoard.github(s, st.copy(heartbeatMs = now - 10 * 60_000, liveActive = false), trend, today, now)
        assertTrue(quiet.title, quiet.title.contains("quiet"))
        assertEquals(DashboardModel.Tone.BAD, quiet.tone)
        // Trading off: no live lane on GitHub, and the board says why.
        assertTrue(LiveBoard.github(s.copy(gates = s.gates.copy(cloudTrading = CloudTradeMode.OFF)), st, trend, today, now).detail.contains("trading is OFF"))
    }

    @Test
    fun `the phone board reads the phone's own lane`() {
        val s = ScanSettings(tailLive = TailLiveSettings(on = true, light = true, engine = false))
        val b = LiveBoard.phone(s, LiveStats(games = 2, pushes = 10), running = true, sockets = listOf(SocketState("Novig", wanted = true, live = true)), trend = emptyList(), today = LiveBoard.Today(), now = now)
        assertEquals(LiveBoard.Where.PHONE, b.where)
        assertTrue(b.title.startsWith("Live on this phone: running"))
        assertEquals("2", b.tiles.first { it.label == "Live games" }.value)
        assertEquals("—", b.tiles.first { it.label == "Novig pushes" }.value)
        assertTrue(LiveBoard.phone(s.copy(tailLive = s.tailLive.copy(on = false)), LiveStats(), false, emptyList(), emptyList(), LiveBoard.Today(), now).title.contains("off"))
    }

    @Test
    fun `the engine's auto-bet judges with the bet slider's kinds, totals and team totals included`() {
        val s = ScanSettings(autoBetKinds = setOf(com.tjshea.vigilant.engine.BetKind.PROP, com.tjshea.vigilant.engine.BetKind.MONEYLINE, com.tjshea.vigilant.engine.BetKind.SPREAD)).let { it.copy(vigGuards = it.vigGuards.copy(betLevel = 61)) }
        val k = CloudEngine.forTrading(s).autoBetKinds
        assertTrue(k.toString(), com.tjshea.vigilant.engine.BetKind.TEAM_TOTAL in k && com.tjshea.vigilant.engine.BetKind.TOTAL in k && com.tjshea.vigilant.engine.BetKind.SPREAD in k)
        // Off the slider (level 0) the phone's own kinds stand.
        assertEquals(s.autoBetKinds, CloudEngine.forTrading(s.copy(vigGuards = s.vigGuards.copy(betLevel = 0))).autoBetKinds)
    }
}
