# CHECKPOINT 2506 — read me first, then TASKS.md

**Written:** 2026-10-04T02:18:55Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `4225ac4d` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.58.3: bids are kept within the wallet and the day's limit (a bet by hand, an auto-bet or a fill can no longer leave more bids up than money: the least valuable come down, hand-approved last; Approve checks the wallet beside the bids up; the wallet strip says over the wallet) and every scan on the timeline records its Novig pace, the public and key routes' slowdowns and how much of the live feed arrived

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  baa4035c ckpt 2505: BV built: RateGate.takeLowRate, ReadPace on BookBatch (public/key start/low/
  d37ac654 ckpt 2504: BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagn
  34f91bf4 ckpt 2503: BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: Ma
  ca3a709e ckpt 2502: BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: h
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
  c126a541 ckpt 2500: v0.58.2 released + recorded (release.yml run 37161700517 green, tag v0.58.2)
  70a079b5 ckpt 2499: pre-release: v0.58.2: the scan study is finished and written inside every ba
  fcbd3e13 ckpt 2498: BT done in code: StudySync.catchUp in the background cycle and at a scan's e
  9ad7aeaa ckpt 2497: BT: Tj asks to confirm the study logs everything in background auto-scan mod
  1a8aa7f0 ckpt 2496: BS1/BS2 built: props splits + what-if lines in the study file; v0.58.1 (code
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
