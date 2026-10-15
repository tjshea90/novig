# CHECKPOINT 3307 — read me first, then TASKS.md

**Written:** 2026-10-15T01:56:29Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `8bb908d3b` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishes the candidate bid lines (fair odds, rules, injuries), the phone reads Novig's books for them and posts through the unchanged desk; bids no longer make the phone scan for itself

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  6a010ec91 ckpt 3306: TCL12: Diagnostics BIDS FROM GITHUB section, Bids tab wording, CLAUDE.md+de
  2ebaf483c ckpt 3305: TCL12: dropped-candidate lines become unavailable (desk takes the bid down)
  a2e65b47a ckpt 3304: TCL12: engine bid tests (5), fetchBids client test; engine gates on bidsNee
  aa4ad8bbf ckpt 3303: TCL12: CloudMain wiring (bidLines+publishBids, injuries via CloudSources), 
  44ad74831 ckpt 3302: Reviewed interrupted TCL12 work (compiles: BidCandidates, CloudBids codec+l
  08c014b0e ckpt 3301: TCL12 written: bid desk data on GitHub (design in research/cloud_bids_2026-
  9b118fe1b ckpt 3300: v0.102.0 released and recorded; engine restarted on the new code (gate+engi
  dc63ba210 ckpt 3299: pre-release: v0.102.0: cloud review: bids keep the phone scanning (the clou
  35b8fbe3e ckpt 3298: TCL10: efficiency (poll cadence, 5 s clock, requote throttle), push/keys mu
  2a846efcc ckpt 3297: TCL10: cloud review fixes (bids pause, recheck, pause/kill, token removal, 
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
