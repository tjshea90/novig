# CHECKPOINT 3508 — read me first, then TASKS.md

**Written:** 2026-10-16T22:18:42Z · **tests:** all 5 fast checks green
**Branch:** `ccr-49ded3ac-g4ipzt` · **builds on:** `76d27c152` (this checkpoint is the commit after it)

## Just done
pre-release: PropLine is now a grading source (box scores, scores) in the app and the engine's GitHub grader; API manager shows how many lines each API fed and how many only it fed (versionCode 309, v0.114.13)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.13), then run: bash tools/record-release.sh v0.114.13 309 "PropLine is now a grading source (box scores, scores) in the app and the engine's GitHub grader; API manager shows how many lines each API fed and how many only it fed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  70b472c6f ckpt 3507: TCL67b: PropLineScores verified on real shapes
  347aaced5 ckpt 3506: fix probe indentation
  c4b130175 ckpt 3505: TCL67: PropLineScores grading source + CLAUDE.md autonomy rule
  0fb4f7cc7 ckpt 3504: TCL61: unique-contribution (lines fed / only it) on the API scorecard
  db32d6f1c ckpt 3503: TCL56/64/65 closed; CLAUDE.md PropLine section updated
  1ee1f392b ckpt 3502: research: open-vs-close study from PropLine archive; CHANGES line
  a5fb82a8d ckpt 3501: PropLineTrends verified against real /trends answer; v0.114.12 shipped
  68ec8c961 ckpt 3500: pre-release: PropLine +EV shown after Vigilant's prop cut + hit-rate trends
  41d29b50c ckpt 3499: TCL52a: FlowRow chips (All agree, PropLine filters)
  85451736c ckpt 3498: propline probe: trends by name fallback; real /results and /clv/grade shape
```
