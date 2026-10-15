# CHECKPOINT 3309 — read me first, then TASKS.md

**Written:** 2026-10-15T02:11:16Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `cf96663bc` (this checkpoint is the commit after it)

## Just done
TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wiring), BoltOdds on the engine, ranking persisted+shown, Cloud page buttons, Diagnostics GITHUB ENGINE, docs

## Do this next
TCL13e: full floor, ship v0.104.0 (versionCode 282), release.yml, record, restart the engine on the new code, then read status.json novigRoute/ranking/bolt (Tj must tap Send a Novig read-only key + Send my API keys for the engine to use them)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M BOLTODDS_API.md
     M CHECKPOINT.md
     M NOVIG_API.md
     M TASKS.md
    ?? research/cloud_novig_bolt_2026-10-15.md

## Last ten checkpoints
```
  671773ac5 ckpt 3308: pre-release: v0.103.0: the bid desk's data runs on GitHub: the engine publi
  257a52f09 ckpt 3307: pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishe
  6a010ec91 ckpt 3306: TCL12: Diagnostics BIDS FROM GITHUB section, Bids tab wording, CLAUDE.md+de
  2ebaf483c ckpt 3305: TCL12: dropped-candidate lines become unavailable (desk takes the bid down)
  a2e65b47a ckpt 3304: TCL12: engine bid tests (5), fetchBids client test; engine gates on bidsNee
  aa4ad8bbf ckpt 3303: TCL12: CloudMain wiring (bidLines+publishBids, injuries via CloudSources), 
  44ad74831 ckpt 3302: Reviewed interrupted TCL12 work (compiles: BidCandidates, CloudBids codec+l
  08c014b0e ckpt 3301: TCL12 written: bid desk data on GitHub (design in research/cloud_bids_2026-
  9b118fe1b ckpt 3300: v0.102.0 released and recorded; engine restarted on the new code (gate+engi
  dc63ba210 ckpt 3299: pre-release: v0.102.0: cloud review: bids keep the phone scanning (the clou
```

(19 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
