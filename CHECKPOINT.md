# CHECKPOINT 352 — read me first, then TASKS.md

**Written:** 2026-09-25T12:47:45Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `db85bca` (this checkpoint is the commit after it)

## Just done
Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~40-100 burst then 429 Retry-After 1; Odds API credit rules + ToS on multi-accounts; Polymarket + Kalshi free no-key game lines verified live with limits; pinnapi free Pinnacle trial 100/day no expiry; OddsPapi per-game 250/mo; recommendation). NOVIG_API.md §5.1 updated

## Do this next
Implement v0.6.0: R1 manual-only scans + R2 paced Novig reads (+signed book route with key) + Polymarket/Kalshi/pinnapi reference providers merged per Novig event + Odds API reuse window + settings UI

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M NOVIG_API.md
     M RESEARCH.md

## Last ten checkpoints
```
  db85bca ckpt 351: Logged Tj's 2026-09-25 request (manual-only scans, Novig 429 rate limiting, pr
  1b18e1e ckpt 350: SHIPPED v0.5.0 (code 9): CI 36101866470 green, release 36102142715 green (4.6M
  ace6b60 ckpt 349: pre-release: v0.5.0: Novig API key support (one-time setup mints a phone-held 
  0915546 ckpt 348: C2 full tests done + v0.5.0 (code 9) staged: settings-change-mid-refresh race 
  448b656 ckpt 347: Full test fix batch 2: tracker writes only on change (was every 2s tick), feed
  39d5c9e ckpt 346: Full test, fix batch 1 (fake-EV risks): school-qualifier penalty (Texas vs Tex
  c8398ff ckpt 345: B2+B3 done: Novig key setup UI + Keystore read key + live websocket wired into
  8501708 ckpt 344: B2/B3 data side: NovigSetup (mgmt key -> echo -> reuse/open Vigilant subaccoun
  e12071d ckpt 343: SHIPPED v0.4.0 (code 8): CI 36100221957 green, release 36100439547 green, APK 
  a5f4edc ckpt 342: B1 done: NOVIG-V3 signer (BouncyCastle) verified against all 30 Novig vectors 
```
