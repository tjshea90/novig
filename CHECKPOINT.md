# CHECKPOINT 2183 — read me first, then TASKS.md

**Written:** 2026-09-30T06:55:43Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `9da64a10` (this checkpoint is the commit after it)

## Just done
pre-release: v0.31.0: Check odds now (and the closing-line capture) reads every open bet both ways, CNO's page and Vigilant's own fair odds incl. ParlayAPI, and averages them when both answer, so the +EV and beat-the-close stats read the fullest data; ParlayAPI Retry-After honored (versionCode 59, v0.31.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.31.0), then run: bash tools/record-release.sh v0.31.0 59 "v0.31.0: Check odds now (and the closing-line capture) reads every open bet both ways, CNO's page and Vigilant's own fair odds incl. ParlayAPI, and averages them when both answer, so the +EV and beat-the-close stats read the fullest data; ParlayAPI Retry-After honored"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c424cce8 ckpt 2182: O1 done: Check odds now + closing capture read every open bet both ways (CNO
  1cb92e7a ckpt 2181: Wrote Tj's 06:50Z request into TASKS.md as O1-O3 (Check odds now always adds
  e8b4fe3b ckpt 2180: Corrected the probe tally to 15 credits (19,867 -> 19,852)
  c0af9949 ckpt 2179: v0.30.0 (code 58) released (release.yml green, Release + APK confirmed) and 
  a6208d0f ckpt 2178: pre-release: v0.30.0: ParlayAPI: injury tags on prop bets; credits-a-day cha
  a6e3eb00 ckpt 2177: v0.30.0 (code 58) gated by ship.sh (1168 tests green) and pushed; M1-M5, M7 
  0d90eeca ckpt 2176: pre-release: v0.30.0: ParlayAPI features: injury tags on prop bets (ESPN via
  bfec9e2b ckpt 2175: M7 done (football+basketball): ParlayAPI 1st-half lines as source parlay_1h 
  1ecf0021 ckpt 2174: M5 done: ParlayAPI's picks at Novig on the +EV tab (tap-only best-bets, re-p
  ec0c2790 ckpt 2173: M5 data: ParlayBestBets (/best-bets?books=novig 10 cr/league, tap only, bet 
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
