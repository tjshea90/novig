# CHECKPOINT 2670 — read me first, then TASKS.md

**Written:** 2026-10-07T06:01:15Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `d5fb25ef` (this checkpoint is the commit after it)

## Just done
v0.71.2 RELEASED + recorded (OUT-player fix); obscure-bid fill (DE3+DE4) BUILT: MakerQuote tier B (wider margin, sharp edge, sharp agreement, min books, half stake), priority popular-first, small-market bids held back while a popular one waits and taken down to make room, MakerBid/RestingBid.obscure, BidReport tier split, switch + 5 typed safeguards in Bids, Settings search, Diagnostics note; 18 ObscureFillTest + desk/report/UI tests, 14 mutants killed, full floor 2411 green; evidence agent (DE2, fill rates by popularity tier) and an adversarial reviewer still running

## Do this next
when the evidence agent reports (research/obscure_bid_study_2026-10-07.json): set the defaults (ScanSettings makerObscure* and whether the fill ships ON or OFF) from its numbers; apply the reviewer's confirmed defects; RESEARCH.md section 102; bump version to v0.71.3 code 126; full floor; push; CI green on the exact commit (no pushes during CI); bash ship.sh; release.yml; record-release; answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  cfc58c12 ckpt 2669: pre-release: v0.71.2: no bid, auto-bet or alert on a player the injury repor
  82e1dde6 ckpt 2668: pre-release: v0.71.2: no bid, auto-bet or alert on a player the injury repor
  33503b81 ckpt 2667: v0.71.1 RELEASED and RECORDED (code 124): DD1 app faults fixed; DE0 ticked
  07851783 ckpt 2666: pre-release: v0.71.1: the app faults the v0.70.1 analysis found are fixed: t
  11c55be5 ckpt 2665: TASKS DE0/DE1 written in Tj's words with the full plan for obscure-bid fill 
  9bc3df2f ckpt 2664: v0.71.1 candidate: DD1 app faults fixed (RESEARCH.md section 101), version 0
  42a856ef ckpt 2663: DD1 faults fixed with tests: study export, timeline cap, health lines, exit 
  fccffd7a ckpt 2662: TASKS DD1/DD2 written in Tj's words
  20fc2cab ckpt 2661: v0.71.0 RELEASED and RECORDED (code 123): settings pass DC1-DC5
  47402386 ckpt 2660: pre-release: v0.71.0: settings pass: Settings home grouped under four headin
```

(19 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
