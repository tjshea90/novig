# CHECKPOINT 2384 — read me first, then TASKS.md

**Written:** 2026-10-02T20:43:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `7bf0ce5c` (this checkpoint is the commit after it)

## Just done
AZ2+AZ3 done (TASKS ticked): TrackerLocksTest + LockedBetsTest green, mutants 8/8 killed; NOVIG_API.md §16 updated

## Do this next
AZ4: full floor (bash tools/test.sh), light sweep of Tracker screenshots, bump to v0.48.0 (code 86), ship.sh, CI, release.yml, record-release, answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M NOVIG_API.md
     M TASKS.md

## Last ten checkpoints
```
  25465506 ckpt 2383: AZ2/AZ3 code: LockedBets (markets/partly/ids/hide/stats) + LockStats, ScanSe
  cec190b2 ckpt 2382: AZ1 done (TASKS ticked): misses were missing Novig ids never retried, Replac
  bc1dbe75 ckpt 2381: AZ1 code + data tests: NovigBetFinder.locate, NovigIds (re-look up missing i
  97845a81 ckpt 2380: AZ1 investigation redone (workflow results lost with the old container): mis
  4179f52b ckpt 2379: AZ1 investigation workflow running (ids, read path, pricer, lock/stats map);
  c4867092 ckpt 2378: AZ: Tj's request (hide locked bets, lock stats, Novig-odds misses) written i
  4061e276 ckpt 2377: AY done: v0.47.0 released, verified, recorded (locks + auto-lock + Novig-onl
  bbd3d8b0 ckpt 2376: pre-release: v0.47.0: lock in a profit on bets placed through Vigilant once 
  1738e581 ckpt 2375: floor green on v0.47.0 (1,736: 1,713 passed, 23 skipped)
  971f4c34 ckpt 2374: floor had 2 StickyHeadersTest fails (Novig-only row made the pinned bar too 
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
