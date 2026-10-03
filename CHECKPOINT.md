# CHECKPOINT 2456 — read me first, then TASKS.md

**Written:** 2026-10-03T17:06:33Z · **tests:** all 3 fast checks green
**Branch:** `ccr-c4435189-1cfj54` · **builds on:** `9d583339` (this checkpoint is the commit after it)

## Just done
BL6a: sharp veto bar (ScanSettings.sharpVetoMinEv, 1% default) in SharpVeto/alerts/auto-bet/bids/presets (Volume 1%, Strict 2%), UI chips + search + diagnostics; SharpVetoTest/PresetsTest/MakerTest green

## Do this next
BL6b: credible-EV ordering for auto-bet; game-line bid trap check; app tests (AutoBetUiTest etc.); then Polymarket results (/tmp/claude-0/pm.out) and RESEARCH.md §72 write-up

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
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

(23 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
