# CHECKPOINT 2722 — read me first, then TASKS.md

**Written:** 2026-10-07T22:44:08Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f8e1d0b1-qlnoqa` · **builds on:** `6a80d377` (this checkpoint is the commit after it)

## Just done
pre-release: v0.75.0: bids can be priced from CrazyNinjaOdds alone, Vigilant's scan off. Settings › Bids › Bids priced from (default Vigilant's scan): the background cycle reads CNO's list, a wider list (sides at 0% EV and up) and the game pages of the games worth a bid; a sharp book on the page must agree and the bid sits under the lower of CNO's fair and the sharp book's own; a bid rests only while the older of CNO's list and page data is under an age limit (default 120 s; an unknown age counts as old); CNO pausing, going late or unreadable takes every CNO bid down; CNO bids carry source cno and the age of CNO's data, split in Diagnostics, the BIDS section and the scan study; a bet by hand on a side with a bid resting there now takes the bid down (versionCode 133, v0.75.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.75.0), then run: bash tools/record-release.sh v0.75.0 133 "v0.75.0: bids can be priced from CrazyNinjaOdds alone, Vigilant's scan off. Settings › Bids › Bids priced from (default Vigilant's scan): the background cycle reads CNO's list, a wider list (sides at 0% EV and up) and the game pages of the games worth a bid; a sharp book on the page must agree and the bid sits under the lower of CNO's fair and the sharp book's own; a bid rests only while the older of CNO's list and page data is under an age limit (default 120 s; an unknown age counts as old); CNO pausing, going late or unreadable takes every CNO bid down; CNO bids carry source cno and the age of CNO's data, split in Diagnostics, the BIDS section and the scan study; a bet by hand on a side with a bid resting there now takes the bid down"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6a80d377 ckpt 2721: DM4: 23 mutants killed (age rule, orientation, sharp requirement, unknown ag
  2b31e45d ckpt 2720: DM3c done: Bids tab (Bids priced from chip, CNO age limit chips, CNO status 
  04317943 ckpt 2719: DM3b done: CnoBidLane (data: wide read + page lane with a 3-page/step budget
  f555eb44 ckpt 2718: DM3a done: BidSource + makerSource + makerCnoMaxAgeSeconds + bidsFromCno in 
  886a28fc ckpt 2717: DM1 done: wrote RESEARCH.md §113 (verdict: CNO prices are already old when 
  75aa246a ckpt 2716: DM1 findings so far written into TASKS.md (one page-wide CNO age, missing ag
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
  3e800174 ckpt 2714: DM1 research workflow wf_edc6b432-17e launched (7 read-only scouts: app age 
  bd668740 ckpt 2713: Tj's yes to CNO-only auto bids written into TASKS.md as DM1-DM5 (research ho
  b9874dc4 ckpt 2712: v0.74.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
```
