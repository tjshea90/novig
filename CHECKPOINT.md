# CHECKPOINT 3748 — read me first, then TASKS.md

**Written:** 2026-10-19T15:54:03Z · **tests:** all 6 fast checks green
**Branch:** `ccr-2df6ce48-pkrh5v` · **builds on:** `524ea1f5f` (this checkpoint is the commit after it)

## Just done
PL data: SlateProfit by game day (5 am-5 am ET, today = today's games, later() for open stakes on later games, merged curve steps); WalletProfit (cash + open positions - transfers); SlateProfitTest 7 + WalletProfitTest 3 pass

## Do this next
PL app: WalletMoney reader (balance + positions + ledger TRANSFER_IN/OUT, persisted), container + VM + MainActivity wiring, TrackerPeriod by game day, ProfitChart.kt rewrite, ProfitChartTest

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  55b142426 ckpt 3747: PL: SlateProfit reworked (today/day/days/lastDays by calendar day, merged c
  2ae72ef29 ckpt 3746: EA: keeper steps aside for a newer keeper, one log line per state, full-loo
  70e7a9f40 ckpt 3745: EA5-EA7: keeper (engine-alarm.yml: always running, 2-min checks in hours, s
  2ede70f12 ckpt 3744: EA1-EA3 done: alarm on main and armed (run 39277095427), watchdog starts a 
  5407f7a7d ckpt 3743: EA: engine-alarm.yml + tools/engine_alarm.py (free runner waits to 08:00 ET
  e1941aeae ckpt 3742: ANALYST 2026-10-19 skipped (no new data)
  7e3f1784e ckpt 3741: pre-release: v1.0.5: the chat with Claude rebuilt from scratch (one account
  26888632e ckpt 3740: CR5 code done + reviewed; landing gate fixed (marks the tree taken BEFORE a
  108d9f54e ckpt 3739: CR5 app chat written: data CloudChat v2 (model/thread/presence) + ChatSync 
  f0c34da40 ckpt 3738: CR tools live on main (504eafa56); chat + chat-seat branches created by cha
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
