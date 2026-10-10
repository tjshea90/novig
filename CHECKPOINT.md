# CHECKPOINT 2810 — read me first, then TASKS.md

**Written:** 2026-10-10T01:13:12Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `30de2ca8` (this checkpoint is the commit after it)

## Just done
pre-release: v0.84.2: lab grading from final scores (SGO/OddsPapi/ESPN/MLB), paper bids, would-be bets (versionCode 157, v0.84.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.84.2), then run: bash tools/record-release.sh v0.84.2 157 "v0.84.2: lab grading from final scores (SGO/OddsPapi/ESPN/MLB), paper bids, would-be bets"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d6635253 ckpt 2809: analysed Tj's phone research file v0.84.1 (21h): recipes, slices, grading br
  62de3c1e ckpt 2808: archive everything: uploads to research/uploads, lab-digest branch, CLAUDE.m
  a09c68cf ckpt 2807: lab steward routine created, archive verified
  f7e985d0 ckpt 2806: lab steward protocol + permanent archive (release step, sweep workflow), CLA
  8a1c6370 ckpt 2805: pre-release: v0.84.1: wide-quote guard: a book or exchange quote whose two s
  3f7fd00e ckpt 2804: pre-ship: v0.84.1: wide-quote guard: a book or exchange quote whose two side
  5d9b3616 ckpt 2803: pre-release: v0.84.0: OddsPapi v5 ready to switch on: keys (rotated), toggle
  903d944c ckpt 2802: OddsPapi data layer built and compiling (client, parser, markets, books, con
  1029e4c0 ckpt 2801: pre-release: v0.83.7: Pinnodds awake in the app again (it was only Claude's 
  5e983eb0 ckpt 2800: pre-ship: v0.83.7: Pinnodds awake in the app again (it was only Claude's ses
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
