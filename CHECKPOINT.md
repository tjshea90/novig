# CHECKPOINT 2502 — read me first, then TASKS.md

**Written:** 2026-10-04T01:55:14Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `ce8f916d` (this checkpoint is the commit after it)

## Just done
BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: hand last, leaders, cheaper, EV), MakerDesk.fit + post net of bids/day limit, Report.trimmed; MakerTest 48 green (10 new)

## Do this next
mutants on the trim (separate copy), then app layer: MakerRunner (fit when no scan yet, guard fitToWallet, post passes wallet/day limit, MAKER log trimmed), AppContainer wallet/bids collector, WalletStrip over-wallet marker, tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
  c126a541 ckpt 2500: v0.58.2 released + recorded (release.yml run 37161700517 green, tag v0.58.2)
  70a079b5 ckpt 2499: pre-release: v0.58.2: the scan study is finished and written inside every ba
  fcbd3e13 ckpt 2498: BT done in code: StudySync.catchUp in the background cycle and at a scan's e
  9ad7aeaa ckpt 2497: BT: Tj asks to confirm the study logs everything in background auto-scan mod
  1a8aa7f0 ckpt 2496: BS1/BS2 built: props splits + what-if lines in the study file; v0.58.1 (code
  d68bde0b ckpt 2495: BR3 answered: Tj picked option 1 (leave rules, add props split to the study)
  bcca9206 ckpt 2494: v0.58.0 released + recorded (release.yml run 37159279383 green, tag v0.58.0)
  deaaacb3 ckpt 2493: pre-release: v0.58.0: the scan study also logs what CNO's filters hide (a se
  162485c7 ckpt 2492: BQ done in code: v0.58.0 (code 100) prepared; floor green (1,919 passed, 23 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
