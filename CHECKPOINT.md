# CHECKPOINT 3751 — read me first, then TASKS.md

**Written:** 2026-10-19T16:15:16Z · **tests:** all 6 fast checks green
**Branch:** `ccr-2df6ce48-pkrh5v` · **builds on:** `c6f48daa7` (this checkpoint is the commit after it)

## Just done
PL: NovigTradingClient.transfers() reads TRANSFER_IN/OUT with no time bounds (a filter by event start can't drop them); WalletMoney uses it; ApiBettingTest covers it; v1.0.6 code 346

## Do this next
full tools/test.sh (running), then ship.sh, release.yml, record-release, dispatch the fixed keeper, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  18072eab0 ckpt 3750: EA fix: both keepers (15:29Z, 15:39Z) died at start on 'import engine_watch
  6fe62af1b ckpt 3749: PL app: WalletMoney reader + VM/Activity wiring, Tracker periods by game da
  494dc6f90 ckpt 3748: PL data: SlateProfit by game day (5 am-5 am ET, today = today's games, late
  55b142426 ckpt 3747: PL: SlateProfit reworked (today/day/days/lastDays by calendar day, merged c
  2ae72ef29 ckpt 3746: EA: keeper steps aside for a newer keeper, one log line per state, full-loo
  70e7a9f40 ckpt 3745: EA5-EA7: keeper (engine-alarm.yml: always running, 2-min checks in hours, s
  2ede70f12 ckpt 3744: EA1-EA3 done: alarm on main and armed (run 39277095427), watchdog starts a 
  5407f7a7d ckpt 3743: EA: engine-alarm.yml + tools/engine_alarm.py (free runner waits to 08:00 ET
  e1941aeae ckpt 3742: ANALYST 2026-10-19 skipped (no new data)
  7e3f1784e ckpt 3741: pre-release: v1.0.5: the chat with Claude rebuilt from scratch (one account
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
