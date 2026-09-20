# CHECKPOINT 123 — read me first, then TASKS.md

**Written:** 2026-09-20T06:09:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `28dea40` (this checkpoint is the commit after it)

## Just done
CI confirmed fully green (run 35493330913): all tests across engine+data+app pass, assembleDebug succeeds, produced a real 9.3MB debug APK artifact. Fixed 3 real bugs along the way: android-actions/setup-android@v3 crashes on a removed legacy SDK package (dropped it, write license hashes directly instead), kotlinOptions.jvmTarget is a hard error on Kotlin 2.3.10 (migrated to the compilerOptions DSL), and ScannerViewModel's all-default constructor needed @JvmOverloads or by viewModels()'s reflection-based factory would crash at runtime despite compiling clean. Recorded all three in BRIEF.md's Build traps section. TASKS.md and BRIEF.md updated with final confirmed status.

## Do this next
The Vigilant beta is done and verified for this request: 79 total tests green (54 local + app module's via CI), real debug APK building in CI. Tell Tj the beta is ready, what he needs to do to go live (Odds API signup + contact Novig), and offer to watch the CI workflow going forward if he wants. Nothing else blocking — next real work is either going live (pending Tj's two action items) or Tj directing what to build next.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  49c1307 ckpt 96: Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine
  3edb9b3 ckpt 29: Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already capt
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
  e870ad3 ckpt 12: Stood up the full checkpoint/handoff system for novig, adapted from fantasy-foo
```

(26 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
