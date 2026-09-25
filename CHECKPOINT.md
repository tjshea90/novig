# CHECKPOINT 333 — read me first, then TASKS.md

**Written:** 2026-09-25T05:21:16Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `5f58fc6` (this checkpoint is the commit after it)

## Just done
A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag book cache, 429/edge-403 backoff), NovigText parsers, TeamMatcher, Leagues, ScanSettings, Planner (event+side matching, only reference-quoted lines), Pricing (pure), Scanner (timing/caching), JsonFileStore (atomic), BetTracker (CLV). Removed GraphQL/proxy + deprecated v2 clients

## Do this next
Write data-module tests (MockWebServer fixtures from live 2026-09-25 shapes), then app module rework

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  551afcd ckpt 332: A2 engine done: FairValue (sharp/average/blend + fallback + minBooks), per-mar
  908b0f6 ckpt 331: Logged Tj's 2026-09-25 build request into TASKS.md as milestones A/B/C
  372174e ckpt 330: Read Novig's official v3 API docs + OpenAPI spec, verified the public no-key r
  9c728ab ckpt 329: Logged Tj's 2026-09-25 Novig API beta access message into TASKS.md
  9aeda4b ckpt 328: v0.3.3 shipped and confirmed green (CI run 35695873352, release run 3569566794
  30a5637 ckpt 327: Bumped versionCode 6->7 / versionName 0.3.2->0.3.3 for the OkHttp Authenticato
  cb8e8e5 ckpt 326: Diagnosed and fixed a real OkHttp Authenticator bug found from Tj's own device
  736058e ckpt 325: Shipped v0.3.2 for real (confirmed green: run 35693512531, release published).
  68871a7 ckpt 324: Extended the release.yml self-heal fix: the actual failure was a leftover DRAF
  982ee12 ckpt 323: Diagnosed and fixed a real release.yml bug hit for real shipping v0.3.2: cance
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
