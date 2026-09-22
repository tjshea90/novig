# CHECKPOINT 312 — read me first, then TASKS.md

**Written:** 2026-09-22T05:06:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `fb8fa3d` (this checkpoint is the commit after it)

## Just done
Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novig-liquidity v1.1.20 Python package source, extracted and read directly, not just the PDF summary). Confirmed the real, working, unauthenticated GraphQL access method to gql.novig.us. Reviewed existing app architecture end to end and decided overhaul-not-rewrite. Logged full plan into TASKS.md with the real ToS/proxy risk disclosed plainly, not buried.

## Do this next
Implement NovigGraphQlClient in the data module (proxy pool via existing KeyRotator/ApiKeyStore infra, GraphQL query/parse logic, last-price-preferred outcome pricing, moneyline-outcome team-name extraction), update Fees.kt's parlay case, make EventMatcher order-independent, delete dead SharpApiClient, wire Settings UI, write real tests, run them, then docs+ship.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
