# CHECKPOINT 4638 — read me first, then TASKS.md

**Written:** 2026-11-18T04:07:12Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `ade0fa424` (this checkpoint is the commit after it)

## Just done
pre-release: MKV: the Novig parlay maker in the app (Engine > Parlays: status, evidence counter, OFF/SHADOW/LIVE with a confirm, limits); the engine prices by return on collateral (versionCode 477, v1.0.137)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.137), then run: bash tools/record-release.sh v1.0.137 477 "MKV: the Novig parlay maker in the app (Engine > Parlays: status, evidence counter, OFF/SHADOW/LIVE with a confirm, limits); the engine prices by return on collateral"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ade0fa424 ckpt 4637: MK5: rfq-catalog.yml snapshots Novig's public catalog every 4 h into the rf
  65a5e2f60 ckpt 4636: MKV: app suite green on the exact tree (v1.0.137 bump)
  0e334bb99 ckpt 4635: MKV: parlay maker in the app (Engine > Parlays: status, evidence counter, O
  35071ac65 ckpt 4634: PLQ: answered Tj PropLine health+feature list
  3b6279c74 ckpt 4633: Handoff manifest saved (.handoff/). The handoff tool had replaced the marke
  b2e2a0f37 ckpt 4632: MK3: research index rebuilt for the RFQ leg/catalog studies (index test gre
  21bd5babd ckpt 4631: MK3: warm-run read (TALLY rows + 164 priced trades): market pays median +1.
  eb2082199 ckpt 4630: MK3: rfq_legs.py (what the legs of RFQs are, via Novig's public catalog) + 
  d15c145fe ckpt 4629: MK3: CHANGES.md line for the sampled RFQ journal; engine reset issued (cc b
  c855ae799 ckpt 4628: MK3: first real SHADOW run read: production serves RFQ v3, maker registered
```
