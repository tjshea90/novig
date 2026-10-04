# CHECKPOINT 2503 — read me first, then TASKS.md

**Written:** 2026-10-04T02:00:27Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `fd42d6de` (this checkpoint is the commit after it)

## Just done
BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: MakerRunner.fitToWallet + no-scan fit + hand-post budget, AppContainer wallet watch (combine wallet.flow+makerStore.flow), WalletStrip 'over the wallet'; app tests added (MakerAppTest +5, WalletStripTest +2) not yet run. Tj also asked mid-turn about slow Novig scans: BV in TASKS.md

## Do this next
run :app:test --tests '*MakerAppTest' and '*WalletStripTest', view screenshots/0_wallet_strip_over.png, fix failures; then BV (record the Novig public pacer's state per scan in ScanTiming + SCAN line), sweep, full floor, ship v0.58.3

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ca3a709e ckpt 2502: BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: h
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
  c126a541 ckpt 2500: v0.58.2 released + recorded (release.yml run 37161700517 green, tag v0.58.2)
  70a079b5 ckpt 2499: pre-release: v0.58.2: the scan study is finished and written inside every ba
  fcbd3e13 ckpt 2498: BT done in code: StudySync.catchUp in the background cycle and at a scan's e
  9ad7aeaa ckpt 2497: BT: Tj asks to confirm the study logs everything in background auto-scan mod
  1a8aa7f0 ckpt 2496: BS1/BS2 built: props splits + what-if lines in the study file; v0.58.1 (code
  d68bde0b ckpt 2495: BR3 answered: Tj picked option 1 (leave rules, add props split to the study)
  bcca9206 ckpt 2494: v0.58.0 released + recorded (release.yml run 37159279383 green, tag v0.58.0)
  deaaacb3 ckpt 2493: pre-release: v0.58.0: the scan study also logs what CNO's filters hide (a se
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
