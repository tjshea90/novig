# CHECKPOINT 3049 — read me first, then TASKS.md

**Written:** 2026-10-12T05:39:24Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `588a7cde0` (this checkpoint is the commit after it)

## Just done
TBO2 done: Novig docs re-read (135 pages + OpenAPI), findings in research/novig_docs_recheck_2026-10-12.md + NOVIG_API.md §24; fixed live fee 0.06 for NCAAF/MLB/NHL (MarketFee.gameFor, CnoBooks/CnoChecks/SharpVeto/AtBet/ParlayCompare/BetTracker/cnoStake), engine+data tests green. (signing-vectors.json NOT archived: its published test private keys trip the secret scan; URL is in NOVIG_API.md)

## Do this next
TBO3: build BoltOdds (data/.../boltodds/*): Parser, Board, Convert, Books/Markets, Client (REST), Stream (WS), Sources, KeyTest; then ApiProvider.BOLTODDS, QuotaPolicy, ScanSettings.boltOdds, ApiEntry, ApiProbe, ApiChain/SOURCE_ORDER, VigilantApp wiring, Settings page, Diagnostics, tests.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M NOVIG_API.md
     M TASKS.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/CnoScreen.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/cno/CnoBooks.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/cno/CnoChecks.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/reference/ParlayCompare.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/scanner/SharpVeto.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/tracker/AtBet.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/tracker/BetTracker.kt
     M data/src/test/kotlin/com/tjshea/vigilant/data/cno/CnoBooksTest.kt
     M engine/src/main/kotlin/com/tjshea/vigilant/engine/Fees.kt
     M engine/src/test/kotlin/com/tjshea/vigilant/engine/FeesTest.kt
    ?? research/novig_docs_2026-10-12/spec_openapi-v3.json
    ?? research/novig_docs_recheck_2026-10-12.md

## Last ten checkpoints
```
  678d21dbb ckpt 3046: TBO1: BoltOdds docs read in full (single page), saved raw to research/bolto
  a3856d33a ckpt 3045: TBO: wrote Tj's BoltOdds Pro + Novig docs request into TASKS.md (TBO1-TBO5)
  cc60dda84 ckpt 3044: TAN7 done: v0.89.35 released (Release run 38204510821, recorded in BUILDLOG
  5e530badd ckpt 3043: pre-release: v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk 
  e299c8137 ckpt 3042: TAN: NET event line carries the start of a 400's body; follow-ups (e)(f) wr
  8a91641a5 ckpt 3041: version 0.89.35; TASKS TAN2-TAN6 ticked with evidence; skill doc lists the 
  b1b4d24d0 ckpt 3040: RESEARCH §135 written (the TAN analysis + what was acted on and what was n
  f8f04ecbd ckpt 3039: TAN6: Kelly stake scaled by lead time (EvMath.leadScale: 1.0 up to 9h, 0.6 
  93fe0376d ckpt 3038: TAN4: Settings home 20 -> 14 rows: APIs & keys hub with child pages (SGO, O
  e132c821e ckpt 3037: TAN4 settings audit written (research/tan_settings_audit_2026-10-12.md): AP
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
