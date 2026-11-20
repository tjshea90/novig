# CHECKPOINT 4800 — read me first, then TASKS.md

**Written:** 2026-11-20T21:46:24Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `405ce1279` (this checkpoint is the commit after it)

## Just done
NT2: the NCAAB tab leaves out games with no betting odds (no Novig market, no ESPN line) once Novig's board is read, footnote counts them; full suite green (4543); v1.0.148 (code 488)

## Do this next
land on main (push.sh), wait for CI green, ship.sh, release.yml, record-release.sh v1.0.148 488; tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2a8959f6f ckpt 4799: NT1 SHIPPED: v1.0.147 (release.yml green, recorded in BUILDLOG): NCAAB tab 
  2dd3aab57 ckpt 4798: pre-release: NT1: your NCAAB tab bets tracked: bets, money bet, profit/loss
  3ed97d792 ckpt 4797: NT1: the NCAAB tab's own bet record (origin tag, NcaabBetStats, card in the
  51170169b ckpt 4796: PW1: wallet-adaptive parlay maker (prop reserve shares, outcome count per $
  d62480947 ckpt 4795: PS1: second parlay study (21,425 graded requests, 4 slates), settings audit
  86ed9fb85 ckpt 4794: PF2: verified margin-vs-shape on the graded table (shape 8.0% of P&L varian
  2a9006344 ckpt 4793: PF1 DONE: parlay profit profile live on cloud-config (rfq-maker-engine.json
  a047ad3ab ckpt 4792: pre-release: PF1: the parlay maker runs on Claude's profit profile; the Par
  69a013492 ckpt 4791: PF1: CLAUDE.md standing rule for the profile file; waiting for CI on d92a8f
  d92a8f21d ckpt 4790: PF1: profile written to cloud-config rfq-maker-engine.json (81ac24d85) and 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
