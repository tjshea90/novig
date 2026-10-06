# CHECKPOINT 2601 — read me first, then TASKS.md

**Written:** 2026-10-06T01:13:10Z · **tests:** all 3 fast checks green
**Branch:** `ccr-3436e911-cyln4u` · **builds on:** `8ad73b7c` (this checkpoint is the commit after it)

## Just done
pre-release: v0.68.1: Low API usage bids stay up (Auto pace: a scan starts before the bids' fair goes old, resting-bid markets read first); the +EV tab, scan timeline and health checks say when Low API usage had nothing to read (versionCode 116, v0.68.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.68.1), then run: bash tools/record-release.sh v0.68.1 116 "v0.68.1: Low API usage bids stay up (Auto pace: a scan starts before the bids' fair goes old, resting-bid markets read first); the +EV tab, scan timeline and health checks say when Low API usage had nothing to read"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  10ad5b04 ckpt 2600: v0.68.1 ready: Auto pace + resting markets first + the +EV tab/timeline/heal
  9ee7b975 ckpt 2599: CR5 code + tests green (Auto pace, resting markets pinned, mutants killed); 
  edf8a14c ckpt 2598: CR1/CR2/CR5 code written (Auto pace + resting markets read first, 9 timeline
  e398422c ckpt 2597: CR4/CR5 written into TASKS.md (Tj: bids all cancel together; asks for a fres
  b5b20ce2 ckpt 2596: CR0: Tj reports Low API usage bids stay up ~2 min then cancel, no new ones; 
  f7ddc5d5 ckpt 2595: Scanning page: Vigilant's scan pace follows the low API usage pace (was alwa
  3360711f ckpt 2594: v0.68.0 released and recorded: CI 37383599447 green on 51b2462c, release.yml
  51b2462c ckpt 2593: pre-release: v0.68.0: Low API usage bids (Bids › Rules › Which bids go u
  dd8697f2 ckpt 2592: CQ4/CQ5 prep: RESEARCH 92.4 written, TASKS CQ1-CQ4 ticked, version 0.68.0 co
  eab22bd9 ckpt 2591: CQ4: bids tagged (focus, fairBooks, ages), BidReport splits, Diagnostics low
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
