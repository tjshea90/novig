# CHECKPOINT 349 — read me first, then TASKS.md

**Written:** 2026-09-25T06:16:30Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `1fd2efa` (this checkpoint is the commit after it)

## Just done
pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held read-only key) + live websocket order books with 2s repricing; full-test fixes for 4 fake-EV matching risks, tracker disk churn, crash paths, battery (no polling on non-price tabs), Compose skipping (versionCode 9, v0.5.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.5.0), then run: bash tools/record-release.sh v0.5.0 9 "v0.5.0: Novig API key support (one-time setup mints a phone-held read-only key) + live websocket order books with 2s repricing; full-test fixes for 4 fake-EV matching risks, tracker disk churn, crash paths, battery (no polling on non-price tabs), Compose skipping"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
  8501708 ckpt 344: B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccoun
  e12071d ckpt 343: SHIPPED v0.4.0 (code 8): CI 36100221957 green, release 36100439547 green, APK 
  a5f4edc ckpt 342: B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors 
  9c73837 ckpt 340: pre-release: v0.4.0: official Novig public API (no proxy, no key), OddsJam-sty
  9e299a9 ckpt 339: CI fix: run 36100015485 failed because './gradlew test' also ran testReleaseUn
  0c9b3fd ckpt 338: Docs: NOVIG_API.md §5.1 live-measured facts (burst 429, 100% outcome resoluti
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
