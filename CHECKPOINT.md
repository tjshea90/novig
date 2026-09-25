# CHECKPOINT 359 — read me first, then TASKS.md

**Written:** 2026-09-25T13:34:02Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `9c20089` (this checkpoint is the commit after it)

## Just done
pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing on a timer); Novig reads paced (4/s, burst 10, 2 at a time, pause+slow on 429) or via the signed per-key book route with a key; free fair odds from Polymarket + Kalshi (no key) and Pinnacle via pinnapi (free key) merged per game, The Odds API optional and re-used 15m; per-game line cap; Kalshi depth + Polymarket liquidity filters. 173 tests; live scan NFL 14/16, MLB 16/17, NCAAF 112/120 matched, zero 429s. (versionCode 10, v0.6.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.6.0), then run: bash tools/record-release.sh v0.6.0 10 "v0.6.0: manual-only scans (Scan button / pull to refresh, nothing on a timer); Novig reads paced (4/s, burst 10, 2 at a time, pause+slow on 429) or via the signed per-key book route with a key; free fair odds from Polymarket + Kalshi (no key) and Pinnacle via pinnapi (free key) merged per game, The Odds API optional and re-used 15m; per-game line cap; Kalshi depth + Polymarket liquidity filters. 173 tests; live scan NFL 14/16, MLB 16/17, NCAAF 112/120 matched, zero 429s."

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
