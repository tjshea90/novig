# CHECKPOINT 4806 — read me first, then TASKS.md

**Written:** 2026-11-21T03:39:08Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `7d995a6c8` (this checkpoint is the commit after it)

## Just done
ON1: engine_set gates.cloudTrading=LIVE gates.cloudBids=true vigGuards.bidDeployPct=50 --on pushed to cloud-config (04c5d75d8); CHANGES.md entry with revert; maker is LIVE in profile but halted by its own 24h loss stop (-$60.17 vs $26.26), liveParlays left SHADOW

## Do this next
verify engine status shows tradeMode live and desk active (cloud-feed status.json), then tell Tj: parlay maker halted by loss stop, offer rfq_set dayLossStopPct, offer liveParlays

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ba8689716 ckpt 4805: ENGINEER 11-20 routine: watch only, nothing to change
  da720cd8b ckpt 4804: full :data tests run for WalletBetMoney fix
  d5d578a96 ckpt 4803: WalletBetMoney: graded win with no Novig payout yet no longer shows as a fu
  520f0318f ckpt 4802: NT2 SHIPPED: v1.0.148 (release green, recorded)
  c87fa9036 ckpt 4801: pre-release: NT2: the NCAAB tab no longer lists small college games with no
  6e782d76a ckpt 4800: NT2: the NCAAB tab leaves out games with no betting odds (no Novig market, 
  2a8959f6f ckpt 4799: NT1 SHIPPED: v1.0.147 (release.yml green, recorded in BUILDLOG): NCAAB tab 
  2dd3aab57 ckpt 4798: pre-release: NT1: your NCAAB tab bets tracked: bets, money bet, profit/loss
  3ed97d792 ckpt 4797: NT1: the NCAAB tab's own bet record (origin tag, NcaabBetStats, card in the
  51170169b ckpt 4796: PW1: wallet-adaptive parlay maker (prop reserve shares, outcome count per $
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
