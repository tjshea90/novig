# CHECKPOINT 2447 — read me first, then TASKS.md

**Written:** 2026-10-03T15:41:31Z · **tests:** all 3 fast checks green
**Branch:** `ccr-f3d86383-spsexb` · **builds on:** `798bf052` (this checkpoint is the commit after it)

## Just done
BK5: trap study (tools/research/novig_trap_study.py) + Tj's bets: early bets (6h+) lose to close; game-line Novig moves are traps. TrapGuard (data) + settings trapEarlyHours/trapNovigMove + wiring in AutoBettor/alerts/maker; NovigPublicClient.trades; BetLedger LEAD for every bet + ids; diag maker bids read from file

## Do this next
BK5: app tests for AutoBettor trap guard (fake recentTrades) + alerts early; UI TrapGuardSection (Auto-bet tab, Alerts page, Bids tab) + SettingsIndex; Diagnostics settings line; RESEARCH.md §71; NOVIG_API §5 trades semantics

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f5624b68 ckpt 2446: BK: Tj's full-tests + EV/CLV + bids + trap-bets request (v0.54.0 diag) writt
  1ba158ae ckpt 2445: BJ done: v0.54.0 (code 94) released + recorded; BJ1-BJ4 ticked
  3234a954 ckpt 2444: pre-release: v0.54.0: bids rest to their expiry (no re-post loop), bids that
  d8e2170f ckpt 2443: v0.54.0 (code 94) bumped; BRIEF.md v0.54.0 make-orders rules; full floor run
  5b96f57a ckpt 2442: BJ3 done: findings worked/triaged from the v0.53.0 file (lag resolved, 429s,
  9a2850d5 ckpt 2441: NOVIG_API.md §17 verified facts (wallet not held, orders/{id} 404 off book,
  29b46cbc ckpt 2440: BJ2a-f done and ticked: 8 new tests, mutants 7/7
  22a7b37a ckpt 2439: BJ2a-f code in (churn rule, withoutOwn + bidLevels, leaders-first PRIORITY, 
  1331effe ckpt 2438: BJ1 evidence confirmed from Tj's re-sent v0.53.0 file; BJ2 split into BJ2a-f
  1beb897b ckpt 2437: resumed after usage cut: sub-agent workflow results lost (never committed, o
```

(18 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
