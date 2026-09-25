# CHECKPOINT 358 — read me first, then TASKS.md

**Written:** 2026-09-25T13:31:00Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `8bf46dc` (this checkpoint is the commit after it)

## Just done
Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.md surface list, NOVIG_API §11.1 (signed book route, stream unwired), RESEARCH §11.5 live results; TASKS R1-R5 ticked with named tests

## Do this next
R6: push, confirm CI green on the commit, ship.sh 'v0.6.0 ...', trigger release.yml on main, get_release_by_tag v0.6.0, record-release.sh v0.6.0 10, report to Tj (answers to Q1-Q5 + multi-key advice + link)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
