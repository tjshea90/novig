# CHECKPOINT 4631 — read me first, then TASKS.md

**Written:** 2026-11-18T03:39:33Z · **tests:** 1 RED: test_research_index.sh (9 green)
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `2ef443c57` (this checkpoint is the commit after it)

## Just done
MK3: warm-run read (TALLY rows + 164 priced trades): market pays median +1.06 pts of pot over our fair, an 8% quote would win 37%; wallet-limited (~$5-15/day at best); catalog snapshot saved; MK5/MK6/MK7 queued

## Do this next
MK5: daily catalog snapshot + grade the market's trades; MK6 coverage (maxLegs, same-game, prop ladders); LIVE only by the evidence rule

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/novig_rfq_maker_2026-11-18.md

## Last ten checkpoints
```
  eb2082199 ckpt 4630: MK3: rfq_legs.py (what the legs of RFQs are, via Novig's public catalog) + 
  d15c145fe ckpt 4629: MK3: CHANGES.md line for the sampled RFQ journal; engine reset issued (cc b
  c855ae799 ckpt 4628: MK3: first real SHADOW run read: production serves RFQ v3, maker registered
  0f0039298 ckpt 4627: MK: maker landed 02:48, runner replaced again (the 02:46 job predated it); 
  f34f0e681 ckpt 4626: MK: Novig parlay maker built (protocol, pricer, maker, socket, engine wirin
  f0ff790a4 ckpt 4625: NVR done: verified live (device pass until Thu 03:00 ET; locks 37 games/160
  c8217ab93 ckpt 4624: NVR: locks guard is the share of the engine's own candidates, not a guessed
  cfb5539b4 ckpt 4623: NVR: :data:test green on the landing tree
  225fcabf1 ckpt 4622: NVR: moved the device-check/locks loops below postMessage (cloud compile), 
  350e50d60 ckpt 4621: NVR: Novig geolocation status (GeoWatch + message), locks (NovigLocks into 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
