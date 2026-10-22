package com.tjshea.vigilant.cloud

import com.tjshea.vigilant.data.cloud.CloudSources
import com.tjshea.vigilant.data.cloud.NovigClose
import com.tjshea.vigilant.data.cloud.NovigCloses
import com.tjshea.vigilant.data.cloud.TradeEntry
import com.tjshea.vigilant.data.live.KalshiLiveData
import com.tjshea.vigilant.data.live.KalshiLiveRow
import com.tjshea.vigilant.data.novig.NovigPublicClient
import com.tjshea.vigilant.data.pinnodds.DayJournal
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.sharpapi.SaSplit
import com.tjshea.vigilant.data.sharpapi.SharpApiSplits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/**
 * The research feeds the API audit found (AF6, AF7, AF9; research/api_full_audit_2026-10-22.md), each a journal in the folder the archive loop keeps in the cloud-archive Releases. None of them prices,
 * orders or changes a setting; each reads only while its API is on, and a failure is a log line, never a stop.
 *  - `kalshi-live-<day>`: Kalshi's public live data of every sports game in play (status and the sport's score fields), every 30 s while games are live: the raw shapes a score source is built from.
 *  - `sharpapi-splits-<day>`: SharpAPI's public betting splits (DraftKings, Circa, BetMGM tickets and money %), every 30 min.
 *  - `novig-close-<day>`: Novig's own closing price (last trade before the start, from its candles) of every side the engine ordered or bid on, once per side after its game starts.
 */
object AuditFeeds {
    fun launch(
        scope: CoroutineScope,
        out: File,
        http: OkHttpClient,
        json: Json,
        sources: CloudSources,
        novig: NovigPublicClient,
        settings: () -> ScanSettings?,
        ordered: () -> List<TradeEntry>,
        log: (String) -> Unit,
    ) {
        val dir = File(out, "cloud")
        val kalshiJournal = DayJournal(dir, "kalshi-live", KalshiLiveRow.serializer()) { it.atMs }
        val kalshi = KalshiLiveData(http, json)
        scope.launch {
            delay(60_000L)
            var lastLog = 0L
            while (isActive) {
                val s = settings()
                if (s != null && s.useKalshi) {
                    try {
                        val now = System.currentTimeMillis()
                        val live = kalshi.liveGames(now)
                        if (live.isNotEmpty()) {
                            val rows = kalshi.read(live, now)
                            if (rows.isNotEmpty()) kalshiJournal.appendAll(rows)
                            if (now - lastLog > 30 * 60_000L) {
                                lastLog = now
                                log("Kalshi live data: ${live.size} games in play, ${rows.size} answered (" + rows.groupingBy { it.type }.eachCount().entries.joinToString(", ") { "${it.key} ${it.value}" } + ")")
                            }
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { log("Kalshi live data failed: ${e.message?.take(120)}") }
                }
                delay(KalshiLiveData.LIVE_EVERY_MS)
            }
        }

        val splitsJournal = DayJournal(dir, "sharpapi-splits", SaSplit.serializer()) { it.seenAtMs }
        scope.launch {
            delay(180_000L)
            while (isActive) {
                val s = settings()
                if (s != null && sources.sharpApiOn(s)) {
                    try {
                        val rows = SharpApiSplits.all(sources.sharpApiClient, System.currentTimeMillis())
                        if (rows.isNotEmpty()) splitsJournal.appendAll(rows)
                        log("SharpAPI splits: ${rows.size} rows (" + rows.groupingBy { it.book }.eachCount().entries.joinToString(", ") { "${it.key} ${it.value}" } + ")")
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        log("SharpAPI splits failed: ${e.message?.take(120)}")
                        // Not on this plan: no point asking every half hour.
                        if (e.message?.contains("tier_restricted") == true) delay(6 * 3_600_000L)
                    }
                }
                delay(SharpApiSplits.EVERY_MS)
            }
        }

        val closeJournal = DayJournal(dir, "novig-close", NovigClose.serializer()) { it.atMs }
        scope.launch {
            delay(240_000L)
            val done = HashSet<String>()
            while (isActive) {
                val now = System.currentTimeMillis()
                val due = ordered().filter { e ->
                    e.mode != "PAPER" && e.contracts > 0 && "${e.marketId}|${e.outcomeId}" !in done &&
                        now - e.startsTs in NovigCloses.AFTER_START_MS..NovigCloses.MAX_AFTER_START_MS
                }.distinctBy { "${it.marketId}|${it.outcomeId}" }.take(40)
                for (e in due) {
                    val series = try {
                        novig.candles(e.marketId, "1m", startsAfter = e.startsTs - NovigCloses.WINDOW_MS, startsBefore = e.startsTs)
                    } catch (x: CancellationException) { throw x } catch (_: Exception) { null } ?: continue
                    done += "${e.marketId}|${e.outcomeId}"
                    runCatching { closeJournal.append(NovigCloses.row(now, e, series)) }
                }
                delay(10 * 60_000L)
            }
        }
    }
}
