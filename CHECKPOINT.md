# CHECKPOINT 340 — read me first, then TASKS.md

**Written:** 2026-09-25T05:52:59Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `77687e7` (this checkpoint is the commit after it)

## Just done
pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-style fair odds (sharp/average/blend, 5 devig methods), executable-price EV + Kelly capped at +EV liquidity, live refresh while open, Games board, bet tracker with CLV (versionCode 8, v0.4.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.4.0), then run: bash tools/record-release.sh v0.4.0 8 "v0.4.0: official Novig public API (no proxy, no key), OddsJam-style fair odds (sharp/average/blend, 5 devig methods), executable-price EV + Kelly capped at +EV liquidity, live refresh while open, Games board, bet tracker with CLV"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
  f1639a9 ckpt 335: A4 app rework compiles locally (ANDROID_HOME=/opt/android-sdk): VigilantApp co
  8988317 ckpt 334: Data-module tests written and green: engine+data 91/91 (NovigTextTest, TeamMat
  b860d7a ckpt 333: A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag
  551afcd ckpt 332: A2 engine done: FairValue (sharp/average/blend + fallback + minBooks), per-mar
  908b0f6 ckpt 331: Logged Tj's 2026-09-25 build request into TASKS.md as milestones A/B/C
  372174e ckpt 330: Read Novig's official v3 API docs + OpenAPI spec, verified the public no-key r
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
