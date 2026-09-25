# CHECKPOINT 338 — read me first, then TASKS.md

**Written:** 2026-09-25T05:46:57Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `2d58562` (this checkpoint is the commit after it)

## Just done
Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resolution, Odds API lists novig), BRIEF build trap 6 (local SDK + Maven mirror) + new locked decisions, CLAUDE.md test protocols now name real tabs/subsystems; TASKS A4/A5/C1 ticked

## Do this next
Wait for CI run 36099937822 green, then trigger release.yml for v0.4.0, confirm, record BUILDLOG, send link; then Milestone B (Novig key signer)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M BRIEF.md
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md

## Last ten checkpoints
```
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
  f1639a9 ckpt 335: A4 app rework compiles locally (ANDROID_HOME=/opt/android-sdk): VigilantApp co
  8988317 ckpt 334: Data-module tests written and green: engine+data 91/91 (NovigTextTest, TeamMat
  b860d7a ckpt 333: A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag
  551afcd ckpt 332: A2 engine done: FairValue (sharp/average/blend + fallback + minBooks), per-mar
  908b0f6 ckpt 331: Logged Tj's 2026-09-25 build request into TASKS.md as milestones A/B/C
  372174e ckpt 330: Read Novig's official v3 API docs + OpenAPI spec, verified the public no-key r
  9c728ab ckpt 329: Logged Tj's 2026-09-25 Novig API beta access message into TASKS.md
  9aeda4b ckpt 328: v0.3.3 shipped and confirmed green (CI run 35695873352, release run 3569566794
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
