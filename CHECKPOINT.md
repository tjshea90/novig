# CHECKPOINT 4810 — read me first, then TASKS.md

**Written:** 2026-11-21T04:02:15Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `4a1fd76e7` (this checkpoint is the commit after it)

## Just done
ON2 DEPLOYED: loss-stop fix live (run 44863668546: maker connected, not halted, true 24h -$67.18 kept in status); engine LIVE trading + bids; monitoring armed (send_later trig_01FVuZ2aBWyyo6FBzxDoTZWb, re-arm every 45 min)

## Do this next
at each check-in: status.json (heartbeat, desk, rfq halted/quotes/fills), engine-log errors (Novig RATE_LIMIT/EOF seen once 03:48), ParlayAPI credits 6%; judge the profile only on 20+ of its own settled fills; FL1 flaky test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  4a1fd76e7 ckpt 4809: ON2: RfqConfig.lossStopFromMs + RfqLedger.Summary.pnlStop24h: the maker's l
  f0f3752f8 ckpt 4808: ON1 DONE: engine LIVE (trading, bids via maker=true, scan on), desk resting
  c512d817d ckpt 4807: ON1: engine adopted gates.cloudTrading=LIVE (status tradeMode live); found 
  348e10fef ckpt 4806: ON1: engine_set gates.cloudTrading=LIVE gates.cloudBids=true vigGuards.bidD
  ba8689716 ckpt 4805: ENGINEER 11-20 routine: watch only, nothing to change
  da720cd8b ckpt 4804: full :data tests run for WalletBetMoney fix
  d5d578a96 ckpt 4803: WalletBetMoney: graded win with no Novig payout yet no longer shows as a fu
  520f0318f ckpt 4802: NT2 SHIPPED: v1.0.148 (release green, recorded)
  c87fa9036 ckpt 4801: pre-release: NT2: the NCAAB tab no longer lists small college games with no
  6e782d76a ckpt 4800: NT2: the NCAAB tab leaves out games with no betting odds (no Novig market, 
```
