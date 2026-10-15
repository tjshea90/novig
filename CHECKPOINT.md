# CHECKPOINT 3311 — read me first, then TASKS.md

**Written:** 2026-10-15T02:14:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `a50f88791` (this checkpoint is the commit after it)

## Just done
pre-release: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a read-only Novig key made for the engine alone, BoltOdds' sockets on the runner), the engine's API ranking by measured freshness and accuracy is kept across handovers and shown, Diagnostics GITHUB ENGINE (versionCode 282, v0.104.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.104.0), then run: bash tools/record-release.sh v0.104.0 282 "v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a read-only Novig key made for the engine alone, BoltOdds' sockets on the runner), the engine's API ranking by measured freshness and accuracy is kept across handovers and shown, Diagnostics GITHUB ENGINE"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a50f88791 ckpt 3310: pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a 
  810737e67 ckpt 3309: TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wir
  671773ac5 ckpt 3308: pre-release: v0.103.0: the bid desk's data runs on GitHub: the engine publi
  257a52f09 ckpt 3307: pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishe
  6a010ec91 ckpt 3306: TCL12: Diagnostics BIDS FROM GITHUB section, Bids tab wording, CLAUDE.md+de
  2ebaf483c ckpt 3305: TCL12: dropped-candidate lines become unavailable (desk takes the bid down)
  a2e65b47a ckpt 3304: TCL12: engine bid tests (5), fetchBids client test; engine gates on bidsNee
  aa4ad8bbf ckpt 3303: TCL12: CloudMain wiring (bidLines+publishBids, injuries via CloudSources), 
  44ad74831 ckpt 3302: Reviewed interrupted TCL12 work (compiles: BidCandidates, CloudBids codec+l
  08c014b0e ckpt 3301: TCL12 written: bid desk data on GitHub (design in research/cloud_bids_2026-
```
