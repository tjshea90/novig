package com.tjshea.vigilant.data.cloud

import com.tjshea.vigilant.data.scanner.ApiGates
import com.tjshea.vigilant.data.scanner.CloudTradeMode
import com.tjshea.vigilant.data.scanner.ScanSettings
import com.tjshea.vigilant.data.scanner.TailLiveSettings
import com.tjshea.vigilant.data.tracker.TrackedBet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLiveLaneTest {
    private fun s(mode: CloudTradeMode, tail: TailLiveSettings, cloud: Boolean = true) =
        ScanSettings(tailLive = tail, gates = ApiGates(cloudScan = cloud, cloudTrading = mode))

    @Test fun laneRunsOnlyWhenEngineDrivesTradesAndTailIsOn() {
        val on = TailLiveSettings(on = true)
        assertTrue(s(CloudTradeMode.PAPER, on).engineLive)
        assertTrue(s(CloudTradeMode.LIVE, on).engineLive)
        assertFalse(s(CloudTradeMode.OFF, on).engineLive)
        assertFalse(s(CloudTradeMode.LIVE, on, cloud = false).engineLive)
        assertFalse(s(CloudTradeMode.LIVE, on.copy(on = false)).engineLive)
        assertFalse(s(CloudTradeMode.LIVE, on.copy(engine = false)).engineLive)
        assertFalse(s(CloudTradeMode.LIVE, on).copy(killed = true).engineLive)
    }

    @Test fun realOrdersNeedBothTheTailBuyAndTheLiveSwitch() {
        val buy = TailLiveSettings(on = true, bet = true)
        assertTrue(CloudLiveLane.realOrders(s(CloudTradeMode.LIVE, buy)))
        assertFalse(CloudLiveLane.realOrders(s(CloudTradeMode.PAPER, buy)))
        assertFalse(CloudLiveLane.realOrders(s(CloudTradeMode.LIVE, buy.copy(bet = false))))
    }

    @Test fun bidsFollowTheBidsSwitchOnlyWhileTheLaneIsOn() {
        assertTrue(CloudLiveLane.bidsOn(s(CloudTradeMode.PAPER, TailLiveSettings(on = true, bids = true))))
        assertFalse(CloudLiveLane.bidsOn(s(CloudTradeMode.PAPER, TailLiveSettings(on = true, bids = false))))
        assertFalse(CloudLiveLane.bidsOn(s(CloudTradeMode.OFF, TailLiveSettings(on = true, bids = true))))
    }

    @Test fun aFillBecomesALedgerEntryThatIsNotABid() {
        val bet = TrackedBet(
            id = "b1", createdAtMs = 5L, league = "NBA", eventName = "A at B", startsTs = 9L, marketLabel = "Total", selection = "Under 200.5", marketId = "m", outcomeId = "o",
            price = 0.9, cost = 0.95, fairAtBet = 0.97, evPercentAtBet = 0.05, stake = 0.95, contracts = 1L, fee = 0.02, orderId = "ord",
        )
        val e = CloudLiveLane.entry(bet, "live tail bet")
        assertEquals("live-b1", e.id)
        assertEquals(1L, e.contracts)
        assertEquals(0.95, e.cost, 1e-9)
        assertFalse(e.note.startsWith("bid"))
    }

    @Test fun statusSaysLiveOnlyWhileTheHeartbeatIsFresh() {
        val st = CloudStatus(heartbeatMs = 1_000_000L, liveActive = true)
        assertTrue(st.liveLive(1_000_000L + 60_000L))
        assertFalse(st.liveLive(1_000_000L + CloudStatus.DESK_STALE_MS + 1))
    }

    @Test fun theSoftBookTakerAndBidsStayPaperUntilRealIsAllowedOnTheAverage() {
        val live = ScanSettings(pinnLive = true, pinnLiveBet = true, liveBid = true, liveBidReal = true, gates = ApiGates(cloudScan = true, cloudTrading = CloudTradeMode.LIVE))
        assertTrue(live.engineLive)
        assertTrue(CloudPinnLane.takerOn(live))
        assertFalse(CloudPinnLane.takerReal(live))
        assertFalse(CloudPinnLane.bidsReal(live))
        val allowed = live.copy(gates = live.gates.copy(boltLiveReal = true))
        assertTrue(CloudPinnLane.takerReal(allowed))
        assertTrue(CloudPinnLane.bidsReal(allowed))
        assertFalse(CloudPinnLane.takerReal(allowed.copy(gates = allowed.gates.copy(cloudTrading = CloudTradeMode.PAPER))))
        assertFalse(CloudPinnLane.wanted(ScanSettings(gates = ApiGates(cloudScan = true, cloudTrading = CloudTradeMode.LIVE))))
    }
}
