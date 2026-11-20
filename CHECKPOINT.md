# CHECKPOINT 4798 — read me first, then TASKS.md

**Written:** 2026-11-20T21:29:16Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `ea566f781` (this checkpoint is the commit after it)

## Just done
pre-release: NT1: your NCAAB tab bets tracked: bets, money bet, profit/loss, record and percent won, all time and by day, in the NCAAB tab and on the Tracker (new NCAAB filter) (versionCode 487, v1.0.147)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.147), then run: bash tools/record-release.sh v1.0.147 487 "NT1: your NCAAB tab bets tracked: bets, money bet, profit/loss, record and percent won, all time and by day, in the NCAAB tab and on the Tracker (new NCAAB filter)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3ed97d792 ckpt 4797: NT1: the NCAAB tab's own bet record (origin tag, NcaabBetStats, card in the
  51170169b ckpt 4796: PW1: wallet-adaptive parlay maker (prop reserve shares, outcome count per $
  d62480947 ckpt 4795: PS1: second parlay study (21,425 graded requests, 4 slates), settings audit
  86ed9fb85 ckpt 4794: PF2: verified margin-vs-shape on the graded table (shape 8.0% of P&L varian
  2a9006344 ckpt 4793: PF1 DONE: parlay profit profile live on cloud-config (rfq-maker-engine.json
  a047ad3ab ckpt 4792: pre-release: PF1: the parlay maker runs on Claude's profit profile; the Par
  69a013492 ckpt 4791: PF1: CLAUDE.md standing rule for the profile file; waiting for CI on d92a8f
  d92a8f21d ckpt 4790: PF1: profile written to cloud-config rfq-maker-engine.json (81ac24d85) and 
  cd41e97d7 ckpt 4789: PF1: engine profit profile file + precedence over the phone's rfq-maker.jso
  124c900f8 ckpt 4788: MK57: maxLegFair .8 -> .7 (Tj: sucker bets, not sharp heavy-favourite actio
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
