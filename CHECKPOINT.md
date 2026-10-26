# CHECKPOINT 4268 — read me first, then TASKS.md

**Written:** 2026-10-26T00:32:30Z · **tests:** all 9 fast checks green
**Branch:** `ccr-3cb0aa53-b2tjli` · **builds on:** `0cf27929f` (this checkpoint is the commit after it)

## Just done
PX14: results gap (23% open) fixed in code via Novig's own settlement (GradeMain/TradeSettle, tested); CLV gap (41% no close) half-built: orders_clv.py lines + --novig-close (selftest ok). PX11f/PX12/PX13 shipped (v1.0.86 released+recorded; engine on 2-min full scans + Kalshi re-dating)

## Do this next
1) wire orders_clv lines + novig_trade_close into tools/propline_clv_job.sh (see TASKS PX14b next); 2) bash tools/test.sh, land, dispatch engine-grade.yml + propline.yml clv; 3) tell Tj: v1.0.86 link https://github.com/tjshea90/novig/releases/tag/v1.0.86, PX11f split-event fix confirmed live, PX12 cause (far games re-read every 6 min -> 2), PX12c no socket contention, PX13 ParlayAPI carries only 4 books on Chiefs@Broncos, Kalshi re-dating, outlier audit (worst book 0.6%, none to exclude), PX14 coverage numbers

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  fe084774f ckpt 4267: PX13b: Kalshi quotes kept current between scans while the socket vouches an
  33cd0c5d5 ckpt 4266: PX13a answered (ParlayAPI carries 4 books on that game); PX13c census pinnS
  a6f817fa0 ckpt 4265: PX12 landed on main, engine reset; PX12c answered (no socket contention)
  29d2da2d3 ckpt 4264: PX12: full scan every 2 min, not 6 (far games' fair was 295 s old on every 
  84ec1b9df ckpt 4263: PX11d-2: census line 'book outliers' = each book's share left out by BadOdd
  51935d90a ckpt 4262: pre-release: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engi
  c3a33ddfc ckpt 4261: pre-ship: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engine'
  3bf49b42a ckpt 4260: PR1a/b: Engine pull refreshes engine data only; Engine › APIs has its own
  4c8fb2f43 ckpt 4259: research index rebuilt; engine reset onto main (PX11f fix)
  76fc04638 ckpt 4258: PX11f/c/d: ParlayAPI split-event fix, bid 3-book floor, per-book outlier ce
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
