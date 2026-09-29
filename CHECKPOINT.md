# CHECKPOINT 2107 — read me first, then TASKS.md

**Written:** 2026-09-29T21:23:13Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `a62443f2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.23.0: the Novig management key is entered once and saved on the phone (sealed by its secure hardware, kept through every update, Replace/Forget in Settings); add or take back any amount you type for the Vigilant wallet; a Bet sheet the wallet can't cover has Add money, which opens Settings on the wallet with the shortfall typed in and a Back to the bet button; the wallet shows in CNO only too (versionCode 51, v0.23.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.23.0), then run: bash tools/record-release.sh v0.23.0 51 "v0.23.0: the Novig management key is entered once and saved on the phone (sealed by its secure hardware, kept through every update, Replace/Forget in Settings); add or take back any amount you type for the Vigilant wallet; a Bet sheet the wallet can't cover has Add money, which opens Settings on the wallet with the shortfall typed in and a Back to the bet button; the wallet shows in CNO only too"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a62443f2 ckpt 2106: B1-B4 ticked; version 0.23.0 (code 51); sweep fixes (keep() rename, section 
  bd62a7ef ckpt 2105: docs updated (NOVIG_API.md §14 saved management key, NovigSetup/NovigBettin
  ca5ef53e ckpt 2104: B1-B4 implemented and targeted tests green: ManagementKeyStoreTest (6), Wall
  121e16e5 ckpt 2103: B1-B4 code written (not compiled yet): ManagementKeyStore (data, sealed by K
  ae1154d4 ckpt 2102: Recorded Tj's request (wallet top-up by typed amount in Settings; 'add money
  4fa76cb1 ckpt 2101: v0.22.0 (code 50) released and recorded; A1-A6 ticked
  c1e67e57 ckpt 2100: pre-release: v0.22.0: the widget opens only from its button; Settings in sev
  37df2065 ckpt 2099: A1-A5 ticked; RESEARCH.md §40; version bumped to 0.22.0 (code 50); MGM sett
  014c514c ckpt 2098: A5: tabs and filters pinned on the Tracker (Stats|Bets, Open/Settled/All, co
  502aff59 ckpt 2097: A4: Settings is seven top tabs (Scan, CNO & widget, Fair odds, +EV feed, Bet
```
