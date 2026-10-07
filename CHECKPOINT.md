# CHECKPOINT 2715 — read me first, then TASKS.md

**Written:** 2026-10-07T21:40:31Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f242447d-q6sygj` · **builds on:** `54aede92` (this checkpoint is the commit after it)

## Just done
Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to GitHub: research/cno_bid_workflow_2026-10-07/ (FINDINGS.md readable, journal.jsonl raw). Notable: the live-probe scout could NOT measure CNO's 'Last Updated' age because the page ships 'Loading...' and fills it by a POST its rules forbade; it made 3 GETs, one connection reset, then stopped

## Do this next
workflow still running (wiring scout, synthesizer, critic): re-run python3 -I scratchpad/save_wf.py when it finishes and commit again; then measure CNO's Last Updated cadence myself with the app's own POST manners (few requests), settle the age verdict (RESEARCH §113), design, build

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  897ed404 ckpt 2711: pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) 
  6e640fc1 ckpt 2710: DJ4 + DJ5 done in the tree (Diagnostics + scan study split bets and bids; mu
  435bc978 ckpt 2709: DJ2 + DJ3 done in the tree: bid/bet rule + tagBids wiring, Tracker Bets/Bids
  7b4b02b7 ckpt 2708: DK1/DK2 done: researched CNO-only auto bids (RESEARCH §112; §111 stub for 
  29fe2dd7 ckpt 2707: DJ2 data layer written (TrackedBet.isBid, BetTracker.tagBids, BetOrBid, BetL
  544725eb ckpt 2706: DJ1 done: scouted the code (maker flag vs atBet.how, nothing reads both; Tra
  7a473229 ckpt 2705: Erased the off-topic fastboot/OTG question from INBOX.md (2 entries) and TAS
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
