# CHECKPOINT 354 — read me first, then TASKS.md

**Written:** 2026-09-25T13:07:22Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `72c9062` (this checkpoint is the commit after it)

## Just done
v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (ReferenceSource impls), TheOddsApiClient as ReferenceSource (selected families only, spaced calls, reuse window), Planner multi-provider match+merge with per-game line cap (linesPerGame), manual Scanner.scan(settings, sources, onProgress) with reuse + stale handling, NovigPublicClient RateGate pacing (4/s burst 10, 2 at a time, pause+slowdown on 429) + signed per-key book route with public fallback; HybridNovigSource removed; ScanSettings schema migrate (exchanges sharp)

## Do this next
Update data tests (ScannerTest, PlannerPricingTest, TheOddsApiClientTest .fetch) + new tests for Polymarket/Kalshi/pinnapi parsers, merge, line cap, RateGate, paced books; then app: VM manual scan + progress, remove live loop/stream wiring, Settings sources section + pinnapi key, FeedScreen Scan button, NovigKeySection; screenshots; CI; ship v0.6.0 code 10

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
  8501708 ckpt 344: B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccoun
```

(17 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
