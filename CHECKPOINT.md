# CHECKPOINT 2268 — read me first, then TASKS.md

**Written:** 2026-10-01T18:07:38Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `41af1875` (this checkpoint is the commit after it)

## Just done
AH3-AH5 built: 5 s interval (cadence fixed), stake sentences pinned by tests, per-bet HIGH-importance notification with stake+EV+odds+wallet, test button, blocked-notification warning + health check

## Do this next
AH2: Add money in every Bet slip (inline transfer with the saved management key, else to Settings with the amount); then mutation checks, docs, floor, ship v0.40.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/test/kotlin/com/tjshea/vigilant/app/AutoBetDiagnosticsTest.kt

## Last ten checkpoints
```
  b20b948d ckpt 2267: wrote Tj's four-part request (add-money in every bet slip, auto-bet stake ru
  e7b75633 ckpt 2266: v0.39.3 released and recorded: auto-bet one-cent floor (AG1-AG3)
  b2ad578c ckpt 2265: pre-release: v0.39.3: auto-bet has no $1 minimum: stakes down to one cent (K
  20a42607 ckpt 2264: wrote Tj's no-$1-minimum request into TASKS.md as AG1-AG3
  06e8ccbb ckpt 2263: answered Tj's phone-off question; RESEARCH §54 + AF1-AF3 done (no code chan
  feb94e04 ckpt 2262: wrote Tj's phone-off auto-bet question into TASKS.md as AF1-AF3
  f8800188 ckpt 2261: v0.39.2 released and recorded: auto-bet longest-odds limit (AE1-AE3)
  c133deb1 ckpt 2260: pre-release: v0.39.2: auto-bet gets a longest-odds limit (Settings › Betti
  947c80cf ckpt 2259: wrote Tj's longest-odds request into TASKS.md as AE1-AE3 (answer: Kelly scal
  6e96526f ckpt 2258: v0.39.1 released and recorded (AD1-AD4 done): priced-market reuse + stale-bo
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
