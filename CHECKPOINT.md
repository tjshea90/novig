# CHECKPOINT 2711 — read me first, then TASKS.md

**Written:** 2026-10-07T20:26:54Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f242447d-q6sygj` · **builds on:** `cc19a89e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) says which a record is, older bid fills are tagged; the Tracker has a Both / Bets / Bids chip on both tabs for the lists and every stat, with a BID tag and EV posted on bids; Diagnostics and the scan study give bets and bids each their own block and splits, bids ended without a fill are counted by how, and a bid is no longer marked as Tj's own bet in the study (versionCode 132, v0.74.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.74.0), then run: bash tools/record-release.sh v0.74.0 132 "v0.74.0: bets and bids told apart. One rule (TrackedBet.isBid) says which a record is, older bid fills are tagged; the Tracker has a Both / Bets / Bids chip on both tabs for the lists and every stat, with a BID tag and EV posted on bids; Diagnostics and the scan study give bets and bids each their own block and splits, bids ended without a fill are counted by how, and a bid is no longer marked as Tj's own bet in the study"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6e640fc1 ckpt 2710: DJ4 + DJ5 done in the tree (Diagnostics + scan study split bets and bids; mu
  435bc978 ckpt 2709: DJ2 + DJ3 done in the tree: bid/bet rule + tagBids wiring, Tracker Bets/Bids
  7b4b02b7 ckpt 2708: DK1/DK2 done: researched CNO-only auto bids (RESEARCH §112; §111 stub for 
  29fe2dd7 ckpt 2707: DJ2 data layer written (TrackedBet.isBid, BetTracker.tagBids, BetOrBid, BetL
  544725eb ckpt 2706: DJ1 done: scouted the code (maker flag vs atBet.how, nothing reads both; Tra
  7a473229 ckpt 2705: Erased the off-topic fastboot/OTG question from INBOX.md (2 entries) and TAS
  ef2819e7 ckpt 2704: DJ (bets vs bids logging): TASKS.md DJ1-DJ6 written; scouting workflow wf_ec
  0e535d34 ckpt 2703: Tj's bets-vs-bids logging request written into TASKS.md (DJ1-DJ6)
  6195d011 ckpt 2702: v0.73.0 released and recorded; DI1-DI5 all done (v0.72.3, v0.72.4, v0.73.0)
  d800e268 ckpt 2701: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
