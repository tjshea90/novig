# CHECKPOINT 3076 — read me first, then TASKS.md

**Written:** 2026-10-12T08:33:09Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `1038ff33a` (this checkpoint is the commit after it)

## Just done
Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0_first_evidence_2026-10-12.md): bbo works (scans 115s -> ~25s), key's real stream allowance is 512 at 4/s, 2048 markets; canary was inconclusive forever (fixed: asked again), refusal counters + allowance provenance in Diagnostics, token model carried across reconnects, Test key lets the app feed go and waits for allowance; 3 new tests (mutation-checked)

## Do this next
TNS3 compression on the market socket, TNS4 market status, TNS5 stream history in event log, TNS6 ship v0.92.0; TNV14: deepen costs 16+8 tokens a market at 4/s - consider REST-first deepen

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
  0ebec61a3 ckpt 3070: TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the roo
  1dda5d441 ckpt 3069: TNV6: private orders stream: OrderEvents (open/fill/cancel/reject, seq gaps
  544ebf656 ckpt 3068: TNV8: Test key gains the best-bid check (feed of its own: markets arrive as
  1bf9059c7 ckpt 3067: TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Setti
  e25fe6c70 ckpt 3066: TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (scree
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
