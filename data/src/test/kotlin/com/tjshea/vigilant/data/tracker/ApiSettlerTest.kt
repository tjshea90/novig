package com.tjshea.vigilant.data.tracker

import com.tjshea.vigilant.data.novig.BookBatch
import com.tjshea.vigilant.data.novig.NovigEvent
import com.tjshea.vigilant.data.novig.NovigMarket
import com.tjshea.vigilant.data.novig.NovigOutcome
import com.tjshea.vigilant.data.novig.NovigSource
import com.tjshea.vigilant.data.novig.signing.NovigSignedClient
import com.tjshea.vigilant.data.novig.signing.PemSigningKey
import com.tjshea.vigilant.data.novig.trading.BetTarget
import com.tjshea.vigilant.data.novig.trading.NovigFill
import com.tjshea.vigilant.data.novig.trading.NovigTradingClient
import com.tjshea.vigilant.engine.MarketFee
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * Grading the bets placed through Novig's API from Novig's own ledger (Tj, 2026-09-29: "include the API grading bets feature for the tracker
 * system"), against a mock Novig speaking the documented ledger and positions routes (NOVIG_API.md §14-15).
 */
class ApiSettlerTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private val pair = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }.generateKeyPair()
    private val pem = "-----BEGIN PRIVATE KEY-----\n" + // FAKE: wraps a key generated fresh by this test run
        Base64.getEncoder().encodeToString(PrivateKeyInfoFactory.createPrivateKeyInfo(pair.private).encoded) + "\n-----END PRIVATE KEY-----"

    private val start = 1_800_000_000_000L
    private var now = start + 5 * 3_600_000L
    private val hour = 3_600_000L

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun tracker() = BetTracker(File.createTempFile("bets", ".json").also { it.delete() }, clock = { now })
    private fun trading() = NovigTradingClient(NovigSignedClient(OkHttpClient(), json, PemSigningKey("kid", pem), server.url("").toString().trimEnd('/')), json)

    private val market = NovigMarket(
        "mkt", "ev", "MONEY", "OPEN", "Team A vs Team B", start, MarketFee.GAME,
        listOf(NovigOutcome("A", "Team A", "TBD"), NovigOutcome("B", "Team B", "TBD")),
    )

    private fun target(key: String? = "k") = BetTarget(
        market, "A", "NFL", "Team B @ Team A", start, "Moneyline", "Team A", fair = 0.5, fairAsOfMs = null, source = BetTracker.SOURCE_VIGILANT, placedKey = key,
    )

    /** 400 contracts for $1.85: a win pays $4.00. */
    private fun fill(orderId: String = "o1", cost: String = "1.85000", ts: Long = start - hour) =
        NovigFill("f-$orderId", orderId, null, "mkt", "A", 400, cost.toDouble(), true, 0.0, ts)

    private suspend fun bet(t: BetTracker, orderId: String = "o1"): TrackedBet {
        t.logApi(target(key = "k-$orderId"), orderId, listOf(fill(orderId)))
        return t.all().first { it.orderId == orderId }
    }

    /** Novig's side: [ledger] SETTLEMENT rows as (ref, amount), [held] the positions still held. */
    private fun novig(ledger: List<Pair<String, String>> = emptyList(), held: Boolean = false, down: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                if (down) return MockResponse().setResponseCode(503)
                return when {
                    path.contains("/transactions") -> MockResponse().setBody(
                        """{"items":[${ledger.mapIndexed { i, (ref, amt) -> """{"transactionId":"t$i","kind":"SETTLEMENT","amount":"$amt","ref":"$ref","ts":1}""" }.joinToString(",")}]}""",
                    )
                    path.startsWith("/v3/portfolio/positions") -> MockResponse().setBody(if (held) """[{"marketId":"mkt","outcomeId":"A","qty":400,"cost":"1.85000"}]""" else "[]")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    private fun settler(t: BetTracker, feed: suspend (TrackedBet) -> BetGrader.Grade? = { null }) = ApiSettler(t, trading(), "sub", feed, clock = { now })

    @Test
    fun `a payout of a full win settles the bet as won with Novig as the grader`() = runBlocking {
        novig(ledger = listOf("mkt" to "4.00000"))
        val t = tracker(); bet(t)
        val r = settler(t).run()
        assertEquals(ApiSettler.Report(1, 1, 0, 0), r)
        val b = t.all().single()
        assertEquals(BetStatus.WON, b.status)
        assertEquals(BetSettler.BY_NOVIG, b.settledBy)
        assertEquals("Novig paid $4.00 on 400 contracts: a win", b.gradeNote)
        assertEquals(2.15, b.profit!!, 1e-9)
        // The request asked for SETTLEMENT rows around the game's start (a wide window: Novig's own start can differ from CNO's).
        val ask = server.takeRequest().path!!
        assertTrue(ask, ask.contains("kind=SETTLEMENT") && ask.contains("startsAfter=${start - ApiSettler.START_SLACK_MS}") && ask.contains("startsBefore=${start + ApiSettler.START_SLACK_MS}"))
    }

    @Test
    fun `a ledger row that names the fill or the order settles the bet as well as one naming the market`() = runBlocking {
        novig(ledger = listOf("f-o1" to "4.00000"))
        val t = tracker(); bet(t)
        assertEquals(BetStatus.WON, run { settler(t).run(); t.all().single().status })
        novig(ledger = listOf("o1" to "4.00000"))
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        assertEquals(BetStatus.WON, t2.all().single().status)
    }

    @Test
    fun `getting the cost back is a push, and any other payout is a fair-value settlement with its exact profit`() = runBlocking {
        novig(ledger = listOf("mkt" to "1.85000"))
        val t = tracker(); bet(t)
        settler(t).run()
        assertEquals(BetStatus.PUSH, t.all().single().status)
        assertEquals(0.0, t.all().single().profit!!, 1e-9)

        novig(ledger = listOf("mkt" to "2.10000"))
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        val fmv = t2.all().single()
        assertEquals(BetStatus.FMV, fmv.status)
        assertEquals(2.10 / 4.00, fmv.settleValue!!, 1e-9)
        assertEquals(2.10 - 1.85, fmv.profit!!, 1e-9)
        assertTrue(fmv.gradeNote!!.startsWith("Novig settled it at a fair value: paid $2.10"))
    }

    @Test
    fun `two bets on one side of a market share the market's single payout - each is a win, not a fair-value void`() = runBlocking {
        // 800 contracts across two bets pay $8.00 in one row for the market: judged on their total, never against one bet's $4.00.
        novig(ledger = listOf("mkt" to "8.00000"))
        val t = tracker(); bet(t, "o1"); bet(t, "o2")
        val r = settler(t).run()
        assertEquals(ApiSettler.Report(2, 2, 0, 0), r)
        assertTrue(t.all().all { it.status == BetStatus.WON && it.settledBy == BetSettler.BY_NOVIG })

        // The same two bets at different prices, paid their cost back (a push): still a push each, not a fair-value split.
        val t2 = tracker()
        t2.logApi(target("a"), "o1", listOf(fill("o1", cost = "1.00000")))
        t2.logApi(target("b"), "o2", listOf(fill("o2", cost = "2.00000")))
        novig(ledger = listOf("mkt" to "3.00000"))
        settler(t2).run()
        assertTrue(t2.all().all { it.status == BetStatus.PUSH })

        // A fair-value void of the pair gives both the same fraction of a full win.
        val t3 = tracker(); bet(t3, "o1"); bet(t3, "o2")
        novig(ledger = listOf("mkt" to "4.00000"))
        settler(t3).run()
        assertTrue(t3.all().all { it.status == BetStatus.FMV && kotlin.math.abs(it.settleValue!! - 0.5) < 1e-9 })
    }

    @Test
    fun `bets on both sides of a market are told apart only when the payout is exactly one side's win`() = runBlocking {
        val t = tracker()
        t.logApi(target("a"), "o1", listOf(fill("o1")))
        val other = target("b").copy(outcomeId = "B", selection = "Team B")
        t.logApi(other, "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 500, 2.0, true, 0.0, start - hour)))
        // Side A's 400 contracts win $4.00: A won, B lost.
        novig(ledger = listOf("mkt" to "4.00000"))
        settler(t).run()
        assertEquals(BetStatus.WON, t.all().single { it.orderId == "o1" }.status)
        assertEquals(BetStatus.LOST, t.all().single { it.orderId == "o2" }.status)

        // A payout that matches neither side is left to a tap with a note, and nothing is guessed.
        val t2 = tracker()
        t2.logApi(target("a"), "o1", listOf(fill("o1")))
        t2.logApi(other, "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 500, 2.0, true, 0.0, start - hour)))
        novig(ledger = listOf("mkt" to "3.30000"))
        val r = settler(t2).run()
        assertEquals(2, r.manual)
        assertTrue(t2.all().all { it.status == BetStatus.PENDING && it.gradeManual })
    }

    @Test
    fun `a locked market (both sides held equally) is graded from the score feeds, its push from the cost paid back, and a feed that disagrees with Novig is left to a tap`() = runBlocking {
        // 400 contracts of A for $1.85 and the lock: 400 of B for $2.00. Novig pays $4.00 for the market whichever side won (Tj, 2026-10-02: full tests).
        val lockTarget = target("b").copy(outcomeId = "B", selection = "Team B", lockFor = "pick")
        fun locked(t: BetTracker) = runBlocking {
            t.logApi(target("a"), "o1", listOf(fill("o1")))
            t.logApi(lockTarget, "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 400, 2.0, true, 0.0, start - hour)))
        }
        val t = tracker(); locked(t)
        novig(ledger = listOf("mkt" to "4.00000"))
        // The feeds grade the pick (Team A) a loss: B won.
        val r = settler(t) { b -> if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.LOST, "Final: Team B 24, Team A 17") else null }.run()
        assertEquals(ApiSettler.Report(2, 2, 0, 0), r)
        assertEquals(BetStatus.LOST, t.all().single { it.orderId == "o1" }.status)
        assertEquals(BetStatus.WON, t.all().single { it.orderId == "o2" }.status)
        assertTrue(t.all().single { it.orderId == "o2" }.gradeNote!!.contains("Final: Team B 24, Team A 17"))
        // The money is what Novig paid: $4.00 back on $3.85.
        assertEquals(0.15, t.all().sumOf { it.profit!! }, 1e-9)

        // Every side's cost paid back: a push, both of them.
        val t2 = tracker(); locked(t2)
        novig(ledger = listOf("mkt" to "3.85000"))
        settler(t2).run()
        assertTrue(t2.all().all { it.status == BetStatus.PUSH })

        // No feed to say which side won: left to a tap, as before (the money is the same either way).
        val t3 = tracker(); locked(t3)
        novig(ledger = listOf("mkt" to "4.00000"))
        assertEquals(2, settler(t3).run().manual)
        assertTrue(t3.all().all { it.status == BetStatus.PENDING && it.gradeManual })

        // A payout that isn't the winner's win (a fair-value void of unequal holdings) isn't forced into a win.
        val t4 = tracker()
        t4.logApi(target("a"), "o1", listOf(fill("o1")))
        t4.logApi(lockTarget, "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 500, 2.5, true, 0.0, start - hour)))
        novig(ledger = listOf("mkt" to "4.70000"))
        assertEquals(2, settler(t4) { b -> if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.WON, "Final") else null }.run().manual)
    }

    @Test
    fun `a market Novig hasn't paid and still holds is waiting, and a day later it's flagged`() = runBlocking {
        novig(held = true)
        val t = tracker(); bet(t)
        val r = settler(t).run()
        assertEquals(1, r.waiting)
        assertEquals(BetStatus.PENDING, t.all().single().status)
        assertEquals("Novig hasn't settled this market yet", t.all().single().gradeNote)
        assertFalse(t.all().single().gradeManual)
        now = start + 26 * hour
        val late = settler(t).run()
        assertEquals(1, late.manual)
        assertTrue(t.all().single().gradeNote!!.contains("a day after the game"))
        assertTrue(t.all().single().gradeManual)
    }

    @Test
    fun `no payout and no position is a loss, but the score feeds are asked first`() = runBlocking {
        novig()
        // The feeds say lost: settled as a loss with their evidence.
        val t = tracker(); bet(t)
        settler(t) { BetGrader.Grade.Result(BetStatus.LOST, "Final: Team B 24, Team A 17") }.run()
        assertEquals(BetStatus.LOST, t.all().single().status)
        assertTrue(t.all().single().gradeNote!!.startsWith("Novig paid nothing for it and no longer holds the position: a loss (Final: Team B 24"))
        assertEquals(-1.85, t.all().single().profit!!, 1e-9)

        // The feeds say WON while Novig paid nothing: left to a tap.
        val t2 = tracker(); bet(t2)
        val r2 = settler(t2) { BetGrader.Grade.Result(BetStatus.WON, "Final: Team A 31, Team B 17") }.run()
        assertEquals(1, r2.manual)
        assertEquals(BetStatus.PENDING, t2.all().single().status)
        assertTrue(t2.all().single().gradeNote!!.contains("the score feeds say won") && t2.all().single().gradeManual)

        // No readable feed: waits, and takes the loss only six hours after the start.
        val t3 = tracker(); bet(t3)
        assertEquals(1, settler(t3).run().waiting)
        assertEquals(BetStatus.PENDING, t3.all().single().status)
        now = start + 7 * hour
        settler(t3).run()
        assertEquals(BetStatus.LOST, t3.all().single().status)
    }

    /** The locked pair of Tj's Ollie Gordon market: 400 contracts of A ($1.85) and the lock, 400 of B ($2.00); $4.00 pays whichever wins, so +$0.15. */
    private fun lockedPair(t: BetTracker) = runBlocking {
        t.logApi(target("a"), "o1", listOf(fill("o1")))
        t.logApi(target("b").copy(outcomeId = "B", selection = "Team B", lockFor = "pick"), "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 400, 2.0, true, 0.0, start - hour)))
    }

    @Test
    fun `a market held on both sides is never graded lost from Novig's silence alone - the winning leg stays open for the payout or a tap`() = runBlocking {
        // Tj, 2026-10-05: Over 29.5 won (100 yards), Novig's ledger showed nothing for it, and six hours after the start it was graded lost, so the locked
        // market read as losing both legs (-$3.00 on a lock worth +$0.12). The feeds read the pick (Team A lost: B won) but not the lock's own wording.
        novig()
        val t = tracker(); lockedPair(t)
        val feed: suspend (TrackedBet) -> BetGrader.Grade? = { b -> if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.LOST, "Final: Team B 24, Team A 17") else null }
        settler(t, feed).run()
        // Three hours after the start the lock is waiting, the pick is graded.
        assertEquals(BetStatus.LOST, t.all().single { it.orderId == "o1" }.status)
        assertEquals(BetStatus.PENDING, t.all().single { it.orderId == "o2" }.status)
        now = start + 7 * hour
        val r = settler(t, feed).run()
        assertEquals(1, r.manual)
        val lock = t.all().single { it.orderId == "o2" }
        assertEquals(BetStatus.PENDING, lock.status)
        assertTrue(lock.gradeManual && lock.gradeNote == ApiSettler.BOTH_HELD_SILENT)
        // The Locked card: nothing graded yet, so it reads what the lock is worth, never a double loss.
        val stats = LockedBets.stats(t.all())
        assertEquals(0.15, stats.profit, 1e-9)
        assertEquals(0, stats.graded)

        // The payout turns up in the ledger later: the lock is graded won by it, and the market made +$0.15.
        novig(ledger = listOf("mkt" to "4.00000"))
        settler(t, feed).run()
        assertEquals(BetStatus.WON, t.all().single { it.orderId == "o2" }.status)
        assertEquals(0.15, t.all().sumOf { it.profit!! }, 1e-9)

        // A single bet (no other side held) still takes the loss from silence after six hours.
        novig()
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        assertEquals(BetStatus.LOST, t2.all().single().status)
    }

    @Test
    fun `a pick with a small hedge on the other side is guarded the same way - Tj's Tuten rushing market, 232 contracts of the Under and 1 of the Over`() = runBlocking {
        novig()
        fun held(t: BetTracker) = runBlocking {
            t.logApi(target("a"), "o1", listOf(NovigFill("f-o1", "o1", null, "mkt", "A", 232, 1.0788, true, 0.0, start - hour)))
            t.logApi(target("b").copy(outcomeId = "B", selection = "Over 53.5"), "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 1, 0.00465, true, 0.0, start - hour)))
        }
        val t = tracker(); held(t)
        now = start + 7 * hour
        val feed: suspend (TrackedBet) -> BetGrader.Grade? = { b -> if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.LOST, "Tuten ran 73 yards") else null }
        val r = settler(t, feed).run()
        assertEquals(BetStatus.LOST, t.all().single { it.orderId == "o1" }.status)
        assertEquals(BetStatus.PENDING, t.all().single { it.orderId == "o2" }.status)
        assertEquals(1, r.manual)

        // What Tj's phone holds: the 1-contract Over graded lost from silence. Taken back.
        val t2 = tracker(); held(t2)
        t2.editMany(t2.all().associate { b ->
            b.id to { x: TrackedBet ->
                x.copy(status = BetStatus.LOST, settledAtMs = now, settledBy = BetSettler.BY_NOVIG, gradeNote = if (x.orderId == "o2") ApiSettler.SILENT_LOSS else "${ApiSettler.SILENT_LOSS} (Tuten ran 73 yards)")
            }
        })
        assertEquals(1, settler(t2, feed).run().reopened)
        assertEquals(BetStatus.PENDING, t2.all().single { it.orderId == "o2" }.status)
        assertEquals(BetStatus.LOST, t2.all().single { it.orderId == "o1" }.status)

        // A market with a Draw side is three-way: two of its sides can both lose, so silence still grades a loss there.
        val t3 = tracker()
        runBlocking {
            t3.logApi(target("a").copy(selection = "Team A"), "o1", listOf(fill("o1")))
            t3.logApi(target("b").copy(outcomeId = "B", selection = "Draw"), "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 100, 0.3, true, 0.0, start - hour)))
        }
        settler(t3).run()
        assertTrue(t3.all().all { it.status == BetStatus.LOST })
    }

    /** Tj's Ollie Gordon market: the pick ("Ollie Gordon Under 29.5", 400 contracts of A) and the imported lock ("Over 29.5", 400 of B) on [line]. */
    private fun linePair(t: BetTracker, pick: String = "Ollie Gordon Under 29.5", lock: String = "Over 29.5") = runBlocking {
        t.logApi(target("a").copy(selection = pick, marketLabel = "Player Rushing Yards"), "o1", listOf(fill("o1")))
        t.logApi(target("b").copy(outcomeId = "B", selection = lock, marketLabel = "Player Rushing Yards", lockFor = "pick"), "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 400, 2.0, true, 0.0, start - hour)))
    }

    private val pickLostLockUnreadable: suspend (TrackedBet) -> BetGrader.Grade? = { b ->
        if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.LOST, "Gordon ran for 100 yards") else null
    }

    @Test
    fun `a half-point two-way market whose other leg a score feed graded lost grades the lock leg won - six hours after the start, with the feed's words`() = runBlocking {
        // Tj, 2026-10-07 (rec 11): the feeds read the pick, not the imported lock's own wording ("Over 29.5"), so the lock waited for Novig's ledger or a tap.
        novig()
        val t = tracker(); linePair(t)
        // Three hours after the start: the pick is graded, the lock waits (Novig may still pay it).
        settler(t, pickLostLockUnreadable).run()
        assertEquals(BetStatus.LOST, t.all().single { it.orderId == "o1" }.status)
        assertEquals(BetStatus.PENDING, t.all().single { it.orderId == "o2" }.status)
        // Six hours after: nothing paid it, so the feed's loss of the other leg is the answer.
        now = start + 7 * hour
        val r = settler(t, pickLostLockUnreadable).run()
        assertEquals(1, r.settled); assertEquals(0, r.manual)
        val lock = t.all().single { it.orderId == "o2" }
        assertEquals(BetStatus.WON, lock.status)
        assertEquals(BetSettler.BY_NOVIG, lock.settledBy)
        assertEquals("${ApiSettler.INFERRED_WON} (Gordon ran for 100 yards)", lock.gradeNote)
        assertFalse(lock.gradeManual)
        // The market made +$0.15 (the lock leg's $4.00 win less the $3.85 both legs cost), not a double loss.
        assertEquals(0.15, t.all().sumOf { it.profit!! }, 1e-9)
    }

    @Test
    fun `the lock leg is graded in the same pass that grades the pick, and a spread's half point counts too`() = runBlocking {
        novig()
        now = start + 7 * hour
        val t = tracker(); linePair(t)
        val r = settler(t, pickLostLockUnreadable).run()
        assertEquals(2, r.settled)
        assertEquals(setOf(BetStatus.LOST, BetStatus.WON), t.all().map { it.status }.toSet())
        // A spread: "Team A -3.5" / "Team B +3.5".
        val t2 = tracker(); linePair(t2, "Team A -3.5", "Team B +3.5")
        settler(t2, pickLostLockUnreadable).run()
        assertEquals(BetStatus.WON, t2.all().single { it.orderId == "o2" }.status)
    }

    @Test
    fun `a whole-number line, a moneyline, a silent loss and a result Tj tapped never grade the other leg won`() = runBlocking {
        novig()
        now = start + 7 * hour
        // 29 can push, so one leg losing doesn't mean the other won.
        val t1 = tracker(); linePair(t1, "Ollie Gordon Under 29", "Over 29")
        settler(t1, pickLostLockUnreadable).run()
        assertEquals(BetStatus.PENDING, t1.all().single { it.orderId == "o2" }.status)
        assertEquals(ApiSettler.BOTH_HELD_SILENT, t1.all().single { it.orderId == "o2" }.gradeNote)
        // A moneyline has no line at all.
        val t2 = tracker(); linePair(t2, "Team A", "Team B")
        settler(t2, pickLostLockUnreadable).run()
        assertEquals(BetStatus.PENDING, t2.all().single { it.orderId == "o2" }.status)
        // The other leg lost from Novig's silence alone (no feed evidence): not proof.
        val t3 = tracker(); linePair(t3)
        t3.editMany(mapOf(t3.all().single { it.orderId == "o1" }.id to { b: TrackedBet ->
            b.copy(status = BetStatus.LOST, settledAtMs = now, settledBy = BetSettler.BY_NOVIG, gradeNote = ApiSettler.SILENT_LOSS)
        }))
        settler(t3).run()
        assertEquals(BetStatus.PENDING, t3.all().single { it.orderId == "o2" }.status)
        // Tj tapped the other leg lost: his tap isn't a score feed's word, so this stays his to decide too.
        val t4 = tracker(); linePair(t4)
        t4.editMany(mapOf(t4.all().single { it.orderId == "o1" }.id to { b: TrackedBet ->
            b.copy(status = BetStatus.LOST, settledAtMs = now, settledBy = BetSettler.BY_YOU, gradeNote = "Marked lost by you")
        }))
        settler(t4).run()
        assertEquals(BetStatus.PENDING, t4.all().single { it.orderId == "o2" }.status)
        // A feed that lost both legs: the other-side-lost guard still wins (no win is invented from a misread).
        val t5 = tracker(); linePair(t5)
        val r5 = settler(t5) { BetGrader.Grade.Result(BetStatus.LOST, "Final: misread") }.run()
        assertEquals(1, r5.settled)
        assertEquals(ApiSettler.OTHER_SIDE_LOST, t5.all().single { it.status == BetStatus.PENDING }.gradeNote)
        // A three-way market (a Draw side) is not two-way.
        val t6 = tracker()
        runBlocking {
            t6.logApi(target("a").copy(selection = "Team A -0.5"), "o1", listOf(fill("o1")))
            t6.logApi(target("b").copy(outcomeId = "B", selection = "Draw"), "o2", listOf(NovigFill("f-o2", "o2", null, "mkt", "B", 100, 0.3, true, 0.0, start - hour)))
        }
        settler(t6, pickLostLockUnreadable).run()
        assertNotEquals(BetStatus.WON, t6.all().single { it.orderId == "o2" }.status)
    }

    @Test
    fun `a payout in Novig's ledger still wins over the inference`() = runBlocking {
        novig(ledger = listOf("mkt" to "4.00000"))
        now = start + 7 * hour
        val t = tracker(); linePair(t)
        settler(t, pickLostLockUnreadable).run()
        val lock = t.all().single { it.orderId == "o2" }
        assertEquals(BetStatus.WON, lock.status)
        assertTrue(lock.gradeNote!!.startsWith("Novig paid"))
    }

    @Test
    fun `a feed that says lost for both sides of a locked market grades only the first`() = runBlocking {
        novig()
        val t = tracker(); lockedPair(t)
        val r = settler(t) { BetGrader.Grade.Result(BetStatus.LOST, "Final: misread") }.run()
        assertEquals(1, r.settled)
        assertEquals(1, r.manual)
        assertEquals(1, t.all().count { it.status == BetStatus.LOST })
        assertTrue(t.all().single { it.status == BetStatus.PENDING }.gradeNote == ApiSettler.OTHER_SIDE_LOST)
    }

    @Test
    fun `both legs of a locked market graded lost are taken back (the silent one), and a result Tj tapped is left`() = runBlocking {
        novig()
        val t = tracker(); lockedPair(t)
        // What Tj's phone holds: the pick lost with the feeds' evidence, the lock lost from silence alone.
        t.editMany(mapOf(
            t.all().single { it.orderId == "o1" }.id to { b: TrackedBet ->
                b.copy(status = BetStatus.LOST, settledAtMs = now, settledBy = BetSettler.BY_NOVIG, gradeNote = "${ApiSettler.SILENT_LOSS} (Final: Team B 24, Team A 17)")
            },
            t.all().single { it.orderId == "o2" }.id to { b: TrackedBet ->
                b.copy(status = BetStatus.LOST, settledAtMs = now, settledBy = BetSettler.BY_NOVIG, gradeNote = ApiSettler.SILENT_LOSS)
            },
        ))
        assertEquals(-3.85, t.all().sumOf { it.profit!! }, 1e-9)
        now = start + 7 * hour
        val r = settler(t) { b -> if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.LOST, "Final") else null }.run()
        assertEquals(1, r.reopened)
        assertEquals(BetStatus.LOST, t.all().single { it.orderId == "o1" }.status)
        val lock = t.all().single { it.orderId == "o2" }
        assertEquals(BetStatus.PENDING, lock.status)
        assertNull(lock.settledAtMs)
        assertTrue(lock.gradeManual && lock.gradeNote == ApiSettler.BOTH_HELD_SILENT)
        // The card reads the lock's worth, not the double loss.
        assertEquals(0.15, LockedBets.stats(t.all()).profit, 1e-9)

        // Tj tapped the lock's loss himself: his result stands.
        val t2 = tracker(); lockedPair(t2)
        t2.editMany(t2.all().associate { b -> b.id to { x: TrackedBet -> x.copy(status = BetStatus.LOST, settledAtMs = now, settledBy = BetSettler.BY_YOU, gradeNote = "Marked lost by you") } })
        assertEquals(0, settler(t2).run().reopened)
        assertTrue(t2.all().all { it.status == BetStatus.LOST })

        // One leg lost and one won (the normal lock): nothing is touched.
        val t3 = tracker(); lockedPair(t3)
        novig(ledger = listOf("mkt" to "4.00000"))
        settler(t3) { b -> if (b.outcomeId == "A") BetGrader.Grade.Result(BetStatus.LOST, "Final") else null }.run()
        assertEquals(0, settler(t3).run().reopened)
        assertEquals(0.15, t3.all().sumOf { it.profit!! }, 1e-9)
    }

    @Test
    fun `when Novig can't be read nothing changes`() = runBlocking {
        novig(down = true)
        val t = tracker(); bet(t)
        val r = settler(t).run()
        assertTrue(r.stopped)
        assertEquals(BetStatus.PENDING, t.all().single().status)
    }

    @Test
    fun `a result Tj tapped is never overwritten, and other markets' payouts are ignored`() = runBlocking {
        novig(ledger = listOf("mkt" to "4.00000"))
        val t = tracker(); val b = bet(t)
        t.settle(b.id, BetStatus.LOST)
        assertEquals(0, settler(t).run().asked)
        assertEquals(BetStatus.LOST, t.all().single().status)
        novig(ledger = listOf("some-other-market" to "9.00000"), held = true)
        val t2 = tracker(); bet(t2)
        settler(t2).run()
        assertEquals(BetStatus.PENDING, t2.all().single().status)
    }

    @Test
    fun `an API bet is left to the ledger while betting is set up, and graded from scores otherwise`() = runBlocking {
        val t = tracker(); bet(t)
        val scores = object : ScoreSource {
            override fun covers(league: String) = false
            override suspend fun games(league: String, date: java.time.LocalDate): List<GameScore>? = emptyList()
            override suspend fun players(game: GameScore): List<PlayerLine>? = emptyList()
        }
        val leaving = BetSettler(t, scores, clock = { now }, leaveApiBets = { true })
        assertTrue(leaving.due(t.all(), now).isEmpty())
        val grading = BetSettler(t, scores, clock = { now }, leaveApiBets = { false })
        assertEquals(1, grading.due(t.all(), now).size)
    }

    // ---- syncing fills the Tracker never got -----------------------------------------------------------------

    private class FakeNovig(val market: NovigMarket?, val event: NovigEvent?) : NovigSource {
        override suspend fun events(leagues: Collection<String>, statuses: Collection<String>, startsBefore: Long?) = emptyList<NovigEvent>()
        override suspend fun markets(leagues: Collection<String>, marketTypes: Collection<String>, eventStatuses: Collection<String>, startsBefore: Long?) = emptyList<NovigMarket>()
        override suspend fun books(marketIds: Collection<String>, onProgress: ((Int, Int) -> Unit)?) = BookBatch(emptyMap(), 0, 0, 0)
        override suspend fun market(marketId: String) = market
        override suspend fun event(eventId: String) = event
    }

    private fun fills(vararg f: String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path!!.startsWith("/v3/portfolio/fills")) MockResponse().setBody("""{"items":[${f.joinToString(",")}]}""") else MockResponse().setResponseCode(404)
        }
    }

    private fun fillJson(orderId: String, ts: Long) =
        """{"fillId":"f-$orderId","orderId":"$orderId","marketId":"mkt","outcomeId":"A","qty":400,"cost":"1.85000","taker":true,"fee":"0.00000","ts":$ts}"""

    @Test
    fun `a fill the Tracker never got is added with no EV claimed, and known or old orders are skipped`() = runBlocking {
        val t = tracker()
        bet(t, "known")
        fills(fillJson("known", now - 60_000), fillJson("lost-answer", now - 60_000), fillJson("old", now - 3 * hour))
        val event = NovigEvent("ev", "FOOTBALL", "NFL", "OPEN_PREGAME", "Team B @ Team A", start)
        val sync = ApiBetSync(t, trading(), FakeNovig(market, event), clock = { now })
        val r = sync.run()
        assertEquals(ApiBetSync.Report(1, 0), r)
        val added = t.all().first { it.orderId == "lost-answer" }
        assertEquals("Team A", added.selection)
        assertEquals("Team B @ Team A", added.eventName)
        assertEquals("NFL", added.league)
        assertTrue(added.imported)
        assertNull(added.evPercentAtBet)
        assertEquals(400L, added.contracts)
        assertEquals(1.85, added.stake, 1e-9)
        assertTrue("the old order stayed out", t.all().none { it.orderId == "old" })
        // Syncing again adds nothing.
        assertEquals(0, sync.run().added)
        // "Sync with Novig" asks for everything: the old order comes in too.
        assertEquals(1, ApiBetSync(t, trading(), FakeNovig(market, event), clock = { now }).run(sinceMs = null).added)
    }

    @Test
    fun `a fill whose market Novig no longer lists is counted, not guessed`() = runBlocking {
        val t = tracker()
        fills(fillJson("gone", now - 60_000))
        val r = ApiBetSync(t, trading(), FakeNovig(null, null), clock = { now }).run()
        assertEquals(ApiBetSync.Report(0, 1), r)
        assertTrue(t.all().isEmpty())
    }
}
