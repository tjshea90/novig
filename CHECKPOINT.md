# CHECKPOINT 4647 — read me first, then TASKS.md

**Written:** 2026-11-18T05:19:43Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `b8e884a27` (this checkpoint is the commit after it)

## Just done
MK7: parlay maker LIVE since 04:50 UTC 2026-11-18 (Tj: 'Live, small limits'): game lines, 2-3 legs, 25/50 collateral, 6% return, 20 dollar 24 h loss stop; first quotes sent, no refusals, executions route answers; board note posted about engine resets; engine churn from other sessions keeps restarting jobs (each restart = ~10 min cold)

## Do this next
watch status.rfq (monitor bi57vtuv9) for the first win/confirm/fill and the first settlements; read rfq-<day> journals (QUOTE_SENT, CONFIRM, QUOTE_EXECUTED, LEDGER, EXECUTED with ourPrice) from cloud-archive Releases; ship v1.0.138 with the Settled parlays tile; MK5 grader; MK6 same-game; stop with: python3 tools/rfq_set.py mode=OFF

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b8e884a27 ckpt 4646: engine reset for PropLine EV sweep; DG3 live lag not yet diagnosed
  4c681795a ckpt 4645: DG2 tests green; DG3 logged
  4f9d09d60 ckpt 4644: DG2: PropLine EV sweep on, phone bets kept 8 days
  363ba23cc ckpt 4643: DG2 logged
  a23a545ca ckpt 4642: chat: triaged diagnostics report DG1
  3891f24bb ckpt 4641: MKV3: LIVE prerequisites: 24 h realized-loss stop from Novig's executions (
  09268ea2c ckpt 4640: MKV: v1.0.137 released (recorded); MKV2 game-lines-only maker + 6% return d
  f70eb1882 ckpt 4639: MKV2: parlays with a player prop are not quoted or counted (market pays +6.
  ed890a96a ckpt 4638: pre-release: MKV: the Novig parlay maker in the app (Engine > Parlays: stat
  ade0fa424 ckpt 4637: MK5: rfq-catalog.yml snapshots Novig's public catalog every 4 h into the rf
```
