# CHECKPOINT 4195 — read me first, then TASKS.md

**Written:** 2026-10-25T09:06:40Z · **tests:** all 9 fast checks green
**Branch:** `ccr-be05497a-18skm3` · **builds on:** `9801c8814` (this checkpoint is the commit after it)

## Just done
SSE streams (ParlayAPI odds + play-by-play) no longer cut by the shared client's 30 s call deadline (35 reconnects in 3 min; pbp reconnects cost 5 credits each); useParlay switched on (was off: engine priced from PropLine alone); v1.0.82 released

## Do this next
reset engine; answer Tj: why odds 7-10 min old (measure per-book ages in the live feed)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c4a6c5b4d ckpt 4194: v1.0.82 gated+pushed to main (4,146 tests green); release.yml run 401780160
  445aac267 ckpt 4193: pre-release: v1.0.82 PB: real-time list (PropLine + ParlayAPI Business stre
  b63c4ff47 ckpt 4192: pre-ship: v1.0.82 PB: real-time list (PropLine + ParlayAPI Business streams
  971e60854 ckpt 4191: pre-ship: v1.0.82 PB: real-time list (PropLine + ParlayAPI Business streams
  3becc8d30 ckpt 4190: PB14 bid desk lines re-made from real-time prices every 15 s; PB17 ParlayAP
  a059fd65a ckpt 4189: PB11/PB19/PB15/PB16 built (not yet full-suite tested): ParlayLive+ParlayStr
  d2ea5e70c ckpt 4188: ticked PB1-4, PB7, PB9, PB18, PB20 with their tests; streamprobe re-run (wh
  71e25af0f ckpt 4187: PB9 phone rests (ScanSettings.phoneRests/phoneAutoScan: no auto-scan servic
  b73a5cff5 ckpt 4186: PB: merged tj Gmail's PhoneSleep WIP; streamprobe frames saved (research/pa
  ed57aa38f ckpt 4185: research index rebuilt (ParlayAPI Business docs folder)
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
