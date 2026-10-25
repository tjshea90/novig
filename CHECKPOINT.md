# CHECKPOINT 4189 — read me first, then TASKS.md

**Written:** 2026-10-25T08:34:22Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-be05497a-18skm3` · **builds on:** `3ab0436a8` (this checkpoint is the commit after it)

## Just done
PB11/PB19/PB15/PB16 built (not yet full-suite tested): ParlayLive+ParlayStream (Business SSE, gate parlaySocket), RealtimeList + Scanner.liveReprice + Pricing.refair (engine re-prices watched sides every 3 s from PropLine+ParlayAPI pushes and Novig socket, publishes, trades), status.realtimeAtMs (phone skips its 15 s requote), live server /feed (phone reads list over tunnel); ParlayAPI REST books 10->15; NBA props mapped

## Do this next
run :data:test :cloud:test :app:testDebugUnitTest; fix; then full suite, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M cloud/src/test/kotlin/com/tjshea/vigilant/cloud/LiveStatusServerTest.kt

## Last ten checkpoints
```
  d2ea5e70c ckpt 4188: ticked PB1-4, PB7, PB9, PB18, PB20 with their tests; streamprobe re-run (wh
  71e25af0f ckpt 4187: PB9 phone rests (ScanSettings.phoneRests/phoneAutoScan: no auto-scan servic
  b73a5cff5 ckpt 4186: PB: merged tj Gmail's PhoneSleep WIP; streamprobe frames saved (research/pa
  ed57aa38f ckpt 4185: research index rebuilt (ParlayAPI Business docs folder)
  51f196e04 ckpt 4184: retry landing
  db6acb778 ckpt 4183: tests green: PB2-PB5 + engine hourly loop fix
  8c2336acb ckpt 4182: tests green on PB2-PB5 + streamprobe
  24ed75397 ckpt 4181: PB10: ParlayAPI Business docs saved (research/parlayapi_business_2026-10-25
  a35541a9a ckpt 4180: PB2 Pinnodds trial: one board every 15 min (~96/day); PB4/PB5: hourly accou
  58bf568f3 ckpt 4179: PB3: Unabated + OddsPapi v5 removed from app and engine (clients, sources, 
```

(33 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
