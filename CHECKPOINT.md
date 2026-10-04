# CHECKPOINT 2505 — read me first, then TASKS.md

**Written:** 2026-10-04T02:12:18Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `c2507599` (this checkpoint is the commit after it)

## Just done
BV built: RateGate.takeLowRate, ReadPace on BookBatch (public/key start/low/end + key 429s), ScanTiming.pace + liveFeedHeld + routeNotes (text + timeline speedNote), Scanner aggregates; tests green (RateGate, ScanTiming, LiveFeedPlan, NovigPublicClient, AppRecorder). BU floor green earlier (1,973 tests)

## Do this next
mutants on BV in the scratch copy, full floor, sweep (BU + BV code and UI), docs (NOVIG_API/RESEARCH/BRIEF/BUILDLOG/TASKS), ship v0.58.3 (code 103)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/test/kotlin/com/tjshea/vigilant/app/AppRecorderTest.kt
     M data/src/test/kotlin/com/tjshea/vigilant/data/scanner/ScanTimingTest.kt

## Last ten checkpoints
```
  d37ac654 ckpt 2504: BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagn
  34f91bf4 ckpt 2503: BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: Ma
  ca3a709e ckpt 2502: BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: h
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
  c126a541 ckpt 2500: v0.58.2 released + recorded (release.yml run 37161700517 green, tag v0.58.2)
  70a079b5 ckpt 2499: pre-release: v0.58.2: the scan study is finished and written inside every ba
  fcbd3e13 ckpt 2498: BT done in code: StudySync.catchUp in the background cycle and at a scan's e
  9ad7aeaa ckpt 2497: BT: Tj asks to confirm the study logs everything in background auto-scan mod
  1a8aa7f0 ckpt 2496: BS1/BS2 built: props splits + what-if lines in the study file; v0.58.1 (code
  d68bde0b ckpt 2495: BR3 answered: Tj picked option 1 (leave rules, add props split to the study)
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
