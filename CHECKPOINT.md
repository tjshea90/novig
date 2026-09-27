# CHECKPOINT 518 — read me first, then TASKS.md

**Written:** 2026-09-27T17:46:43Z · **tests:** all 1 fast checks green
**Branch:** `claude/resume-interrupted-session-tuvmsn` · **builds on:** `ee94afe` (this checkpoint is the commit after it)

## Just done
Floor found PropLineClientTest last-seen test contradicting the withdrawn-outcome rule (parser right, fixture wrong): test rewritten (market 9 min, older side 8 min -> 8 min; fails on v0.16.3 code which used the market's time). KDoc fixes: ScanSettings.staleReferenceMinutes (now only a failed feed's read-order memory), orphan enabledSources doc; RESEARCH 24.2 re-read every 4 min. SDK + mirror set up (BRIEF trap 6).

## Do this next
Floor rerun (--continue, -Pscreenshots) in progress: check XML counts + exit, look at feed/CNO/widget PNGs, assembleRelease, then tick T4/U4 and ship v0.16.4 code 31

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ce2ec54 ckpt 517: U2/U3 ticked, RESEARCH §24.3, CLAUDE.md surface list, version v0.16.4 code 31
  6852c62 ckpt 516: U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere 
  0fec760 ckpt 515: U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quot
  af6c68b ckpt 514: U1 audit done: RESEARCH.md §24.1 (re-use windows 15-60 min, stale-limit keep,
  70c9568 ckpt 513: T1-T3 done: PropLine relays Novig's prices in the same calls (RefSnapshot.novi
  b9a59a9 ckpt 512: Wrote Tj's second request (no stale sportsbook odds in any comparison; after T
  2abdfb9 ckpt 511: Wrote Tj's request (PropLine's Novig prices order Novig reads, fallback to ori
  c15cb5c ckpt 510: SHIPPED v0.16.3 code 30 (S1-S5 ticked): https://github.com/tjshea90/novig/rele
  e46361a ckpt 509: pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as
  51e51af ckpt 508: S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
