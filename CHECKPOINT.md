# CHECKPOINT 4197 — read me first, then TASKS.md

**Written:** 2026-10-25T09:26:08Z · **tests:** all 9 fast checks green
**Branch:** `ccr-be05497a-18skm3` · **builds on:** `9382cffb9` (this checkpoint is the commit after it)

## Just done
restart button fix: engine honours a reset mid-scan and never loses it; phone keeps Claude's newer resetAtMs/checkApisAt (CloudEngineTest); full suite green

## Do this next
measure odds ages on run 376 and answer Tj; then reset once more for the restart fix

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  d9d5e617e ckpt 4196: odds-age fix: stale books left out beside fresh ones (FreshFirstTest), Parl
  2ad3720fb ckpt 4195: SSE streams (ParlayAPI odds + play-by-play) no longer cut by the shared cli
  c4a6c5b4d ckpt 4194: v1.0.82 gated+pushed to main (4,146 tests green); release.yml run 401780160
  445aac267 ckpt 4193: pre-release: v1.0.82 PB: real-time list (PropLine + ParlayAPI Business stre
  b63c4ff47 ckpt 4192: pre-ship: v1.0.82 PB: real-time list (PropLine + ParlayAPI Business streams
  971e60854 ckpt 4191: pre-ship: v1.0.82 PB: real-time list (PropLine + ParlayAPI Business streams
  3becc8d30 ckpt 4190: PB14 bid desk lines re-made from real-time prices every 15 s; PB17 ParlayAP
  a059fd65a ckpt 4189: PB11/PB19/PB15/PB16 built (not yet full-suite tested): ParlayLive+ParlayStr
  d2ea5e70c ckpt 4188: ticked PB1-4, PB7, PB9, PB18, PB20 with their tests; streamprobe re-run (wh
  71e25af0f ckpt 4187: PB9 phone rests (ScanSettings.phoneRests/phoneAutoScan: no auto-scan servic
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
