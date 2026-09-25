# CHECKPOINT 348 — read me first, then TASKS.md

**Written:** 2026-09-25T06:12:51Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `b3d5278` (this checkpoint is the commit after it)

## Just done
C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race fixed; full local gate green (135 tests, 1 live skipped; R8 release 4.6MB); live smoke 3872/3872 + 12/12 books; NOVIG_API.md §11.1 documents setup/stream + what's unverified

## Do this next
Wait for CI green on main HEAD, ship.sh, trigger release.yml v0.5.0, confirm, record BUILDLOG, final report to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M NOVIG_API.md
     M TASKS.md

## Last ten checkpoints
```
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
  8501708 ckpt 344: B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccoun
  e12071d ckpt 343: SHIPPED v0.4.0 (code 8): CI 36100221957 green, release 36100439547 green, APK 
  a5f4edc ckpt 342: B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors 
  9c73837 ckpt 340: pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-sty
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
