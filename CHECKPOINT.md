# CHECKPOINT 4440 — read me first, then TASKS.md

**Written:** 2026-11-14T05:59:49Z · **tests:** all 10 fast checks green
**Branch:** `claude/epic-goldberg-2yhbbk` · **builds on:** `2ae34b7f8` (this checkpoint is the commit after it)

## Just done
engine-grade.yml had failed every scheduled run since 2026-11-06: lines_dataset.py bids held 3.9M bid dicts (24 GB) and killed the runner; bids() now streams through SQLite (162 MB peak for 612k records, byte-identical on real segments); engine results publish before the dataset step, which can no longer fail the job

## Do this next
dispatch engine-grade.yml on this branch, confirm it goes green and engine-results/engine-dataset update; then ship (tools-only: lands without the app gate)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M .github/workflows/engine-grade.yml
     M CHECKPOINT.md

## Last ten checkpoints
```
  88b1af9f2 ckpt 4439: pre-release: ParlayAPI stopped serving Pinnacle (Nov 9): closes ask DraftKi
  2ac9255df ckpt 4438: pre-ship: ParlayAPI stopped serving Pinnacle (Nov 9): closes ask DraftKings
  043b81025 ckpt 4437: PA1: ParlayCloses DK+FD moneyline only (soft), ParlayWithdrawn guard, alter
  1e7759187 ckpt 4436: PA1: probe run 2 read: Pinnacle absent from closes file since at least Nov 
  3b3724a9e ckpt 4435: PA1: closeprobe run 1 read (UNKNOWN_BOOKMAKER body, DK/FD flat rows labelle
  12408afad ckpt 4434: PA1: added closeprobe task (tools/parlay.py + parlay.yml) to record ParlayA
  6beecfb4d ckpt 4433: PA1: read ParlayAPI's public docs + engine status; wrote PA1a-e into TASKS.
  afe30e836 ckpt 4432: ANALYST 11-13 slot2: digest unchanged, engine asleep wallet $0.01, no chang
  bce84639c ckpt 4431: WATCH 11-13 slot1 (tj Gmail): engine asleep wallet $0, no change
  9a3599945 ckpt 4430: covered ANALYST 11-12 slot0: no new evidence
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
