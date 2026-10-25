# CHECKPOINT 4250 — read me first, then TASKS.md

**Written:** 2026-10-25T22:54:22Z · **tests:** all 9 fast checks green
**Branch:** `ccr-e58c5a58-3qyzld` · **builds on:** `fb46d0558` (this checkpoint is the commit after it)

## Just done
PX11a/b/c done in code (fill misreport fixed phone+engine, NOFILL keeps the row, book table best-first with red disagreeing books, bid veto study); full floor 4,219 green

## Do this next
ship v1.0.85, engine reset; next session: PX11f per-bet feed census (why ParlayAPI's fresh copies are lost), PX11d outlier audit, PX11e Pinnacle sources

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  694adcf71 ckpt 4249: PX11c bid study (keep the sharp veto: 0 fills against a sharp book, no-shar
  5f0740331 ckpt 4248: PX11a root cause + fix (FILLED orders whose fills were rate-limited were re
  6a646d461 ckpt 4247: PX11a: novig-probe.yml passes default statuses (Gradle refuses empty --args
  3e9157ea2 ckpt 4246: PX11a: novig-probe.yml + cloud/OrdersProbeMain.kt (Novig's record of reject
  3081884d7 ckpt 4245: PX11 written into TASKS.md (5 parts)
  4aa23e7c5 ckpt 4244: v1.0.84 released + recorded; phone adopted Pinnodds off (its own copy now s
  cddc690ee ckpt 4243: PX10 findings recorded (Pinnacle census numbers, migration gap); v1.0.84 re
  835ab9fd2 ckpt 4242: pre-release: v1.0.84 PX10: phone keeps a Claude session's settings change i
  a4c29748b ckpt 4241: pre-ship: v1.0.84 PX10: phone keeps a Claude session's settings change it h
  14947206f ckpt 4240: PX10d/e: Pinnacle census in the quote census; ParlayLive booksHeld fixed; p
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
