# CHECKPOINT 4641 — read me first, then TASKS.md

**Written:** 2026-11-18T04:43:50Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `1969284d9` (this checkpoint is the commit after it)

## Just done
MKV3: LIVE prerequisites: 24 h realized-loss stop from Novig's executions (RfqLedger, onLedger), last-look price tolerance, results tile; research section 11; 4314 tests green

## Do this next
engine reset to load them; then rfq_set mode=LIVE maxCollateral=25 maxOpenCollateral=50 maxLegs=3 dayLossStop=20 minReturn=0.06 (Tj approved); watch the first QUOTE_SENT/CONFIRM rows; ship 1.0.138 with the results tile

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  09268ea2c ckpt 4640: MKV: v1.0.137 released (recorded); MKV2 game-lines-only maker + 6% return d
  f70eb1882 ckpt 4639: MKV2: parlays with a player prop are not quoted or counted (market pays +6.
  ed890a96a ckpt 4638: pre-release: MKV: the Novig parlay maker in the app (Engine > Parlays: stat
  ade0fa424 ckpt 4637: MK5: rfq-catalog.yml snapshots Novig's public catalog every 4 h into the rf
  65a5e2f60 ckpt 4636: MKV: app suite green on the exact tree (v1.0.137 bump)
  0e334bb99 ckpt 4635: MKV: parlay maker in the app (Engine > Parlays: status, evidence counter, O
  35071ac65 ckpt 4634: PLQ: answered Tj PropLine health+feature list
  3b6279c74 ckpt 4633: Handoff manifest saved (.handoff/). The handoff tool had replaced the marke
  b2e2a0f37 ckpt 4632: MK3: research index rebuilt for the RFQ leg/catalog studies (index test gre
  21bd5babd ckpt 4631: MK3: warm-run read (TALLY rows + 164 priced trades): market pays median +1.
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
