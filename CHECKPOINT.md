# CHECKPOINT 454 — read me first, then TASKS.md

**Written:** 2026-09-27T01:29:35Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `de36f77` (this checkpoint is the commit after it)

## Just done
L1 done: taps race CNO + Novig catalog (first exact wins); links lane catalog-first (CNO only for what it can't name), 30 ahead; live 60/60 exact, 0 wrong; links file keeps newest; data 296 app 117 green

## Do this next
L2: widget switch for Vigilant scan (Both <-> CNO only), dedupe same bet across scanners, opt-in auto-scan while widget open; then L3 (measure CNO under load, Novig live price for CNO rows, RESEARCH 20.3), L4 ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  8b2086b ckpt 453: Logged Tj's 01:18Z requests as L1-L4 in TASKS.md
  8eb5cb1 ckpt 452: K10 done: v0.15.3 shipped (CI green 7b9636f, Release published, recorded); K1-
  7b9636f ckpt 451: pre-release: v0.15.3: full test: CNO pages parsed off the main thread (smoothe
  7431be3 ckpt 450: K10b/c done; v0.15.3 code 23 bumped; forced floor 446 green; v0.15.2 released 
  732628d ckpt 449: K10a: parse off main (CnoClient/PlayerTeams), pause fixes (links, mid-read), v
  b39913d ckpt 448: CI fix: CnoNetworkTest dead-connection retry pinned to 127.0.0.1 (CI runners r
  cb2fe83 ckpt 447: pre-release: v0.15.2: taps open Novig's bet slip every time (links read ahead,
  8c4d920 ckpt 446: K1-K9 done, v0.15.2 code 22 bumped, TASKS ticked, forced floor 438 green (engi
  1ef848e ckpt 445: K1 (links lane + TapLink: cache -> CNO 5s -> Novig catalog -> game, toast), K2
  d17f056 ckpt 444: K1/K2/K6/K9 data layer: CnoNetwork (DoH+remembered DNS, 20s keep-alive, 12s re
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
