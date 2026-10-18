# CHECKPOINT 3687 — read me first, then TASKS.md

**Written:** 2026-10-18T22:54:38Z · **tests:** all 5 fast checks green
**Branch:** `ccr-7f278c49-1o357z` · **builds on:** `b15fb35c3` (this checkpoint is the commit after it)

## Just done
full tests: floor 3918 passed; fixed slate chip names clashing with Tracker period chips (StickyHeadersTest), SlateProfit unknown-start guard; BoltOddsLiveTest flaked once under load (passes alone, untouched code); screenshots 9a/9b/4t/4u checked

## Do this next
bash ship.sh v1.0.0, then release.yml, record-release, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  74efbf781 ckpt 3686: TCL122-9 done (settings tests green 208/208); CLAUDE.md v1.0 rules; TASKS t
  1ba41163e ckpt 3685: TCL122-5 SX socket freshness fix + API audit doc; TCL122-9 settings regroup
  7f8a89ad4 ckpt 3684: TCL122-4 phone half written: LiveBoard model, LightUi phone sockets/stats/t
  e8159d38e ckpt 3683: TCL122-4 engine half: liveOnGitHub/phoneSockets/phoneLive predicates; forCl
  fc623715e ckpt 3682: TCL122-6 SlateProfit model (slate days, fixed start) + new profit card (Tod
  049c471bf ckpt 3681: TCL122-8 meter bars (no M3 stop dot, spent=full red), TCL122-3 CloudLink.re
  4940b3a8f ckpt 3680: TCL122-0 toolbox audit written; took lease by Tj's force; merged mirage's T
  a7343bbf8 ckpt 3679: TCL122-1 Messages show only alerts, recommendations, pushed info; no bets/b
  9e6c0d604 ckpt 3678: TCL122 v1.0 big update written into TASKS
  da76e7451 ckpt 3677: v0.117.6 released, engine sockets verified live
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
