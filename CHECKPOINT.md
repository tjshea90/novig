# CHECKPOINT 346 — read me first, then TASKS.md

**Written:** 2026-09-25T06:07:31Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `d984770` (this checkpoint is the commit after it)

## Just done
Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Texas Tech, Kansas vs Kansas State, A&M), closeness tie-break, event needs one strong team match (no city-only pairs), per-league start-gap window (daily sports 6h, fights 12h, football 36h: no Friday-vs-Saturday series pricing), started-but-still-pregame games dropped. 5 new tests confirmed FAILING on pre-fix c8398ff, passing now; data 87 green

## Do this next
Fix batch 2: tracker write churn, feed league filter/clear, hoisted clock, pause loop on Tracker/Settings, stream connect guard, persistence runCatching, pull-refresh indicator, Compose stability config, lastRest prune, track toast; then docs + ship.sh gate

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
  8501708 ckpt 344: B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccoun
  e12071d ckpt 343: SHIPPED v0.4.0 (code 8): CI 36100221957 green, release 36100439547 green, APK 
  a5f4edc ckpt 342: B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors 
  9c73837 ckpt 340: pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-sty
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
  f1639a9 ckpt 335: A4 app rework compiles locally (ANDROID_HOME=/opt/android-sdk): VigilantApp co
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
