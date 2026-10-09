# CHECKPOINT 2791 — read me first, then TASKS.md

**Written:** 2026-10-09T16:10:51Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `c565a9e5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.83.2: SportsGameOdds actually prices the scan (sgo was missing from the enabled sources), Caesars/William Hill/Hard Rock book ids fixed, usage parser matches the real answer, startsBefore + paged reads, tape ages use median/p90 (versionCode 149, v0.83.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.83.2), then run: bash tools/record-release.sh v0.83.2 149 "v0.83.2: SportsGameOdds actually prices the scan (sgo was missing from the enabled sources), Caesars/William Hill/Hard Rock book ids fixed, usage parser matches the real answer, startsBefore + paged reads, tape ages use median/p90"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c565a9e5 ckpt 2790: pre-ship: v0.83.2: SportsGameOdds actually prices the scan (sgo was missing 
  7488534c ckpt 2789: pre-release: v0.83.1: SportsGameOdds re-check against the docs: book filter 
  237c3d7f ckpt 2788: pre-ship: v0.83.1: SportsGameOdds re-check against the docs: book filter (SG
  b8d4c306 ckpt 2787: v0.83.0 shipped; GitHub lab proven end to end (run 37906589603 wrote lab-dat
  0d1e8da1 ckpt 2786: lab: run in the repo root so out/ is where the workflow looks
  61e5c25b ckpt 2785: lab workflow: state checkout moved off the :data directory (it emptied the m
  afa164c1 ckpt 2784: lab workflow fix: evaluationDependsOn(:data), --no-configure-on-demand, save
  8b211e22 ckpt 2783: pre-release: v0.83.0: GitHub research lab (lab module + lab-record.yml: scan
  e3669839 ckpt 2782: SGO: spec fixes (403/429/closes 504), injuries+other-books via SGO, toggle-o
  3f89f9f4 ckpt 2781: pre-release: v0.82.0: SportsGameOdds Pro: key list (rotated like the others)
```
