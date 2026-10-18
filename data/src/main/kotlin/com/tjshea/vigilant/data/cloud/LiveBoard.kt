package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cloud.DashboardModel.Tile
import com.tjshea.vigilant.data.cloud.DashboardModel.Tone
import com.tjshea.vigilant.data.scanner.ScanSettings
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The Live tab's dashboard in words and numbers (Tj, 2026-10-18, TCL122-4: "renovate the live dashboard for live betting. it should be a monitor and control board for the GitHub live betting engine with
 * stats and status on all the sockets ... there should be a switch for GitHub and phone ... the functionality should be the same, but all data and bandwidth on the phone mobile connection. include cool
 * real time graphs and easy to see stats for both"). One [Board] for whichever machine runs live ([ScanSettings.liveOnGitHub]): what it is doing, a few big numbers, every socket, the graphs' readings and
 * the games it watches. Pure, so the screen only draws it.
 */
object LiveBoard {
    enum class Where(val label: String) { GITHUB("GitHub"), PHONE("Phone") }

    data class Today(val bets: Int = 0, val open: Int = 0, val pnl: Double = 0.0)

    data class Board(
        val where: Where,
        val title: String,
        val tone: Tone,
        val detail: String,
        val tiles: List<Tile>,
        val sockets: List<SocketState>,
        val trend: List<LiveTrend.Sample>,
        val gameLines: List<String>,
    )

    private val ET: ZoneId = ZoneId.of("America/New_York")

    private fun dayStart(now: Long): Long = Instant.ofEpochMilli(now).atZone(ET).toLocalDate().atStartOfDay(ET).toInstant().toEpochMilli()

    /** The engine's live fills today (Eastern day): its ledger entries from the live lanes (`live-` ids, notes that start "live"). */
    fun githubToday(ledger: TradeLedger?, now: Long): Today {
        val from = dayStart(now)
        val live = ledger?.entries.orEmpty().filter { (it.id.startsWith("live-") || it.note.startsWith("live")) && it.atMs >= from }
        return Today(live.size, live.count { it.status == "OPEN" || it.status == "UNCONFIRMED" }, live.sumOf { it.pnl ?: 0.0 })
    }

    fun github(s: ScanSettings, st: CloudStatus?, trend: List<LiveTrend.Sample>, today: Today, now: Long): Board {
        val running = st?.liveLive(now) == true
        val (title, tone, detail) = when {
            !s.cloudDrives -> Triple("GitHub engine is off", Tone.WARN, "The cloud scan is off or paused (Settings › Cloud scan). Pick Phone to run live here instead.")
            st == null -> Triple("Waiting for GitHub", Tone.WARN, "No word from the engine yet.")
            s.gates.cloudTrading == CloudTradeMode.OFF -> Triple("Live is off on GitHub", Tone.WARN, "The engine's trading is OFF (Settings › Cloud scan › Engine trading): it runs no live lane.")
            !(s.tailLive.on || s.pinnLive || s.liveBid) -> Triple("Live is off on GitHub", Tone.NEUTRAL, "Switch Live light on below and GitHub starts it within seconds.")
            running -> Triple(
                "Live on GitHub: running" + if (st.liveStats?.real == true) " · REAL money" else " · paper",
                if (st.liveStopped.isNotBlank()) Tone.WARN else Tone.GOOD,
                st.liveNote.ifBlank { "Watching for live games." } + (st.liveStopped.takeIf { it.isNotBlank() }?.let { "\nStopped: $it" } ?: ""),
            )
            now - st.heartbeatMs > CloudStatus.DESK_STALE_MS -> Triple("GitHub engine is quiet", Tone.BAD, "Its last word was ${DashboardModel.ago(now - st.heartbeatMs)}. Live does not move to the phone by itself: pick Phone if you want it here.")
            else -> Triple("GitHub is starting live", Tone.WARN, st.liveNote.ifBlank { st.liveStopped.ifBlank { "The lane starts with the engine's next pass." } })
        }
        val stats = st?.liveStats.takeIf { running }
        return Board(Where.GITHUB, title, tone, detail, tiles(stats, trend, today, now), st?.socketStates.orEmpty(), trend, stats?.gameLines.orEmpty())
    }

