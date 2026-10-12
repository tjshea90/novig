# CHECKPOINT 3054 — read me first, then TASKS.md

**Written:** 2026-10-12T06:15:23Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `d01978332` (this checkpoint is the commit after it)

## Just done
pre-release: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample), live taker fee 0.06 for NCAAF/MLB/NHL, Novig docs re-check, NovigStream wake-up race fix (versionCode 221, v0.90.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.90.0), then run: bash tools/record-release.sh v0.90.0 221 "v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample), live taker fee 0.06 for NCAAF/MLB/NHL, Novig docs re-check, NovigStream wake-up race fix"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d01978332 ckpt 3053: pre-ship: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample),
  298a71e29 ckpt 3052: TBO: docs written (BOLTODDS_API.md §7, RESEARCH.md §136, CLAUDE.md BoltOd
  e568abb49 ckpt 3051: TBO3 (app wiring): ApiEntry.BOLTODDS, VigilantApp client/feed/sources/boltA
  855ff31d5 ckpt 3050: TBO3 (data side, part 1): data/.../boltodds/ Models, Parser, Markets, Books
  9bd2933e8 ckpt 3049: TBO2 done: Novig docs re-read (135 pages + OpenAPI), findings in research/n
  678d21dbb ckpt 3046: TBO1: BoltOdds docs read in full (single page), saved raw to research/bolto
  a3856d33a ckpt 3045: TBO: wrote Tj's BoltOdds Pro + Novig docs request into TASKS.md (TBO1-TBO5)
  cc60dda84 ckpt 3044: TAN7 done: v0.89.35 released (Release run 38204510821, recorded in BUILDLOG
  5e530badd ckpt 3043: pre-release: v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk 
  e299c8137 ckpt 3042: TAN: NET event line carries the start of a 400's body; follow-ups (e)(f) wr
```
