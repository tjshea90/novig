# CHECKPOINT 4187 — read me first, then TASKS.md

**Written:** 2026-10-25T08:16:27Z · **tests:** all 9 fast checks green
**Branch:** `ccr-be05497a-18skm3` · **builds on:** `186381fc3` (this checkpoint is the commit after it)

## Just done
PB9 phone rests (ScanSettings.phoneRests/phoneAutoScan: no auto-scan service/wake lock while the engine scans+trades; PhoneRestsTest); PB20 bet sheet age+feed per book (BookPrices.source, CloudCodec 's', QuoteAge; QuoteAgeTest, CloudCodecTest); PB18 test (Business key first); PB7 24/7 default (ScanSettings.cloudHours=false, engine_alarm default + selftest)

## Do this next
run TheOddsApiClientTest + CloudEngineTest; PB7 routines; PB11 ParlayAPI SSE engine client

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b73a5cff5 ckpt 4186: PB: merged tj Gmail's PhoneSleep WIP; streamprobe frames saved (research/pa
  ed57aa38f ckpt 4185: research index rebuilt (ParlayAPI Business docs folder)
  51f196e04 ckpt 4184: retry landing
  db6acb778 ckpt 4183: tests green: PB2-PB5 + engine hourly loop fix
  8c2336acb ckpt 4182: tests green on PB2-PB5 + streamprobe
  24ed75397 ckpt 4181: PB10: ParlayAPI Business docs saved (research/parlayapi_business_2026-10-25
  a35541a9a ckpt 4180: PB2 Pinnodds trial: one board every 15 min (~96/day); PB4/PB5: hourly accou
  58bf568f3 ckpt 4179: PB3: Unabated + OddsPapi v5 removed from app and engine (clients, sources, 
```

(17 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
