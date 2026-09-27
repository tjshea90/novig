# CHECKPOINT 519 — read me first, then TASKS.md

**Written:** 2026-09-27T18:02:01Z · **tests:** all 1 fast checks green
**Branch:** `claude/resume-interrupted-session-tuvmsn` · **builds on:** `b255a01` (this checkpoint is the commit after it)

## Just done
pre-release: v0.16.4: no sportsbook odds over 5 minutes old ever price an EV (re-use capped at 2 min, feed/widget/mini/Games drop old EVs, CNO rows hidden while CNO's odds are old); PropLine's Novig prices order the Novig reads with automatic fallback. 585 tests (versionCode 31, v0.16.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.16.4), then run: bash tools/record-release.sh v0.16.4 31 "v0.16.4: no sportsbook odds over 5 minutes old ever price an EV (re-use capped at 2 min, feed/widget/mini/Games drop old EVs, CNO rows hidden while CNO's odds are old); PropLine's Novig prices order the Novig reads with automatic fallback. 585 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d81ae2a ckpt 518: Floor found PropLineClientTest last-seen test contradicting the withdrawn-outc
  ce2ec54 ckpt 517: U2/U3 ticked, RESEARCH §24.3, CLAUDE.md surface list, version v0.16.4 code 31
  6852c62 ckpt 516: U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere 
  0fec760 ckpt 515: U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quot
  af6c68b ckpt 514: U1 audit done: RESEARCH.md §24.1 (re-use windows 15-60 min, stale-limit keep,
  70c9568 ckpt 513: T1-T3 done: PropLine relays Novig's prices in the same calls (RefSnapshot.novi
  b9a59a9 ckpt 512: Wrote Tj's second request (no stale sportsbook odds in any comparison; after T
  2abdfb9 ckpt 511: Wrote Tj's request (PropLine's Novig prices order Novig reads, fallback to ori
  c15cb5c ckpt 510: SHIPPED v0.16.3 code 30 (S1-S5 ticked): https://github.com/tjshea90/novig/rele
  e46361a ckpt 509: pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
