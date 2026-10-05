# CHECKPOINT 2580 — read me first, then TASKS.md

**Written:** 2026-10-05T20:26:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-cb9fb4b3-95tfr2` · **builds on:** `6e77053e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.67.0: longest odds a bid may be posted at (Bids rules: +140 = no bid at +141 or longer; default No limit); wash guard (never two of our bids on the two sides of one market that add to $1+, also on the Post button); both-sides research RESEARCH §91 (Over+Under of one prop is fine: one bet, EV adds, variance lower) (versionCode 114, v0.67.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.67.0), then run: bash tools/record-release.sh v0.67.0 114 "v0.67.0: longest odds a bid may be posted at (Bids rules: +140 = no bid at +141 or longer; default No limit); wash guard (never two of our bids on the two sides of one market that add to $1+, also on the Post button); both-sides research RESEARCH §91 (Over+Under of one prop is fine: one bet, EV adds, variance lower)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6e77053e ckpt 2579: CP1-CP3 ticked; scratch screenshot test removed; version 0.67.0 code 114
  1f364251 ckpt 2578: CP1: RESEARCH §91 written (both sides fine; the pair = one bet 'not 3 runs'
  7fa3bd5f ckpt 2577: CP2: wash guard in MakerPlan (never two bids of ours on the two sides of a m
  fad2a893 ckpt 2576: CP3 built+tested: makerMaxOdds in MakerRules/outsideWindow (all bid paths), 
  d39aa899 ckpt 2575: CP3: longest-odds bid limit: rules + settings field + Bids settings chips/ty
  119cb5c1 ckpt 2574: TASKS.md: CP1-CP4 written from Tj's 19:53Z message (both sides of a prop gua
  d53f88d3 ckpt 2573: v0.66.0 released and recorded (CO1-CO7 done)
  dcaaa801 ckpt 2572: pre-release: v0.66.0: API audit against the providers' own docs (RESEARCH §
  e8e00ce0 ckpt 2571: pre-ship: v0.66.0: API audit against the providers' own docs (RESEARCH §90)
  93cffb3f ckpt 2570: docs: RESEARCH 90.8 built/left, NOVIG_API batch use, CO1-CO7 ticked
```
