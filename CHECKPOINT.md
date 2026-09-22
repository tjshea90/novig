# CHECKPOINT 321 — read me first, then TASKS.md

**Written:** 2026-09-22T05:55:11Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `533d32f` (this checkpoint is the commit after it)

## Just done
Tj tried the new direct-mode toggle for real and hit a genuine HTTP 503 from Novig on the first attempt (screenshot). Diagnosed and fixed a real bug found while investigating: 502/503/504 were lumped into the same hard-rejection bucket as 401/403, when they more accurately mean 'temporarily unavailable' — reclassified as rate-limited/retryable, error message now says 'temporarily rejected' for these. Added real HTTP-layer tests via MockWebServer for the direct-mode path (closes a gap flagged as untested when this shipped) — 101 tests green, including 4 new end-to-end tests (503, 403, full round trip, one-event-failure-doesn't-sink-the-scan).

## Do this next
Verify app-module CI green, ship as v0.3.2, tell Tj honestly: this confirms direct access probably doesn't work reliably without a proxy (matches the reference package's own finding), suggest trying again or with his VPN on before concluding proxies are required.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5b43606 ckpt 320: Shipped v0.3.1 for real: confirmed CI green (run 35692029112) and release gree
  668dbd5 ckpt 319: Confirmed CI green for real on the direct-mode addition (run 35692029112, conc
  82ed486 ckpt 318: Updated BRIEF.md/RESEARCH.md (new §4.4.1) documenting the free direct-access 
  a94144e ckpt 317: Added a free 'direct access, no proxy' opt-in path answering Tj's question abo
  684b84d ckpt 316: Shipped v0.3.0 for real: confirmed CI green (run 35690359093) and release gree
  d18bc8a ckpt 315: Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
  1255f07 ckpt 312: Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novi
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
