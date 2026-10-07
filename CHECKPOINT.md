# CHECKPOINT 2671 — read me first, then TASKS.md

**Written:** 2026-10-07T06:20:16Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `9e909cf7` (this checkpoint is the commit after it)

## Just done
DG6 done (ApiSettler grades the other leg of a half-point two-way held market WON when a feed graded one lost, 4 tests, 7 mutants killed); reviewer's 6 confirmed defects in the obscure fill fixed with tests and 8 mutants killed (room-making now only for popular bids that could go up, fewest cancels, no hand-approved, Kelly half stake); full floor 2421 green

## Do this next
evidence agent a154e69c97e180386 still running (research/obscure_bid_study_2026-10-07.json): set obscure defaults from it, RESEARCH 102, version 0.71.3 code 126; then DG1 (first-seen guard), DG2, DG3, DG5, DG4, DG7

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
    ?? research/obscure_bid_review_2026-10-07.md

## Last ten checkpoints
```
  939ffa7a ckpt 2670: v0.71.2 RELEASED + recorded (OUT-player fix); obscure-bid fill (DE3+DE4) BUI
  cfc58c12 ckpt 2669: pre-release: v0.71.2: no bid, auto-bet or alert on a player the injury repor
  82e1dde6 ckpt 2668: pre-release: v0.71.2: no bid, auto-bet or alert on a player the injury repor
  33503b81 ckpt 2667: v0.71.1 RELEASED and RECORDED (code 124): DD1 app faults fixed; DE0 ticked
  07851783 ckpt 2666: pre-release: v0.71.1: the app faults the v0.70.1 analysis found are fixed: t
  11c55be5 ckpt 2665: TASKS DE0/DE1 written in Tj's words with the full plan for obscure-bid fill 
  9bc3df2f ckpt 2664: v0.71.1 candidate: DD1 app faults fixed (RESEARCH.md section 101), version 0
  42a856ef ckpt 2663: DD1 faults fixed with tests: study export, timeline cap, health lines, exit 
  fccffd7a ckpt 2662: TASKS DD1/DD2 written in Tj's words
  20fc2cab ckpt 2661: v0.71.0 RELEASED and RECORDED (code 123): settings pass DC1-DC5
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
