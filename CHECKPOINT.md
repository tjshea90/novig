# CHECKPOINT 2581 — read me first, then TASKS.md

**Written:** 2026-10-05T20:43:56Z · **tests:** all 3 fast checks green
**Branch:** `ccr-cb9fb4b3-95tfr2` · **builds on:** `337e8cb1` (this checkpoint is the commit after it)

## Just done
v0.67.0 shipped to main (full floor 2157 passed locally); CI run 37369765480 and release.yml run 37371189756 both stuck 'queued' (GitHub runner backlog)

## Do this next
when release run 37371189756 completes green: mcp__github__get_release_by_tag v0.67.0, then bash tools/record-release.sh v0.67.0 114 '<note from the pre-release ckpt>', then send Tj the Release link + the plain answers (both sides fine, window pair = one bet, wash guard built, longest-odds setting built). If the run is cancelled/failed, re-trigger release.yml on main

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  337e8cb1 ckpt 2580: pre-release: v0.67.0: longest odds a bid may be posted at (Bids rules: +140 
  6e77053e ckpt 2579: CP1-CP3 ticked; scratch screenshot test removed; version 0.67.0 code 114
  1f364251 ckpt 2578: CP1: RESEARCH §91 written (both sides fine; the pair = one bet 'not 3 runs'
  7fa3bd5f ckpt 2577: CP2: wash guard in MakerPlan (never two bids of ours on the two sides of a m
  fad2a893 ckpt 2576: CP3 built+tested: makerMaxOdds in MakerRules/outsideWindow (all bid paths), 
  d39aa899 ckpt 2575: CP3: longest-odds bid limit: rules + settings field + Bids settings chips/ty
  119cb5c1 ckpt 2574: TASKS.md: CP1-CP4 written from Tj's 19:53Z message (both sides of a prop gua
  d53f88d3 ckpt 2573: v0.66.0 released and recorded (CO1-CO7 done)
  dcaaa801 ckpt 2572: pre-release: v0.66.0: API audit against the providers' own docs (RESEARCH §
  e8e00ce0 ckpt 2571: pre-ship: v0.66.0: API audit against the providers' own docs (RESEARCH §90)
```
