# CHECKPOINT 356 — read me first, then TASKS.md

**Written:** 2026-09-25T13:18:33Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `66ac7f3` (this checkpoint is the commit after it)

## Just done
v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() with progress, settings changes reprice only (no network), migrate settings; MainActivity has no loop; Feed/Games Scan button + pull-to-refresh + progress bar + 'tap Scan' empty states; Settings: Fair odds sources (pinnapi key, Polymarket, Kalshi, Odds API reuse chips), alternate lines per game, refresh section removed; NovigKeySection without stream; AppContainer.referenceSources + novig.keyed

## Do this next
Look at every screenshot PNG for layout bugs; then full ./gradlew test locally; update NOVIG_API/RESEARCH/BRIEF docs + TASKS ticks; bump v0.6.0 code 10; CI; ship; report to Tj with research answer

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     D app/screenshots/1c_feed_no_key.png

## Last ten checkpoints
```
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
