# CHECKPOINT 3310 — read me first, then TASKS.md

**Written:** 2026-10-15T02:11:50Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `5d8854d83` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a read-only Novig key made for the engine alone, BoltOdds' sockets on the runner), the engine's API ranking by measured freshness and accuracy is kept across handovers and shown, Diagnostics GITHUB ENGINE

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  810737e67 ckpt 3309: TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wir
  671773ac5 ckpt 3308: pre-release: v0.103.0: the bid desk's data runs on GitHub: the engine publi
  257a52f09 ckpt 3307: pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishe
  6a010ec91 ckpt 3306: TCL12: Diagnostics BIDS FROM GITHUB section, Bids tab wording, CLAUDE.md+de
  2ebaf483c ckpt 3305: TCL12: dropped-candidate lines become unavailable (desk takes the bid down)
  a2e65b47a ckpt 3304: TCL12: engine bid tests (5), fetchBids client test; engine gates on bidsNee
  aa4ad8bbf ckpt 3303: TCL12: CloudMain wiring (bidLines+publishBids, injuries via CloudSources), 
  44ad74831 ckpt 3302: Reviewed interrupted TCL12 work (compiles: BidCandidates, CloudBids codec+l
  08c014b0e ckpt 3301: TCL12 written: bid desk data on GitHub (design in research/cloud_bids_2026-
  9b118fe1b ckpt 3300: v0.102.0 released and recorded; engine restarted on the new code (gate+engi
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
