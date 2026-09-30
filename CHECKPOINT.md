# CHECKPOINT 2218 — read me first, then TASKS.md

**Written:** 2026-09-30T22:18:51Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `f0a95460` (this checkpoint is the commit after it)

## Just done
X1/X2: scan and usage mirrors throttled (350 ms / 1 s), feed rebuilt only on a new result and off main; crash stack + Android exit record in Diagnostics

## Do this next
X3: ship v0.36.1 (65), CI, release, record, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  809c349d ckpt 2217: Logged Tj's crash report (switching tabs during a Vigilant scan: laggy then 
  45b6058b ckpt 2216: v0.36.0 (code 64) released and recorded: W1-W4 done
  af9b6d8a ckpt 2215: pre-release: v0.36.0: Diagnostics fixes from Tj's first report (no false ala
  bccf7b7e ckpt 2214: W2/W3: Diagnostics false alarms fixed (busy source, backup source, spent key
  b50f4dbc ckpt 2213: Logged Tj's pasted v0.35.0 Diagnostics as TASKS.md W1-W4
  d857f629 ckpt 2212: v0.35.0 (code 63) released and recorded: V1-V4 done
  03124699 ckpt 2211: pre-release: v0.35.0: Bet sheet takes any typed amount and opens at the wall
  7830ace6 ckpt 2210: Fixed a real race in ApiBettingController.placer() (two plans on two threads
  47c2a353 ckpt 2209: V3 done: Diagnostics health checks (FAIL/WARN/OK with evidence and code), ac
  5743ac38 ckpt 2208: V1 done: CNO has no book choice; ParlayAPI has no more sharp books; PropLine
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
