# CHECKPOINT 461 — read me first, then TASKS.md

**Written:** 2026-09-27T02:48:43Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `475ca20` (this checkpoint is the commit after it)

## Just done
Full test (Tj 02:36Z): floor 474->480 green; fixed (each test failed on the old code): (1) NovigStream: a closed socket's late callback flipped a deliberate close to Failed and could tear down a quick reconnect -> callbacks from stale sockets ignored (NovigStreamTest, was flaky in the floor); (2) 'only agreed' + Novig's price now flickered a bet in/out every 15 s -> live prices read for stable candidates (UiState.livePriceRows, MiniWindowTest); (3) CNO card x/check squeezed by Kelly column -> moved top-right (cnoCardButtonsKeepTheirSize...); (4) CNO card/sheet EV not warned under minimum at Novig's price now -> orange EvBadge(low) (cnoCardWarns...); (5) CNO tab order vs widget -> sorted by shown EV; (6) stale copy: Settings widget text (x remove, Both switch, top x), CNO tab footer, Tracker 'before kickoff'; cnoShown passes now to cnoAgrees; RESEARCH 21 clarified (stream not wired)

## Do this next
ship.sh v0.15.5 (code 25), CI green, release, record, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9b34b97 ckpt 460: M1-M3: live +EV findings in RESEARCH 21 (not feasible on free feeds; measured)
  839495c ckpt 459: v0.15.4 released and recorded (L1-L4 done)
  9ebfebe ckpt 458: pre-release: v0.15.4: bet slips open without CNO (Novig's own catalog, 60/60 e
  d1ad63e ckpt 457: L1-L3 done; v0.15.4 code 24; forced floor 474 green (engine 39, data 305, app 
  3021174 ckpt 456: Logged Tj's live +EV request as M1-M3 (next version, after L4 ships); L3 in pr
  82074cb ckpt 455: L2 done: widget top-bar switch CNO only/Both, same bet shown once (outcome mat
  5cc41a5 ckpt 454: L1 done: taps race CNO + Novig catalog (first exact wins); links lane catalog-
  8b2086b ckpt 453: Logged Tj's 01:18Z requests as L1-L4 in TASKS.md
  8eb5cb1 ckpt 452: K10 done: v0.15.3 shipped (CI green 7b9636f, Release published, recorded); K1-
  7b9636f ckpt 451: pre-release: v0.15.3: full test: CNO pages parsed off the main thread (smoothe
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
