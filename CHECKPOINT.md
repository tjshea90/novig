# CHECKPOINT 2380 — read me first, then TASKS.md

**Written:** 2026-10-02T20:21:56Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `21bbaf4d` (this checkpoint is the commit after it)

## Just done
AZ1 investigation redone (workflow results lost with the old container): misses are (1) bets w/o Novig marketId (logCno passes none; one-shot betFinder.find at log time only; logAlert/importPlaced CNO too) silently skipped by NovigNow.priceable; (2) Replace's rememberOutcome fills outcomeId only, never marketId; (3) NovigNow.mid null with a bid but no offer; (4) no per-bet reason, view says 'tap Check Novig now' forever

## Do this next
AZ1 code: NovigIds repair (betFinder from BetRecheck.rowOf) before Novig-only read and Check odds now; mid falls back to bid; per-bet novigWhy reasons; then AZ2 LockedMarkets + hide option, AZ3 lock stats

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4179f52b ckpt 2379: AZ1 investigation workflow running (ids, read path, pricer, lock/stats map);
  c4867092 ckpt 2378: AZ: Tj's request (hide locked bets, lock stats, Novig-odds misses) written i
  4061e276 ckpt 2377: AY done: v0.47.0 released, verified, recorded (locks + auto-lock + Novig-onl
  bbd3d8b0 ckpt 2376: pre-release: v0.47.0: lock in a profit on bets placed through Vigilant once 
  1738e581 ckpt 2375: floor green on v0.47.0 (1,736: 1,713 passed, 23 skipped)
  971f4c34 ckpt 2374: floor had 2 StickyHeadersTest fails (Novig-only row made the pinned bar too 
  3a659c13 ckpt 2373: AY6 done + docs: NovigNow, Tracker Novig-only chip, diagnostics fields, NOVI
  cb1d3514 ckpt 2372: AY6 code: TrackedBet novigFair/novigAtMs/novigClose(+At), NovigNow (mid, app
  0cacac9f ckpt 2371: AY3-AY5 done: lock scanner, by-hand lock card, auto-lock, stats; LockAppTest
  d3f03028 ckpt 2370: AY4/AY5 UI: LockCard + confirm on the bet sheet, Tracker lock badge, Auto-be
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
