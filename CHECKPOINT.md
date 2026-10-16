# CHECKPOINT 3441 — read me first, then TASKS.md

**Written:** 2026-10-16T16:49:09Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `871c3d795` (this checkpoint is the commit after it)

## Just done
session handoff: v0.114.5 released; v0.114.6 (Kalshi key send) building; ParlayAPI paid-first + engine plan read pushed

## Do this next
1) get_release_by_tag v0.114.6 then tools/record-release.sh v0.114.6 302 'v0.114.6: the app can send your Kalshi key to GitHub so the engine runs Kalshi socket'; tell Tj via chat-reply t1792169257957. 2) engine-log 'ParlayAPI plans:' -> tell Tj which key is paid (reply t1792169064333). 3) after Tj sends Kalshi key: start-up line must say 'Kalshi: signed key present, socket on'. 4) ship v0.114.7 (phone KeyPool paid-first) with any batched asks. 5) TCL40d bid re-post churn; TCL39b SGO docs audit; watch bid desk fills (guard now 1h/8h). Start chat_watch under Monitor with absolute path.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  871c3d795 ckpt 3440: TCL47: ParlayAPI paid key goes first (KeyPool), engine reads plans hourly
  00103ecfa ckpt 3439: Bolt 2 keys confirmed working; v0.114.6 releasing (Kalshi send)
  907d72588 ckpt 3438: pre-release: v0.114.6: the app can send your Kalshi key to GitHub so the en
  7117154ba ckpt 3437: pre-ship: v0.114.6: the app can send your Kalshi key to GitHub so the engin
  9b7042126 ckpt 3436: TCL45a: Kalshi key to GitHub secrets, engine signer + socket; app sends it
  d822fa174 ckpt 3435: engine start log shows how many keys each secret holds
  4ee06d0a5 ckpt 3434: pre-release: v0.114.5: the chat refreshes the moment the app comes back aft
  e778af458 ckpt 3433: pre-ship: v0.114.5: the chat refreshes the moment the app comes back after 
  87e39ba2e ckpt 3432: TCL43: bolt multi-key already supported; answered
  3e5964d35 ckpt 3431: TCL42a: chat loop wakes on app resume
```
