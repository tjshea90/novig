# CHECKPOINT 3058 — read me first, then TASKS.md

**Written:** 2026-10-12T06:51:28Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `a9bb55b9e` (this checkpoint is the commit after it)

## Just done
TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport with most markets across up to 10 books incl prophetx/kalshi/novig/polymarket, two raw games per book, odds probe open info + raw frames, /livescores and /playbyplay socket probes via BoltRawProbe); report() prints connection notes + raw frames; 46 Bolt tests green

## Do this next
TBP5: full floor (bash tools/test.sh), bump versionCode 222 / 0.90.1 in app/build.gradle.kts + BUILDLOG.md, bash ship.sh, wait CI via send_later, trigger release.yml, record-release, link to Tj and ask him to re-run Settings > APIs > BoltOdds Pro > Test and share sample

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3fa23b9e5 ckpt 3057: TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zl
  189399b0e ckpt 3056: BoltOdds sample #1 read: 34 books (no Pinnacle/Circa/bet365; 9 of Tj's book
  3794ec48b ckpt 3055: v0.90.0 released and recorded: BoltOdds Pro (websocket lines + props, Test 
  a9bc66a3c ckpt 3054: pre-release: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sampl
  d01978332 ckpt 3053: pre-ship: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample),
  298a71e29 ckpt 3052: TBO: docs written (BOLTODDS_API.md §7, RESEARCH.md §136, CLAUDE.md BoltOd
  e568abb49 ckpt 3051: TBO3 (app wiring): ApiEntry.BOLTODDS, VigilantApp client/feed/sources/boltA
  855ff31d5 ckpt 3050: TBO3 (data side, part 1): data/.../boltodds/ Models, Parser, Markets, Books
  9bd2933e8 ckpt 3049: TBO2 done: Novig docs re-read (135 pages + OpenAPI), findings in research/n
  678d21dbb ckpt 3046: TBO1: BoltOdds docs read in full (single page), saved raw to research/bolto
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
