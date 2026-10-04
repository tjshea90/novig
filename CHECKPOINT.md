# CHECKPOINT 2513 — read me first, then TASKS.md

**Written:** 2026-10-04T03:29:41Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e34b35d5-c6t9c8` · **builds on:** `38783e53` (this checkpoint is the commit after it)

## Just done
pre-release: v0.58.4: a close from another game can no longer count (a Washington State moneyline 'closed' at another State game's price: team matching needed one team to really match, and the best-fitting game now wins); closes that can't be the bet's are left out of CLV, Tracker and study alike; the study file says which filters decide the shown list and whose fair line a Tracker close is (versionCode 104, v0.58.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.58.4), then run: bash tools/record-release.sh v0.58.4 104 "v0.58.4: a close from another game can no longer count (a Washington State moneyline 'closed' at another State game's price: team matching needed one team to really match, and the best-fitting game now wins); closes that can't be the bet's are left out of CLV, Tracker and study alike; the study file says which filters decide the shown list and whose fair line a Tracker close is"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
  d0334f7d ckpt 2507: pre-release: v0.58.3: bids are kept within the wallet and the day's limit (a
  c80df9fc ckpt 2506: pre-ship: v0.58.3: bids are kept within the wallet and the day's limit (a be
  baa4035c ckpt 2505: BV built: RateGate.takeLowRate, ReadPace on BookBatch (public/key start/low/
  d37ac654 ckpt 2504: BU app tests green (MakerAppTest + WalletStripTest 23 passed). Tj sent diagn
  34f91bf4 ckpt 2503: BU mutants 11/11 killed (M4/M6 needed stronger tests); app layer written: Ma
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
