# CHECKPOINT 2448 — read me first, then TASKS.md

**Written:** 2026-10-03T15:54:59Z · **tests:** all 3 fast checks green
**Branch:** `ccr-f3d86383-spsexb` · **builds on:** `006b5495` (this checkpoint is the commit after it)

## Just done
BK5 trap guard: AutoBettor/alerts/maker wiring + tests (mutants 5/5), UI TrapGuardSection on Auto-bet tab/Alerts/Bids, Settings home Bids row + Bids search entries + every-tab-entry tests, Diagnostics trap line + lead split for all bets + diag bids-from-file pin

## Do this next
write RESEARCH.md §71 + NOVIG_API §5 trades semantics; then BK3/BK4 EV/CLV/bid math audit; BK1 parlay-api 500/503 backoff

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bd881674 ckpt 2447: BK5: trap study (tools/research/novig_trap_study.py) + Tj's bets: early bets
  f5624b68 ckpt 2446: BK: Tj's full-tests + EV/CLV + bids + trap-bets request (v0.54.0 diag) writt
  1ba158ae ckpt 2445: BJ done: v0.54.0 (code 94) released + recorded; BJ1-BJ4 ticked
  3234a954 ckpt 2444: pre-release: v0.54.0: bids rest to their expiry (no re-post loop), bids that
  d8e2170f ckpt 2443: v0.54.0 (code 94) bumped; BRIEF.md v0.54.0 make-orders rules; full floor run
  5b96f57a ckpt 2442: BJ3 done: findings worked/triaged from the v0.53.0 file (lag resolved, 429s,
  9a2850d5 ckpt 2441: NOVIG_API.md §17 verified facts (wallet not held, orders/{id} 404 off book,
  29b46cbc ckpt 2440: BJ2a-f done and ticked: 8 new tests, mutants 7/7
  22a7b37a ckpt 2439: BJ2a-f code in (churn rule, withoutOwn + bidLevels, leaders-first PRIORITY, 
  1331effe ckpt 2438: BJ1 evidence confirmed from Tj's re-sent v0.53.0 file; BJ2 split into BJ2a-f
```

(17 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
