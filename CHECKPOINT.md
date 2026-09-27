# CHECKPOINT 445 — read me first, then TASKS.md

**Written:** 2026-09-27T00:40:08Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `e886f2b` (this checkpoint is the commit after it)

## Just done
K1 (links lane + TapLink: cache -> CNO 5s -> Novig catalog -> game, toast), K2/K6 (friendly DNS/timeout errors), K3 (stale scan-done notification cancelled on new process / CNO only / new scan), K4 (bar icons-only when narrow, 360dp default), K7 (x remove, hidden list, Undo), K8 (only-agreed setting + held-back counts), K9 RESEARCH 20.2; tests: data 286, app 113, engine 39 green

## Do this next
Re-run floor after polish (grammar, Refresh icon), update TASKS ticks/BRIEF/CLAUDE surface, bump v0.15.2 code 22, ship.sh, CI, release, record, send link; then K10 full test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md

## Last ten checkpoints
```
  d17f056 ckpt 444: K1/K2/K6/K9 data layer: CnoNetwork (DoH+remembered DNS, 20s keep-alive, 12s re
  1d2da36 ckpt 443: Logged K9 (reliable CNO refresh research); CnoFeed link lane + lighter books l
  3f2758e ckpt 442: Logged K8 (only-agreed-bets option); CnoNetwork (RememberingDns, keep-alive 20
  17ba6f1 ckpt 441: v0.15.1 released + recorded; logged K6 (DNS/timeouts) and K7 (✕ hide button)
  5c9fe1c ckpt 440: Logged Tj's K1-K5 (bet slip taps, CNO unreachable, Milwaukee notification, nar
  3b2a13c ckpt 439: pre-release: v0.15.1: floating widget resizes with two fingers (spread/pinch, 
  3cf811b ckpt 438: J5 tests green: 409 tests 0 failed; FloatingWidget handles touches in dispatch
  a79a462 ckpt 437: J1-J4, J6 done: framed widget with corner handles, pinch/spread + two-finger m
  4cfc2e5 ckpt 436: Logged Tj's request: pinch/corner resize, easier move, cut-off bottom-right gr
  cd1f331 ckpt 435: SHIPPED v0.15.0 (code 20): floating CNO widget, placed bets, teams, green chec
```

(24 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
