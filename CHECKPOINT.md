# CHECKPOINT 3758 — read me first, then TASKS.md

**Written:** 2026-10-19T18:47:49Z · **tests:** all 6 fast checks green
**Branch:** `ccr-413cf302-mvy06f` · **builds on:** `6ca1cdfe3` (this checkpoint is the commit after it)

## Just done
merged mirage's CH24a (watchdog unreadable-branch fix) into my branch, selftests ok; SC1 scanner rescan audit started

## Do this next
land on main, restart keeper, then SC1a audit

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  65d1d1f29 ckpt 3757: ENGINEER 10-19 routine: WATCH only, engine healthy, no change
  0e0b99791 ckpt 3756: v1.0.6 released (PL + PL5) and recorded; chat told; keeper run 39284378407 
  c34750c5c ckpt 3755: pre-release: v1.0.6: the Tracker's profit card rebuilt (PL): Today is today
  346639a1e ckpt 3754: EA8a/EA9 written up (keeper import crash, CI flood: ci.yml main-only, data-
  8e2363d3d ckpt 3753: PL: Tracker tests follow the new card (all-time headline by tag, clv card s
  1b1ac7101 ckpt 3752: PL5: the wallet figure reads every money-moving ledger kind (transfers, dep
  cce5b1756 ckpt 3751: PL: NovigTradingClient.transfers() reads TRANSFER_IN/OUT with no time bound
  18072eab0 ckpt 3750: EA fix: both keepers (15:29Z, 15:39Z) died at start on 'import engine_watch
  6fe62af1b ckpt 3749: PL app: WalletMoney reader + VM/Activity wiring, Tracker periods by game da
  2ae72ef29 ckpt 3746: EA: keeper steps aside for a newer keeper, one log line per state, full-loo
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
