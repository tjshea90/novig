# CHECKPOINT 357 — read me first, then TASKS.md

**Written:** 2026-09-25T13:29:58Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `9e1dbe0` (this checkpoint is the commit after it)

## Just done
v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); release APK 4.65MB builds; live scan verified (NFL 14/16 games matched via Polymarket+Kalshi, MLB 16/17, NCAAF 112/120, zero 429s); Kalshi top-of-book depth filter (>=100 contracts) + test; UI copy fixes (no-match state, list grammar, single progress indicator)

## Do this next
Docs: NOVIG_API §5.1/§11.1 (signed book route, pacing, stream unwired), RESEARCH §11.4 live results, BRIEF manual-only rule, CLAUDE.md surface list; tick TASKS R1-R6 with tests; then push, CI green, ship.sh, release.yml, record-release, report to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
