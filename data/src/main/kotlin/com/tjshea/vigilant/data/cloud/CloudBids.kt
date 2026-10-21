package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.JsonSplit
import com.tjshea.vigilant.data.cloud.CloudCodec.dbl
import com.tjshea.vigilant.data.cloud.CloudCodec.int
import com.tjshea.vigilant.data.cloud.CloudCodec.lng
import com.tjshea.vigilant.data.cloud.CloudCodec.num
import com.tjshea.vigilant.data.cloud.CloudCodec.obj
import com.tjshea.vigilant.data.cloud.CloudCodec.str
import com.tjshea.vigilant.data.cloud.CloudCodec.strOrNull
import com.tjshea.vigilant.data.novig.BidLevel
import com.tjshea.vigilant.data.novig.NovigBook
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.trading.maker.BidCandidates.BidLine
import com.tjshea.vigilant.data.novig.trading.maker.MakerLine
import com.tjshea.vigilant.data.novig.trading.maker.MakerLines
import com.tjshea.vigilant.data.novig.trading.maker.MakerRules
import com.tjshea.vigilant.data.scanner.BetKind
import com.tjshea.vigilant.data.scanner.Freshness
import com.tjshea.vigilant.data.tracker.BetTracker
import com.tjshea.vigilant.data.tracker.FairBasis
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The bid desk's candidate lines as the GitHub engine publishes them (`bids.json.gz`; Tj, 2026-10-15): every side worth taking to Novig's books for a bid, with the fair odds' side of the work already done
 * ([com.tjshea.vigilant.data.novig.trading.maker.BidCandidates]). [partial]: the scan behind them is still running (its finished leagues only), as a [com.tjshea.vigilant.data.scanner.ScanResult.partial] is.
 */
