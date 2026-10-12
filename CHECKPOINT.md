# CHECKPOINT 3046 — read me first, then TASKS.md

**Written:** 2026-10-12T05:29:52Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `412d96552` (this checkpoint is the commit after it)

## Just done
TBO1: BoltOdds docs read in full (single page), saved raw to research/boltodds_2026-10-12/, BOLTODDS_API.md written (Pro = odds WS x3 connections + snapshot + info endpoints + get_parlays; scores/PBP/SGP/historical are NOT Pro); probes: invalid key -> REST 403 detail, WS closes 1008 'Invalid token'. Studied wiring (ApiEntry/ApiProbe/QuotaPolicy/ApiChain/SOURCE_ORDER/UnabatedFeed pattern).

## Do this next
TBO2: fetch docs.novig.com, diff vs NOVIG_API.md. Then TBO3 build: data/.../boltodds/ (Client REST, Stream WS, Board, Parser, Convert, Books/Markets, Sources, KeyTest), ApiProvider.BOLTODDS, QuotaPolicy.BOLTODDS, ScanSettings.boltOdds switch, ApiEntry, ApiProbe branch, ApiChain/SOURCE_ORDER, VigilantApp wiring, Settings page, tests (ApiTogglesTest idsOf).

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  a3856d33a ckpt 3045: TBO: wrote Tj's BoltOdds Pro + Novig docs request into TASKS.md (TBO1-TBO5)
  cc60dda84 ckpt 3044: TAN7 done: v0.89.35 released (Release run 38204510821, recorded in BUILDLOG
  5e530badd ckpt 3043: pre-release: v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk 
  e299c8137 ckpt 3042: TAN: NET event line carries the start of a 400's body; follow-ups (e)(f) wr
  8a91641a5 ckpt 3041: version 0.89.35; TASKS TAN2-TAN6 ticked with evidence; skill doc lists the 
  b1b4d24d0 ckpt 3040: RESEARCH §135 written (the TAN analysis + what was acted on and what was n
  f8f04ecbd ckpt 3039: TAN6: Kelly stake scaled by lead time (EvMath.leadScale: 1.0 up to 9h, 0.6 
  93fe0376d ckpt 3038: TAN4: Settings home 20 -> 14 rows: APIs & keys hub with child pages (SGO, O
  e132c821e ckpt 3037: TAN4 settings audit written (research/tan_settings_audit_2026-10-12.md): AP
  b003ff695 ckpt 3036: TAN3: auto-bet asks player-prop Overs for 3 more points of edge (default, A
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
