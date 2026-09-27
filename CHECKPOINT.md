# CHECKPOINT 515 — read me first, then TASKS.md

**Written:** 2026-09-27T17:16:31Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `0794837` (this checkpoint is the commit after it)

## Just done
U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quote's last-seen (never after fetch), prices only quotes seen <=5 min before, re-use capped at 2 min, recheck/reprice judge age now, Opportunity.fairAsOfMs/fairIsOld. FreshOddsTest: 4 stale-path tests FAILED on old code, pass now; scanner tests' fakes use Fixtures.oddsApiSeenNow. data 384/0

## Do this next
U3 part 2: PropLine last_seen_at as each quote's time; source-internal re-use caps (PropLine props 10m, Odds API props 60m) + settings choices/defaults; UI expiry (feed/widget/mini/Games/sheet), recheck->scan when fair too old; CNO rows + green check <=5 min

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  af6c68b ckpt 514: U1 audit done: RESEARCH.md §24.1 (re-use windows 15-60 min, stale-limit keep,
  70c9568 ckpt 513: T1-T3 done: PropLine relays Novig's prices in the same calls (RefSnapshot.novi
  b9a59a9 ckpt 512: Wrote Tj's second request (no stale sportsbook odds in any comparison; after T
  2abdfb9 ckpt 511: Wrote Tj's request (PropLine's Novig prices order Novig reads, fallback to ori
  c15cb5c ckpt 510: SHIPPED v0.16.3 code 30 (S1-S5 ticked): https://github.com/tjshea90/novig/rele
  e46361a ckpt 509: pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as
  51e51af ckpt 508: S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567
  c066f28 ckpt 507: S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; w
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
  87963fb ckpt 505: S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheade
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
