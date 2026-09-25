# CHECKPOINT 347 — read me first, then TASKS.md

**Written:** 2026-09-25T06:10:10Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `a9066e0` (this checkpoint is the commit after it)

## Just done
Full test fix batch 2: tracker writes only on change (was every 2s tick), feed filters deselected leagues instantly + clears when none, stream connect guarded (missing Keystore key -> Failed, not crash), persistence failures never crash the loop, loop idles on Tracker/Settings, real pull-to-refresh indicator, one shared card clock, Compose stability config (report confirms OpportunityCard skippable, all params stable), lastRest pruning, track toast. Docs: CLAUDE.md stale 'no app/no build' sections fixed, repo visibility recorded public; ship.sh runs full suite when SDK present. 135 tests green

## Do this next
Remaining full-test items: re-render screenshots, CI green, bump to v0.5.0 (code 9), ship, final report to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M ship.sh

## Last ten checkpoints
```
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
  8501708 ckpt 344: B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccoun
  e12071d ckpt 343: SHIPPED v0.4.0 (code 8): CI 36100221957 green, release 36100439547 green, APK 
  a5f4edc ckpt 342: B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors 
  9c73837 ckpt 340: pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-sty
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
  8db7c9f ckpt 337: UI verified via Robolectric+Roborazzi screenshots (feed dark/light, no-key, de
  2464858 ckpt 336: Live-verified vs real Novig API: side resolution 98.2% -> fixed school U-abbre
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
