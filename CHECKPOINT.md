# CHECKPOINT 2493 — read me first, then TASKS.md

**Written:** 2026-10-03T22:41:07Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e9ab2595-yxxu6w` · **builds on:** `162485c7` (this checkpoint is the commit after it)

## Just done
pre-release: v0.58.0: the scan study also logs what CNO's filters hide (a second, wide CNO read; the app's list, alerts, auto-bet and widget unchanged), flagged shown or hidden with why, with every CNO column; Settings switch (versionCode 100, v0.58.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.58.0), then run: bash tools/record-release.sh v0.58.0 100 "v0.58.0: the scan study also logs what CNO's filters hide (a second, wide CNO read; the app's list, alerts, auto-bet and widget unchanged), flagged shown or hidden with why, with every CNO column; Settings switch"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  162485c7 ckpt 2492: BQ done in code: v0.58.0 (code 100) prepared; floor green (1,919 passed, 23 
  698f3b3c ckpt 2491: BQ2 part 2: study wide path (w/xw sights, NOT_LISTED, cols, lean hydrate+fol
  df2afb76 ckpt 2490: BQ2 part 1: CnoClient.fetchWide (own session, opened filters, columns kept),
  9c963c5a ckpt 2489: BQ1 done: wide-read design (second CNO session, w sights, cols, NOT_LISTED) 
  5638fd93 ckpt 2488: BQ: Tj's request to log ALL CNO finds in the scan study (even ones filtered 
  7278307f ckpt 2487: v0.57.0 (scan study) released + recorded; BO (v0.56.2) and BP complete
  167b1ac1 ckpt 2486: pre-release: v0.57.0: scan study: every bet a CNO or Vigilant scan lists is 
  831086dd ckpt 2485: study READ ME: no 'rank in the list' (not recorded), bids margin not hard-co
  e0ae8f5b ckpt 2484: study export: the fields Tj named (kind, minutes to the start, CNO books, bo
  d20871f6 ckpt 2483: v0.57.0 prepared: version 99; floor green (1,887 passed, 23 skipped); screen
```
