# CHECKPOINT 4725 — read me first, then TASKS.md

**Written:** 2026-11-20T00:10:32Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `a52b56445` (this checkpoint is the commit after it)

## Just done
MK21: ladder extrapolation of unposted NBA/NHL/NFL prop rungs (RfqLadder.extrapolate, ladderExtrapolate), RfqLadderTest, tools/ladder_extrap.py, research note; explained the quote drop (LIVE switch + options) and the constant 38% fair gap; full suite green

## Do this next
deploy: engine reset (confirm startedAtMs), then read journal rows with an 'extrapolated' leg vs market executed prices; decide the 20% prop premium on that evidence; MK20 shadow comparison; protected prop markets; Kalshi player props

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2a6be887b ckpt 4724: MK20: same-game prop-pair model (PropPairs, upper-bound ratios from PropLin
  a73e68eaa ckpt 4723: MK10 23:51: loss stop set to 0 from the app by Tj; quoting again
  152f10ec6 ckpt 4722: MK10 23:21: loss stop tripped (-26.23), maker halted by design
  a16930efa ckpt 4721: MK19/MK17d: app tests green; landing the engine fixes
  6dc92d160 ckpt 4720: MK19: ParlayAPI Business key no longer held back (usage-meter slack scales 
  8089c1808 ckpt 4719: MK10 22:51: LIVE realized -26.2, favourite-parlay bias note
  f932bfc60 ckpt 4718: pre-release: MK18: parlay controls show your change at once and confirm whe
  344b5e7cb ckpt 4717: pre-ship: MK18: parlay controls show your change at once and confirm when t
  88d5cf7b7 ckpt 4716: MK17c: collateral hold 120 s -> 45 s after the auction closes (fills come 6
  1c29ee335 ckpt 4715: pre-release: MK17b: Parlays tiles show the whole wallet and the cash free t
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
