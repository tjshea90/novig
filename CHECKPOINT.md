# CHECKPOINT 2386 — read me first, then TASKS.md

**Written:** 2026-10-02T20:48:01Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `dfdc9a12` (this checkpoint is the commit after it)

## Just done
pre-release: v0.48.0: locked markets hidden from the Tracker's lists and stats (switch, on by default) with their own Locked in card (bets locked and %, profit locked and %); open bets missing Novig's odds fixed (missing Novig ids looked up again, one-sided books priced, each miss says why) (versionCode 86, v0.48.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.48.0), then run: bash tools/record-release.sh v0.48.0 86 "v0.48.0: locked markets hidden from the Tracker's lists and stats (switch, on by default) with their own Locked in card (bets locked and %, profit locked and %); open bets missing Novig's odds fixed (missing Novig ids looked up again, one-sided books priced, each miss says why)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  dfdc9a12 ckpt 2385: pre-ship: v0.48.0: locked markets hidden from the Tracker's lists and stats 
  4d2c5867 ckpt 2384: AZ2+AZ3 done (TASKS ticked): TrackerLocksTest + LockedBetsTest green, mutant
  25465506 ckpt 2383: AZ2/AZ3 code: LockedBets (markets/partly/ids/hide/stats) + LockStats, ScanSe
  cec190b2 ckpt 2382: AZ1 done (TASKS ticked): misses were missing Novig ids never retried, Replac
  bc1dbe75 ckpt 2381: AZ1 code + data tests: NovigBetFinder.locate, NovigIds (re-look up missing i
  97845a81 ckpt 2380: AZ1 investigation redone (workflow results lost with the old container): mis
  4179f52b ckpt 2379: AZ1 investigation workflow running (ids, read path, pricer, lock/stats map);
  c4867092 ckpt 2378: AZ: Tj's request (hide locked bets, lock stats, Novig-odds misses) written i
  4061e276 ckpt 2377: AY done: v0.47.0 released, verified, recorded (locks + auto-lock + Novig-onl
  bbd3d8b0 ckpt 2376: pre-release: v0.47.0: lock in a profit on bets placed through Vigilant once 
```