class CloudBids(val atMs: Long, val partial: Boolean, val configRev: Long, val lines: List<BidLine>, val unreadable: Int = 0,
    /** A quick scan's reach (CloudLanes): bids on games starting after it were not judged and stay up. In memory only, for the engine's own desk. */
    val reachUntilMs: Long? = null,
) {
    companion object {
        const val VERSION = 1

        fun encode(b: CloudBids): ByteArray {
            val j = buildJsonObject {
                put("v", VERSION); put("at", b.atMs); put("pt", b.partial); put("rev", b.configRev)
                put("lines", JsonArray(b.lines.map(::line)))
            }
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(Json.encodeToString(JsonObject.serializer(), j).toByteArray(Charsets.UTF_8)) }
            return out.toByteArray()
        }

        /**
         * The candidates in [gz], or null when the bytes are not a file this version understands; a line that cannot be read is skipped and counted. The lines are cut out of the text one at a time
         * ([JsonSplit]: a `JsonElement` tree costs about 8 times its JSON in heap and a list can be thousands of lines), so the peak is the text plus one line.
         */
        fun decode(gz: ByteArray): CloudBids? = runCatching {
            val text = GZIPInputStream(gz.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val split = JsonSplit.elements(text, "lines")
            val j = Json.parseToJsonElement(split?.rest ?: text).jsonObject
            var bad = 0
            fun one(e: () -> BidLine): BidLine? = runCatching(e).getOrNull().also { if (it == null) bad++ }
            val lines = if (split != null) (0 until split.size).mapNotNull { i -> one { line(Json.parseToJsonElement(split.element(i)).jsonObject) } }
            else j["lines"]!!.jsonArray.mapNotNull { e -> one { line(e.jsonObject) } }
            CloudBids(j["at"]!!.jsonPrimitive.content.toLong(), j["pt"]?.jsonPrimitive?.content == "true", j["rev"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L, lines, bad)
        }.getOrNull()

        private fun line(b: BidLine): JsonObject = buildJsonObject {
            val l = b.line
            put("mk", CloudCodec.market(l.market)); put("o", l.outcomeId); put("st", l.startsTs); put("es", b.eventStartsTs)
            put("lg", l.league); put("en", l.eventName); put("ml", l.marketLabel); put("sel", l.selection); put("kd", l.kind.name)
            l.fair?.let { put("f", it) }; l.fairAsOfMs?.let { put("fa", it) }; l.fairNewestMs?.let { put("fn", it) }
            put("bk", l.books); put("fb", CloudCodec.strings(l.fairBooks)); put("bf", CloudCodec.doubles(l.bookFairs)); put("sf", CloudCodec.doubles(l.sharpFairs))
            l.offer?.let { put("of", it) }; l.bestBid?.let { put("bb", it) }; l.bookAtMs?.let { put("ba", it) }
            if (l.bidLevels.isNotEmpty()) put("bl", buildJsonArray { l.bidLevels.forEach { add(buildJsonArray { add(JsonPrimitive(it.priceMilli)); add(JsonPrimitive(it.contracts)) }) } })
            l.basis?.let { x -> put("bs", buildJsonObject { put("s", x.source); put("sh", CloudCodec.strings(x.sharp)); put("b", x.books) }) }
            l.unavailable?.let { put("un", it) }
        }

        private fun line(j: JsonObject): BidLine {
            val l = MakerLine(
                market = CloudCodec.market(j.obj("mk")), outcomeId = j.str("o"), startsTs = j.lng("st")!!, league = j.str("lg"), eventName = j.str("en"), marketLabel = j.str("ml"), selection = j.str("sel"),
                kind = BetKind.valueOf(j.str("kd")), fair = j.dbl("f"), fairAsOfMs = j.lng("fa"), fairNewestMs = j.lng("fn"), fairOld = false, books = j.int("bk") ?: 0,
                fairBooks = j["fb"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }, bookFairs = j["bf"]?.jsonArray.orEmpty().map { it.num()!! }, sharpFairs = j["sf"]?.jsonArray.orEmpty().map { it.num()!! },
                offer = j.dbl("of"), bestBid = j.dbl("bb"), live = false, source = BetTracker.SOURCE_VIGILANT,
                basis = j["bs"]?.jsonObject?.let { FairBasis(it.str("s"), it["sh"]?.jsonArray.orEmpty().map { x -> x.jsonPrimitive.content }, it.int("b") ?: 0) },
                bookAtMs = j.lng("ba"), bidLevels = j["bl"]?.jsonArray.orEmpty().map { val a = it.jsonArray; BidLevel(a[0].jsonPrimitive.content.toInt(), a[1].jsonPrimitive.content.toLong()) },
                unavailable = j.strOrNull("un"),
            )
            return BidLine(l, j.lng("es")!!)
        }
    }
}

/**
 * The phone's side of the bid desk on GitHub's candidates (Tj, 2026-10-15): holds the newest [CloudBids], reads Novig's order book for the candidates that need one (over the phone's own key and stream:
 * the part that must be seconds old and needs the key), and hands the unchanged desk its [MakerLine]s: GitHub's fair side, Novig's side as new as the phone has it. Nothing here decides a bid.
 *
 *  - [lines] is [MakerLines.from]'s answer for the candidates: the freshest Novig side known (the phone's own read when newer than the engine's), the fair's age worked out at [now], and the same book-age
 *    rule ([MakerLines.maxBookAgeMs]); a side the phone has a bid or an unjudged fill on is kept from the last list that carried it ([keep]) so its fill can still be judged, and marked
 *    unavailable ([DROPPED]) once a complete list no longer carries it, so the desk takes its bid down as the local scan would.
 *  - [refresh] reads the books of the candidates whose Novig side is older than [REFRESH_AFTER_MS], soonest game first, at most [maxReads] a call; a book the stream already holds costs nothing.
 */
class CloudBidLane(
    private val novig: NovigSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var bids: CloudBids? = null
    private val books = ConcurrentHashMap<String, NovigBook>()
    private val kept = ConcurrentHashMap<String, BidLine>()
    @Volatile var lastReadAtMs: Long = 0L
        private set
    @Volatile var lastReads: Int = 0
        private set
    @Volatile var problem: String? = null
        private set

    val current: CloudBids? get() = bids

    /** A newer candidate list arrived (older ones are ignored). */
    fun put(b: CloudBids) {
        val held = bids
        if (held == null || b.atMs >= held.atMs) {
            bids = b
            // The markets most worth a push come first (soonest games): the stream starts on them now.
            runCatching { novig.watch(b.lines.asSequence().map { it.line.marketId }.distinct().take(novig.watchRoom()).toList()) }
        }
    }

    /** One of [outcomeIds] is a side the phone has a bid or an unjudged fill on: its last candidate line is kept for as long as it is asked for. */
    private fun remember(b: CloudBids, outcomeIds: Set<String>, prune: Boolean) {
        if (outcomeIds.isEmpty()) { if (prune) kept.clear(); return }
        for (l in b.lines) if (l.line.outcomeId in outcomeIds) kept[l.line.outcomeId] = l
        if (prune) kept.keys.removeIf { it !in outcomeIds }
    }

    /**
     * The desk's lines at [now] under [rules]; [keep]: the outcome ids the phone has bids or unjudged fills on. Empty before any candidate list arrived. [prune]: [keep] is ALL of them, so the lines kept
     * for any other side are let go (the desk's own pass and the Bids tab); a caller that asks about one side only (an Approve) leaves what the others kept alone.
     */
    fun lines(now: Long, rules: MakerRules, keep: Set<String> = emptySet(), prune: Boolean = true): List<MakerLine> {
        val b = bids ?: return emptyList()
        remember(b, keep, prune)
        val seen = HashSet<String>()
        val out = ArrayList<MakerLine>(b.lines.size)
        fun add(bl: BidLine, dropped: Boolean = false) {
            if (!seen.add(bl.line.outcomeId)) return
            var l = bl.line
            // A side the phone has a bid on that the newest COMPLETE list no longer carries stopped passing the bid rules at the engine (the fair moved, the player is out, its window closed): the same line the
            // local scan would have skipped, so the desk takes the bid down. A running scan's list (partial) only says that league is not priced yet, which keeps the bid up as it always did.
            if (dropped) l = l.copy(unavailable = l.unavailable ?: DROPPED)
            books[l.marketId]?.takeIf { l.bookAtMs == null || it.fetchedAtMs > l.bookAtMs!! }?.let { l = MakerLines.withBook(l, it) }
            val at = l.bookAtMs ?: return
            val old = if (rules.volume && l.startsTs - now >= 6 * 3_600_000L) MakerLines.VOLUME_FAR_BOOK_AGE_MS else MakerLines.MAX_BOOK_AGE_MS
            if (now - at > old) return
            out += l.copy(fairOld = l.fairAsOfMs != null && now - l.fairAsOfMs!! > Freshness.maxAgeMs(bl.eventStartsTs, now))
        }
        b.lines.forEach(::add)
        for ((id, bl) in kept) if (id !in seen) add(bl, dropped = !b.partial)
        return out
    }

    /**
     * Reads Novig's books for the candidates whose Novig side is older than [REFRESH_AFTER_MS] at [now] (or has none), soonest game first, at most [maxReads] markets: the stream's complete books first
     * (free), the rest in one paced batch. Returns the markets read. A failed read leaves the engine's book in place (judged by its age as ever) and says why ([problem]).
     */
    suspend fun refresh(now: Long, maxReads: Int): Int {
        val b = bids ?: return 0
        val wanted = LinkedHashSet<String>()
        for (bl in b.lines) {
            val at = books[bl.line.marketId]?.fetchedAtMs ?: bl.line.bookAtMs
            if (at == null || now - at > refreshAfterMs(bl.line.startsTs, now)) wanted += bl.line.marketId
            if (wanted.size >= maxReads) break
        }
        if (wanted.isEmpty()) return 0
        var read = 0
        try {
            val held = novig.pushed(wanted)
            held.forEach { (id, bk) -> books[id] = bk }
            read += held.size
            val rest = wanted.filter { it !in held }
            if (rest.isNotEmpty()) {
                val batch = novig.books(rest)
                batch.books.forEach { (id, bk) -> books[id] = bk }
                read += batch.books.size
                problem = batch.lastError?.takeIf { batch.books.isEmpty() }
            } else problem = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            problem = e.message?.take(100) ?: e.javaClass.simpleName
        }
        // Books of markets no candidate asks for any more go.
        val live = b.lines.mapTo(HashSet()) { it.line.marketId }
        books.keys.removeIf { it !in live }
        lastReadAtMs = clock(); lastReads = read
        return read
    }

    /** How many of the candidates' Novig sides are as new as [REFRESH_AFTER_MS] at [now], and how many there are: Diagnostics' "books ready". */
    fun readiness(now: Long): Pair<Int, Int> {
        val b = bids ?: return 0 to 0
        val fresh = b.lines.count { bl -> (books[bl.line.marketId]?.fetchedAtMs ?: bl.line.bookAtMs)?.let { now - it <= refreshAfterMs(bl.line.startsTs, now) } == true }
        return fresh to b.lines.size
    }

    /**
     * What Diagnostics says about the bid desk's data on GitHub: the candidate list's age and size, how many candidates have a Novig side as new as [REFRESH_AFTER_MS], the last read and its problem.
     * [engine]: the engine's own account of the list it published (null = no heartbeat read yet).
     */
    fun report(now: Long, engine: CloudStatus?, rules: MakerRules): List<String> = buildList {
        val b = bids
        if (b == null) add("no candidate list has reached this phone yet" + (engine?.let { if (it.bidsAtMs > 0) " (the engine says it published one ${(now - it.bidsAtMs) / 1000}s ago: not fetched, or unreadable)" else " (the engine has not published one)" } ?: " (no engine heartbeat read)"))
        else {
            val (fresh, total) = readiness(now)
            add("candidate list: ${b.lines.size} lines made ${(now - b.atMs) / 1000}s ago at settings rev ${b.configRev}" + (if (b.partial) " from a scan still running" else " from a finished scan") + (if (b.unreadable > 0) " · ${b.unreadable} lines unreadable" else "") + (engine?.takeIf { it.bidsBytes > 0 }?.let { " · file ${it.bidsBytes / 1024} KB" } ?: ""))
            add("Novig sides as new as the lane wants ($fresh of $total; 90 s for a game under 6 h away, 4.5 min under 48 h, 9 min further out) · last read ${if (lastReadAtMs > 0) "${(now - lastReadAtMs) / 1000}s ago, ${lastReads} markets" else "none yet"}" + (problem?.let { " · PROBLEM: $it" } ?: ""))
            add("lines the desk can judge now: ${lines(now, rules, prune = false).size} (a Novig side older than ${MakerLines.MAX_BOOK_AGE_MS / 60_000} min is no line; the fair's age is judged on this phone's clock)")
        }
        engine?.let { add("engine: candidate pass ${if (it.bidsAtMs > 0) "${(now - it.bidsAtMs) / 1000}s ago, ${it.bidsCount} lines" else "none published"} · heartbeat ${(now - it.heartbeatMs) / 1000}s ago") }
    }

    companion object {
        /** A candidate's Novig side older than this is read again (a game under [NEAR_MS] away). */
        const val REFRESH_AFTER_MS = 90_000L

        /**
         * How old a candidate's Novig side may be before it is read again, by how near its game is: a bid only needs a book under [MakerLines.MAX_BOOK_AGE_MS] old (it says whether the bid would cross the offer and what
         * is already bid), and a game days away moves slowly, so the far ones are read less often and the key's reads (about 14 a second) go to the games that fill first.
         */
        fun refreshAfterMs(startsTs: Long, now: Long): Long = when {
            startsTs - now < NEAR_MS -> REFRESH_AFTER_MS
            startsTs - now < MID_MS -> 3 * REFRESH_AFTER_MS
            else -> 6 * REFRESH_AFTER_MS
        }
        const val NEAR_MS = 6 * 3_600_000L
        const val MID_MS = 48 * 3_600_000L

        /** Why a side the phone has a bid on is no line any more: GitHub's complete list dropped it. */
        const val DROPPED = "GitHub's candidate list no longer carries this side (it stopped passing the bid rules)"

    }
}
