# CHECKPOINT 313 — read me first, then TASKS.md

**Written:** 2026-09-22T05:17:42Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `0dc85ed` (this checkpoint is the commit after it)

## Just done
Built and verified NovigGraphQlClient (data module): real, direct GraphQL client for Novig's unauthenticated internal backend, reusing KeyRotator/ApiKeyStore for proxy rotation, last-price-preferred outcome pricing, moneyline-outcome-based team name extraction with description-parsing fallback, best-effort market-type classification. Made EventMatcher order-independent (real correctness fix). Updated Fees.kt's parlay case from Unknown to the now-confirmed 0.10-multiplier formula. Deleted dead SharpApiClient (confirmed no free Novig access). Wired ScannerViewModel + Settings UI (proxy field with prominent risk-disclosure copy, opt-in only). All 95 engine+data tests green (22 new NovigGraphQlClient tests covering price selection, market classification, team-name extraction, proxy parsing, GraphQL error handling).

## Do this next
Update BRIEF.md/RESEARCH.md with the new §4.4 access method + honest risk disclosure, resolve now-answered open items. Then evaluate adding SportsGameOdds as a reference-leg option (PDF §5). Then verify app-module CI, bump version, ship, send Tj the release link.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1255f07 ckpt 312: Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novi
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
```

(25 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
