# CHECKPOINT 334 — read me first, then TASKS.md

**Written:** 2026-09-25T05:25:25Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `17c4904` (this checkpoint is the commit after it)

## Just done
Data-module tests written and green: engine+data 91/91 (NovigTextTest, TeamMatcherTest, NovigPublicClientTest w/ ETag+429+edge-403, TheOddsApiClientTest rewrite, PlannerPricingTest incl. 3-way soccer + home/away swap, ScannerTest timing/credits, JsonFileStoreTest, BetTrackerTest). Fixed Scanner to stamp reference fetch time itself

## Do this next
A4: app module rework (container, ViewModel live loop, OddsJam-style screens); compile locally with ANDROID_HOME=/opt/android-sdk

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/scanner/Scanner.kt

## Last ten checkpoints
```
  b860d7a ckpt 333: A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag
  551afcd ckpt 332: A2 engine done: FairValue (sharp/average/blend + fallback + minBooks), per-mar
  908b0f6 ckpt 331: Logged Tj's 2026-09-25 build request into TASKS.md as milestones A/B/C
  372174e ckpt 330: Read Novig's official v3 API docs + OpenAPI spec, verified the public no-key r
  9c728ab ckpt 329: Logged Tj's 2026-09-25 Novig API beta access message into TASKS.md
  9aeda4b ckpt 328: v0.3.3 shipped and confirmed green (CI run 35695873352, release run 3569566794
  30a5637 ckpt 327: Bumped versionCode 6->7 / versionName 0.3.2->0.3.3 for the OkHttp Authenticato
  cb8e8e5 ckpt 326: Diagnosed and fixed a real OkHttp Authenticator bug found from Tj's own device
  736058e ckpt 325: Shipped v0.3.2 for real (confirmed green: run 35693512531, release published).
  68871a7 ckpt 324: Extended the release.yml self-heal fix: the actual failure was a leftover DRAF
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
