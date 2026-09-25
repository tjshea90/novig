# CHECKPOINT 355 — read me first, then TASKS.md

**Written:** 2026-09-25T13:12:21Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `330fbfa` (this checkpoint is the commit after it)

## Just done
v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (reuse window, request key, exchange merge, source toggle on reprice, no fetch before first scan); ExchangeClientsTest (Polymarket/Kalshi/pinnapi parsers + request shapes from live 2026-09-25 samples); RateGateTest; NovigPublicClientTest pacing (<=2 in flight, rate), catalog shares gate, signed key route + public fallback; PlannerPricingTest merge/flip, duplicate book, etDate matching, line cap

## Do this next
App layer: AppContainer sources (pinnapi key, Polymarket, Kalshi, Odds API) + novig.keyed; MainViewModel manual scan() with progress, remove runLiveLoop/stream/setPricesVisible, settings changes reprice only, migrate settings; MainActivity no loop; FeedScreen Scan button/progress/empty states; Games pull-to-refresh; Settings: sources section + pinnapi key + lines per game + reuse chips, remove refresh section; NovigKeySection without stream; StatusLine; SampleScan/ScreenshotTest; then CI + ship v0.6.0 code 10

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
