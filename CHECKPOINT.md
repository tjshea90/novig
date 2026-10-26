# CHECKPOINT 4269 — read me first, then TASKS.md

**Written:** 2026-10-26T00:44:55Z · **tests:** all 9 fast checks green
**Branch:** `ccr-db238840-epvfzh` · **builds on:** `5e87d936a` (this checkpoint is the commit after it)

## Just done
PX14b: Novig's own close wired into the CLV job (propline_clv_job.sh: orders_clv lines -> novig_trade_close -> --novig-close); SxBetStreamTest race made robust; full test.sh green

## Do this next
dispatch engine-grade.yml + propline.yml task clv on main, read new results/CLV coverage, tell Tj before/after

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  98e213980 ckpt 4268: PX14: results gap (23% open) fixed in code via Novig's own settlement (Grad
  fe084774f ckpt 4267: PX13b: Kalshi quotes kept current between scans while the socket vouches an
  33cd0c5d5 ckpt 4266: PX13a answered (ParlayAPI carries 4 books on that game); PX13c census pinnS
  a6f817fa0 ckpt 4265: PX12 landed on main, engine reset; PX12c answered (no socket contention)
  29d2da2d3 ckpt 4264: PX12: full scan every 2 min, not 6 (far games' fair was 295 s old on every 
  84ec1b9df ckpt 4263: PX11d-2: census line 'book outliers' = each book's share left out by BadOdd
  51935d90a ckpt 4262: pre-release: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engi
  c3a33ddfc ckpt 4261: pre-ship: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engine'
  3bf49b42a ckpt 4260: PR1a/b: Engine pull refreshes engine data only; Engine › APIs has its own
  4c8fb2f43 ckpt 4259: research index rebuilt; engine reset onto main (PX11f fix)
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