    fun phone(s: ScanSettings, stats: LiveStats, running: Boolean, sockets: List<SocketState>, trend: List<LiveTrend.Sample>, today: Today, now: Long): Board {
        val (title, tone, detail) = when {
            s.killed -> Triple("STOP ALL is on", Tone.BAD, "Nothing live runs while STOP ALL is on.")
            !(s.tailLive.on && s.tailLive.light) -> Triple("Live is off on this phone", Tone.NEUTRAL, "Switch Live light on below to run it on this phone's connection.")
            running -> Triple("Live on this phone: running" + if (s.tailLive.bet) " · REAL money" else " · paper", Tone.GOOD, if (stats.problem.isNotBlank()) stats.problem else "GitHub's live lane and its sockets are paused; this phone holds them.")
            else -> Triple("This phone is starting live", Tone.WARN, stats.problem.ifBlank { "It needs Novig's live feed on this phone (the read key) and a live game on a picked league." })
        }
        return Board(Where.PHONE, title, tone, detail, tiles(stats.takeIf { running }, trend, today, now), sockets, trend, if (running) stats.gameLines else emptyList())
    }

    /** The big numbers: games, pushes and judgments a minute (from the graph's readings), how fast a judgment follows its push, offers, bids up, and today's live bets. */
    fun tiles(stats: LiveStats?, trend: List<LiveTrend.Sample>, today: Today, now: Long): List<Tile> {
        val pushes = LiveTrend.recentPerMinute(trend, { it.pushes })
        val judged = LiveTrend.recentPerMinute(trend, { it.judgments })
        fun rate(x: Double?) = x?.let { String.format(Locale.US, "%.0f/min", it) } ?: "—"
        return listOf(
            Tile("Live games", stats?.games?.toString() ?: "—", stats?.let { "${it.withScore} with a score · ${it.markets} markets" } ?: "none watched"),
            Tile("Novig pushes", rate(pushes), stats?.let { "${it.pushes} since start · last ${if (it.lastPushMs > 0) DashboardModel.ago(now - it.lastPushMs) else "never"}" } ?: ""),
            Tile("Judgments", rate(judged), stats?.takeIf { it.judgeTookMs > 0 }?.let { "${it.judgeTookMs} ms after the push" } ?: "the lane's looks at a game"),
            Tile("Offers / bids up", stats?.let { "${it.offers} / ${it.bidsUp}" } ?: "—", stats?.let { "${it.bidWants} bids asked for" } ?: ""),
            Tile(
                "Live bets today", "${today.bets}", (if (today.open > 0) "${today.open} open · " else "") + signed(today.pnl) + " settled",
                if (today.pnl > 0.005) Tone.GOOD else if (today.pnl < -0.005) Tone.BAD else Tone.NEUTRAL,
            ),
        )
    }

    /** A socket's state in a word: live, wanted but not connected, or off. */
    fun socketTone(x: SocketState): Tone = when {
        x.wanted && x.live -> Tone.GOOD
        x.wanted -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    fun socketWord(x: SocketState, now: Long): String = when {
        x.wanted && x.live -> "live" + (if (x.lastMs > 0) " · last word ${DashboardModel.ago(now - x.lastMs)}" else "")
        x.wanted -> "not connected" + (if (x.lastMs > 0) " · last word ${DashboardModel.ago(now - x.lastMs)}" else "")
        else -> "off"
    } + (x.detail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")

    private fun signed(x: Double) = (if (x < 0) "-$" else "+$") + String.format(Locale.US, "%.2f", kotlin.math.abs(x))
}
