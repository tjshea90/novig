# CHECKPOINT 2103 — read me first, then TASKS.md

**Written:** 2026-09-29T21:03:09Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `7e94742e` (this checkpoint is the commit after it)

## Just done
B1-B4 code written (not compiled yet): ManagementKeyStore (data, sealed by KeystoreSecretBox, excluded from backups), controller saveKey/forgetKey/keyFor/remember, typed wallet amount (WalletAmount), TopUp + requestTopUp/backToBet, sheet Add money button, Settings WalletBlock/TopUpBanner/ManagementKeyBlock, Connect with saved key, betting section shown in CNO only too

## Do this next
compile (ANDROID_HOME=/opt/android-sdk bash tools/test.sh :app:compileDebugKotlin), fix errors, then update ApiBettingUiTest/ControllerTest and add ManagementKeyStoreTest, WalletAmountTest, persistence-through-update test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ae1154d4 ckpt 2102: Recorded Tj's request (wallet top-up by typed amount in Settings; 'add money
  4fa76cb1 ckpt 2101: v0.22.0 (code 50) released and recorded; A1-A6 ticked
  c1e67e57 ckpt 2100: pre-release: v0.22.0: the widget opens only from its button; Settings in sev
  37df2065 ckpt 2099: A1-A5 ticked; RESEARCH.md §40; version bumped to 0.22.0 (code 50); MGM sett
  014c514c ckpt 2098: A5: tabs and filters pinned on the Tracker (Stats|Bets, Open/Settled/All, co
  502aff59 ckpt 2097: A4: Settings is seven top tabs (Scan, CNO & widget, Fair odds, +EV feed, Bet
  a6f40324 ckpt 2096: A1/A3: bets-only pass asks only the bets' market families (BetsScope.familie
  0da2dec5 ckpt 2095: A2: widget only opens from its button: miniWindow default off + schema-10 mi
```

(14 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
