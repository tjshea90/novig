# CHECKPOINT 2731 — read me first, then TASKS.md

**Written:** 2026-10-08T02:26:51Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f7044881-1c7epp` · **builds on:** `de005ea4` (this checkpoint is the commit after it)

## Just done
v0.76.1 ready: LiveFeedService (foreground service holding the CPU while the live feed is on, 4 tests + 3 mutants killed; found a test deadlock and the repo rule that every notification carries the wallet line), doubleheader-safe matching (stage predicate inside matchEvents + nearest start), alternate-line changes no longer arm a game; version 0.76.1 code 135; floor green 2683

## Do this next
NEXT: wait for CI green on this commit (gh run watch), bash ship.sh, trigger release.yml (mcp actions_run_trigger), get_release_by_tag v0.76.1, tools/record-release.sh v0.76.1 135, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b6b1c150 ckpt 2730: pre-release: v0.76.0: Pinnodds live (Pinnacle vs Novig): Pinnodds WebSocket 
  3e95a5d8 ckpt 2729: Pinnodds live ready to ship: all-sports league list, final pooled study (52 
  61095455 ckpt 2728: Pinnodds live: UI test + settings search entries, MockWebServer tests for th
  9a4d414e ckpt 2727: Pinnodds live: app wiring done (ApiProvider.PINNODDS, ScanSettings pinnLive*
  6136ef32 ckpt 2726: PINNODDS LIVE (TASKS.md PW*): read pinnodds docs + tested the key (trial_dem
  a4df2b62 ckpt 2725: Made the ten-sources research resumable by any session/account: tools/resear
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
