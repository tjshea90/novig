# CHECKPOINT 3393 — read me first, then TASKS.md

**Written:** 2026-10-16T00:43:33Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `cfdb2b908` (this checkpoint is the commit after it)

## Just done
TCL31f: phone stands down its bid desk when status.deskLive, fetches desk.json, Dashboard 'What the engine is doing with money', syncNow on app open, Settings switch; EngineBoardTest

## Do this next
full test run, docs (CLAUDE.md/ROUTINE.md/CHANGES.md), ship, release, restart engine on new code, verify bids post

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  124582be2 ckpt 3392: TCL31a-e: engine bid desk built (CloudBidRunner, wired in CloudEngine/Cloud
  b7de504cd ckpt 3391: TCL31 planned: engine bid desk (see TASKS.md TCL31)
  fed8df095 ckpt 3390: Dormancy also by slot window (a fresh routine container is acct-unknown); t
  1724f3455 ckpt 3389: Dormancy safeguard: this account's routine stops at rotation checkin / coor
  8bccba7f3 ckpt 3388: TCL30: volume diagnosis + standing volume/quality loop (CLAUDE.md, ROUTINE.
  5e2281ea8 ckpt 3387: pre-release: v0.111.0: chat with Claude inside the app (through the repo; s
  b57240f3a ckpt 3386: pre-ship: v0.111.0: chat with Claude inside the app (through the repo; sess
  7d4783d5f ckpt 3385: TCL29 chat built (model, phone page, watcher, tell_tj chat-reply), away.jso
  dfe553bef ckpt 3384: slot3 ENGINEER: WATCH only, engine healthy, 0 orders held by trap guard
  04f6651f0 ckpt 3383: TCL28 shipped as v0.110.0 (released, recorded), engine restarted on new cod
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
