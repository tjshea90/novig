# CHECKPOINT 3304 — read me first, then TASKS.md

**Written:** 2026-10-15T01:46:00Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `d7c20a7bb` (this checkpoint is the commit after it)

## Just done
TCL12: engine bid tests (5), fetchBids client test; engine gates on bidsNeedLines

## Do this next
TCL12d: read CloudLink bid loop + MakerRunner cloud paths for defects (recommend/post paths, mine/keep, Diagnostics lines, UI text), then docs (CLAUDE.md cloud section, doc), full tests, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/cloud/CloudClientTest.kt

## Last ten checkpoints
```
  aa4ad8bbf ckpt 3303: TCL12: CloudMain wiring (bidLines+publishBids, injuries via CloudSources), 
  44ad74831 ckpt 3302: Reviewed interrupted TCL12 work (compiles: BidCandidates, CloudBids codec+l
  08c014b0e ckpt 3301: TCL12 written: bid desk data on GitHub (design in research/cloud_bids_2026-
  9b118fe1b ckpt 3300: v0.102.0 released and recorded; engine restarted on the new code (gate+engi
  dc63ba210 ckpt 3299: pre-release: v0.102.0: cloud review: bids keep the phone scanning (the clou
  35b8fbe3e ckpt 3298: TCL10: efficiency (poll cadence, 5 s clock, requote throttle), push/keys mu
  2a846efcc ckpt 3297: TCL10: cloud review fixes (bids pause, recheck, pause/kill, token removal, 
  d022bf6e3 ckpt 3296: TCL10 written (review/fix/simplify/mirror/refresh/freshness)
  e4cb95841 ckpt 3295: v0.101.0 released and recorded; engine restarted on the new engine code
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
