# CHECKPOINT 2985 — read me first, then TASKS.md

**Written:** 2026-10-11T17:50:37Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `8d8477e19` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.21: a minimum star rating in Auto-bet now decides which bets are placed (edge, odds, kinds, books-agree, sharp, trap-guard and small-prop filters are ignored while it is on; Any enforces them again); dollar limits, pregame, fresh prices, +EV floor, injuries and the 15% cap still apply (versionCode 206, v0.89.21)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.21), then run: bash tools/record-release.sh v0.89.21 206 "v0.89.21: a minimum star rating in Auto-bet now decides which bets are placed (edge, odds, kinds, books-agree, sharp, trap-guard and small-prop filters are ignored while it is on; Any enforces them again); dollar limits, pregame, fresh prices, +EV floor, injuries and the 15% cap still apply"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e7cbe3118 ckpt 2984: pre-ship: v0.89.21: a minimum star rating in Auto-bet now decides which bet
  b91c3a22d ckpt 2983: TP done: star rating overrides the selection filters (3 lanes), UI note, te
  f69319c26 ckpt 2982: TP: wrote the star-rating-overrides-filters request into TASKS.md; v0.89.20
  96de1ae98 ckpt 2981: pre-release: v0.89.20: bets by hand on live games (the Bet sheet no longer 
  30bddb6b4 ckpt 2980: pre-ship: v0.89.20: bets by hand on live games (the Bet sheet no longer ref
  bb685c195 ckpt 2979: TO2 done with tests; docs (NOVIG_API §24, RESEARCH 132.3, CLAUDE.md); TO3/
  bc1e90049 ckpt 2978: TO2: manual live bets (planner prices live fee, live note, betRefused codes
  d4d5a3f69 ckpt 2977: TO: wrote Tj's live-betting request into TASKS.md; answer = Vigilant rule n
  4c1454c14 ckpt 2976: v0.89.19 released (TL, TM, TN done and shipped)
  62fef5926 ckpt 2975: pre-release: v0.89.19: every API toggled on is used for any data and one to
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
