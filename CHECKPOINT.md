# CHECKPOINT 337 — read me first, then TASKS.md

**Written:** 2026-09-25T05:45:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `f4726f6` (this checkpoint is the commit after it)

## Just done
UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, detail, games, tracker, settings at Moto G size) + 2 Compose interaction tests; fixed 4 visual bugs (segmented label overflow, unverified 'bookmaker' chip, misleading no-key empty state, lavender chip color). 103 tests green across engine/data/app

## Do this next
Re-check detail/settings screenshots, then CI workflow tweaks (concurrency), push, confirm CI green, ship v0.4.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M .gitignore
     M CHECKPOINT.md

## Last ten checkpoints
```
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
  f1639a9 ckpt 335: A4 app rework compiles locally (ANDROID_HOME=/opt/android-sdk): VigilantApp co
  8988317 ckpt 334: Data-module tests written and green: engine+data 91/91 (NovigTextTest, TeamMat
  b860d7a ckpt 333: A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag
  551afcd ckpt 332: A2 engine done: FairValue (sharp/average/blend + fallback + minBooks), per-mar
  908b0f6 ckpt 331: Logged Tj's 2026-09-25 build request into TASKS.md as milestones A/B/C
  372174e ckpt 330: Read Novig's official v3 API docs + OpenAPI spec, verified the public no-key r
  9c728ab ckpt 329: Logged Tj's 2026-09-25 Novig API beta access message into TASKS.md
  9aeda4b ckpt 328: v0.3.3 shipped and confirmed green (CI run 35695873352, release run 3569566794
  30a5637 ckpt 327: Bumped versionCode 6->7 / versionName 0.3.2->0.3.3 for the OkHttp Authenticato
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
