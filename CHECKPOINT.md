# CHECKPOINT 2304 — read me first, then TASKS.md

**Written:** 2026-10-02T04:11:59Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `901ebf1c` (this checkpoint is the commit after it)

## Just done
Live feed: opened at the first plan, ONE bulk subscribe of the unread lines once the plan is in (all sources answered / more unread than it holds / 30 s), what it holds goes first; unsubscribe charged at most the bucket; fresh connection forgets the last scan's list; ScanTiming says 'live feed asked for N at X s' (LiveFeedPlanTest 5 + NovigStreamTest 2, mutation-checked)

## Do this next
AO2 lag: measure list behavior during scan (animateItem churn, re-sorting); diagnostics: crash version attribution, background low-memory classification, onTrimMemory; mid-scan fair refresh; API research

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d3062e41 ckpt 2303: AO3 part: ParlayAPI quotes dated by the book's verified-at last_update, not 
  5e414948 ckpt 2302: wrote Tj's full-tests + lag + 9-min odds + APIs request into TASKS.md as AO1
  07644e98 ckpt 2301: AN1-AN3: liquidity-follows-sharps tested on Novig's own trades (RESEARCH.md 
  411ea776 ckpt 2300: wrote Tj's sharp-liquidity scanner request into TASKS.md as AN1-AN3
  efc3c122 ckpt 2299: v0.43.0 released and recorded: diagnostics file for Claude with flight recor
  6de668d6 ckpt 2298: pre-release: v0.43.0: Diagnostics is a file for Claude: always-on flight rec
  47980f55 ckpt 2297: pre-ship: v0.43.0: Diagnostics is a file for Claude: always-on flight record
  ece86098 ckpt 2296: AM1-AM6 ticked; version 0.43.0 code 78; full floor 1582 passed with screensh
  96717928 ckpt 2295: AM mutation checks: data layer, advisor, file, share, wiring (AppRecorder ex
  32341875 ckpt 2294: AM data-layer mutation checks done (EventLog, NetStats, NetInterceptor, Logc
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
