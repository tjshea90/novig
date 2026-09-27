# CHECKPOINT 522 — read me first, then TASKS.md

**Written:** 2026-09-27T19:21:20Z · **tests:** all 1 fast checks green
**Branch:** `claude/betmgm-ev-scanner-h4yfqw` · **builds on:** `1ba3783` (this checkpoint is the commit after it)

## Just done
V1 data layer + V2 links: data/book (Sportsbook, BookBoard, SportsbookScanner, BetMgmLinks), OddsScanner interface (Scanner implements, ScanRunner takes it), PropLine relayNovig/bookIds opt-in params (Novig defaults unchanged), NovigBook.posted exact prices, BetTracker ownBook. Tests: SportsbookScannerTest (10), BetMgmLinksTest (4); engine 39 / data 399 (8 skipped) green

## Do this next
V3: BuildConfig.BOOK in app (novig) + new mgm module compiling app's sources (betmgm); book-aware UI copy; Novig-only parts off in MGM; CNO site 4

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f515f63 ckpt 521: Wrote Tj's BetMGM request into TASKS.md (V1-V5) with the design decision: sepa
  b663cd1 ckpt 520: SHIPPED v0.16.4 code 31 (T4+U4 ticked): floor engine 39 / data 385 (8 live ski
  7bd459b ckpt 519: pre-release: v0.16.4: no sportsbook odds over 5 minutes old ever price an EV (
  d81ae2a ckpt 518: Floor found PropLineClientTest last-seen test contradicting the withdrawn-outc
  ce2ec54 ckpt 517: U2/U3 ticked, RESEARCH §24.3, CLAUDE.md surface list, version v0.16.4 code 31
  6852c62 ckpt 516: U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere 
  0fec760 ckpt 515: U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quot
  af6c68b ckpt 514: U1 audit done: RESEARCH.md §24.1 (re-use windows 15-60 min, stale-limit keep,
  70c9568 ckpt 513: T1-T3 done: PropLine relays Novig's prices in the same calls (RefSnapshot.novi
  b9a59a9 ckpt 512: Wrote Tj's second request (no stale sportsbook odds in any comparison; after T
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
