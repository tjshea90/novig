# CHECKPOINT 2728 — read me first, then TASKS.md

**Written:** 2026-10-08T01:45:43Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f7044881-1c7epp` · **builds on:** `74973ada` (this checkpoint is the commit after it)

## Just done
Pinnodds live: UI test + settings search entries, MockWebServer tests for the key test and the socket (found: OkHttp skips network interceptors for websockets; start race), prefilter for /pre frames, mutation pass (30 mutants killed; 2 survivors found a real bug: any-edge mode never evaluated, and a vacuous stage test), feed-quiet limit 45 s > the 30 s heartbeat; NOVIG_API.md s21 and PINNODDS_API.md written. 72 pinnodds tests green

## Do this next
NEXT: RESEARCH.md s116 + CLAUDE.md pointer + TASKS ticks + BUILDLOG/version 0.76.0; save the study tapes (gz) to research/pinnodds_2026-10-08/; stop the recorder (scratchpad/pinn/rec.pid); full floor; ship.sh; release.yml; tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9a4d414e ckpt 2727: Pinnodds live: app wiring done (ApiProvider.PINNODDS, ScanSettings pinnLive*
  6136ef32 ckpt 2726: PINNODDS LIVE (TASKS.md PW*): read pinnodds docs + tested the key (trial_dem
  a4df2b62 ckpt 2725: Made the ten-sources research resumable by any session/account: tools/resear
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
