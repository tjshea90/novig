# CHECKPOINT 574 — read me first, then TASKS.md

**Written:** 2026-09-28T15:09:11Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `0869709` (this checkpoint is the commit after it)

## Just done
R2a: key reads were capped at 4 in flight (OkHttp's 5/host, websocket holds one) -> vigilantHttpClient 16/host; one refused wave halved pace per refusal and could stop the scan -> once per burst; 10 in flight + 30-book batches with a key. Tests fail before, pass after.

## Do this next
R2b: board from public CDN routes first, signed catalog only when throttled

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  62ba92d ckpt 573: R1 done: causes found (v0.19.2 hold-back waits for Kalshi 8-28s; v0.19.1 7 day
  dbbe366 ckpt 572: R1: measured board (7d = 8,319 markets, 0.67MB, 2 pages) and free sources live
  facb691 ckpt 571: Recorded Tj's 'API reading very slow since latest version' as R1-R2
  8e5378c ckpt 570: Released Vigilant v0.19.2 (code 37): release.yml green, tag v0.19.2 has vigila
  a34fcd4 ckpt 569: pre-release: v0.19.2: bets shown mid-scan stay: a league's bets wait until all
  19ebb4b ckpt 568: pre-release v0.19.2 (37): steady feed (hold bets until fair sources answer, 2-
  f22cd3f ckpt 567: Recorded Tj's 'bets appeared then disappeared' question as Q1-Q2
  3738919 ckpt 566: Released Vigilant v0.19.1 (code 36): release.yml run 36392649770 green, tag v0
  d6e8948 ckpt 565: pre-release v0.19.1 (36): floor 689 green (engine 39, data 445/10 live skipped
  d0342a0 ckpt 564: M1+M2 done: week-ahead default, later-games count, DELAYED, strike guard, /v3/
```

(16 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
