# CHECKPOINT 314 — read me first, then TASKS.md

**Written:** 2026-09-22T05:21:01Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `e207e78` (this checkpoint is the commit after it)

## Just done
Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replaces SharpAPI wiring, fee formula update, KeyRotator-reuse-for-proxies note) and RESEARCH.md (new §4.4 with full verified findings from the actual novig-liquidity package source, §9 rewritten to honestly distinguish the ToS-gray-area direct-GraphQL path actually wired in from the still-dormant 'official API' clean case, §10 items 1/3 resolved, two new open items for what's still best-effort/unconfirmed against real live data). Updated TASKS.md. Deliberately did not build a SportsGameOdds reference-leg client (PDF §5's suggestion) — TheOddsApiClient already works and is confirmed live; that would be scope creep beyond what was asked.

## Do this next
Verify the app module compiles for real via CI (no local Android SDK), then bump version and ship per CLAUDE.md's Releasing section, then report back to Tj with the real risk summary and next steps (get a proxy provider, paste credentials into Settings).

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
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

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
