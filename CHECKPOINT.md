# CHECKPOINT 344 — read me first, then TASKS.md

**Written:** 2026-09-25T05:58:58Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `92444e0` (this checkpoint is the commit after it)

## Just done
B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccount -> Keystore-style P-256 trading::read key -> echo; cleans up keys on failure), HybridNovigSource (stream books, paced REST fallback), NovigSource.focus -> stream subscriptions; NovigSetupTest + NovigStreamTest (mock Novig websocket: signed upgrade, subscribe, snapshot+delta, 451 advice). data 82 tests green

## Do this next
App side: KeystoreVault, NovigConnectionStore, Settings 'Connect Novig' UI (SAF .pem picker), stream lifecycle in live loop, 2s repricing while streaming, status line

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/novig/stream/NovigStreamTest.kt

## Last ten checkpoints
```
  e12071d ckpt 343: SHIPPED v0.4.0 (code 8): CI 36100221957 green, release 36100439547 green, APK 
  a5f4edc ckpt 342: B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors 
  9c73837 ckpt 340: pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-sty
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
  f1639a9 ckpt 335: A4 app rework compiles locally (ANDROID_HOME=/opt/android-sdk): VigilantApp co
  8988317 ckpt 334: Data-module tests written and green: engine+data 91/91 (NovigTextTest, TeamMat
  b860d7a ckpt 333: A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
