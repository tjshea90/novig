# CHECKPOINT 4624 — read me first, then TASKS.md

**Written:** 2026-11-18T01:23:16Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `76748e2ba` (this checkpoint is the commit after it)

## Just done
NVR: locks guard is the share of the engine's own candidates, not a guessed size cap (the first real list held thousands); device check works live (pass good until Thu 03:00 ET)

## Do this next
engine_set --reset; read the real locks counts in engine-log; tell Tj to open the Novig app before Thu 03:00 ET

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cfb5539b4 ckpt 4623: NVR: :data:test green on the landing tree
  225fcabf1 ckpt 4622: NVR: moved the device-check/locks loops below postMessage (cloud compile), 
  350e50d60 ckpt 4621: NVR: Novig geolocation status (GeoWatch + message), locks (NovigLocks into 
  9495a33ba ckpt 4620: ENGINEER 11-17 slot2: watch ok, PLR4 ticked, no ship
  4c096d534 ckpt 4619: PLR: SGP logger+grader live-tested with the key (344 quotes, Novig best on 
  9749ec207 ckpt 4618: PLR trial fixes from the first real run: SGP needs a total point, ev/calc n
  d941c0de1 ckpt 4617: TASKS: PLS/PLR status after landing; trial + engine reset in flight
  6e4a22eba ckpt 4616: research index rebuilt for the PropLine studies
  0158be6e5 ckpt 4615: PLS+PLR: all three modules green (4241 passed); PropLine upgrade watch, Pin
  849040d2d ckpt 4614: PLS: full :cloud/:data suites green (3041 passed); Pinnacle cache age, Prop
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
