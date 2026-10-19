# CHECKPOINT 3760 — read me first, then TASKS.md

**Written:** 2026-10-19T18:55:13Z · **tests:** all 6 fast checks green
**Branch:** `ccr-413cf302-mvy06f` · **builds on:** `6aecea382` (this checkpoint is the commit after it)

## Just done
added delete-old-releases.yml (APK releases only, keep newest 3 + v0.17.0, dry_run input)

## Do this next
after :data:test passes main gets both workflows; dispatch delete-stale-branches then delete-old-releases (dry_run first)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    ?? .github/workflows/delete-old-releases.yml

## Last ten checkpoints
```
  74f7f5c20 ckpt 3759: added delete-stale-branches.yml (manual one-shot, 71 listed session branche
  a1a0f2073 ckpt 3758: merged mirage's CH24a (watchdog unreadable-branch fix) into my branch, self
  65d1d1f29 ckpt 3757: ENGINEER 10-19 routine: WATCH only, engine healthy, no change
  0e0b99791 ckpt 3756: v1.0.6 released (PL + PL5) and recorded; chat told; keeper run 39284378407 
  c34750c5c ckpt 3755: pre-release: v1.0.6: the Tracker's profit card rebuilt (PL): Today is today
  346639a1e ckpt 3754: EA8a/EA9 written up (keeper import crash, CI flood: ci.yml main-only, data-
  8e2363d3d ckpt 3753: PL: Tracker tests follow the new card (all-time headline by tag, clv card s
  1b1ac7101 ckpt 3752: PL5: the wallet figure reads every money-moving ledger kind (transfers, dep
  cce5b1756 ckpt 3751: PL: NovigTradingClient.transfers() reads TRANSFER_IN/OUT with no time bound
  18072eab0 ckpt 3750: EA fix: both keepers (15:29Z, 15:39Z) died at start on 'import engine_watch
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
