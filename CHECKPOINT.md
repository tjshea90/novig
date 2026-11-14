# CHECKPOINT 4439 — read me first, then TASKS.md

**Written:** 2026-11-14T05:37:01Z · **tests:** all 10 fast checks green
**Branch:** `claude/epic-goldberg-2yhbbk` · **builds on:** `2ac9255df` (this checkpoint is the commit after it)

## Just done
pre-release: ParlayAPI stopped serving Pinnacle (Nov 9): closes ask DraftKings+FanDuel moneylines (soft, after ESPN), no ParlayAPI request names Pinnacle, alternates off (saves 2 of 5 credits a league), line moves name their book (versionCode 444, v1.0.104)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.104), then run: bash tools/record-release.sh v1.0.104 444 "ParlayAPI stopped serving Pinnacle (Nov 9): closes ask DraftKings+FanDuel moneylines (soft, after ESPN), no ParlayAPI request names Pinnacle, alternates off (saves 2 of 5 credits a league), line moves name their book"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2ac9255df ckpt 4438: pre-ship: ParlayAPI stopped serving Pinnacle (Nov 9): closes ask DraftKings
  043b81025 ckpt 4437: PA1: ParlayCloses DK+FD moneyline only (soft), ParlayWithdrawn guard, alter
  1e7759187 ckpt 4436: PA1: probe run 2 read: Pinnacle absent from closes file since at least Nov 
  3b3724a9e ckpt 4435: PA1: closeprobe run 1 read (UNKNOWN_BOOKMAKER body, DK/FD flat rows labelle
  12408afad ckpt 4434: PA1: added closeprobe task (tools/parlay.py + parlay.yml) to record ParlayA
  6beecfb4d ckpt 4433: PA1: read ParlayAPI's public docs + engine status; wrote PA1a-e into TASKS.
  afe30e836 ckpt 4432: ANALYST 11-13 slot2: digest unchanged, engine asleep wallet $0.01, no chang
  bce84639c ckpt 4431: WATCH 11-13 slot1 (tj Gmail): engine asleep wallet $0, no change
  9a3599945 ckpt 4430: covered ANALYST 11-12 slot0: no new evidence
  04660fb77 ckpt 4429: WATCH 11-13 slot1: engine healthy, no change
```
