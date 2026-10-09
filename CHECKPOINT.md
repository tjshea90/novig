# CHECKPOINT 2792 — read me first, then TASKS.md

**Written:** 2026-10-09T16:21:49Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `598d93d7` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.83.3: SGO Pro mode freshness: 10-minute guard for every game while the switch is on (SGO refreshes about every 5 min); off is unchanged

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  316c6d8a ckpt 2791: pre-release: v0.83.2: SportsGameOdds actually prices the scan (sgo was missi
  c565a9e5 ckpt 2790: pre-ship: v0.83.2: SportsGameOdds actually prices the scan (sgo was missing 
  7488534c ckpt 2789: pre-release: v0.83.1: SportsGameOdds re-check against the docs: book filter 
  237c3d7f ckpt 2788: pre-ship: v0.83.1: SportsGameOdds re-check against the docs: book filter (SG
  b8d4c306 ckpt 2787: v0.83.0 shipped; GitHub lab proven end to end (run 37906589603 wrote lab-dat
  0d1e8da1 ckpt 2786: lab: run in the repo root so out/ is where the workflow looks
  61e5c25b ckpt 2785: lab workflow: state checkout moved off the :data directory (it emptied the m
  afa164c1 ckpt 2784: lab workflow fix: evaluationDependsOn(:data), --no-configure-on-demand, save
  8b211e22 ckpt 2783: pre-release: v0.83.0: GitHub research lab (lab module + lab-record.yml: scan
  e3669839 ckpt 2782: SGO: spec fixes (403/429/closes 504), injuries+other-books via SGO, toggle-o
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
