# CHECKPOINT 576 — read me first, then TASKS.md

**Written:** 2026-09-28T15:23:19Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `442a81c` (this checkpoint is the commit after it)

## Just done
R2e: docs (RESEARCH §29 with live Kalshi lines-first timings, NOVIG_API §11.1, CLAUDE.md), Kalshi reused quotes keep their read time, v0.19.3 (38); full floor 709/0 failures

## Do this next
ship.sh v0.19.3, wait for CI, trigger release.yml, confirm, record-release, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md

## Last ten checkpoints
```
  0a9e9f7 ckpt 575: R2c Kalshi game lines first (lines pass, hold-back lets lines through) + R2d S
  d2cabfe ckpt 574: R2a: key reads were capped at 4 in flight (OkHttp's 5/host, websocket holds on
  62ba92d ckpt 573: R1 done: causes found (v0.19.2 hold-back waits for Kalshi 8-28s; v0.19.1 7 day
  dbbe366 ckpt 572: R1: measured board (7d = 8,319 markets, 0.67MB, 2 pages) and free sources live
  facb691 ckpt 571: Recorded Tj's 'API reading very slow since latest version' as R1-R2
  8e5378c ckpt 570: Released Vigilant v0.19.2 (code 37): release.yml green, tag v0.19.2 has vigila
  a34fcd4 ckpt 569: pre-release: v0.19.2: bets shown mid-scan stay: a league's bets wait until all
  19ebb4b ckpt 568: pre-release v0.19.2 (37): steady feed (hold bets until fair sources answer, 2-
  f22cd3f ckpt 567: Recorded Tj's 'bets appeared then disappeared' question as Q1-Q2
  3738919 ckpt 566: Released Vigilant v0.19.1 (code 36): release.yml run 36392649770 green, tag v0
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
