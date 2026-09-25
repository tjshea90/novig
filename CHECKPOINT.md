# CHECKPOINT 342 — read me first, then TASKS.md

**Written:** 2026-09-25T05:55:54Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `4974fb4` (this checkpoint is the commit after it)

## Just done
B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors + published signatures; NovigSignedClient; StreamBooks + NovigStream (websocket book channel, token-budgeted chunked subscribes) in data module. data 75 tests green. Secret-scan false positive on runtime-generated test PEMs resolved with the scanner's documented FAKE marker. v0.4.0 release run 36100439547 in progress

## Do this next
Confirm v0.4.0 release + BUILDLOG + send link; then B2 (app Settings Novig key setup, Keystore P-256 read key) + B3 (wire NovigStream into Scanner)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/novig/stream/NovigStream.kt
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/novig/signing/
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/novig/stream/

## Last ten checkpoints
```
  9c73837 ckpt 340: pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-sty
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
  f1639a9 ckpt 335: A4 app rework compiles locally (ANDROID_HOME=/opt/android-sdk): VigilantApp co
  8988317 ckpt 334: Data-module tests written and green: engine+data 91/91 (NovigTextTest, TeamMat
  b860d7a ckpt 333: A1/A3 code written (data module compiles): NovigPublicClient (/v3/public, ETag
  551afcd ckpt 332: A2 engine done: FairValue (sharp/average/blend + fallback + minBooks), per-mar
  908b0f6 ckpt 331: Logged Tj's 2026-09-25 build request into TASKS.md as milestones A/B/C
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
