# CHECKPOINT 3044 — read me first, then TASKS.md

**Written:** 2026-10-12T04:47:25Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `6f858aa29` (this checkpoint is the commit after it)

## Just done
TAN7 done: v0.89.35 released (Release run 38204510821, recorded in BUILDLOG, link sent to Tj). TAN9: fixed NovigStream.scheduleSync lost wake-up that made CI run 38204399946 fail (test fails without the fix, :data NovigStream+Live tests green).

## Do this next
Wait for CI on this commit (ci.yml); if green, the fix is unreleased code (next version, bump versionCode 221/0.89.36 with ship.sh when the next change batch is ready). Then TAN8 follow-ups (a)-(f) in TASKS.md, starting with (a) once Tj sends a Diagnostics with the NovigReads line.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  5e530badd ckpt 3043: pre-release: v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk 
  e299c8137 ckpt 3042: TAN: NET event line carries the start of a 400's body; follow-ups (e)(f) wr
  8a91641a5 ckpt 3041: version 0.89.35; TASKS TAN2-TAN6 ticked with evidence; skill doc lists the 
  b1b4d24d0 ckpt 3040: RESEARCH §135 written (the TAN analysis + what was acted on and what was n
  f8f04ecbd ckpt 3039: TAN6: Kelly stake scaled by lead time (EvMath.leadScale: 1.0 up to 9h, 0.6 
  93fe0376d ckpt 3038: TAN4: Settings home 20 -> 14 rows: APIs & keys hub with child pages (SGO, O
  e132c821e ckpt 3037: TAN4 settings audit written (research/tan_settings_audit_2026-10-12.md): AP
  b003ff695 ckpt 3036: TAN3: auto-bet asks player-prop Overs for 3 more points of edge (default, A
  2b015ed08 ckpt 3035: TAN3 rules: bid desk skips bids >1c behind the best bid; bid slider horizon
  8e11a6ba3 ckpt 3034: TAN2: bid-desk step in the background cycle time-boxed to the cycle interva
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
