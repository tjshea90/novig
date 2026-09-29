# CHECKPOINT 2077 — read me first, then TASKS.md

**Written:** 2026-09-29T16:03:08Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `535e01eb` (this checkpoint is the commit after it)

## Just done
pre-release: v0.21.0: bet through Novig's API from a separate Vigilant wallet (confirm tap, IOC at the shown price, pregame only, caps) and grade API bets from Novig's own ledger; Sync with Novig; Test key speed numbers understood; MoneyLine API tested and not adopted (versionCode 46, v0.21.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.21.0), then run: bash tools/record-release.sh v0.21.0 46 "v0.21.0: bet through Novig's API from a separate Vigilant wallet (confirm tap, IOC at the shown price, pregame only, caps) and grade API bets from Novig's own ledger; Sync with Novig; Test key speed numbers understood; MoneyLine API tested and not adopted"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  535e01eb ckpt 2076: W9: placement never cancelled by closing the sheet (+test that failed withou
  a9194471 ckpt 2075: ApiSettler: bets sharing a market are graded together (one market payout no 
  32c7dac5 ckpt 2074: W1-W8 ticked; RESEARCH.md §36.6 (MoneyLine tested) and §37 (API betting de
  2741c198 ckpt 2073: W6-W8 app layer done: connection store + container wiring, ApiBettingControl
  445ddb62 ckpt 2072: W6/W7 data: NovigBettingSetup (use the phone's trading key or revoke+mint, f
  6c651d47 ckpt 2071: W4/W5 data layer: NovigTradingClient, ApiBetPlanner, ApiBetPlacer (IOC at th
  8c20dd5a ckpt 2070: W1/W2 done (MoneyLine tested: stale ~2 h, no Pinnacle, 3 MB for 3 events); N
  a611dc3c ckpt 2069: Recorded Tj's request (build API betting + API grading for the Tracker; Mone
  cdfbe7b5 ckpt 2068: v0.20.2 (code 45) released and recorded; V1-V8 ticked
  fa5ee5b9 ckpt 2067: pre-release: v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the 
```
