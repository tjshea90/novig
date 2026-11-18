# CHECKPOINT 4630 — read me first, then TASKS.md

**Written:** 2026-11-18T03:21:04Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `d318f40ca` (this checkpoint is the commit after it)

## Just done
MK3: rfq_legs.py (what the legs of RFQs are, via Novig's public catalog) + first-10-min leg findings in the research file: 45% in play, props ~40% of legs, 55% of trades are 4-20 legs, coverage unmeasured (cold window)

## Do this next
read the warm job's sampled journal (run 44229304633, uploads ~03:37 UTC) with rfq_summary.py + TALLY rows; decide coverage work (props ladder, fair-age rule) from it; tell Tj what runs

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/novig_rfq_maker_2026-11-18.md

## Last ten checkpoints
```
  d15c145fe ckpt 4629: MK3: CHANGES.md line for the sampled RFQ journal; engine reset issued (cc b
  c855ae799 ckpt 4628: MK3: first real SHADOW run read: production serves RFQ v3, maker registered
  0f0039298 ckpt 4627: MK: maker landed 02:48, runner replaced again (the 02:46 job predated it); 
  f34f0e681 ckpt 4626: MK: Novig parlay maker built (protocol, pricer, maker, socket, engine wirin
  f0ff790a4 ckpt 4625: NVR done: verified live (device pass until Thu 03:00 ET; locks 37 games/160
  c8217ab93 ckpt 4624: NVR: locks guard is the share of the engine's own candidates, not a guessed
  cfb5539b4 ckpt 4623: NVR: :data:test green on the landing tree
  225fcabf1 ckpt 4622: NVR: moved the device-check/locks loops below postMessage (cloud compile), 
  350e50d60 ckpt 4621: NVR: Novig geolocation status (GeoWatch + message), locks (NovigLocks into 
  9495a33ba ckpt 4620: ENGINEER 11-17 slot2: watch ok, PLR4 ticked, no ship
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
