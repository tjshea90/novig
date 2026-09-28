# CHECKPOINT 572 — read me first, then TASKS.md

**Written:** 2026-09-28T14:52:24Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `d39a763` (this checkpoint is the commit after it)

## Just done
R1: measured board (7d = 8,319 markets, 0.67MB, 2 pages) and free sources live (LiveSourceTimingTest: Kalshi 27.6s at 2/s over NFL/NCAAF/MLB/WNBA/ATP/WTA, Polymarket 9.2s); since v0.19.2 a league's bets wait for Kalshi

## Do this next
R2: floor keyed pace at 14/s (limits only raise it), public board first (signed only when throttled), speed Kalshi, add scan timing diagnostics to Settings > Novig API; ship v0.19.3

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  facb691 ckpt 571: Recorded Tj's 'API reading very slow since latest version' as R1-R2
  8e5378c ckpt 570: Released Vigilant v0.19.2 (code 37): release.yml green, tag v0.19.2 has vigila
  a34fcd4 ckpt 569: pre-release: v0.19.2: bets shown mid-scan stay: a league's bets wait until all
  19ebb4b ckpt 568: pre-release v0.19.2 (37): steady feed (hold bets until fair sources answer, 2-
  f22cd3f ckpt 567: Recorded Tj's 'bets appeared then disappeared' question as Q1-Q2
  3738919 ckpt 566: Released Vigilant v0.19.1 (code 36): release.yml run 36392649770 green, tag v0
  d6e8948 ckpt 565: pre-release v0.19.1 (36): floor 689 green (engine 39, data 445/10 live skipped
  d0342a0 ckpt 564: M1+M2 done: week-ahead default, later-games count, DELAYED, strike guard, /v3/
  2d1fca0 ckpt 563: M1: Days ahead 7 default (+schema 8 moves a saved 3), all events read so later
  8f76985 ckpt 562: Recorded Tj's 'way more than 7 games' + Novig docs request as M1-M3
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
