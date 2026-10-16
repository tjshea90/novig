# CHECKPOINT 3500 — read me first, then TASKS.md

**Written:** 2026-10-16T21:54:52Z · **tests:** all 5 fast checks green
**Branch:** `ccr-49ded3ac-g4ipzt` · **builds on:** `c15eeee1a` (this checkpoint is the commit after it)

## Just done
pre-release: PropLine +EV shown after Vigilant's prop cut + hit-rate trends; spent API keys show a full red bar; chat no longer blank with the keyboard open; filter chips wrap (All agree, PropLine leagues); PropLine steam workflow (versionCode 308, v0.114.12)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.12), then run: bash tools/record-release.sh v0.114.12 308 "PropLine +EV shown after Vigilant's prop cut + hit-rate trends; spent API keys show a full red bar; chat no longer blank with the keyboard open; filter chips wrap (All agree, PropLine leagues); PropLine steam workflow"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  41d29b50c ckpt 3499: TCL52a: FlowRow chips (All agree, PropLine filters)
  85451736c ckpt 3498: propline probe: trends by name fallback; real /results and /clv/grade shape
  92c0abb40 ckpt 3497: TCL65c: hit-rate trends on PropLine screen, steam/movement workflow, probe 
  6c17decce ckpt 3496: TCL65b: investigation + calibrated PropLine screen + backtest script
  41debb67c ckpt 3495: TCL64a partial: PropLine rows matched to all priced lines; chat watcher run
  060a3257b ckpt 3494: TCL63: spent-key bars full red, chat keyboard inset fix, PropLine review se
  f5887f98d ckpt 3493: TCL61: API manager scorecard section on the Engine dashboard (compiles)
  571f774e5 ckpt 3492: engine restarted with 14 Odds keys; handoff rule; TCL62 done
  2eb025303 ckpt 3491: TCL61 core: scorecard, paid-first ranking, surplus second opinions, Odds AP
  5bd900c43 ckpt 3490: TCL61f ParlayAPI finding + reply
```
