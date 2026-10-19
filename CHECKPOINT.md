# CHECKPOINT 3749 — read me first, then TASKS.md

**Written:** 2026-10-19T16:09:49Z · **tests:** all 6 fast checks green
**Branch:** `ccr-2df6ce48-pkrh5v` · **builds on:** `973680864` (this checkpoint is the commit after it)

## Just done
PL app: WalletMoney reader + VM/Activity wiring, Tracker periods by game day, profit card rewrite (all-time wallet headline, Today line with day names, running total + day bars), Diagnostics line; ProfitChartTest 11 pass; research/profit_card_2026-10-19.md, CLAUDE.md P/L rule

## Do this next
PL4: bump v1.0.6 code 346, full tools/test.sh, ship.sh, release.yml, record-release, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  494dc6f90 ckpt 3748: PL data: SlateProfit by game day (5 am-5 am ET, today = today's games, late
  55b142426 ckpt 3747: PL: SlateProfit reworked (today/day/days/lastDays by calendar day, merged c
  2ae72ef29 ckpt 3746: EA: keeper steps aside for a newer keeper, one log line per state, full-loo
  70e7a9f40 ckpt 3745: EA5-EA7: keeper (engine-alarm.yml: always running, 2-min checks in hours, s
  2ede70f12 ckpt 3744: EA1-EA3 done: alarm on main and armed (run 39277095427), watchdog starts a 
  5407f7a7d ckpt 3743: EA: engine-alarm.yml + tools/engine_alarm.py (free runner waits to 08:00 ET
  e1941aeae ckpt 3742: ANALYST 2026-10-19 skipped (no new data)
  7e3f1784e ckpt 3741: pre-release: v1.0.5: the chat with Claude rebuilt from scratch (one account
  26888632e ckpt 3740: CR5 code done + reviewed; landing gate fixed (marks the tree taken BEFORE a
  108d9f54e ckpt 3739: CR5 app chat written: data CloudChat v2 (model/thread/presence) + ChatSync 
```

(23 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
