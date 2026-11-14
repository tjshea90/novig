# CHECKPOINT 4438 — read me first, then TASKS.md

**Written:** 2026-11-14T05:36:14Z · **tests:** all 10 fast checks green
**Branch:** `claude/epic-goldberg-2yhbbk` · **builds on:** `23665dda2` (this checkpoint is the commit after it)

## Just done
pre-ship: ParlayAPI stopped serving Pinnacle (Nov 9): closes ask DraftKings+FanDuel moneylines (soft, after ESPN), no ParlayAPI request names Pinnacle, alternates off (saves 2 of 5 credits a league), line moves name their book

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    M  TASKS.md

## Last ten checkpoints
```
  043b81025 ckpt 4437: PA1: ParlayCloses DK+FD moneyline only (soft), ParlayWithdrawn guard, alter
  1e7759187 ckpt 4436: PA1: probe run 2 read: Pinnacle absent from closes file since at least Nov 
  3b3724a9e ckpt 4435: PA1: closeprobe run 1 read (UNKNOWN_BOOKMAKER body, DK/FD flat rows labelle
  12408afad ckpt 4434: PA1: added closeprobe task (tools/parlay.py + parlay.yml) to record ParlayA
  6beecfb4d ckpt 4433: PA1: read ParlayAPI's public docs + engine status; wrote PA1a-e into TASKS.
  afe30e836 ckpt 4432: ANALYST 11-13 slot2: digest unchanged, engine asleep wallet $0.01, no chang
  bce84639c ckpt 4431: WATCH 11-13 slot1 (tj Gmail): engine asleep wallet $0, no change
  9a3599945 ckpt 4430: covered ANALYST 11-12 slot0: no new evidence
  04660fb77 ckpt 4429: WATCH 11-13 slot1: engine healthy, no change
  31c136e8e ckpt 4428: WATCH 11-12 slot2: engine idle, wallet $0.20, no change
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
