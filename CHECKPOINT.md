# CHECKPOINT 3531 — read me first, then TASKS.md

**Written:** 2026-10-17T06:49:53Z · **tests:** all 5 fast checks green
**Branch:** `ccr-b02146f9-09npa9` · **builds on:** `2272add7d` (this checkpoint is the commit after it)

## Just done
TCL81 auto-resume of a hand-off engine at next opening, loop watchdog w/ stack dump, stopUntil change, stale-data model+UI, 2s poll, old-bets list

## Do this next
run broader tests, bump v0.114.17, ship, restart engine; TCL80 tunnel after

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  d2b467b17 ckpt 3530: progressChangedAtMs status field + watchdog stall rule
  44c2f9b40 ckpt 3529: pre-release: v0.114.16: Engine tab Address tile shows the real Novig verdic
  901b20b97 ckpt 3528: v0.114.16: Address tile reads the real verdict, novigAddress status, Addres
  eb1c8c25c ckpt 3527: TCL78: standing job rule at top of CLAUDE.md + ROUTINE.md; address tile fix
  8d7e224a6 ckpt 3526: pre-release: v0.114.15: Run now / Stop now for the GitHub engine from Setti
  5bda9c5ec ckpt 3525: TCL74: manual Run/Stop of the engine outside hours (CloudManual, config fie
  ccd7c6029 ckpt 3524: pre-release: v0.114.14: red STAY AWAY banner on bet types that lose in your
  86d1a353c ckpt 3523: TCL73: profile regenerated (2210 records), engine hard-blocks proven-losing
  65b90b24a ckpt 3522: TCL72 done: totals in taker kinds, engine restarted, Tj told
  6c59b860c ckpt 3521: TCL72: P&L analysis, totals added to taker kinds (class B), research note +
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
