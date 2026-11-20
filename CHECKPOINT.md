# CHECKPOINT 4728 — read me first, then TASKS.md

**Written:** 2026-11-20T00:45:45Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `8e60330ac` (this checkpoint is the commit after it)

## Just done
MK22: PropLadderFair: the maker's prop fair from every book's price at a strike incl. N+ milestone ladders (36% -> 99% of Novig prop strikes), PropLine parser keeps milestones, ladderVotes switch, tests, real-board validation, research note; full suite green

## Do this next
deploy: engine reset, confirm startedAtMs; read engine log 'RFQ prop votes:' and the status no-fair share; then ParlayAPI ladders, game-line alternates, protected markets, prop premium on journal evidence

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0b0247ecf ckpt 4727: MK22: propcov task (what PropLine really sends for player props: two-sided 
  51f0f638c ckpt 4726: ANALYST 2026-11-19: no change, watch note logged
  d8c6c2271 ckpt 4725: MK21: ladder extrapolation of unposted NBA/NHL/NFL prop rungs (RfqLadder.ex
  2a6be887b ckpt 4724: MK20: same-game prop-pair model (PropPairs, upper-bound ratios from PropLin
  a73e68eaa ckpt 4723: MK10 23:51: loss stop set to 0 from the app by Tj; quoting again
  152f10ec6 ckpt 4722: MK10 23:21: loss stop tripped (-26.23), maker halted by design
  a16930efa ckpt 4721: MK19/MK17d: app tests green; landing the engine fixes
  6dc92d160 ckpt 4720: MK19: ParlayAPI Business key no longer held back (usage-meter slack scales 
  8089c1808 ckpt 4719: MK10 22:51: LIVE realized -26.2, favourite-parlay bias note
  f932bfc60 ckpt 4718: pre-release: MK18: parlay controls show your change at once and confirm whe
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
