# CHECKPOINT 2383 — read me first, then TASKS.md

**Written:** 2026-10-02T20:38:00Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `a8a2af6f` (this checkpoint is the commit after it)

## Just done
AZ2/AZ3 code: LockedBets (markets/partly/ids/hide/stats) + LockStats, ScanSettings.trackerHideLocked (default on), Tracker: hide chip + filtered lists/stats + Locked in card, Auto-bet tab switch, Diagnostics lock + Novig-price lines; LockedBetsTest green

## Do this next
UI test for Tracker hide/lock card (TrackerLocksTest, Robolectric like TrackerNovigOnlyTest), TrackerText lock caption test, then mutants, full floor, ship v0.48.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cec190b2 ckpt 2382: AZ1 done (TASKS ticked): misses were missing Novig ids never retried, Replac
  bc1dbe75 ckpt 2381: AZ1 code + data tests: NovigBetFinder.locate, NovigIds (re-look up missing i
  97845a81 ckpt 2380: AZ1 investigation redone (workflow results lost with the old container): mis
  4179f52b ckpt 2379: AZ1 investigation workflow running (ids, read path, pricer, lock/stats map);
  c4867092 ckpt 2378: AZ: Tj's request (hide locked bets, lock stats, Novig-odds misses) written i
  4061e276 ckpt 2377: AY done: v0.47.0 released, verified, recorded (locks + auto-lock + Novig-onl
  bbd3d8b0 ckpt 2376: pre-release: v0.47.0: lock in a profit on bets placed through Vigilant once 
  1738e581 ckpt 2375: floor green on v0.47.0 (1,736: 1,713 passed, 23 skipped)
  971f4c34 ckpt 2374: floor had 2 StickyHeadersTest fails (Novig-only row made the pinned bar too 
  3a659c13 ckpt 2373: AY6 done + docs: NovigNow, Tracker Novig-only chip, diagnostics fields, NOVI
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
