# CHECKPOINT 2457 — read me first, then TASKS.md

**Written:** 2026-10-03T17:20:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-c4435189-1cfj54` · **builds on:** `23b2e5b6` (this checkpoint is the commit after it)

## Just done
BL6b: auto-bet orders by credible EV (AutoBet.credibleEv); game-line bids get the trap guard's move rule (MakerLines.moveWanted/withMoves, MakerRunner reads ≤6 markets/pass, none by default); tests + mutants 7/7 killed; Polymarket follow study done (top-quarter copy CLV +0.99¢@1min, moneylines +1.32¢)

## Do this next
BL6c: UI tests (AutoBetUiTest new chips/search), Diagnostics sharp-bar split in tj_bets_by_lead.py; then RESEARCH.md §72 full write-up, BRIEF.md rules, TASKS ticks; full floor + screenshots; ship v0.56.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  20a23992 ckpt 2456: BL6a: sharp veto bar (ScanSettings.sharpVetoMinEv, 1% default) in SharpVeto/
  b171632c ckpt 2455: BL1/BL2 in progress: sources read (Kaunitz, Moskowitz, Buchdahl, Data Golf, 
  904a2700 ckpt 2454: BL: Tj's deep sharp/CLV/trap research + implement request written to TASKS.m
  b951447c ckpt 2453: BK done: v0.55.0 (code 95) released + recorded (trap guard, Bids in Settings
  26b836bc ckpt 2452: pre-release: v0.55.0: trap guard (auto-bet, alerts and bids only on games wi
  4a4c2e8b ckpt 2451: BK1-BK5 ticked with tests named; floor 1,838 passed / 23 skipped
  ec138cae ckpt 2450: BK2: bet-sheet trap note (CNO + Vigilant sheets) + TrapGuardUiTest, screensh
  544557f4 ckpt 2449: BK1: diag per-endpoint failure kinds (PathStat.fails), market cache 6 h (429
  58cd9bd0 ckpt 2448: BK5 trap guard: AutoBettor/alerts/maker wiring + tests (mutants 5/5), UI Tra
  bd881674 ckpt 2447: BK5: trap study (tools/research/novig_trap_study.py) + Tj's bets: early bets
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
