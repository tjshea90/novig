# CHECKPOINT 3098 — read me first, then TASKS.md

**Written:** 2026-10-12T17:14:43Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `6dab775a8` (this checkpoint is the commit after it)

## Just done
pre-release: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrokes, Coral and BetMGM count as one book everywhere (fair line, books agree, stars, bids, lab); also carries v0.93.2: a resting bid at or over its fair is cancelled in the same scan pass; star minimum overrides Profit-proven but historically losing types lose stars (versionCode 231, v0.93.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.93.3), then run: bash tools/record-release.sh v0.93.3 231 "v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrokes, Coral and BetMGM count as one book everywhere (fair line, books agree, stars, bids, lab); also carries v0.93.2: a resting bid at or over its fair is cancelled in the same scan pass; star minimum overrides Profit-proven but historically losing types lose stars"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6dab775a8 ckpt 3097: pre-ship: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrok
  7efdb28cc ckpt 3096: pre-release: v0.93.2: a resting bid priced at or over its fair comes down i
  5cb6bc146 ckpt 3095: pre-ship: v0.93.2: a resting bid priced at or over its fair comes down in t
  9621ebb1e ckpt 3094: pre-release: v0.93.1: the bet sheet closes by itself when Novig has the ord
  75311ee0d ckpt 3093: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  75fc650d5 ckpt 3092: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  17452a28c ckpt 3091: pre-release: v0.93.0: Profit-proven only switch (Settings > Betting & Novig
  6e085878a ckpt 3090: pre-ship: v0.93.0: Profit-proven only switch (Settings > Betting & Novig ac
  a41a7c859 ckpt 3089: TPF1-4: profit-by-type analysis (research/profit_types_2026-10-12.md), Prof
  68c3a2724 ckpt 3088: pre-release: v0.92.3: a bet by hand frees the bet sheet once Novig has the 
```
