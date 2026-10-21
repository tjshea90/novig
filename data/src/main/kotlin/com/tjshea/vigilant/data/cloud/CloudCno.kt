package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.cno.CnoBooksView
import com.tjshea.vigilant.data.cno.CnoException
import com.tjshea.vigilant.data.cno.CnoFilters
import com.tjshea.vigilant.data.cno.CnoRow
import com.tjshea.vigilant.data.cno.CnoSnapshot
import com.tjshea.vigilant.data.cno.CnoSource
import com.tjshea.vigilant.data.cno.CnoVenueSource
import com.tjshea.vigilant.data.cno.CnoView
import com.tjshea.vigilant.data.scanner.ScanSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The CNO scan on the GitHub engine (Tj, 2026-10-21, CK: "put the entire cno scanner on GitHub engine. I want to be able to see the results on my phone from the cno scan, but all the bandwidth and
 * processing is on GitHub, just like vigilant already does"). The engine runs the phone's own [com.tjshea.vigilant.data.cno.CnoFeed] over a [CnoVenueSource] (Novig's and Kalshi's lists) with the
 * phone's CNO settings, mirrored in the config (view link, filters, venues, refresh pace, the books check), and publishes the list and every listed bet's book page as `cno.json.gz` on `cloud-feed`
 * ([CloudCnoFeed]); the status announces it ([CloudStatus.cnoAtMs]) and the phone fetches it only when that stamp moves. On the phone [CloudCnoSource] adopts it as its "read", so the CNO tab, the widget,
 * the alerts, the auto-bet's CNO lane and the bids priced from CNO all run on it unchanged, and the phone makes no CNO request.
 *
 * The phone reads CNO itself again ([engineServes] false) whenever the engine cannot: switched off, out of its hours, a heartbeat older than [ALIVE_MS], or CNO refusing the runner (no good read
 * in [REFUSED_MS]). Tapping a bet's "Open in Novig" still asks Novig's catalog (and, failing that, CNO) from the phone: one request at a tap.
 */
@Serializable
data class CloudCnoFeed(
    /** When the engine published this (its clock). */
    val atMs: Long,
    /** The engine's last good read (Novig's and Kalshi's lists merged); null before the first. */
    val snapshot: CnoSnapshot? = null,
    /** Each listed bet's CNO game page (the books check), by [CnoRow.key]. */
    val books: Map<String, CnoBooksView> = emptyMap(),
    /** Why the last read failed (cleared by a good one), as the phone's own feed would say it. */
    val error: String? = null,
    val errors: Int = 0,
    /** When the engine last read CNO successfully. 0 = never. */
    val lastGoodAtMs: Long = 0L,
    /** CNO asked for a pause until then (its own clock). */
    val pausedUntilMs: Long? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }

        fun encode(f: CloudCnoFeed): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(json.encodeToString(serializer(), f).toByteArray()) } }.toByteArray()

        fun decode(bytes: ByteArray): CloudCnoFeed? = runCatching {
            json.decodeFromString(serializer(), GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) })
        }.getOrNull()
    }
}

object CloudCno {
    /** A heartbeat older than this: the engine is not running, the phone reads CNO itself. */
    const val ALIVE_MS = 3 * 60_000L

    /** The engine has had no good CNO read for this long while it tries (CNO refusing the runner's address, say): the phone reads CNO itself. */
    const val REFUSED_MS = 10 * 60_000L

    /** Whether the engine should read CNO for [s]: the cloud scan drives, the CNO scanner is on, and the switch that sends it to GitHub is on. */
    fun wanted(s: ScanSettings): Boolean = s.cloudDrives && s.cnoOn && s.gates.cnoOnGitHub

    /** The view the engine and the phone read for [s]: the saved link, normalized ([CnoView.normalize]), or Novig's default. */
    fun viewOf(s: ScanSettings): String = CnoView.normalize(s.cnoViewUrl) ?: CnoView.DEFAULT

    /**
     * Whether the engine is serving the CNO list now ([status] is the newest heartbeat the phone holds, [now] the phone's clock): running, inside its hours, its CNO loop on, and a good
     * read within [REFUSED_MS] of its last try (or still in its first [REFUSED_MS]).
     */
    fun engineServes(status: CloudStatus?, now: Long): Boolean {
        val st = status ?: return false
        if (st.idle || now - st.heartbeatMs > ALIVE_MS) return false
        if (!st.cnoOn) return false
        val good = st.cnoGoodAtMs
        return if (good > 0L) st.heartbeatMs - good < REFUSED_MS else st.heartbeatMs - st.startedAtMs < REFUSED_MS
    }
}

/** The newest `cno.json.gz` the phone fetched. Written by the phone's poller ([com.tjshea.vigilant.app.CloudLink]), read by [CloudCnoSource]. */
class CloudCnoLatest {
    @Volatile var feed: CloudCnoFeed? = null
    @Volatile var fetchedAtMs: Long = 0L

    fun put(f: CloudCnoFeed, now: Long) { feed = f; fetchedAtMs = now }
}

/**
 * The phone's CNO "read" while the engine serves the list: the engine's snapshot and book pages, from memory; no request. Rows of a book switched off since the engine's last read are left out at once
 * (the engine's next read follows within seconds).
 */
class CloudCnoSource(private val latest: CloudCnoLatest) : CnoSource {
    override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot {
        val f = latest.feed ?: throw CnoException("Waiting for the GitHub engine's first CNO read")
        val snap = f.snapshot ?: throw CnoException(f.error?.let { "GitHub engine: $it" } ?: "Waiting for the GitHub engine's first CNO read")
        val v = filters.venues
        val rows = snap.rows.filter { r -> if (CnoVenueSource.isKalshi(r)) v.kalshi else v.novig }
        return if (rows.size == snap.rows.size) snap else snap.copy(rows = rows)
    }

    override suspend fun books(row: CnoRow): CnoBooksView? = latest.feed?.books?.get(row.key)

    override suspend fun booksBulk(row: CnoRow): CnoBooksView? = books(row)
}

/**
 * The CNO source the app holds: the engine's list ([cloud]) while [useCloud] says the engine serves it, else the phone's own reads ([local]). A bet's Novig link and the study's wide read stay
 * with [local] (one request at a tap; the wide read is the phone's study), and the wide read is skipped while the engine serves the list (no CNO request from the phone then).
 */
class SwitchingCnoSource(
    private val local: CnoSource,
    private val cloud: CnoSource,
    private val useCloud: suspend () -> Boolean,
) : CnoSource {
    override suspend fun fetch(url: String, filters: CnoFilters): CnoSnapshot = (if (useCloud()) cloud else local).fetch(url, filters)

    override suspend fun fetchWide(url: String, filters: CnoFilters, rows: Int): CnoSnapshot? = if (useCloud()) null else local.fetchWide(url, filters, rows)

    override suspend fun books(row: CnoRow): CnoBooksView? = if (useCloud()) cloud.books(row) else local.books(row)

    override suspend fun booksBulk(row: CnoRow): CnoBooksView? = if (useCloud()) cloud.booksBulk(row) else local.booksBulk(row)

    override suspend fun novigLink(row: CnoRow): String? = local.novigLink(row)
}
