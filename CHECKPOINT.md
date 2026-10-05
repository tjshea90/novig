# CHECKPOINT 2551 — read me first, then TASKS.md

**Written:** 2026-10-05T16:59:26Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `44fa1a52` (this checkpoint is the commit after it)

## Just done
CK1/CK3 code: sharp-anchored bid price + Kelly on the anchor, fill-time judging (fairAtFill), picked-off guard (makerHalted, Resume bids), unlimited bids (+60/pass cap), Quick & likely mode (QuickLikely), Bids tab controls; tests green; bid-focus study run (tools/research/novig_bid_focus_study.py)

## Do this next
CK2: track bids in Diagnostics + the scan study + AtBet for maker fills; then RESEARCH §88.2-88.4 write-up; HealthChecks for bids; then v0.64.0 ship prep (full test floor, sweep)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f3449ca6 ckpt 2550: CL1 done: kill switch (killed + derived paused, KillMarker second copy, Kill
  08cbeea6 ckpt 2549: wrote the work order and CI1 finding into TASKS.md (kill switch first, then 
  10c64bc3 ckpt 2548: CJ1 done: Tracker profit/staked/chart count every settled bet in both views 
  7c5301a7 ckpt 2547: wrote Tj's 2026-10-05 Pinnacle-only / Novig-only profit / auto-bid / kill-sw
  0b8ca56c ckpt 2546: CH3 done: v0.63.0 released and recorded (guard covers any two-outcome holdin
  55d1ac02 ckpt 2545: pre-release: v0.63.0: the grading guard covers any market held on both sides
  503a49b0 ckpt 2544: CH1-CH2 done: investigation (RESEARCH §87.1): 8 silence-rule grades, 7 righ
  0cf7e469 ckpt 2543: CG3 done: v0.62.0 released and recorded (the Ollie Gordon lock grade fix); L
  cca73357 ckpt 2542: pre-release: v0.62.0: a market held on both sides is never graded lost from 
  afa63093 ckpt 2541: pre-ship: v0.62.0: a market held on both sides is never graded lost from Nov
```

(14 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
