# CHECKPOINT 3755 — read me first, then TASKS.md

**Written:** 2026-10-19T16:35:16Z · **tests:** all 6 fast checks green
**Branch:** `ccr-2df6ce48-pkrh5v` · **builds on:** `346639a1e` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.6: the Tracker's profit card rebuilt (PL): Today is today's games, $0.00 until a result; open bets are money at stake, never a loss; all-time profit = the Vigilant wallet (cash + open bets) minus the money put in, net of every withdrawal (Novig's ledger, every money-moving kind); the Tracker's period chips count by game day; charts name the day, running total + a bar a day (versionCode 346, v1.0.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.6), then run: bash tools/record-release.sh v1.0.6 346 "v1.0.6: the Tracker's profit card rebuilt (PL): Today is today's games, $0.00 until a result; open bets are money at stake, never a loss; all-time profit = the Vigilant wallet (cash + open bets) minus the money put in, net of every withdrawal (Novig's ledger, every money-moving kind); the Tracker's period chips count by game day; charts name the day, running total + a bar a day"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  346639a1e ckpt 3754: EA8a/EA9 written up (keeper import crash, CI flood: ci.yml main-only, data-
  8e2363d3d ckpt 3753: PL: Tracker tests follow the new card (all-time headline by tag, clv card s
  1b1ac7101 ckpt 3752: PL5: the wallet figure reads every money-moving ledger kind (transfers, dep
  cce5b1756 ckpt 3751: PL: NovigTradingClient.transfers() reads TRANSFER_IN/OUT with no time bound
  18072eab0 ckpt 3750: EA fix: both keepers (15:29Z, 15:39Z) died at start on 'import engine_watch
  6fe62af1b ckpt 3749: PL app: WalletMoney reader + VM/Activity wiring, Tracker periods by game da
  494dc6f90 ckpt 3748: PL data: SlateProfit by game day (5 am-5 am ET, today = today's games, late
  55b142426 ckpt 3747: PL: SlateProfit reworked (today/day/days/lastDays by calendar day, merged c
  2ae72ef29 ckpt 3746: EA: keeper steps aside for a newer keeper, one log line per state, full-loo
  70e7a9f40 ckpt 3745: EA5-EA7: keeper (engine-alarm.yml: always running, 2-min checks in hours, s
```
