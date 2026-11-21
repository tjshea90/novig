# CHECKPOINT 4808 — read me first, then TASKS.md

**Written:** 2026-11-21T03:43:19Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `035810480` (this checkpoint is the commit after it)

## Just done
ON1 DONE: engine LIVE (trading, bids via maker=true, scan on), desk resting 123 real bids $64.44; bidDeployPct restored to 100; CHANGES.md + TASKS.md updated

## Do this next
if Tj wants the parlay maker quoting now: rfq_set.py dayLossStopPct (his word); in-play parlays liveParlays only on his word; consider a cash reserve so bid fills cannot starve parlay collateral

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  c512d817d ckpt 4807: ON1: engine adopted gates.cloudTrading=LIVE (status tradeMode live); found 
  348e10fef ckpt 4806: ON1: engine_set gates.cloudTrading=LIVE gates.cloudBids=true vigGuards.bidD
  ba8689716 ckpt 4805: ENGINEER 11-20 routine: watch only, nothing to change
  da720cd8b ckpt 4804: full :data tests run for WalletBetMoney fix
  d5d578a96 ckpt 4803: WalletBetMoney: graded win with no Novig payout yet no longer shows as a fu
  520f0318f ckpt 4802: NT2 SHIPPED: v1.0.148 (release green, recorded)
  c87fa9036 ckpt 4801: pre-release: NT2: the NCAAB tab no longer lists small college games with no
  6e782d76a ckpt 4800: NT2: the NCAAB tab leaves out games with no betting odds (no Novig market, 
  2a8959f6f ckpt 4799: NT1 SHIPPED: v1.0.147 (release.yml green, recorded in BUILDLOG): NCAAB tab 
  2dd3aab57 ckpt 4798: pre-release: NT1: your NCAAB tab bets tracked: bets, money bet, profit/loss
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
