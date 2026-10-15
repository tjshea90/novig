# Wallet "0" on GitHub, phone-vs-GitHub conflicts, the runner's address (Tj, 2026-10-15, TCL22)

Evidence read from `cloud-feed` (status.json, usage.json, engine-log.txt) and `cloud-config` history at 19:23-19:35 UTC; run 38780653142.

## 1. Why the wallet said 0
`status.walletCash = -1` means NOT READ (the phone showed it as 0). The engine's wallet probe (`CloudEngine.walletSleeping`) asks the balance at the start and then every 30 min, and the probe returns "not applicable" while the mode is not LIVE.
Timeline: engine start 17:53 (mode OFF) -> probe 19:02:25 (mode PAPER/OFF, so null) -> Tj set LIVE at 19:21:28 (cloud-config commit 1908f7757) -> the next probe was not due until 19:32. A pass that places no order never reads the wallet either
(`CloudTrader.pass` returns before the cash read when no candidate passes the rules; 25-33 of ~34 listed bets were held back every pass, mostly "starts in more than 6 h (trap guard)" and "no Novig price read in the last 3 minutes").
Fix (TCL22a): a probe that gave nothing is asked again after 60 s (`walletRetryMs`), not after 30 min; a read after the switch to LIVE is therefore within a minute. The dashboard says "not read yet: NOT zero".
Whether the trading key can actually read the balance (and from this runner's address) is proved only by the first LIVE read: look for `walletReadAtMs > 0` in status.json, or `balance read refused/failed` in engine-log.txt.

## 2. Phone vs GitHub: do they conflict?
- The phone does not scan while `cloudDrives` (VigilantApp: `sources = emptyList()` when cloud drives); it only reads the list, re-prices listed rows over its own Novig read key and runs the bid desk.
- Novig market stream: the phone's connection is on ITS read key, the engine's on the engine's own read key (`NOVIG_READ_KEY_*`): different `stream` buckets, no starvation.
- Orders: while `gates.cloudTrading != OFF` the phone's auto-bet stands down (`AutoBettor.kt:173`), so one decision = one order. The bid desk is unchanged and still orders with the subaccount's trading key, which the engine ALSO holds (same key, TCL20c): they share that key's REST rate bucket; they do not share a socket (the engine orders by REST only; the phone's order socket is its own). The engine cancels only orders carrying its own client ids (`cancelResting`), never the phone's bids.
- BoltOdds: Pro allows 3 sockets; the engine uses 2. The phone opens its own only when something reads it (sources are empty while the cloud drives; the live lane / streaming engine / Test button can). The engine's ledger shows one refusal at 19:26: "Disconnected due to slow consumption", the SERVER closing the engine's socket because the engine read too slowly, not a connection-limit refusal; it came back (`bolt 6+0` next scan).
- Verdict: no conflict found in the evidence; the one shared resource is the trading key's REST bucket. To be safe, keep BoltOdds' socket switch OFF on the phone while the cloud scan is on (Settings > APIs > Sockets).

## 3. Is the runner's address accepted?
For READS: yes. `novigRoute`: "Novig read-only key active: 16 reads a second, websocket on"; scan notes: "Novig refused none", ~12,000 Novig prices per scan (8,100 by the live feed), `addressHandovers = 0`.
For TRADING: not yet proved. The balance/order calls use the trading key; Novig's other check (the key holder's device: the Novig app opened on Tj's phone within 3 days, `GEOLOCATION_*`) is never touched by the engine, and a refusal there stops ordering for 30 min with the reason in `tradeStopped`.
