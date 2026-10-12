# CHECKPOINT 3102 — read me first, then TASKS.md

**Written:** 2026-10-12T17:29:57Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `21712abeb` (this checkpoint is the commit after it)

## Just done
pre-release: v0.93.4: repeat and hedge guard (a side is bet or bid at most twice / $100 by default, the other side of a market only when both sides are a sure profit; auto-bet, bids, live bids and the live engine), Entain family + BetMGM counted as one book, resting bids priced at or over fair come down in the same pass, star minimum overrides Profit-proven with lowered stars for losing types (versionCode 232, v0.93.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.93.4), then run: bash tools/record-release.sh v0.93.4 232 "v0.93.4: repeat and hedge guard (a side is bet or bid at most twice / $100 by default, the other side of a market only when both sides are a sure profit; auto-bet, bids, live bids and the live engine), Entain family + BetMGM counted as one book, resting bids priced at or over fair come down in the same pass, star minimum overrides Profit-proven with lowered stars for losing types"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  21712abeb ckpt 3101: pre-ship: v0.93.4: repeat and hedge guard (a side is bet or bid at most twi
  9c78c9d5f ckpt 3100: TST2 done: RepeatGuard (pure, RepeatGuardTest) wired into ApiBetPlacer (aut
  2771ed5aa ckpt 3099: Recorded Tj's streaming-engine request as TST1-5; v0.93.3 CI/release still 
  2eb959c28 ckpt 3098: pre-release: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladb
  6dab775a8 ckpt 3097: pre-ship: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrok
  7efdb28cc ckpt 3096: pre-release: v0.93.2: a resting bid priced at or over its fair comes down i
  5cb6bc146 ckpt 3095: pre-ship: v0.93.2: a resting bid priced at or over its fair comes down in t
  9621ebb1e ckpt 3094: pre-release: v0.93.1: the bet sheet closes by itself when Novig has the ord
  75311ee0d ckpt 3093: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  75fc650d5 ckpt 3092: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
```
