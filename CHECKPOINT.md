# CHECKPOINT 4809 — read me first, then TASKS.md

**Written:** 2026-11-21T03:56:21Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `4570e2d08` (this checkpoint is the commit after it)

## Just done
ON2: RfqConfig.lossStopFromMs + RfqLedger.Summary.pnlStop24h: the maker's loss stop counts only fills made since the research profile began (it was tripped by the old settings' fills); full floor green (4545 passed; NovigOrderSocketTest flaked once under load, passes alone and on rerun); CHANGES.md ON2

## Do this next
land on main (check origin/main), engine_set --reset for a runner on the new code, then rfq_set lossStopFromMs=<restart ms>, confirm maker resumes (status rfq.halted empty), then send_later check-ins

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f0f3752f8 ckpt 4808: ON1 DONE: engine LIVE (trading, bids via maker=true, scan on), desk resting
  c512d817d ckpt 4807: ON1: engine adopted gates.cloudTrading=LIVE (status tradeMode live); found 
  348e10fef ckpt 4806: ON1: engine_set gates.cloudTrading=LIVE gates.cloudBids=true vigGuards.bidD
  ba8689716 ckpt 4805: ENGINEER 11-20 routine: watch only, nothing to change
  da720cd8b ckpt 4804: full :data tests run for WalletBetMoney fix
  d5d578a96 ckpt 4803: WalletBetMoney: graded win with no Novig payout yet no longer shows as a fu
  520f0318f ckpt 4802: NT2 SHIPPED: v1.0.148 (release green, recorded)
  c87fa9036 ckpt 4801: pre-release: NT2: the NCAAB tab no longer lists small college games with no
  6e782d76a ckpt 4800: NT2: the NCAAB tab leaves out games with no betting odds (no Novig market, 
  2a8959f6f ckpt 4799: NT1 SHIPPED: v1.0.147 (release.yml green, recorded in BUILDLOG): NCAAB tab 
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
