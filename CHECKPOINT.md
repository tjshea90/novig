# CHECKPOINT 2583 — read me first, then TASKS.md

**Written:** 2026-10-05T21:20:19Z · **tests:** all 3 fast checks green
**Branch:** `ccr-cb9fb4b3-95tfr2` · **builds on:** `bbe9c0ce` (this checkpoint is the commit after it)

## Just done
Tj asked to trigger the apk build: release.yml run #119 (id 37375200456) triggered on main 21:20Z; GitHub Actions still in a Major Outage (githubstatus 21:09Z)

## Do this next
when run 37375200456 is green: get_release_by_tag v0.67.0, bash tools/record-release.sh v0.67.0 114 '<ship note>', send Tj the Release link; never start a second run while one is queued; if it dies with no runner, re-trigger once after the outage clears (a send_later check-in at 21:35Z is armed)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6369ba81 ckpt 2582: CP4: swept, floor green locally, v0.67.0 on main; release blocked by a GitHu
  0f9447e6 ckpt 2581: v0.67.0 shipped to main (full floor 2157 passed locally); CI run 37369765480
  337e8cb1 ckpt 2580: pre-release: v0.67.0: longest odds a bid may be posted at (Bids rules: +140 
  6e77053e ckpt 2579: CP1-CP3 ticked; scratch screenshot test removed; version 0.67.0 code 114
  1f364251 ckpt 2578: CP1: RESEARCH §91 written (both sides fine; the pair = one bet 'not 3 runs'
  7fa3bd5f ckpt 2577: CP2: wash guard in MakerPlan (never two bids of ours on the two sides of a m
  fad2a893 ckpt 2576: CP3 built+tested: makerMaxOdds in MakerRules/outsideWindow (all bid paths), 
  d39aa899 ckpt 2575: CP3: longest-odds bid limit: rules + settings field + Bids settings chips/ty
  119cb5c1 ckpt 2574: TASKS.md: CP1-CP4 written from Tj's 19:53Z message (both sides of a prop gua
  d53f88d3 ckpt 2573: v0.66.0 released and recorded (CO1-CO7 done)
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
