# CHECKPOINT 2305 — read me first, then TASKS.md

**Written:** 2026-10-02T04:17:19Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `40834cd1` (this checkpoint is the commit after it)

## Just done
AO2 lag found and fixed: root built a new ApiBetActions for the STATIC LocalApiBet on every state (3/s mid-scan) -> whole tree recomposed with skipping off; now ProvideApiBet (remembered), plus remembered FloatingActions, ParlayPickActions (root+feed), LocalOpenNovig (ScanLagTest: control shows 6 draws vs 1)

## Do this next
Diagnostics review fixes: crash version attribution (installed-since), background low-memory classified by importance, onTrimMemory drops stale caches; then mid-scan fair refresh decision, API research, RESEARCH §63, docs

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  354f4eb9 ckpt 2304: Live feed: opened at the first plan, ONE bulk subscribe of the unread lines 
  d3062e41 ckpt 2303: AO3 part: ParlayAPI quotes dated by the book's verified-at last_update, not 
  5e414948 ckpt 2302: wrote Tj's full-tests + lag + 9-min odds + APIs request into TASKS.md as AO1
  07644e98 ckpt 2301: AN1-AN3: liquidity-follows-sharps tested on Novig's own trades (RESEARCH.md 
  411ea776 ckpt 2300: wrote Tj's sharp-liquidity scanner request into TASKS.md as AN1-AN3
  efc3c122 ckpt 2299: v0.43.0 released and recorded: diagnostics file for Claude with flight recor
  6de668d6 ckpt 2298: pre-release: v0.43.0: Diagnostics is a file for Claude: always-on flight rec
  47980f55 ckpt 2297: pre-ship: v0.43.0: Diagnostics is a file for Claude: always-on flight record
  ece86098 ckpt 2296: AM1-AM6 ticked; version 0.43.0 code 78; full floor 1582 passed with screensh
  96717928 ckpt 2295: AM mutation checks: data layer, advisor, file, share, wiring (AppRecorder ex
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
