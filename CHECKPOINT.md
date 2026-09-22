# CHECKPOINT 317 — read me first, then TASKS.md

**Written:** 2026-09-22T05:45:28Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `bee5e5b` (this checkpoint is the commit after it)

## Just done
Added a free 'direct access, no proxy' opt-in path answering Tj's question about free proxy alternatives (VPN, airplane-mode IP cycling). NovigGraphQlClient now accepts an empty proxy list and connects directly instead of refusing to run (previously KeyRotator's own init check would have crashed on an empty list). New Settings toggle, separate from the proxy list, with honest copy about the tradeoff. ScannerViewModel/SettingsViewModel/SettingsScreen/MainActivity wired through. 97 tests green (2 new construction-regression tests).

## Do this next
Update BRIEF.md/RESEARCH.md with this addition and the honest technical reasoning (VPN = single non-rotating IP possibly already blocklisted; airplane mode depends on carrier NAT; this app's light manual-refresh volume may not need a pool at all), verify CI green for the app module, ship as v0.3.1, tell Tj it's ready to try for free.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  684b84d ckpt 316: Shipped v0.3.0 for real: confirmed CI green (run 35690359093) and release gree
  d18bc8a ckpt 315: Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
  1255f07 ckpt 312: Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novi
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
```

(17 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
