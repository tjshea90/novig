# CHECKPOINT 4795 — read me first, then TASKS.md

**Written:** 2026-11-20T19:45:19Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `8a8a59844` (this checkpoint is the commit after it)

## Just done
PS1: second parlay study (21,425 graded requests, 4 slates), settings audit, minLead 1 h + minFair .35 + new maxLeadMs 12 h in the profile (cloud-config 763581dab), tools/rfq_calibration_study.py + daily STUDY.md; full suite green (4514 passed)

## Do this next
land on main (push.sh); the engine is OFF; when Tj turns it on confirm status rfq.config shows minFair .35 / minLeadMs 3600000 / maxLeadMs 43200000 and configLock; read STUDY.md in the rfq-pnl-study Release after the 10:37 UTC run; no app release needed

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  86ed9fb85 ckpt 4794: PF2: verified margin-vs-shape on the graded table (shape 8.0% of P&L varian
  2a9006344 ckpt 4793: PF1 DONE: parlay profit profile live on cloud-config (rfq-maker-engine.json
  a047ad3ab ckpt 4792: pre-release: PF1: the parlay maker runs on Claude's profit profile; the Par
  69a013492 ckpt 4791: PF1: CLAUDE.md standing rule for the profile file; waiting for CI on d92a8f
  d92a8f21d ckpt 4790: PF1: profile written to cloud-config rfq-maker-engine.json (81ac24d85) and 
  cd41e97d7 ckpt 4789: PF1: engine profit profile file + precedence over the phone's rfq-maker.jso
  124c900f8 ckpt 4788: MK57: maxLegFair .8 -> .7 (Tj: sucker bets, not sharp heavy-favourite actio
  8ddbf3625 ckpt 4787: MK56: identical-parlay guard (maxSameParlay 1, maxParlaysPerOutcome 2) code
  bba77e4ab ckpt 4786: MK55: 16:20 check: job handover ok (44767621550), 8 fills today (15:37 FSU 
  1486dec0b ckpt 4785: MK54: walletLegShare 0.25 (same two legs filled twice in 7 minutes; per-leg
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
