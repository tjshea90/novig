# CHECKPOINT 2716 — read me first, then TASKS.md

**Written:** 2026-10-07T21:42:01Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f242447d-q6sygj` · **builds on:** `c4b02728` (this checkpoint is the commit after it)

## Just done
DM1 findings so far written into TASKS.md (one page-wide CNO age, missing age read as fresh, CNO publishes every 13-33 s, owner says 1-2 min latency, list only holds rows +EV at Novig's ask, bid freshness = oldest input) and the NOTE that this container broke the 'never reads CNO' rule in a small way (4 GETs, no POSTs) and will read nothing more

## Do this next
wait for the workflow synthesis + critic (saver loop pushes results to research/cno_bid_workflow_2026-10-07/), then RESEARCH §113, DM2 design, DM3 build; age numbers come from the phone (log cnoAge, QuerySpeed, ServerUpdateDuration)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  897ed404 ckpt 2711: pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) 
  6e640fc1 ckpt 2710: DJ4 + DJ5 done in the tree (Diagnostics + scan study split bets and bids; mu
  435bc978 ckpt 2709: DJ2 + DJ3 done in the tree: bid/bet rule + tagBids wiring, Tracker Bets/Bids
  7b4b02b7 ckpt 2708: DK1/DK2 done: researched CNO-only auto bids (RESEARCH §112; §111 stub for 
  29fe2dd7 ckpt 2707: DJ2 data layer written (TrackedBet.isBid, BetTracker.tagBids, BetOrBid, BetL
  544725eb ckpt 2706: DJ1 done: scouted the code (maker flag vs atBet.how, nothing reads both; Tra
```
