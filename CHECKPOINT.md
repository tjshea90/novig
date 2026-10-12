# CHECKPOINT 3080 — read me first, then TASKS.md

**Written:** 2026-10-12T08:51:13Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `425e2e491` (this checkpoint is the commit after it)

## Just done
pre-release: v0.92.0: orders over Novig's websocket (trading-key connection, never replayed, REST when nothing can have been placed), compressed live feed, market status acted on (closed = no bet, GOLIVE = live), feed diagnostics (allowance source, refusals, history in the event log), canary retries, Test key frees the app's feed first (versionCode 224, v0.92.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.92.0), then run: bash tools/record-release.sh v0.92.0 224 "v0.92.0: orders over Novig's websocket (trading-key connection, never replayed, REST when nothing can have been placed), compressed live feed, market status acted on (closed = no bet, GOLIVE = live), feed diagnostics (allowance source, refusals, history in the event log), canary retries, Test key frees the app's feed first"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
  18fee1961 ckpt 3078: TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing 
  c312ddb79 ckpt 3077: TNS3: socket compression on the market socket (header X-Novig-WS-Compress o
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
  0ebec61a3 ckpt 3070: TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the roo
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
