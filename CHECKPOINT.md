# CHECKPOINT 2504 — read me first, then TASKS.md

**Written:** 2026-10-04T02:04:56Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `0ccbfe52` (this checkpoint is the commit after it)

## Just done
BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagnostics v0.58.2 (22:01 local Oct 3): proves the wallet/bids overshoot (wallet $11.55 with $18.71 resting; $7.42 with $16.14) and gives the slow-scan evidence: 299 s scan = 4,274 prices at 14.5/s vs key limit 16/s, live feed asked 2,000 delivered 315; 451 ANONYMIZED_NETWORK flaps send scans to the public route (4/s, halved to 2/s by 429s)

## Do this next
run the full floor (bash tools/test.sh), then BV: record per scan the pacer state (public/key lowest pace, key 429s, live feed held at end) in ScanTiming + richer SCAN timeline line, tests, then sweep, ship v0.58.3

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  34f91bf4 ckpt 2503: BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: Ma
  ca3a709e ckpt 2502: BU2 done in data: MakerPlan trim (budget<0 takes least valuable bids down: h
  a9cbe2b6 ckpt 2501: BU: wrote Tj's wallet-vs-open-bids request into TASKS.md; cause confirmed in
  c126a541 ckpt 2500: v0.58.2 released + recorded (release.yml run 37161700517 green, tag v0.58.2)
  70a079b5 ckpt 2499: pre-release: v0.58.2: the scan study is finished and written inside every ba
  fcbd3e13 ckpt 2498: BT done in code: StudySync.catchUp in the background cycle and at a scan's e
  9ad7aeaa ckpt 2497: BT: Tj asks to confirm the study logs everything in background auto-scan mod
  1a8aa7f0 ckpt 2496: BS1/BS2 built: props splits + what-if lines in the study file; v0.58.1 (code
  d68bde0b ckpt 2495: BR3 answered: Tj picked option 1 (leave rules, add props split to the study)
  bcca9206 ckpt 2494: v0.58.0 released + recorded (release.yml run 37159279383 green, tag v0.58.0)
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
