# CHECKPOINT 2510 — read me first, then TASKS.md

**Written:** 2026-10-04T03:05:07Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `ffb56f88` (this checkpoint is the commit after it)

## Just done
BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622 bets); copied into research/, wrote BX1-BX6 into TASKS.md (verify claims in code, fix the close matcher, log-only study fixes, ask Tj about prop page reads). BW (per-game exposure) workflow wf_15ad2df6-74f still running in the background

## Do this next
BX1: scout the close matcher (ParlayAPI Pinnacle closes, GuardedCloses, CloseBackfill) in a Workflow alongside BW; then fix BX2 first (money-adjacent stats), then BX3/BX4; BW design arrives from the workflow

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
    ?? research/scan_study_analysis_2026-10-03_v0.58.3.md
    ?? research/scan_study_analysis_2026-10-03_v0.58.3_checkpoint.md

## Last ten checkpoints
```
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
  d0334f7d ckpt 2507: pre-release: v0.58.3: bids are kept within the wallet and the day's limit (a
  c80df9fc ckpt 2506: pre-ship: v0.58.3: bids are kept within the wallet and the day's limit (a be
  baa4035c ckpt 2505: BV built: RateGate.takeLowRate, ReadPace on BookBatch (public/key start/low/
  d37ac654 ckpt 2504: BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagn
  34f91bf4 ckpt 2503: BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: Ma
  ca3a709e ckpt 2502: BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: h
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
  c126a541 ckpt 2500: v0.58.2 released + recorded (release.yml run 37161700517 green, tag v0.58.2)
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
