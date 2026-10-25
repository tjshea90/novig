# CHECKPOINT 4181 — read me first, then TASKS.md

**Written:** 2026-10-25T07:50:56Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-cfd54eb0-3h0vnn` · **builds on:** `3a147e0c3` (this checkpoint is the commit after it)

## Just done
PB10: ParlayAPI Business docs saved (research/parlayapi_business_2026-10-25/); parlay.py streamprobe task (odds/hot/drop SSE + freshness routes, keyless)

## Do this next
trigger parlay.yml streamprobe on main; read frames; build engine SSE client

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M .github/workflows/parlay.yml
     M CHECKPOINT.md

## Last ten checkpoints
```
  a35541a9a ckpt 4180: PB2 Pinnodds trial: one board every 15 min (~96/day); PB4/PB5: hourly accou
  58bf568f3 ckpt 4179: PB3: Unabated + OddsPapi v5 removed from app and engine (clients, sources, 
  071777187 ckpt 4178: PB1 SharpAPI Free tier: plan read from X-RateLimit-Limit, 6 s spacing, game
  99b57535f ckpt 4177: api-docs mirror: ParlayAPI blog/changelog/integrations/versioning + PropLin
  6b9ef6543 ckpt 4176: PB: Tj's downgrade/ParlayAPI Business/settings/battery request written into
  dbedd3059 ckpt 4175: first live PropLine stream run verified (26k moves/15 min, 0 gaps); Novig p
  079c4f5ae ckpt 4174: PSL9: v1.0.81 released + recorded
  eae6dc6b3 ckpt 4173: v1.0.81 shipped (ship.sh green, on main), release.yml run 40170789987 build
  5ea52564f ckpt 4172: pre-release: PropLine Streaming Lite: engine WebSockets (moves, pulls, scra
  8fe49550b ckpt 4171: pre-ship: PropLine Streaming Lite: engine WebSockets (moves, pulls, scratch
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
