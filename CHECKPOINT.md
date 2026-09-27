# CHECKPOINT 462 — read me first, then TASKS.md

**Written:** 2026-09-27T02:48:52Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `405f267` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.5: full test: the Novig stream no longer reports a failure after a deliberate close or breaks a quick reconnect; 'only bets the books agree on' with Novig's price now no longer flickers; CNO cards keep full-size placed/remove buttons (top right), warn in orange when Novig's price now is under your minimum, and sort by the EV shown; updated Settings, CNO and Tracker wording. 480 tests (versionCode 25, v0.15.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.5), then run: bash tools/record-release.sh v0.15.5 25 "v0.15.5: full test: the Novig stream no longer reports a failure after a deliberate close or breaks a quick reconnect; 'only bets the books agree on' with Novig's price now no longer flickers; CNO cards keep full-size placed/remove buttons (top right), warn in orange when Novig's price now is under your minimum, and sort by the EV shown; updated Settings, CNO and Tracker wording. 480 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  405f267 ckpt 461: Full test (Tj 02:36Z): floor 474->480 green; fixed (each test failed on the ol
  9b34b97 ckpt 460: M1-M3: live +EV findings in RESEARCH 21 (not feasible on free feeds; measured)
  839495c ckpt 459: v0.15.4 released and recorded (L1-L4 done)
  9ebfebe ckpt 458: pre-release: v0.15.4: bet slips open without CNO (Novig's own catalog, 60/60 e
  d1ad63e ckpt 457: L1-L3 done; v0.15.4 code 24; forced floor 474 green (engine 39, data 305, app 
  3021174 ckpt 456: Logged Tj's live +EV request as M1-M3 (next version, after L4 ships); L3 in pr
  82074cb ckpt 455: L2 done: widget top-bar switch CNO only/Both, same bet shown once (outcome mat
  5cc41a5 ckpt 454: L1 done: taps race CNO + Novig catalog (first exact wins); links lane catalog-
  8b2086b ckpt 453: Logged Tj's 01:18Z requests as L1-L4 in TASKS.md
  8eb5cb1 ckpt 452: K10 done: v0.15.3 shipped (CI green 7b9636f, Release published, recorded); K1-
```
